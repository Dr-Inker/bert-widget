package global.bert.widget

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.key
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import global.bert.widget.data.BERTHomeVisits
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
    // 0.14: a tester read "sun’s on the lawn. do not disturb." as lore. Now that the line comes out of Bert in a speech
    // bubble most of the bank reads plainly; the few that leaned on knowing he is "the mayor" were reworded.
    private val bank = mapOf(
        BertSlot.GATE to listOf("first one out. the gate is a formality.", "woofmornin. i have checked the hedge for news.",
            "dew on the paws. worth it.", "someone walked past at six and i saw everything.",
            "the gate opens at seven, or whenever i say."),
        BertSlot.ROUNDS to listOf("rounds. every post, every corner, every smell that matters.", "inspecting the daisies. they pass.",
            "a leaf moved and i handled it.", "the hedge has been told.", "civic walk in progress. wave only if you mean it."),
        BertSlot.NAP to listOf("sun\u2019s on the lawn, and so am i. do not disturb.", "napping under the blossom, as is my right.",
            "eyes closed. ears open. hat on.", "official nap. minutes to follow."),
        BertSlot.POST to listOf("waiting on the postman. he knows what he did.", "nothing new on the board yet. i\u2019ll stand here.",
            "gate duty. nobody gets past without a sniff.", "the post is late. this has been noted."),
        BertSlot.EVENING to listOf("lamp\u2019s lit. the hills can clock off.", "one more lap of the lawn, then home.",
            "the hat stays on until the door shuts.", "heading in. the daisies are in charge."),
        BertSlot.BED to listOf("it\u2019s late. the lamp has this.", "asleep. the hat stays on. don\u2019t ask.",
            "door\u2019s shut. dreaming, professionally.", "shh. off duty till morning."),
    )
    val pats = listOf("that was the spot.", "again. i permit it.", "careful of the hat.", "ears. yes. ears.", "a fine constituent.",
        "you may continue.")
    const val TOO_MANY = "alright. enough politics."
    const val NAPPING = "five more minutes."
    /** Asleep in the sun he still answers a pat, without waking: the first is always [NAPPING]. */
    val naps = listOf(NAPPING, "mm. left ear. good.", "still asleep. keep going.")
    const val WOKEN = "you woke the mayor."

    fun of(slot: BertSlot, day: LocalDate): String = bank.getValue(slot).let { it[(day.dayOfYear + slot.ordinal) % it.size] }

    /** Every line he can say in the bubble, so tests can check each one fits. */
    internal val every: List<String> get() = bank.values.flatten() + pats + naps + listOf(TOO_MANY, WOKEN) +
        PlaceSpot.entries.map { it.hint } + BertSlot.entries.mapNotNull { hint(it, PlaceFirsts(1, false, emptySet()), gate = true) }

    /**
     * For someone new, Bert says what the picture does instead of his daily line. These must read with no lore at all
     * (no hat, no hoodie, no mayor): they are the first words a person hears from him. Until they have patted him it is
     * one thing a day, the pat first (at night he is indoors, so it is the window). Once patted, in the same sitting, he
     * moves on to a spot they have not tried yet, one at a time, until each has been found; then his own line. Null when
     * there is nothing left to show (or the first days are over).
     */
    fun hint(slot: BertSlot, firsts: PlaceFirsts, gate: Boolean): String? {
        if (!firsts.showingRound) return null
        val spots = PlaceSpot.entries.filter { it !in firsts.tapped && (gate || it != PlaceSpot.GATE) }.map { it.hint }
        if (firsts.patted) return spots.firstOrNull()
        val first = when (slot) {
            BertSlot.BED -> "tap the window. i\u2019m a light sleeper."
            BertSlot.NAP -> "asleep. pats still accepted."
            else -> "pat me. i permit it."
        }
        val all = listOf(first) + spots
        return all[(firsts.days - 1).coerceAtLeast(0) % all.size]
    }
}

/**
 * The three things in the picture that open something, and where each one's glint sits (place art px): on the stone
 * ball of the gate's left post (clear of Bert at the gate and on gate duty), the chalkboard frame's top corner and the
 * noticeboard's roof. Each sits on something darker than the sky, so a pale star shows by day.
 */
internal enum class PlaceSpot(val key: String, val glintX: Int, val glintY: Int, val hint: String) {
    GATE("gate", 262, 226, "the gate goes somewhere. try it."),
    CHALK("chalk", 181, 248, "the chalkboard knows the price."),
    BOARD("board", 366, 255, "the noticeboard has today\u2019s news."),
}

/** How long the place keeps showing a newcomer around: Bert's hints stop after this many distinct days. */
internal const val FIRST_DAYS = 5

/**
 * What a person has found in the place so far: [days] they have come round (ever, counted once a day), whether they
 * have [patted] Bert, and which spots they have [tapped].
 */
internal data class PlaceFirsts(val days: Int, val patted: Boolean, val tapped: Set<PlaceSpot>) {
    val showingRound: Boolean get() = days <= FIRST_DAYS
}

/**
 * The newcomer state, in the same `bert_place` preferences as [BertVisits]. It only ever counts up: nothing resets,
 * and nothing is lost by staying away.
 */
internal class PlaceDiscovery(private val context: Context) {
    private val prefs = context.getSharedPreferences("bert_place", Context.MODE_PRIVATE)

