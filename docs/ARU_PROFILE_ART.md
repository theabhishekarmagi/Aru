# Aru profile illustrations

Updated 16 October 2026 with the built-in image-generation tool. The four transparent PNG atlases are stored in `app/src/main/res/drawable-nodpi/`: `aru_profile_name.png`, `aru_profile_birthday.png`, `aru_profile_height.png`, and `aru_profile_weight.png`.

Each atlas now contains eight successive poses in a 4 × 2 grid (previously four poses). Compose automatically repeats a six-second cycle: 600 ms per pose, short 132 ms crossfades between poses, and an extended final-pose rest. The scale also has a small settling wobble. Tapping optionally restarts the cycle; it is never required to begin playback. Playback stops when the activity is not resumed and restarts on return. Disabling Android animations displays the final pose without motion.

These are illustrated pose sequences with blended transitions, not skeletal or optical-flow animation. Assets stay local: no new library, network request, or runtime AI cost is needed.

Settings → Replay onboarding opens the same illustrated profile pages as signup in a read-only preview, followed by the feature tour. Replay cannot save or clear profile fields or onboarding-completion flags. Previous works across the boundary between the profile preview and feature tour; Skip exits replay.

## Shared generation prompt

Use case: illustration-story. Create an animation sprite atlas for Aru Android onboarding. Input image is CHARACTER AND STYLE REFERENCE ONLY. Exactly 8 sequential poses on a precisely regular 4 COLUMN by 2 ROW grid, read left to right then top to bottom. Landscape canvas. No drawn grid, no text, no captions. Each complete hamster and prop is centered in its own equal cell, occupies central 70% only, with generous transparent gutter on ALL sides. SAME camera, body proportions, baseline and size across all 8 frames. Cream round hamster, black hand-drawn outlines, tiny ears, whiskers, black bead eyes, lavender/purple props. Flat color, NO glow, NO gradient, no shadow outside character, genuine transparent background. Eight distinct small progressive changes in an action, suitable for animation. Preserve cute mascot identity.

## Scene prompts appended to shared prompt

- NAME TAG scene: 1 holds blank lavender tag and pencil poised; 2 pencil approaches tag; 3 begins short abstract mark; 4 completes abstract squiggle; 5 lowers pencil and starts turning tag toward viewer; 6 proudly presents tag; 7 lifts free paw halfway; 8 waves with happy smile. No legible letters. Eight frames, exactly 4 columns and 2 rows.
- BIRTHDAY scene: no tag or pencil. 1 holds tiny lavender birthday cake with unlit candle; 2 candle starts glowing; 3 flame fully lit and hamster smiles; 4 hamster leans toward cake; 5 cheeks puff blowing; 6 flame extinguishes with thin smoke curl; 7 happy smile with 2 confetti pieces; 8 happy smile with 3 falling confetti pieces. Exactly 8 frames.
- HEIGHT scene: no tag or pencil. Aru stands beside a vertical lavender measuring ruler. 1 looks toward ruler; 2 stands straighter; 3 begins stretching upward; 4 raises heels slightly; 5 reaches full tiptoes; 6 holds paw at head height beside ruler; 7 looks proudly toward measurement; 8 relaxes and smiles pointing at ruler. Ruler stays stationary, ticks only no numbers. Exactly 8 frames.
- WEIGHT scene: no tag or pencil. Aru jumps onto a lavender analog weighing scale. Keep scale fixed at bottom center of EVERY cell and whole body fully visible. 1 stands behind scale ready; 2 crouches; 3 begins small jump; 4 at top of small jump above scale; 5 lands slightly squashed; 6 balances tilting slightly left needle left; 7 balances slightly right needle right; 8 stands centered smiling needle centered. Gentle neutral happy mood, no numbers. Exactly 8 frames.

The previous name atlas was the reference for its replacement; the new eight-pose name atlas was the style reference for the other three. Generated alpha channels and original 1774 × 887 resolution are preserved.
