package global.bert.widget

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.ActivityState
import global.bert.widget.data.JournalEntry
import global.bert.widget.data.QuoteState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * 0.15, "slate and paper": the garden carries on down the page. Under Bert's bubble, the price is chalked on a slate in a
 * wooden frame, like the chalkboard standing in the scene, and Berthalla's note is a cream paper note pinned up in his
 * handwriting. One rule for the whole app from here: numbers are chalked on slate; Bert's words are on paper.
 *
 * Both are scene materials, not app panels: drawn on the scene's own art-pixel grid (k device px per art px, from
 * placeFrame, as the bubble is), with stepped corners and outlines like the bubble's, and lit for the time of day (the
 * paper is the bubble's own paper in each light, so it never glares under the night scene). They are the same in every
 * app colour scheme.
 */

/** The slate and its frame in the scene's light: the chalkboard by the gate's colours by day, dimmer at dusk and night. */
internal data class SlateTone(
    val outline: Color, val woodLight: Color, val wood: Color, val woodDark: Color,
    val slate: Color, val slateShade: Color, val smudge: Color,
    /** Chalk for the price; [chalkSoft] for the smaller words, a lighter hand. */
    val chalk: Color, val chalkSoft: Color,
    /** Coloured chalk: the 24h change up or down, and a delayed price's warning. */
    val green: Color, val red: Color, val yellow: Color,
) {
    companion object {
        fun of(time: PlaceTime) = when (time) {
            PlaceTime.DUSK -> SlateTone(Color(0xFF24130F), Color(0xFF7F5C43), Color(0xFF5D4333), Color(0xFF432E22),
                Color(0xFF2C2F37), Color(0xFF202228), Color(0xFF31353E),
                Color(0xFFE9E4D5), Color(0xFFB3B0A8), Color(0xFF76DCA7), Color(0xFFF7897E), Color(0xFFE8BD68))
            PlaceTime.NIGHT -> SlateTone(Color(0xFF07050A), Color(0xFF5E4434), Color(0xFF4A3529), Color(0xFF33241B),
                Color(0xFF262930), Color(0xFF1C1E23), Color(0xFF2B2E36),
                Color(0xFFD6D1C3), Color(0xFFA5A29A), Color(0xFF6CCB9A), Color(0xFFE88379), Color(0xFFDDB463))
            else -> SlateTone(Color(0xFF2A1A0F), Color(0xFF8E6649), Color(0xFF674A38), Color(0xFF4C3426),
                Color(0xFF30343D), Color(0xFF23262D), Color(0xFF363A44),
                Color(0xFFF1ECDD), Color(0xFFBBB8B0), Color(0xFF7BE6AE), Color(0xFFFF8F84), Color(0xFFF2C46B))
        }
    }
}

/** The note's paper (the bubble's own, [BubbleTone]) with its eyebrow and link inks and the pin, in the scene's light. */
internal data class PaperTone(val paper: BubbleTone, val eyebrow: Color, val link: Color, val pin: Color, val pinLight: Color, val pinDark: Color) {
    companion object {
        fun of(time: PlaceTime) = when (time) {
            PlaceTime.DUSK -> PaperTone(BubbleTone.of(time), Color(0xFF6E543A), Color(0xFF643A96), Color(0xFFC23C2B), Color(0xFFD9644F), Color(0xFF952A1E))
            PlaceTime.NIGHT -> PaperTone(BubbleTone.of(time), Color(0xFF54412D), Color(0xFF52307C), Color(0xFF9E3A2E), Color(0xFFB8584B), Color(0xFF762A20))
            else -> PaperTone(BubbleTone.of(time), Color(0xFF7D6043), Color(0xFF6B3FA0), Color(0xFFD8402C), Color(0xFFF0705C), Color(0xFFA82A1C))
        }
    }
}

/**
 * What the slate says. With a quote: [price] in dollars (as Market, Alerts and the widgets say it), the 24h [change],
 * and [time]: when it was read, or in yellow chalk ([delayed]) that it is late. Without one: [status], and what to do.
 * [spoken] is the whole slate in one sentence for TalkBack.
 */
internal data class SlateFace(
    val price: String?, val status: String?, val change: String?, val falling: Boolean,
    val time: String?, val delayed: Boolean, val spoken: String,
) 

