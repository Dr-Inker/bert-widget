package global.bert.widget

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 0.12, “Bert’s day” (docs/CONCEPT-0.12-BERTS-PLACE.md): Home opens onto Bert's garden at the phone's own time of day.
 * Where he is follows his routine; how he stands follows his mood. The scene is never a card: full width, from the top
 * of the screen, with the noticeboard (Berthalla's note), the price tag on the gate post and this month's paw prints
 * all in the picture.
 */

/** Bert's handwriting, as on berthalla.io (Gochi Hand, SIL Open Font License 1.1). */
internal val BertHand = FontFamily(Font(R.font.gochi_hand))

/** Six renders of the same Godot scene, relit (not tinted) for the time of day. */
internal enum class PlaceTime(val image: Int, val wide: Int, val light: Boolean, private val tint: FloatArray?, private val desat: Float) {
    DAWN(R.drawable.place_dawn, R.drawable.place_wide_dawn, true, floatArrayOf(1.0f, 0.848f, 0.796f), 0.05f),
    DAY(R.drawable.place_day, R.drawable.place_wide_day, true, null, 0f),
    GOLDEN(R.drawable.place_golden, R.drawable.place_wide_golden, true, floatArrayOf(1.0f, 0.876f, 0.692f), 0f),
    DUSK(R.drawable.place_dusk, R.drawable.place_wide_dusk, false, floatArrayOf(0.496f, 0.408f, 0.576f), 0.18f),
    NIGHT(R.drawable.place_night, R.drawable.place_wide_night, false, floatArrayOf(0.12f, 0.128f, 0.244f), 0.4f);

    /** The same light the scene was graded with, for what the app draws into it (Bert, his paw prints). */
    val filter: ColorFilter? = tint?.let { (r, g, b) ->
        val m = ColorMatrix().apply { setToSaturation(1f - desat) }
        m.timesAssign(ColorMatrix().apply { setToScale(r, g, b, 1f) })
        ColorFilter.colorMatrix(m)
    }
}

/** The day Bert last walked home in the evening, for this run of the app. */
private object EveningWalk { var walkedOn: java.time.LocalDate? = null }

/** Where Bert is, by the phone's clock. Night has no Bert in the garden: he's inside, and the window is lit. */
internal enum class BertSlot(val time: PlaceTime, val bertX: Int?, val walk: IntRange? = null) {
    GATE(PlaceTime.DAWN, 180),                 // 06-09, first out: at the gate itself
    ROUNDS(PlaceTime.DAY, 40, 40..110),        // 09-12, walking his rounds up and down the lawn
    NAP(PlaceTime.DAY, 44),                    // 12-15, asleep in the blossom's shade
    POST(PlaceTime.GOLDEN, 268),               // 15-18, by the noticeboard, waiting on the post
    EVENING(PlaceTime.DUSK, 180, 70..180),     // 18-21, walking home towards the gate
    BED(PlaceTime.NIGHT, null);                // 21-06, asleep indoors

    companion object {
        fun at(hour: Int): BertSlot = when (hour) {
            in 6..8 -> GATE
            in 9..11 -> ROUNDS
            in 12..14 -> NAP
            in 15..17 -> POST
            in 18..20 -> EVENING
            else -> BED
        }
    }
}

/**
 * One line in his voice per slot. Lines have to fail without the hat, the hoodie or the mayor; drafted with Grok
 * (critique round 1, 15 October) and cut by hand.
 */
internal object BertLines {
    private val bank = mapOf(
        BertSlot.GATE to listOf("first one out. the gate is a formality.", "woofmornin. i have checked the hedge for news.",
            "dew on the paws. worth it.", "someone walked past at six and i saw everything.",
            "the mayor opens the gate at seven, or thereabouts."),
        BertSlot.ROUNDS to listOf("rounds. every post, every corner, every smell that matters.", "inspecting the daisies. they pass.",
            "a leaf moved and i handled it.", "the hedge has been told.", "civic walk in progress. wave only if you mean it."),
        BertSlot.NAP to listOf("sun\u2019s on the lawn. do not disturb.", "napping under the blossom, as is my right.",
            "eyes closed. ears open. hat on.", "official nap. minutes to follow."),
        BertSlot.POST to listOf("waiting on the post. the post knows.", "nothing new on the board yet. i\u2019ll stand here.",
            "gate duty. nobody gets past without a sniff.", "the post is late. this has been noted."),
        BertSlot.EVENING to listOf("lamp\u2019s lit. the hills can clock off.", "one more lap of the lawn, then home.",
            "the hat stays on until the door shuts.", "heading in. the daisies are in charge."),
        BertSlot.BED to listOf("it\u2019s late. the lamp has this.", "asleep. the hat disagrees.", "door\u2019s shut. dreams are civic too.",
            "shh. the mayor is off duty."),
    )
    val pats = listOf("that was the spot.", "again. i permit it.", "careful of the hat.", "ears. yes. ears.", "a fine constituent.",
        "you may continue.")
    const val TOO_MANY = "alright. enough politics."
    const val NAPPING = "five more minutes."
    const val WOKEN = "you woke the mayor."

    fun of(slot: BertSlot, day: LocalDate): String = bank.getValue(slot).let { it[(day.dayOfYear + slot.ordinal) % it.size] }
}

/** The days this month the person came round: one paw print each, in the order they came. Nothing to lose. */
internal class BertVisits(context: Context) {
    private val prefs = context.getSharedPreferences("bert_place", Context.MODE_PRIVATE)

    fun record(day: LocalDate): Set<Int> {
        val month = "${day.year}-${day.monthValue}"
        val days = if (prefs.getString(KEY_MONTH, null) == month) days() else emptySet()
        val next = days + day.dayOfMonth
        if (next != days || prefs.getString(KEY_MONTH, null) != month)
            prefs.edit().putString(KEY_MONTH, month).putString(KEY_DAYS, next.sorted().joinToString(",")).apply()
        return next
    }

    private fun days(): Set<Int> = prefs.getString(KEY_DAYS, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()

    private companion object {
        const val KEY_MONTH = "month"
        const val KEY_DAYS = "days"
    }
}

// Place image space: 400 x 298, cut from the world at (770, 90). The grass line is row 282; below it, plain loam
// (no soil cross-section: the owner, rightly, found the roots meaningless on a home screen).
private const val PLACE_W = 400
private const val PLACE_H = 298
private const val WIDE_W = 580                  // place_wide_*: the same scene with 180 more art px on the left
private const val WIDE_SHIFT = 180
private const val SHOWN_MIN = 360               // at least this much across, so all of Bert's day (x 40..352) is in frame

/**
 * How the scene sits on a page [widthPx] wide on a screen [screenHeightPx] tall: [k] device pixels per art pixel,
 * [shown] art pixels across ending at the right edge of the place (x0 = 400 - shown, negative into the wide bake's
 * extra garden), drawn [left] px in from the page's edge (centred when even the wide bake can't fill it: landscape).
 */
internal data class PlaceFrame(val k: Int, val shown: Int, val left: Int) {
    val x0: Int get() = PLACE_W - shown
    val heightPx: Int get() = PLACE_H * k
}

internal fun placeFrame(widthPx: Float, screenHeightPx: Float): PlaceFrame {
    // Whole device pixels per art pixel, as large as keeps 360 art px across, and in landscape no more than about
    // two-thirds of the screen's height, so Bert is never below the fold.
    var k = maxOf(1, (widthPx / SHOWN_MIN).toInt())
    if (screenHeightPx > 0) k = minOf(k, maxOf(1, (screenHeightPx * 0.66f / PLACE_H).toInt()))
    val shown = ceil(widthPx / k).toInt().coerceAtMost(WIDE_W)
    return PlaceFrame(k, shown, ((widthPx - shown * k) / 2).toInt().coerceAtLeast(0))
}

/** The scene's height on screen (whole art pixels, as [BertsPlace] draws it). */
internal fun placeHeightPx(widthPx: Int, screenHeightPx: Int): Int = placeFrame(widthPx.toFloat(), screenHeightPx.toFloat()).heightPx
private const val PLACE_FEET_TOP = 198          // an 84 px standing frame's top, so his feet land on the grass line
private const val PLACE_NAP_TOP = 232           // the 84 x 56 sun-nap frame's top
private val BOARD = PlaceRect(356, 250, 34, 34)       // the noticeboard, standing against the wall past the gate
private val CHALK = PlaceRect(138, 248, 48, 36)      // the A-frame chalkboard between the blossom and the gate
private val SLATE = PlaceRect(148, 251, 32, 24)      // its writable slate
private val GATEWAY = PlaceRect(248, 211, 92, 72)    // the picket gate and its posts: through it, The Lost Trail
private val WINDOW = PlaceRect(262, 104, 28, 32)

private data class PlaceRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** Which of the six renders and poses, for a clock reading; pure so tests can walk the whole day. */
internal fun placeTimeAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): BertSlot =
    BertSlot.at(Instant.ofEpochMilli(epochMillis).atZone(zone).hour)

@Composable
internal fun BertsPlace(
    now: Long,
    mood: String?,
    priceUsd: Double?,
    newNotes: Int,
    openBoard: () -> Unit,
    openMarket: () -> Unit,
    modifier: Modifier = Modifier,
    meetBert: (() -> Unit)? = null,
    openTrail: (() -> Unit)? = null,
    skyCovered: Boolean = false,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val slot = placeTimeAt(now, zone)
    val time = slot.time
    val visits = remember(today) { BertVisits(context.applicationContext).record(today) }

    var patUntil by remember { mutableLongStateOf(0L) }
    var pats by remember { mutableIntStateOf(0) }
    var lastPat by remember { mutableLongStateOf(0L) }
    var wokenUntil by remember { mutableLongStateOf(0L) }
    var said by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    // Where he is right now (his rounds and his walk home move him), which walk frame, which way, and the pat's hop.
    // The evening walk home happens once a day; coming back to Home afterwards finds him already at the gate.
    val walkHome = slot == BertSlot.EVENING && EveningWalk.walkedOn != today
    var bertX by remember(slot) { mutableIntStateOf(if (walkHome) slot.walk!!.first else slot.bertX ?: 0) }
    var walkFrame by remember(slot) { mutableIntStateOf(-1) }
    var facingLeft by remember(slot) { mutableStateOf(false) }
    var lift by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val still = rememberReducedMotion()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(slot, still, lifecycle) {
        val path = slot.walk ?: return@LaunchedEffect
        if (still) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            suspend fun walkTo(to: Int) {
                facingLeft = to < bertX
                var f = 0
                while (bertX != to) {
                    bertX += if (to > bertX) minOf(2, to - bertX) else -minOf(2, bertX - to)
                    walkFrame = f++ % 8
                    delay(110)
                }
                walkFrame = -1
            }
            if (slot == BertSlot.EVENING) {
                // Heading home: in from the lawn to the gate once, then he waits there.
                if (walkHome) { walkTo(path.last); facingLeft = false; EveningWalk.walkedOn = today }
            } else forever {
                while (true) {
                    walkTo(path.last); delay(2_400)
                    walkTo(path.first); delay(3_200)
                }
            }
        }
    }
    LaunchedEffect(patUntil, wokenUntil) {
        val until = maxOf(patUntil, wokenUntil)
        if (until == 0L) return@LaunchedEffect
        delay((until - System.currentTimeMillis()).coerceAtLeast(0))
        tick++
        said = null
    }
    // Re-read the clock whenever a pat or a wake ends (tick), so the pose and the window come back on time.
    val clock = if (tick >= 0) System.currentTimeMillis() else 0L
    val patting = clock < patUntil
    val woken = clock < wokenUntil

    // Status-bar icons follow the sky: dark on a day sky, light at dusk and night, and light once the page has scrolled
    // up under the bar (skyCovered); light again when Home is left.
    val view = LocalView.current
    DisposableEffect(time, skyCovered) {
        val window = (view.context as? Activity)?.window
        if (window != null && !view.isInEditMode) WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = time.light && !skyCovered
        onDispose { if (window != null) WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false }
    }

    // The wide bake, so wider phones see more garden rather than less of Bert (its x 180.. is the place's x 0..).
    val scene = ImageBitmap.imageResource(if (slot == BertSlot.BED && woken) R.drawable.place_wide_nightoff else time.wide)
    val pose = when {
        slot.bertX == null -> null
        slot == BertSlot.NAP -> R.drawable.bert_mood_asleep
        patting -> R.drawable.bert_pat
        else -> moodStill(mood)
    }
    val bert = pose?.let { ImageBitmap.imageResource(it) }
    val walkSheet = ImageBitmap.imageResource(R.drawable.bert_walk)
    val line = said ?: BertLines.of(slot, today)

    fun pat() {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (!still) scope.launch { for (h in intArrayOf(2, 4, 3, 1, 0)) { lift = h; delay(45) } }
        val at = System.currentTimeMillis()
        if (slot == BertSlot.NAP) { said = BertLines.NAPPING; patUntil = at + 1_600; return }
        pats = if (at - lastPat < 4_000) pats + 1 else 1
        lastPat = at
        said = if (pats >= 5) BertLines.TOO_MANY else BertLines.pats[(pats - 1 + today.dayOfYear) % BertLines.pats.size]
        patUntil = at + 1_400
    }

    Column(modifier.fillMaxWidth()) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val frame = placeFrame(widthPx, LocalConfiguration.current.screenHeightDp * density.density)
        val k = frame.k; val shown = frame.shown; val x0 = frame.x0; val left = frame.left
        val heightDp = with(density) { (PLACE_H * k).toDp() }
        fun Int.dp(): Dp = with(density) { this@dp.toDp() }

        Canvas(Modifier.fillMaxWidth().height(heightDp).testTag("berts-place")
            .semantics { contentDescription = sceneDescription(slot, woken) }) {
            translate(left = left.toFloat()) {
            drawImage(scene, srcOffset = IntOffset(x0 + WIDE_SHIFT, 0), srcSize = IntSize(shown, PLACE_H),
                dstSize = IntSize(shown * k, PLACE_H * k), filterQuality = FilterQuality.None)
            // His prints, pressed into the soil in the order the days came: overlapping, slightly uneven.
            val soil = Color(0xFF5E3A1A)
            visits.sorted().forEach { d ->
                val px = 8 + (d - 1) * 12 + (d * 7 % 5) - x0
                val py = 288 + (d * 13 % 4) + if (d % 2 == 0) 2 else 0
                if (px < 0 || px > shown - 6) return@forEach
                fun dot(x: Int, y: Int, w: Int = 1, h: Int = 1) = drawRect(soil, androidx.compose.ui.geometry.Offset(((px + x) * k).toFloat(), ((py + y) * k).toFloat()),
                    androidx.compose.ui.geometry.Size((w * k).toFloat(), (h * k).toFloat()), colorFilter = time.filter)
                dot(1, 2, 3, 2); dot(0, 0); dot(2, -1); dot(4, 0)
            }
            fun px(x: Int, y: Int, c: Color, w: Int = 1, h: Int = 1) = drawRect(c, androidx.compose.ui.geometry.Offset(((x - x0) * k).toFloat(),
                (y * k).toFloat()), androidx.compose.ui.geometry.Size((w * k).toFloat(), (h * k).toFloat()))
            // The price, chalked on the slate in art pixels (in cents, as a shopkeeper would); a chalk paw when there
            // is no price to give. Chalk is never perfectly even.
            val chalk = Color(0xFFEDEBE4).copy(alpha = if (time.light) 0.9f else 0.55f)
            val text = priceUsd?.let { chalkPrice(it) }
            if (text != null) {
                val width = ChalkFont.width(text)
                var cx = SLATE.x + (SLATE.w - width) / 2
                val cy = SLATE.y + (SLATE.h - ChalkFont.HEIGHT) / 2
                for (ch in text) {
                    val glyph = ChalkFont.glyph(ch)
                    glyph.forEachIndexed { row, bits -> bits.forEachIndexed { col, on ->
                        if (on == '#') px(cx + col, cy + row, chalk.copy(alpha = chalk.alpha * (if ((cx + col + row) % 5 == 0) 0.7f else 1f)))
                    } }
                    cx += glyph[0].length + 1
                }
            } else ChalkFont.PAW.forEachIndexed { row, bits -> bits.forEachIndexed { col, on ->
                if (on == '#') px(SLATE.x + 12 + col, SLATE.y + 8 + row, chalk.copy(alpha = chalk.alpha * 0.7f))
            } }
            if (slot.bertX != null) {
                val x = bertX
                // A soft contact shadow, in pixels, so he stands on the grass rather than over it.
                if (slot != BertSlot.NAP) {
                    val shade = Color.Black.copy(alpha = 0.22f)
                    px(x + 22, 281, shade, 40, 1); px(x + 26, 282, shade, 32, 1)
                }
                val top = (if (slot == BertSlot.NAP) PLACE_NAP_TOP else PLACE_FEET_TOP) - lift
                val frame = walkFrame
                val sheet = if (frame >= 0) walkSheet else bert
                if (sheet != null) {
                    val src = if (frame >= 0) IntOffset(frame * 84, 0) else IntOffset.Zero
                    val size = if (frame >= 0) IntSize(84, 84) else IntSize(sheet.width, sheet.height)
                    val left = (x - x0) * k
                    scale(if (facingLeft) -1f else 1f, 1f, pivot = androidx.compose.ui.geometry.Offset(left + size.width * k / 2f, 0f)) {
                        drawImage(sheet, srcOffset = src, srcSize = size, dstOffset = IntOffset(left, top * k),
                            dstSize = IntSize(size.width * k, size.height * k), filterQuality = FilterQuality.None, colorFilter = time.filter)
                    }
                }
            }
            }
        }

        // Tap targets, also the TalkBack nodes for what's in the picture.
        fun Modifier.at(r: PlaceRect, pad: Int = 0) = this.offset((left + (r.x - pad - x0) * k).dp(), ((r.y - pad) * k).dp())
            .size(((r.w + 2 * pad) * k).dp(), ((r.h + 2 * pad) * k).dp())
        Box(Modifier.at(BOARD, 6).testTag("place-board").clickable(role = Role.Button, onClickLabel = "Read the noticeboard") { openBoard() }
            .semantics { contentDescription = if (newNotes > 0) "Noticeboard, $newNotes new" else "Noticeboard: today’s note from Berthalla" })
        if (openTrail != null) Box(Modifier.at(GATEWAY).testTag("place-gate").clickable(role = Role.Button, onClickLabel = "Go through the gate") { openTrail() }
            .semantics { contentDescription = "The garden gate: through it, The Lost Trail" })
        Box(Modifier.at(CHALK, 6).testTag("place-tag").clickable(role = Role.Button, onClickLabel = "Open Market") { openMarket() }
            .semantics { contentDescription = priceUsd?.let { "Chalkboard: BERT ${formatPrice(it)}" } ?: "Chalkboard" })
        if (slot == BertSlot.BED) Box(Modifier.at(WINDOW, 8).testTag("place-window").clickable(role = Role.Button, onClickLabel = "Tap the window") {
            if (!woken) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                said = BertLines.WOKEN
                wokenUntil = System.currentTimeMillis() + 2_200
            }
        }.semantics { contentDescription = if (woken) "The cottage window, dark" else "The cottage window, lit" })
        if (slot.bertX != null) {
            // Him, with a little room round his sprite. Declared last so it sits on top: when he stands in front of the
            // gate or the chalkboard, a tap on him is a pat, not a trip through the gate.
            val hit = PlaceRect(bertX + 4, 186, 74, PLACE_H - 186 - 12)
            Box(Modifier.at(hit).testTag("place-bert").combinedClickable(role = Role.Button, onClickLabel = "Give Bert a pat",
                onLongClickLabel = meetBert?.let { "Meet Bert" }, onLongClick = meetBert) { pat() }
                .semantics { contentDescription = "Bert" })
        }

    }
    // The loam runs on into the page in dithered pixel steps (no smooth gradient), so the scene has no bottom edge.
    val earth = remember(scene) { Color(scene.asAndroidBitmap().getPixel(2, PLACE_H - 1)) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val frame = placeFrame(with(density) { maxWidth.toPx() }, LocalConfiguration.current.screenHeightDp * density.density)
        val k = frame.k
        Canvas(Modifier.fillMaxWidth().height(with(density) { (8 * k).toDp() })) {
            // Under the scene only (it is narrower than the page in landscape).
            val cols = if (frame.left > 0) frame.shown else (size.width / k).toInt() + 1
            translate(left = frame.left.toFloat()) {
            for (row in 0 until 8) {
                // rows 0-1 solid loam, then 3/4, 1/2, 1/4, 1/8 of the pixels, then nothing
                val keep = when (row) { 0, 1 -> 8; 2 -> 6; 3 -> 4; 4 -> 2; 5 -> 1; else -> 0 }
                for (c in 0 until cols) {
                    val bayer = intArrayOf(0, 4, 2, 6, 1, 5, 3, 7)[(c + row * 3) % 8]
                    if (bayer < keep) drawRect(earth, androidx.compose.ui.geometry.Offset((c * k).toFloat(), (row * k).toFloat()),
                        androidx.compose.ui.geometry.Size(k.toFloat(), k.toFloat()))
                }
            }
            }
        }
    }
    // His line, in his hand, just under the ground.
    Text(line, fontFamily = BertHand, fontSize = 30.sp, lineHeight = 34.sp, color = Cream,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp).testTag("place-line")
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
    }
}

