package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.ActivityState

/**
 * Play (0.13, replacing Explore): the games, then music. The Lost Trail is the same picture and progress as on Home; a
 * live Flappy Bert tournament sits under its arcade. The pack's links are in Home's "Around the pack" sheet.
 */
@Composable
internal fun PlayScreen(activityState: ActivityState, now: Long) {
    val context = LocalContext.current
    Text("Off the lead.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    Text("A trail that works offline, an arcade with tournaments, and music for the walk home.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
    LostTrailContinueCard(rememberLostTrailProgress(now)) { openLostTrail(context) }
    FeaturedExperience("Flappy Bert", "One more flap?", "Arcade runs and community tournaments.", BERTSymbol.PLAY, Green, BERTLink.FLAPPY)
    if (activityState is ActivityState.Available) activityState.activity.event?.let { event ->
        TournamentCard(event, now, delayed = activityState.updateDelayed || activityState.activity.isStaleAt(now),
            updatedAt = activityState.activity.updatedAtEpochMillis)
    }
    FeaturedExperience("Bert Music", "Soundtrack your day.", "Music from the BERT community.", BERTSymbol.MUSIC, Color(0xFFD2B5FF), BERTLink.MUSIC)
}

/** The foot of Home: one quiet way into the rest of the pack (official BERT, Berthalla, the community, Woofhub). */
@Composable
internal fun AroundThePackLink() {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = true }.heightIn(min = 48.dp).padding(vertical = 8.dp)
        .testTag("around-the-pack"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BERTIcon(BERTSymbol.PACK)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Around the pack", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Official BERT, Berthalla and the community", color = Muted, fontSize = 13.sp)
        }
    }
    if (open) AroundThePackSheet { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AroundThePackSheet(close: () -> Unit) {
    // Opened all the way, like the noticeboard, so the last link stops above the navigation bar.
    ModalBottomSheet(onDismissRequest = close, containerColor = Navy, contentColor = Cream,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
            .testTag("pack-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("around the pack", fontFamily = BertHand, fontSize = 26.sp, color = Cream, modifier = Modifier.semantics { heading() })
            OfficialBertCard()
            ExperienceLink("Berthalla on X", "Daily posts in Bert’s voice from Berthalla, a fan project.", BERTSymbol.STORY, BERTLink.DISPATCHES)
            ExperienceLink("Berthalla", "Games, music and the Bert Studio on berthalla.io.", BERTSymbol.PLAY, BERTLink.BERTHALLA)
            ExperienceLink("Meet the community", "Catch up with the pack.", BERTSymbol.PACK, BERTLink.COMMUNITY)
            ExperienceLink("Woofhub", "Dog adoption, care and profiles.", BERTSymbol.HEART, BERTLink.WOOFHUB)
            TextButton(onClick = close, modifier = Modifier.testTag("pack-close")) { Text("Close") }
        }
    }
}

@Composable
private fun FeaturedExperience(title: String, invitation: String, description: String, symbol: BERTSymbol, accent: Color, link: BERTLink) {
    val context = LocalContext.current
    Card(onClick = { openBERTLink(context, link) }, colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BERTIcon(symbol, accent)
                Text(title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                BERTIcon(BERTSymbol.ARROW, accent)
            }
            Text(invitation, color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            Text(link.launchLabel, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun ExperienceLink(title: String, description: String, symbol: BERTSymbol, link: BERTLink) {
    val context = LocalContext.current
    Card(onClick = { openBERTLink(context, link) }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BERTIcon(symbol)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Text(link.launchLabel, color = AccentText, fontSize = 12.sp)
            }
        }
    }
}