internal fun slateFace(quote: QuoteState, now: Long, use24Hour: Boolean, locale: Locale = Locale.getDefault()): SlateFace = when (quote) {
    is QuoteState.Available -> {
        val q = quote.quote
        val price = formatPrice(q.priceUsd)
        val change = q.change24hPct?.let { formatPercent(it) }
        val delayed = quoteDelayed(quote, now)
        // The phone's own clock words ("As of 9:09 AM", "As of 8 Sep 09:09"), in chalk's lower case.
        val asOf = formatObservedAt(q.observedAtEpochMillis, now, locale = locale, use24Hour = use24Hour)
        val freshness = quoteFreshness(quote, now)
        SlateFace(price, null, change, (q.change24hPct ?: 0.0) < 0, (if (delayed) freshness else asOf).lowercase(locale), delayed,
            "BERT price $price" + (change?.let { ", $it over the past 24 hours" } ?: "") + ". " + (if (delayed) freshness else asOf) + ".")
    }
    QuoteState.Loading -> SlateFace(null, "checking the price…", null, false, null, false, "Checking the price…")
    is QuoteState.Unavailable -> SlateFace(null, "price unavailable", null, false, "check your connection.", false, PRICE_UNAVAILABLE_SPOKEN)
}

private const val PRICE_UNAVAILABLE_SPOKEN = "Price unavailable. Check your connection."

/** What the note says: an Outfit [eyebrow], the [body] in Bert's hand, and a [link] when a tap opens the board. */
internal data class NoteFace(val eyebrow: String, val body: String, val link: String?, val spoken: String)

internal const val NO_POST = "no post today. the postman’s on strike."
internal const val OFFLINE_NOTE = "can’t reach the board right now. check your connection."
private const val OFFLINE_NOTE_SHORT = "can’t reach the board right now."

/**
 * The note under the slate. With today's note: the note itself (its whitespace folded, so three lines carry as much of it
 * as fit), when it was first seen, and the way to the board. Without one, the board's own words for why: the connection
 * when there is no feed and nothing saved ([ActivityState.Unavailable], said shorter when the slate above already says to
 * check it), the postman for a feed with nothing in it. The link stays whenever the board has notes to read; [fresh]
 * counts the notes first seen since the last visit (not today's).
 */
internal fun noteFace(state: ActivityState, entry: JournalEntry?, fresh: Int, isNew: Boolean, boardHasNotes: Boolean,
                      slateSaysOffline: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): NoteFace {
    val current = (state as? ActivityState.Available)?.activity?.dispatch
    val link = when {
        fresh == 1 -> "1 new note on the board"
        fresh > 1 -> "${fresh.coerceAtMost(6)} new notes on the board"
        else -> "Read the board"
    }
    val linkSpoken = if (fresh > 0) ". $link" else ""
    if (current != null) {
        val seen = entry?.let { boardWhen(it.firstSeenEpochMillis, now, zone, locale) }
        val head = if (isNew) "NEW ON THE BOARD" else "ON THE BOARD"
        val note = current.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        val said = (if (isNew) "New on the board" else "On the board") + (seen?.let { ", ${it.lowercase(locale)}" } ?: "")
        // Each part's words kept together, so a long eyebrow breaks at the dot, never inside "THIS MORNING".
        return NoteFace(listOfNotNull(head, seen).joinToString(" · ") { it.replace(' ', '\u00A0') }, note, link, "$said: $note$linkSpoken")
    }
    val body = when (state) {
        ActivityState.Unavailable -> if (slateSaysOffline) OFFLINE_NOTE_SHORT else OFFLINE_NOTE
        ActivityState.Loading -> "checking the board…"
        else -> NO_POST
    }
    val opens = boardHasNotes || fresh > 0
    return NoteFace("ON THE BOARD", body, link.takeIf { opens }, "On the board: $body" + if (opens) linkSpoken else "")
}