    /** Counts [today] once (a clock set backwards neither counts nor rewinds) and returns what has been found. */
    fun record(today: LocalDate): PlaceFirsts {
        // These keys are new in 0.14, so someone updating from 0.13 has none and would be greeted as a stranger in a
        // garden they have visited for weeks. Anyone who came round before today (a paw print on another day, kept since
        // well before 0.14, or the app opened on another day by an older version) starts as having patted Bert and found
        // every spot. Only the first record after the update can do this; a fresh install leaves no such trace, even when
        // its first days were spent in Market from the widget (0.14 counts those opens, but not as an older version's).
        if (!prefs.contains(KEY_SEEN_DAYS) && cameBefore(today)) {
            prefs.edit().putBoolean(KEY_PATTED, true).putString(KEY_TAPPED, PlaceSpot.entries.joinToString(",") { it.key }).apply()
        }
        val last = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE)
        var days = prefs.getInt(KEY_SEEN_DAYS, 0)
        if (today.toEpochDay() > last) {
            days += 1
            prefs.edit().putLong(KEY_LAST_DAY, today.toEpochDay()).putInt(KEY_SEEN_DAYS, days).apply()
        }
        val tapped = prefs.getString(KEY_TAPPED, "").orEmpty().split(',').mapNotNull { k -> PlaceSpot.entries.firstOrNull { it.key == k } }
        return PlaceFirsts(days, prefs.getBoolean(KEY_PATTED, false), tapped.toSet())
    }

    fun patted() { prefs.edit().putBoolean(KEY_PATTED, true).apply() }

    private fun cameBefore(today: LocalDate): Boolean =
        BertVisits(context).cameBefore(today) || BERTHomeVisits(context).openedBefore014(today)

    fun tapped(spot: PlaceSpot, firsts: PlaceFirsts): PlaceFirsts {
        if (spot in firsts.tapped) return firsts
        val next = firsts.copy(tapped = firsts.tapped + spot)
        prefs.edit().putString(KEY_TAPPED, next.tapped.joinToString(",") { it.key }).apply()
        return next
    }

    private companion object {
        const val KEY_LAST_DAY = "first_last_day"
        const val KEY_SEEN_DAYS = "first_days"
        const val KEY_PATTED = "patted"
        const val KEY_TAPPED = "tapped"
    }
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

    /**
     * The days this month the person came round, for a screen that only shows them (the You page). Unlike [record] it
     * never writes, so looking at your visits is never itself a visit.
     */
    fun thisMonth(day: LocalDate): Set<Int> = if (prefs.getString(KEY_MONTH, null) == "${day.year}-${day.monthValue}") days() else emptySet()

    /** Whether a paw print was left on a day before [today]: in an earlier month, or on another day of this one. */
    fun cameBefore(today: LocalDate): Boolean {
        val month = prefs.getString(KEY_MONTH, null) ?: return false
        return month != "${today.year}-${today.monthValue}" || days().any { it != today.dayOfMonth }
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
    /** The drawn scene in the page's pixels. */
    val sceneRect: IntRect get() = IntRect(left, 0, left + shown * k, heightPx)
    /** Art rect [r], padded by [pad] art px, in the page's pixels. */
    fun px(x: Int, y: Int, w: Int, h: Int, pad: Int = 0) = IntRect(left + (x - pad - x0) * k, (y - pad) * k, left + (x + w + pad - x0) * k, (y + h + pad) * k)
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
// Bert's face and hat in an 84 px standing frame, either way he faces (from the sprites' own pixels): x 18..66, rows 0..46.
private const val FACE_X = 18
private const val FACE_W = 48
private const val FACE_H = 48
private val BOARD = PlaceRect(356, 250, 34, 34)       // the noticeboard, standing against the wall past the gate
private val CHALK = PlaceRect(138, 248, 48, 36)      // the A-frame chalkboard between the blossom and the gate
private val SLATE = PlaceRect(148, 251, 32, 24)      // its writable slate
private val GATEWAY = PlaceRect(248, 211, 92, 72)    // the picket gate and its posts: through it, The Lost Trail
private val WINDOW = PlaceRect(262, 104, 28, 32)

private val SLATE_FACE = PlaceRect(150, 252, 30, 23) // the slate inside its frame, which lightens under a finger
private val BOARD_FACE = PlaceRect(362, 260, 22, 15) // the noticeboard's back panel, papers and all
private val PICKETS = PlaceRect(272, 240, 45, 41)    // the picket gate between its posts, which jumps when pressed
private val GATE_SIGN = PlaceRect(252, 240, 84, 43)  // the gate for its label: post to post, from the pickets' tops
// Where the fresh note goes (the bare corner of the board's panel, over the old notes' edge), and where the board's own
// paper and panel colours are read from.
private const val NOTE_X = 375
private const val NOTE_Y = 261
private const val NOTE_W = 7
private const val NOTE_H = 8
private const val BOARD_PAPER_X = 368
private const val BOARD_PAPER_Y = 264
private const val BOARD_PANEL_X = 378
private const val BOARD_PANEL_Y = 262
/** The board's paper in the day render: the pin's red is scaled by how far the light has moved the paper from this. */
private val DAY_PAPER = Color(0xFFF2E7CA)
private val PIN = Color(0xFFD23A2E)
private val PIN_SHINE = Color(0xFFFF9A80)
private val PIN_SHADE = Color(0xFF8A1E18)
/** A press: the face it lands on catches the light, darker things gaining the most in absolute terms. */
private val PRESS_LIGHT = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    1.5f, 0f, 0f, 0f, 6f,
    0f, 1.5f, 0f, 0f, 6f,
    0f, 0f, 1.5f, 0f, 8f,
    0f, 0f, 0f, 1f, 0f)))
/** The least a tap target in the picture keeps when it has to move out from under the You button. */
private val MIN_TARGET = 32.dp
/** How long a press outlives the finger (a quick tap's press arrives with its release, in a scrolling page). */
private const val PRESS_HELD_MS = 180L

private data class PlaceRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** A tap target in the picture, where the art puts it ([natural], page px), and whether it may be moved for the You button. */
internal data class PlaceTarget(val natural: IntRect, val movable: Boolean)

/**
 * 0.14 labels (owner on the S25: the pixel cues "are minute on a phone screen"): a small tag in the You button's style on
 * each thing in the picture that opens something. [ABOVE] it, its tail pointing down at it; to its [RIGHT] or [LEFT],
 * pointing across; or [OVER] its foot, with no tail.
 */
internal enum class LabelSide { ABOVE, RIGHT, LEFT, OVER }

/** What a label may say, longest first: the "›" goes first, then the words shorten. */
internal fun labelTexts(spot: PlaceSpot, newNotes: Int): List<String> = when (spot) {
    PlaceSpot.CHALK -> listOf("Price ›", "Price")
    PlaceSpot.GATE -> listOf("Play ›", "Play")
    PlaceSpot.BOARD -> if (newNotes > 0) {
        val n = if (newNotes > 9) "9+" else "$newNotes"
        listOf("News · $n", "News $n", n)
    } else listOf("News")
}

/** A label's measures, in page px: its tail's length and half-width, its gap from its thing, its margin, its distance from others. */
internal data class LabelDims(val tail: Int, val tailHalf: Int, val gap: Int, val margin: Int, val apart: Int)

/**
 * A placed label: which of its texts, its tag's box, which side of its thing, and where its tail's tip is (page px);
 * [floating] when, for want of room, it stands clear above its thing rather than touching it.
 */
