package com.aru.journal.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aru.journal.R
import kotlin.math.roundToInt
import kotlin.math.sin

/** Eight illustrated poses per sheet, arranged left-to-right in a 4 × 2 grid.
 * Playback automatically loops while visible, with a resting beat at the end.
 * Android's disabled animator setting displays the finished pose without motion.
 */
@Composable
fun AruProfileIllustration(step: Int, modifier: Modifier = Modifier, motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    val resources = listOf(R.drawable.aru_profile_name, R.drawable.aru_profile_birthday,
        R.drawable.aru_profile_height, R.drawable.aru_profile_weight)
    val names = listOf("Aru and the name tag", "Aru and the birthday cake", "Aru measuring height", "Aru on the weighing scale")
    val poses = listOf(
        listOf("Holding a name tag", "Lifting the pencil", "Starting to write", "Writing on the name tag", "Turning the name tag", "Showing the name tag", "Raising a paw", "Waving hello"),
        listOf("Holding a birthday cake", "Lighting the candle", "Watching the candle", "Leaning toward the cake", "Blowing out the candle", "Watching the smoke", "Starting to celebrate", "Celebrating with confetti"),
        listOf("Looking at the measuring tape", "Standing tall", "Reaching upward", "Raising the heels", "Stretching on tiptoes", "Marking the height", "Checking the measurement", "Smiling at the measurement"),
        listOf("Getting ready to jump", "Crouching for a jump", "Jumping onto the scale", "Above the scale", "Landing on the scale", "Balancing to the left", "Balancing to the right", "Smiling as the needle settles")
    )
    val sheet = ImageBitmap.imageResource(resources[step])
    var replay by remember(step) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var resumeCount by remember(lifecycle) { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val progress = if (motionEnabled && resumed) key(step, replay, resumeCount) {
        val loop = rememberInfiniteTransition(label = "Aru automatic illustration")
        val value by loop.animateFloat(0f, 10f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "Aru eight poses")
        value
    } else 8f
    val pose = progress.toInt().coerceIn(0, 7)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(196.dp)
            .clickable(enabled = motionEnabled, role = Role.Button, onClickLabel = "Replay animation") { replay++ }
            .semantics {
                contentDescription = names[step]
                stateDescription = poses[step][pose]
            }) {
            val frameWidth = sheet.width / 4
            val frameHeight = sheet.height / 2
            // Keep a small inner gutter so adjacent cells cannot bleed through filtering.
            val gutter = (frameHeight * .025f).roundToInt()
            val contentWidth = frameWidth - gutter * 2
            val contentHeight = frameHeight - gutter * 2
            val fit = minOf(size.width / contentWidth, size.height / contentHeight)
            val width = (contentWidth * fit).roundToInt()
            val height = (contentHeight * fit).roundToInt()
            val fraction = progress - pose
            val blend = if (pose == 0) 1f else (fraction / .22f).coerceIn(0f, 1f)
            val wobble = if (step == 3 && pose in 5..6) sin(fraction * Math.PI * 2).toFloat() * 1.2f * (1 - fraction) else 0f
            rotate(wobble) {
                fun frame(index: Int, opacity: Float) {
                    drawImage(sheet,
                        srcOffset = IntOffset((index % 4) * frameWidth + gutter, (index / 4) * frameHeight + gutter),
                        srcSize = IntSize(contentWidth, contentHeight),
                        dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                        dstSize = IntSize(width, height), alpha = opacity, filterQuality = FilterQuality.High)
                }
                if (blend < 1f) frame(pose - 1, 1f - blend)
                frame(pose, blend)
            }
        }
        Text("Your little journal companion", color = Color(0xFF8E898F), fontSize = 12.sp)
    }
}
