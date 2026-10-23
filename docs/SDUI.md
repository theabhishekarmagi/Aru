# Server-driven UI for Aru Android

Version `0.7.0-sdui` (versionCode 10) adds a native Compose renderer for a small, versioned JSON contract. Friends must install this APK once. Subsequent **supported configuration changes** arrive from the server; arbitrary Kotlin changes still need another APK.

## Publish a change

Edit [`web/public/android-ui.json`](../web/public/android-ui.json), increment `revision`, then commit and push that file to the `webapp` branch. Vercel serves it at:

```
https://arufoodjournal.vercel.app/android-ui.json
```

Make sure Vercel → Aru → Settings → Environments → Production → Branch Tracking is set to `webapp`. Currently pushes to this branch create previews unless this setting is changed; in that case explicitly deploy/promote the tested commit to Production. Wait for the production deployment to finish. The app refreshes in the background on foreground/resume, at most once every five minutes. Return to Aru after that interval to see a change. A page already visible does not update continuously. No logout, journal reset, or APK replacement is involved.

The server response contains public presentation data only. Never put credentials, account IDs, personal messages, or meal records in it. Publishing requires your existing GitHub/Vercel access; the APK can only read it.

## Contract, schema version 1

- `schemaVersion`: exactly `1`.
- `revision`: nonnegative integer; increment for each published configuration. It is an identifier, not an anti-rollback counter, so a deliberate rollback remains possible.
- `minVersionCode`: minimum compatible Android build (currently `10`). Older clients retain a valid cached document or built-in defaults.
- `accent`: `purple`, `blue`, or `green`. Controls the Material primary color, SDUI card accent, and primary controls; it does not recolor every existing journal element.
- `features`: `camera`, `microphone`, and `savedMeals`, each boolean, default `true`. These control editing-toolbar visibility. `savedMeals: false` also hides saved-meal shortcuts in SDUI cards. Existing settings/tour screens are retained; these flags are presentation switches, never authorization controls.
- `cards`: up to eight cards, rendered in array order above journal entries, inside the same scrollable list.

Every card has a unique `id` (`a-z`, `A-Z`, digits, `_`, `-`, 1–48 characters), a `type`, a nonblank `title` (up to 100 characters), optional `body` (up to 500 characters), `tone` (`neutral` or `accent`), and up to three `actions`.

Supported card types:

| Type | Native behavior |
| --- | --- |
| `text` | Title and optional explanation/banner text. |
| `actions` | Title, optional body, and native shortcut buttons. |
| `daily_summary` | Current selected day's local journal totals, with incomplete nutrition explicitly marked. Optional shortcut buttons. |

Actions have a nonblank `label` (up to 40 characters) and an `action` from this allowlist:

- `add_meal`: add/focus a blank line for the selected day.
- `goals`: open the existing goals sheet.
- `saved_meals`: open saved meals (when the corresponding flag is visible).
- `settings`: open profile settings when a profile is available.

Unknown card types/actions are ignored to support gradual client rollouts. Unsupported schema versions, client versions, invalid text lengths, duplicate IDs, and malformed documents are rejected. Unknown object fields are ignored.

For example, replace the cards array with this to show a daily summary and a goal shortcut:

```json
[
  {
    "id": "daily-overview",
    "type": "daily_summary",
    "title": "Your day at a glance",
    "body": "Review the portions in your meals to keep estimates useful.",
    "tone": "accent",
    "actions": [{ "label": "Adjust goals", "action": "goals" }]
  }
]
```

Set `cards` to `[]` and all feature flags to `true` to restore the original journal layout remotely. Removing this endpoint instead leaves a last-known-good cache in use until it expires; publish an explicit reset when that is the intention.

## Reliability and boundaries

- Startup and journal editing never wait for a network request.
- Config is fetched only from the hard-coded public HTTPS origin. Requests do not include account tokens or user data; redirects are rejected.
- Downloads are bounded to 64 KiB, with connection/read timeouts. No remote HTML, scripts, APKs, URL actions, or executable code is accepted.
- Valid configuration is cached atomically in the app's private `remote-ui-v1.json`, separate from journal/account storage. Cache read/write failures never alter the journal.
- On startup the last valid configuration is used immediately while checking the server. Offline/invalid responses keep that configuration for up to seven days since its last successful fetch; older/corrupt cache falls back to the original journal with no extra cards and all three toolbar buttons available.
- This does not bypass Google sign-in, change RLS, migrate local meals, or change nutrition-service permissions/budgets.
- New native component types, actions, device capabilities, and permissions still require a new APK.

## Verification

`RemoteUiTest` covers published schema compatibility, ordering, unknown components/actions, payload bounds, version gates, bad/expired caches, cancellation, throttling/concurrency, and disk/network failures.

On a dedicated test emulator, `RemoteUiUiTest` verifies that changing the remote repository updates cards and button visibility in the same running app, preserves an editable synthetic meal, dispatches goal-sheet actions, and cannot bypass sign-in. Its explicitly named live test fetches the published configuration over HTTPS; all journal fixtures are local and synthetic.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aru.journal.RemoteUiUiTest
```