internal data class PlacedLabel(val text: String, val box: IntRect, val side: LabelSide, val tip: IntOffset, val floating: Boolean = false) {
    /** The box and its tail: everything the label covers. */
    val bounds: IntRect get() = when (side) {
        LabelSide.ABOVE -> IntRect(box.left, box.top, box.right, tip.y)
        LabelSide.RIGHT -> IntRect(tip.x, box.top, box.right, box.bottom)
        LabelSide.LEFT -> IntRect(box.left, box.top, tip.x, box.bottom)
        LabelSide.OVER -> box
    }
}

/**
 * Where each thing's label prefers to go, in order. The chalkboard's goes beside it (above it is where Bert's face
 * passes on his rounds, and over it would hide the price); the gate's hangs on the gate, like a sign, or else stands
 * on its pickets' tops; the board's sits on its roof.
 */
private fun labelSides(spot: PlaceSpot) = when (spot) {
    PlaceSpot.CHALK -> listOf(LabelSide.RIGHT, LabelSide.ABOVE, LabelSide.LEFT, LabelSide.OVER)
    PlaceSpot.GATE -> listOf(LabelSide.OVER, LabelSide.ABOVE)
    PlaceSpot.BOARD -> listOf(LabelSide.ABOVE, LabelSide.LEFT, LabelSide.OVER)
}

/**
 * Each labelled thing's art, unpadded: what its label points at, and what other labels keep off. The gate's is the
 * gate itself, post to post from the pickets' tops down (GATE_SIGN), not its whole tap target, whose top is the post
 * balls' sky: a label pointing there floated over nothing.
 */
internal fun labelAnchors(frame: PlaceFrame, gate: Boolean): Map<PlaceSpot, IntRect> = buildMap {
    put(PlaceSpot.CHALK, frame.px(CHALK.x, CHALK.y, CHALK.w, CHALK.h))
    if (gate) put(PlaceSpot.GATE, frame.px(GATE_SIGN.x, GATE_SIGN.y, GATE_SIGN.w, GATE_SIGN.h))
    put(PlaceSpot.BOARD, frame.px(BOARD.x, BOARD.y, BOARD.w, BOARD.h))
}

/**
 * What no label may cover in [slot] (page px): Bert's face wherever it can be in this part of the day (his walk, a
 * pat's hop, facing either way), so the labels never move as he walks; the chalked price; his bubble's tail; and at
 * night the window's target (where it finally is) and the dream puffs rising from his bubble. The You button is added
 * by the caller.
 */
internal fun labelKeepOff(frame: PlaceFrame, slot: BertSlot, window: IntRect?): List<IntRect> = buildList {
    val xs = listOfNotNull(slot.bertX, slot.walk?.first, slot.walk?.last)
    if (slot == BertSlot.NAP) slot.bertX?.let { add(frame.px(it + 7, PLACE_NAP_TOP + 12, 70, 38)) }
    else if (xs.isNotEmpty()) add(frame.px(xs.min() + FACE_X, PLACE_FEET_TOP - 4, xs.max() - xs.min() + FACE_W, FACE_H + 4))
    add(frame.px(SLATE.x, SLATE.y + 6, SLATE.w, 12))
    // The bubble's tail, which rises from the loam to just under his feet wherever he is that part of the day.
    if (xs.isNotEmpty() && slot != BertSlot.NAP) add(frame.px(xs.min() + BERT_MID - 10, 282, xs.max() - xs.min() + 20 + 8, PLACE_H - 282))
    if (slot == BertSlot.NAP) slot.bertX?.let { add(frame.px(it + BERT_MID - 10, 282, 28, PLACE_H - 282)) }
    if (slot == BertSlot.BED) {
        window?.let { add(it) }
        // The puffs: three, leaning either way from under the window, from the loam up to row 270 (BertSays).
        add(frame.px(WINDOW_MID - 14, 269, 28, PLACE_H - 269))
    }
}

/** Labels end above this row: the top of Bert's bubble, which stands a little into the scene's loam. */
internal fun labelGround(frame: PlaceFrame): Int = LABEL_GROUND * frame.k

/**
 * Where each label goes. For each thing, each of its texts (longest first), each side it prefers, and nudges along that
 * side, the first box that stays inside [scene] by [LabelDims.margin], ends above [ground] (the bubble's loam), keeps its
 * tail on its thing, and covers nothing in [keepOff], no other thing's middle and no label already placed (by
 * [LabelDims.apart]); the things are taken in whichever order shortens and drops the least. A label that fits nowhere is
 * left out (null) rather than let anything collide.
 * [measure] sizes a text's tag. Pure, so it can be swept with the targets.
 */
