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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
                Color(theme.accentArgb), System.currentTimeMillis())
        }
    }
}

@Composable
private fun DailyContent(activity: BERTActivity?, background: Color, panel: Color, text: Color, muted: Color, accent: Color, now: Long) {
    val size = LocalSize.current
    val compact = size.width < 200.dp
    val short = size.height < 130.dp
    val pad = if (short) 8f else 10f
    val icon = if (short) 22f else if (compact) 26f else 30f
    Column(GlanceModifier.fillMaxSize().background(ColorProvider(background)).padding(pad.dp).clickable(actionStartActivity<MainActivity>())) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.bert_token), "Bert", GlanceModifier.size(icon.dp), contentScale = ContentScale.Crop)
            if (!compact) {
                Spacer(GlanceModifier.width(8.dp))
                Text("BERT", style = TextStyle(color = ColorProvider(text), fontSize = if (short) 13.sp else 15.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.defaultWeight())
            activity?.mood?.let { mood ->
                Text("● ${mood.uppercase().take(12)}", style = TextStyle(color = ColorProvider(accent), fontSize = 9.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.height(if (short) 4.dp else 6.dp))
        val dispatch = activity?.dispatch
        val event = activity?.event
        val delayed = activity?.isStaleAt(now) ?: true
        val showTournament = event != null && event.phaseAt(now, delayed) == EventPhase.OPEN
        // Glance cannot measure, so fit whole lines from the known size: a partial last line would be cut mid-glyph.
        val bodySp = if (compact) 12f else 14f
        val fontScale = LocalContext.current.resources.configuration.fontScale
        val availableDp = size.height.value - pad * 2 - icon - (if (short) 4f else 6f) - (if (showTournament) 24f else 0f) - 2f
        val lines = (availableDp / (bodySp * 1.34f * fontScale)).toInt().coerceIn(1, 6)
        Text(
            dispatch ?: "Bert's next update will appear here.",
            style = TextStyle(color = ColorProvider(if (dispatch != null) text else muted), fontSize = bodySp.sp, fontWeight = FontWeight.Medium),
            maxLines = lines,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (showTournament && event != null) {
            val days = (event.endsAtEpochMillis - now) / 86_400_000
            val hours = (event.endsAtEpochMillis - now) % 86_400_000 / 3_600_000
            val left = if (days >= 1) "${days}d ${hours}h" else "${hours}h"
            val leader = event.standings.firstOrNull()
            Text(
                // Longest form that fits: pinned by BERTWidgetRenderTest at 120, 250 and 360dp.
                when {
                    size.width < 160.dp -> "$left LEFT"
                    size.width < 300.dp -> "${event.name.uppercase().take(18)} · $left LEFT"
                    else -> "${event.name.uppercase().take(22)} · $left LEFT${leader?.let { " · ${it.name.uppercase().take(14)} ${it.score}" }.orEmpty()}"
                },
                style = TextStyle(color = ColorProvider(Color(0xFFFFC857)), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().background(ColorProvider(panel)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
