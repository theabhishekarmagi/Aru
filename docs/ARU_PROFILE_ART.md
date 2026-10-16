# Aru profile illustrations

Created 16 October 2026 with the built-in image-generation tool using the existing Aru logo as the character reference. All four transparent PNG sheets are stored in `app/src/main/res/drawable-nodpi/`: `aru_profile_name.png`, `aru_profile_birthday.png`, `aru_profile_height.png`, and `aru_profile_weight.png`.

Each sheet contains four successive poses in a 2 × 2 grid. Compose plays them once over three seconds with short crossfades, adds a small settling wobble to the scale scene, and leaves the last pose visible. Tap Aru to replay. Disabling Android animations presents the final pose immediately. These are illustrated frame sequences, not independently rigged Lottie characters. No runtime AI request or new library is needed.

## Shared generation prompt

Use case: illustration-story. Asset: transparent 2x2 animation sprite sheet, exactly four equal square cells, read left-to-right then top-to-bottom. Reference image is the Aru brand mascot: keep its round chubby hamster body, tiny round ears, whiskers, black bead eyes, thick slightly hand-drawn black outlines, cream filled fur, lavender and vivid purple accessories. OMIT the Aru wordmark. Flat friendly 2D art, no gradients or 3D. Each cell has identical camera, scale and baseline, full body fully visible within central 80% with clear padding; no borders, no cell labels, no background, genuine alpha transparency. Each cell is a different successive pose of ONE action. 

## Scene directions

- Name: hold a blank tag and pencil; write abstract strokes; turn the tag toward the user; wave while showing the tag. No legible writing.
- Birthday: hold a small cake with an unlit candle; light the candle; blow across the flame; show extinguished candle with smoke and purple confetti.
- Height: stand next to a lavender ruler; stretch on tiptoes; mark height; relax and point proudly at the measurement. Ticks only, no numbers.
- Weight: prepare to jump beside a lavender analog scale; jump above the scale; land and balance with the needle tilted; smile as the needle settles. No numbers or weight judgement.

The logo and first name sheet served as visual references for the remaining scenes. Assets preserve their generated alpha channels and original resolution.

