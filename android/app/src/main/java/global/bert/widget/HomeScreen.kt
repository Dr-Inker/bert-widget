package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
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
import global.bert.widget.data.BERTEvent
import global.bert.widget.data.BERTStanding
import global.bert.widget.data.EventPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeScreen(state: ActivityState, now: Long, refreshing: Boolean, retry: () -> Unit, navigate: (BERTDestination) -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (LocalDensity.current.fontScale > 1.3f) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.bert_icon), null, Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)))
                    Column(Modifier.weight(1f)) { SectionLabel("THE MAYOR IS IN") }
                }
                Text("Hey, pack.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.bert_icon), null, Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("THE MAYOR IS IN")
                        Text("Hey, pack.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
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
                    state.activity.dispatch?.let { dispatch ->
                        var expanded by rememberSaveable(dispatch) { mutableStateOf(false) }
                        var overflowed by remember(dispatch) { mutableStateOf(false) }
                        Text(dispatch, color = Cream, fontSize = 19.sp, lineHeight = 28.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!expanded) overflowed = it.hasVisualOverflow })
                        if (overflowed || expanded) TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Read less" else "Read full update")
                        }
                    }
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = retry, enabled = !refreshing) { Text(if (refreshing) "Refreshing…" else "Refresh") }
                TextButton(onClick = { openBERTLink(context, BERTLink.DISPATCHES) }) { Text("Bert on X ↗") }
            }
        }
    }

    if (state is ActivityState.Available) state.activity.event?.let { event ->
        TournamentCard(event, now, delayed = state.updateDelayed || state.activity.isStaleAt(now), updatedAt = state.activity.updatedAtEpochMillis)
    }

    SectionLabel("A LITTLE BERT IN YOUR DAY")
    FeatureLink("Make something BERT", "Create a caption card, draw a scene or personalize your phone.", BERTSymbol.CREATE) { navigate(BERTDestination.CREATE) }
    FeatureLink("Find your next adventure", "Games, music and the rest of Bert’s world.", BERTSymbol.EXPLORE) { navigate(BERTDestination.EXPLORE) }

    FeatureLink("Your BERT tools", "Market data and your private holdings record.", BERTSymbol.TOOLS) { navigate(BERTDestination.TOOLS) }
}

@Composable
internal fun FeatureLink(title: String, description: String, symbol: BERTSymbol, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cream.copy(alpha = 0.1f))) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BERTIcon(symbol)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** "Ends in 2 days 7h", "Ends in 3h 12m", "Ends in 4m" — computed on the device clock. */
internal fun formatTimeLeft(endsAt: Long, now: Long): String {
    val minutes = ((endsAt - now) / 60_000).coerceAtLeast(0)
    val days = minutes / 1_440
    val hours = minutes % 1_440 / 60
    return when {
        days >= 2 -> "Ends in $days days ${hours}h"
        days == 1L -> "Ends in 1 day ${hours}h"
        minutes >= 60 -> "Ends in ${minutes / 60}h ${minutes % 60}m"
        else -> "Ends in ${minutes}m"
    }
}

@Composable
private fun TournamentCard(event: BERTEvent, now: Long, delayed: Boolean, updatedAt: Long) {
    val context = LocalContext.current
    val phase = event.phaseAt(now, delayed)
    val ended = phase == EventPhase.ENDED
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (phase == EventPhase.OPEN) Green.copy(alpha = 0.35f) else Cream.copy(alpha = 0.1f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(phase.label, if (phase == EventPhase.OPEN) Green else Muted)
                if (!ended && phase != EventPhase.UNCONFIRMED) StatusPill(formatTimeLeft(event.endsAtEpochMillis, now).uppercase(), AccentText)
            }
            Text(event.name, color = Cream, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            val sponsor = event.sponsor?.replace(Regex("dr\\.?\\s*inker\\s*labs", RegexOption.IGNORE_CASE), "DrInkerLABS")
            Text(listOfNotNull("Flappy Bert tournament", sponsor?.let { "sponsored by $it" }).joinToString(" · "), color = Muted, fontSize = 13.sp)
            event.pool?.let { Text("${'$'}$it prize pool · top 5 paid", color = Amber, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            if (event.standings.isNotEmpty()) {
                SectionLabel(if (ended) "FINAL STANDINGS" else "LEADERBOARD")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.standings.forEach { row -> StandingRow(row, leader = row.rank == 1) }
                }
                if (delayed) {
                    val minutes = ((now - updatedAt) / 60_000).coerceAtLeast(0)
                    Text("Standings as of ${if (minutes < 60) "${minutes}m" else "${minutes / 60}h"} ago", color = Amber, fontSize = 12.sp)
                }
            }
            if (phase == EventPhase.UNCONFIRMED) Text("Check the game for the current tournament status.", color = Amber, fontSize = 13.sp)
            val endDate = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'").withZone(ZoneId.of("UTC")).format(Instant.ofEpochMilli(event.endsAtEpochMillis))
            Text("${if (ended) "Ended" else "Scheduled end"} $endDate", color = Muted, fontSize = 12.sp)
            if (!ended) Button(onClick = { openBERTLink(context, BERTLink.FLAPPY) }) { Text("Play in Telegram ↗") }
        }
    }
}

@Composable
private fun StandingRow(row: BERTStanding, leader: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (leader) Amber.copy(alpha = 0.12f) else PanelStrong)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${row.rank}", color = if (leader) Amber else Muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(18.dp))
        Text(row.name, color = Cream, fontSize = 15.sp, fontWeight = if (leader) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${row.score}", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        row.prizeUsd?.let { Text("${'$'}$it", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 40.dp)) }
    }
}

