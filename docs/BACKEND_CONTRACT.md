# Supabase backend integration contract

Supabase is selected for the backend and Postgres database, with Google sign-in through Supabase Auth. Local auth code and a tested SQL migration are prepared. Project provisioning/configuration, cloud synchronization, and the nutrition Edge Function remain pending; this document does not describe working deployed endpoints.

## Authentication

Use the chosen real SDK to obtain a session. Backend verifies signature, issuer, audience, expiry and ownership on every request. Derive account identity from the verified token, not the client-supplied `accountId`. Do not store tokens in the journal JSON or emit them in logs. Clear UI state and cancel jobs on logout/account switch.

## Estimate request/response

The Android domain exposes `EstimateRequest`: stable entry ID, account ID (local correlation only), text revision, unique request ID, original text, journal date and timezone. `NutritionEstimator` is the suspend integration boundary.

Backend response maps to `NutritionEstimate`: matched items, each consumed portion, nullable five-nutrient values, references and basis/version/market metadata, assumptions, a brief explanation, review flag, and timestamp. Do not return invented provenance or model reasoning as evidence. Structured validation must reject nonfinite/negative nutrients and unreviewed assumed portions.

The journal repository accepts a completion only while account, entry ID, revision, request ID and calculating status still match. A response for an old edit/retry/deleted entry is ignored. Manual correction clears the request and advances revision.

## Scheduler requirements

- Persist text first. Explicitly queue calculation after meaningful editing completion; do not fire paid inference on every keystroke.
- Run local disk IO off the main thread using one ordered repository instance.
- Restore interrupted calculations to queued state after process restart. Queue is persisted locally, but no background worker exists yet.
- Obtain a fresh valid session before processing a request. Pause signed-out work; do not invent guest identity.
- Catch transient transport/provider failures with bounded retries and safe error codes. Never log meal text, prompts, access tokens or provider keys.
- Cancellation leaves/requeues resumable work; cancellation must not apply a late result. Route outcomes through repository matching checks.
- Backend authenticates and rate limits requests, handles idempotency by authenticated user + request ID, and limits abuse/cost.

## Nutrition resolution

Normalize spelling/aliases while preserving original input. Identify restaurant market/item/size and quantities before matching. Resolve Indian dishes against versioned INDB records, general ingredients against USDA, and restaurant items against official local-market nutrition. Missing detail yields an explicit editable assumption or clarification. Missing fiber or serving information stays unknown.

Provider and USDA/search credentials live in server environment/secrets storage. Android receives only the authenticated endpoint configuration and user session tokens. BuildConfig and APK resources must never include service API secrets.
