package global.bert.widget.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import global.bert.widget.BERTEntry
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.compose.runtime.remember
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import global.bert.widget.MainActivity
import global.bert.widget.R
import global.bert.widget.data.BERTHoldingsStore
import global.bert.widget.data.BERTMarketHistoryRepository
import global.bert.widget.data.BERTPriceHistory
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.formatObservedAt
import global.bert.widget.formatCompactUsd
import global.bert.widget.formatHoldingsUsd
import global.bert.widget.formatPercent
import global.bert.widget.formatPrice
import global.bert.widget.formatTokenAmount
import global.bert.widget.theme.BERTThemePack
import global.bert.widget.theme.BERTPalette
import global.bert.widget.theme.BERTThemeStore
import global.bert.widget.work.BERTRefreshWorker
import global.bert.widget.work.BERTWidgetStaleRenderWorker

open class BERTWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Data is read inside the composition, keyed on a revision that updateAllBERTWidgets bumps. Values read
            // before provideContent would stay frozen while Glance keeps a session alive between updates.
            val revision = currentState(REVISION) ?: 0
            val size = LocalSize.current
            // The chart is a 600x180 bitmap: made only for the layout that draws it (critique 2026-11-12 ENG-4).
            val chart = drawsSparkline(size)
            val data = remember(revision, chart) { WidgetData.load(context, sparkline = chart) }
            val quote = data.quote
            val sparkline = data.sparkline
            val palette = data.palette
            val fontScale = LocalContext.current.resources.configuration.fontScale
            CompositionLocalProvider(LocalWidgetPalette provides palette) {
                val market = size.width >= MARKET_MIN_WIDTH
                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(ColorProvider(palette.background))
                        .clickable(actionStartActivity<MainActivity>(entryParameters(BERTEntry.MARKET))),
                ) {
                    // Sizes below are pinned by BERTWidgetRenderTest, which renders every text at launcher sizes.
                    if (market) {
                        val narrow = size.width < 290.dp
                        MarketWidgetContent(quote, data.holdings, showPosition = data.showPosition,
                            layout = marketLayout(size.height.value, fontScale, narrow), narrow = narrow, largeText = fontScale > LARGE_TEXT)
                    } else CompactWidgetContent(quote, sparkline, narrow = size.width < 150.dp, short = size.height < COMPACT_CHART_MIN_HEIGHT)
                }
            }
        }
    }
}

