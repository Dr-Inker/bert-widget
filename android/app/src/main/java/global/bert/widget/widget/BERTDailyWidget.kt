package global.bert.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import global.bert.widget.BERTEntry
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import global.bert.widget.MainActivity
import global.bert.widget.R
import global.bert.widget.formatObservedAt
import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.data.MyBertRepository
import global.bert.widget.data.MyBertStore
import global.bert.widget.data.EventPhase
import global.bert.widget.theme.BERTThemeStore

/** Bert himself on the home screen: mood, latest dispatch and the tournament countdown. No market data. */
class BERTDailyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val revision = currentState(REVISION) ?: 0
            val activity = remember(revision) { BERTActivityRepository(context).load() }
            val theme = remember(revision) { BERTThemeStore(context).loadPalette() }
            val myBert = remember(revision) { WidgetBert.load(context) }
            CompositionLocalProvider(LocalMoodPill provides (Color(theme.highlightArgb) to Color(theme.onHighlightArgb))) {
                DailyContent(activity, Color(theme.backgroundArgb), Color(theme.panelArgb), Color(theme.textArgb), Color(theme.mutedArgb),
                    System.currentTimeMillis(), myBert)
            }
        }
    }
}

/** The mood pill's fill and text: the palette's highlight pair (orange on ink by default, lilac on ink for BERT Violet). */
internal val LocalMoodPill = compositionLocalOf { Color(0xFFFF9433) to Color(0xFF1F0E02) }
private val Gold = Color(0xFFFFC857)

/** Smallest text on any widget: freshness, status, mood and event lines never go below 10sp. */
internal const val FRESHNESS_SP = 10f

/** Width of the mood pill for [mood] (already upper-case): bold caps at 10sp run about 0.7em a glyph, plus 7dp padding a side. */
internal fun moodPillWidth(mood: String, fontScale: Float) = mood.length * FRESHNESS_SP * 0.7f * fontScale + 14f

/**
 * The holder's own Bert (My Bert) in place of the photo: the picture kept on the phone, at most [MAX_PX] a side,
 * so it works offline and stays well inside the launcher's bitmap budget.
 */
internal class WidgetBert(val number: Int, val picture: Bitmap) {
    val description get() = "Your Bert #$number"