/** When a note was first seen, as the eyebrow says it: "THIS MORNING", "YESTERDAY", "MONDAY", "5 SEP"; null if ahead of the clock. */
internal fun boardWhen(seenAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? {
    val seen = Instant.ofEpochMilli(seenAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone)
    val days = ChronoUnit.DAYS.between(seen.toLocalDate(), today.toLocalDate())
    return when {
        days < 0 -> null
        days == 0L -> when (seen.hour) {
            in 5..11 -> "THIS MORNING"
            in 12..16 -> "THIS AFTERNOON"
            in 17..20 -> "THIS EVENING"
            in 21..23 -> "TONIGHT"
            else -> if (today.hour < 5) "TONIGHT" else "LAST NIGHT"
        }
        days == 1L -> "YESTERDAY"
        days < 7 -> DateTimeFormatter.ofPattern("EEEE", locale).format(seen).uppercase(locale)
        else -> DateTimeFormatter.ofPattern("d MMM", locale).format(seen).uppercase(locale).replace(".", "")
    }
}

// ---- The objects on the page ----

/** Room round the page's edge, as the column below keeps (in dp; set on whole art px). */
private val EDGE = 20.dp
private val GAP_ABOVE = 4.dp
private val GAP_BETWEEN = 12.dp
/** The price grows with the phone's text size up to this much; past it, a big number is big enough and the slate keeps it on one line. */
private const val FIGURE_SCALE_MAX = 1.5f

/**
 * The slate and the note under Bert's bubble, on the scene's grid: the same [placeFrame] as the picture above, the
 * objects' edges, sizes and the gaps between them in whole art px, so their stepped corners and outlines are the
 * garden's own pixels. Laid out across the scene's width (all of the page in portrait).
 */
@Composable
internal fun SlateAndPaper(time: PlaceTime, slate: SlateFace, note: NoteFace, openMarket: () -> Unit, openBoard: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val frame = placeFrame(with(density) { maxWidth.toPx() }, LocalConfiguration.current.screenHeightDp * density.density)
        val k = frame.k
        fun art(dp: Dp) = with(density) { (dp.toPx() / k).let { kotlin.math.round(it).toInt() } }
        val widthPx = constraints.maxWidth
        val areaRight = minOf(frame.left + frame.shown * k, widthPx)
        val margin = art(EDGE)
        val left = frame.left + margin * k
        val w = ((areaRight - frame.left) / k - 2 * margin).coerceAtLeast(1)
        val above = art(GAP_ABOVE) * k
        val between = art(GAP_BETWEEN) * k
        Layout(content = {
            Slate(k, SlateTone.of(time), slate, openMarket)
            Note(k, PaperTone.of(time), note, openBoard)
        }, Modifier.fillMaxWidth()) { measurables, constraints ->
            val each = Constraints.fixedWidth(w * k)
            val placed = measurables.map { it.measure(each) }
            val height = above + placed.sumOf { it.height } + between * (placed.size - 1)
            layout(constraints.maxWidth, height) {
                var y = above
                // Left to right whatever the language's direction, like the picture above: the grid is the picture's.
                for (p in placed) { p.place(left, y); y += p.height + between }
            }
        }
    }
}

/** Art px round an object's words: [left], [top], [right], [bottom]. */
private data class ArtInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * A press, or keyboard focus: the face catches the light (as the slate in the picture does under a finger). Only a
 * little: the chalk on it is not lit with it, and every chalk keeps 4.5:1 on the lit slate too (BERTContrastTest).
 */
internal const val LIT_GAIN = 1.08f
internal const val LIT_LIFT = 2f
private val LIT = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    LIT_GAIN, 0f, 0f, 0f, LIT_LIFT,
    0f, LIT_GAIN, 0f, 0f, LIT_LIFT,
    0f, 0f, LIT_GAIN, 0f, LIT_LIFT,
    0f, 0f, 0f, 1f, 0f)))
/**
 * Paper under a finger dips a little towards its shade instead (lighter than cream is white, which would read as a glare);
 * only so far that its eyebrow and link keep 4.5:1 while it is held (BERTContrastTest).
 */
internal const val PAPER_DIP = 0.97f
private val DIPPED = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    PAPER_DIP, 0f, 0f, 0f, 0f,
    0f, PAPER_DIP, 0f, 0f, 0f,
    0f, 0f, PAPER_DIP, 0f, 0f,
    0f, 0f, 0f, 1f, 0f)))

/**
 * One pixel object: [content] inside [insets], the whole rounded up to whole art px, with [art] (one bitmap pixel per art
 * px, for its size in art px) drawn behind at [k]. The size is set by the layout and read only when drawing, in the same
 * frame, as the bubble does, so the words never wait a frame for their paper.
 */
