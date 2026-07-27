package global.bert.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.data.QuoteState
import global.bert.widget.widget.BERTWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BERTScreen() } }
    }
}

@Composable
private fun BERTScreen() {
    val context = LocalContext.current
    val repository = remember { BERTQuoteRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf<QuoteState>(repository.load()?.let { QuoteState.Available(it) } ?: QuoteState.Loading)
    }

    suspend fun refresh() {
        state = try {
            val quote = repository.refresh()
            BERTWidget().updateAll(context)
            QuoteState.Available(quote)
        } catch (error: Exception) {
            repository.load()?.let { QuoteState.Available(it, true) }
                ?: QuoteState.Unavailable(error.message ?: "Quote unavailable")
        }
    }

    LaunchedEffect(Unit) { refresh() }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFFFBF5)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("🐾  \$BERT", color = Color(0xFFC2410C), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            when (val current = state) {
                QuoteState.Loading -> Text("Loading BERT…")
                is QuoteState.Unavailable -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { scope.launch { refresh() } }) { Text("Try again") }
                }
                is QuoteState.Available -> QuoteContent(current.quote, current.updateDelayed)
            }
            Spacer(Modifier.weight(1f))
            Text("Market data is informational and may be delayed.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun QuoteContent(quote: BERTQuote, updateDelayed: Boolean) {
    Text(formatPrice(quote.priceUsd), fontSize = 38.sp, fontWeight = FontWeight.Bold)
    Text(
        "${formatPercent(quote.change24hPct)} today",
        color = when { quote.change24hPct == null -> Color.Gray; quote.change24hPct >= 0 -> Color(0xFF15803D); else -> Color(0xFFB91C1C) },
        fontWeight = FontWeight.SemiBold,
    )
    if (updateDelayed || quote.isStale) {
        Spacer(Modifier.height(8.dp)); Text("Showing cached data", color = Color(0xFFC2410C))
    }
    Spacer(Modifier.height(28.dp)); HorizontalDivider()
    MetricRow("Market cap", quote.marketCapUsd)
    MetricRow("24h volume", quote.volume24hUsd)
    MetricRow("Liquidity", quote.liquidityUsd)
}

@Composable
private fun MetricRow(label: String, value: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) { Text(label, color = Color.Gray); Text(formatCompactUsd(value), fontWeight = FontWeight.SemiBold) }
}
