package global.bert.widget

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.data.BERTHoldingsStore
import global.bert.widget.data.QuoteState
import global.bert.widget.widget.BERTMarketWidgetReceiver
import global.bert.widget.widget.BERTWidget
import global.bert.widget.widget.BERTWidgetReceiver
import global.bert.widget.widget.updateAllBERTWidgets
import kotlinx.coroutines.launch

private val Navy = Color(0xFF071A2F)
private val Panel = Color(0xFF0D2942)
private val PanelStrong = Color(0xFF123957)
private val Cream = Color(0xFFFFF5DF)
private val Muted = Color(0xFF9CB0C5)
private val Orange = Color(0xFFF25836)
private val Green = Color(0xFF45E09A)
private val Red = Color(0xFFFF6B7A)
private val Amber = Color(0xFFFFC857)

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
    val holdingsStore = remember { BERTHoldingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf<QuoteState>(repository.load()?.let { QuoteState.Available(it) } ?: QuoteState.Loading)
    }
    var refreshing by remember { mutableStateOf(false) }
    var holdings by remember { mutableStateOf(holdingsStore.load()) }

    suspend fun refresh() {
        refreshing = true
        state = try {
            val quote = repository.refresh()
            updateAllBERTWidgets(context)
            QuoteState.Available(quote)
        } catch (_: Exception) {
            repository.load()?.let { QuoteState.Available(it, true) }
                ?: QuoteState.Unavailable("BERT market data is unavailable right now.")
        }
        refreshing = false
    }

    LaunchedEffect(Unit) { refresh() }
    Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandHeader(refreshing)
            when (val current = state) {
                QuoteState.Loading -> LoadingPanel()
                is QuoteState.Unavailable -> UnavailablePanel(current.message) { scope.launch { refresh() } }
                is QuoteState.Available -> QuoteDashboard(
                    quote = current.quote,
                    updateDelayed = current.updateDelayed,
                    refreshing = refreshing,
                    onRefresh = { scope.launch { refresh() } },
                    holdings = holdings,
                    onHoldingsChanged = { amount ->
                        holdingsStore.save(amount)
                        holdings = amount
                        scope.launch { updateAllBERTWidgets(context) }
                    },
                )
            }
            WidgetSetup(context)
            Text(
                "Informational market data only · v${BuildConfig.VERSION_NAME}",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun BrandHeader(refreshing: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.bert_token),
            contentDescription = "BERT token",
            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("\$BERT", color = Cream, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("BERTRAM THE POMERANIAN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        if (refreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Orange, strokeWidth = 2.dp)
        else StatusPill("MARKET ONLINE", Green)
    }
}

@Composable
private fun QuoteDashboard(
    quote: BERTQuote,
    updateDelayed: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    holdings: Double?,
    onHoldingsChanged: (Double?) -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("CURRENT PRICE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(formatPrice(quote.priceUsd), color = Cream, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            val changeColor = when { quote.change24hPct == null -> Muted; quote.change24hPct >= 0 -> Green; else -> Red }
            Text("${formatPercent(quote.change24hPct)} · 24H", color = changeColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "${if (updateDelayed || quote.isStale) "DELAYED · " else ""}${formatAge(quote.observedAtEpochMillis)}",
                color = if (updateDelayed || quote.isStale) Amber else Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard("MARKET CAP", quote.marketCapUsd, Modifier.weight(1f))
        MetricCard("24H VOLUME", quote.volume24hUsd, Modifier.weight(1f))
        MetricCard("LIQUIDITY", quote.liquidityUsd, Modifier.weight(1f))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Orange),
        ) { Text(if (refreshing) "Refreshing…" else "Refresh", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = {
                val safeUrl = BERTQuoteRepository.requireValidDexScreenerPairUrl(quote.pairUrl)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
            },
            modifier = Modifier.weight(1f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        ) { Text("View market ↗", color = Cream, fontWeight = FontWeight.Bold) }
    }

    HoldingsEditor(holdings, quote.priceUsd, onHoldingsChanged)

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("MARKET SOURCE")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(quote.sourceName.uppercase(), color = Cream, fontWeight = FontWeight.Bold)
                Text(quote.dex.replaceFirstChar { it.uppercase() }, color = Muted)
            }
            SectionLabel("SOLANA MINT")
            Text( BERTQuoteRepository.BERT_MINT, color = Cream, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BERT mint", BERTQuoteRepository.BERT_MINT))
                    Toast.makeText(context, "Mint copied", Toast.LENGTH_SHORT).show()
                },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy mint", color = Cream) }
        }
    }
}