@Composable
private fun PixelObject(k: Int, insets: ArtInsets, art: (Int, Int) -> ObjectArt, lit: () -> ColorFilter?,
                        modifier: Modifier, grainRows: () -> IntRange = { IntRange.EMPTY }, content: @Composable () -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val cache = remember { ArtCache() }
    Layout(content, modifier.drawWithContent {
        if (size.width <= 0) { drawContent(); return@drawWithContent }
        val made = cache.get(size, art)
        val filter = lit()
        fun layer(image: ImageBitmap, alpha: Float = 1f) = drawImage(image, dstOffset = IntOffset.Zero,
            dstSize = IntSize(image.width * k, image.height * k), alpha = alpha, filterQuality = FilterQuality.None, colorFilter = filter)
        layer(made.back)
        drawContent()
        // The front layer only over [grainRows] (the price's line): grain in the small words would read as dirt.
        val rows = grainRows()
        made.front?.takeIf { !rows.isEmpty() }?.let { clipRect(top = rows.first.toFloat(), bottom = rows.last + 1f) { layer(it, GRAIN_ALPHA) } }
    }) { measurables, constraints ->
        val w = constraints.maxWidth / k
        val inner = Constraints(maxWidth = ((w - insets.left - insets.right) * k).coerceAtLeast(0))
        val words = measurables.map { it.measure(inner) }
        val wordsH = words.maxOfOrNull { it.height } ?: 0
        val rows = (wordsH + k - 1) / k + insets.top + insets.bottom
        size = IntSize(w, rows)
        layout(w * k, rows * k) {
            // The words on whole art px from the top inset; the part pixel of rounding goes under them.
            words.forEach { it.place(insets.left * k, insets.top * k) }
        }
    }
}

/** An object's pixels: [back] behind its words, and [front], if any, laid over them (the slate's chalk grain). */
private class ObjectArt(val back: ImageBitmap, val front: ImageBitmap? = null)

/** The last art built for an object, kept until its size or look changes (the art lambda changes with the tone). */
private class ArtCache {
    private var key: Pair<IntSize, Any>? = null
    private var art: ObjectArt? = null
    fun get(size: IntSize, make: (Int, Int) -> ObjectArt): ObjectArt {
        val next = size to (make as Any)
        art?.let { if (key == next) return it }
        return make(size.width, size.height).also { key = next; art = it }
    }
}

// The slate's frame in art px: a 1 px outline round 5 px of wood on three sides, and a deeper ledge along the bottom.
private const val WOOD = 5
private const val LEDGE = 6
/** The frame's stepped corner (how far in each of the outline's first rows starts): squarer than the paper's, as wood is. */
private val SLATE_CORNER = intArrayOf(2, 1)

@Composable
private fun Slate(k: Int, tone: SlateTone, face: SlateFace, open: () -> Unit) {
    val density = LocalDensity.current
    fun art(dp: Dp) = with(density) { kotlin.math.round(dp.toPx() / k).toInt() }
    // Inside the frame: the slate's shaded top two rows and left column, then room for the chalk.
    val insets = ArtInsets(1 + WOOD + 1 + art(12.dp), 1 + WOOD + 2 + art(5.dp), 1 + WOOD + art(10.dp), 1 + LEDGE + art(6.dp))
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val focused by press.collectIsFocusedAsState()
    val art = remember(tone) { { w: Int, h: Int -> slateArt(w, h, tone) } }
    // The price's row, set by the words' layout (from their top), where the chalk grain goes.
    val band = remember { IntArray(2) }
    PixelObject(k, insets, art, { if (pressed || focused) LIT else null },
        Modifier.testTag("home-price")
            .clickable(press, indication = null, onClickLabel = "Open Market", role = Role.Button, onClick = open)
            .semantics(mergeDescendants = true) { contentDescription = face.spoken },
        grainRows = { insets.top * k + band[0] until insets.top * k + band[1] }) {
        SlateWords(face, tone, band)
    }
}

