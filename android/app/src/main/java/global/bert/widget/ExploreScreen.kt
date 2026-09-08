package global.bert.widget

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun ExploreScreen() {
    Text("Small dog. Big world.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    Text("Pick something that makes your day a little more BERT.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
    SectionLabel("PLAY & LISTEN")
    ExperienceLink("Flappy Bert", "Tap your way through the arcade. Scores and tournaments live in the game.", "↗", BERTLink.FLAPPY)
    ExperienceLink("Bert Music", "Three community tracks. Find your town soundtrack.", "♫", BERTLink.MUSIC)
    SectionLabel("AROUND THE PACK")
    ExperienceLink("Bert’s dispatches", "Character updates, daily puzzles and municipal opinions.", "☀", BERTLink.DISPATCHES)
    ExperienceLink("Meet the community", "Catch up with the BERT pack on Telegram.", "◎", BERTLink.COMMUNITY)
    ExperienceLink("Woofhub", "Explore dog adoption and care, or visit your dog’s profile.", "♡", BERTLink.WOOFHUB)
    ExperienceLink("Meet Bertram", "The story behind the small dog in the big hat.", "⌂", BERTLink.STORY)
}

@Composable
internal fun ExperienceLink(title: String, description: String, symbol: String, link: BERTLink) {
    val context = LocalContext.current
    FeatureLink(title, "$description\n${link.launchLabel} ↗", symbol) { openBERTLink(context, link) }
}