/** A hand-made chalk font in art pixels: digits, point, cent and dollar signs, seven rows tall. */
internal object ChalkFont {
    const val HEIGHT = 7
    private val glyphs = mapOf(
        '0' to listOf(".##.", "#..#", "#.##", "##.#", "#..#", "#..#", ".##."),
        '1' to listOf(".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"),
        '2' to listOf(".##.", "#..#", "...#", "..#.", ".#..", "#...", "####"),
        '3' to listOf("###.", "...#", "...#", ".##.", "...#", "...#", "###."),
        '4' to listOf("..#.", ".##.", "#.#.", "#.#.", "####", "..#.", "..#."),
        '5' to listOf("####", "#...", "###.", "...#", "...#", "#..#", ".##."),
        '6' to listOf(".##.", "#...", "#...", "###.", "#..#", "#..#", ".##."),
        '7' to listOf("####", "...#", "..#.", "..#.", ".#..", ".#..", ".#.."),
        '8' to listOf(".##.", "#..#", "#..#", ".##.", "#..#", "#..#", ".##."),
        '9' to listOf(".##.", "#..#", "#..#", ".###", "...#", "...#", ".##."),
        '.' to listOf(".", ".", ".", ".", ".", ".", "#"),
        '\u00a2' to listOf("..#.", ".###", "#.#.", "#.#.", "#.#.", ".###", "..#."),
        '$' to listOf(".#.", "###", "#..", "###", "..#", "###", ".#."),
    )
    val PAW = listOf("#...#", ".....", ".#.#.", "#####", ".###.")
    fun glyph(c: Char): List<String> = glyphs[c] ?: glyphs.getValue('.')
    fun width(text: String): Int = text.sumOf { glyph(it)[0].length + 1 } - 1
}

/** BERT under a dollar is chalked in cents ("1.54¢"); from a dollar up, in dollars ("$1.23"). */
internal fun chalkPrice(usd: Double): String = if (usd < 1.0) {
    val cents = usd * 100
    (if (cents < 0.1) String.format(java.util.Locale.US, "%.3f", cents) else if (cents < 10) String.format(java.util.Locale.US, "%.2f", cents)
    else String.format(java.util.Locale.US, "%.1f", cents)) + "\u00a2"
} else String.format(java.util.Locale.US, "$%.2f", usd)

private fun sceneDescription(slot: BertSlot, woken: Boolean): String = when (slot) {
    BertSlot.GATE -> "Morning in Bert’s garden. Bert is out by the gate."
    BertSlot.ROUNDS -> "Bert’s garden, late morning. Bert is on his rounds."
    BertSlot.NAP -> "Bert’s garden, early afternoon. Bert is asleep in the sun."
    BertSlot.POST -> "Bert’s garden in the golden afternoon. Bert is on gate duty."
    BertSlot.EVENING -> "Bert’s garden at dusk. The cottage lamp is lit."
    BertSlot.BED -> if (woken) "Night. The cottage window has gone dark." else "Night. Bert is asleep inside; the cottage window is lit."
}
