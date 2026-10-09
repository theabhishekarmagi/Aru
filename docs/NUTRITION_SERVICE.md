# Nutrition calculation

Aru sends a food description to the authenticated `estimate-nutrition` Supabase Edge Function only when Calculate/Recalculate is tapped. Editing saves locally without triggering paid inference. The function verifies the Supabase user with Auth, rejects anonymous users, reserves a request budget, calls OpenAI Responses with a strict JSON schema, validates the result, and returns an identity-bound completion. The app rejects mismatched request/revision completions and persists accepted estimates in its account-scoped local journal.

## Configuration

In [Aru Edge Function secrets](https://supabase.com/dashboard/project/joynqjmfvfrjudmkrfuu/functions/secrets), set `OPENAI_API_KEY`. Never put it in Android, Git, or chat. The user reported saving this secret. The function also uses Supabase's built-in `SUPABASE_URL` and server-only `SUPABASE_SERVICE_ROLE_KEY`.

Optional `OPENAI_NUTRITION_MODEL` overrides the default `gpt-4.1-mini-2025-04-14`. Use a model supporting Responses structured outputs and verify its cost/behavior before changing. No OpenAI SDK dependency is needed: the function uses the HTTPS API directly.

## Honest estimates

This version uses model estimates, **not verified INDB, USDA, or official restaurant lookup**. Every output has `AI_ESTIMATE` provenance, review required, no fabricated citation URL, and editable portion/nutrition values. Unknown nutrients remain null; zero means zero. Missing sizes, recipes, oil/ghee and restaurant variants must be explained as assumptions. Server code creates provenance; model text cannot declare a database match. The local INDB workbook is not imported into this function. Source-grounded retrieval and accuracy benchmarks remain future work.

## Bounds and failure handling

- Maximum 2,000 input characters, 12 items, 4,000 output tokens; provider timeout 30 seconds, app timeout 55 seconds.
- Per-user maximum 5 requests/minute and 40/rolling 24 hours; project maximum 200/rolling 24 hours. Reservations are serialized in Postgres so concurrent requests cannot bypass limits.
- A `(user, request ID)` reservation with a SHA-256 payload fingerprint prevents duplicated paid calls. Completed results are cached; mismatched or in-flight duplicates are rejected. Failed requests stay charged. Explicit Retry creates a new request; there is no automatic provider retry.
- Cached food estimates and request metadata live in the unexposed `aru_private` schema, with client grants revoked and RLS default denial. Service-role-only SECURITY INVOKER RPCs reserve and complete requests. Data older than seven days is removed opportunistically on the next reservation; this is not a scheduled deletion guarantee.
- Provider requests use `store:false`; this does not itself guarantee zero provider retention. Neither prompts, access tokens nor API keys are logged by our handler. Provider errors return safe codes.
- Database reservation/cache is not journal cloud sync. Saved journal entries, manual corrections and saved meals remain local.

## Verification commands

`node --test supabase/tests/nutrition.test.mjs` checks request/output validation, forged-source replacement, anonymous denial, missing-key errors, cache/idempotency states, budget denial and sanitized provider failure with mocked transport.

`npm test --prefix supabase/tests` checks migrations, owner RLS and budget RPC permissions/rate limits using PGlite. `hosted-nutrition-budget.sql` checks the actual project in a rolled-back transaction.

Android unit tests cover wire identity, AI provenance and null nutrients. The optional `LiveNutritionSmokeTest` requires an already signed-in emulator and explicit `-Pandroid.testInstrumentationRunnerArguments.runLiveNutrition=true`; it makes two paid provider calls and cached replays, without writing journal entries. Ordinary instrumentation skips it.

Official references: [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses), [GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [Supabase authorization headers](https://supabase.com/docs/guides/functions/auth-headers), [Kotlin function invocation](https://supabase.com/docs/reference/kotlin/functions-invoke).

Use a dedicated disposable emulator for Gradle connected tests: the runner may uninstall the app and clear its local session/journal. To test an existing real session, install the test APK with `adb install -r` and run the opt-in instrumentation directly; do not run the Gradle connected suite against that session.
