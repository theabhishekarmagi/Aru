# Aru requirements

Last updated: 8 October 2026. Source: user instructions relayed from the parent chat.

## Confirmed product scope

- Name: Aru. Android first. Free personal test app initially, no public release.
- Purpose: calorie tracking and a personal food journal. Indian regional home cooking broadly, restaurants, and fast-food chains.
- V1 input is exclusively text. Understand natural descriptions, misspellings, dish names, restaurant context, and quantities without requiring recipes.
- Example input: `Eat burger and frenchfries at Mcdonels`. Correct spelling/context interpretation must not invent the burger variant, market, fries size, or quantity.
- Support count, household, weight, volume, and serving portions using extensible unit identifiers. Ambiguous quantities are assumptions that the user can inspect and edit.
- Show calories (kcal), protein, carbs, fat, and fiber (grams); daily totals; configurable calorie and macro goals. Goals start unset, not at invented targets.
- Show matched items, portions, assumptions, and source references. Allow manual corrections and saved-meal reuse.
- Account mandatory. No guest mode, fake login, or anonymous journal bypass.
- Online AI processing is authorized. AI/search/USDA API keys remain exclusively on the server, never in APK resources, BuildConfig, source, or local app storage.
- Reference nutrition: INDB for Indian dishes; USDA FoodData Central for general ingredients and other foods; official restaurant data when available with market, item, and size preserved.
- Excluded: camera, voice, widgets, reminders, Health integrations, subscriptions, coaching, weight tracking.

## Pending choices and assets

- Confirmed: Supabase backend/Postgres and Google sign-in through Supabase Auth. Project configuration is complete; user confirmed real Google sign-in. See `SUPABASE_SETUP.md`.
- OpenRouter selected; API key must be saved as OPENROUTER_API_KEY in Supabase secrets. Authenticated nutrition Edge Function is deployed; see NUTRITION_SERVICE.md for live validation limits.
- Interface language preference and desired food-input language coverage. English scaffold strings are provisional, not a settled language decision.
- Design is now specified: match the Amy website intro and screens, adapted to the confirmed Aru scope. See `AMY_DESIGN.md`.
- A user-preferred INDB sheet/version may still arrive. Official public 2024.11 workbook was downloaded and inspected as a provisional reference. See `REFERENCE_DATA.md`.
- Backend retention, server-side account isolation, export/deletion, and account recovery details before real personal data flows through the service.

## Foundation implementation and boundaries

- Native Kotlin/Jetpack Compose app with a temporary account-required setup gate. No fabricated authentication or live AI result. The Amy-style journal UI is implemented and tested with isolated instrumentation sessions; normal access remains gated until real sign-in is configured.
- Pure domain models and authenticated repository; per-account local atomic JSON storage. The domain writes text immediately on add/edit, before estimation. The editor dispatches each change through an ordered IO queue; estimation starts once after a 1.8-second typing pause, cancelling superseded work. Failed requests do not retry without a new edit or explicit Retry.
- Stable UUID entry IDs, revision + request ID protection, draft/queued/calculating/ready/review/failed/manual status, late-result rejection, and process-interruption recovery.
- Manual corrections invalidate in-flight requests. Saved meals and each reused entry own independent serialized nutrition snapshots.
- Durable delete undo for the latest 20 deletions; restored entries keep identity and advance revision. Old estimates cannot resurrect deletions or overwrite restored entries.
- All five nutrients preserve unknown versus zero. Totals expose known amounts, missing-item counts, pending entries, and entries needing review.
- Date and timezone recorded explicitly on every entry; moving between zones must not silently reassign historical journal days.
- No nutrition dataset imported into APK yet; no server implementation or network permission yet.
- Local persistence is private app storage with Android backup disabled. It is not an encrypted database. Authentication gate and store contracts are client-side foundations, not substitutes for backend authorization.

## Integration acceptance criteria

1. Real login must establish a nonexpired account session; signed-out users cannot read or mutate journal data. Backend independently verifies token and ownership.
2. Text survives app restart/offline use before calculation. A failed storage write is visible; existing corrupted/unsupported state is never silently reset.
3. Old estimate responses fail after edits, retries, manual correction, deletion, undo, or account changes.
4. Changing one reused serving never changes another serving or its saved template.
5. Ambiguous portions show assumptions and require review. Missing nutrients remain unknown. Totals visibly disclose pending or incomplete estimates.
6. Restaurant matches retain market/menu variant/size. Incorrect matches can be corrected without recipes.
7. Goals are user-configurable; no personalized target is invented.
8. All provider keys stay server-side; meal text is excluded from diagnostic logs.
9. INDB import is versioned, attributed, validated and benchmarked against held-out actual records. No evaluation claims until a live estimator exists.
10. Screens follow the supplied Amy reference and receive emulator UI checks. English is provisional; localization and broader accessibility/device coverage remain pending. Without real auth and estimation, this is not an end-to-end usable app.
