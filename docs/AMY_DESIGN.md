# Aru interface reference

Reference supplied by the user on 8 October 2026: https://www.amyfoodjournal.com/

Reviewed the 23.5-second muted looping intro (`/videos/polish_2.mp4`) across its full sequence, plus the home, editing, and goals images on the page. Local reference media lives in `references/amy/` for inspection only; it is gitignored and not packaged in the Android app.

## Applied design

- Warm cream-to-pale-lilac page, generous blank space, plain editable text lines, no boxed meal cards on the journal.
- Aru wordmark, centered floating date pill, circular settings control.
- Right-aligned calorie/status actions beside each line.
- Floating rounded white daily totals bar with colored macro labels. Aru adds fiber to the reference's calorie/macronutrient display.
- Rounded nutrition bottom sheet: large meal description, calorie summary, expandable item cards, portion information, estimate notes, source links, manual correction and saved-meal actions.
- Rounded goals card with colored progress bars, editable targets, and unset targets preserved.
- Android date picker and keyboard behavior rather than imitating iOS system chrome.

Aru retains its own name. No Amy logo, cat art, video, marketing copy, or sample nutrition is shipped. The intro's camera/microphone, streaks, location tracking, and widgets are outside the confirmed v1 scope.

## Behavior and current boundary

The Compose screens use the authenticated repository through an ordered IO controller. Text changes are persisted immediately; only future AI requests are debounced. Calendar navigation, item details, manual nutrient/portion corrections, saved-meal reuse, goals, and delete/undo use real repository operations. Unknown values and incomplete totals remain visible.

A real auth adapter and authenticated nutrition service are still unconfigured. Normal app launches show the account-required screen. The notebook is exercised by instrumentation tests with an isolated test account and private test storage. Test sessions and synthetic nutrition exist only in `androidTest`, not in the shipped app. No guest route or fake login was introduced.

The intro supplies a visual reference, not evidence of calorie accuracy. Aru does not display invented confidence scores or intermediate claims about searches. It displays only repository calculation states and returned estimate notes.
