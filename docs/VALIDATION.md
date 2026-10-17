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
- Deployed `estimate-nutrition` version 15 with transient photo handling. After OpenRouter returned HTTP 404 because the original Gemma endpoint could not satisfy the strict structured-output request, the default photo model was changed to `dots-studio/dots-3-note-preview:free`, which advertises image input and structured-output support in OpenRouter's live catalog. `OPENROUTER_VISION_MODEL` can select another compatible free model. The text route remains on Nemotron. A first-use notice explains external AI processing and advises against faces or personal information.
- Backend tests verify bounded JPEG-only requests, structured image messages, authentication, and that image bytes are not included in the completed database result. Android tests cover derived-only photo persistence, controller routing without a text estimate, compact action visibility, saved-meal insertion, and opening/closing the CameraX screen.
- Emulator screenshots were visually inspected at `artifacts/screenshots/aru-compact-input.png`, `artifacts/screenshots/aru-meal-camera.png`, and `artifacts/screenshots/aru-photo-attached.png`. The Pixel emulator camera feed, compact sheet, all controls, and temporary attachment preview rendered correctly.
- Dictation uses the device speech recognition service and was validated at the UI/permission integration level; microphone audio recognition accuracy was not benchmarked. The deployed photo route could not be exercised with a real authenticated personal session during automated tests, so model accuracy remains unmeasured and every result remains editable with confidence/review treatment.
- Built and signature-verified `artifacts/apk/Aru-debug-2026-10-11-inline-camera.apk`; SHA-256 `8cbce99a8cee425441c31eb38a0df3b160d2ffdc0867dc6a258678649b69f083`.
- Corrected the hosted nutrition budget after diagnostics found 40 requests in the rolling window: 20 successful and 20 historical provider failures. Daily limits now count only pending and completed work, while separate hourly failure limits prevent retry storms. A rolled-back live reservation probe returned `reserved` with 20 counted daily requests.
- Added compact meal summaries to the existing structured nutrition response and deployed Edge Function version 18. The full original description remains the editable source, summaries are normalized and capped at 160 characters, journal rows display at most two lines, and a pulsing “Summarizing meal…” transition appears before the summarized label. The Android UI test captured the settled result at `artifacts/screenshots/aru-summarized-entry.png`.
- Added persisted meal creation times, a working Nutrition Details overflow menu for saving or deleting meals, and per-day blank-draft deduplication. Instrumentation verifies that the `+` saved-meal flow reuses stored nutrition without calling the estimator and that only one “Write what you ate…” row remains visible.
- Built, v2-signature-verified, and emulator-installed version `0.4.0-journal-actions` as `artifacts/apk/Aru-debug-latest.apk`; SHA-256 `43e3ed389178b1e4c5d27ea402b0f52ace988f219791d86f89242d7b94621fbf`.
- Replaced the hand-drawn Google mark with Google’s pre-approved light pill asset from the official Sign in with Google branding bundle, and removed the duplicate photo-analysis progress row. Production Edge Function v18 logs showed two successful photo requests averaging 16,192 ms versus three text requests averaging 5,333 ms; the image requests were only 268–339 KB, identifying free vision-model inference as the dominant delay. Photo input is now capped at 960 px/80% JPEG quality and sent at low image detail with a smaller completion budget; Edge Function version 19 emits content-free stage timing for future measurements.
- Passed 15 backend tests, Android JVM/lint/build checks, the 11-test emulator suite (one authenticated live smoke test skipped), focused Google-auth and photo-attachment instrumentation, signature verification, and emulator installation. The complete emulator run also exposed and verified a fix that keeps `SpeechRecognizer` creation and destruction on Android's main thread. Built version `0.4.1-photo-flow` as `artifacts/apk/Aru-debug-latest.apk`; SHA-256 `693c1d156fc7ce735a26e09c2f4624dcbb8ac0fb6f2e0890514ca611eed58752`.
- Deployed Edge Function version 20 with compact summary instructions and deterministic dish-name fallback for verbose model output. Android also derives compact labels from estimated item names, so legacy meals without a stored summary settle to one line after the summarizing animation. The header now displays the selected day's non-empty meal count beside a filled settings icon. Passed 16 backend tests, JVM/lint/build checks, and the complete 11-test emulator suite with one authenticated live smoke test skipped. Built, signature-verified, and emulator-installed version `0.4.2-compact-journal`; SHA-256 `ce22ae4cd82b2b75c6bbe84de20f8ec02624573e5367810af09938ceafb0dc7f`.

## Optional profile and interactive onboarding — 12 October 2026