internal fun placeLabels(anchors: Map<PlaceSpot, IntRect>, texts: (PlaceSpot) -> List<String>, measure: (String) -> IntSize,
                         scene: IntRect, ground: Int, keepOff: List<IntRect>, dims: LabelDims): Map<PlaceSpot, PlacedLabel?> {
    // The other things' middles (each art inset by a third across, a quarter down): a label may lean on another thing's
    // edge, a post or a frame, but never sits on the thing itself, where it would read as that thing's.
    val middles = anchors.mapValues { (_, it) -> IntRect(it.left + it.width / 3, it.top + it.height / 4, it.right - it.width / 3, it.bottom - it.height / 4) }
    val sizes = HashMap<String, IntSize>()
    fun sizeOf(text: String) = sizes.getOrPut(text) { measure(text) }

    // Every way one label could go, best first: its longest text before shorter ones; for each, its preferred sides in
    // turn, each with small nudges along it and then larger ones (and a sign on its thing lifted up it if its foot is
    // taken), before the next side.
    fun candidates(spot: PlaceSpot, a: IntRect): Sequence<PlacedLabel> = sequence {
        for (text in texts(spot)) {
            val size = sizeOf(text)
            val w = size.width
            val h = size.height
            val r = h / 2 + dims.tailHalf
            for (side in labelSides(spot)) for (lift in if (side == LabelSide.OVER) listOf(0, h / 2, h) else listOf(0)) for (tier in listOf(0..2, 3..6)) {
                val span = if (side == LabelSide.ABOVE || side == LabelSide.OVER) w else h
                for (step in tier) for (d in if (step == 0) listOf(0) else listOf(step * span / 6, -step * span / 6)) {
                    val label = when (side) {
                        LabelSide.ABOVE -> {
                            val left = a.left + a.width / 2 - w / 2 + d
                            val bottom = a.top - dims.gap - dims.tail
                            if (w < 2 * r) continue
                            val tipX = (a.left + a.width / 2).coerceIn(left + r, left + w - r)
                            if (tipX < a.left + dims.tailHalf || tipX > a.right - dims.tailHalf) continue
                            PlacedLabel(text, IntRect(left, bottom - h, left + w, bottom), side, IntOffset(tipX, a.top - dims.gap))
                        }
                        LabelSide.RIGHT, LabelSide.LEFT -> {
                            val top = a.top + a.height / 2 - h / 2 + d
                            val tipY = (a.top + a.height / 2).coerceIn(top + h / 4, top + h - h / 4)
                            if (tipY < a.top + dims.tailHalf || tipY > a.bottom - dims.tailHalf) continue
                            if (side == LabelSide.RIGHT) {
                                val left = a.right + dims.gap + dims.tail
                                PlacedLabel(text, IntRect(left, top, left + w, top + h), side, IntOffset(a.right + dims.gap, tipY))
                            } else {
                                val right = a.left - dims.gap - dims.tail
                                PlacedLabel(text, IntRect(right - w, top, right, top + h), side, IntOffset(a.left - dims.gap, tipY))
                            }
                        }
                        LabelSide.OVER -> {
                            val left = a.left + a.width / 2 - w / 2 + d
                            // On its foot, or lifted up it (at night the dream puffs rise past the gate's foot).
                            val bottom = minOf(a.bottom, ground) - dims.gap - lift
                            if (bottom - h < a.top) continue
                            val box = IntRect(left, bottom - h, left + w, bottom)
                            if (minOf(box.right, a.right) - maxOf(box.left, a.left) < minOf(w, a.width) / 2) continue
                            PlacedLabel(text, box, side, IntOffset((a.left + a.width / 2).coerceIn(box.left, box.right), bottom))
                        }
                    }
                    yield(label)
                }
            }
        }
        // Last of all, only where nothing nearer fits any of its texts (a small, dense or large-text screen): standing a
        // little clear above its thing, its tail pointing down at it from the air, rather than not there at all.
        for (text in texts(spot)) {
            val size = sizeOf(text)
            val w = size.width
            val h = size.height
            val r = h / 2 + dims.tailHalf
            if (w < 2 * r) continue
            for (lift in listOf(h / 2, h)) for (step in 0..6) for (d in if (step == 0) listOf(0) else listOf(step * w / 6, -step * w / 6)) {
                val left = a.left + a.width / 2 - w / 2 + d
                val bottom = a.top - dims.gap - dims.tail - lift
                val tipX = (a.left + a.width / 2).coerceIn(left + r, left + w - r)
                if (tipX < a.left + dims.tailHalf || tipX > a.right - dims.tailHalf) continue
                yield(PlacedLabel(text, IntRect(left, bottom - h, left + w, bottom), LabelSide.ABOVE, IntOffset(tipX, bottom + dims.tail), floating = true))
            }
        }
    }

    // One pass in a given order: each label takes the first way that is clear of everything so far.
    fun pass(order: List<PlaceSpot>): Map<PlaceSpot, PlacedLabel?> {
        val placed = LinkedHashMap<PlaceSpot, PlacedLabel?>()
        for (spot in order) {
            val a = anchors.getValue(spot)
            val others = middles.filterKeys { it != spot }.values
            fun clear(b: IntRect): Boolean = b.left >= scene.left + dims.margin && b.right <= scene.right - dims.margin &&
                b.top >= scene.top + dims.margin && b.bottom <= ground && keepOff.none { it.overlaps(b) } && others.none { it.overlaps(b) } &&
                placed.values.filterNotNull().none { p ->
                    IntRect(p.bounds.left - dims.apart, p.bounds.top - dims.apart, p.bounds.right + dims.apart, p.bounds.bottom + dims.apart).overlaps(b)
                }
            // Its own thing is the one art a label may sit on (OVER), so only the others' middles are checked.
            placed[spot] = candidates(spot, a).firstOrNull { clear(it.bounds) }
        }
        return placed
    }

    // Whichever order of placing them leaves the fewest out, then the fewest shortened, then the most on their preferred
    // sides (the things are few: every order is tried, left to right first, so the answer is the same every time).
    fun cost(p: Map<PlaceSpot, PlacedLabel?>): Int = p.entries.sumOf { (spot, l) ->
        if (l == null) 1000 else 100 * (if (l.floating) 1 else 0) + 10 * texts(spot).indexOf(l.text) + labelSides(spot).indexOf(l.side)
    }
    val leftToRight = anchors.entries.sortedBy { it.value.left }.map { it.key }
    return orders(leftToRight).map { pass(it) }.minBy(::cost).let { best -> anchors.keys.associateWith { best[it] } }
}

/** Every order of [items], the given one first. */
private fun <T> orders(items: List<T>): List<List<T>> =
    if (items.size <= 1) listOf(items) else items.flatMap { first -> orders(items - first).map { listOf(first) + it } }

/**
 * The tap targets for [slot] in [frame], by test tag, where the art puts them. Bert's ([bertX], his left edge in art px;
 * he walks) is never moved: the button may cover part of him and the rest of him is still his.
 */
internal fun placeNaturals(frame: PlaceFrame, slot: BertSlot, bertX: Int, gate: Boolean): Map<String, PlaceTarget> = buildMap {
    fun at(r: PlaceRect, pad: Int) = frame.px(r.x, r.y, r.w, r.h, pad)
    put("place-board", PlaceTarget(at(BOARD, 6), true))
    put("place-tag", PlaceTarget(at(CHALK, 6), true))
    if (gate) put("place-gate", PlaceTarget(at(GATEWAY, 0), true))
    if (slot == BertSlot.BED) put("place-window", PlaceTarget(at(WINDOW, 8), true))
    if (slot.bertX != null) put("place-bert", PlaceTarget(at(PlaceRect(bertX + 4, 186, 74, PLACE_H - 186 - 12), 0), false))
}

