# Share images to Aru (v0.11.0)

## Android

Install the v0.11.0 APK once. In Photos, Gallery, Files, a browser or another app, select **one image → Share → Aru**. Aru accepts ACTION_SEND with image/* and handles both a cold launch and a running activity. Camera captures and the existing in-app photo picker use the same review flow.

Sign in and finish any required profile setup, then tap **Analyze image**. The image remains a draft until **Add to Aru**:

- **Dish:** review the transient image, dish description, estimated calories/macros, portions and assumptions; then add it.
- **Menu:** review extracted, categorized food choices; search, select what was eaten, and adjust amounts. Continue estimates only selected items. No drinks, sides or other choices are added unless selected.
- **Ingredients:** select consumed ingredients and quantities; a printed recipe is not automatically logged as a whole meal. These are generic estimates, not verified nutrition-label extraction.
- **Unrelated/unreadable:** no Add action; retry with a clearer image or a crop.

The scanner extracts at most 60 readable options per image and allows up to 12 selections per meal (the existing nutrition contract). Dense/unreadable pages should be cropped into smaller sections. A default serving is an editable assumption, not a measured weight. Cancelling before Add leaves the journal unchanged. Added nutrition stays editable through the normal meal details.

## Website and iPhone

Use the camera control → Choose photo to upload an image, then follow the same choice/review flow. The website does not register a system share target. iPhone Safari does not provide the native share extension shown in the reference screenshots; an iOS application with a Share Extension is separate work. No iOS app is included in this release.

## Data and compatibility

Incoming Android images must use a sender-granted content URI; filesystem paths and network URLs are never resolved. Reads are bounded at 25 MB and decoded with downsampling, then converted to metadata-free JPEG at up to 1600 px and 1.5 MB. Web images use the existing 1280 px/1.5 MB conversion. Unsupported formats show an error; JPEG and PNG are the most portable choices.

Images and previews are kept only in client memory during the active flow. Android retains an incoming image in its ViewModel across activity recreation and sign-in, clears it on dismissal or account change, and requires a new share after process death. The server sends it to the existing AI provider only after Analyze. Aru does not store image bytes in journal entries, Storage, function logs or the request cache. Existing first-use/provider privacy terms still apply. Derived food choices/estimates use the existing private request cache and request budgets.

The new `scan` API mode classifies and extracts items, or returns a dish estimate. Menu selection uses the existing text endpoint for one additional request; source-image classification never writes a journal entry. Legacy text and photo requests retain their response contracts. Routes remain free-only with authentication, strict schema validation and the existing per-user/project budgets. The new mode uses higher image detail, up to 6000 output tokens, and a 50-second provider timeout inside the existing 65-second client limit.

## Verification

Server tests cover scan bounds, dish/menu/ingredient/other routing, strict validation, absence of image bytes in cached results, legacy API behavior and CORS/auth. Android tests cover preview-before-save, selected-only estimation/persistence, unknown-image handling and an actual OS ACTION_SEND launch; domain tests confirm preview requests never create entries. Browser checks cover file upload, image conversion, selections/search/quantity editing, cancel, preview, explicit Add, unrelated input and failure/retry. These validate the flow, not calorie accuracy on arbitrary real-world images.
