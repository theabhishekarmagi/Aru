# First web release validation — 17 October 2026

This section records the first release. Its landing-page/sidebar layout has been replaced by the Android UI parity release below.

## Verified

- Production static build passes. npm audit reports zero vulnerabilities for the installed dependency set.
- Public Sites deployment responds with the Aru landing page. The published audience is `public`; personal records still require Supabase authentication and owner RLS.
- Live Google account selection returns to the exact web callback, establishes a real Supabase session, and opens a private empty journal. A full reload restores the session.
- A temporary meal on a separate verification date saves to `journal_entries`. A real authenticated nutrition request returns an estimate, which persists and renders after reload.
- Manual correction persists a changed calorie value, preserves zero fat and leaves a blank protein value unknown. The notebook explicitly marks incomplete daily totals.
- A temporary favourite saves to `saved_meals` and can be reused without another AI request. Daily totals update from one to two entries.
- Removal updates `deleted_at`; Undo restores the row. Hosted inspection confirms the restored revision and null deletion timestamp.
- Temporary calorie and protein goals save and are restored after reload, while other goals remain unset. All temporary journal/favourite/goal fixtures were removed after verification.
- Journal responsive checks at 320px, 390px, 768px and 1440px report matching viewport and document widths, without horizontal overflow. Phone layouts show bottom navigation, while larger layouts show desktop navigation.
- Meal details expand; preview edits require authentication; date navigation changes the selected day; the date control accepts another date.
- Web domain tests pass for unknown/zero nutrition, incomplete totals, deleted entries, leap-year/month navigation and manual bounds.
- Existing nutrition service tests and new CORS tests pass. The hosted endpoint returns HTTP 204 with the necessary preflight headers, while its user-JWT verification remains enabled.
- Existing local PGlite RLS/budget tests pass. Hosted transaction tests confirm owner access, cross-owner denial and anonymous denial, with fixtures rolled back.

## Remaining verification limits

- Responsive browser emulation was used; a physical iPhone Safari run and a real home-screen installation have not been performed here.
- Photo bounds and service behavior are covered by the existing mocked service suite. No real food-photo provider request was made during this web verification.
- The existing Android journal is local; this release does not import or synchronize those Android records.
- Google’s consent-screen publishing/test-user policy remains controlled by the project owner. If the Google OAuth app is in Testing, new friends need to be added as test users there.

The build emits informational third-party `use client` directive warnings from Lucide; these are a client-only Vite build and do not prevent successful bundling.


# Android UI parity release — 17 October 2026

The current frontend follows `AruApp.kt`, `ProfileOnboarding.kt`, `AruProfileIllustration.kt` and `InteractiveFeatureTour.kt`, rather than the earlier marketing-style web layout. Android source values are used directly: ink #242126, muted #8E898F, purple #8354EE, the cream-to-lilac paper background, Roboto text, and original artwork assets.

Implemented screens: account gate, inline journal, compact editing actions, floating totals, calendar, full settings/profile editing, four-step animated profile setup, five-step interactive tour, goals, saved meals, per-item nutrition details and corrections, meal options and camera sheet. Wider screens use the same journal with a centered maximum width, without a sidebar. No pricing page exists.

Initial checks: production build passes; five domain tests pass, including known totals within partially unknown multi-item meals. The iPhone-width account screen was visually inspected against the native screen. Live journal and responsive checks are recorded after deployment.

Live parity checks: Google session restoration opens the real first-use profile flow; skipping optional questions saves a blank profile, all five feature-tour screens navigate correctly, and completion opens the native-style journal. On a separate verification date, typing a note automatically saves it and receives a real nutrition result after the pause. Per-item manual correction saves revised calories and retains the distinction between unknown protein and zero fat. The meal-options dialog saves a temporary favourite. The original Android drawable logo (rather than the padded brand export) and the onboarding sprite sheets are reused directly. Temporary meal/favourite data is cleaned up after testing.
