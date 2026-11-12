-- Borrowed Time backend. Tables live in a private schema; the app only calls
-- the bt_* functions, which check a per-device secret on every call.

create extension if not exists pgcrypto with schema extensions;

create schema if not exists bt;
revoke all on schema bt from public, anon, authenticated;

create table if not exists bt.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 32),
  code text not null unique,
  created_at timestamptz not null default now()
);

create table if not exists bt.members (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references bt.groups(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 24),
  secret_hash text not null,
  tz_offset int not null default 330 check (tz_offset between -840 and 840),
  limit_minutes int not null default 60 check (limit_minutes between 0 and 1440),
  used_seconds int not null default 0 check (used_seconds >= 0),
  used_day date,
  guard_ok boolean not null default false,
  apps_count int not null default 0,
  last_seen timestamptz,
  joined_at timestamptz not null default now(),
  active boolean not null default true
);
create index if not exists members_group_idx on bt.members(group_id) where active;

create table if not exists bt.requests (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references bt.groups(id) on delete cascade,
  requester uuid not null references bt.members(id) on delete cascade,
  minutes int not null check (minutes in (5, 10, 15, 30)),
  note text check (note is null or char_length(note) <= 140),
  day date not null,
  status text not null default 'pending' check (status in ('pending','approved','declined','expired')),
  granted_minutes int not null default 0,
  decided_by uuid references bt.members(id) on delete set null,
  created_at timestamptz not null default now(),
  decided_at timestamptz
);
create index if not exists requests_group_idx on bt.requests(group_id, created_at desc);
create index if not exists requests_req_idx on bt.requests(requester, day);

create table if not exists bt.votes (
  request_id uuid not null references bt.requests(id) on delete cascade,
  member_id uuid not null references bt.members(id) on delete cascade,
  vote text not null check (vote in ('approve','decline')),
  primary key (request_id, member_id)
);

alter table bt.groups enable row level security;
alter table bt.members enable row level security;
alter table bt.requests enable row level security;
alter table bt.votes enable row level security;

-- helpers ------------------------------------------------------------------

create or replace function bt.auth(p_member uuid, p_secret text)
returns bt.members language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members;
begin
  select * into m from bt.members
   where id = p_member and active
     and secret_hash = encode(extensions.digest(coalesce(p_secret, ''), 'sha256'), 'hex');
  if not found then raise exception 'unauthorized'; end if;
  return m;
end $$;

create or replace function bt.local_day(p_tz int)
returns date language sql stable as $$
  select ((now() at time zone 'utc') + make_interval(mins => p_tz))::date
$$;

create or replace function bt.new_code()
returns text language plpgsql as $$
declare alphabet text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; c text; i int;
begin
  loop
    c := '';
    for i in 1..6 loop
      c := c || substr(alphabet, 1 + floor(random() * 31)::int, 1);
    end loop;
    exit when not exists (select 1 from bt.groups where code = c);
  end loop;
  return c;
end $$;

-- snapshot -------------------------------------------------------------------