@Composable
private fun CompactWidgetContent(quote: BERTQuote?, sparkline: android.graphics.Bitmap?, narrow: Boolean, short: Boolean) {
    val palette = LocalWidgetPalette.current
    Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
        WidgetHeader(compact = true, quote = quote, showName = !narrow)
        Spacer(GlanceModifier.height(4.dp))
        if (quote == null) {
            UnavailableWidgetContent(compact = true)
            return@Column
        }
        Text(
            formatPrice(quote.priceUsd),
            style = TextStyle(color = ColorProvider(palette.text), fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${trendArrow(quote.change24hPct)} ${formatPercent(quote.change24hPct)}",
                style = TextStyle(color = ColorProvider(changeColor(quote)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "24H",
                modifier = GlanceModifier.background(ColorProvider(palette.panel)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = TextStyle(color = ColorProvider(palette.muted), fontSize = FRESHNESS_SP.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (short) {
            // Price and freshness matter more than the chart when the widget is resized small.
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.height(8.dp))
            if (sparkline == null) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight().background(ColorProvider(palette.panel)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("NO HISTORY YET", style = eyebrowStyle())
                }
            } else {
                Image(
                    provider = ImageProvider(sparkline),
                    contentDescription = "BERT price history",
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
        }
        Text(
            freshnessLabel(quote, narrow),
            style = TextStyle(color = ColorProvider(if (quote.isStale) Amber else palette.muted), fontSize = FRESHNESS_SP.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** Market widget layouts, richest first; [marketLayout] picks the richest one a size holds at the phone's text size. */
internal enum class MarketLayout { SPACIOUS, ROOMY, SLIM, MINIMAL }

/** From this text size up the widget shortens and drops secondary text instead of letting it wrap or clip. */
private const val LARGE_TEXT = 1.15f
private val MARKET_MIN_WIDTH = 240.dp
private val COMPACT_CHART_MIN_HEIGHT = 140.dp

/** Vertical padding of a boxed metric: tighter at large text, so the 3x2 cell keeps its source line. */
private fun metricPadding(largeText: Boolean) = if (largeText) 4f else 7f

/**
 * Glance cannot measure, so the layout comes from estimated heights in dp: each text line takes about 1.33em (Roboto
 * with font padding), sp scaled by [fontScale]. At default text the thresholds stay 200dp and 165dp as before; at large
 * text a size gets the richest layout whose lines all fit, down to MINIMAL (price, change and position, no metrics).
 */
internal fun marketLayout(heightDp: Float, fontScale: Float, narrow: Boolean): MarketLayout {
    val em = 1.33f * fontScale
    val head = 24f + 3f
    val priceRow = em * ((if (narrow) 22f else 25f) + 12f)
    val metrics = em * (FRESHNESS_SP + 12f)
    val boxed = 2 * metricPadding(fontScale > LARGE_TEXT) + em * (FRESHNESS_SP + 14f)
    val footer = em * FRESHNESS_SP
    val spacious = 2 * 12f + 30f + 8f + em * (30f + 14f) + 10f + 14f + em * (FRESHNESS_SP + 14f) + footer
    val roomy = 2 * 6f + head + priceRow + boxed + 6f + footer
    val slim = 2 * 6f + head + priceRow + metrics
    return when {
        heightDp >= maxOf(200f, spacious) -> MarketLayout.SPACIOUS
        heightDp >= maxOf(165f, roomy) -> MarketLayout.ROOMY
        heightDp >= slim -> MarketLayout.SLIM
        else -> MarketLayout.MINIMAL
    }
}

/** Only the compact widget at its taller sizes draws the price chart; the market layout and a short compact one never do. */
internal fun drawsSparkline(size: androidx.compose.ui.unit.DpSize) = size.width < MARKET_MIN_WIDTH && size.height >= COMPACT_CHART_MIN_HEIGHT

@Composable
private fun MarketWidgetContent(quote: BERTQuote?, holdings: Double?, showPosition: Boolean, layout: MarketLayout, narrow: Boolean, largeText: Boolean) {
    val palette = LocalWidgetPalette.current
    val spacious = layout == MarketLayout.SPACIOUS
    Row(modifier = GlanceModifier.fillMaxSize().padding(vertical = if (spacious) 12.dp else 6.dp, horizontal = 8.dp)) {
        Spacer(GlanceModifier.width(3.dp).fillMaxHeight().background(ColorProvider(palette.accent)))
        Spacer(GlanceModifier.width(11.dp))
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(compact = false, quote = quote, spacious = spacious)
            Spacer(GlanceModifier.height(if (spacious) 8.dp else 3.dp))
            if (quote == null) {
                UnavailableWidgetContent(compact = false)
                return@Column
            }
            // With no room for the metrics, the price sits in the middle rather than above an empty band.
            if (layout == MarketLayout.MINIMAL) Spacer(GlanceModifier.defaultWeight())
            // At large text on the narrow sizes the position tile takes only the room its words need.
            val compactTile = narrow && largeText
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        formatPrice(quote.priceUsd),
                        style = TextStyle(color = ColorProvider(palette.text), fontSize = if (spacious) 30.sp else if (narrow) 22.sp else 25.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${trendArrow(quote.change24hPct)} ${formatPercent(quote.change24hPct)} · 24H",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(changeColor(quote)), fontSize = if (spacious) 14.sp else 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
                // "Show my holdings on the Market widget" off: the market figures alone (critique 2026-11-12 PRIV-1).
                if (showPosition) {
                    Spacer(GlanceModifier.width(if (compactTile) 6.dp else if (narrow) 8.dp else 12.dp))
                    HoldingsCapsule(quote, holdings, layout, narrow, compactTile, largeText)
                }
            }
            val pad = metricPadding(largeText).dp
            when (layout) {
                MarketLayout.SPACIOUS -> {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetric("MARKET CAP", quote.marketCapUsd, GlanceModifier.defaultWeight(), panel = true, padV = pad)
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetMetric("24H VOLUME", quote.volume24hUsd, GlanceModifier.defaultWeight(), panel = true, padV = pad)
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetMetric("LIQUIDITY", quote.liquidityUsd, GlanceModifier.defaultWeight(), panel = true, padV = pad)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    MarketFooter(quote)
                }
                MarketLayout.ROOMY, MarketLayout.SLIM -> {
                    val roomy = layout == MarketLayout.ROOMY
                    // At large text the boxes keep a narrower inner margin, so "MARKET CAP" stays on one line.
                    val padH = if (largeText) 6.dp else 10.dp
                    Spacer(GlanceModifier.defaultWeight())
                    // Between the slim and spacious layouts, boxed metrics and the source line fill what would be a gap.
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        WidgetMetric("MARKET CAP", quote.marketCapUsd, GlanceModifier.defaultWeight(), panel = roomy, padV = pad, padH = padH)
                        if (roomy) Spacer(GlanceModifier.width(6.dp))
                        WidgetMetric("24H VOLUME", quote.volume24hUsd, GlanceModifier.defaultWeight(), panel = roomy, padV = pad, padH = padH)
                        if (roomy) Spacer(GlanceModifier.width(6.dp))
                        WidgetMetric("LIQUIDITY", quote.liquidityUsd, GlanceModifier.defaultWeight(), panel = roomy, padV = pad, padH = padH)
                    }
                    if (roomy) {
                        Spacer(GlanceModifier.height(6.dp))
                        MarketFooter(quote)
                    }
                }
                MarketLayout.MINIMAL -> Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun WidgetHeader(compact: Boolean, quote: BERTQuote?, spacious: Boolean = false, showName: Boolean = true) {
    val palette = LocalWidgetPalette.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.bert_token),
            contentDescription = "BERT token",
            modifier = GlanceModifier.width(if (compact) 26.dp else if (spacious) 30.dp else 24.dp)
                .height(if (compact) 26.dp else if (spacious) 30.dp else 24.dp),
            contentScale = ContentScale.Crop,
        )
        if (showName) {
            Spacer(GlanceModifier.width(9.dp))
            // The official wordmark at the cap height the text name had (640x186 artwork).
            val markHeight = if (compact) 12.dp else if (spacious) 16.dp else 14.dp
            Image(ImageProvider(R.drawable.bert_wordmark), "BERT",
                GlanceModifier.height(markHeight).width(markHeight * (640f / 186f)),
                contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ColorProvider(palette.text)))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            when { quote == null -> "● NO QUOTE"; quote.isStale -> "● DELAYED"; else -> "● UPDATED" },
            style = TextStyle(
                color = ColorProvider(if (quote == null || quote.isStale) Amber else Green),
                fontSize = FRESHNESS_SP.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun HoldingsCapsule(quote: BERTQuote, holdings: Double?, layout: MarketLayout, narrow: Boolean, compactTile: Boolean, largeText: Boolean) {
    val palette = LocalWidgetPalette.current
    val spacious = layout == MarketLayout.SPACIOUS
    // A compact tile wraps its words: a shorter label, the value, and no token count (the app shows it).
    val width = if (compactTile) GlanceModifier else GlanceModifier.width(if (spacious) 154.dp else if (narrow) 108.dp else 142.dp)
    Column(
        modifier = width
            .background(ColorProvider(palette.panel))
            .padding(horizontal = if (compactTile) 6.dp else if (spacious) 14.dp else if (narrow) 8.dp else 11.dp,
                vertical = if (spacious) 10.dp else if (layout == MarketLayout.ROOMY && !largeText) 6.dp else 3.dp),
    ) {
        Text(if (compactTile) "HOLDINGS" else "YOUR POSITION", maxLines = 1, style = eyebrowStyle(color = palette.text))
        Text(
            if (holdings == null) (if (compactTile) "NOT SET" else "SET HOLDINGS") else formatHoldingsUsd(holdings * quote.priceUsd),
            maxLines = 1,
            style = TextStyle(color = ColorProvider(if (holdings == null) Amber else palette.text), fontSize = if (spacious) 15.sp else 12.sp, fontWeight = FontWeight.Bold),
        )
        if (holdings != null && !compactTile) {
            Text("${formatTokenAmount(holdings)} BERT", maxLines = 1, style = eyebrowStyle())
        }
    }
}

@Composable
private fun UnavailableWidgetContent(compact: Boolean) {
    val palette = LocalWidgetPalette.current
    Text("PRICE UNAVAILABLE", style = eyebrowStyle())
    Spacer(GlanceModifier.height(4.dp))
    Text(
        "Open BERT to refresh",
        style = TextStyle(color = ColorProvider(palette.text), fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun WidgetMetric(label: String, value: Double?, modifier: GlanceModifier = GlanceModifier, panel: Boolean = false,
                         padV: androidx.compose.ui.unit.Dp = 7.dp, padH: androidx.compose.ui.unit.Dp = 10.dp) {
    val palette = LocalWidgetPalette.current
    Column(modifier = if (panel) modifier.background(ColorProvider(palette.panel)).padding(horizontal = padH, vertical = padV) else modifier) {
        Text(label, style = eyebrowStyle())
        Text(formatCompactUsd(value), style = TextStyle(color = ColorProvider(palette.text), fontSize = if (panel) 14.sp else 12.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MarketFooter(quote: BERTQuote) {
    val palette = LocalWidgetPalette.current
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            "${quote.sourceName.uppercase()} · ${quote.dex.uppercase()}",
            style = eyebrowStyle(),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            freshnessLabel(quote),
            style = eyebrowStyle(color = if (quote.isStale) Amber else palette.muted),
        )
    }
}

@Composable
private fun changeColor(quote: BERTQuote) = when {
    quote.change24hPct == null -> LocalWidgetPalette.current.muted
    quote.change24hPct >= 0 -> Green
    else -> Red
}

private fun trendArrow(change: Double?) = when {
    change == null -> "•"
    change >= 0 -> "↗"
    else -> "↘"
}

// Absolute time: a widget can sit on screen long after it was rendered, so "updated now" would go false.
@Composable
private fun freshnessLabel(quote: BERTQuote, narrow: Boolean = false): String {
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    // Narrow widgets already show "● DELAYED" in the header, so the footer keeps only the observation date.
    val prefix = if (quote.isStale && !narrow) "DELAYED · " else ""
    return "$prefix${formatObservedAt(quote.observedAtEpochMillis, use24Hour = use24Hour, dateOnlyWhenOld = narrow)}".uppercase()
}

@Composable
private fun eyebrowStyle(color: Color? = null) =
    TextStyle(color = ColorProvider(color ?: LocalWidgetPalette.current.muted), fontSize = FRESHNESS_SP.sp, fontWeight = FontWeight.Medium)

internal data class WidgetPalette(
    val background: Color,
    val panel: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
) {
    companion object {
        fun from(theme: BERTPalette) = WidgetPalette(
            background = Color(theme.backgroundArgb),
            panel = Color(theme.panelArgb),
            text = Color(theme.textArgb),
            muted = Color(theme.mutedArgb),
            accent = Color(theme.accentArgb),
        )
    }
}

private val LocalWidgetPalette = compositionLocalOf {
    WidgetPalette.from(BERTThemePack.DEFAULT.palette)
}

private val Green = Color(0xFF45E09A)
internal val Red = Color(0xFFFF7583)
private val Amber = Color(0xFFFFC857)

/** Starts background refresh with the first placed widget and stops it after the last is removed. */
abstract class BERTWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        BERTRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        BERTRefreshWorker.syncSchedule(context)
    }
}

internal class WidgetData private constructor(
    val quote: BERTQuote?, val holdings: Double?, val showPosition: Boolean, val sparkline: android.graphics.Bitmap?, val palette: WidgetPalette,
) {
    companion object {
        /** [sparkline] false skips the chart's bitmap (and the history read behind it) for a layout that never draws it. */
        fun load(context: Context, sparkline: Boolean): WidgetData {
            val quote = BERTQuoteRepository(context).load()
            val chart = if (!sparkline) null else {
                val history = BERTPriceHistory.combine(BERTMarketHistoryRepository(context).load(), BERTPriceHistory(context).load())
                // The chart takes its colour from the same 24h change the widget prints next to it.
                BERTSparkline.render(history, rising = quote?.change24hPct?.let { it >= 0 })
            }
            val store = BERTHoldingsStore(context)
            return WidgetData(quote, store.load(), store.showOnWidget, chart, WidgetPalette.from(BERTThemeStore(context).loadPalette()))
        }
    }
}

internal val REVISION = intPreferencesKey("bert_widget_revision")

class BERTWidgetReceiver : BERTWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = BERTWidget()
}

class BERTMarketWidget : BERTWidget()

class BERTMarketWidgetReceiver : BERTWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = BERTMarketWidget()
}

class BERTDailyWidgetReceiver : BERTWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = BERTDailyWidget()
}

suspend fun updateAllBERTWidgets(context: Context, scheduleStaleRender: Boolean = true) {
    // Scheduled first so a failed render cannot leave an earlier quote's timer in place.
    if (scheduleStaleRender) BERTWidgetStaleRenderWorker.schedule(context, BERTQuoteRepository(context).load())
    val manager = GlanceAppWidgetManager(context)
    for (widget in listOf(BERTWidget(), BERTMarketWidget(), BERTDailyWidget(), BERTPlaceWidget())) {
        for (id in manager.getGlanceIds(widget.javaClass)) {
            // Bumping the revision makes a live session reload its data rather than recompose stale values.
            updateAppWidgetState(context, id) { it[REVISION] = (it[REVISION] ?: 0) + 1 }
            widget.update(context, id)
        }
    }
}

/** Widget taps land on the matching screen; Glance passes action parameters to the activity as intent extras. */
internal fun entryParameters(entry: BERTEntry) = actionParametersOf(ActionParameters.Key<String>(BERTEntry.EXTRA) to entry.name)
