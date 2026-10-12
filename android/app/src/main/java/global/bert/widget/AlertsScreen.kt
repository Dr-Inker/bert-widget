package global.bert.widget

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.alerts.BERTNotifier
import global.bert.widget.data.AlertSettings
import global.bert.widget.data.BERTAlertRules
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.data.QuoteState
import global.bert.widget.data.parsePositionNumber
import global.bert.widget.work.BERTRefreshWorker

@Composable
internal fun AlertsScreen(quote: QuoteState, store: BERTAlertStore? = null) {
    val context = LocalContext.current
    val alerts = remember { store ?: BERTAlertStore(context.applicationContext) }
    var settings by remember { mutableStateOf(alerts.settings()) }
    var permitted by remember { mutableStateOf(BERTNotifier.canPost(context)) }
    var above by rememberSaveable { mutableStateOf(settings.priceAbove?.let(::formatExactTokenAmount)?.replace(",", "").orEmpty()) }
    var below by rememberSaveable { mutableStateOf(settings.priceBelow?.let(::formatExactTokenAmount)?.replace(",", "").orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    // Set on save when the fresh price is already past a level, so the next-check alert is not a surprise.
    var crossed by remember { mutableStateOf(false to false) }

    fun apply(next: AlertSettings) {
        settings = next
        alerts.save(next)
        // Channels exist before the first alert, so Android's per-channel settings are reachable straight away.
        if (next.anyEnabled) BERTNotifier.ensureChannels(context.applicationContext)
        BERTRefreshWorker.syncSchedule(context.applicationContext)
        saved = true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted && BERTNotifier.canPost(context)
    }
    fun enable(next: AlertSettings) {
        apply(next)
        if (next.anyEnabled && !BERTNotifier.canPost(context) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else permitted = BERTNotifier.canPost(context)
    }

    Text("Alerts", color = Cream, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    Text("Off unless you switch them on. Checked about every 15 minutes while your phone allows background work, so they are not instant.",
        color = Muted, fontSize = 15.sp, lineHeight = 22.sp)

    if (settings.anyEnabled && !permitted) {
        Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notifications are off for BERT", color = Amber, fontWeight = FontWeight.Bold)
                Text("Alerts can’t reach you until Android allows them.", color = Cream, fontSize = 14.sp)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open notification settings") }
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AlertSwitch("Tournament ending", "Once, in the last 24 hours of a Flappy Bert tournament.", settings.tournament) { enable(settings.copy(tournament = it)) }
            AlertSwitch("Berthalla’s updates", "When Berthalla posts a new update in Bert’s voice.", settings.dispatch) { enable(settings.copy(dispatch = it)) }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("PRICE ALERTS")
            (quote as? QuoteState.Available)?.let { Text("BERT is ${formatPrice(it.quote.priceUsd)} now.", color = Cream, fontSize = 15.sp) }
            Text("Each alert fires when BERT crosses your price, then re-arms once the price comes back 3% (or when you change the level).", color = Muted, fontSize = 13.sp)
            val aboveValue = parsePositionNumber(above)?.takeIf { it > 0 }
            val belowValue = parsePositionNumber(below)?.takeIf { it > 0 }
            OutlinedTextField(above, { above = it; saved = false; crossed = false to crossed.second }, Modifier.fillMaxWidth(), label = { Text("Alert above (USD)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = above.isNotBlank() && aboveValue == null, supportingText = {
                    if (above.isNotBlank() && aboveValue == null) Text("Enter a price like 0.02")
                    else if (crossed.first) Text("BERT is already above this — it will alert on the next check.", color = Amber)
                })
            OutlinedTextField(below, { below = it; saved = false; crossed = crossed.first to false }, Modifier.fillMaxWidth(), label = { Text("Alert below (USD)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = below.isNotBlank() && belowValue == null, supportingText = {
                    if (below.isNotBlank() && belowValue == null) Text("Enter a price like 0.012")
                    else if (crossed.second) Text("BERT is already below this — it will alert on the next check.", color = Amber)
                })
            val valid = (above.isBlank() || aboveValue != null) && (below.isBlank() || belowValue != null)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = valid, onClick = {
                    val next = settings.copy(priceAbove = aboveValue, priceBelow = belowValue)
                    enable(next)
                    // After save, so a level changed just now counts as re-armed while an unchanged, already-announced one does not.
                    crossed = BERTAlertRules.alreadyCrossed(next, alerts.memory(), (quote as? QuoteState.Available)?.quote, System.currentTimeMillis())
                }) { Text("Save price alerts") }
                if (saved) Text("Saved", color = Green, fontSize = 14.sp)
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AlertSwitch("Big move", "When BERT is up or down this much over 24 hours. At most one notice every 6 hours, and the next waits until the move has faded.",
                settings.bigMovePct != null) { enable(settings.copy(bigMovePct = if (it) BERTAlertRules.BIG_MOVE_CHOICES.first() else null)) }
            if (settings.bigMovePct != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BERTAlertRules.BIG_MOVE_CHOICES.forEach { pct ->
                        FilterChip(selected = settings.bigMovePct == pct, onClick = { enable(settings.copy(bigMovePct = pct)) }, label = { Text("$pct%") })
                    }
                }
            }
        }
    }
    Text("Alerts never include your holdings. Price checks use the same public market data as the rest of the app.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun AlertSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        val haptic = LocalHapticFeedback.current
        Switch(checked = checked, onCheckedChange = { on ->
            haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onChange(on)
        }, modifier = Modifier.semantics { contentDescription = title })
    }
}
