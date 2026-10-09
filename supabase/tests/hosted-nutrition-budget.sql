begin;
insert into auth.users(id) values ('40000000-0000-4000-8000-000000000001');
set local role authenticated;
do $$ begin
 begin
  perform public.reserve_nutrition_request('40000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000001','x');
  raise exception 'Client executed budget RPC';
 exception when insufficient_privilege then null; end;
end $$;
set local role service_role;
do $$ declare r jsonb; i integer; u uuid='40000000-0000-4000-8000-000000000001'; begin
 r=public.reserve_nutrition_request(u,u,'x');
 if r->>'state'<>'reserved' then raise exception 'Reservation failed: %',r; end if;
 r=public.reserve_nutrition_request(u,u,'x');
 if r->>'state'<>'pending' then raise exception 'Duplicate not blocked'; end if;
 r=public.reserve_nutrition_request(u,u,'changed');
 if r->>'state'<>'conflict' then raise exception 'Payload mismatch not blocked'; end if;
 perform public.finish_nutrition_request(u,u,'{"test":true}');
 r=public.reserve_nutrition_request(u,u,'x');
 if r->>'state'<>'complete' then raise exception 'Cache not returned'; end if;
 for i in 2..5 loop
  r=public.reserve_nutrition_request(u,('40000000-0000-4000-8000-'||lpad(i::text,12,'0'))::uuid,'x');
  if r->>'state'<>'reserved' then raise exception 'Reservation failed'; end if;
 end loop;
 r=public.reserve_nutrition_request(u,'40000000-0000-4000-8000-000000000006','x');
 if r->>'state'<>'rate_limited' then raise exception 'Rate limit failed'; end if;
end $$;
reset role;
rollback;
select 'Budget isolation, duplicate protection, cache, rate limit passed; fixtures rolled back' as result;