/**
 * The chalk on the slate: small "BERT", the price large, the change in coloured chalk and a "›" at the right; under them
 * the period and the time. Laid out by hand, so that nothing wraps where it should not:
 *  - the price and the change never wrap inside themselves;
 *  - when the top line cannot hold all three (large text, a narrow phone), the change drops to the next line and takes
 *    its period with it ("+1.63% past 24 hours"), and the time has a line of its own; "BERT" goes above the price if
 *    even the two of them do not fit;
 *  - the time's words are kept together, so a line breaks at the dot between period and time, never inside "9:09 am".
 * [band] is set to the price row's top and bottom (px from the words' top), for the chalk grain.
 */
@Composable
private fun SlateWords(face: SlateFace, tone: SlateTone, band: IntArray) {
    val fontScale = LocalDensity.current.fontScale
    val capped = minOf(fontScale, FIGURE_SCALE_MAX) / fontScale
    fun chalk(size: Float, color: Color, line: Float? = null) = TextStyle(fontFamily = BertHand, fontSize = size.sp, color = color,
        lineHeight = line?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified)
    val small = chalk(15f, tone.chalkSoft, 19f)
    Layout(content = {
        Text("BERT", Modifier.layoutId("label"), style = chalk(16f, tone.chalkSoft), maxLines = 1, softWrap = false)
        if (face.price != null) Text(face.price, Modifier.layoutId("main").testTag("slate-price"), style = chalk(31f * capped, tone.chalk),
            maxLines = 1, softWrap = false)
        else Text(face.status.orEmpty(), Modifier.layoutId("main").testTag("slate-status"), style = chalk(21f, tone.chalk, 25f))
        face.change?.let {
            Text(it, Modifier.layoutId("change").testTag("slate-change"), style = chalk(21f, if (face.falling) tone.red else tone.green),
                maxLines = 1, softWrap = false)
            Text("past 24 hours", Modifier.layoutId("period").testTag("slate-period"), style = small, maxLines = 1, softWrap = false)
        }
        Text("›", Modifier.layoutId("arrow"), style = chalk(26f, tone.chalkSoft), maxLines = 1, softWrap = false)
        face.time?.let {
            Text("·", Modifier.layoutId("dot"), style = small, maxLines = 1, softWrap = false)
            // Each part's words kept together ("as of 9:09 am", "updated 42m ago"); it breaks at its dots if it must.
            Text(it.split(" · ").joinToString(" · ") { part -> part.replace(' ', '\u00A0') }, Modifier.layoutId("time").testTag("slate-time"), style = if (face.delayed) small.copy(color = tone.yellow) else small)
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 9.dp.roundToPx()
        val dotGap = 6.dp.roundToPx()
        fun find(id: String) = measurables.firstOrNull { it.layoutId == id }
        val loose = Constraints()
        val arrow = find("arrow")!!.measure(loose)
        val room = (width - arrow.width - gap).coerceAtLeast(0)
        val label = find("label")!!.measure(loose)
        val main = find("main")!!.let { if (face.price != null) it.measure(loose) else it.measure(Constraints(maxWidth = room)) }
        val change = find("change")?.measure(loose)
        val period = find("period")?.measure(loose)
        val dot = find("dot")?.measure(loose)
        val time = find("time")?.measure(Constraints(maxWidth = width))
        /** A row of words on one baseline, [gaps] between them; null entries are left out. */
        class Row(val items: List<Placeable>, val gaps: List<Int>) { val w = items.sumOf { it.width } + gaps.sum() }
        fun row(vararg p: Placeable?, between: Int = gap): Row { val items = p.filterNotNull(); return Row(items, List((items.size - 1).coerceAtLeast(0)) { between }) }
        fun fits(r: Row, max: Int = room) = r.w <= max
        val rows = mutableListOf<Row>()
        val top = row(label, main, change)
        val together = fits(row(label, main))
        if (together) rows += if (change != null && fits(top)) top else row(label, main) else { rows += row(label); rows += row(main) }
        val dropped = change != null && !(together && fits(top))
        val mainRow = rows.indexOfFirst { main in it.items }
        // Under the price: either the change with its period, then the time; or the period, a dot and the time.
        if (dropped) {
            if (fits(row(change, period), width)) rows += row(change, period) else { rows += row(change); rows += row(period) }
            time?.let { rows += row(it) }
        } else {
            val second = Row(listOfNotNull(period, dot.takeIf { period != null }, time), if (period != null) listOf(dotGap, dotGap) else emptyList())
            when {
                second.items.isEmpty() -> {}
                fits(second, width) -> rows += second
                else -> { period?.let { rows += row(it) }; time?.let { rows += row(it) } }
            }
        }
        fun Placeable.base() = this[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: height
        val tops = IntArray(rows.size); val bases = IntArray(rows.size)
        var y = 0
        rows.forEachIndexed { i, r ->
            val ascent = r.items.maxOf { it.base() }
            val descent = r.items.maxOf { it.height - it.base() }
            tops[i] = y; bases[i] = y + ascent
            y += ascent + descent
        }
        band[0] = tops[mainRow]; band[1] = if (mainRow + 1 < rows.size) tops[mainRow + 1] else y
        layout(width, maxOf(y, arrow.height)) {
            rows.forEachIndexed { i, r ->
                var x = 0
                r.items.forEachIndexed { j, p -> p.place(x, bases[i] - p.base()); x += p.width + (r.gaps.getOrNull(j) ?: 0) }
            }
            arrow.place(width - arrow.width, bases[mainRow] - arrow.base())
        }
    }
}

/**
 * The slate, one bitmap pixel per art px: a dark outline with stepped corners round a carpentered wooden frame (four
 * pieces mitred at the corners, lit along the outside top and left and the inside bottom and right, a few darker grain
 * lines, a deeper ledge at the foot with a stub of chalk on it), and the slate set into it, shaded along its top and left
 * where the frame overhangs, with the haze of old chalk wiped off, as the chalkboard in the picture has.
 */
private fun slateArt(w: Int, h: Int, tone: SlateTone): ObjectArt {
    val px = IntArray(w * h)
    val outline = tone.outline.toArgb(); val light = tone.woodLight.toArgb(); val wood = tone.wood.toArgb(); val dark = tone.woodDark.toArgb()
    val slate = tone.slate.toArgb(); val shade = tone.slateShade.toArgb(); val smudge = tone.smudge.toArgb()
    fun inside(x: Int, y: Int): Boolean {
        if (x !in 0 until w || y !in 0 until h) return false
        val edge = minOf(y, h - 1 - y)
        val inset = if (edge < SLATE_CORNER.size) SLATE_CORNER[edge] else 0
        return x >= inset && x < w - inset
    }
    val faceL = 1 + WOOD; val faceT = 1 + WOOD; val faceR = w - 1 - WOOD; val faceB = h - 1 - LEDGE // exclusive right/bottom
    fun face(x: Int, y: Int) = x in faceL until faceR && y in faceT until faceB
    for (y in 0 until h) for (x in 0 until w) {
        if (!inside(x, y)) continue
        val edge = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1)
        // The corners are mitred: in each corner square, the diagonal from the outside corner to the inside one (the
        // bottom's is stretched to the deeper ledge).
        val corner = (x < faceL || x >= faceR) && (y < faceT || y >= faceB)
        val across = if (x < faceL) x - 1 else w - 2 - x
        val down = if (y < faceT) y - 1 else (h - 2 - y) * WOOD / LEDGE
        val mitre = corner && across == down
        px[y * w + x] = when {
            edge -> outline
            face(x, y) -> if (y < faceT + 2 || x == faceL) shade else slate
            mitre -> dark
            // Outside edges: lit from above and the left, shadowed below and on the right.
            y == 1 || x == 1 -> light
            y == h - 2 || x == w - 2 -> dark
            // Inside edges: the frame's lip throws the top and left into shadow and catches the light at the bottom and right.
            y == faceT - 1 && x in faceL - 1..faceR -> dark
            x == faceL - 1 && y in faceT - 1 until faceB -> dark
            y == faceB && x in faceL..faceR -> light
            x == faceR && y in faceT until faceB -> light
            else -> wood
        }
    }
    // Grain: short darker runs along each piece, placed by a hash of their position so they keep still.
    fun hash(a: Int, b: Int) = ((a * 73_856_093) xor (b * 19_349_663)).let { (it xor (it ushr 13)) and 0x7fffffff }
    fun grain(x: Int, y: Int) { if (px[y * w + x] == wood) px[y * w + x] = dark }
    for (y in 3 until faceT - 1) { var x = 6 + hash(y, 1) % 9; while (x < w - 8) { val len = 4 + hash(x, y) % 7; for (i in 0 until len) grain(x + i, y); x += len + 14 + hash(y, x) % 23 } }
    for (y in faceB + 2 until h - 2) { var x = 5 + hash(y, 2) % 11; while (x < w - 8) { val len = 5 + hash(x, y) % 8; for (i in 0 until len) grain(x + i, y); x += len + 12 + hash(y, x) % 21 } }
    for (x in listOf(3, 4, w - 4, w - 5)) { var y = faceT + 1 + hash(x, 3) % 5; while (y < faceB - 2) { val len = 3 + hash(x, y) % 4; for (i in 0 until len) grain(x, y + i); y += len + 7 + hash(y, x) % 9 } }
    // The haze of old chalk wiped off: two soft clouds, solid at the heart and dithered at the rim, faint enough that the
    // chalk on top keeps its contrast (checked against [SlateTone.smudge]).
    val faceW = faceR - faceL; val faceH = faceB - faceT
    for ((cx, cy, rx) in listOf(Triple(0.63f, 0.42f, 0.17f), Triple(0.22f, 0.70f, 0.11f))) {
        val mx = faceL + faceW * cx; val my = faceT + faceH * cy; val ax = faceW * rx; val ay = maxOf(3f, faceH * 0.16f)
        for (y in faceT + 2 until faceB) for (x in faceL + 1 until faceR) {
            // Leaning 2:1, like a wipe of the hand.
            val u = (x - mx) / ax; val v = (y - my + (x - mx) * 0.18f) / ay
            val d = u * u + v * v
            if (px[y * w + x] != slate) continue
            if (d < 0.55f || (d < 1f && (x + y) % 2 == 0)) px[y * w + x] = smudge
        }
    }
    // A stub of chalk resting on the ledge, towards the right, where a hand would put it down: lit on top, shaded under.
    val chalk = tone.chalk.toArgb(); val chalkShade = tone.chalkSoft.toArgb()
    val stubX = faceR - 22
    if (stubX > faceL + 8) {
        for (x in stubX until stubX + 9) { px[(faceB + 1) * w + x] = chalk; px[(faceB + 2) * w + x] = chalkShade }
        px[(faceB + 1) * w + stubX] = chalkShade
        for (x in stubX + 1 until stubX + 10) if (px[(faceB + 3) * w + x] != outline) px[(faceB + 3) * w + x] = dark
    }
    // Chalk is never solid: a scatter of the slate's own pixels, laid half-strength over the words, lets the board show
    // through the strokes in the scene's grid. Over bare slate it is the slate itself, so it shows only where the chalk is.
    val grain = IntArray(w * h)
    for (y in faceT + 2 until faceB) for (x in faceL + 1 until faceR) if (hash(x * 3 + 1, y * 5 + 2) % 100 < GRAIN_PERCENT) grain[y * w + x] = px[y * w + x]
    return ObjectArt(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
        Bitmap.createBitmap(grain, w, h, Bitmap.Config.ARGB_8888).asImageBitmap())
}

/** How many of the slate's pixels show through the chalk, and how strongly. */
private const val GRAIN_PERCENT = 16
private const val GRAIN_ALPHA = 0.55f

// The paper's pin, in art px: a round head 8 px across inside its 1 px outline, its top this far down the note.
private const val PIN_HEAD = 8
private const val PIN_TOP = 4
/** The paper's stepped corner, the bubble's own. */
private val PAPER_CORNER = intArrayOf(3, 1, 1)
/** The bottom right corner is turned up this far (art px). */
private const val DOG_EAR = 8

@Composable
private fun Note(k: Int, tone: PaperTone, face: NoteFace, open: () -> Unit) {
    val density = LocalDensity.current
    fun art(dp: Dp) = with(density) { kotlin.math.round(dp.toPx() / k).toInt() }
    val insets = ArtInsets(1 + art(15.dp), PIN_TOP + PIN_HEAD + 2 + art(8.dp), 1 + art(15.dp), 1 + 2 + art(10.dp))
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val focused by press.collectIsFocusedAsState()
    val art = remember(tone) { { w: Int, h: Int -> ObjectArt(paperBitmap(w, h, tone)) } }
    val opens = face.link != null
    val tap = if (opens) Modifier.clickable(press, indication = null, onClickLabel = "Read the noticeboard", role = Role.Button, onClick = open) else Modifier
    PixelObject(k, insets, art, { if (opens && (pressed || focused)) DIPPED else null },
        Modifier.testTag("board-row").then(tap).semantics(mergeDescendants = true) { contentDescription = face.spoken }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(face.eyebrow, color = tone.eyebrow, fontFamily = Outfit, fontSize = 11.sp, lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text(face.body, Modifier.testTag("note-body"), color = tone.paper.ink, fontFamily = BertHand, fontSize = 19.sp, lineHeight = 24.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            face.link?.let {
                Text("$it ›", Modifier.testTag("note-link"), color = tone.link, fontFamily = Outfit, fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The note, one bitmap pixel per art px: the bubble's paper with its stepped corners and outline and the same two rows
 * of shade along the bottom (the paper's thickness), the bottom right corner turned up, and a red pin at the top centre
 * with a highlight, a shadow side, and its shadow on the paper.
 */
private fun paperBitmap(w: Int, h: Int, tone: PaperTone): ImageBitmap {
    val px = IntArray(w * h)
    val outline = tone.paper.outline.toArgb(); val fill = tone.paper.fill.toArgb(); val shade = tone.paper.shade.toArgb()
    // The turned-up corner: the corner itself is gone (behind the fold), and the flap lies on the paper.
    val earX = w - 1 - DOG_EAR; val earY = h - 1 - DOG_EAR
    fun cut(x: Int, y: Int) = x >= earX && y >= earY && (x - earX) + (y - earY) > DOG_EAR
    fun inside(x: Int, y: Int): Boolean {
        if (x !in 0 until w || y !in 0 until h || cut(x, y)) return false
        val edge = minOf(y, h - 1 - y)
        val inset = if (edge < PAPER_CORNER.size) PAPER_CORNER[edge] else 0
        return x >= inset && x < w - inset
    }
    for (y in 0 until h) for (x in 0 until w) {
        if (!inside(x, y)) continue
        val edge = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1)
        val flap = x >= earX && y >= earY
        px[y * w + x] = when {
            edge -> outline
            // The flap's two folded edges are outlined; inside it is the paper's underside, in shade.
            flap && (x == earX || y == earY) -> outline
            flap -> shade
            !inside(x, y + 2) || !inside(x, y + 3) -> shade
            else -> fill
        }
    }
    // The pin: a round head (a 10 px disc with its outline), a glint up and to the left, a shaded crescent down and to
    // the right, and its shadow on the paper two px below and one to the right.
    val pin = tone.pin.toArgb(); val pinLight = tone.pinLight.toArgb(); val pinDark = tone.pinDark.toArgb()
    val px0 = (w - PIN_HEAD) / 2 - 1; val py0 = PIN_TOP - 1
    val size = PIN_HEAD + 2
    val mid = (size - 1) / 2f
    fun head(x: Int, y: Int) = x in 0 until size && y in 0 until size && (x - mid) * (x - mid) + (y - mid) * (y - mid) <= mid * mid + mid
    for (y in 0 until size) for (x in 0 until size) {
        val ax = px0 + x + 1; val ay = py0 + y + 2
        if (head(x, y) && ax in 0 until w && ay in 0 until h && px[ay * w + ax] == fill) px[ay * w + ax] = shade
    }
    for (y in 0 until size) for (x in 0 until size) {
        val ax = px0 + x; val ay = py0 + y
        if (!head(x, y) || ax !in 0 until w || ay !in 0 until h) continue
        val rim = !head(x - 1, y) || !head(x + 1, y) || !head(x, y - 1) || !head(x, y + 1)
        // The lit side is a disc pushed up and to the left; whatever of the head lies outside it is in shade.
        val lit = (x - mid + 1.2f) * (x - mid + 1.2f) + (y - mid + 1.2f) * (y - mid + 1.2f) <= (mid - 1.1f) * (mid - 1.1f)
        px[ay * w + ax] = when {
            rim -> outline
            (x == 3 || x == 4) && y == 2 || x == 2 && (y == 3 || y == 4) -> pinLight
            lit -> pin
            else -> pinDark
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
