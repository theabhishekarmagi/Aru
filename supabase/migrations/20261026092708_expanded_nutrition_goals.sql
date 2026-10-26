-- Optional goals and preferences remain private under the existing owner policies.
alter table public.nutrition_goals
 add column sugar_g numeric check (sugar_g > 0 and sugar_g <= 2000 and sugar_g < 'Infinity'::numeric),
 add column sodium_mg numeric check (sodium_mg > 0 and sodium_mg <= 100000 and sodium_mg < 'Infinity'::numeric),
 add column goal_type text not null default 'maintain' check (goal_type in ('maintain','lose','gain')),
 add column target_weight_kg numeric check (target_weight_kg between 20 and 500),
 add column target_date date,
 add column weight_unit text not null default 'kg' check (weight_unit in ('kg','lb')),
 add column dietary_preferences text[] not null default '{}' check (dietary_preferences <@ array['Vegetarian','Vegan','Pescatarian','Halal','Gluten-free','Dairy-free']::text[] and cardinality(dietary_preferences) <= 6),
 add column equation_sex text check (equation_sex in ('female','male')),
 add column activity_level text not null default 'sedentary' check (activity_level in ('sedentary','light','moderate','active')),
 add column track_sugar boolean not null default false,
 add column track_fiber boolean not null default false,
 add column track_sodium boolean not null default false;
update public.nutrition_goals set track_fiber=true where fiber_g is not null;
