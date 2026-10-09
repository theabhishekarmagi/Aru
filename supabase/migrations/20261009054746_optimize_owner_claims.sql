-- Keep each auth helper itself in an uncorrelated scalar subquery.
alter policy owner_access on public.journal_entries
using ((select auth.uid()) = user_id and coalesce((select auth.jwt())->>'is_anonymous', 'false') <> 'true')
with check ((select auth.uid()) = user_id and coalesce((select auth.jwt())->>'is_anonymous', 'false') <> 'true');
alter policy owner_access on public.saved_meals
using ((select auth.uid()) = user_id and coalesce((select auth.jwt())->>'is_anonymous', 'false') <> 'true')
with check ((select auth.uid()) = user_id and coalesce((select auth.jwt())->>'is_anonymous', 'false') <> 'true');
alter policy owner_access on public.nutrition_goals
using ((select auth.uid()) = user_id and coalesce((select auth.jwt())->>'is_anonymous', 'false') <> 'true')
with check ((select auth.uid()) = user_id and coalesce((select auth.jwt())->>'is_anonymous', 'false') <> 'true');