    /** A strip at [aspect] (width / height) around his face, which sits a little above the middle of the art. */
    fun banner(aspect: Float): Bitmap {
        var w = picture.width.toFloat(); var h = w / aspect
        if (h > picture.height) { h = picture.height.toFloat(); w = h * aspect }
        val x = ((picture.width - w) / 2f).toInt()
        val y = (picture.height * 0.42f - h / 2f).coerceIn(0f, picture.height - h).toInt()
        return Bitmap.createBitmap(picture, x, y, w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    }

    companion object {
        private const val MAX_PX = 384

        fun load(context: Context): WidgetBert? {
            val chosen = MyBertStore(context).chosen() ?: return null
            val file = MyBertStore(context).imageFile
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            val picture = MyBertRepository.decodeBounded(bytes, MAX_PX) ?: return null
            return WidgetBert(chosen.bert.number, picture)
        }
    }
}

@Composable
private fun DailyContent(activity: BERTActivity?, background: Color, panel: Color, text: Color, muted: Color, now: Long, myBert: WidgetBert? = null) {
    val size = LocalSize.current
    val w = size.width.value; val h = size.height.value
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val pad = if (h < 130f) 8f else 12f
    // From a 2x2-ish width up, Bert gets a full-height portrait; a tall narrow widget gets a banner of his face across
    // the top; only the smallest sizes fall back to a round avatar in the header.
    val wide = w >= 200f
    val banner = !wide && h >= 150f
    // A shorter banner on the 150dp-tall sizes leaves room for Bert's words, the as-of time and the tournament line together.
    val bannerH = if (banner) minOf((h - pad * 2) * (if (h < 170f) 0.30f else 0.38f), (w - pad * 2) / 2f) else 0f
    // The photo is a tall crop; a holder's Bert is square art, shown whole rather than cut down to a strip.
    val portraitW = if (!wide) 0f else if (myBert != null) minOf(h - pad * 2, w * 0.36f) else minOf((h - pad * 2) * 0.78f, w * 0.34f)
    val portraitH = if (!wide) 0f else if (myBert != null) portraitW else minOf(h - pad * 2, portraitW / 0.6f)
    val gap = if (wide) 12f else 0f
    val columnW = w - pad * 2 - portraitW - gap
    val avatar = if (h < 130f) 22f else 28f
    val markH = if (wide) 14f else 12f
    val nameW = markH * (640f / 186f)

    val event = activity?.event
    val delayed = activity?.isStaleAt(now) ?: true
    val showTournament = event != null && event.phaseAt(now, delayed) == EventPhase.OPEN
    // The mood is never cut to fit: the pill is drawn whole or not at all. Over the banner it always fits; in the header
    // row it is dropped when the avatar and the whole word do not fit together, and the wordmark gives way before it.
    val mood = activity?.mood?.uppercase()
    val pillW = mood?.let { moodPillWidth(it, fontScale) } ?: 0f
    val avatarW = if (wide) 0f else avatar
    val showPill = mood != null && (if (banner) pillW + 12f <= columnW else pillW + avatarW + 6f <= columnW)
    val showName = !banner && (wide || w >= 160f) && avatarW + (if (avatarW > 0f) 8f else 0f) + nameW + 8f + (if (showPill) pillW else 0f) <= columnW
    val pillH = if (showPill) FRESHNESS_SP * 1.34f * fontScale + 4f else 0f
    val headerH = if (banner) bannerH + 2f else maxOf(avatarW, (if (wide) 15f else 13f) * 1.34f * fontScale, pillH)
    val footerH = if (showTournament) FRESHNESS_SP * 1.34f * fontScale + 8f + 6f else 0f
    // When this copy of Bert's feed is from: the widget is not re-rendered continuously, so the time is absolute.
    // Shown only where Bert's words keep at least two lines; a narrow column drops the "UPDATED" word.
    val bodyRoom = h - pad * 2 - headerH - 4f - footerH - 4f
    val stampLineH = FRESHNESS_SP * 1.34f * fontScale + 3f
    val stamp = activity?.takeIf { bodyRoom - stampLineH >= 2 * 12f * 1.34f * fontScale }?.let {
        val time = formatObservedAt(it.updatedAtEpochMillis, now, use24Hour = DateFormat.is24HourFormat(LocalContext.current), dateOnlyWhenOld = true)
            .removePrefix("As of ")
        // The longest label the column holds: the word UPDATED where it fits (bold caps run about 0.64em a glyph), else the time.
        val labelled = (if (delayed) "SAVED · $time" else "UPDATED $time").uppercase()
        if (labelled.length * FRESHNESS_SP * 0.64f * fontScale <= columnW) labelled else time.uppercase()
    }
    val stampH = if (stamp != null) stampLineH else 0f
    val dispatch = activity?.dispatch
    val body = dispatch ?: "Bert’s next update will appear here."
    val (bodySp, lines) = fitDispatch(body, columnW, bodyRoom - stampH, fontScale)

    Box(GlanceModifier.fillMaxSize().background(ColorProvider(background)).clickable(actionStartActivity<MainActivity>(entryParameters(BERTEntry.HOME)))) {
        Image(ImageProvider(R.drawable.bert_widget_glow), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Row(GlanceModifier.fillMaxSize().padding(pad.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                Image(myBert?.let { ImageProvider(it.picture) } ?: ImageProvider(R.drawable.bert_widget_portrait), myBert?.description ?: "Bert",
                    GlanceModifier.width(portraitW.dp).height(portraitH.dp).cornerRadius(14.dp), contentScale = ContentScale.Crop)
                Spacer(GlanceModifier.width(gap.dp))
            }
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                if (banner) Box(GlanceModifier.fillMaxWidth().height(bannerH.dp), contentAlignment = Alignment.TopEnd) {
                    val strip = remember(myBert, w, bannerH) { myBert?.banner((w - pad * 2) / bannerH) }
                    Image(strip?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.bert_widget_banner), myBert?.description ?: "Bert",
                        GlanceModifier.fillMaxSize().cornerRadius(12.dp), contentScale = ContentScale.Crop)
                    if (showPill && mood != null) MoodPill(mood, GlanceModifier.padding(6.dp))
                } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) {
                        Image(myBert?.let { ImageProvider(it.picture) } ?: ImageProvider(R.drawable.bert_token), myBert?.description ?: "Bert",
                            GlanceModifier.size(avatar.dp).cornerRadius((avatar / 2).dp), contentScale = ContentScale.Crop)
                    }
                    if (showName) {
                        if (!wide) Spacer(GlanceModifier.width(8.dp))
                        Image(ImageProvider(R.drawable.bert_wordmark), "BERT", GlanceModifier.height(markH.dp).width(nameW.dp),
                            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ColorProvider(text)))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    if (showPill && mood != null) MoodPill(mood)
                }
                Spacer(GlanceModifier.height(if (banner) 6.dp else 4.dp))
                // Glance cannot measure, so fitDispatch picks whole lines at the largest size that holds the post.
                Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(body, maxLines = lines, modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(color = ColorProvider(if (dispatch != null) text else muted), fontSize = bodySp.sp, fontWeight = FontWeight.Medium))
                }
                if (stamp != null) {
                    Spacer(GlanceModifier.height(3.dp))
                    Text(stamp, maxLines = 1, style = TextStyle(color = ColorProvider(if (delayed) Gold else muted), fontSize = FRESHNESS_SP.sp, fontWeight = FontWeight.Bold))
                }
                if (showTournament && event != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    val days = (event.endsAtEpochMillis - now) / 86_400_000
                    val hours = (event.endsAtEpochMillis - now) % 86_400_000 / 3_600_000
                    val left = if (days >= 1) "${days}d ${hours}h LEFT" else if (hours >= 1) "${hours}h LEFT" else "<1h LEFT"
                    val leader = event.standings.firstOrNull()
                    val forms = listOfNotNull(
                        leader?.let { "${event.name.uppercase().take(22)} · $left · ${it.name.uppercase().take(14)} ${it.score}" },
                        "${event.name.uppercase().take(18)} · $left",
                        "FLAPPY · $left", // a bare countdown does not say what is ending
                        left,
                    )
                    // Bold caps at 10sp run about 0.64em a glyph; take the longest form the column holds.
                    val label = forms.firstOrNull { it.length * FRESHNESS_SP * 0.64f * fontScale + 16f <= columnW } ?: forms.last()
                    Text(label, maxLines = 1,
                        style = TextStyle(color = ColorProvider(Gold), fontSize = FRESHNESS_SP.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(panel)).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MoodPill(mood: String, outer: GlanceModifier = GlanceModifier) {
    Box(outer) {
        val (fill, ink) = LocalMoodPill.current
        Text(mood, maxLines = 1, style = TextStyle(color = ColorProvider(ink), fontSize = FRESHNESS_SP.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.background(ColorProvider(fill)).cornerRadius(9.dp).padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

/**
 * Largest font (12–22sp) and line count at which [text] fits a [widthDp]×[heightDp] box, by greedy word wrap with an
 * average glyph of 0.53em. Falls back to 12sp with as many whole lines as fit; the last one ellipsizes.
 */
internal fun fitDispatch(text: String, widthDp: Float, heightDp: Float, fontScale: Float): Pair<Float, Int> {
    for (sp in 22 downTo 12) {
        val em = sp * fontScale
        val maxLines = (heightDp / (em * 1.34f)).toInt()
        if (maxLines >= 1 && wrappedLines(text, (widthDp / (em * 0.53f)).toInt()) <= maxLines) return sp.toFloat() to maxLines
    }
    return 12f to (heightDp / (12f * fontScale * 1.34f)).toInt().coerceAtLeast(1)
}

private fun wrappedLines(text: String, perLine: Int): Int {
    if (perLine < 1) return Int.MAX_VALUE
    var lines = 1; var used = 0
    for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val need = if (used == 0) word.length else used + 1 + word.length
        if (need <= perLine) used = need
        else { lines += 1 + (word.length - 1) / perLine; used = (word.length - 1) % perLine + 1 }
    }
    return lines
}
