package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExploreScreen() {
    Text("Follow your nose.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    Text("A game, a soundtrack, a whole pack to meet.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
    LostTrailCard()
    FeaturedExperience("Flappy Bert", "One more flap?", "Arcade runs and community tournaments.", BERTSymbol.PLAY, Green, BERTLink.FLAPPY)
    FeaturedExperience("Bert Music", "Soundtrack your day.", "Music from the BERT community.", BERTSymbol.MUSIC, Color(0xFFD2B5FF), BERTLink.MUSIC)
    SectionLabel("AROUND THE PACK")
    ExperienceLink("Bert’s dispatches", "Town updates and municipal opinions.", BERTSymbol.STORY, BERTLink.DISPATCHES)
    ExperienceLink("Meet the community", "Catch up with the pack.", BERTSymbol.PACK, BERTLink.COMMUNITY)
    ExperienceLink("Woofhub", "Dog adoption, care and profiles.", BERTSymbol.HEART, BERTLink.WOOFHUB)
    ExperienceLink("Meet Bertram", "The dog behind the hat.", BERTSymbol.HOME, BERTLink.STORY)
}

@Composable
internal fun LostTrailCard() {
    val context = LocalContext.current
    Card(onClick = { openLostTrail(context) }, colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Amber.copy(alpha = 0.35f))) {
        Image(painterResource(R.drawable.lost_trail), contentDescription = "Bert on the Lantern Orchard trail", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SUPER BERT WORLD", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("The Lost Trail", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Six worlds. Eighteen keepsakes. One mighty bark.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            Text("Play here · works offline", color = Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