create or replace function bt.snapshot(p_member uuid)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members; g bt.groups; res json; today date;
begin
  select * into m from bt.members where id = p_member;
  select * into g from bt.groups where id = m.group_id;
  today := bt.local_day(m.tz_offset);

  update bt.requests set status = 'expired', decided_at = now()
   where group_id = m.group_id and status = 'pending' and created_at < now() - interval '2 hours';

  select json_build_object(
    'now', extract(epoch from now())::bigint,
    'group', json_build_object('id', g.id, 'name', g.name, 'code', g.code),
    'me', json_build_object(
      'id', m.id, 'name', m.name,
      'requests_today', (select count(*) from bt.requests r where r.requester = m.id and r.day = today),
      'granted_today', coalesce((select sum(r.granted_minutes) from bt.requests r
                                  where r.requester = m.id and r.day = today and r.status = 'approved'), 0)
    ),
    'members', coalesce((
      select json_agg(json_build_object(
        'id', x.id, 'name', x.name, 'limit', x.limit_minutes,
        'used', case when x.used_day = bt.local_day(x.tz_offset) then x.used_seconds else 0 end,
        'granted', coalesce((select sum(r.granted_minutes) from bt.requests r
                              where r.requester = x.id and r.day = bt.local_day(x.tz_offset) and r.status = 'approved'), 0),
        'guard_ok', x.guard_ok, 'apps', x.apps_count,
        'seen', extract(epoch from x.last_seen)::bigint,
        'tz', x.tz_offset
      ) order by x.joined_at)
      from bt.members x where x.group_id = m.group_id and x.active), '[]'::json),
    'requests', coalesce((
      select json_agg(q order by q.created desc) from (
        select r.id, r.requester as requester_id, rm.name as requester_name, r.minutes, r.note, r.status,
               r.granted_minutes as granted, extract(epoch from r.created_at)::bigint as created,
               extract(epoch from r.decided_at)::bigint as decided, dm.name as decided_by,
               (select v.vote from bt.votes v where v.request_id = r.id and v.member_id = m.id) as my_vote
          from bt.requests r
          join bt.members rm on rm.id = r.requester
          left join bt.members dm on dm.id = r.decided_by
         where r.group_id = m.group_id and r.created_at > now() - interval '36 hours'
         order by r.created_at desc limit 40
      ) q), '[]'::json)
  ) into res;
  return res;
end $$;

-- public API -----------------------------------------------------------------

