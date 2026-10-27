# Nutrition calculation

Aru sends a food description to the authenticated `estimate-nutrition` Supabase Edge Function after a 1.8-second pause in typing. Android dictation writes into the same text flow. The nutrition response also includes a validated summary under 160 characters, so compact journal display does not require a second provider request; the original description remains stored locally for editing and future recalculation. A transient photo request sends one compressed JPEG to the same authenticated function and returns a short dish description plus structured nutrition; the photo is never written to the journal, Supabase Storage, database cache, or handler logs. Each text edit is saved locally first and cancels the prior debounce/in-flight client request. A revision check rejects stale server results. The function verifies the Supabase user, reserves a request budget, calls OpenRouter with a strict JSON schema, validates the result, and returns an identity-bound completion.

## Configuration

In [Aru Edge Function secrets](https://supabase.com/dashboard/project/joynqjmfvfrjudmkrfuu/functions/secrets), set `OPENROUTER_API_KEY`. Never put it in Android or Git. The function trims accidental surrounding whitespace or quotes. It also uses Supabase's built-in `SUPABASE_URL` and server-only `SUPABASE_SERVICE_ROLE_KEY`.

Optional `OPENROUTER_MODEL` and `OPENROUTER_VISION_MODEL` override the text and photo primary models, respectively. Both default to `dots-studio/dots-3-note-preview:free`. Only IDs ending in `:free` or the explicitly free `openrouter/free` router are accepted. Each request supplies the primary plus `openrouter/free` as a fallback, letting OpenRouter choose an available free model with the required text/image and structured-output support. Provider fallback is enabled; strict JSON-schema validation and zero prompt/completion price ceilings apply to all routes. There is still only one outbound API request and one budget reservation; no application retry loop or paid fallback is configured. Success logs record the actual model returned by OpenRouter without logging meal content or credentials.

## Honest estimates

This version uses model estimates, **not verified INDB, USDA, or official restaurant lookup**. Every output has `AI_ESTIMATE` provenance, review required, no fabricated citation URL, and editable portion/nutrition values. Unknown nutrients remain null; zero means zero. Missing sizes, recipes, oil/ghee and restaurant variants must be explained as assumptions. Server code creates provenance; model text cannot declare a database match. The local INDB workbook is not imported into this function. Source-grounded retrieval and accuracy benchmarks remain future work.

## Bounds and failure handling

- Text is limited to 2,000 characters. Photos are converted to JPEG, scaled to at most 1,280 pixels on the longest side, and limited to 1.5 MB before upload. Results allow at most 12 items and 4,000 output tokens. Text uses a 55-second app timeout; photo analysis uses 65 seconds.
- Per-user maximum 5 requests/minute and 40 successful or pending requests per rolling 24 hours; project maximum 40 successful or pending requests per rolling 24 hours. Failed attempts are excluded from the daily allowance, with separate guards of 20 failed attempts/user/hour and 100 failed attempts/project/hour to prevent retry storms. Reservations are serialized in Postgres so concurrent requests cannot bypass limits.
- A `(user, request ID)` reservation with a SHA-256 provider/model/payload fingerprint prevents duplicate provider calls. Completed results are cached; mismatched or in-flight duplicates are rejected. A new edit or explicit Retry creates a new request; failures do not automatically retry. Cancelling the client cannot guarantee cancellation of a provider call already running on the server, so all existing server budgets remain enforced.
- Cached food estimates and request metadata live in the unexposed `aru_private` schema, with client grants revoked and RLS default denial. Service-role-only SECURITY INVOKER RPCs reserve and complete requests. Data older than seven days is removed opportunistically on the next reservation; this is not a scheduled deletion guarantee.
- OpenRouter forwards meal text or the transient photo to a downstream model provider. Free endpoints are not guaranteed zero-retention processing, so the app shows a first-use notice and asks users to avoid faces or personal information. Neither meal text, photos, access tokens nor API keys are logged by the handler. Provider errors return safe codes; only bounded status/code fields are logged.
- Database reservation/cache is not journal cloud sync. Saved journal entries, manual corrections and saved meals remain local.

## Verification commands

`node --test supabase/tests/nutrition.test.mjs` checks text/photo request bounds, ensures photo bytes are absent from cached results, validates forged-source replacement, anonymous denial, missing-key errors, cache/idempotency states, budget denial and sanitized provider failure with mocked transport.

`npm test --prefix supabase/tests` checks migrations, owner RLS and budget RPC permissions/rate limits using PGlite. `hosted-nutrition-budget.sql` checks the actual project in a rolled-back transaction.

Android unit tests cover wire identity, AI provenance and null nutrients. The optional `LiveNutritionSmokeTest` requires an already signed-in emulator and explicit `-Pandroid.testInstrumentationRunnerArguments.runLiveNutrition=true`; it makes one free provider call and cached replays, without writing journal entries. Ordinary instrumentation skips it.

Official references: [OpenRouter structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs), [selected free model](https://openrouter.ai/nvidia/nemotron-3-super-120b-a12b:free), [OpenRouter limits](https://openrouter.ai/docs/api_reference/limits), [Supabase authorization headers](https://supabase.com/docs/guides/functions/auth-headers), [Kotlin function invocation](https://supabase.com/docs/reference/kotlin/functions-invoke).

Use a dedicated disposable emulator for Gradle connected tests: the runner may uninstall the app and clear its local session/journal. To test an existing real session, install the test APK with `adb install -r` and run the opt-in instrumentation directly; do not run the Gradle connected suite against that session.

## Current validation status — 10 October 2026

The OpenRouter handler and 40-successful-request shared rolling-day cap are deployed. Failed provider/configuration attempts no longer consume the daily allowance. After replacing the invalid Supabase secret, an authenticated Android emulator smoke test completed a real nutrition estimate successfully through OpenRouter. Earlier OpenAI insufficient-quota failures are historical and no OpenAI calls remain in the handler.

OpenRouter's ordinary free tier permits 50 free requests/day and 20/minute; usage elsewhere on the same account also counts, so the app's stricter budget cannot guarantee availability. Free endpoint availability and model quality may vary. Values remain editable AI estimates.

## Provider availability repair — 26 October 2026

Production Android and web requests were authenticated successfully but failed at OpenRouter with HTTP 404; Aru returned HTTP 503 (`provider_unavailable`). The Nemotron free model remained listed, but its only endpoint reported status -5. Dots and Liquid free structured-output endpoints reported status 0. A hard-pinned primary with fallbacks disabled made the provider outage affect both clients. The primary and bounded free-only routing above repair this shared server dependency; client reinstalls are unnecessary. Free provider availability, limits, and output quality still vary.

Deployed as `estimate-nutrition` version 22 with JWT verification retained. Verification: all 17 function tests and the database owner-access/budget suite passed. Retrying an existing failed web entry completed successfully through the production function; its success log identified `dots-studio/dots-3-note-preview:free` and a 7.2-second total duration. No Android client changes were needed; Android calls the same function and response contract. The fallback routing contract is covered by mocked tests, rather than a forced production outage.

## Shared-image scan mode (v0.11.0)

The additive `scan` mode distinguishes dish/menu/ingredients/other and uses higher image detail for readable menu text. It returns a validated dish estimate or up to 60 selectable food options; menu/ingredient scans have no nutrition total. Clients estimate only chosen items and save only after Add to Aru. Text/photo legacy contracts remain compatible. Scan requests allow 6000 output tokens and a 50-second provider timeout. Android downsamples shared photos to at most 1600 px; the website retains its 1280 px conversion. See [shared images](SHARED_IMAGES.md).
