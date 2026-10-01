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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
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
            val data = remember(revision) { WidgetData.load(context) }
            val quote = data.quote
            val holdings = data.holdings
            val sparkline = data.sparkline
            val palette = data.palette
            CompositionLocalProvider(LocalWidgetPalette provides palette) {
                val size = LocalSize.current
                val market = size.width >= 240.dp
                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(ColorProvider(palette.background))
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    // Sizes below are pinned by BERTWidgetRenderTest, which renders every text at launcher sizes.
                    if (market) MarketWidgetContent(quote, holdings, spacious = size.height >= 200.dp, narrow = size.width < 290.dp)
                    else CompactWidgetContent(quote, sparkline, narrow = size.width < 150.dp, short = size.height < 165.dp)
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
                style = TextStyle(color = ColorProvider(palette.muted), fontSize = 8.sp, fontWeight = FontWeight.Bold),
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
                    Text("NO HISTORY YET", style = eyebrowStyle(size = 8))
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
            style = TextStyle(color = ColorProvider(if (quote.isStale) Amber else palette.muted), fontSize = 8.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun MarketWidgetContent(quote: BERTQuote?, holdings: Double?, spacious: Boolean, narrow: Boolean) {
    val palette = LocalWidgetPalette.current
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
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        formatPrice(quote.priceUsd),
                        style = TextStyle(color = ColorProvider(palette.text), fontSize = if (spacious) 30.sp else if (narrow) 22.sp else 25.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${trendArrow(quote.change24hPct)} ${formatPercent(quote.change24hPct)} · 24H",
                        style = TextStyle(color = ColorProvider(changeColor(quote)), fontSize = if (spacious) 14.sp else 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.width(if (narrow) 8.dp else 12.dp))
                HoldingsCapsule(quote, holdings, spacious, narrow)
            }
            if (spacious) {
                Spacer(GlanceModifier.height(10.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    WidgetMetric("MARKET CAP", quote.marketCapUsd, GlanceModifier.defaultWeight(), panel = true)
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetMetric("24H VOLUME", quote.volume24hUsd, GlanceModifier.defaultWeight(), panel = true)
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetMetric("LIQUIDITY", quote.liquidityUsd, GlanceModifier.defaultWeight(), panel = true)
                }
                Spacer(GlanceModifier.defaultWeight())
                MarketFooter(quote)
            } else {
                Spacer(GlanceModifier.defaultWeight())
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    WidgetMetric("MARKET CAP", quote.marketCapUsd, GlanceModifier.defaultWeight())
                    WidgetMetric("24H VOLUME", quote.volume24hUsd, GlanceModifier.defaultWeight())
                    WidgetMetric("LIQUIDITY", quote.liquidityUsd, GlanceModifier.defaultWeight())
                }
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
            Text(
                "BERT",
                style = TextStyle(color = ColorProvider(palette.text), fontSize = if (compact) 13.sp else if (spacious) 17.sp else 15.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            when { quote == null -> "● NO QUOTE"; quote.isStale -> "● DELAYED"; else -> "● UPDATED" },
            style = TextStyle(
                color = ColorProvider(if (quote == null || quote.isStale) Amber else Green),
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun HoldingsCapsule(quote: BERTQuote, holdings: Double?, spacious: Boolean, narrow: Boolean) {
    val palette = LocalWidgetPalette.current
    Column(
        modifier = GlanceModifier.width(if (spacious) 154.dp else if (narrow) 108.dp else 142.dp)
            .background(ColorProvider(palette.panel))
            .padding(horizontal = if (spacious) 14.dp else if (narrow) 8.dp else 11.dp, vertical = if (spacious) 10.dp else 6.dp),
    ) {
        Text("YOUR POSITION", style = eyebrowStyle(color = palette.text, size = 8))
        Text(
            if (holdings == null) "SET HOLDINGS" else formatHoldingsUsd(holdings * quote.priceUsd),
            style = TextStyle(color = ColorProvider(if (holdings == null) Amber else palette.text), fontSize = if (spacious) 15.sp else 12.sp, fontWeight = FontWeight.Bold),
        )
        if (holdings != null) {
            Text("${formatTokenAmount(holdings)} BERT", style = eyebrowStyle())
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
private fun WidgetMetric(label: String, value: Double?, modifier: GlanceModifier = GlanceModifier, panel: Boolean = false) {
    val palette = LocalWidgetPalette.current
    Column(modifier = if (panel) modifier.background(ColorProvider(palette.panel)).padding(horizontal = 10.dp, vertical = 7.dp) else modifier) {
        Text(label, style = eyebrowStyle(size = 8))
        Text(formatCompactUsd(value), style = TextStyle(color = ColorProvider(palette.text), fontSize = if (panel) 14.sp else 12.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MarketFooter(quote: BERTQuote) {
    val palette = LocalWidgetPalette.current
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            "${quote.sourceName.uppercase()} · ${quote.dex.uppercase()}",
            style = eyebrowStyle(size = 8),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            freshnessLabel(quote),
            style = eyebrowStyle(color = if (quote.isStale) Amber else palette.muted, size = 8),
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
private fun eyebrowStyle(color: Color? = null, size: Int = 9) =
    TextStyle(color = ColorProvider(color ?: LocalWidgetPalette.current.muted), fontSize = size.sp, fontWeight = FontWeight.Medium)

private data class WidgetPalette(
    val background: Color,
    val panel: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
) {
    companion object {
        fun from(theme: BERTThemePack) = WidgetPalette(
            background = Color(theme.backgroundArgb),
            panel = Color(theme.panelArgb),
            text = Color(theme.textArgb),
            muted = Color(theme.mutedArgb),
            accent = Color(theme.accentArgb),
        )
    }
}

private val LocalWidgetPalette = compositionLocalOf {
    WidgetPalette.from(BERTThemePack.WOOFHUB_NIGHT)
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

private class WidgetData(val quote: BERTQuote?, val holdings: Double?, val sparkline: android.graphics.Bitmap?, val palette: WidgetPalette) {
    companion object {
        fun load(context: Context): WidgetData {
            val quote = BERTQuoteRepository(context).load()
            val history = BERTPriceHistory.combine(BERTMarketHistoryRepository(context).load(), BERTPriceHistory(context).load())
            // The chart takes its colour from the same 24h change the widget prints next to it.
            val sparkline = BERTSparkline.render(history, rising = quote?.change24hPct?.let { it >= 0 })
            return WidgetData(quote, BERTHoldingsStore(context).load(), sparkline, WidgetPalette.from(BERTThemeStore(context).load()))
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
    for (widget in listOf(BERTWidget(), BERTMarketWidget(), BERTDailyWidget())) {
        for (id in manager.getGlanceIds(widget.javaClass)) {
            // Bumping the revision makes a live session reload its data rather than recompose stale values.
            updateAppWidgetState(context, id) { it[REVISION] = (it[REVISION] ?: 0) + 1 }
            widget.update(context, id)
        }
    }
}
