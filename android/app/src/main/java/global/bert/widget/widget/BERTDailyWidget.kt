package global.bert.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
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
import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.data.EventPhase
import global.bert.widget.theme.BERTThemeStore

/** Bert himself on the home screen: mood, latest dispatch and the tournament countdown. No market data. */
class BERTDailyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val revision = currentState(REVISION) ?: 0
            val activity = remember(revision) { BERTActivityRepository(context).load() }
            val theme = remember(revision) { BERTThemeStore(context).load() }
            DailyContent(activity, Color(theme.backgroundArgb), Color(theme.panelArgb), Color(theme.textArgb), Color(theme.mutedArgb),
                System.currentTimeMillis())
        }
    }
}

private val Orange = Color(0xFFFF9433)
private val OnOrange = Color(0xFF1F0E02)
private val Gold = Color(0xFFFFC857)

@Composable
private fun DailyContent(activity: BERTActivity?, background: Color, panel: Color, text: Color, muted: Color, now: Long) {
    val size = LocalSize.current
    val w = size.width.value; val h = size.height.value
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val pad = if (h < 130f) 8f else 12f
    // From a 2x2-ish width up, Bert gets a full-height portrait; a tall narrow widget gets a banner of his face across
    // the top; only the smallest sizes fall back to a round avatar in the header.
    val wide = w >= 200f
    val banner = !wide && h >= 150f
    val bannerH = if (banner) minOf((h - pad * 2) * 0.38f, (w - pad * 2) / 2f) else 0f
    val portraitW = if (wide) minOf((h - pad * 2) * 0.78f, w * 0.34f) else 0f
    val portraitH = if (wide) minOf(h - pad * 2, portraitW / 0.6f) else 0f
    val gap = if (wide) 12f else 0f
    val columnW = w - pad * 2 - portraitW - gap
    val avatar = if (h < 130f) 22f else 28f
    val showName = wide || w >= 160f

    val event = activity?.event
    val delayed = activity?.isStaleAt(now) ?: true
    val showTournament = event != null && event.phaseAt(now, delayed) == EventPhase.OPEN
    val headerH = if (banner) bannerH + 2f else maxOf(if (wide) 0f else avatar, (if (wide) 15f else 13f) * 1.34f * fontScale)
    val footerH = if (showTournament) 9f * 1.34f * fontScale + 8f + 6f else 0f
    val dispatch = activity?.dispatch
    val body = dispatch ?: "Bert's next update will appear here."
    val (bodySp, lines) = fitDispatch(body, columnW, h - pad * 2 - headerH - 4f - footerH - 4f, fontScale)

    Box(GlanceModifier.fillMaxSize().background(ColorProvider(background)).clickable(actionStartActivity<MainActivity>())) {
        Image(ImageProvider(R.drawable.bert_widget_glow), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Row(GlanceModifier.fillMaxSize().padding(pad.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                Image(ImageProvider(R.drawable.bert_widget_portrait), "Bert",
                    GlanceModifier.width(portraitW.dp).height(portraitH.dp).cornerRadius(14.dp), contentScale = ContentScale.Crop)
                Spacer(GlanceModifier.width(gap.dp))
            }
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                if (banner) Box(GlanceModifier.fillMaxWidth().height(bannerH.dp), contentAlignment = Alignment.TopEnd) {
                    Image(ImageProvider(R.drawable.bert_widget_banner), "Bert", GlanceModifier.fillMaxSize().cornerRadius(12.dp), contentScale = ContentScale.Crop)
                    activity?.mood?.let { MoodPill(it.uppercase().take(9), GlanceModifier.padding(6.dp)) }
                } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) {
                        Image(ImageProvider(R.drawable.bert_token), "Bert", GlanceModifier.size(avatar.dp).cornerRadius((avatar / 2).dp), contentScale = ContentScale.Crop)
                        if (showName) Spacer(GlanceModifier.width(8.dp))
                    }
                    if (showName) Text("BERT", style = TextStyle(color = ColorProvider(text), fontSize = if (wide) 15.sp else 13.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    activity?.mood?.let { MoodPill(it.uppercase().take(if (wide) 12 else 9)) }
                }
                Spacer(GlanceModifier.height(if (banner) 6.dp else 4.dp))
                // Glance cannot measure, so fitDispatch picks whole lines at the largest size that holds the post.
                Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(body, maxLines = lines, modifier = GlanceModifier.fillMaxWidth(),
                        style = TextStyle(color = ColorProvider(if (dispatch != null) text else muted), fontSize = bodySp.sp, fontWeight = FontWeight.Medium))
                }
                if (showTournament && event != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    val days = (event.endsAtEpochMillis - now) / 86_400_000
                    val hours = (event.endsAtEpochMillis - now) % 86_400_000 / 3_600_000
                    val left = if (days >= 1) "${days}d ${hours}h LEFT" else "${hours}h LEFT"
                    val leader = event.standings.firstOrNull()
                    val forms = listOfNotNull(
                        leader?.let { "${event.name.uppercase().take(22)} · $left · ${it.name.uppercase().take(14)} ${it.score}" },
                        "${event.name.uppercase().take(18)} · $left",
                        left,
                    )
                    // Bold caps at 9sp run about 0.64em a glyph; take the longest form the column holds.
                    val label = forms.firstOrNull { it.length * 9f * 0.64f * fontScale + 16f <= columnW } ?: forms.last()
                    Text(label, maxLines = 1,
                        style = TextStyle(color = ColorProvider(Gold), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(panel)).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MoodPill(mood: String, outer: GlanceModifier = GlanceModifier) {
    Box(outer) {
        Text(mood, maxLines = 1, style = TextStyle(color = ColorProvider(OnOrange), fontSize = 9.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.background(ColorProvider(Orange)).cornerRadius(9.dp).padding(horizontal = 7.dp, vertical = 2.dp))
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
