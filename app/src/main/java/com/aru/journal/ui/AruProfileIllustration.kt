package com.aru.journal.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aru.journal.R
import kotlin.math.roundToInt
import kotlin.math.sin

/** Four illustrated poses per sheet, arranged left-to-right in a 2 × 2 grid.
 * Playback is local, finite, cancellable with the page, and can be replayed by tapping.
 * Android's disabled animator setting displays the finished pose without motion.
 */
@Composable
fun AruProfileIllustration(step: Int, modifier: Modifier = Modifier, motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    val resources = listOf(R.drawable.aru_profile_name, R.drawable.aru_profile_birthday,
        R.drawable.aru_profile_height, R.drawable.aru_profile_weight)
    val names = listOf("Aru and the name tag", "Aru and the birthday cake", "Aru measuring height", "Aru on the weighing scale")
    val poses = listOf(
        listOf("Holding a name tag", "Writing on the name tag", "Showing the name tag", "Waving hello"),
        listOf("Holding a birthday cake", "Lighting the candle", "Blowing out the candle", "Celebrating with confetti"),
        listOf("Looking at the measuring tape", "Stretching on tiptoes", "Marking the height", "Smiling at the measurement"),
        listOf("Getting ready to jump", "Jumping onto the scale", "Balancing on the scale", "Smiling as the needle settles")
    )
    val sheet = ImageBitmap.imageResource(resources[step])
    val progress = remember(step) { Animatable(if (motionEnabled) 0f else 4f) }
    var replay by remember(step) { mutableIntStateOf(0) }
    LaunchedEffect(step, replay, motionEnabled) {
        if (motionEnabled) {
            progress.snapTo(0f)
            progress.animateTo(4f, tween(3000, easing = LinearEasing))
        } else progress.snapTo(4f)
    }
    val pose = progress.value.toInt().coerceIn(0, 3)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(196.dp)
            .clickable(enabled = motionEnabled, role = Role.Button, onClickLabel = "Replay animation") { replay++ }
            .semantics {
                contentDescription = names[step]
                stateDescription = poses[step][pose]
            }) {
            val frameWidth = sheet.width / 2
            val frameHeight = sheet.height / 2
            // Keep a small inner gutter so adjacent cells cannot bleed through filtering.
            val gutter = (frameHeight * .025f).roundToInt()
            val contentWidth = frameWidth - gutter * 2
            val contentHeight = frameHeight - gutter * 2
            val fit = minOf(size.width / contentWidth, size.height / contentHeight)
            val width = (contentWidth * fit).roundToInt()
            val height = (contentHeight * fit).roundToInt()
            val fraction = progress.value - pose
            val blend = if (pose == 0) 1f else (fraction / .16f).coerceIn(0f, 1f)
            val wobble = if (step == 3 && pose == 2) sin(fraction * Math.PI * 4).toFloat() * 2f * (1 - fraction) else 0f
            rotate(wobble) {
                fun frame(index: Int, opacity: Float) {
                    drawImage(sheet,
                        srcOffset = IntOffset((index % 2) * frameWidth + gutter, (index / 2) * frameHeight + gutter),
                        srcSize = IntSize(contentWidth, contentHeight),
                        dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                        dstSize = IntSize(width, height), alpha = opacity, filterQuality = FilterQuality.High)
                }
                if (pose > 0 && blend < 1f) frame(pose - 1, 1f - blend)
                frame(pose, blend)
            }
        }
        Text(if (motionEnabled) "Tap Aru to replay" else "Your little journal companion", color = Color(0xFF8E898F), fontSize = 12.sp)
    }
}
