package global.bert.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.QuoteState

@Composable
internal fun HomeScreen(state: QuoteState, now: Long, navigate: (BERTDestination) -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("WELCOME TO BERT")
            Text("Small dog.\nA whole world to explore.", color = Cream, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
            Text("Find your way around the BERT world, follow the market, and make your phone your own.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            Button(onClick = { navigate(BERTDestination.STUDIO) }) { Text("Make it BERT", fontWeight = FontWeight.Bold) }
        }
    }
    Card(onClick = { navigate(BERTDestination.MARKET) }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel("MARKET AT A GLANCE")
                Text("Explore →", color = Cream, fontSize = 12.sp)
            }
            when (state) {
                is QuoteState.Available -> {
                    Text(formatPrice(state.quote.priceUsd), color = Cream, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${formatPercent(state.quote.change24hPct)} past 24h", color = movementColor(state.quote.change24hPct), fontSize = 14.sp)
                    QuoteFreshness(state, now)
                }
                QuoteState.Loading -> Text("Fetching the latest quote…", color = Muted)
                is QuoteState.Unavailable -> Text("Market unavailable · Tap to retry", color = Amber)
            }
        }
    }
    SectionLabel("YOUR BERT TOOLKIT")
    FeatureLink("Your holdings", "Track your position privately on this device", "◎") { navigate(BERTDestination.HOLDINGS) }
    FeatureLink("Widgets & wallpapers", "Put a little BERT on your home screen", "✦") { navigate(BERTDestination.STUDIO) }
    SectionLabel("EXPLORE THE BERT WORLD")
    Text("Around the ecosystem · opens in your browser", color = Muted, fontSize = 12.sp)
    FeatureLink("Meet BERT", "The story, artwork, and community · bert.global", "↗") { openBERTLink(context, "https://www.bert.global/") }
    FeatureLink("Woofhub", "Dog adoption and care · woofhub.com", "↗") { openBERTLink(context, "https://woofhub.com/") }
    FeatureLink("Berthalla", "Explore more of the BERT ecosystem · berthalla.io", "↗") { openBERTLink(context, "https://berthalla.io/") }
    Text("You can explore BERT and personalize your phone without entering holdings.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
internal fun FeatureLink(title: String, description: String, symbol: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(symbol, color = Orange, fontSize = 23.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

internal fun openBERTLink(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "Install a browser to open this link.", Toast.LENGTH_LONG).show() }
}