/**
 * Where each target finally goes, so that none sits under [overlay] (Home's floating You button, page px) where that can
 * be helped. A target the button doesn't cover stays exactly where the art puts it. One it covers, if movable, goes
 * below the button, or else beside it (to its left, at its own height): whichever first fits inside [scene], keeps at
 * least [minTarget] (or its own size, if smaller) each way, and meets neither the button nor any other target, neither
 * where that target finally is nor where its art is (so a tap on the gate's pickets is always the gate). If neither fits
 * it stays where the art puts it; the button then covers part of it and the rest still answers.
 *
 * Bert is no obstacle: his target is on top of everything and may overlap anything by design (he stands in front of the
 * gate and the chalkboard; a tap on him is a pat), and as he walks the others must not jitter round him. Covered
 * targets are placed top-down, then left to right, so the answer never depends on the order they are listed in. Pure,
 * so the whole range of phones can be swept in a test.
 */
internal fun placeTargets(targets: Map<String, PlaceTarget>, overlay: IntRect, scene: IntRect, minTarget: Int): Map<String, IntRect> {
    val finals = targets.mapValuesTo(LinkedHashMap()) { it.value.natural }
    fun covered(r: IntRect) = r.left < overlay.right && r.right > overlay.left && r.top < overlay.bottom && r.bottom > overlay.top
    val moving = targets.filter { (_, t) -> t.movable && covered(t.natural) }.keys
        .sortedWith(compareBy({ targets.getValue(it).natural.top }, { targets.getValue(it).natural.left }, { it }))
    for (tag in moving) {
        val n = targets.getValue(tag).natural
        // Everything a moved target must keep off: each other movable target where its art is and where it now is.
        val blockers = targets.filter { (other, t) -> other != tag && t.movable }.keys.flatMap { listOf(targets.getValue(it).natural, finals.getValue(it)) }
        val needW = minOf(minTarget, n.width)
        val needH = minOf(minTarget, n.height)
        fun fits(c: IntRect) = c.left >= scene.left && c.top >= scene.top && c.right <= scene.right && c.bottom <= scene.bottom &&
            c.width >= needW && c.height >= needH && !c.overlaps(overlay) && blockers.none { it.overlaps(c) }
        // Below the button, down to whatever is under it in the same columns, or the foot of the scene.
        val below = run {
            val top = overlay.bottom
            var bottom = minOf(maxOf(n.bottom, top + minTarget), scene.bottom)
            for (o in blockers) if (o.left < n.right && o.right > n.left && o.bottom > top) bottom = minOf(bottom, maxOf(o.top, top))
            IntRect(n.left, top, n.right, maxOf(bottom, top))
        }
        // Beside it: ending at its left edge, at the target's own height, a finger's size at least.
        val beside = run {
            val w = maxOf(n.width, minTarget)
            val h = maxOf(n.height, minTarget)
            val left = maxOf(scene.left, overlay.left - w)
            val top = maxOf(scene.top, minOf(n.top, scene.bottom - h))
            IntRect(left, top, left + w, top + h)
        }
        finals[tag] = listOf(below, beside).firstOrNull(::fits) ?: n
    }
    return finals
}

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
    overlay: androidx.compose.ui.unit.IntRect = androidx.compose.ui.unit.IntRect.Zero,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val slot = placeTimeAt(now, zone)
    val time = slot.time
    val visits = remember(today) { BertVisits(context.applicationContext).record(today) }
    val discovery = remember { PlaceDiscovery(context.applicationContext) }
    var firsts by remember(today) { mutableStateOf(discovery.record(today)) }

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
    // The labels say what can be tapped (0.14, the owner's S25); a newcomer's endless glints next to them were clutter,
    // so they went. What stays is one quiet twinkle pass whenever Home comes back into view: each spot's star swells
    // once, left to right, and the garden rests. Never a loop; with animations off, nothing. Each spot's level (0 to 4)
    // is packed into one state, read only when drawing, so a beat redraws the scene and never recomposes it; [twinkling]
    // covers the whole pass, the half second before the first star included, and is read only by the tap targets'
    // semantics, so it invalidates those, not the composition.
    val spots = PlaceSpot.entries.filter { it != PlaceSpot.GATE || openTrail != null }
    // Left to right, the order the stars take their turns in.
    val order = spots.sortedBy { it.glintX }
    var twinkles by remember { mutableIntStateOf(0) }
    var twinkling by remember { mutableStateOf(false) }
    LaunchedEffect(still, order, lifecycle) {
        if (still) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            try {
                twinkling = true
                delay(TWINKLE_DELAY_MS)
                for (beat in 0 until TWINKLE_BEATS) {
                    twinkles = packGlints(order) { i -> glintLevel(beat - i * TWINKLE_STAGGER) }
                    delay(GLINT_BEAT_MS)
                }
            } finally { twinkles = 0; twinkling = false }
        }
    }
    // A spot being pressed, held a moment past the finger so a quick tap still shows (see go() at the tap targets).
    var pressed by remember { mutableStateOf<PlaceSpot?>(null) }
    val presses = remember { PlaceSpot.entries.associateWith { MutableInteractionSource() } }
    LaunchedEffect(presses) {
        for ((spot, source) in presses) launch {
            source.interactions.collectLatest { i ->
                when (i) {
                    is PressInteraction.Press -> pressed = spot
                    is PressInteraction.Release, is PressInteraction.Cancel -> { delay(PRESS_HELD_MS); if (pressed == spot) pressed = null }
                }
            }
        }
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
    // The noticeboard's own paper and panel in this light, for the fresh note pinned over them.
    val boardInk = remember(scene) {
        val bitmap = scene.asAndroidBitmap()
        Color(bitmap.getPixel(BOARD_PAPER_X + WIDE_SHIFT, BOARD_PAPER_Y)) to Color(bitmap.getPixel(BOARD_PANEL_X + WIDE_SHIFT, BOARD_PANEL_Y))
    }
    // What he says when nobody is patting him: his line for the time of day, or for a newcomer, a hint.
    val resting = BertLines.hint(slot, firsts, gate = openTrail != null) ?: BertLines.of(slot, today)
    val line = said ?: resting

    // The first pat ever (or, at night, the first knock on his window) ends his asking for one; once this reply has been
    // said he moves on to the spots not yet found (BertLines.hint), and his own line comes back when they all have been.
    fun greeted() { if (!firsts.patted) { discovery.patted(); firsts = firsts.copy(patted = true) } }

    fun pat() {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (!still) scope.launch { for (h in intArrayOf(2, 4, 3, 1, 0)) { lift = h; delay(45) } }
        val at = System.currentTimeMillis()
        greeted()
        pats = if (at - lastPat < 4_000) pats + 1 else 1
        lastPat = at
        if (slot == BertSlot.NAP) { said = BertLines.naps[(pats - 1) % BertLines.naps.size]; patUntil = at + 1_600; return }
        said = if (pats >= 5) BertLines.TOO_MANY else BertLines.pats[(pats - 1 + today.dayOfYear) % BertLines.pats.size]
        patUntil = at + 1_400
    }

    Column(modifier.fillMaxWidth()) {
    // The scene is a picture: its tap targets are placed in the picture's own left-to-right pixels, so a right-to-left
    // phone (supportsRtl) must not mirror them away from the art the Canvas draws unmirrored.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    // One traversal group: the reading order set inside (traversalIndex) orders the picture's own parts, and the
    // picture is read as one unit where it sits on the page, before Bert's bubble and the page below.
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
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
            // A pressed spot answers the finger: the slate and the board's face catch the light, the gate's pickets jump.
            // Redrawn from the scene itself, so the press is in the same light as everything else.
            fun sceneAt(r: PlaceRect, dy: Int = 0, filter: ColorFilter? = null) = drawImage(scene, srcOffset = IntOffset(r.x + WIDE_SHIFT, r.y),
                srcSize = IntSize(r.w, r.h), dstOffset = IntOffset((r.x - x0) * k, (r.y + dy) * k), dstSize = IntSize(r.w * k, r.h * k),
                filterQuality = FilterQuality.None, colorFilter = filter)
            when (pressed) {
                PlaceSpot.CHALK -> sceneAt(SLATE_FACE, filter = PRESS_LIGHT)
                PlaceSpot.BOARD -> sceneAt(BOARD_FACE, filter = PRESS_LIGHT)
                PlaceSpot.GATE -> sceneAt(PICKETS, dy = -2)
                null -> {}
            }
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
            // News: a fresh note pinned over the old ones, a second one behind it when there's more than one. Its paper
            // is the board's own paper in this light, a shade brighter for being new; the pin is Bert's hat red.
            if (newNotes > 0) {
                val (paper, panel) = boardInk
                // By day the new paper is near white against the old cream; in the dark it is only a shade up on it.
                val fresh = if (time.light) lerp(paper, Color.White, 0.6f)
                    else Color(minOf(1f, paper.red * 1.18f), minOf(1f, paper.green * 1.18f), minOf(1f, paper.blue * 1.18f))
                val shadow = Color.Black.copy(alpha = 0.35f)
                fun note(x: Int, y: Int, sheet: Color) {
                    px(x + 1, y + NOTE_H, shadow, NOTE_W, 1); px(x + NOTE_W, y + 1, shadow, 1, NOTE_H)
                    px(x, y, sheet, NOTE_W, NOTE_H)
                    val ink = panel.copy(alpha = 0.55f)
                    px(x + 1, y + 3, ink, NOTE_W - 2, 1); px(x + 1, y + 5, ink, NOTE_W - 3, 1)
                }
                if (newNotes > 1) note(NOTE_X - 3, NOTE_Y + 2, paper)
                note(NOTE_X, NOTE_Y, fresh)
                // A round pin, its red carried by the light the paper is in, so at dusk and night it is a dim red, not a
                // lamp; but a little warmer than the light alone would leave it, so news still shows after dark.
                fun lit(c: Color): Color {
                    val l = Color(minOf(1f, c.red * paper.red / DAY_PAPER.red), minOf(1f, c.green * paper.green / DAY_PAPER.green),
                        minOf(1f, c.blue * paper.blue / DAY_PAPER.blue))
                    return if (time.light) l else lerp(l, c, 0.3f)
                }
                val pin = NOTE_X + NOTE_W / 2
                px(pin, NOTE_Y - 1, lit(PIN)); px(pin - 1, NOTE_Y, lit(PIN_SHINE)); px(pin, NOTE_Y, lit(PIN)); px(pin + 1, NOTE_Y, lit(PIN))
                px(pin, NOTE_Y + 1, lit(PIN_SHADE))
            }
            // Glints: a four-point star that swells and fades, once a spot when Home comes back; a pressed spot flashes
            // at its peak.
            for (spot in order) {
                val level = if (pressed == spot) GLINT_PEAK else glintOf(twinkles, spot)
                if (level == 0) continue
                val a = if (time.light) 0.95f else 0.8f
                val arm = Color(0xFFFFF6D8)
                val (gx, gy) = spot.glintX to spot.glintY
                fun ring(d: Int, alpha: Float) {
                    val c = (if (d >= 3) GLINT_WARM else arm).copy(alpha = a * alpha)
                    px(gx - d, gy, c); px(gx + d, gy, c); px(gx, gy - d, c); px(gx, gy + d, c)
                }
                fun corners(alpha: Float) {
                    val c = GLINT_WARM.copy(alpha = a * alpha)
                    px(gx - 1, gy - 1, c); px(gx + 1, gy - 1, c); px(gx - 1, gy + 1, c); px(gx + 1, gy + 1, c)
                }
                when (level) {
                    1 -> { px(gx, gy, Color.White.copy(alpha = a * 0.8f)); ring(1, 0.35f) }
                    2 -> { px(gx, gy, Color.White.copy(alpha = a)); ring(1, 1f); ring(2, 0.45f) }
                    3 -> { px(gx, gy, Color.White.copy(alpha = a)); ring(1, 1f); ring(2, 1f); ring(3, 0.5f); corners(0.35f) }
                    else -> { px(gx, gy, Color.White.copy(alpha = a)); ring(1, 1f); ring(2, 1f); ring(3, 0.6f); ring(4, 0.3f); corners(0.45f) }
                }
            }
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
        // Where each target goes (placeTargets): where the art is, unless Home's floating You button covers it.
        val minTarget = with(density) { MIN_TARGET.roundToPx() }
        val naturals = placeNaturals(frame, slot, bertX, gate = openTrail != null)
        val finals = placeTargets(naturals, overlay, frame.sceneRect, minTarget)
        fun Modifier.at(tag: String): Modifier {
            val p = finals.getValue(tag)
            return this.offset(p.left.dp(), p.top.dp()).size(p.width.dp(), p.height.dp())
        }
        // A spot is found for good the first time it is tapped (Bert stops hinting at it). The press is drawn in the picture (no ripple box over
        // the pixels) and held a moment past the tap, so even a quick one is seen to land: Home stays in view under the
        // board, crossfades out to Market and sits behind the trail's opening, all for longer than the hold.
        fun found(spot: PlaceSpot) { firsts = discovery.tapped(spot, firsts) }
        fun go(spot: PlaceSpot, open: () -> Unit) {
            found(spot)
            pressed = spot
            open()
            scope.launch { delay(PRESS_HELD_MS); if (pressed == spot) pressed = null }
        }
        // What each thing is called. Its label's pill leads with the label's word, as Voice Access matches what is said
        // against what is shown ("tap Price", "tap Play", "tap News"); the thing itself does not, so the two are not one
        // name twice (no "which one?" from Voice Access). TalkBack still stops on both, the thing and then its pill, as
        // the pill has to answer to touch-exploration; the two read alike but not the same.
        fun named(spot: PlaceSpot): String = when (spot) {
            PlaceSpot.CHALK -> priceUsd?.let { "Chalkboard: BERT ${formatPrice(it)}" } ?: "Chalkboard"
            PlaceSpot.GATE -> "The garden gate to The Lost Trail"
            PlaceSpot.BOARD -> if (newNotes > 0) "Noticeboard, $newNotes new" else "Noticeboard: today\u2019s note from Berthalla"
        }
        fun labelled(spot: PlaceSpot): String = when (spot) {
            PlaceSpot.CHALK -> priceUsd?.let { "Price: chalkboard, BERT ${formatPrice(it)}" } ?: "Price: chalkboard"
            PlaceSpot.GATE -> "Play: the garden gate to The Lost Trail"
            PlaceSpot.BOARD -> if (newNotes > 0) "News, $newNotes new: noticeboard" else "News: today\u2019s note from Berthalla"
        }
        val actions = mapOf(PlaceSpot.CHALK to "Open Market", PlaceSpot.GATE to "Go through the gate", PlaceSpot.BOARD to "Read the noticeboard")
        // Reading order across the picture, left to right: the scene, each thing then its label, the window, Bert.
        fun read(spot: PlaceSpot): Float = 1f + order.indexOf(spot)
        fun Modifier.spot(spot: PlaceSpot, open: () -> Unit) = this
            .clickable(interactionSource = presses.getValue(spot), indication = null, role = Role.Button, onClickLabel = actions.getValue(spot)) { go(spot, open) }
            .semantics {
                contentDescription = named(spot)
                traversalIndex = read(spot)
                placeTwinkle = twinkling
                placePressed = pressed == spot
            }
        Box(Modifier.at("place-board").testTag("place-board").spot(PlaceSpot.BOARD, openBoard).semantics { placeNews = newNotes > 0 })
        if (openTrail != null) Box(Modifier.at("place-gate").testTag("place-gate").spot(PlaceSpot.GATE, openTrail))
        Box(Modifier.at("place-tag").testTag("place-tag").spot(PlaceSpot.CHALK, openMarket))
        if (slot == BertSlot.BED) Box(Modifier.at("place-window").testTag("place-window").clickable(role = Role.Button, onClickLabel = "Tap the window") {
            if (!woken) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                // His night hint asks for exactly this, so it counts as the pat would: otherwise he asks again once he's back to sleep.
                greeted()
                said = BertLines.WOKEN
                wokenUntil = System.currentTimeMillis() + 2_200
            }
        }.semantics { contentDescription = if (woken) "The cottage window, dark" else "The cottage window, lit"; traversalIndex = 8f })
        if (slot.bertX != null) {
            // Him, with a little room round his sprite. Declared after the things so he sits on top of them: when he stands
            // in front of the gate or the chalkboard, a tap on him is a pat, not a trip through the gate.
            Box(Modifier.at("place-bert").testTag("place-bert").combinedClickable(role = Role.Button, onClickLabel = "Give Bert a pat",
                onLongClickLabel = meetBert?.let { "Meet Bert" }, onLongClick = meetBert) { pat() }
                .semantics { contentDescription = "Bert"; traversalIndex = 9f })
        }
        // The labels, declared after Bert so that what you see is what you tap: a label drawn over him takes the taps on
        // its tag (the pill; its tail and the space round it are his). A tap on a label is a tap on its thing (found,
        // press and all), through a stock clickable, so cancels, fling-stops and slides off behave as anywhere else. To
        // TalkBack a pill is its thing too (its name, a button, its action), so touching it to explore reads what it says
        // and a double-tap does what it does; read right after its thing. Not a keyboard stop: the thing's target is.
        val fontScale = density.fontScale
        val baseStyle = androidx.compose.material3.LocalTextStyle.current
        val labelStyle = remember(baseStyle, fontScale) {
            baseStyle.copy(color = Cream, fontSize = (LABEL_SP * minOf(fontScale, LABEL_SCALE_MAX) / fontScale).sp,
                lineHeight = (LABEL_SP * 1.2f * minOf(fontScale, LABEL_SCALE_MAX) / fontScale).sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val pad = remember(density) { with(density) { IntSize(LABEL_PAD_X.roundToPx(), LABEL_PAD_Y.roundToPx()) } }
        val dims = remember(density) { with(density) { LabelDims(LABEL_TAIL.roundToPx(), LABEL_TAIL_HALF.roundToPx(), 2.dp.roundToPx(), 6.dp.roundToPx(), 6.dp.roundToPx()) } }
        val labels = remember(frame, slot, newNotes, openTrail != null, overlay, finals["place-window"], labelStyle, dims) {
            placeLabels(labelAnchors(frame, openTrail != null), { labelTexts(it, newNotes) },
                { text -> measurer.measure(text, labelStyle, maxLines = 1).size.let { IntSize(it.width + 2 * pad.width, it.height + 2 * pad.height) } },
                frame.sceneRect, labelGround(frame), labelKeepOff(frame, slot, finals["place-window"]) + overlay, dims)
        }
        val opens = mapOf(PlaceSpot.CHALK to openMarket, PlaceSpot.BOARD to openBoard) + (openTrail?.let { mapOf(PlaceSpot.GATE to it) } ?: emptyMap())
        for ((spot, label) in labels) {
            if (label == null) continue
            val open = opens[spot] ?: continue
            key(spot) {
                PlaceLabelTag(label, labelStyle, dims) { it.dp() }
                val pill = label.box
                Box(Modifier.offset(pill.left.dp(), pill.top.dp()).size(pill.width.dp(), pill.height.dp())
                    .testTag("label-${spot.key}").clearAndSetSemantics {
                        placeLabelText = label.text
                        contentDescription = labelled(spot)
                        role = Role.Button
                        onClick(actions.getValue(spot)) { go(spot, open); true }
                        traversalIndex = read(spot) + 0.5f
                    }
                    .focusProperties { canFocus = false }
                    .clickable(interactionSource = presses.getValue(spot), indication = null) { go(spot, open) })
            }
        }

    }
    }
    // The loam runs on into the page, and Bert's line sits in it as speech (BertSays.kt). At night he's indoors, so the
    // bubble rises towards the lit window instead of to him.
    val earth = remember(scene) { Color(scene.asAndroidBitmap().getPixel(2, PLACE_H - 1)) }
    BertSays(line, resting, time, earth, toWindow = slot.bertX == null,
        anchor = slot.walk?.let { (it.first + it.last) / 2 + BERT_MID } ?: slot.bertX?.let { it + BERT_MID } ?: WINDOW_MID,
        speaker = { if (slot.bertX == null) WINDOW_MID else bertX + BERT_MID })
    }
}

