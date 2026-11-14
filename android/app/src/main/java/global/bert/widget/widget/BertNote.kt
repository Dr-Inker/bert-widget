package global.bert.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import global.bert.widget.R
import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTEvent
import global.bert.widget.data.EventPhase
import global.bert.widget.formatObservedAt
import global.bert.widget.moodPhrase
import global.bert.widget.moodStill
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Bert's day widget, 0.15: a note from Bert, on paper. Pixel Bert acts the feed's mood, his words are in his own hand
 * (Gochi Hand), and the tournament is chalked on a small slate. The rule for the whole app: numbers are chalked on slate;
 * Bert's words are on paper in his handwriting.
 *
 * Glance cannot set a font on Text, so every piece of words is drawn here into a bitmap at the widget's exact pixel size
 * and the phone's text size, and shown unscaled (layout/widget_note_words.xml) with its full text as the content
 * description. Because the text is measured with the real typeface rather than estimated, the layout is exact: what is
 * planned here is what the launcher shows.
 */

/** One drawn piece of the note. [description] is what TalkBack reads (null: decorative); [shown] is the visible text. */
internal class NotePiece(val bitmap: Bitmap, val description: String?, val shown: String? = null, val lines: Int = 1) {
    val width get() = bitmap.width
    val height get() = bitmap.height
}

/** Where everything goes, in device pixels. [wide]: Bert in a panel on the left; otherwise he heads a stacked note. */
internal class NotePlan(
    val wide: Boolean,
    val density: Float,
    val pad: Int,
    val bottomPad: Int,
    /** Wide only: the panel Bert stands in, and the gap between it and the words. */
    val panelWidth: Int,
    val columnGap: Int,
    /** Pixel Bert, or the holder's own Bert. */
    val bert: NotePiece,
    /** Device pixels per art pixel of pixel Bert, or null when it is the holder's own picture. */
    val pixelsPerArtPixel: Int?,
    val caption: NotePiece?,
    /** When this copy of Bert's feed is from: the eyebrow over the post (wide) or the line under the caption (stacked). */
    val stamp: NotePiece?,
    val post: NotePiece?,
    val slate: NotePiece?,
    val captionGap: Int,
    val postGap: Int,
    /** Stacked only: the mood on its own line under Bert, where it does not fit beside him. */
    val captionBelow: Boolean = false,
) {
    val pieces get() = listOfNotNull(bert, caption, stamp, post, slate)
    /** Bitmap bytes this render hands the launcher. */
    val bytes get() = pieces.sumOf { it.bitmap.allocationByteCount.toLong() }
}

/** The pose for the feed's mood: Home's mapping ([moodStill]), plus his nap, which Home only draws in the garden. */
internal class NotePose(val drawable: Int, val crop: Rect)

/** Every opaque pixel of the six standing mood stills and F/10 (84 x 84) lies in this box; their feet share its bottom. */
internal val STANDING_ART = Rect(9, 2, 73, 84)
/** bert_mood_asleep (84 x 56): curled up, wider than tall. */
internal val ASLEEP_ART = Rect(7, 12, 77, 50)

internal fun notePose(mood: String?): NotePose =
    if (mood?.trim()?.lowercase() in setOf("asleep", "sleeping", "sleepy")) NotePose(R.drawable.bert_mood_asleep, ASLEEP_ART)
    else NotePose(moodStill(mood), STANDING_ART)

/** The handwritten caption, longest first: Home's character note for the mood ([moodPhrase]), then the bare word. */
internal fun captionForms(mood: String): List<String> {
    val word = mood.trim().lowercase()
    if (word in setOf("asleep", "sleeping", "sleepy")) return listOf("fast asleep", "asleep")
    val phrase = moodPhrase(word)
    return listOf(if (phrase.startsWith("feeling")) phrase else "feeling $phrase", word).distinct()
}

/**
 * The slate's line, longest first, in sentence case: the event's name and what is left, else "Flappy" and what is left.
 * Never a bare countdown, never a name cut short (a name that does not fit gives way to "Flappy").
 */
internal fun slateForms(event: BERTEvent, now: Long): List<String> {
    val (days, hours) = timeLeft(event, now)
    val left = if (days >= 1) "${days}d ${hours}h left" else if (hours >= 1) "${hours}h left" else "under 1h left"
    return listOfNotNull("${event.name.trim()} · $left", "Flappy · $left", if (days >= 1) "Flappy · ${days}d left" else null)
}

