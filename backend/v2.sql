-- v2: abuse throttles, asks counted per name in the group, rejoin cooldown.

create table if not exists bt.attempts (
  id bigserial primary key,
  ip text not null,
  kind text not null,
  at timestamptz not null default now()
);
create index if not exists attempts_idx on bt.attempts(ip, kind, at desc);
alter table bt.attempts enable row level security;
alter table bt.members add column if not exists left_at timestamptz;

create or replace function bt.throttle(p_kind text, p_max int, p_window interval)
returns void language plpgsql security definer set search_path = bt, public as $$
declare ip text; n int;
begin
  begin
    ip := coalesce(split_part((current_setting('request.headers', true)::json ->> 'x-forwarded-for'), ',', 1), 'unknown');
  exception when others then ip := 'unknown'; end;
  delete from bt.attempts where at < now() - interval '1 day';
  select count(*) into n from bt.attempts where bt.attempts.ip = throttle.ip and kind = p_kind and at > now() - p_window;
  if n >= p_max then raise exception 'too_many'; end if;
  insert into bt.attempts(ip, kind) values (ip, p_kind);
end $$;
revoke all on function bt.throttle(text, int, interval) from public, anon, authenticated;

create or replace function bt.new_code()
returns text language plpgsql as $$
declare alphabet text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; c text; i int;
begin
  loop
    c := '';
    for i in 1..6 loop
      c := c || substr(alphabet, 1 + (get_byte(extensions.gen_random_bytes(1), 0) % 31), 1);
    end loop;
    exit when not exists (select 1 from bt.groups where code = c);
  end loop;
  return c;
end $$;

create or replace function public.bt_create_group(p_group_name text, p_name text, p_tz int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare g_id uuid; m_id uuid; sec text; code text;
begin
  perform bt.throttle('create', 5, interval '1 hour');
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
  perform bt.throttle('join', 12, interval '10 minutes');
  if char_length(trim(coalesce(p_name, ''))) not between 1 and 24 then raise exception 'bad_name'; end if;
  select * into g from bt.groups where code = upper(trim(coalesce(p_code, '')));
  if not found then raise exception 'no_such_group'; end if;
  if (select count(*) from bt.members where group_id = g.id and active) >= 6 then raise exception 'group_full'; end if;
  if exists (select 1 from bt.members where group_id = g.id and active and lower(name) = lower(trim(p_name))) then
    raise exception 'name_taken';
  end if;
  if exists (select 1 from bt.members where group_id = g.id and not active and lower(name) = lower(trim(p_name))
             and left_at > now() - interval '12 hours') then
    raise exception 'recently_left';
  end if;
  sec := encode(extensions.gen_random_bytes(24), 'hex');
  insert into bt.members(group_id, name, secret_hash, tz_offset)
  values (g.id, trim(p_name), encode(extensions.digest(sec, 'sha256'), 'hex'), coalesce(p_tz, 330))
  returning id into m_id;
  return json_build_object('member_id', m_id, 'secret', sec, 'snapshot', bt.snapshot(m_id));
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
  if (select count(*) from bt.requests r join bt.members x on x.id = r.requester
       where x.group_id = m.group_id and lower(x.name) = lower(m.name) and r.day = today) >= 3 then
    raise exception 'daily_limit';
  end if;
  update bt.requests set status = 'expired', decided_at = now()
   where requester = m.id and status = 'pending' and created_at < now() - interval '2 hours';
  if exists (select 1 from bt.requests where requester = m.id and status = 'pending') then raise exception 'already_pending'; end if;
  insert into bt.requests(group_id, requester, minutes, note, day)
  values (m.group_id, m.id, p_minutes, nullif(left(trim(coalesce(p_note, '')), 140), ''), today);
  return bt.snapshot(m.id);
end $$;

create or replace function public.bt_leave(p_member uuid, p_secret text)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members;
begin
  m := bt.auth(p_member, p_secret);
  update bt.members set active = false, left_at = now() where id = m.id;
  update bt.requests set status = 'expired', decided_at = now() where requester = m.id and status = 'pending';
  return json_build_object('left', true);
end $$;
