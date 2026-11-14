package global.bert.widget

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
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
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 0.12, “Bert’s day” (docs/CONCEPT-0.12-BERTS-PLACE.md): Home opens onto Bert's garden at the phone's own time of day.
 * Where he is follows his routine; how he stands follows his mood. The scene is never a card: full width, from the top
 * of the screen, with the noticeboard (Berthalla's note), Bert's chalked tally of the visit streak and this month's paw
 * prints all in the picture.
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

    /** [c] in this light, as [filter] draws it (for a colour that then needs warming up after dark, like the pin). */
    fun lit(c: Color): Color {
        val (tr, tg, tb) = tint ?: return c
        val s = 1f - desat
        val r = (0.213f + 0.787f * s) * c.red + 0.715f * (1 - s) * c.green + 0.072f * (1 - s) * c.blue
        val g = 0.213f * (1 - s) * c.red + (0.715f + 0.285f * s) * c.green + 0.072f * (1 - s) * c.blue
        val b = 0.213f * (1 - s) * c.red + 0.715f * (1 - s) * c.green + (0.072f + 0.928f * s) * c.blue
        return Color((r * tr).coerceIn(0f, 1f), (g * tg).coerceIn(0f, 1f), (b * tb).coerceIn(0f, 1f), c.alpha)
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
    internal val every: List<String> get() = bank.values.flatten() + pats + naps + listOf(TOO_MANY, WOKEN) + milestones.values +
        PlaceThing.entries.flatMap { it.remarks } + NEWS_BELOW + BertSlot.entries.mapNotNull { hint(it, PlaceFirsts(1, false)) } +
        streakDays.values + streakBack + streakRuns.indices.map { streakDay(998, it) } +
        listOf(VisitStreak(1, 1, LocalDate.MIN), VisitStreak(998, 998, LocalDate.MIN), VisitStreak(998, 999, LocalDate.MIN)).map { streakTold(it) }

    /**
     * For someone new, Bert asks for a pat instead of saying his daily line (at night he is indoors, so it is the
     * window). These must read with no lore at all (no hat, no hoodie, no mayor): they are the first words a person
     * hears from him. Null once he has been patted (or the window knocked), or once the first days are over.
     */
    fun hint(slot: BertSlot, firsts: PlaceFirsts): String? {
        if (!firsts.showingRound || firsts.patted) return null
        return when (slot) {
            BertSlot.BED -> "tap the window. i\u2019m a light sleeper."
            BertSlot.NAP -> "asleep. pats still accepted."
            else -> "pat me. i permit it."
        }
    }

    /** What he says, once ever, on a lifetime pat count; it takes the place of that pat's own reply. */
    val milestones = linkedMapOf(
        10 to "ten pats. you\u2019re getting good at this.",
        50 to "fifty pats. i\u2019ve been counting, obviously.",
        100 to "a hundred pats. i\u2019ll have that framed.",
        500 to "five hundred pats. we\u2019re practically family.",
        1000 to "a thousand pats. a legend of this lawn.",
    )

    /** The first open on a new day of a run: his word on it, once that day; some lengths have their own. */
    val streakDays = linkedMapOf(
        3 to "three days running. people will talk.",
        7 to "a whole week of visits. i\u2019m touched.",
        14 to "two weeks in a row. you know the hedge by now.",
        30 to "thirty days running. practically a neighbour.",
        50 to "fifty days in a row. i saved you a spot on the lawn.",
        100 to "a hundred days running. the daisies talk about you.",
        200 to "two hundred days. the gate squeaks your name.",
        365 to "a whole year of days. i don\u2019t say this lightly: good.",
    )
    private val streakRuns = listOf<(Int) -> String>({ "back again. $it days running." }, { "$it days in a row. i\u2019ve noticed." },
        { "day $it. the gate remembers you." })
    /** Day [n] of a run (2 or more), on the [turn]th of his everyday ways of saying it. */
    fun streakDay(n: Int, turn: Int): String = streakDays[n] ?: streakRuns[turn % streakRuns.size](n)
    /** A new run after a gap: glad to see them, and nothing more. No guilt, no lost streak, no "come back tomorrow". */
    val streakBack = listOf("there you are. the garden kept your spot.", "welcome back. the daisies didn\u2019t notice a thing.",
        "you\u2019re here. that\u2019s the main thing.")

    /** What he says on the first open of a new day, or null (the first open ever, the same day again, the clock gone back). */
    fun dayLine(step: StreakStep, day: LocalDate): String? = when (step.change) {
        StreakChange.NEXT_DAY -> streakDay(step.streak.days, day.dayOfYear)
        StreakChange.RESTARTED -> streakBack[day.dayOfYear % streakBack.size]
        else -> null
    }

    /** What he can say in [slot] besides his resting line, with [streak] as it stands on [day]. */
    fun sayable(slot: BertSlot, streak: VisitStreak, day: LocalDate): List<String> = buildList {
        if (slot == BertSlot.BED) add(WOKEN)
        else {
            addAll(if (slot == BertSlot.NAP) naps else pats + TOO_MANY)
            addAll(PlaceThing.entries.flatMap { it.remarks }); add(NEWS_BELOW); add(streakTold(streak)); addAll(milestones.values)
        }
        add(streakDay(streak.days, day.dayOfYear)); addAll(streakBack)
    }

    /** A tap on the noticeboard while it has a fresh note: where to read it, before any of his remarks. */
    const val NEWS_BELOW = "something new. it\u2019s pinned up just below."

    /** A tap on the chalkboard (its first in a sitting): the run and the best, his way. */
    fun streakTold(s: VisitStreak): String = when {
        s.days == 1 && s.best <= 1 -> "day one. everyone starts somewhere."
        s.days == 1 -> "day one of a new run. best is ${s.best}."
        s.days >= s.best -> "${s.days} days running. your best yet."
        else -> "${s.days} days running. best is ${s.best}."
    }
}

/**
 * The things in the picture Bert has a word about when they are tapped. 0.15: the garden is where you play with Bert,
 * not a menu; Market, the board and the trail are one tap away under it (the slate, the note, the trail section and
 * the Play tab), so nothing here goes anywhere. [tag] is the thing's tap target. Lines are lowercase and dry, in his
 * voice; nothing about prices beyond his own chalk.
 */
internal enum class PlaceThing(val tag: String, val remarks: List<String>) {
    BLOSSOM("place-blossom", listOf("best shade in the garden. reserved.", "petals everywhere. i am not sweeping them.",
        "that bush and i go back years.")),
    CHALK("place-tag", listOf("chalked it myself. paws are not ideal for this.", "my chalkboard. i keep it tidy, mostly.",
        "i drew a paw on it once. still proud.")),
    COTTAGE("place-cottage", listOf("home. the lamp knows when i\u2019m due.", "door, roof, chimney. all mine.",
        "i nap in there when the lawn gets busy.")),
    GATE("place-gate", listOf("the gate. it squeaks. i allow it.", "the way to the trail is further down the page.",
        "i let things in. selectively.")),
    BOARD("place-board", listOf("the noticeboard. i pin things up. badly.", "news goes up here. i read it first.",
        "if it\u2019s on the board, i\u2019ve sniffed it.")),
}

/** How long the place keeps showing a newcomer around: Bert asks for a pat on at most this many distinct days. */
internal const val FIRST_DAYS = 5

/** What a person has done in the place so far: [days] they have come round (ever, counted once a day), and whether they have [patted] Bert. */
internal data class PlaceFirsts(val days: Int, val patted: Boolean) {
    val showingRound: Boolean get() = days <= FIRST_DAYS
}

/**
 * The newcomer state and the lifetime pat count, in the same `bert_place` preferences as [BertVisits], on the phone
 * only. It only ever counts up: nothing resets, and nothing is lost by staying away. (0.14 also kept a `tapped` list of
 * the spots found; 0.15 neither reads nor writes it.)
 */
internal class PlaceDiscovery(private val context: Context) {
    private val prefs = context.getSharedPreferences("bert_place", Context.MODE_PRIVATE)

    /** Counts [today] once (a clock set backwards neither counts nor rewinds) and returns what has been done. */
    fun record(today: LocalDate): PlaceFirsts {
        // These keys are new in 0.14, so someone updating from 0.13 has none and would be greeted as a stranger in a
        // garden they have visited for weeks. Anyone who came round before today (a paw print on another day, kept since
        // well before 0.14, or the app opened on another day by an older version) starts as having patted Bert. Only the
        // first record after the update can do this; a fresh install leaves no such trace, even when its first days were
        // spent in Market from the widget (0.14 counts those opens, but not as an older version's).
        if (!prefs.contains(KEY_SEEN_DAYS) && cameBefore(today)) prefs.edit().putBoolean(KEY_PATTED, true).apply()
        val last = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE)
        var days = prefs.getInt(KEY_SEEN_DAYS, 0)
        if (today.toEpochDay() > last) {
            days += 1
            prefs.edit().putLong(KEY_LAST_DAY, today.toEpochDay()).putInt(KEY_SEEN_DAYS, days).apply()
        } else if (last > today.toEpochDay() + CLOCK_SLACK_DAYS) {
            // The clock was set forward and has come back: count from today again rather than wait for that date.
            prefs.edit().putLong(KEY_LAST_DAY, today.toEpochDay()).apply()
        }
        return PlaceFirsts(days, prefs.getBoolean(KEY_PATTED, false))
    }

    fun patted() { prefs.edit().putBoolean(KEY_PATTED, true).apply() }

    /** Pats ever given, on this phone. */
    val pats: Int get() = prefs.getInt(KEY_PATS, 0)

    /** Counts one more pat and returns the new lifetime count. Each count is reached once, so each milestone fires once. */
    fun pat(): Int = (pats + 1).also { prefs.edit().putInt(KEY_PATS, it).apply() }

    /** The visit streak as stored, without counting anything; null before the first. */
    fun streak(): VisitStreak? = if (!prefs.contains(KEY_STREAK_DAY)) null
        else VisitStreak(prefs.getInt(KEY_STREAK, 1), prefs.getInt(KEY_STREAK_BEST, 1), LocalDate.ofEpochDay(prefs.getLong(KEY_STREAK_DAY, 0)),
            if (prefs.contains(KEY_STREAK_ANCHOR)) LocalDate.ofEpochDay(prefs.getLong(KEY_STREAK_ANCHOR, 0)) else null)

    /** What opening the app on [today] does or did: the step that was stored today, or the one an open now would make. */
    private fun step(today: LocalDate): StreakStep {
        val stored = streak()
        if (stored != null && stored.last == today)
            return StreakStep(stored.copy(anchor = null), prefs.getString(KEY_STREAK_CHANGE, null)?.let { runCatching { StreakChange.valueOf(it) }.getOrNull() } ?: StreakChange.SAME_DAY)
        // Installs from before 0.15 have no streak yet: this month's paw prints may prove a run (seededStreak); otherwise
        // the run starts at one, not at their [PlaceFirsts.days] total, which counts days ever, not in a row.
        if (stored == null) seededStreak(BertVisits(context).thisMonth(today), today)?.let { return StreakStep(it, StreakChange.FIRST) }
        return visitStreakOn(stored, today)
    }

    /**
     * The app was opened (or came back to the front) on [today], from any way in: Home, a widget, an alert or a
     * shortcut. Counts it by [visitStreakOn], once per date, and returns the day's step.
     */
    fun visit(today: LocalDate): StreakStep {
        val step = step(today)
        if (step.streak != streak()) prefs.edit().apply {
            putInt(KEY_STREAK, step.streak.days); putInt(KEY_STREAK_BEST, step.streak.best); putLong(KEY_STREAK_DAY, step.streak.last.toEpochDay())
            step.streak.anchor?.let { putLong(KEY_STREAK_ANCHOR, it.toEpochDay()) } ?: remove(KEY_STREAK_ANCHOR)
            // The day's change is kept for Bert's word later that day; a same-day tidy-up never overwrites it.
            if (step.change in COUNTED) putString(KEY_STREAK_CHANGE, step.change.name)
        }.apply()
        return step
    }

    /** The streak as it stands on [today], for showing (the chalkboard, the You page): never writes. */
    fun streakOn(today: LocalDate): StreakStep = step(today)

    /** The day Bert last said his word on a new day of the run (once seen; see BertsPlace). */
    fun daySaidOn(): LocalDate? = if (prefs.contains(KEY_DAY_SAID)) LocalDate.ofEpochDay(prefs.getLong(KEY_DAY_SAID, 0)) else null
    fun daySaid(today: LocalDate) { prefs.edit().putLong(KEY_DAY_SAID, today.toEpochDay()).apply() }

    private fun cameBefore(today: LocalDate): Boolean =
        BertVisits(context).cameBefore(today) || BERTHomeVisits(context).openedBefore014(today)

    private companion object {
        const val KEY_LAST_DAY = "first_last_day"
        const val KEY_SEEN_DAYS = "first_days"
        const val KEY_PATTED = "patted"
        const val KEY_PATS = "pats_ever"
        const val KEY_STREAK = "streak_days"
        const val KEY_STREAK_BEST = "streak_best"
        const val KEY_STREAK_DAY = "streak_last_day"
        const val KEY_STREAK_CHANGE = "streak_change"
        const val KEY_STREAK_ANCHOR = "streak_anchor_day"
        val COUNTED = setOf(StreakChange.FIRST, StreakChange.NEXT_DAY, StreakChange.RESTARTED)
        const val KEY_DAY_SAID = "streak_said_day"
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
private val BOARD = PlaceRect(356, 250, 34, 34)       // the noticeboard, standing against the wall past the gate
private val CHALK = PlaceRect(138, 248, 48, 36)      // the A-frame chalkboard between the blossom and the gate
private val GATEWAY = PlaceRect(248, 211, 92, 72)    // the picket gate and its posts
private val WINDOW = PlaceRect(262, 104, 28, 32)
private val COTTAGE = PlaceRect(166, 30, 172, 128)   // the cottage, chimney to doorstep (by day; at night the window is its target)
private val BLOSSOM = PlaceRect(0, 128, 142, 112)    // the blossom bush's crown, down to the hedge

private val SLATE_FACE = PlaceRect(150, 252, 30, 23) // the slate inside its frame, which lightens under a finger
private val BOARD_FACE = PlaceRect(362, 260, 22, 15) // the noticeboard's back panel, papers and all
private val PICKETS = PlaceRect(272, 240, 45, 41)    // the picket gate between its posts, which jumps when pressed
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

/**
 * A tap target in the picture, where the art puts it ([natural], page px), whether it may be moved for the You button,
 * and whether a moved target must keep off it ([blocks]: everything but Bert, who stands in front of things).
 */
internal data class PlaceTarget(val natural: IntRect, val movable: Boolean, val blocks: Boolean = movable)

/**
 * The tap targets for [slot] in [frame], by test tag, where the art puts them. Bert's ([bertX], his left edge in art px;
 * he walks) is never moved: the button may cover part of him and the rest of him is still his.
 */
internal fun placeNaturals(frame: PlaceFrame, slot: BertSlot, bertX: Int): Map<String, PlaceTarget> = buildMap {
    fun at(r: PlaceRect, pad: Int) = frame.px(r.x, r.y, r.w, r.h, pad)
    val scene = frame.sceneRect
    fun inScene(r: IntRect) = IntRect(maxOf(r.left, scene.left), maxOf(r.top, scene.top), minOf(r.right, scene.right), minOf(r.bottom, scene.bottom))
    // The cottage and the blossom are big and stay put (the You button may cover a corner of the cottage; the rest of
    // it still answers), but a moved target keeps off them. Out by day only: at night the window is the cottage's.
    if (slot != BertSlot.BED) put(PlaceThing.COTTAGE.tag, PlaceTarget(inScene(at(COTTAGE, 0)), movable = false, blocks = true))
    put(PlaceThing.BLOSSOM.tag, PlaceTarget(inScene(at(BLOSSOM, 0)), movable = false, blocks = true))
    put(PlaceThing.BOARD.tag, PlaceTarget(at(BOARD, 7), true)) // 48 art px: 48dp where k == density
    put(PlaceThing.CHALK.tag, PlaceTarget(at(CHALK, 6), true))
    put(PlaceThing.GATE.tag, PlaceTarget(at(GATEWAY, 0), true))
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
        val blockers = targets.filter { (other, t) -> other != tag && t.blocks }.keys.flatMap { listOf(targets.getValue(it).natural, finals.getValue(it)) }
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
    newNotes: Int,
    modifier: Modifier = Modifier,
    meetBert: (() -> Unit)? = null,
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
    // The visit streak: counted by the app when it is opened, from any way in (BERTApp); the garden only shows it, on
    // the chalkboard, and has Bert say his word on a new day.
    val visit = remember(today) { discovery.streakOn(today) }
    val streak = visit.streak

    var patUntil by remember { mutableLongStateOf(0L) }
    var pats by remember { mutableIntStateOf(0) }
    var lastPat by remember { mutableLongStateOf(0L) }
    var wokenUntil by remember { mutableLongStateOf(0L) }
    var said by remember { mutableStateOf<String?>(null) }
    // How long a remark about a thing, or a milestone, stays in the bubble.
    var saidUntil by remember { mutableLongStateOf(0L) }
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
    // A new day of a run: Bert's word on it, once that day, the first time Home is actually in front of someone (resumed:
    // not behind the game, not in the background). It counts as said once it has been up for DAY_LINE_SEEN_MS, so a
    // rotation or a quick switch away before then has him say it again, and after then never again that day. Not on the
    // first open ever (the newcomer's ask for a pat comes first).
    val gardenAway by androidx.compose.runtime.rememberUpdatedState(skyCovered)
    LaunchedEffect(lifecycle, today) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            if (discovery.daySaidOn() == today) return@repeatOnLifecycle
            val line = BertLines.dayLine(discovery.streakOn(today), today) ?: return@repeatOnLifecycle
            // Not while the garden is scrolled away (Home keeps its scroll): he waits until someone can see him say it.
            androidx.compose.runtime.snapshotFlow { gardenAway }.first { !it }
            said = line; saidUntil = System.currentTimeMillis() + DAY_LINE_MS
            delay(DAY_LINE_SEEN_MS)
            discovery.daySaid(today)
        }
    }
    LaunchedEffect(patUntil, wokenUntil, saidUntil) {
        val until = maxOf(patUntil, wokenUntil, saidUntil)
        if (until == 0L) return@LaunchedEffect
        delay((until - System.currentTimeMillis()).coerceAtLeast(0))
        tick++
        said = null
    }
    // A thing being pressed, held a moment past the finger so a quick tap still shows (see remark() at the tap targets).
    var pressed by remember { mutableStateOf<PlaceThing?>(null) }
    val presses = remember { PlaceThing.entries.associateWith { MutableInteractionSource() } }
    LaunchedEffect(presses) {
        for ((thing, source) in presses) launch {
            source.interactions.collectLatest { i ->
                when (i) {
                    is PressInteraction.Press -> pressed = thing
                    is PressInteraction.Release, is PressInteraction.Cancel -> { delay(PRESS_HELD_MS); if (pressed == thing) pressed = null }
                }
            }
        }
    }
    // A pat's heart: a small pixel heart that rises from him and fades, one art pixel a beat ([heartStep] 0 until
    // HEART_STEPS; -1 when there is none), from [heartAt] (place art px, its bottom middle). With animations off it fades
    // where it appears. Read only when drawing (and by Bert's target's semantics, for tests).
    var heartStep by remember { mutableIntStateOf(-1) }
    var heartAt by remember { mutableStateOf(IntOffset.Zero) }
    var hearts by remember { mutableIntStateOf(0) }
    LaunchedEffect(hearts) {
        if (hearts == 0) return@LaunchedEffect
        try {
            for (step in 0 until HEART_STEPS) { heartStep = step; delay(HEART_STEP_MS) }
        } finally { heartStep = -1 }
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
    val resting = BertLines.hint(slot, firsts) ?: BertLines.of(slot, today)
    val line = said ?: resting

    // The first pat ever (or, at night, the first knock on his window) ends his asking for one: once this reply has been
    // said, his own line comes back.
    fun greeted() { if (!firsts.patted) { discovery.patted(); firsts = firsts.copy(patted = true) } }

    fun pat() {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (!still) scope.launch { for (h in intArrayOf(2, 4, 3, 1, 0)) { lift = h; delay(45) } }
        val at = System.currentTimeMillis()
        greeted()
        pats = if (at - lastPat < 4_000) pats + 1 else 1
        lastPat = at
        // A heart over his head, where his head is now (asleep in the sun, lower down and further along).
        heartAt = if (slot == BertSlot.NAP) IntOffset(bertX + NAP_HEAD_X, PLACE_NAP_TOP + NAP_HEAD_TOP - 2)
            else IntOffset(bertX + if (facingLeft) 83 - HEAD_X else HEAD_X, PLACE_FEET_TOP + HEAD_TOP - 2)
        hearts++
        // Lifetime pats, kept on the phone: a round number gets its own line, once ever.
        BertLines.milestones[discovery.pat()]?.let { said = it; patUntil = at + 1_400; saidUntil = at + 3_200; return }
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
            // A pressed thing answers the finger: the slate and the board's face catch the light, the gate's pickets jump.
            // Redrawn from the scene itself, so the press is in the same light as everything else.
            fun sceneAt(r: PlaceRect, dy: Int = 0, filter: ColorFilter? = null) = drawImage(scene, srcOffset = IntOffset(r.x + WIDE_SHIFT, r.y),
                srcSize = IntSize(r.w, r.h), dstOffset = IntOffset((r.x - x0) * k, (r.y + dy) * k), dstSize = IntSize(r.w * k, r.h * k),
                filterQuality = FilterQuality.None, colorFilter = filter)
            when (pressed) {
                PlaceThing.CHALK -> sceneAt(SLATE_FACE, filter = PRESS_LIGHT)
                PlaceThing.BOARD -> sceneAt(BOARD_FACE, filter = PRESS_LIGHT)
                PlaceThing.GATE -> sceneAt(PICKETS, dy = -2)
                else -> {}
            }
            // 0.15: the chalkboard is Bert's own tally of the visit streak, "day" over the count (the price is on the slate
            // under the garden). Chalk is never perfectly even, and not dimmed by the night beyond its own alpha.
            val chalk = Color(0xFFEDEBE4).copy(alpha = if (time.light) 0.9f else 0.55f)
            for (p in chalkboardTally(streak.days)) px(p.x, p.y, chalk.copy(alpha = chalk.alpha * (if ((p.x + p.y) % 5 == 0) 0.7f else 1f)))
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
            // A pat's heart, on the scene's own grid: the hat's red, outlined in the scene's near-black.
            val step = heartStep
            if (step >= 0) {
                val alpha = HEART_FADE[step]
                val hx = heartAt.x - HEART[0].length / 2
                val hy = heartAt.y - HEART.size + 1 - if (still) 0 else step
                HEART.forEachIndexed { row, bits -> bits.forEachIndexed { col, c ->
                    val colour = when (c) { 'o' -> HEART_OUTLINE; 'r' -> PIN; 's' -> PIN_SHINE; else -> null }
                    // In his light by day; after dark a little warmer than the light alone would leave it, as the
                    // board's pin is, or a dark red heart is lost against the blossom at dusk.
                    if (colour != null) {
                        val drawn = if (time.light) colour else lerp(time.lit(colour), colour, 0.3f)
                        drawRect(drawn.copy(alpha = alpha), androidx.compose.ui.geometry.Offset(((hx + col - x0) * k).toFloat(), ((hy + row) * k).toFloat()),
                            androidx.compose.ui.geometry.Size(k.toFloat(), k.toFloat()), colorFilter = if (time.light) time.filter else null)
                    }
                } }
            }
            }
        }

        // Tap targets. Bert and, at night, the window are buttons; the things are not (0.15: the garden is for playing
        // with Bert, not a menu). A tap on a thing gets a word from Bert about it and goes nowhere. They are left out of
        // TalkBack and the keyboard altogether: a remark about a chalkboard is no use by voice, and Market, the board and
        // the trail are buttons just below the garden.
        // Where each target goes (placeTargets): where the art is, unless Home's floating You button covers it.
        val minTarget = with(density) { MIN_TARGET.roundToPx() }
        val naturals = placeNaturals(frame, slot, bertX)
        val finals = placeTargets(naturals, overlay, frame.sceneRect, minTarget)
        fun Modifier.at(tag: String): Modifier {
            val p = finals.getValue(tag)
            return this.offset(p.left.dp(), p.top.dp()).size(p.width.dp(), p.height.dp())
        }
        // The press is drawn in the picture (no ripple box over the pixels) and held a moment past the tap, so even a
        // quick one is seen to land. His remarks about each thing take turns, from a different one each day.
        val remarked = remember { IntArray(PlaceThing.entries.size) }
        // The chalkboard's first tap in a sitting has him read out his tally (the run and the best); then his remarks.
        var toldRun by remember { mutableStateOf(false) }
        // The noticeboard, while it has a fresh note: the first tap says where it is (under the garden).
        var toldNews by remember(newNotes > 0) { mutableStateOf(false) }
        fun remark(thing: PlaceThing) {
            pressed = thing
            scope.launch { delay(PRESS_HELD_MS); if (pressed == thing) pressed = null }
            said = if (thing == PlaceThing.CHALK && !toldRun) { toldRun = true; BertLines.streakTold(streak) }
                else if (thing == PlaceThing.BOARD && newNotes > 0 && !toldNews) { toldNews = true; BertLines.NEWS_BELOW }
                else { val n = remarked[thing.ordinal]++; thing.remarks[(n + today.dayOfYear) % thing.remarks.size] }
            saidUntil = System.currentTimeMillis() + 2_800
        }
        // At night he is indoors asleep, so the things have nothing to say (the window is how you reach him).
        // The cottage and the blossom first, so the smaller things on them, and Bert, take the taps where they meet.
        // The chalkboard is also read, as plain text, day and night: its tally is the visit streak.
        for (thing in listOf(PlaceThing.COTTAGE, PlaceThing.BLOSSOM, PlaceThing.BOARD, PlaceThing.GATE, PlaceThing.CHALK)) {
            if (thing.tag !in finals || (slot.bertX == null && thing != PlaceThing.CHALK)) continue
            Box(Modifier.at(thing.tag).testTag(thing.tag)
                .clearAndSetSemantics {
                    placePressed = pressed == thing
                    if (thing == PlaceThing.BOARD) placeNews = newNotes > 0
                    if (thing == PlaceThing.CHALK) { contentDescription = streakSpoken(streak); traversalIndex = 7f }
                }
                .focusProperties { canFocus = false }
                .then(if (slot.bertX != null) Modifier.clickable(interactionSource = presses.getValue(thing), indication = null) { remark(thing) } else Modifier))
        }
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
            // in front of the gate or the chalkboard, a tap on him is a pat, not a word about the gate.
            Box(Modifier.at("place-bert").testTag("place-bert").combinedClickable(role = Role.Button, onClickLabel = "Give Bert a pat",
                onLongClickLabel = meetBert?.let { "Meet Bert" }, onLongClick = meetBert) { pat() }
                .semantics { contentDescription = "Bert"; traversalIndex = 9f })
            // For tests: whether a heart is up, on a node of its own, so Bert's node is not invalidated ten times a pat.
            Box(Modifier.testTag("place-heart").semantics { placeHeart = heartStep >= 0 })
        }
    }
    }
    // The loam runs on into the page, and Bert's line sits in it as speech (BertSays.kt). At night he's indoors, so the
    // bubble rises towards the lit window instead of to him; once a knock has woken him (window dark) he is not dreaming.
    val earth = remember(scene) { Color(scene.asAndroidBitmap().getPixel(2, PLACE_H - 1)) }
    // Everything he can say here, so the space under the scene is kept for the tallest and the page never jumps.
    val reserve = remember(slot, streak, today) { BertLines.sayable(slot, streak, today) }
    BertSays(line, resting, time, earth, from = when { slot.bertX != null -> BubbleFrom.GARDEN; woken -> BubbleFrom.AWAKE_INDOORS; else -> BubbleFrom.DREAM },
        anchor = slot.walk?.let { (it.first + it.last) / 2 + BERT_MID } ?: slot.bertX?.let { it + BERT_MID } ?: WINDOW_MID,
        speaker = { if (slot.bertX == null) WINDOW_MID else bertX + BERT_MID }, reserve = reserve)
    }
}

