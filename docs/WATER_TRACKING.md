# Water tracking (Android v0.10.0 and web)

The journal stays a plain notebook: water entries show a blue droplet and recorded milliliters instead of a calorie label. Tap entry text to open its editable description, portion presets, and custom ml field. Tap the blue droplet/ml label or the daily water summary to open Daily Water.

- Plain `water` and `one glass of water` default to **250 ml**.
- `three glasses of water` records **750 ml**.
- A bottle defaults to **500 ml**; actual bottle sizes vary.
- Explicit volumes such as `330 ml water`, `1.5 litres of water`, and `water 500ml` are supported.
- Presets: one glass (250 ml), two glasses (500 ml), one bottle (500 ml), one litre (1000 ml).
- Custom whole-number amounts from 1 to 20000 ml can be recorded. Glass/bottle presets are assumptions; use your measured amount when available.

## Persistence and nutrition

Android stores water in the existing account-scoped local journal, as an optional `water` field on each entry. The website stores water in the owner-protected `journal_entries` and `saved_meals` tables. These clients do not sync journals with each other. Old journal files remain readable; existing plain-water entries are normalized when the journal opens. Amounts survive reopening, delete/undo, and saved-meal reuse for mixed food/water entries.

Plain water never requests an AI nutrition estimate and is excluded from calorie/macronutrient totals, pending-nutrition counts, and the header's meal count. Mixed descriptions such as `rice and a glass of water` retain regular food nutrition alongside the water amount. A volume-only correction preserves any in-flight food estimate; a description change still invalidates stale estimates.

A manually chosen amount overrides the detected amount without rewriting the original description, like manual meal-portion corrections. Unrelated food-text edits retain that correction. Changing the detected water quantity (e.g. `water` to `two glasses of water`) recalculates the amount; removing water removes its hydration field.

Parsing is conservative and English-language. Watermelon, coconut water, sweetened/flavoured drinks, and cooking/ingredient descriptions are left to normal food estimation. For ambiguous descriptions, log the plain drinking water as a separate line.

## Daily Water and Weekly Insights

Daily Water shows an animated water level, the selected date’s total and percentage, and quick +250 ml/+500 ml actions. The goal defaults to **2000 ml (2 litres)** and can be changed to a whole number from 1 to 20000 ml. This is an editable app default, not a calculated hydration prescription. Android saves it in goal preferences; the web saves it as `nutrition_goals.water_goal_ml`.

Settings includes **Daily Water** and **Weekly Insights**. Insights covers seven days ending on the selected date (capped at today), with previous/next week navigation, daily calorie and water charts, macro averages, and days logged. Food averages use only days with complete values for that nutrient; pending/incomplete days do not dilute averages. Water averages include all seven days. A saved meal or water entry counts toward logging consistency. Deleted entries and unsaved web drafts are excluded. Unknown nutrition displays — and partial totals show +.

## Introductory cards

The public SDUI configuration is now revision 2 with `cards: []`. Devices already using the SDUI APK remove the cards after their next eligible foreground refresh (no more than once every five minutes). The new tracker and Weekly Insights require installing v0.10.0 once; they are outside the current SDUI card schema.

## Verification

Unit tests cover quantities, exclusions, persistence/serialization, old-entry normalization, calorie isolation, in-flight estimate handling, saved-meal reuse, delete/undo, and the absence of AI calls for plain water.

`WaterUiTest` uses synthetic local journal fixtures to verify the droplet/ml row, tap-to-open description and portions, glass/bottle selection, custom ml persistence after controller recreation, shorter description edits, and mixed food/water estimates. Existing food editing/deletion and saved-meal tests guard against regressions.

The v0.10.0 tests additionally cover default/edited goals, daily totals, seven-day averages and incomplete days, website water parsing, PostgreSQL validation/owner isolation, meal-text details editing, and the Settings insights route.
