package global.bert.widget

import androidx.lifecycle.repeatOnLifecycle
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.roundToInt

// Art: Bertians R2 F/10 (PixelLab), owner-approved for the app 2026-10-12: bert_f10 (84px, facing east), its 8-frame
// east walk (bert_walk), six mood stills (bert_mood_*: 0.10 art lane, PixelLab Pro + inpaint from F/10, snapped to
// F/10's 33-colour palette; owner-approved 2026-10-12) and today_garden: banner B of the 0.10 art lane, the R2 scene's
// gate end re-rendered in Godot with the pond filled, so the grass runs unbroken to the wall and wicket gate (grass
// line row 104 in every column). Provenance: /opt/bert-widget-qa/art-0.10/README.md.
// Everything is drawn nearest-neighbour at a whole-number scale.
internal const val FRAME = 84
internal const val GARDEN_W = 600
internal const val GARDEN_H = 136
internal const val FEET_TOP = 20 // frame top so his feet land on the grass line (row 104)
internal const val REST = 382 // frame left: on the path, just short of the wall and the wicket gate
private const val STEP = 4 // garden pixels per walk frame
private val HOP = intArrayOf(2, 5, 7, 8, 7, 5, 2, 0)

private data class BertPose(val x: Int = REST, val walkFrame: Int? = null, val facingLeft: Boolean = false, val lift: Int = 0)

/** True when the person has switched animations off (Remove animations, or a zero animator scale). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Runs an endless loop only where endless animation is allowed; Compose tests forbid it, so they stay idle-able. */
private suspend fun forever(block: suspend () -> Unit) {
    val policy = coroutineContext[InfiniteAnimationPolicy]
    if (policy == null) block() else policy.onInfiniteOperation(block)
}

/**
 * Today's banner: Bert in the garden. He walks a few steps in when Home opens, breathes (a one-pixel bob), hops and
 * woofs when tapped, and paces while [pacing] (a pull-to-refresh). With animations off he stands still; a tap still woofs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayGarden(pacing: Boolean, modifier: Modifier = Modifier, mood: String? = null, onLongPress: (() -> Unit)? = null) {
    val haptics = LocalHapticFeedback.current
    val still = rememberReducedMotion()
    val garden = ImageBitmap.imageResource(R.drawable.today_garden)
    // Standing still he wears the feed's mood (a character note; Home never puts it next to the price).
    val bert = ImageBitmap.imageResource(moodStill(mood))
    val walk = ImageBitmap.imageResource(R.drawable.bert_walk)
    var pose by remember { mutableStateOf(BertPose()) }
    var taps by remember { mutableIntStateOf(0) }
    var woof by remember { mutableStateOf(false) }
    val pace by rememberUpdatedState(pacing)
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        woof = true
        delay(900)
        woof = false
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(still, lifecycle) {
        if (still) return@LaunchedEffect
        // Only while Home is on screen: a stopped-but-alive activity must not keep waking the CPU to move Bert.
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { forever {
            suspend fun walkTo(from: Int, to: Int) {
                val dir = if (to > from) 1 else -1
                for (i in 1..(kotlin.math.abs(to - from) / STEP)) {
                    pose = BertPose(from + dir * STEP * i, (i - 1) % 8, facingLeft = dir < 0)
                    delay(95)
                }
                pose = BertPose(to)
            }
            walkTo(REST - 28, REST) // a few steps in, every time Home opens
            var heard = taps
            var bob = 0
            while (true) when {
                taps != heard -> { heard = taps; HOP.forEach { pose = BertPose(lift = it); delay(45) } }
                pace -> { walkTo(REST, REST - 24); walkTo(REST - 24, REST) } // back down the path and up again, never into the wall
                else -> {
                    bob = 1 - bob
                    pose = BertPose(lift = bob)
                    // Wake early for a tap or a pull, so neither waits out a breath.
                    withTimeoutOrNull(if (bob == 1) 450L else 1_050L) { snapshotFlow { taps != heard || pace }.first { it } }
                }
            }
        } }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        // A whole number of device pixels per art pixel; wide cards scale up rather than run out of garden.
        val k = maxOf(density.density.roundToInt(), ceil(widthPx / GARDEN_W).toInt(), 1)
        val shown = ceil(widthPx / k).toInt().coerceAtMost(GARDEN_W)
        // Bert sits about a third in; the clamp keeps the wall and gate in view on phones, wider cards show more path.
        val x0 = (REST - (shown * 0.30f).toInt()).coerceIn(0, GARDEN_W - shown)
        Canvas(Modifier.fillMaxWidth().height(with(density) { (GARDEN_H * k).toDp() })
            .semantics { contentDescription = "Bert in his red hat, by the garden gate" }
            .combinedClickable(interactionSource = null, indication = null, role = Role.Button,
                onClickLabel = "Say hello", onLongClickLabel = onLongPress?.let { "Meet Bert" }, onLongClick = onLongPress,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); taps++ })) {
            drawImage(garden, srcOffset = IntOffset(x0, 0), srcSize = IntSize(shown, GARDEN_H),
                dstSize = IntSize(shown * k, GARDEN_H * k), filterQuality = FilterQuality.None)
            val now = pose
            val left = (now.x - x0) * k
            scale(if (now.facingLeft) -1f else 1f, 1f, pivot = Offset(left + FRAME * k / 2f, 0f)) {
                drawImage(if (now.walkFrame == null) bert else walk, srcOffset = IntOffset((now.walkFrame ?: 0) * FRAME, 0),
                    srcSize = IntSize(FRAME, FRAME), dstOffset = IntOffset(left, (FEET_TOP - now.lift) * k),
                    dstSize = IntSize(FRAME * k, FRAME * k), filterQuality = FilterQuality.None)
            }
        }
        // The woof sits beside his face, in the sky.
        val bubbleX = (REST - x0 + 66) * k
        val bubbleY = (FEET_TOP + 14) * k
        AnimatedVisibility(woof, Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) { placeable.place(bubbleX, bubbleY - placeable.height / 2) }
        }, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
            Text("woof!", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics { testTag = "woof" }.clip(RoundedCornerShape(12.dp)).background(Cream)
                    .padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

/** The size nearest [target] at which the 84px F/10 still lands on whole device pixels. */
@Composable
internal fun pixelBertSize(target: Dp): Dp {
    val density = LocalDensity.current
    return with(density) { (FRAME * (target.toPx() / FRAME).roundToInt().coerceAtLeast(1)).toDp() }
}

/** The F/10 still, nearest-neighbour, so it never blurs. Size it with [pixelBertSize]. */
@Composable
internal fun PixelBertPortrait(size: Dp, modifier: Modifier = Modifier) {
    Image(ImageBitmap.imageResource(R.drawable.bert_f10), "Pixel Bert, drawn from Bertram",
        modifier.size(size), filterQuality = FilterQuality.None)
}

/** The still for a feed mood; anything unknown is plain F/10. */
internal fun moodStill(mood: String?): Int = when (mood?.trim()?.lowercase()) {
    "content" -> R.drawable.bert_mood_content
    "giddy" -> R.drawable.bert_mood_giddy
    "smug" -> R.drawable.bert_mood_smug
    "anxious" -> R.drawable.bert_mood_anxious
    "melancholy" -> R.drawable.bert_mood_melancholy
    "restless" -> R.drawable.bert_mood_restless
    else -> R.drawable.bert_f10
}
