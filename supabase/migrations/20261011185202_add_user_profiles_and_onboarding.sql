create table public.user_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text check (display_name is null or length(trim(display_name)) between 1 and 100),
  date_of_birth date check (date_of_birth is null or date_of_birth between date '1900-01-01' and date '2100-01-01'),
  height_cm numeric(5,1) check (height_cm is null or height_cm between 50 and 300),
  weight_kg numeric(5,1) check (weight_kg is null or weight_kg between 20 and 500),
  profile_setup_completed boolean not null default false,
  onboarding_completed boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.user_profiles enable row level security;
revoke all on public.user_profiles from anon, authenticated;
grant select, insert, update on public.user_profiles to authenticated;

create policy owner_access on public.user_profiles
for all to authenticated
using (
  (select auth.uid()) = user_id
  and coalesce((select auth.jwt()) ->> 'is_anonymous', 'false') <> 'true'
)
with check (
  (select auth.uid()) = user_id
  and coalesce((select auth.jwt()) ->> 'is_anonymous', 'false') <> 'true'
);