@Composable
private fun HoldingsEditor(holdings: Double?, priceUsd: Double, onSave: (Double?) -> Unit) {
    val context = LocalContext.current
    var input by remember(holdings) {
        mutableStateOf(holdings?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed = input.replace(",", "").trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SectionLabel("YOUR BERT")
            Text("Track the live value of your holdings.", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Stored only on this device. No wallet connection.", color = Muted, fontSize = 11.sp)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BERT amount") },
                placeholder = { Text("e.g. 250000") },
                suffix = { Text("BERT") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    if (error != null) Text(error!!)
                    else if (parsed != null) Text("Current value · ${formatHoldingsUsd(parsed * priceUsd)}")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Cream,
                    unfocusedTextColor = Cream,
                    disabledTextColor = Muted,
                    errorTextColor = Cream,
                    focusedContainerColor = PanelStrong,
                    unfocusedContainerColor = PanelStrong,
                    errorContainerColor = PanelStrong,
                    cursorColor = Orange,
                    errorCursorColor = Red,
                    focusedBorderColor = Orange,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.24f),
                    errorBorderColor = Red,
                    focusedLabelColor = Orange,
                    unfocusedLabelColor = Muted,
                    errorLabelColor = Red,
                    focusedPlaceholderColor = Muted,
                    unfocusedPlaceholderColor = Muted,
                    focusedSuffixColor = Muted,
                    unfocusedSuffixColor = Muted,
                    focusedSupportingTextColor = Muted,
                    unfocusedSupportingTextColor = Muted,
                    errorSupportingTextColor = Red,
                ),
            )
            Button(
                onClick = {
                    if (input.isBlank()) {
                        onSave(null)
                        Toast.makeText(context, "Holdings removed", Toast.LENGTH_SHORT).show()
                    } else if (parsed == null) {
                        error = "Enter a valid amount of zero or more."
                    } else {
                        onSave(parsed)
                        Toast.makeText(context, "Holdings saved to the Market widget", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (input.isBlank()) "Remove holdings" else "Save to Market widget", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Double?, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = RoundedCornerShape(17.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(formatCompactUsd(value), color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun WidgetSetup(context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("HOME-SCREEN WIDGETS")
            Text("Choose the quick price view or the full market desk.", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WidgetButton("Compact", "2×2", Modifier.weight(1f)) { pinWidget(context, BERTWidgetReceiver::class.java) }
                WidgetButton("Market", "4×2", Modifier.weight(1f)) { pinWidget(context, BERTMarketWidgetReceiver::class.java) }
            }
            Text("If your launcher does not support one-tap placement, long-press the home screen and choose Widgets → BERT.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun WidgetButton(name: String, size: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = Cream, fontWeight = FontWeight.Bold)
            Text(size, color = Muted, fontSize = 10.sp)
        }
    }
}

private fun pinWidget(context: Context, receiver: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    } else {
        Toast.makeText(context, "Open your launcher’s widget picker to add BERT", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun LoadingPanel() {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Orange, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Column { Text("Opening the market desk…", color = Cream, fontWeight = FontWeight.Bold); Text("Fetching a validated BERT quote", color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun UnavailablePanel(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusPill("UPDATE DELAYED", Amber)
            Text(message, color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Check your connection and try again. No market value has been guessed or substituted.", color = Muted, lineHeight = 20.sp)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("Try again") }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(99.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) {
        Text(text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Orange, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}