// The labels: the You button's family (panel, cream ring, soft shadow), a size smaller. Their text grows with the phone's
// text size up to LABEL_SCALE_MAX, past which there is no room in the picture for it.
private const val LABEL_SP = 13f
private const val LABEL_SCALE_MAX = 1.6f
private val LABEL_PAD_X = 10.dp
private val LABEL_PAD_Y = 5.dp
private val LABEL_TAIL = 6.dp
private val LABEL_TAIL_HALF = 5.dp
private const val LABEL_GROUND = 294

/**
 * One label, as drawn: its tag and tail as one shape (so the ring runs round both), the words in the tag, at [label]'s
 * place in the scene's box. Drawing only; its tap is a separate target over the tag.
 */
@Composable
private fun PlaceLabelTag(label: PlacedLabel, style: androidx.compose.ui.text.TextStyle, dims: LabelDims, dp: (Int) -> Dp) {
    val b = label.bounds
    val box = label.box
    // Remembered: Home recomposes on every step of Bert's walk, and the tag's outline (a path union) need not be rebuilt.
    val shape = remember(label, dims) { androidx.compose.foundation.shape.GenericShape { _, _ ->
        val bx = (box.left - b.left).toFloat(); val by = (box.top - b.top).toFloat()
        val w = box.width.toFloat(); val h = box.height.toFloat()
        addRoundRect(androidx.compose.ui.geometry.RoundRect(bx, by, bx + w, by + h, androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)))
        val tx = (label.tip.x - b.left).toFloat(); val ty = (label.tip.y - b.top).toFloat()
        val half = dims.tailHalf.toFloat()
        val tail = androidx.compose.ui.graphics.Path()
        when (label.side) {
            LabelSide.ABOVE -> { tail.moveTo(tx - half, by + h - 1); tail.lineTo(tx, ty); tail.lineTo(tx + half, by + h - 1) }
            LabelSide.RIGHT -> { tail.moveTo(bx + 1, ty - half); tail.lineTo(tx, ty); tail.lineTo(bx + 1, ty + half) }
            LabelSide.LEFT -> { tail.moveTo(bx + w - 1, ty - half); tail.lineTo(tx, ty); tail.lineTo(bx + w - 1, ty + half) }
            LabelSide.OVER -> {}
        }
        if (label.side != LabelSide.OVER) { tail.close(); op(this, tail, androidx.compose.ui.graphics.PathOperation.Union) }
    } }
    Box(Modifier.offset(dp(b.left), dp(b.top)).size(dp(b.width), dp(b.height)).clearAndSetSemantics {}.shadow(4.dp, shape).background(PanelStrong, shape)
        .border(1.5.dp, Cream, shape)) {
        Box(Modifier.offset(dp(box.left - b.left), dp(box.top - b.top)).size(dp(box.width), dp(box.height)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text(label.text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** The middle of an 84 px Bert frame, from its left edge: where his speech points. */
private const val BERT_MID = 42
/** The middle of the cottage window, which speaks for him at night. */
private val WINDOW_MID = WINDOW.x + WINDOW.w / 2

/** Whether a spot is in the one twinkle pass Home gives everyone when it comes back into view. */
internal val PlaceTwinkle = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("PlaceTwinkle")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placeTwinkle by PlaceTwinkle
/** Whether a spot is showing its press. */
internal val PlacePressed = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("PlacePressed")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placePressed by PlacePressed
/** What a scene label says (tests read it; TalkBack doesn't, the label being visual only). */
internal val PlaceLabelText = androidx.compose.ui.semantics.SemanticsPropertyKey<String>("PlaceLabelText")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placeLabelText by PlaceLabelText
/** Whether the noticeboard has a fresh note pinned on it (unread news). */
internal val PlaceNews = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("PlaceNews")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placeNews by PlaceNews

// The glint's swell and fade, one level per 100 ms beat: a dot, a cross, a star, the star at full stretch (held), and
// back. The pass starts half a second after Home appears, each spot 0.4 s after the last, and is over about two
// seconds later.
private val GLINT_FLASH = intArrayOf(1, 2, 3, 4, 4, 3, 2, 1)
private const val GLINT_PEAK = 4
/** The star's outer reach is warmer than its core, like light off brass, so it holds against a pale sky too. */
private val GLINT_WARM = Color(0xFFFFE08A)
private const val GLINT_BEAT_MS = 100L
private const val TWINKLE_STAGGER = 4
private val TWINKLE_BEATS = GLINT_FLASH.size + 2 * TWINKLE_STAGGER
private const val TWINKLE_DELAY_MS = 500L
private fun glintLevel(beat: Int): Int = if (beat in GLINT_FLASH.indices) GLINT_FLASH[beat] else 0
/** Every spot's glint level in one Int, three bits a spot by its ordinal; [level] is given each spot's turn in [order]. */
private fun packGlints(order: List<PlaceSpot>, level: (Int) -> Int): Int =
    order.withIndex().fold(0) { acc, (i, spot) -> acc or (level(i) shl (spot.ordinal * 3)) }
private fun glintOf(packed: Int, spot: PlaceSpot): Int = packed shr (spot.ordinal * 3) and 7

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