private fun timeLeft(event: BERTEvent, now: Long): Pair<Long, Long> {
    val ms = (event.endsAtEpochMillis - now).coerceAtLeast(0)
    return ms / 86_400_000 to ms % 86_400_000 / 3_600_000
}

/** What TalkBack says for the slate: the whole name and the time left in words. */
internal fun slateDescription(event: BERTEvent, now: Long): String {
    val (days, hours) = timeLeft(event, now)
    fun unit(n: Long, one: String) = "$n $one${if (n == 1L) "" else "s"}"
    val left = when {
        days >= 1 -> "${unit(days, "day")} ${unit(hours, "hour")} left"
        hours >= 1 -> "${unit(hours, "hour")} left"
        else -> "under an hour left"
    }
    return "Flappy Bert tournament, ${event.name.trim()}, $left"
}

/** Shown when no feed has ever arrived. It is the app speaking, not Bert, so it is not in his handwriting. */
internal const val NO_NOTE_YET = "Bert’s next note will appear here."

private class NoteFonts(val hand: Typeface, val label: Typeface, val plain: Typeface)

@Volatile private var cachedFonts: NoteFonts? = null

private fun fonts(context: Context): NoteFonts = cachedFonts ?: NoteFonts(
    context.resources.getFont(R.font.gochi_hand), context.resources.getFont(R.font.outfit_semibold), context.resources.getFont(R.font.outfit_medium),
).also { cachedFonts = it }

/**
 * Plans and draws the note for a [widthDp] x [heightDp] widget at the phone's density and text size. Bitmaps are made
 * here once per render (the widget is re-rendered on new data or a resize, never on a timer).
 */
internal fun planNote(
    context: Context, widthDp: Float, heightDp: Float, activity: BERTActivity?, now: Long, myBert: WidgetBert?, use24Hour: Boolean,
): NotePlan = NotePlanner(context, widthDp, heightDp, activity, now, myBert, use24Hour).plan()

