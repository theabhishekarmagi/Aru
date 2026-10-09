# Aru Android foundation

Native Kotlin/Jetpack Compose implementation for a private food-journal test app. The Amy-style interface includes a notes editor, date navigation, daily totals/goals, nutrition details, manual correction, source links, saved meals, and delete/undo. See [design reference](docs/AMY_DESIGN.md).

Supabase database tables and owner-only access policies are deployed. Android Google sign-in is implemented; Google OAuth provider credentials are still required. Without configuration, normal launches show the account-required screen. The AI backend and cloud sync remain pending. See [Supabase setup](docs/SUPABASE_SETUP.md). UI instrumentation uses test-only sessions and isolated storage; no guest login or fabricated live nutrition is included.

See [requirements](docs/REQUIREMENTS.md) and [reference-data notes](docs/REFERENCE_DATA.md).

## Build

Install Android SDK platform 37 (required by the current Compose BOM), build tools 36, and use JDK 17 or Android Studio's bundled JDK 21. Set `ANDROID_HOME` to your SDK directory, or create untracked `local.properties` with `sdk.dir=...`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Dependencies are pinned using official primary documentation checked 8 October 2026:
- [AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes), built-in Kotlin 2.2.10, Gradle 9.3.1.
- [Compose BOM 2026.09.00](https://developer.android.com/develop/ui/compose/bom), matching Kotlin Compose compiler plugin 2.2.10.
- [Activity Compose 1.12.2](https://developer.android.com/jetpack/androidx/releases/activity).
- [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.9.0) 1.9.0.

Minimum SDK 26 is a provisional foundation choice (java.time support); target SDK is 36 and compile SDK is 37. Package `com.aru.journal` is provisional and must be reviewed before public distribution.

## Code boundaries

- `auth/`: Supabase Google sign-in, encrypted session storage, and the account-session boundary.
- `domain/`: portions, sources, nullable nutrition, journal state, goals, estimation boundary, and repository invariants.
- `data/`: atomic account-scoped JSON persistence. Use one repository/store instance and perform calls on an IO dispatcher. Future multi-process/sync use requires a transactional database or additional coordination.
- `ui/`: Amy-style Compose screens and an ordered IO controller.
- `MainActivity`: lifecycle-owned controller, mandatory account gate, and app entry point.
- `references/`: official workbook and user-requested Amy design references, outside APK assets.

Journal state includes recent deleted entries for undo. Full deletion/export and encrypted storage are future integration work. Backend must enforce ownership regardless of client checks. No provider secrets belong in this repository or APK.

## Emulator UI tests

Use a dedicated test emulator (instrumentation may install/uninstall the app):

```sh
./gradlew :app:connectedDebugAndroidTest
```

Instrumentation fixtures use an isolated test session and storage. Screenshots are copied to `/data/local/tmp/aru-*.png` on the emulator. Test dependencies use AndroidX Test runner 1.7.0 and Espresso 3.7.0, including the [InputManager compatibility fix](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).
