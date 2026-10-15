package global.bert.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun movementColor(change: Double?): Color = when {
    change == null -> Muted
    change >= 0 -> Green
    else -> Red
}

@Composable
internal fun QuoteFreshness(state: QuoteState.Available, now: Long) {
    val delayed = state.updateDelayed || state.quote.isStaleAt(now)
    Text(
        "${if (delayed) "Delayed · " else ""}${formatAge(state.quote.observedAtEpochMillis, now)}",
        color = if (delayed) Amber else Muted, fontSize = 12.sp,
    )
}

@Composable
internal fun MarketScreen(state: QuoteState, history: List<BERTPriceSample>, now: Long, refreshing: Boolean, refresh: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        QuoteState.Loading -> LoadingPanel()
        is QuoteState.Unavailable -> UnavailablePanel(state.message, refresh)
        is QuoteState.Available -> {
            val quote = state.quote
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("BERT / USD")
                    Text(formatPrice(quote.priceUsd), color = Cream, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${formatPercent(quote.change24hPct)} · past 24h", color = movementColor(quote.change24hPct), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    QuoteFreshness(state, now)
                    if (state.updateDelayed) Text("Couldn’t refresh. Showing the last saved quote.", color = Amber, fontSize = 13.sp)
                }
            }
            if (quote.hasActivity) ActivityCard(quote)
            PriceHistoryPanel(history, now)
            // Stacked rows keep labels and figures readable on narrow screens and at large font sizes.
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MarketMetric("Market cap", quote.marketCapUsd)
                    MarketMetric("Main pool 24h volume", quote.volume24hUsd)
                    MarketMetric("Main pool liquidity", quote.liquidityUsd)
                }
            }
            Button(colors = actionButtonColors(), border = actionButtonBorder(!refreshing), onClick = refresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
                Text(if (refreshing) "Refreshing…" else "Refresh quote")
            }
            OutlinedButton(onClick = {
                openBERTMarketLink(context, quote.pairUrl)
            }, modifier = Modifier.fillMaxWidth()) { Text("Open DEX Screener ↗", color = Cream) }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("KNOW WHAT YOU'RE LOOKING AT")
            Text("Market data from DEX Screener. Refreshes about every minute while this app is open. Android schedules widget updates separately.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Solana · BERT mint", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(BERTQuoteRepository.BERT_MINT, color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BERT mint", BERTQuoteRepository.BERT_MINT))
                Toast.makeText(context, "Mint copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy mint", color = Cream) }
            Text("Informational market data only.", color = Muted, fontSize = 12.sp)
        }
    }
}

/** Facts about the main pool's recent trading; deliberately no verdicts, so no "bullish" or "pressure" wording. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityCard(quote: BERTQuote) {
    val changes = listOf("1H" to quote.change1hPct, "6H" to quote.change6hPct, "24H" to quote.change24hPct).filter { it.second != null }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("ACTIVITY")
            if (changes.isNotEmpty()) {
                // Wraps whole chips onto a new line at large font sizes instead of breaking a figure.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    changes.forEach { (label, value) -> ChangeChip(label, value) }
                }
            }
            val buys1h = quote.buys1h
            val sells1h = quote.sells1h
            if (buys1h != null && sells1h != null) {
                Text(tradeCounts("Last hour:", buys1h, sells1h), color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (buys1h + sells1h > 0) {
                    val summary = "In the last hour, $buys1h buys and $sells1h sells."
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).semantics { contentDescription = summary }) {
                        if (buys1h > 0) Box(Modifier.weight(buys1h.toFloat()).fillMaxHeight().background(Green))
                        if (sells1h > 0) Box(Modifier.weight(sells1h.toFloat()).fillMaxHeight().background(Red))
                    }
                }
            }
            val buys24h = quote.buys24h
            val sells24h = quote.sells24h
            if (buys24h != null && sells24h != null) {
                Text(tradeCounts("Past 24h:", buys24h, sells24h), color = Muted, fontSize = 13.sp)
            }
            Text("Transactions on the main pool, via DEX Screener", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

private const val NBSP = "\u00A0"

/** "64 buys" with a non-breaking space, so a count never parts from its noun. */
private fun plural(count: Int, noun: String) = "${String.format(Locale.US, "%,d", count)}$NBSP$noun${if (count == 1) "" else "s"}"

