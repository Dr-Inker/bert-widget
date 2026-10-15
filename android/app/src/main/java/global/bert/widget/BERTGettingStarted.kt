package global.bert.widget

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.work.BERTRefreshWorker
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ONBOARDING_PREFS = "bert_onboarding"

/** Local-only flags for the getting-started card. Nothing here leaves the phone. */
internal object BERTOnboarding {
    private const val PLAYED_GAME = "played_lost_trail"
    private const val HIDDEN = "getting_started_hidden"

    fun markPlayedGame(context: Context) =
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).edit().putBoolean(PLAYED_GAME, true).apply()

    fun hide(context: Context) =
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE).edit().putBoolean(HIDDEN, true).apply()

    fun progress(context: Context): GettingStarted {
        val prefs = context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        return GettingStarted(
            hidden = prefs.getBoolean(HIDDEN, false),
            widget = BERTRefreshWorker.hasPlacedWidgets(context),
            game = prefs.getBoolean(PLAYED_GAME, false),
            card = CaptionLibrary(context).hasAny(),
            alert = BERTAlertStore(context).settings().anyEnabled,
        )
    }
}

internal data class GettingStarted(val hidden: Boolean, val widget: Boolean, val game: Boolean, val card: Boolean, val alert: Boolean) {
    val done: Int get() = listOf(widget, game, card, alert).count { it }
    val visible: Boolean get() = !hidden && done < 4
}

/** Where a getting-started step sends the person. */
internal enum class GettingStartedStep { WIDGET, GAME, CARD, ALERT }

@Composable
internal fun GettingStartedCard(progress: GettingStarted, open: (GettingStartedStep) -> Unit, hide: () -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("GET TO KNOW BERT · ${progress.done} OF 4")
            Text("Four things worth a try", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            StepRow("Put Bert on your home screen", progress.widget) { open(GettingStartedStep.WIDGET) }
            StepRow("Play a world of The Lost Trail", progress.game) { open(GettingStartedStep.GAME) }
            StepRow("Make and save a caption card", progress.card) { open(GettingStartedStep.CARD) }
            StepRow("Switch on an alert", progress.alert) { open(GettingStartedStep.ALERT) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openBERTLink(context, BERTLink.FEEDBACK) }) {
                    Text("Tell us what’s rough")
                    Spacer(Modifier.width(6.dp))
                    BERTIcon(BERTSymbol.ARROW, AccentText, Modifier.size(14.dp))
                }
                TextButton(onClick = hide) { Text("Hide", color = Muted) }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (done) Navy else PanelStrong, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.semantics { contentDescription = if (done) "$label, done" else label }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepMark(done)
            Text(label, color = if (done) Muted else Cream, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
    }
}

/** Drawn, not typed: a ticked green disc when done, an open orange ring when not. */
@Composable
private fun StepMark(done: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2
        if (done) {
            drawCircle(Green, r)
            val u = size.width / 20
            drawPath(Path().apply { moveTo(5.5f * u, 10.5f * u); lineTo(8.5f * u, 13.5f * u); lineTo(14.5f * u, 7f * u) }, Navy,
                style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else drawCircle(AccentText, r - 1.dp.toPx(), style = Stroke(1.8.dp.toPx()))
    }
}

/** The last crash or freeze Android recorded for BERT that the person has not yet seen. */
internal data class ExitReport(val timestamp: Long, val kind: String, val description: String?)

internal object BERTExitReports {
    private const val SEEN = "last_seen_exit"

    /**
     * Reads Android's own record of how BERT last stopped (API 30+). The first call only sets a baseline, so
     * crashes from before this feature existed are never raised. Nothing is sent unless the person chooses to.
     */
    fun pending(context: Context): ExitReport? {
        if (Build.VERSION.SDK_INT < 30) return null
        val prefs = context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exits = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 8) }.getOrDefault(emptyList())
        val seen = prefs.getLong(SEEN, -1L)
        if (seen < 0) {
            prefs.edit().putLong(SEEN, exits.maxOfOrNull { it.timestamp } ?: 0L).apply()
            return null
        }
        val latest = exits.filter { it.timestamp > seen && kindOf(it.reason) != null }.maxByOrNull { it.timestamp } ?: return null
        return ExitReport(latest.timestamp, kindOf(latest.reason)!!, latest.description?.take(300))
    }

    fun markSeen(context: Context, report: ExitReport) {
        val prefs = context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
        if (report.timestamp > prefs.getLong(SEEN, -1L)) prefs.edit().putLong(SEEN, report.timestamp).apply()
    }

    private fun kindOf(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash"
        ApplicationExitInfo.REASON_ANR -> "freeze (ANR)"
        else -> null
    }

    /** The exact text the person sees in the issue form before deciding to post it. */
    fun reportText(report: ExitReport): String = buildString {
        appendLine("BERT ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) stopped: ${report.kind}")
        appendLine("When: ${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(report.timestamp).atOffset(ZoneOffset.UTC))}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        report.description?.let { appendLine("Android’s note: $it") }
        appendLine()
        append("What were you doing just before? ")
    }

    fun issueUri(report: ExitReport): Uri = Uri.parse(BERTLink.FEEDBACK.url + "/new").buildUpon()
        .appendQueryParameter("title", "BERT ${BuildConfig.VERSION_NAME} ${report.kind}")
        .appendQueryParameter("body", reportText(report))
        .build()
}

@Composable
internal fun rememberExitReport(): MutableState<ExitReport?> {
    val context = LocalContext.current
    val report = remember { mutableStateOf<ExitReport?>(null) }
    LaunchedEffect(Unit) { report.value = withContext(Dispatchers.IO) { BERTExitReports.pending(context.applicationContext) } }
    return report
}

@Composable
internal fun ExitReportCard(report: ExitReport, done: () -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTag("SORRY ABOUT THAT", Amber)
            Text("BERT closed unexpectedly last time", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("You can send a short report: app and Android version, phone model and Android’s note. It opens on GitHub, where you review it before posting.",
                color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, BERTExitReports.issueUri(report)))
                        BERTExitReports.markSeen(context, report)
                        done()
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Install a browser to send the report.", Toast.LENGTH_LONG).show()
                    }
                }, colors = solidActionButtonColors()) { Text("Review report") }
                TextButton(onClick = { BERTExitReports.markSeen(context, report); done() }) { Text("Not now", color = Muted) }
            }
        }
    }
}