private class NotePlanner(
    private val context: Context, private val widthDp: Float, private val heightDp: Float, private val activity: BERTActivity?,
    private val now: Long, private val myBert: WidgetBert?, private val use24Hour: Boolean,
) {
    private val density = context.resources.displayMetrics.density
    private val fonts = fonts(context)
    private fun dp(v: Float) = (v * density).roundToInt()
    /** Text size in px, as the system scales sp: Android 14's curve for large text included, like Home and the other widgets. */
    private fun sp(v: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics)
    private fun color(id: Int) = context.getColor(id)

    private val width = dp(widthDp)
    private val height = dp(heightDp)
    private val wide = widthDp >= 230f && widthDp >= heightDp * 1.45f
    private val pad = dp(if (heightDp < 120f) 8f else if (heightDp < 130f || widthDp < 140f) 10f else 14f)
    private val bottomPad = pad + context.resources.getDimensionPixelSize(R.dimen.note_lip)
    private val delayed = activity?.isStaleAt(now) ?: true
    private val pose = notePose(activity?.mood)
    private val event = activity?.event?.takeIf { it.phaseAt(now, delayed) == EventPhase.OPEN }
    private val time = activity?.let { formatObservedAt(it.updatedAtEpochMillis, now, use24Hour = use24Hour, dateOnlyWhenOld = true).removePrefix("As of ") }

    fun plan(): NotePlan = if (wide) planWide() else planStacked()

    // ---- Wide: Bert in a shaded panel on the left, the note on the right. ----------------------------------------------

    private fun planWide(): NotePlan {
        val inner = height - pad - bottomPad
        // The panel is about a third of the width; it widens until the caption's longest word fits without breaking.
        var panel = dp((widthDp * 0.34f).coerceIn(88f, 136f))
        var caption: NotePiece? = null
        val mood = activity?.mood
        if (mood != null) {
            while (true) {
                caption = caption(mood, panel - 2 * dp(8f), maxLines = 2, sizes = 17 downTo 12, centred = true, oneLineFloor = if (heightDp < 140f) 12 else 14)
                if (caption != null || panel >= width * 0.46f) break
                panel += dp(4f)
            }
            if (caption == null) caption = caption(mood, panel - 2 * dp(6f), maxLines = 3, sizes = 12 downTo 10, centred = true, anyBreak = true)
        }
        val captionGap = if (caption != null) dp(2f) else 0
        val bertRoomW = panel - 2 * dp(8f)
        val bertRoomH = (inner - (caption?.height ?: 0) - captionGap).coerceAtMost((height * 0.66f).toInt())
        val (bert, k) = bert(bertRoomW, bertRoomH)
        val gap = dp(if (heightDp < 130f) 12f else 16f)
        val columnW = width - panel - gap - pad
        val sizes = PostSizes(max = (heightDp * 0.105f).coerceIn(16f, 22f), preferred = (heightDp * 0.095f).coerceIn(15f, 20f), min = 13f)
        val column = sequenceOf(true to true, true to false, false to false)
            .mapNotNull { (eyebrow, slate) -> column(columnW, inner, eyebrow, slate, sizes, minLines = 2) }.firstOrNull()
            ?: column(columnW, inner, eyebrow = false, slate = false, sizes, minLines = 1)
        return NotePlan(true, density, pad, bottomPad, panel, gap, bert, k, caption, column?.eyebrow, column?.post, column?.slate,
            captionGap, column?.postGap ?: 0)
    }

    // ---- Stacked: Bert, his mood and the time across the top; the note below. --------------------------------------------

    private fun planStacked(): NotePlan {
        val innerW = width - 2 * pad
        val innerH = height - pad - bottomPad
        val art = pose.crop
        val beside = dp(8f)
        val headerGap = dp(if (heightDp < 120f) 4f else 6f)
        val sizes = PostSizes(max = (minOf(widthDp, heightDp) * 0.1f).coerceIn(14f, 19f), preferred = (minOf(widthDp, heightDp) * 0.09f).coerceIn(14f, 17f), min = 12f)
        // Pixel Bert's sizes, largest first: at most about half the note each way.
        val kMax = maxOf(1, floor(minOf(innerW * 0.5f / art.width(), innerH * 0.6f / art.height())).toInt())
        // Holder's Bert: a square from about half the height down to 20dp, in 4dp steps.
        val sides = myBert?.let { generateSequence(minOf(innerW / 2, (innerH * 0.5f).toInt())) { it - dp(4f) }.takeWhile { it >= dp(20f) }.toList().ifEmpty { listOf(dp(20f)) } }
        val mood = activity?.mood

        val short = heightDp < 140f || widthDp < 140f
        // Down to the 10sp floor every widget keeps (FRESHNESS_SP); below 12sp only where a long mood word needs it.
        val captionSizes = 16 downTo 10
        val oneLineFloor = if (short) 12 else 14

        /** One rung of the ladder; [minLines] 0 means the post may go (the last resort, far beyond Android's sizes). */
        fun attempt(scale: Int, stamp: Boolean, slate: Boolean, minLines: Int): NotePlan? {
            val bertW = if (sides != null) scale else art.width() * scale
            val bertH = if (sides != null) scale else art.height() * scale
            val room = innerW - bertW - beside
            val wantStamp = stamp && time != null
            // In the last rungs the post is starving: any one-line form of the mood beats two lines.
            val floor = if (minLines <= 1) 10 else oneLineFloor
            // The mood and the time beside Bert; where they do not fit beside him, on their own lines under him.
            var caption = mood?.let { caption(it, room, 2, captionSizes, centred = false, oneLineFloor = floor) }
            var stampPiece = if (wantStamp) stamp(room, compact = true) else null
            val below = (mood != null && caption == null) || (wantStamp && stampPiece == null)
            if (below) {
                caption = mood?.let { caption(it, innerW, 2, captionSizes, centred = false, oneLineFloor = floor) ?: return null }
                stampPiece = if (wantStamp) stamp(innerW, compact = true) ?: return null else null
            }
            val captionGap = if (caption != null && (stampPiece != null || below)) dp(2f) else 0
            val words = (caption?.height ?: 0) + (if (caption != null && stampPiece != null) captionGap else 0) + (stampPiece?.height ?: 0)
            val header = if (below) bertH + captionGap + words else maxOf(bertH, words)
            val column = column(innerW, innerH - header - headerGap, eyebrow = false, slate = slate, sizes, minLines) ?: return null
            val (bert, k) = if (sides != null) myBertPiece(scale) to null else sprite(scale) to scale
            return NotePlan(false, density, pad, bottomPad, 0, beside, bert, k, caption, stampPiece, column.post, column.slate,
                captionGap, if (column.post != null) headerGap + column.postGap else 0, captionBelow = below)
        }

        // Bert's size is the widget's, not the text's: about half the height, at most about half the width.
        // He keeps it while the slate gives way; he shrinks only when Bert's words would otherwise lose their lines.
        val scales = sides ?: (kMax downTo 1).toList()
        val target = if (sides != null) scales.firstOrNull { it <= innerH * 0.48f && it <= innerW * 0.5f } ?: scales.last()
            else scales.firstOrNull { art.height() * it <= innerH * 0.48f && art.width() * it <= innerW * 0.5f } ?: 1
        val smaller = scales.filter { it < target }
        // A tall, narrow note has the height for three lines under him; elsewhere two lines are the floor.
        val lines = if (heightDp >= widthDp * 1.3f) 3 else 2
        val ladder = sequence {
            yield(listOf(target, 1, 1, lines))
            yield(listOf(target, 1, 0, lines))
            for (k in smaller) yield(listOf(k, 1, 0, 2))
            for (k in listOf(target) + smaller) yield(listOf(k, 0, 0, 2))
            yield(listOf(scales.last(), 0, 0, 1))
            yield(listOf(scales.last(), 0, 0, 0))
        }
        ladder.mapNotNull { (k, stamp, slate, lines) -> attempt(k, stamp == 1, slate == 1, lines) }.firstOrNull()?.let { return it }
        // Beyond anything Android offers: Bert alone, with as much of his mood as fits.
        val bert = if (sides != null) myBertPiece(scales.last()) else sprite(1)
        val caption = mood?.let { caption(it, innerW, 2, 12 downTo 10, centred = false, anyBreak = true) }
            ?.takeIf { bert.height + dp(2f) + it.height <= innerH }
        return NotePlan(false, density, pad, bottomPad, 0, beside, bert, if (sides != null) null else 1, caption, null, null, null,
            dp(2f), 0, captionBelow = true)
    }

    // ---- Pieces ------------------------------------------------------------------------------------------------------------

    private class Column(val eyebrow: NotePiece?, val post: NotePiece?, val slate: NotePiece?, val postGap: Int)
    private class PostSizes(val max: Float, val preferred: Float, val min: Float)

    /** The words column: an optional eyebrow, the post, and an optional slate at the foot. Null when the post would lose its lines. */
    private fun column(columnW: Int, columnH: Int, eyebrow: Boolean, slate: Boolean, sizes: PostSizes, minLines: Int): Column? {
        val eyebrowPiece = if (eyebrow && time != null) stamp(columnW, compact = false) ?: return null else null
        val slatePiece = if (slate && event != null) slate(event, columnW) ?: return null else null
        val postGap = if (eyebrowPiece != null) dp(4f) else 0
        val slateGap = if (slatePiece != null) dp(8f) else 0
        val room = columnH - (eyebrowPiece?.height ?: 0) - postGap - (slatePiece?.height ?: 0) - slateGap
        if (room <= 0) return if (minLines == 0) Column(eyebrowPiece, null, slatePiece, postGap) else null
        val post = post(columnW, room, sizes, maxOf(1, minLines)) ?: if (minLines == 0) null else return null
        return Column(eyebrowPiece, post, slatePiece, postGap)
    }

    private fun post(columnW: Int, room: Int, postSizes: PostSizes, minLines: Int): NotePiece? {
        val text = activity?.dispatch
        // The app's own line is quieter than Bert's words: never larger than 15sp.
        val sizes = if (text != null) postSizes else PostSizes(minOf(postSizes.max, 15f), minOf(postSizes.preferred, 15f), minOf(postSizes.min, 12f))
        val paint = if (text != null) paint(fonts.hand, color(R.color.note_ink)) else paint(fonts.plain, color(R.color.note_caption))
        val body = text ?: NO_NOTE_YET
        val lineEm = if (text != null) 1.16f else 1.25f
        // The largest size at which all of it fits, down to just under the reading size (a long post is not shrunk to a
        // wall of small words); else the reading size, cut at a word, keeping at least [minLines] lines.
        for (s in sizeSteps(sizes.max, maxOf(sizes.min, sizes.preferred - 1f))) {
            val words = Words(paint.withSize(sp(s)), columnW, Layout.Alignment.ALIGN_NORMAL, lineEm)
            val whole = words.layout(body)
            if (words.heightOf(whole) <= room && breaksAtSpaces(body, whole)) return words.piece(body, whole, body)
        }
        // Cut to fit: the largest size from the reading size down that keeps at least four words (critique 2026-11-12
        // ART-4: "woofmornin. …" is not a post); fewer only as the last resort.
        // Two lines is not much of a post. Where the reading size leaves only two, a hand up to 3sp smaller is taken
        // if it gives a third line: on a 2x2 note that is his words where there was a band of bare paper. A note with
        // three lines or more keeps the larger hand.
        var fallback: NotePiece? = null
        var chosen: NotePiece? = null
        var chosenLines = 0
        var floor = sizes.min
        for (s in sizeSteps(sizes.preferred, sizes.min)) {
            if (s < floor - 0.01f) break
            val words = Words(paint.withSize(sp(s)), columnW, Layout.Alignment.ALIGN_NORMAL, lineEm)
            var lines = words.linesWithin(body, room) + 1
            while (lines >= minLines) {
                val cut = words.cutAtWord(body, lines)
                if (cut != null && words.heightOf(cut.second) <= room) {
                    val count = wordCount(cut.first)
                    if (count >= MIN_POST_WORDS) {
                        val shownLines = cut.second.lineCount
                        if (chosen == null) {
                            chosen = words.piece(cut.first, cut.second, body); chosenLines = shownLines
                            if (shownLines > 2) return chosen
                            floor = maxOf(sizes.min, s - 3f)
                        } else if (shownLines > chosenLines) return words.piece(cut.first, cut.second, body)
                    } else if (minLines == 1 && fallback == null) fallback = words.piece(cut.first, cut.second, body)
                    break
                }
                lines--
            }
        }
        if (chosen != null) return chosen
        // Far beyond Android's text sizes a single word can be wider than the column. Rather than no post at all, the
        // last resort breaks inside words and ends in an ellipsis, at the smallest size.
        if (fallback == null && minLines <= 1) {
            val words = Words(paint.withSize(sp(sizes.min)), columnW, Layout.Alignment.ALIGN_NORMAL, lineEm)
            val lines = words.linesWithin(body, room)
            if (lines >= 1) {
                val l = words.layout(body, maxLines = lines)
                if (words.heightOf(l) <= room) {
                    val last = l.lineCount - 1
                    val shown = if (l.getEllipsisCount(last) > 0) body.substring(0, l.getLineStart(last) + l.getEllipsisStart(last)) + "…" else body
                    fallback = words.piece(shown, l, body)
                }
            }
        }
        return fallback
    }

    /** "feeling content" in Bert's hand: whole words only, on at most [maxLines] lines, at the largest size that holds it. */
    private fun caption(mood: String, widthPx: Int, maxLines: Int, sizes: IntProgression, centred: Boolean, anyBreak: Boolean = false, oneLineFloor: Int = 14): NotePiece? {
        if (widthPx <= 0) return null
        val forms = captionForms(mood)
        // One line at a comfortable size reads best; then two lines at the largest size; then the bare word. On a short
        // note any one-line form (even the bare word) beats two lines, which would take the post's room.
        val oneLine = sizes.filter { it >= oneLineFloor }
        val tries = if (oneLineFloor <= 12) forms.map { Triple(it, 1, oneLine) } + forms.map { Triple(it, maxLines, sizes.toList()) }
            else forms.flatMap { listOf(Triple(it, 1, oneLine), Triple(it, maxLines, sizes.toList())) }
        for ((form, lines, steps) in tries) for (s in steps) {
            val words = Words(paint(fonts.hand, color(R.color.note_caption), sp(s.toFloat())), widthPx,
                if (centred) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL, 1.0f)
            val layout = words.layout(form)
            if (layout.lineCount <= lines && (anyBreak || breaksAtSpaces(form, layout))) return words.piece(form, layout, "Bert is ${forms.first()}", snug = !centred)
        }
        return null
    }

    /**
     * The freshness line. Over the post (compact = false): "FROM BERT · 6:45 AM"; beside Bert (compact = true): "6:45 AM".
     * A saved copy (no refresh for 30 minutes) says SAVED, in rust, as the widget always has.
     */
    private fun stamp(widthPx: Int, compact: Boolean): NotePiece? {
        val t = time ?: return null
        val shown = t.uppercase()
        val forms = when {
            delayed -> listOf("SAVED · $shown", "SAVED $shown", shown)
            compact -> listOf(shown)
            else -> listOf("FROM BERT · $shown", shown)
        }
        val paint = paint(fonts.label, color(if (delayed) R.color.note_stale else R.color.note_eyebrow), sp(10.5f)).apply { letterSpacing = 0.1f }
        val spoken = if (delayed) "Saved copy from $t; Bert’s feed has not updated since" else "From Bert, $t"
        for (form in forms) {
            val words = Words(paint, widthPx, Layout.Alignment.ALIGN_NORMAL, 1.0f)
            val layout = words.layout(form)
            if (layout.lineCount == 1 && breaksAtSpaces(form, layout)) return words.piece(form, layout, spoken, snug = true)
        }
        return null
    }

    /** The event chalked on a small slate in a wood frame, as wide as its words. */
    private fun slate(event: BERTEvent, widthPx: Int): NotePiece? {
        val frame = dp(3f).coerceAtLeast(2)
        val padH = dp(8f); val padV = dp(3f)
        for (form in slateForms(event, now)) for (s in (if (wide) 15 downTo 12 else 14 downTo 12)) {
            val paint = paint(fonts.hand, color(R.color.note_chalk), sp(s.toFloat()))
            val textW = ceil(paint.measureText(form)).toInt()
            val w = textW + 2 * (frame + padH)
            if (w > widthPx) continue
            val fm = paint.fontMetricsInt
            val h = (fm.bottom - fm.top) + 2 * (frame + padV)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = dp(2f).toFloat()
            fill.color = color(R.color.note_wood); canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, fill)
            // The frame catches the light along its top and is in shadow along its foot.
            fill.color = 0x26FFFFFF; canvas.drawRect(r, 0f, w - r, dp(1f).toFloat(), fill)
            fill.color = 0x33000000; canvas.drawRect(r, (h - dp(1f)).toFloat(), w - r, h.toFloat(), fill)
            fill.color = color(R.color.note_slate); canvas.drawRect(frame.toFloat(), frame.toFloat(), (w - frame).toFloat(), (h - frame).toFloat(), fill)
            // A faint wipe of old chalk across the board.
            fill.color = 0x0DFFFFFF
            canvas.drawRect(frame.toFloat(), frame + (h - 2 * frame) * 0.18f, (w - frame).toFloat(), frame + (h - 2 * frame) * 0.46f, fill)
            canvas.drawText(form, (frame + padH).toFloat(), (frame + padV - fm.top).toFloat(), paint)
            return NotePiece(bitmap, slateDescription(event, now), form)
        }
        return null
    }

    /** Pixel Bert at the largest whole number of device pixels per art pixel that fits the room. */
    private fun bert(roomW: Int, roomH: Int): Pair<NotePiece, Int?> {
        if (myBert != null) return myBertPiece(minOf(roomW, roomH).coerceAtLeast(dp(24f))) to null
        val k = maxOf(1, minOf(roomW / pose.crop.width(), roomH / pose.crop.height()))
        return sprite(k) to k
    }

    /** The mood still, cropped to [NotePose.crop] and enlarged by exactly [k] with no smoothing: every art pixel a k x k block. */
    fun sprite(k: Int): NotePiece {
        val options = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val art = BitmapFactory.decodeResource(context.resources, pose.drawable, options)
        val crop = pose.crop
        val src = IntArray(crop.width() * crop.height())
        art.getPixels(src, 0, crop.width(), crop.left, crop.top, crop.width(), crop.height())
        val w = crop.width() * k; val h = crop.height() * k
        val out = IntArray(w * h)
        for (y in 0 until h) { val row = (y / k) * crop.width(); for (x in 0 until w) out[y * w + x] = src[row + x / k] }
        return NotePiece(Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888), null)
    }

    /** The holder's Bert as a rounded square [side] px across, cropped to fill, kept as today's My Bert picture. */
    fun myBertPiece(side: Int): NotePiece {
        val picture = myBert!!.picture
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val scale = side.toFloat() / minOf(picture.width, picture.height)
        val shader = BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate((side - picture.width * scale) / 2f, (side - picture.height * scale) / 2f) })
        }
        val r = side * 0.16f
        Canvas(out).drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        return NotePiece(out, myBert.description)
    }

    private fun paint(typeface: Typeface, color: Int, sizePx: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface; this.color = color; if (sizePx > 0f) textSize = sizePx
    }

    private fun TextPaint.withSize(px: Float) = TextPaint(this).apply { textSize = px }

    private fun sizeSteps(from: Float, to: Float): List<Float> = generateSequence(from) { it - 1f }.takeWhile { it >= to - 0.01f }.toList()
}

