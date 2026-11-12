-- v3: friends can see who left; ask count per name; regenerate the group code.
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
      'requests_today', (select count(*) from bt.requests r join bt.members x2 on x2.id = r.requester
                          where x2.group_id = m.group_id and lower(x2.name) = lower(m.name) and r.day = today),
      'granted_today', coalesce((select sum(r.granted_minutes) from bt.requests r
                                  where r.requester = m.id and r.day = today and r.status = 'approved'), 0)
    ),
    'left', coalesce((
      select json_agg(json_build_object('name', l.name, 'at', extract(epoch from l.left_at)::bigint) order by l.left_at desc)
      from bt.members l where l.group_id = m.group_id and not l.active and l.left_at > now() - interval '48 hours'
        and not exists (select 1 from bt.members a where a.group_id = l.group_id and a.active and lower(a.name) = lower(l.name))), '[]'::json),
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


create or replace function public.bt_new_code(p_member uuid, p_secret text)
returns json language plpgsql security definer set search_path = bt, public, extensions as $$
declare m bt.members;
begin
  m := bt.auth(p_member, p_secret);
  perform bt.throttle('newcode', 5, interval '1 hour');
  update bt.groups set code = bt.new_code() where id = m.group_id;
  return bt.snapshot(m.id);
end $$;
revoke all on function public.bt_new_code(uuid, text) from public;
grant execute on function public.bt_new_code(uuid, text) to anon;
