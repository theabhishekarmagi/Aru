-- Shared free-model budget across all users; failed calls count too.
create or replace function public.reserve_nutrition_request(p_user uuid,p_request uuid,p_fingerprint text)
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
 or (select count(*) from aru_private.nutrition_requests where created_at>now()-interval '24 hours')>=40 then
  return jsonb_build_object('state','rate_limited');
 end if;
 insert into aru_private.nutrition_requests(user_id,request_id,fingerprint) values(p_user,p_request,p_fingerprint);
 return jsonb_build_object('state','reserved');
end $$;