/** The middle of an 84 px Bert frame, from its left edge: where his speech points. */
private const val BERT_MID = 42
/** The middle of the cottage window, which speaks for him at night. */
private val WINDOW_MID = WINDOW.x + WINDOW.w / 2

/** Whether a pat's heart is in the picture (tests read it; TalkBack doesn't). */
internal val PlaceHeart = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("PlaceHeart")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placeHeart by PlaceHeart
/** Whether a thing is showing its press. */
internal val PlacePressed = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("PlacePressed")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placePressed by PlacePressed
/** Whether the noticeboard has a fresh note pinned on it (unread news). */
internal val PlaceNews = androidx.compose.ui.semantics.SemanticsPropertyKey<Boolean>("PlaceNews")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.placeNews by PlaceNews

/**
 * The chalkboard's tally for [days], as the art pixels its chalk covers (place art px): "day" on the top row and the
 * count under it, each centred on the slate's face, in [ChalkFont]. Up to 999; past that, "999+".
 */
internal fun chalkboardTally(days: Int): List<IntOffset> = buildList {
    fun row(text: String, top: Int) {
        var x = SLATE_FACE.x + (SLATE_FACE.w - ChalkFont.width(text) + 1) / 2
        for (ch in text) {
            val glyph = ChalkFont.glyph(ch)
            glyph.forEachIndexed { r, bits -> bits.forEachIndexed { c, on -> if (on == '#') add(IntOffset(x + c, top + r)) } }
            x += glyph[0].length + 1
        }
    }
    row("day", TALLY_TOP)
    row(streakShown(days), TALLY_TOP + ChalkFont.HEIGHT + 2)
}
/** The tally's top row on the slate: the two rows sit in the middle of its dark face (rows 254 to 273). */
private const val TALLY_TOP = 256
/** The slate's face (art px), for the tally's tests. */
internal val SLATE_FACE_RECT: IntRect get() = IntRect(SLATE_FACE.x, SLATE_FACE.y, SLATE_FACE.x + SLATE_FACE.w, SLATE_FACE.y + SLATE_FACE.h)

