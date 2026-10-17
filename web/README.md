# Aru for the web

A responsive web counterpart to the native Android food journal. It uses the existing Aru Supabase project and Google provider. The site opens with the same account gate as the Android app; personal journal access requires a real Google session. The UI follows the current Compose screens and reuses the Android logo, Google button and animated onboarding artwork.

## Develop

```sh
npm ci
npm run dev
npm test
npm run build
```

Vite serves the app on localhost. `dist/` is the deployable static site. `.openai/hosting.json` records the existing Sites project; reuse it for subsequent deployments. This directory has its own source repository used by Sites.

## Configuration

`src/config.json` contains only the Supabase public URL and publishable key. Both are public client configuration. Service-role credentials and AI provider keys must stay in the backend.

The Google OAuth flow uses PKCE and redirects to `https://aru-food-journal-web.lemonyibis8.chatgpt.site/?view=journal`. The Supabase project's Auth redirect allowlist must permit this URL. For local development, add the exact local URL separately when needed. The Google provider's existing callback remains `https://joynqjmfvfrjudmkrfuu.supabase.co/auth/v1/callback`.

If Google's consent configuration is in testing mode, the intended friend's account must be added as a Google OAuth test user by the project owner.

## Implemented behavior

- The Android app’s warm paper gradient, Roboto typography, logo/date/settings header, plain editable note lines and floating totals pill. Desktop uses a wider centered notebook with the same controls.
- Real Google authentication, account-owned records and session restoration.
- Automatic text persistence and nutrition calculation after a 1.8-second pause; no separate “Log meal” form. Typing drafts are retained locally while cloud saves are pending.
- Compact editing bar with calories remaining, dictation, saved meals and camera controls. Browser-supported dictation and camera include fallback/error handling.
- Bottom sheets for daily goals, saved meals and nutrition details; per-item portion and nutrient correction, source references, meal options, soft delete and Undo.
- Native-style profile settings, four-question illustrated setup and five-step interactive feature tour. Profile data uses the existing owner-protected user_profiles table.
- Web app manifest and iPhone home-screen instructions. No pricing page, marketing landing page, desktop sidebar or mobile tab bar.

## Boundaries

Meals and goals use online cloud reads/writes. Typing drafts stay in browser local storage under the signed-in account and selected date; this is not offline journal synchronization. Loading failures show a retry screen before editing is enabled. Authentication and AI requests still require network access.

The native Android app currently stores its journal locally. Android records do not automatically appear on the website. The website uses the cloud tables already deployed for the project; connecting the Android outbox is separate work.

The shared nutrition service enforces its existing per-user/project budgets. AI availability and accuracy vary. A meal is saved before its nutrition request; an estimate failure leaves the meal available for manual correction or retry. Photos are transient in the client and service, and are not attached to the saved record.

## Verification

`npm test` covers unknown/zero nutrition, incomplete totals, soft-deletion totals, manual bounds and calendar boundaries. The parent project's `supabase/tests/browser-cors.test.mjs` covers browser preflight and unauthenticated denial; `nutrition.test.mjs` covers the existing service contract and provider budget behavior. `npm test --prefix ../supabase/tests` checks owner RLS and request-budget invariants against PGlite.

Responsive browser checks use 320px, 390px, 768px and desktop widths. A real iPhone Safari run and a real Google sign-in are separate verification steps and must not be inferred from a successful static build.
