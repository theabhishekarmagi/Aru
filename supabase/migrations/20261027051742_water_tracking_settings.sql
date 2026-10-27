-- Additive owner-protected water metadata. Existing journal text is retained.
alter table public.nutrition_goals add column water_goal_ml integer not null default 2000 check (water_goal_ml between 1 and 20000);
alter table public.journal_entries add column water jsonb;
alter table public.saved_meals add column water jsonb;
alter table public.journal_entries add constraint valid_journal_water check (
 water is null or coalesce(jsonb_typeof(water)='object'
 and water ?& array['milliliters','detectionKey','onlyWater']
 and jsonb_typeof(water->'milliliters')='number'
 and (water->>'milliliters')::numeric between 1 and 20000
 and (water->>'milliliters')::numeric=trunc((water->>'milliliters')::numeric)
 and jsonb_typeof(water->'onlyWater')='boolean'
 and jsonb_typeof(water->'detectionKey')='string'
 and length(water->>'detectionKey') between 1 and 2000,false));
alter table public.saved_meals add constraint valid_saved_water check (
 water is null or coalesce(jsonb_typeof(water)='object'
 and water ?& array['milliliters','detectionKey','onlyWater']
 and jsonb_typeof(water->'milliliters')='number'
 and (water->>'milliliters')::numeric between 1 and 20000
 and (water->>'milliliters')::numeric=trunc((water->>'milliliters')::numeric)
 and jsonb_typeof(water->'onlyWater')='boolean'
 and jsonb_typeof(water->'detectionKey')='string'
 and length(water->>'detectionKey') between 1 and 2000,false));
