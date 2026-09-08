package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.ActivityState
import global.bert.widget.data.EventPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(state: ActivityState, now: Long, refreshing: Boolean, retry: () -> Unit, navigate: (BERTDestination) -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.bert_icon), null, Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("THE MAYOR IS IN")
                    Text("Woofmornin.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            when (state) {
                ActivityState.Loading -> {
                    Text("Checking in with Bert…", color = Cream)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentText, trackColor = Panel)
                }
                ActivityState.Unavailable -> {
                    Text("Bert’s update couldn’t load.", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("There’s still plenty to play, make and explore.", color = Muted, lineHeight = 21.sp)
                }
                is ActivityState.Available -> {
                    state.activity.mood?.let { StatusPill("BERT’S MOOD · ${it.uppercase()}", AccentText) }
                    state.activity.dispatch?.let { Text(it, color = Cream, fontSize = 19.sp, lineHeight = 28.sp, maxLines = 5, overflow = TextOverflow.Ellipsis) }
                    val age = ((now - state.activity.updatedAtEpochMillis).coerceAtLeast(0) / 60_000)
                    val ageText = when {
                        age < 1 -> "just now"
                        age < 60 -> "${age}m ago"
                        age < 1440 -> "${age / 60}h ago"
                        else -> "${age / 1440}d ago"
                    }
                    val delayed = state.updateDelayed || state.activity.isStaleAt(now)
                    Text("${if (delayed) "Saved update · " else ""}Source refreshed $ageText", color = if (delayed) Amber else Muted, fontSize = 12.sp)
                    Text("Latest from Berthalla", color = Muted, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = retry, enabled = !refreshing) { Text(if (refreshing) "Refreshing…" else "Refresh") }
                TextButton(onClick = { openBERTLink(context, BERTLink.DISPATCHES) }) { Text("Bert on X ↗") }
            }
        }
    }

    SectionLabel("A LITTLE BERT IN YOUR DAY")
    FeatureLink("Make something BERT", "Create a caption card, draw a scene or personalize your phone.", "✦") { navigate(BERTDestination.CREATE) }
    FeatureLink("Find your next adventure", "Games, music and the rest of Bert’s world.", "↗") { navigate(BERTDestination.EXPLORE) }

    if (state is ActivityState.Available) {
        state.activity.event?.let { event ->
            val phase = event.phaseAt(now, state.updateDelayed || state.activity.isStaleAt(now))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusPill(phase.label, if (phase == EventPhase.OPEN) Green else Muted)
                    Text(event.name, color = Cream, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    val endDate = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'").withZone(ZoneId.of("UTC"))
                        .format(Instant.ofEpochMilli(event.endsAtEpochMillis))
                    Text("Scheduled end $endDate", color = Muted, fontSize = 13.sp)
                    if (phase == EventPhase.UNCONFIRMED) Text("Check the game for the current tournament status.", color = Amber, fontSize = 13.sp)
                    Text("Flappy Bert · tap, flap, try again.", color = Cream, lineHeight = 21.sp)
                    OutlinedButton(onClick = { openBERTLink(context, BERTLink.FLAPPY) }) { Text("Play in Telegram ↗") }
                }
            }
        }
    }
    FeatureLink("Your BERT tools", "Market data and your private holdings record.", "◎") { navigate(BERTDestination.TOOLS) }
}

@Composable
internal fun FeatureLink(title: String, description: String, symbol: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cream.copy(alpha = 0.1f))) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(symbol, color = AccentText, fontSize = 23.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}
