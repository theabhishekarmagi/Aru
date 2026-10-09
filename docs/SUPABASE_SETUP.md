# Supabase setup for Aru

Confirmed by the user: Supabase backend and Postgres database, with Google sign-in through Supabase Auth. Android stays Kotlin/Compose. Server-side nutrition processing is intended for Supabase Edge Functions; the AI provider is still to be selected/configured.

## Prepared locally

- Native Android Credential Manager Google sign-in exchanges a Google ID token for a Supabase session, using a fresh nonce for each attempt.
- Supabase SDK handles token refresh. Access/refresh tokens are encrypted using Android Keystore and kept in no-backup storage.
- Account-specific repository instances prevent queued writes from crossing account changes. Sign-out is available in Settings.
- The app exposes only public project configuration: URL, publishable key, and Google web client ID. See `config/supabase.properties.example`.
- A generated SQL migration defines journal entries, saved meals, and nutrition goals. Owner-based RLS and explicit grants apply to all three tables. Anonymous access is denied. The migration has local PGlite coverage, but has NOT been deployed or checked with hosted Supabase advisors.

## Confirmed project

Project name: **Aru**. Project ref: `joynqjmfvfrjudmkrfuu`. Project URL: `https://joynqjmfvfrjudmkrfuu.supabase.co`. Region: `ap-northeast-1`. It was verified as `ACTIVE_HEALTHY`. No hosted schema or function deployment has been performed by this checkout.

## Required project setup

1. Use the confirmed Aru project and obtain its publishable key from project settings. Place those public values in ignored `local.properties`, along with `GOOGLE_WEB_CLIENT_ID`.
2. Configure Google's consent screen for Aru, with test users while the project is in testing.
3. Create a Google Web OAuth client. Its client ID is the Android Credential Manager server client ID. Configure Google's authorized redirect URI using the exact callback shown in Supabase's Google provider settings.
4. Create a Google Android OAuth client for package `com.aru.journal` and the debug signing certificate SHA-1 (`./gradlew :app:signingReport`). Register release signing separately before distribution.
5. Enable Google in Supabase Auth. Register the web and Android client IDs, web first. Store the Google client secret in Supabase provider configuration, never in Android. Keep nonce checking enabled and anonymous sign-in disabled.
6. Apply the migration to the selected development project, run the Supabase security/performance advisors, then verify account A cannot select/update account B's rows through the actual Data API.
7. Rebuild the app and test real Google sign-in, token refresh, process restart, sign-out, and account switching.

The provider setup follows [Supabase Google Auth documentation](https://supabase.com/docs/guides/auth/social-login/auth-google) and the [native ID-token exchange](https://supabase.com/docs/reference/kotlin/auth-signinwithidtoken).

## Remaining integration

Cloud synchronization and the nutrition Edge Function are not wired yet. The app continues using its account-scoped local journal store after sign-in; it does not claim to upload or restore journal records from Supabase. Sync needs a durable outbox, optimistic concurrency checks, timestamps managed on the server, and conflict handling before enabling multiple devices. Nutrition requires the source-data import and configured AI provider.

Supabase Kotlin SDK 3.2.6 and Ktor 3.3.1 are pinned for compatibility with this project's Kotlin 2.2 toolchain; newer SDK releases require a separate toolchain upgrade. Database test dependency versions are locked in `supabase/tests/package-lock.json`. The Supabase CLI used to generate the migration was 2.120.0.

## Validation

Run `npm ci --prefix supabase/tests && npm test --prefix supabase/tests` for local PostgreSQL RLS tests. The harness stubs Supabase's auth schema and claims; it is not an end-to-end Supabase deployment test.

Run the Android build/unit/lint checks and instrumentation tests on a dedicated emulator. Tests use synthetic credentials only. Real Google/Supabase authentication cannot be confirmed without a configured project.