/**
 * "Last hour: 64 buys · 25 sells". The separator is glued to the figure before it, so the only place the line can break
 * is after the dot: a wrapped line ends with "·" and never starts with one, at any width or font scale.
 */
internal fun tradeCounts(label: String, buys: Int, sells: Int) = "$label ${plural(buys, "buy")}$NBSP· ${plural(sells, "sell")}"

@Composable
private fun ChangeChip(label: String, value: Double?) {
    Surface(shape = RoundedCornerShape(12.dp), color = PanelStrong) {
        // One Text (label + coloured figure) so it reads as a single item and never matches the range chips' "1H"/"24H".
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Muted, fontSize = 13.sp)) { append("$label ") }
                withStyle(SpanStyle(color = movementColor(value), fontWeight = FontWeight.Bold)) { append(formatPercent(value)) }
            },
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 14.sp,
        )
    }
}

@Composable
private fun MarketMetric(label: String, value: Double?) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 14.sp)
            Text(formatCompactUsd(value), color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(formatCompactUsd(value), color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PriceHistoryPanel(history: List<BERTPriceSample>, now: Long) {
    var window by rememberSaveable { mutableStateOf(BERTChartWindow.DAY) }
    val samples = remember(history, window, now) { window.samples(history, now) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("PRICE HISTORY")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BERTChartWindow.entries.forEach { range ->
                    FilterChip(selected = range == window, onClick = { window = range }, label = { Text(range.label) })
                }
            }
            if (samples.size < 2) {
                Text("No recent prices yet", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("The chart fills in as soon as BERT can reach the market.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            } else {
                val low = samples.minOf { it.priceUsd }
                val high = samples.maxOf { it.priceUsd }
                val lineColor = movementColor(samples.last().priceUsd - samples.first().priceUsd)
                val cutoff = now - window.durationMillis
                val summary = "${samples.size} prices over the selected ${window.label} window. Low ${formatPrice(low)}, high ${formatPrice(high)}. Gaps over 30 minutes are not connected."
                Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = summary }) {
                    val inset = 6.dp.toPx()
                    val range = (high - low).takeIf { it > 0 } ?: (high * 0.01)
                    fun point(sample: BERTPriceSample): Offset = Offset(
                        inset + ((sample.observedAtEpochMillis - cutoff).toDouble() / window.durationMillis * (size.width - inset * 2)).toFloat(),
                        if (high == low) size.height / 2 else inset + ((high - sample.priceUsd) / range * (size.height - inset * 2)).toFloat(),
                    )
                    for (fraction in listOf(0.0f, 0.5f, 1.0f)) {
                        val y = inset + fraction * (size.height - 2 * inset)
                        drawLine(Muted.copy(alpha = 0.15f), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                    }
                    val joined = BooleanArray(samples.size)
                    samples.zipWithNext().forEachIndexed { index, (a, b) ->
                        if (b.observedAtEpochMillis - a.observedAtEpochMillis <= 30 * 60_000L) {
                            drawLine(lineColor, point(a), point(b), 2.dp.toPx(), cap = StrokeCap.Round)
                            joined[index] = true
                            joined[index + 1] = true
                        }
                    }
                    // Dots only where a line cannot show the price: isolated samples and the latest one.
                    samples.forEachIndexed { index, sample ->
                        if (!joined[index] || index == samples.lastIndex) drawCircle(lineColor, 3.dp.toPx(), point(sample))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // A clock time at each edge reads "00:26 → 00:26" across a 24-hour window.
                    Text(formatWindowStart(window.durationMillis), color = Muted, fontSize = 12.sp)
                    Text("Now", color = Muted, fontSize = 12.sp)
                }
                Text("Low ${formatPrice(low)} · high ${formatPrice(high)}", color = Cream, fontSize = 12.sp)
                Text("${samples.size} prices · ${clock.format(Date(samples.first().observedAtEpochMillis))}–${clock.format(Date(samples.last().observedAtEpochMillis))}", color = Muted, fontSize = 12.sp)
            }
            Text("5-minute prices from GeckoTerminal, plus the latest quotes on this device.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
