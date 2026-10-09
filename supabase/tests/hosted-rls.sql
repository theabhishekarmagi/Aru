-- Run as project administrator. All synthetic users and rows are rolled back.
begin;
insert into auth.users(id) values ('10000000-0000-4000-8000-000000000001'), ('10000000-0000-4000-8000-000000000002');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"10000000-0000-4000-8000-000000000001","role":"authenticated","is_anonymous":false}',true);
insert into public.journal_entries(id,user_id,journal_date,time_zone,body) values ('20000000-0000-4000-8000-000000000001',auth.uid(),current_date,'Asia/Kolkata','RLS test');
insert into public.saved_meals(id,user_id,name,body,estimate) values ('20000000-0000-4000-8000-000000000001',auth.uid(),'RLS test','RLS test','{}');
insert into public.nutrition_goals(user_id,protein_g) values(auth.uid(),100);
do $$ declare t text; n integer; begin
 foreach t in array array['journal_entries','saved_meals','nutrition_goals'] loop
  execute format('select count(*) from public.%I',t) into n;
  if n <> 1 then raise exception 'Owner cannot read %',t; end if;
  execute format('update public.%I set revision=2 where user_id=auth.uid()',t);
  get diagnostics n = row_count;
  if n <> 1 then raise exception 'Owner cannot update %',t; end if;
  begin
   execute format('update public.%I set user_id=''10000000-0000-4000-8000-000000000002'' where user_id=auth.uid()',t);
   raise exception 'Owner reassignment permitted for %',t;
  exception when insufficient_privilege then null; end;
 end loop;
end $$;
select set_config('request.jwt.claims','{"sub":"10000000-0000-4000-8000-000000000002","role":"authenticated","is_anonymous":false}',true);
do $$ declare t text; n integer; begin
 foreach t in array array['journal_entries','saved_meals','nutrition_goals'] loop
  execute format('select count(*) from public.%I',t) into n;
  if n <> 0 then raise exception 'Other owner can read %',t; end if;
  execute format('update public.%I set revision=3',t);
  get diagnostics n = row_count;
  if n <> 0 then raise exception 'Other owner can update %',t; end if;
 end loop;
 begin
  insert into public.nutrition_goals(user_id,protein_g) values ('10000000-0000-4000-8000-000000000001',50);
  raise exception 'Other owner insert permitted';
 exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claims','{"sub":"10000000-0000-4000-8000-000000000001","role":"authenticated","is_anonymous":true}',true);
do $$ declare t text; n integer; begin
 foreach t in array array['journal_entries','saved_meals','nutrition_goals'] loop
  execute format('select count(*) from public.%I',t) into n;
  if n <> 0 then raise exception 'Anonymous user can read %',t; end if;
 end loop;
end $$;
set local role anon;
do $$ declare t text; begin
 foreach t in array array['journal_entries','saved_meals','nutrition_goals'] loop
  begin
   execute format('select * from public.%I',t);
   raise exception 'Unauthenticated read permitted for %',t;
  exception when insufficient_privilege then null; end;
 end loop;
end $$;
reset role;
rollback;
select 'Hosted ownership and anonymous checks passed; fixtures rolled back' as result;