/** A pat's heart in art pixels: o the scene's outline, r the hat's red, s its shine. */
private val HEART = listOf(
    "..oo.oo..",
    ".orrorro.",
    "orsrrrrro",
    "orrrrrrro",
    ".orrrrro.",
    "..orrro..",
    "...oro...",
    "....o....",
)
/** The scene's own outline, as on the gateposts and the board. */
private val HEART_OUTLINE = Color(0xFF040506)
/** It rises one art pixel a beat (none with animations off) and fades over the last few: just under a second in all. */
private val HEART_FADE = floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 0.8f, 0.6f, 0.4f, 0.2f)
private val HEART_STEPS = HEART_FADE.size
private const val HEART_STEP_MS = 90L
/** How long Bert's word on a new day of a run stays before his own line comes back, and how long it must be up to count as said. */
private const val DAY_LINE_MS = 6_000L
private const val DAY_LINE_SEEN_MS = 2_000L
/** The top of his hat in an 84 px standing frame (its middle across, facing right), and asleep in the nap frame. */
private const val HEAD_X = 44
private const val HEAD_TOP = 2
private const val NAP_HEAD_X = 56
private const val NAP_HEAD_TOP = 12

/** A hand-made chalk font in art pixels, seven rows tall: digits, a point, "day" and "+", and a paw. */
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
        // 0.15, the tally's word and overflow: the digits' height and stroke; "a" and "y" sit on the x-height.
        'd' to listOf("...#", "...#", ".###", "#..#", "#..#", "#..#", ".###"),
        'a' to listOf("....", "....", ".##.", "...#", ".###", "#..#", ".###"),
        'y' to listOf("....", "....", "#..#", "#..#", ".###", "...#", ".##."),
        '+' to listOf("...", "...", ".#.", "###", ".#.", "...", "..."),
    )
    /** A paw print, chalked on the garden widget's slate (BERTPlaceWidget). */
    val PAW = listOf("#...#", ".....", ".#.#.", "#####", ".###.")
    fun glyph(c: Char): List<String> = glyphs[c] ?: glyphs.getValue('.')
    fun width(text: String): Int = text.sumOf { glyph(it)[0].length + 1 } - 1
}

private fun sceneDescription(slot: BertSlot, woken: Boolean): String = when (slot) {
    BertSlot.GATE -> "Morning in Bert’s garden. Bert is out by the gate."
    BertSlot.ROUNDS -> "Bert’s garden, late morning. Bert is on his rounds."
    BertSlot.NAP -> "Bert’s garden, early afternoon. Bert is asleep in the sun."
    BertSlot.POST -> "Bert’s garden in the golden afternoon. Bert is on gate duty."
    BertSlot.EVENING -> "Bert’s garden at dusk. The cottage lamp is lit."
    BertSlot.BED -> if (woken) "Night. The cottage window has gone dark." else "Night. Bert is asleep inside; the cottage window is lit."
}
