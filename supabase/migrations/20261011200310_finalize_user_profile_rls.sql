drop policy if exists owner_access on public.user_profiles;

create policy owner_access on public.user_profiles
for all to authenticated
using ((select auth.uid()) = user_id and ((select auth.jwt()) ->> 'is_anonymous') is distinct from 'true')
with check ((select auth.uid()) = user_id and ((select auth.jwt()) ->> 'is_anonymous') is distinct from 'true');
