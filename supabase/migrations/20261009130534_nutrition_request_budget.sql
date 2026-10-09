create schema if not exists aru_private;
revoke all on schema aru_private from public, anon, authenticated;
grant usage on schema aru_private to service_role;
create table aru_private.nutrition_requests (
 user_id uuid not null references auth.users(id) on delete cascade,
 request_id uuid not null,
 fingerprint text not null,
 created_at timestamptz not null default now(),
 status text not null default 'pending' check(status in ('pending','complete','failed')),
 result jsonb,
 primary key(user_id,request_id)
);
create index nutrition_requests_created on aru_private.nutrition_requests(created_at);
alter table aru_private.nutrition_requests enable row level security;
revoke all on aru_private.nutrition_requests from public, anon, authenticated;
grant select,insert,update,delete on aru_private.nutrition_requests to service_role;
-- Only the authenticated Edge handler may call these with its server-only service key.
-- SECURITY INVOKER: no privilege escalation; no client execution grants.
create function public.reserve_nutrition_request(p_user uuid,p_request uuid,p_fingerprint text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare existing aru_private.nutrition_requests; begin
 perform pg_catalog.pg_advisory_xact_lock(781244982);
 delete from aru_private.nutrition_requests where created_at < now()-interval '7 days';
 select * into existing from aru_private.nutrition_requests where user_id=p_user and request_id=p_request;
 if found then
  if existing.fingerprint <> p_fingerprint then return jsonb_build_object('state','conflict'); end if;
  return jsonb_build_object('state',existing.status,'result',existing.result);
 end if;
 if (select count(*) from aru_private.nutrition_requests where user_id=p_user and created_at>now()-interval '1 minute')>=5
 or (select count(*) from aru_private.nutrition_requests where user_id=p_user and created_at>now()-interval '24 hours')>=40
 or (select count(*) from aru_private.nutrition_requests where created_at>now()-interval '24 hours')>=200 then
  return jsonb_build_object('state','rate_limited');
 end if;
 insert into aru_private.nutrition_requests(user_id,request_id,fingerprint) values(p_user,p_request,p_fingerprint);
 return jsonb_build_object('state','reserved');
end $$;
create function public.finish_nutrition_request(p_user uuid,p_request uuid,p_result jsonb)
returns void language sql security invoker set search_path='' as $$
 update aru_private.nutrition_requests set status=case when p_result is null then 'failed' else 'complete' end,result=p_result
 where user_id=p_user and request_id=p_request and status='pending';
$$;
revoke all on function public.reserve_nutrition_request(uuid,uuid,text) from public,anon,authenticated;
revoke all on function public.finish_nutrition_request(uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.reserve_nutrition_request(uuid,uuid,text) to service_role;
grant execute on function public.finish_nutrition_request(uuid,uuid,jsonb) to service_role;
