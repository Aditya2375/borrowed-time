-- v4: client IP from the platform header (not the caller-controlled first X-Forwarded-For entry),
-- plus project-wide caps that do not depend on any IP.
create or replace function bt.throttle(p_kind text, p_max int, p_window interval, p_global int default 0)
returns void language plpgsql security definer set search_path = bt, public as $$
declare v_ip text; n int; h json; xff text;
begin
  begin
    h := current_setting('request.headers', true)::json;
    xff := h ->> 'x-forwarded-for';
    v_ip := coalesce(nullif(h ->> 'cf-connecting-ip', ''), nullif(trim(split_part(xff, ',', array_length(string_to_array(coalesce(xff,''), ','), 1))), ''), 'unknown');
  exception when others then v_ip := 'unknown'; end;
  delete from bt.attempts where at < now() - interval '1 day';
  if p_global > 0 then
    select count(*) into n from bt.attempts a where a.ip = 'global' and a.kind = p_kind and a.at > now() - interval '1 minute';
    if n >= p_global then raise exception 'too_many'; end if;
    insert into bt.attempts(ip, kind) values ('global', p_kind);
  end if;
  select count(*) into n from bt.attempts a where a.ip = v_ip and a.kind = p_kind and a.at > now() - p_window;
  if n >= p_max then raise exception 'too_many'; end if;
  insert into bt.attempts(ip, kind) values (v_ip, p_kind);
end $$;
revoke all on function bt.throttle(text, int, interval, int) from public, anon, authenticated;
drop function if exists bt.throttle(text, int, interval);

create or replace function public.bt_join_group(p_code text, p_name text, p_tz int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare g bt.groups; m_id uuid; sec text;
begin
  perform bt.throttle('join', 12, interval '10 minutes', 60);
  if char_length(trim(coalesce(p_name, ''))) not between 1 and 24 then raise exception 'bad_name'; end if;
  select * into g from bt.groups where code = upper(trim(coalesce(p_code, '')));
  if not found then return json_build_object('error', 'no_such_group'); end if;
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

create or replace function public.bt_create_group(p_group_name text, p_name text, p_tz int)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare g_id uuid; m_id uuid; sec text; code text;
begin
  perform bt.throttle('create', 5, interval '1 hour', 30);
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


create or replace function public.bt_new_code(p_member uuid, p_secret text)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members;
begin
  m := bt.auth(p_member, p_secret);
  perform bt.throttle('newcode', 5, interval '1 hour', 30);
  update bt.groups set code = bt.new_code() where id = m.group_id;
  return bt.snapshot(m.id);
end $$;
revoke all on function public.bt_new_code(uuid, text) from public;
grant execute on function public.bt_new_code(uuid, text) to anon;
