# Foundation and runtime validation

Validated 8 October 2026 on macOS with Android Studio JDK 21.0.10 and Gradle wrapper 9.3.1.

## Results

- Debug APK assembled successfully: `app/build/outputs/apk/debug/app-debug.apk` (approximately 11 MB).
- `:app:testDebugUnitTest`: 13 tests, zero failures/errors/skipped.
- Tests cover mandatory/expired account sessions, persisted text across repository recreation, stale edits/retries/duplicate completions, manual correction, deletion/undo, independent saved-meal reuse, detached mutable inputs, account switching, interrupted-request recovery, partial daily totals, storage-write failures and input validation.
- Tests use an in-memory store and synthetic nutrition fixtures. They validate domain behavior and serialization snapshots; they do not prove Android filesystem durability or live provider accuracy.
- Final `:app:lintDebug` completed with zero errors and five warnings. Warnings for target SDK 36, available dependency updates and missing launcher icon are retained intentionally. The dependency pairing is pinned and tested; final icon/design is awaiting screenshots. See latest generated `app/build/reports/lint-results-debug.txt`.
- The first build exposed the Compose requirement for compile SDK 37 and an invalid `AtomicFile.exists` call. Both were corrected. SDK platform 37.0 revision 2 installed through Gradle using existing accepted SDK licenses; target SDK stays 36.
- Explicit Android 12+ cloud/device-transfer exclusions were added after lint surfaced `DataExtractionRules` guidance. Backup/transfer behavior still requires a device check.
- Gradle wrapper distribution checksum is pinned to the official SHA-256.

## Android Studio runtime follow-up

Tested 8 October 2026 using the native Android Studio app (Panda 4 / 2025.3.4 Patch 1) on the user's Mac. Opened the actual project, observed successful Gradle sync, started the existing `Pixel_10_Pro_XL` AVD through Device Manager, and used Android Studio's `Run 'app'` control to build, install, and launch Aru. The Studio build reported `BUILD SUCCESSFUL in 11s`; deployment reported successful installation.

- Device: `emulator-5554`, Android 17 / API 37, arm64, 16 KB page-size system image. This is a virtual Pixel 10 Pro XL profile, not physical Pixel hardware.
- Initial APK installation and cold launch succeeded. During initial emulator startup, a Pixel Launcher ANR dialog appeared (not an Aru dialog); choosing Wait allowed it to recover. No emulator data was wiped.
- The initial Aru title crowded the status bar. Added `safeDrawingPadding()` to the gate's Column in `MainActivity.kt`; rebuilt and visually confirmed the title and labels are below the system bars/cutout.
- Updated APK successfully ran from Android Studio's Run button, and was visibly rendered in Running Devices.
- App label checks using UI hierarchy: `Aru`, `An account is required to use Aru.`, `Sign-in is not configured yet.`. Zero clickable app nodes; no guest bypass or journal access is present.
- Background test: pressed Home through Studio's emulator toolbar. `dumpsys activity` confirmed Nexus Launcher was resumed, Aru was paused, and Aru's process remained alive.
- Foreground return: `am start -W` completed with `Status: ok`, `LaunchState: WARM`, TotalTime 1009 ms.
- Two process cold relaunches using `am start -S -W` (stops only Aru, retains its data) completed with `Status: ok`, `LaunchState: COLD`, TotalTime 4426 ms and 3675 ms. Setup gate remained correct after relaunch.
- Aru crash-buffer search returned no matching crash, lifecycle event log contains no Aru `am_crash`/`am_anr`, and latest Aru process log contains no `FATAL EXCEPTION`/`AndroidRuntime`/`ANR` match. These observations cover this short smoke-test session, not long-term reliability.
- Post-fix build and lint succeeded; lint remains zero errors/five existing warnings. Domain code was unchanged; the earlier 13 unit-test result remains the relevant domain validation.

Evidence under `artifacts/runtime/`:
- `aru-tested.png`: final screenshot, 1344 × 2992, inspected after relaunch.
- `aru-gate.png`: pre-fix screenshot showing title close to status bar.
- `aru-launch-before.png`: initial screen with the emulator's Pixel Launcher ANR dialog.
- `aru-window.xml`: inspected final UI hierarchy.
- `background-state.txt`, `foreground-return.txt`, `cold-relaunch-1.txt`, `cold-relaunch-2.txt`: lifecycle evidence.
- `aru-process-log.txt`, `aru-lifecycle.txt`, `aru-crashes.txt`: runtime logs; crash file is empty because no Aru crash matched.
- `build-after-insets.txt`: successful post-fix build/lint output.

Android Studio and the existing emulator are left open with Aru running for user inspection. No unrelated app data was removed or emulator state wiped.

## Limits