create or replace function public.bt_create_group(p_group_name text, p_name text, p_tz int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare g_id uuid; m_id uuid; sec text; code text;
begin
  if char_length(trim(coalesce(p_group_name, ''))) not between 1 and 32 then raise exception 'bad_group_name'; end if;
  if char_length(trim(coalesce(p_name, ''))) not between 1 and 24 then raise exception 'bad_name'; end if;
  code := bt.new_code();
  insert into bt.groups(name, code) values (trim(p_group_name), code) returning id into g_id;
  sec := encode(extensions.gen_random_bytes(24), 'hex');
  insert into bt.members(group_id, name, secret_hash, tz_offset)
  values (g_id, trim(p_name), encode(extensions.digest(sec, 'sha256'), 'hex'), coalesce(p_tz, 330))
  returning id into m_id;
  return json_build_object('member_id', m_id, 'secret', sec, 'snapshot', bt.snapshot(m_id));
end $$;

create or replace function public.bt_join_group(p_code text, p_name text, p_tz int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare g bt.groups; m_id uuid; sec text;
begin
  if char_length(trim(coalesce(p_name, ''))) not between 1 and 24 then raise exception 'bad_name'; end if;
  select * into g from bt.groups where code = upper(trim(coalesce(p_code, '')));
  if not found then raise exception 'no_such_group'; end if;
  if (select count(*) from bt.members where group_id = g.id and active) >= 6 then raise exception 'group_full'; end if;
  if exists (select 1 from bt.members where group_id = g.id and active and lower(name) = lower(trim(p_name))) then
    raise exception 'name_taken';
  end if;
  sec := encode(extensions.gen_random_bytes(24), 'hex');
  insert into bt.members(group_id, name, secret_hash, tz_offset)
  values (g.id, trim(p_name), encode(extensions.digest(sec, 'sha256'), 'hex'), coalesce(p_tz, 330))
  returning id into m_id;
  return json_build_object('member_id', m_id, 'secret', sec, 'snapshot', bt.snapshot(m_id));
end $$;

create or replace function public.bt_sync(p_member uuid, p_secret text, p_limit int, p_used int, p_guard boolean, p_apps int, p_tz int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members;
begin
  m := bt.auth(p_member, p_secret);
  update bt.members set
    limit_minutes = greatest(0, least(1440, coalesce(p_limit, limit_minutes))),
    used_seconds = greatest(0, least(86400, coalesce(p_used, 0))),
    guard_ok = coalesce(p_guard, false),
    apps_count = greatest(0, coalesce(p_apps, 0)),
    tz_offset = coalesce(p_tz, tz_offset),
    used_day = bt.local_day(coalesce(p_tz, tz_offset)),
    last_seen = now()
  where id = m.id;
  return bt.snapshot(m.id);
end $$;

create or replace function public.bt_request(p_member uuid, p_secret text, p_minutes int, p_note text)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members; today date;
begin
  m := bt.auth(p_member, p_secret);
  perform 1 from bt.members where id = m.id for update;
  today := bt.local_day(m.tz_offset);
  if p_minutes not in (5, 10, 15, 30) then raise exception 'bad_minutes'; end if;
  if (select count(*) from bt.members where group_id = m.group_id and active) < 2 then raise exception 'alone'; end if;
  if (select count(*) from bt.requests where requester = m.id and day = today) >= 3 then raise exception 'daily_limit'; end if;
  update bt.requests set status = 'expired', decided_at = now()
   where requester = m.id and status = 'pending' and created_at < now() - interval '2 hours';
  if exists (select 1 from bt.requests where requester = m.id and status = 'pending') then raise exception 'already_pending'; end if;
  insert into bt.requests(group_id, requester, minutes, note, day)
  values (m.group_id, m.id, p_minutes, nullif(left(trim(coalesce(p_note, '')), 140), ''), today);
  return bt.snapshot(m.id);
end $$;

create or replace function public.bt_decide(p_member uuid, p_secret text, p_request uuid, p_approve boolean, p_minutes int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members; r bt.requests; others int; declines int;
begin
  m := bt.auth(p_member, p_secret);
  select * into r from bt.requests where id = p_request and group_id = m.group_id for update;
  if not found then raise exception 'no_such_request'; end if;
  if r.requester = m.id then raise exception 'own_request'; end if;
  if r.status <> 'pending' then raise exception 'already_decided'; end if;
  if r.created_at < now() - interval '2 hours' then
    update bt.requests set status = 'expired', decided_at = now() where id = r.id;
    raise exception 'expired';
  end if;
  if p_approve then
    insert into bt.votes(request_id, member_id, vote) values (r.id, m.id, 'approve')
      on conflict (request_id, member_id) do update set vote = 'approve';
    update bt.requests set status = 'approved', decided_by = m.id, decided_at = now(),
           granted_minutes = greatest(1, least(r.minutes, coalesce(p_minutes, r.minutes)))
     where id = r.id;
  else
    insert into bt.votes(request_id, member_id, vote) values (r.id, m.id, 'decline')
      on conflict (request_id, member_id) do update set vote = 'decline';
    select count(*) into others from bt.members where group_id = m.group_id and active and id <> r.requester;
    select count(*) into declines from bt.votes where request_id = r.id and vote = 'decline';
    if declines >= others then
      update bt.requests set status = 'declined', decided_by = m.id, decided_at = now() where id = r.id;
    end if;
  end if;
  return bt.snapshot(m.id);
end $$;

create or replace function public.bt_leave(p_member uuid, p_secret text)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members;
begin
  m := bt.auth(p_member, p_secret);
  update bt.members set active = false where id = m.id;
  update bt.requests set status = 'expired', decided_at = now() where requester = m.id and status = 'pending';
  return json_build_object('left', true);
end $$;

-- lock the public surface to exactly these functions
revoke all on function bt.auth(uuid, text) from public, anon, authenticated;
revoke all on function bt.snapshot(uuid) from public, anon, authenticated;
revoke all on function bt.new_code() from public, anon, authenticated;
revoke all on function bt.local_day(int) from public, anon, authenticated;

revoke all on function public.bt_create_group(text, text, int) from public;
revoke all on function public.bt_join_group(text, text, int) from public;
revoke all on function public.bt_sync(uuid, text, int, int, boolean, int, int) from public;
revoke all on function public.bt_request(uuid, text, int, text) from public;
revoke all on function public.bt_decide(uuid, text, uuid, boolean, int) from public;
revoke all on function public.bt_leave(uuid, text) from public;

grant execute on function public.bt_create_group(text, text, int) to anon;
grant execute on function public.bt_join_group(text, text, int) to anon;
grant execute on function public.bt_sync(uuid, text, int, int, boolean, int, int) to anon;
grant execute on function public.bt_request(uuid, text, int, text) to anon;
grant execute on function public.bt_decide(uuid, text, uuid, boolean, int) to anon;
grant execute on function public.bt_leave(uuid, text) to anon;