- Added a four-step, animated profile setup after first sign-in for name, date of birth, height and weight. Every question and the entire flow can be skipped. Completion is persisted independently from the feature tour, so either flow resumes correctly after an interrupted launch.
- Added a five-page animated feature tour for text logging, dictation, photo analysis, saved meals and daily nutrition. It supports Next, Previous and Skip, and can be replayed from Settings.
- Replaced the journal settings menu with a full Settings page. It displays optional profile values, allows later edits, and retains links to daily goals, saved meals, replaying the tour and sign-out.
- Applied hosted Supabase migrations `20261011185202`, `20261011195521` and `20261011200310` for `public.user_profiles`. Owner-only RLS is enabled, anonymous access is rejected, field bounds are enforced, and performance advisor findings introduced by the policy were reduced to zero. The existing private nutrition-request default-deny notice and leaked-password-protection warning are unchanged.
- Passed 24 Android JVM tests, 16 backend/RLS tests, lint, APK assembly, and the complete emulator suite: 12 tests passed and the authenticated live-provider smoke test was skipped. The new journey covers optional entry, skipped DOB, profile persistence, tour navigation in both directions, Settings display, and later editing.
- Visually inspected `artifacts/screenshots/aru-profile-name.png`, `aru-feature-tour.png`, and `aru-settings-profile.png` on Pixel 10 Pro XL / API 37.
- Built version `0.5.0-onboarding` (`versionCode 6`) as `artifacts/apk/Aru-debug-v0.5.0-onboarding.apk`, verified its APK Signature Scheme v2 signature, and installed it successfully on the emulator. SHA-256: `cdf5a90fdd2b7d5cbadaaaeaf91bc96b4057ad54cded7bc75a54404e007d64d9`.
## Animated profile illustrations — 16 October 2026

- Added four local Aru sprite sequences for name, birthday, height and weight. Each plays once, holds the final pose, and supports tap-to-replay. Android's disabled-animation setting shows the final pose without motion. These are four-pose illustrations with short crossfades, not rigged character animations.
- Preserved optional profile entry and skipping, and made question content scrollable on constrained screens. No backend changes or runtime AI requests are involved.
- Passed Android JVM tests, lint, and all 11 Journal UI instrumentation tests. After correcting a sprite-frame boundary, reran the two focused animation/profile tests successfully. Tests check pose transitions, rendered pixel changes, replay, reduced motion, navigation and skipping.
- Visually checked all four themed emulator screenshots under `artifacts/screenshots/aru-illustration-{name,birthday,height,weight}.png`.
- Built version `0.5.1-aru-illustrations` (versionCode 7), verified the debug APK signature, and installed `artifacts/apk/Aru-debug-v0.5.1-illustrations.apk` on the Pixel 10 Pro XL / API 37 emulator. The adjacent `.sha256` file records the checksum.

## Automatic illustration playback and complete replay — 16 October 2026

- Replaced the four-pose artwork with eight-pose transparent atlases for every profile scene. Animations now automatically repeat six-second cycles, pause in the background, and restart when the activity resumes. Reduced motion still presents the final pose.
- Settings now offers Replay onboarding: a read-only preview of all four illustrated profile pages followed by the existing feature tour. Previous crosses the profile/feature boundary, and Skip exits without writing profile data.
- Passed JVM tests and lint. Focused emulator tests verified all eight poses play without taps, automatic second cycles, rendered image changes, optional tap restart, reduced motion, and restart after backgrounding. The profile journey test passed navigation, replay, skip and zero persistence writes during replay; the optional-question skipping test also passed.
- Visually inspected `artifacts/screenshots/aru-replay-name.png` and `aru-replay-weight.png`. The replacement artwork, profile values and preview controls render without clipping.
- Built and signature-verified version `0.5.2-animated-onboarding` (versionCode 8) as `artifacts/apk/Aru-debug-v0.5.2-animated-onboarding.apk`; checksum is in its adjacent `.sha256` file.

## Hands-on feature onboarding — 17 October 2026

- Replaced the five static feature cards with working controls. The text step has an example placeholder and persists a stable journal-entry ID on edits, then uses the existing debounced nutrition calculation. Leaving or revisiting the step does not duplicate the entry or submit the example as food.
- Renamed the voice step to Dictate your meal. The page requests microphone permission on first entry; the user starts recording explicitly. Partial/final transcripts populate an editable field. Add to journal saves one entry and calculates it. Speech stops when leaving the page or backgrounding the app.
- The photo page requests camera permission on first entry and exposes the existing compact CameraX capture and photo picker, first-use processing notice, transient photo preview, and real journal-result path. Photos are not persisted in journal data. Camera/gallery operations remain optional.
- The saved-meal + control pulses repeatedly until a meal is saved, then displays confirmation. Saved meals can be inserted into today's journal without estimation. The daily nutrition bar pulses twice per tour session, opens a sheet containing calories, protein, carbs, fat and fiber, and reports incomplete/pending totals.
- Passed JVM tests, lint and APK assembly. Emulator instrumentation passed the complete profile/replay journey, the new text/dictation/photo/save/reuse/totals journey, and a separate fresh-permission test that observed and declined both real Android permission dialogs. Revisiting pages did not prompt again, and Skip remained usable. A repository regression test covers stable draft IDs across edits and store reloads.
- Tests exercised real emulator camera capture and permission dialogs; nutrition responses and speech transcripts were synthetic test fixtures. Live provider nutrition quality and microphone transcription accuracy were not re-benchmarked. No server deployment or schema change was required.
- Reviewed five screenshots in `artifacts/screenshots/aru-tour-{write,dictate,camera,saved,totals}.png`. These isolated feature tests use the standard Material test theme; the production app retains Aru's existing theme and gradient.
- Built version `0.6.0-interactive-tour` (versionCode 9) as `artifacts/apk/Aru-debug-v0.6.0-interactive-tour.apk`; checksum stored alongside it.
