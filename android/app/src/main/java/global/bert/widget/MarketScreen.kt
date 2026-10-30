package global.bert.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException

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
            PriceHistoryPanel(history, now, quote.pairAddress)
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
            OutlinedButton(onClick = { openBERTLink(context, BERTLink.COINMARKETCAP) }, modifier = Modifier.fillMaxWidth()) {
                Text("View on CoinMarketCap ↗", color = Cream)
            }
            // The pool's own chart stays one tap away for anyone who wants the trades; it is also where the prices above come from.
            TextButton(onClick = { openBERTMarketLink(context, quote.pairUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("Chart on DEX Screener ↗", color = AccentText)
            }
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

/** Lets a test (or a preview) stand in for the GeckoTerminal-backed repository the long chart ranges load from. */
internal val LocalBERTRangeHistory = staticCompositionLocalOf<BERTRangeHistoryRepository?> { null }

/**
 * Why a long range could not be had, so the message names the right cause: a server that refused or rate-limited the
 * request is not a problem with the person's connection, and nor is one that took the connection and then took too long to
 * answer (a cold GeckoTerminal page can take 10-20 s; the read waits 30), so that timeout reads as busy rather than as
 * "Check your connection". A connection that could not be made in time (5 s) is a timeout too, but it is the weak signal or
 * captive portal "Check your connection" is for, so only a [BERTHttpReadTimeoutException] counts as busy.
 */
private enum class RangeFailure {
    CONNECTION, BUSY, OTHER;

    companion object {
        fun of(problem: Exception) = when {
            problem is BERTHttpStatusException && problem.status == 429 -> BUSY
            problem is BERTHttpReadTimeoutException -> BUSY
            problem is IOException -> CONNECTION
            else -> OTHER
        }
    }
}

/** Why a long range's last request failed, and when on the app's clock. */
private class RangeFailed(val failure: RangeFailure, val atEpochMillis: Long)

/** After a failed refresh with saved prices on screen, how long to wait before asking again on the app's clock. */
private const val RETRY_AFTER_FAILURE_MILLIS = 5 * 60_000L

/** "a week", "a month": how the failure message names the range that could not be loaded. */
private fun rangeSpan(window: BERTChartWindow) = when (window) {
    BERTChartWindow.WEEK -> "a week of prices"
    BERTChartWindow.MONTH -> "a month of prices"
    BERTChartWindow.YEAR -> "a year of prices"
    else -> "these prices"
}

private fun failedMessage(window: BERTChartWindow, failure: RangeFailure) = when (failure) {
    RangeFailure.CONNECTION -> "Couldn’t load ${rangeSpan(window)}. Check your connection."
    RangeFailure.BUSY -> "Couldn’t load ${rangeSpan(window)}. GeckoTerminal is busy, so try again in a minute."
    RangeFailure.OTHER -> "Couldn’t load ${rangeSpan(window)} from GeckoTerminal. Try again later."
}

/**
 * Why a long range can't be asked for at all, or null when it can. A quote names the pool the prices come from (both the
 * direct source and the Berthalla server always send it), so a quote without one is an old saved copy, and the next good
 * refresh brings it. None of these is worth a "Try again".
 */
private fun rangeUnavailable(window: BERTChartWindow, pool: String?, repository: BERTRangeHistoryRepository): String? = when {
    !repository.hasDirectSource -> "Longer ranges aren’t available in this build."
    pool == null -> "${rangeSpan(window).replaceFirstChar { it.uppercase() }} loads once a fresh quote arrives."
    else -> null
}

private fun rangeFooter(window: BERTChartWindow) = when (window.candles?.timeframe) {
    null -> "5-minute prices from GeckoTerminal, plus the latest quotes on this device."
    "day" -> "Daily closes from GeckoTerminal."
    else -> if (window.candles.aggregate == 1) "Hourly closes from GeckoTerminal." else "${window.candles.aggregate}-hour closes from GeckoTerminal."
}

/** How far the range pills' row reaches into the price card's 18dp padding on each side; see [RangePills]. */
private val PILL_BLEED = 4.dp

/**
 * Single-select range pills, each touch target at least 48dp wide. Six of those need 288dp, a little more than a 360dp
 * phone's card has inside its padding (284dp), so the row reaches [PILL_BLEED] into that padding on each side, where there
 * is nothing else to tap, and the pills drawn sit 1dp inside their touch boxes to keep a 2dp gap. Where even that is too
 * narrow (a 320dp phone), or past 1.3x text (the app's own large-text line, see [MarketMetric]), they split into two rows
 * of three, so neither a touch target nor a label is squeezed.
 */
@Composable
private fun RangePills(selected: BERTChartWindow, select: (BERTChartWindow) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val ranges = BERTChartWindow.entries
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val oneRow = LocalDensity.current.fontScale <= 1.3f && (maxWidth + PILL_BLEED * 2) / ranges.size >= 48.dp
        val perRow = if (oneRow) ranges.size else 3
        Column(Modifier.bleed(PILL_BLEED).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ranges.chunked(perRow).forEach { rowOfRanges ->
                Row(Modifier.fillMaxWidth()) {
                    rowOfRanges.forEach { range ->
                        val isSelected = range == selected
                        // The outer box is the 48dp touch target; the pill inside is the 32dp look.
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).selectable(isSelected, role = Role.RadioButton, onClick = {
                            if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            select(range)
                        }), contentAlignment = Alignment.Center) {
                            Surface(Modifier.fillMaxWidth().padding(horizontal = 1.dp), shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) PanelStrong else Color.Transparent,
                                border = BorderStroke(1.dp, if (isSelected) AccentText else Muted.copy(alpha = 0.35f))) {
                                Text(range.label, Modifier.padding(horizontal = 2.dp, vertical = 6.dp), color = if (isSelected) Cream else Muted,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    // A short last row keeps its pills the same width as the rows above it.
                    repeat(perRow - rowOfRanges.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Measures this [by] wider on each side than offered and places it reaching into the parent's padding, at the parent's width. */
private fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val wide = constraints.maxWidth + 2 * extra
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
private fun PriceHistoryPanel(history: List<BERTPriceSample>, now: Long, pool: String?) {
    var window by rememberSaveable { mutableStateOf(BERTChartWindow.DAY) }
    val context = LocalContext.current
    val rangeRepository = LocalBERTRangeHistory.current ?: remember { BERTRangeHistoryRepository(context.applicationContext) }
    // Each long range's prices, in memory. The saved copies (a prefs read and a JSON parse each) are read off the main
    // thread as soon as the panel shows, while the 24-hour chart is up, so by the time a long range is chosen its saved
    // prices are already here and show at once, with no "Loading" in between.
    val ranges = remember(pool) { mutableStateMapOf<BERTChartWindow, BERTRangeHistory>() }
    var savedRead by remember(pool) { mutableStateOf(false) }
    LaunchedEffect(pool) {
        val saved = withContext(Dispatchers.IO) {
            BERTChartWindow.entries.filter { it.isLongRange }.mapNotNull { range -> rangeRepository.cached(range, pool, now)?.let { range to it } }
        }
        saved.forEach { (range, copy) -> if (range !in ranges) ranges[range] = copy }
        savedRead = true
    }
    // What went wrong with each range's last request, and when; cleared by choosing that range again or "Try again".
    val failures = remember(pool) { mutableStateMapOf<BERTChartWindow, RangeFailed>() }
    // The ranges with a request on its way. A request belongs to the panel, not to the pill that is selected: a blocking
    // GeckoTerminal request can't be stopped part-way, so cancelling it when another pill was chosen only threw its answer
    // away, and coming back asked for the same prices again (a few quick taps could reach the free API's rate limit). So a
    // request carries on when the range changes, its answer lands in [ranges] (and is saved) wherever the person is, and
    // coming back while it is still on its way waits for it instead of asking twice. Leaving the screen cancels them all;
    // if the pool changes under one, its answer lands in the old pool's maps, which nothing reads any more.
    val loading = remember(pool) { mutableStateSetOf<BERTChartWindow>() }
    val requests = rememberCoroutineScope()
    val shown = ranges[window]
    val failed = failures[window]
    // Ask when there is nothing to show yet, or when what is shown has gone stale on the app's clock (now ticks every 15 s,
    // so sitting on 1W for hours still refreshes it), and never while this range's last request is still on its way. After
    // a failure with nothing to show, wait for "Try again"; with saved prices on screen, try again a few minutes later
    // rather than on every tick.
    fun isDue(range: BERTChartWindow): Boolean {
        val copy = ranges[range]
        val lastFailure = failures[range]
        return range.isLongRange && rangeUnavailable(range, pool, rangeRepository) == null && savedRead && range !in loading && if (copy == null) lastFailure == null
            else !BERTRangeHistoryRepository.isFresh(range, copy, now) && (lastFailure == null || now - lastFailure.atEpochMillis >= RETRY_AFTER_FAILURE_MILLIS)
    }
    val unavailable = if (window.isLongRange) rangeUnavailable(window, pool, rangeRepository) else null
    // The range is captured here, in composition, for the effect to ask about. [window] is state, so reading it inside the
    // effect, which starts a frame later, gave whatever had been chosen since: a pill tapped in between could have the
    // effect ask for that range, which its own effect then asked for again.
    val chosen = window
    val due = isDue(chosen)
    LaunchedEffect(chosen, pool, due) {
        // Checked again for the same range as the effect starts, since a request for it may have set off in between.
        if (!due || !isDue(chosen)) return@LaunchedEffect
        val askedAt = now
        loading += chosen
        requests.launch {
            try {
                ranges[chosen] = rangeRepository.refresh(chosen, pool, askedAt)
                failures.remove(chosen)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: Exception) {
                failures[chosen] = RangeFailed(RangeFailure.of(problem), askedAt)
            } finally {
                loading -= chosen
            }
        }
    }
    val samples = remember(history, window, now, shown) {
        if (window.isLongRange) shown?.let { window.longSamples(it.samples, now) }.orEmpty() else window.samples(history, now)
    }
    // A SimpleDateFormat keeps the zone it was made in, so each is made again when the phone's zone changes. Android resets
    // the default zone when the system's changes, and this reads it on every tick of the app's clock (every 15 s), so the
    // times and dates follow a trip across zones without Market having to be left and opened again.
    val zone = TimeZone.getDefault().id
    val clock = remember(zone) { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dates = remember(window, zone) { rangeDateFormat(window) }
    val localDates = remember(window, zone) { rangeDateFormat(window, zone = TimeZone.getDefault()) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("PRICE HISTORY")
            RangePills(window) { if (it != window) { failures.remove(it); window = it } }
            val longEmpty = window.isLongRange && shown == null
            // Prices enough to draw a chart, and its figures.
            val charted = !longEmpty && samples.size >= 2
            val change = if (charted) window.changePct(samples) else null
            // A range whose older prices could not be had starts at its first price and says from when.
            val late = charted && window.startsLate(samples, now)
            val origin = window.originEpochMillis(samples, now)
            val startLabel = formatWindowStart(window, origin, late, dates)
            // The amber note under a long range's chart, when what it shows is not everything that was asked for.
            val note = when {
                !charted || !window.isLongRange -> null
                failed != null -> "Couldn’t refresh. Showing the last saved prices."
                shown?.complete == false && late -> "Couldn’t load the older prices. Showing what arrived."
                else -> null
            }
            val status = when {
                longEmpty && unavailable != null -> RangeStatus(AnnotatedString(unavailable), 16.sp, bold = true, lineHeight = 22.sp)
                // The saved copies are still being read, for a moment at most: room for the chart rather than a "Loading" flash.
                longEmpty && !savedRead -> RangeStatus(AnnotatedString(""), 20.sp, bold = true)
                longEmpty && failed == null -> RangeStatus(AnnotatedString("Loading prices…"), 20.sp, bold = true)
                longEmpty && failed != null -> RangeStatus(AnnotatedString(failedMessage(window, failed.failure)), 16.sp, bold = true, lineHeight = 22.sp)
                !charted -> RangeStatus(AnnotatedString("No recent prices yet"), 20.sp, bold = true)
                // Same colours as the quote's own changes; the figure is first price to last on this chart.
                change != null -> RangeStatus(buildAnnotatedString {
                    withStyle(SpanStyle(color = Muted, fontSize = 13.sp)) { append(if (late) "Change since $startLabel " else "Change over the past ${windowName(window)} ") }
                    withStyle(SpanStyle(color = movementColor(change), fontWeight = FontWeight.Bold)) { append(formatPercent(change)) }
                }, 15.sp, bold = false)
                else -> RangeStatus(AnnotatedString(""), 15.sp, bold = false)
            }
            // The range's status, always this one node in this one place, whatever state the range is in: loading, its
            // change once the prices arrive, a failure, or why it can't be asked for. On a long range it is a polite live
            // region, and what TalkBack hears includes the note under the chart, so it says when a chosen range has
            // loaded, failed or gone stale. TalkBack only reads out a live region whose own words (here, its description)
            // change: one that newly
            // appears (as each of these did, from its own branch) is reported as a change to the panel around it, which
            // is not live, so it usually went unheard. Its words change only when the state or the prices on the chart do
            // (a refresh, or the oldest candle passing out of the window, so on 1W about twice an hour), never on the
            // clock's tick alone; the 5-minute ranges, whose prices move every minute, are not live at all.
            Text(status.text, color = Cream, fontSize = status.fontSize, fontWeight = if (status.bold) FontWeight.Bold else null,
                lineHeight = status.lineHeight, modifier = Modifier
                    .then(if (longEmpty && unavailable == null && !savedRead) Modifier.height(150.dp) else Modifier)
                    // One property, so one change event per new wording: the Text's own words plus a content description
                    // changed together and reached TalkBack as two updates from the same live region. The description is
                    // the one kept, as a description change is the update TalkBack reliably reads out from a live region.
                    .then(if (window.isLongRange) Modifier.clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        val said = listOfNotNull(status.text.text.ifEmpty { null }, note).joinToString(" ")
                        if (said.isNotEmpty()) contentDescription = said
                    } else Modifier))
            when {
                // A reason it can't be asked for needs nothing more, and while the saved copies are read the status line
                // above holds the chart's room itself.
                longEmpty && (unavailable != null || !savedRead) -> Unit
                longEmpty && failed == null -> Text("The first look at a long range can take a few seconds.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                longEmpty -> TextButton(onClick = { failures.remove(window) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Try again", color = AccentText) }
                !charted -> Text("The chart fills in as soon as BERT can reach the market.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                else -> {
                    val low = samples.minOf { it.priceUsd }
                    val high = samples.maxOf { it.priceUsd }
                    val lineColor = movementColor(samples.last().priceUsd - samples.first().priceUsd)
                    val span = (now - origin).coerceAtLeast(1)
                    val gapLimit = window.maxJoinGapMillis
                    val scope = if (late) "since $startLabel" else "over the selected ${window.label} window"
                    val summary = "${samples.size} prices $scope. Low ${formatPrice(low)}, high ${formatPrice(high)}. Gaps over ${formatGapLimit(gapLimit)} are not connected."
                    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = summary }) {
                        val inset = 6.dp.toPx()
                        val range = (high - low).takeIf { it > 0 } ?: (high * 0.01)
                        fun point(sample: BERTPriceSample): Offset = Offset(
                            inset + ((sample.observedAtEpochMillis - origin).toDouble() / span * (size.width - inset * 2)).toFloat(),
                            if (high == low) size.height / 2 else inset + ((high - sample.priceUsd) / range * (size.height - inset * 2)).toFloat(),
                        )
                        for (fraction in listOf(0.0f, 0.5f, 1.0f)) {
                            val y = inset + fraction * (size.height - 2 * inset)
                            drawLine(Muted.copy(alpha = 0.15f), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                        }
                        val joined = BooleanArray(samples.size)
                        samples.zipWithNext().forEachIndexed { index, (a, b) ->
                            if (b.observedAtEpochMillis - a.observedAtEpochMillis <= gapLimit) {
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
                        Text(startLabel, color = Muted, fontSize = 12.sp)
                        Text("Now", color = Muted, fontSize = 12.sp)
                    }
                    Text("Low ${formatPrice(low)} · high ${formatPrice(high)}", color = Cream, fontSize = 12.sp)
                    Text("${samples.size} prices · ${spanOfSamples(window, samples, shown, clock, dates, localDates)}", color = Muted, fontSize = 12.sp)
                    // Hidden from TalkBack: the status line above already says it, so swiping through would hear it twice.
                    if (note != null) Text(note, color = Amber, fontSize = 12.sp, modifier = Modifier.semantics { hideFromAccessibility() })
                }
            }
            Text(rangeFooter(window), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** The status line under the range pills: its words, and how large they read. */
private class RangeStatus(val text: AnnotatedString, val fontSize: TextUnit, val bold: Boolean, val lineHeight: TextUnit = TextUnit.Unspecified)

/** "week", "6 hours": the window as it reads in "Change over the past week". */
private fun windowName(window: BERTChartWindow) = when (window) {
    BERTChartWindow.HOUR -> "hour"
    BERTChartWindow.SIX_HOURS -> "6 hours"
    BERTChartWindow.DAY -> "24 hours"
    BERTChartWindow.WEEK -> "week"
    BERTChartWindow.MONTH -> "month"
    BERTChartWindow.YEAR -> "year"
}

/** Local clock times for the day-sized windows; for the long ones, dates as [formatRangeSpan] gives them (see [rangeDateZone]). */
private fun spanOfSamples(window: BERTChartWindow, samples: List<BERTPriceSample>, copy: BERTRangeHistory?, clock: SimpleDateFormat,
                          dates: SimpleDateFormat, localDates: SimpleDateFormat): String {
    val first = samples.first().observedAtEpochMillis
    val last = samples.last().observedAtEpochMillis
    // A saved copy's last price sits at the moment it was fetched (see BERTRangeHistoryRepository.parse), not on a candle.
    return if (window.isLongRange) formatRangeSpan(first, last, last == copy?.fetchedAtEpochMillis, dates, localDates)
        else "${clock.format(Date(first))}–${clock.format(Date(last))}"
}
