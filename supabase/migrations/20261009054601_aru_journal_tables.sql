-- Journal schema with owner-only access.
create table public.journal_entries (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  journal_date date not null,
  time_zone text not null check (length(time_zone) between 1 and 100),
  body text not null check (length(body) <= 20000),
  revision bigint not null default 1 check (revision > 0),
  status text not null default 'DRAFT' check (status in ('DRAFT','QUEUED','CALCULATING','READY','NEEDS_REVIEW','FAILED','MANUAL')),
  estimate jsonb check (estimate is null or jsonb_typeof(estimate) = 'object'),
  deleted_at timestamptz,
  updated_at timestamptz not null default now()
);
create index journal_entries_owner_date on public.journal_entries(user_id, journal_date);
create table public.saved_meals (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (length(trim(name)) between 1 and 200),
  body text not null check (length(body) <= 20000),
  estimate jsonb not null check (jsonb_typeof(estimate) = 'object'),
  revision bigint not null default 1 check (revision > 0),
  updated_at timestamptz not null default now()
);
create index saved_meals_owner on public.saved_meals(user_id);
create table public.nutrition_goals (
  user_id uuid primary key references auth.users(id) on delete cascade,
  calories_kcal numeric check (calories_kcal > 0 and calories_kcal < 'Infinity'::numeric),
  protein_g numeric check (protein_g > 0 and protein_g < 'Infinity'::numeric),
  carbs_g numeric check (carbs_g > 0 and carbs_g < 'Infinity'::numeric),
  fat_g numeric check (fat_g > 0 and fat_g < 'Infinity'::numeric),
  fiber_g numeric check (fiber_g > 0 and fiber_g < 'Infinity'::numeric),
  revision bigint not null default 1 check (revision > 0),
  updated_at timestamptz not null default now()
);

alter table public.journal_entries enable row level security;
revoke all on public.journal_entries from anon, authenticated;
grant select, insert, update on public.journal_entries to authenticated;
create policy owner_access on public.journal_entries for all to authenticated
using ((select auth.uid()) = user_id and coalesce((select auth.jwt()->>'is_anonymous'), 'false') <> 'true')
with check ((select auth.uid()) = user_id and coalesce((select auth.jwt()->>'is_anonymous'), 'false') <> 'true');

alter table public.saved_meals enable row level security;
revoke all on public.saved_meals from anon, authenticated;
grant select, insert, update on public.saved_meals to authenticated;
create policy owner_access on public.saved_meals for all to authenticated
using ((select auth.uid()) = user_id and coalesce((select auth.jwt()->>'is_anonymous'), 'false') <> 'true')
with check ((select auth.uid()) = user_id and coalesce((select auth.jwt()->>'is_anonymous'), 'false') <> 'true');

alter table public.nutrition_goals enable row level security;
revoke all on public.nutrition_goals from anon, authenticated;
grant select, insert, update on public.nutrition_goals to authenticated;
create policy owner_access on public.nutrition_goals for all to authenticated
using ((select auth.uid()) = user_id and coalesce((select auth.jwt()->>'is_anonymous'), 'false') <> 'true')
with check ((select auth.uid()) = user_id and coalesce((select auth.jwt()->>'is_anonymous'), 'false') <> 'true');
