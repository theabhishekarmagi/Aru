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