Runtime smoke tests cover only the setup gate on this API 37 emulator. No physical-device validation, full accessibility audit, instrumentation suite, real auth flow, food-journal screen, provider call, backend authorization, INDB import or accuracy benchmark was performed. This APK is not a usable end-to-end food tracker.

The system `/usr/bin/java` is Java 26; validation explicitly used Android Studio's JDK 21. Set a supported JDK before running the wrapper. No commit, push, release signing, deployment or publishing was performed.


## Amy reference UI — 8 October 2026

Implemented the notebook design after reviewing the website's muted 23.5-second intro and its home, editing, and goals screenshots. Reference decisions and scope adaptations are in `AMY_DESIGN.md`.

Final command: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` using Android Studio's JDK 21 and the existing SDK.

- Build successful; 13 JVM repository tests passed.
- All 5 instrumentation tests passed on the Pixel 10 Pro XL / API 37 / 16 KB emulator.
- Instrumentation covers mandatory account gating, persisted text, delete/undo, nutrition details, saved-meal creation/reuse with updated totals, manual nutrient edits, editable goals, and cancellation of pending estimates after manual corrections.
- UI data and account sessions are synthetic and isolated in the instrumentation APK; they are not a production guest path.
- Lint: zero errors, five existing warnings (target SDK, Gradle/Activity/serialization versions, missing launcher icon).
- Captured and visually inspected `aru-account.png`, `aru-journal.png`, `aru-nutrition.png`, `aru-saved-meals.png`, and `aru-goals.png` under `artifacts/runtime/`.
- Fixed keyboard save behavior with an IME Done action, explicit resize behavior, and keyboard-aware form tests. Fixed the debounced-estimation/manual-correction race with cancellation and a revision check.
- Test library compatibility was resolved using AndroidX Test runner 1.7.0 / Espresso 3.7.0. Final passing log: `artifacts/runtime/amy-ui-final-validation.txt`.

Limits: no real authentication or AI endpoint is configured, no live nutrition accuracy has been evaluated, and physical-device, localization, screen-reader, large-font, and broader device testing remain. Normal launches deliberately show the account-required screen. Reference media is excluded from APK assets.


## Supabase integration preparation — 9 October 2026

- Android debug build, 13 repository JVM tests, and lint passed with the Supabase SDK integration. Logs are local-only under `artifacts/runtime/`.
- Local PGlite database tests passed for owner access, cross-account read/update/reassignment rejection, anonymous rejection, and invalid goal values. The SQL migration is prepared but has not been applied to the hosted Aru project.
- Added instrumentation coverage for encrypted session storage/restoration and a Google button that cannot bypass authentication. A test method return-type issue was fixed and the test APK compiled. The subsequent targeted run could not execute because the emulator had disconnected (`No connected devices`). These two new tests remain unverified on-device.
- Existing journal UI tests passed before this auth integration. Real Google sign-in, hosted RLS/advisors, cloud sync, and nutrition processing remain pending project/provider configuration and live checks.
- Initial GitHub import excludes local credentials/configuration, build outputs, APKs, runtime logs/screenshots, node_modules, and downloaded third-party media/workbooks. Source references and workbook checksum remain documented.

## Hosted Supabase deployment — 9 October 2026

- Applied all three migrations to Aru (`joynqjmfvfrjudmkrfuu`); local versions match the hosted migration ledger.
- All three tables passed hosted transaction tests for owner read/update, cross-owner read/update/reassignment rejection, and anonymous denial. Synthetic auth users and rows were rolled back; a follow-up count confirmed zero remaining fixtures.
- Actual unauthenticated REST requests with the public publishable key returned HTTP 401 / SQLSTATE 42501 on all three tables. Real-user JWT REST isolation remains pending Google configuration; hosted role tests are not an end-to-end login test.
- Security and performance advisors returned zero findings. Revoked client execution on the pre-existing automatic-RLS event helper without removing its event trigger.
- Local PGlite tests now apply every migration in order and passed.
- Project URL and publishable key were added to ignored local.properties. Google provider and anonymous sign-in are both currently disabled according to public Auth settings. Web client ID and provider credentials are still missing.
- Debug build, repository unit tests, and lint passed after setting the live public configuration. No new emulator test or real Google login was performed in this deployment pass.
- Cloud synchronization and nutrition Edge Functions are not implemented/deployed. The journal remains account-scoped local storage after authentication.

## Google public configuration — 9 October 2026

- Stored the user-supplied Web OAuth client ID in ignored `local.properties`, preserving SDK and Supabase configuration. No secret or local configuration is committed.
- Public Supabase Auth settings confirmed Google is enabled.
- Debug build, JVM tests, and lint passed (`artifacts/runtime/google-config-build.txt`).
- Both `SupabaseAuthTest` instrumentation tests passed on Pixel 10 Pro XL / API 37: configured Google button does not bypass the account gate; encrypted session round-trip and deletion work. These tests use synthetic credentials, not real Google accounts.
- Initial run stalled while the emulator was starting and was terminated. Retry after boot passed in 26 seconds (`artifacts/runtime/google-auth-device-retry.txt`).
- Real Google token exchange, refresh, and account switching remain unverified. Android OAuth client registration must use package `com.aru.journal` and the debug fingerprint in the setup guide.

## Nutrition feature — 9 October 2026

- Deployed `estimate-nutrition` v2 with platform JWT verification plus Auth user validation, and migration `20261009130534_nutrition_request_budget`.
- Eight deterministic backend tests passed; local PostgreSQL tests passed for RLS, privileged RPC access, duplicate/cache behavior, minute and daily budgets. Hosted budget tests passed inside a rolled-back transaction.
- Requests without authentication and with an invalid bearer token returned HTTP 401 from the deployed endpoint.
- Android build/lint passed, 15 JVM tests passed, and all eight emulator regression tests passed (API 37). New coverage verifies edits do not call the estimator, explicit calculation updates totals, and repeated taps cannot create duplicate in-flight calculations.
- Live smoke testing used an existing real Supabase session, reached the OpenAI provider, and initially returned HTTP 429 (`provider_busy` before detailed quota classification was added). No successful provider estimate was obtained in that initial test. A subsequent instrumentation process terminated before returning its assertion result.
- The Gradle connected-test runner uninstalled the app on cleanup. Reinstalled the configured APK. Future real-session smoke tests use direct instrumentation, avoiding Gradle uninstall cleanup.
- Security advisor: private budget table has intentional RLS default-deny/no client policies; service role alone has grants. A password-leak protection warning is unrelated to the Google-only login path and was not changed. [Supabase explanation](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). Performance advisor initially reported the new timestamp index unused; it is retained for budget windows and cleanup queries.
- AI nutrition values are not source-database verified. INDB/USDA/menu retrieval, accuracy benchmarking and cloud journal sync remain outside this implementation.

## Phone APK and final diagnostic — 10 October 2026

- Fixed the Supabase/Ktor default 10-second timeout for nutrition calls: HTTP request/socket timeouts are 50 seconds, with the existing 55-second outer coroutine timeout. Transport timeout errors now map to the app's safe timeout message.
- Debug build, 15 JVM tests and lint passed after the fix. Existing eight emulator regression tests passed on the preceding feature revision; no Gradle connected suite was rerun against the restored real session.
- Deployed Edge Function v3 with allowlisted status/type/code diagnostics only (no prompts, keys, tokens or raw provider error messages). A real-session request returned `provider_quota`, emitted only for OpenAI's `insufficient_quota` code/type. This confirms a quota/billing restriction rather than a temporary request-rate classification. The specific credit/spend-limit subtype was not retained, so check the key's project/organization billing and limits before retrying. No successful nutrition result was obtained; further paid attempts stopped.
- Final signed debug APK: `artifacts/apk/Aru-debug-2026-10-10.apk`, package `com.aru.journal`, min SDK 26 (Android 8), debug certificate SHA-1 matches the registered OAuth client. APK signature verified and final APK installed successfully with `adb install -r` on the emulator.
- SHA-256: `ca3ad9710443d800d02d6434a69437d1c835f1a37f7cf3052f987287a3c62b13`. APK and checksum remain local, excluded from Git. Provider keys remain on Supabase; APK secret-pattern scan found no OpenAI or Supabase secret keys.

## Automatic nutrition after typing — 10 October 2026

- Per the user's updated preference, every text edit saves immediately and schedules one estimate after 1.8 seconds of quiet. Further edits cancel the previous delay/in-flight client job. Blank text, manual corrections and deletion cancel pending work; revision/request identity checks still reject stale results.
- Removed Calculate/Recalculate buttons from the normal flow. Failed entries retain Retry; failures never start an automatic retry loop. The quota message explicitly points to the API key's OpenAI project billing/usage limits or a key with available quota. This UX change does not resolve OpenAI quota.
- Build/lint passed and all 19 JVM tests passed. Four new controller tests cover rapid edits producing one call, in-flight cancellation/blank input, quota failures staying idle until Retry, and manual/deleted entries cancelling scheduled estimates.
- Two focused emulator tests passed using isolated synthetic estimators: automatic totals after a typing pause and cancellation on manual correction. Direct instrumentation preserved the installed app. No live OpenAI requests were made for this change.
- Rebuilt `artifacts/apk/Aru-debug-2026-10-10.apk`, verified its signature, and installed it successfully on the emulator. Current SHA-256: `0fcaad26fef020a6cd3cf477dc34d3480a4e23f2e93139d929c83cf36f59ea6a`; the adjacent `.sha256` file was refreshed. Earlier hashes in this document describe earlier builds.

## OpenRouter migration — 10 October 2026

- Deployed `estimate-nutrition` version 5 with JWT verification, OpenRouter chat completions, JSON schema validation, default `nvidia/nemotron-3-super-120b-a12b:free`, and server-only `OPENROUTER_API_KEY`. Non-free model IDs are rejected; prompt/completion price ceilings are zero and provider fallbacks disabled.
- Deployed `estimate-nutrition` version 8 with bounded provider diagnostics, distinct 401/403 handling, and normalization of accidental whitespace or matching quotes around the OpenRouter secret. Before replacement, a real authenticated emulator request received OpenRouter HTTP 401 (`provider_credentials`), proving the saved secret was invalid; the OpenRouter workspace also showed the original Aru key as never used.
- Replaced the invalid Supabase OpenRouter secret and reran `LiveNutritionSmokeTest` on the Android emulator. The real authenticated request completed successfully in 17.6 seconds (`OK (1 test)`), proving the Android → Supabase Auth → Edge Function → OpenRouter → validated nutrition path works end to end.
- Rotated the replacement credential once more after validation, updated the Supabase secret without persisting the key locally, and reran the same live test successfully in 27.3 seconds. Removed the original invalid and temporary OpenRouter keys; only `Aru Supabase Final` remains.
- Applied hosted migration `20261010102457_free_nutrition_budget`: global cap 40 requests per rolling 24 hours, including failures. Existing per-user 5/minute and 40/day caps remain. PGlite now verifies a second user cannot bypass the global cap.
- Passed 10 mocked backend tests, PGlite migration/RLS/budget tests, Android JVM tests, and debug APK compilation. A live authenticated request was attempted and isolated the remaining failure to the invalid OpenRouter credential.
- Live smoke test now permits one model call plus a cached replay. The previous OpenAI secret has not been deleted; remove it after successful OpenRouter validation.
- Rebuilt debug-signed APK: `artifacts/apk/Aru-debug-2026-10-10.apk`; SHA-256 `872e660d060521d56a3839312eb4bcc092297104e4497d01d76d8095e0dc6ac9`. APK remains ignored by Git.

## Dictation, saved-meal actions, and transient photo analysis — 11 October 2026

- Focused journal entries now show a keyboard-adjacent compact bar with calories remaining/current calories, Android speech dictation, one-tap saved meals, and an in-app CameraX meal camera. Saved meals reuse their stored nutrition without another provider request.
- The camera opens as a compact rounded sheet over the journal, with close, lens switch, fast shutter, and system photo-picker actions. Captured or selected photos animate into a temporary composer preview while analysis runs. Camera output is kept in the app cache only until it is scaled to at most 1,280 px, compressed below 1.5 MB, and submitted. The cache file is deleted in a `finally` block. Aru persists only the returned description and validated nutrition estimate.
- Deployed `estimate-nutrition` version 15 with transient photo handling. The default photo model is `google/gemma-4-26b-a4b-it:free`; `OPENROUTER_VISION_MODEL` can select another free structured-output vision model. The text route remains on Nemotron. A first-use notice explains external AI processing and advises against faces or personal information.
- Backend tests verify bounded JPEG-only requests, structured image messages, authentication, and that image bytes are not included in the completed database result. Android tests cover derived-only photo persistence, controller routing without a text estimate, compact action visibility, saved-meal insertion, and opening/closing the CameraX screen.
- Emulator screenshots were visually inspected at `artifacts/screenshots/aru-compact-input.png`, `artifacts/screenshots/aru-meal-camera.png`, and `artifacts/screenshots/aru-photo-attached.png`. The Pixel emulator camera feed, compact sheet, all controls, and temporary attachment preview rendered correctly.
- Dictation uses the device speech recognition service and was validated at the UI/permission integration level; microphone audio recognition accuracy was not benchmarked. The deployed photo route could not be exercised with a real authenticated personal session during automated tests, so model accuracy remains unmeasured and every result remains editable with confidence/review treatment.
- Built and signature-verified `artifacts/apk/Aru-debug-2026-10-11-inline-camera.apk`; SHA-256 `8cbce99a8cee425441c31eb38a0df3b160d2ffdc0867dc6a258678649b69f083`.
- Corrected the hosted nutrition budget after diagnostics found 40 requests in the rolling window: 20 successful and 20 historical provider failures. Daily limits now count only pending and completed work, while separate hourly failure limits prevent retry storms. A rolled-back live reservation probe returned `reserved` with 20 counted daily requests.