/**
 * Words laid out in [paint] across [widthPx] (the bitmap's width), with room left at the sides and above and below for
 * the handwriting's swashes, so no glyph ever touches the bitmap's edge.
 */
private class Words(val paint: TextPaint, val widthPx: Int, val align: Layout.Alignment, val lineEm: Float) {
    val padX = ceil(paint.textSize * 0.08f).toInt() + 1
    val padY = ceil(paint.textSize * 0.06f).toInt() + 1
    private val inner = (widthPx - 2 * padX).coerceAtLeast(1)

    /** [text] laid out; with [maxLines], cut to that many lines with an ellipsis (only the last resort uses it). */
    fun layout(text: CharSequence, width: Int = inner, maxLines: Int = 0): StaticLayout {
        val fm = paint.fontMetrics
        val natural = fm.descent - fm.ascent
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).setIncludePad(true)
            .setLineSpacing(0f, (lineEm * paint.textSize / natural).coerceIn(0.85f, 1.6f))
            .apply { if (maxLines > 0) setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END) }.build()
    }

    fun heightOf(layout: StaticLayout) = layout.height + 2 * padY

    /** How many lines of [text] fit in [room] px. */
    fun linesWithin(text: String, room: Int): Int {
        val l = layout(text)
        return (0 until l.lineCount).count { l.getLineBottom(it) + 2 * padY + l.bottomPadding <= room }
    }

    /**
     * The longest start of [text] that ends at a word and, with an ellipsis, takes at most [lines] lines. A start that
     * ends a sentence stops there without one.
     */
    fun cutAtWord(text: String, lines: Int): Pair<String, StaticLayout>? {
        val ends = (1..text.length).filter { it == text.length || (!text[it - 1].isWhitespace() && text[it].isWhitespace()) }
        var lo = 0; var hi = ends.size - 1; var best: Pair<String, StaticLayout>? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val shown = shownUpTo(text, ends[mid])
            val l = layout(shown)
            if (l.lineCount <= lines && breaksAtSpaces(shown, l)) { best = shown to l; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    fun piece(shown: String, layout: StaticLayout, description: String, snug: Boolean = false): NotePiece {
        // Snug pieces are as wide as their longest line (a caption beside Bert); the rest fill the column.
        var l = layout; var w = widthPx
        if (snug) {
            val need = ceil((0 until layout.lineCount).maxOf { layout.getLineWidth(it) }).toInt() + 1
            if (need < inner) layout(shown, need).takeIf { it.lineCount == layout.lineCount }?.let { l = it; w = need + 2 * padX }
        }
        val bitmap = Bitmap.createBitmap(w, heightOf(l), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(padX.toFloat(), padY.toFloat())
        l.draw(canvas)
        return NotePiece(bitmap, description, shown, l.lineCount)
    }
}

/** Fewest words of Bert's post a note shows when it is cut (all of a shorter post is always shown whole or not cut). */
internal const val MIN_POST_WORDS = 4

private fun wordCount(shown: String) = shown.removeSuffix("…").split(Regex("\\s+")).count { it.isNotEmpty() }

private fun shownUpTo(text: String, end: Int): String {
    val start = text.substring(0, end).trimEnd()
    if (end >= text.length) return start
    if (start.last() in ".!?…") return start
    return start.trimEnd(',', ';', ':', '—', '–', '-', ' ') + "…"
}

/** True when every line of [layout] ends at a space or the end: no word was broken to fit. */
internal fun breaksAtSpaces(text: CharSequence, layout: Layout): Boolean = (0 until layout.lineCount - 1).all { i ->
    val end = layout.getLineEnd(i)
    end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end].isWhitespace()
}
