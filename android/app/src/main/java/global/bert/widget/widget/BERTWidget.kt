package global.bert.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.formatAge
import global.bert.widget.formatCompactUsd
import global.bert.widget.formatHoldingsUsd
import global.bert.widget.formatPercent
import global.bert.widget.formatPrice
import global.bert.widget.formatTokenAmount

open class BERTWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quote = BERTQuoteRepository(context).load()
        val holdings = BERTHoldingsStore(context).load()
        provideContent {
            val size = LocalSize.current
            val market = size.width >= 240.dp
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Navy))
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (!market) {
                    Image(
                        provider = ImageProvider(R.drawable.bert_widget_ambient),
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxWidth().height(26.dp),
                        contentScale = ContentScale.FillBounds,
                    )
                }
                if (market) {
                    MarketWidgetContent(quote, holdings)
                } else {
                    CompactWidgetContent(quote)
                }
            }
        }
    }
}

@Composable
private fun CompactWidgetContent(quote: BERTQuote?) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
        WidgetHeader(compact = true, quote = quote)
        Spacer(GlanceModifier.height(4.dp))
        if (quote == null) {
            UnavailableWidgetContent(compact = true)
            return@Column
        }
        Text(
            formatPrice(quote.priceUsd),
            style = TextStyle(color = ColorProvider(Cream), fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${trendArrow(quote.change24hPct)} ${formatPercent(quote.change24hPct)}",
                style = TextStyle(color = ColorProvider(changeColor(quote)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "24H",
                modifier = GlanceModifier.background(ColorProvider(PanelStrong)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = TextStyle(color = ColorProvider(Muted), fontSize = 8.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            freshnessLabel(quote),
            style = TextStyle(color = ColorProvider(if (quote.isStale) Amber else Muted), fontSize = 8.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun MarketWidgetContent(quote: BERTQuote?, holdings: Double?) {
    Row(modifier = GlanceModifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 8.dp)) {
        Spacer(GlanceModifier.width(3.dp).fillMaxHeight().background(ColorProvider(Orange)))
        Spacer(GlanceModifier.width(11.dp))
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(compact = false, quote = quote)
            Spacer(GlanceModifier.height(5.dp))
            if (quote == null) {
                UnavailableWidgetContent(compact = false)
                return@Column
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        formatPrice(quote.priceUsd),
                        style = TextStyle(color = ColorProvider(Cream), fontSize = 25.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "${trendArrow(quote.change24hPct)} ${formatPercent(quote.change24hPct)} · 24H",
                        style = TextStyle(color = ColorProvider(changeColor(quote)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.width(12.dp))
                HoldingsCapsule(quote, holdings)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WidgetMetric("MARKET CAP", quote.marketCapUsd, GlanceModifier.defaultWeight())
                WidgetMetric("24H VOLUME", quote.volume24hUsd, GlanceModifier.defaultWeight())
                WidgetMetric("LIQUIDITY", quote.liquidityUsd, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun WidgetHeader(compact: Boolean, quote: BERTQuote?) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.bert_token),
            contentDescription = "BERT token",
            modifier = GlanceModifier.width(if (compact) 26.dp else 28.dp).height(if (compact) 26.dp else 28.dp),
            contentScale = ContentScale.Crop,
        )
        Spacer(GlanceModifier.width(9.dp))
        Text(
            "BERT",
            style = TextStyle(color = ColorProvider(Cream), fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            if (quote?.isStale == true) "● DELAYED" else "● ${if (compact) "LIVE" else "MARKET LIVE"}",
            style = TextStyle(
                color = ColorProvider(if (quote?.isStale == true) Amber else Green),
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun HoldingsCapsule(quote: BERTQuote, holdings: Double?) {
    Column(
        modifier = GlanceModifier.width(142.dp)
            .background(ColorProvider(PanelStrong))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text("YOUR POSITION", style = eyebrowStyle(color = Cream, size = 8))
        Text(
            if (holdings == null) "SET HOLDINGS" else formatHoldingsUsd(holdings * quote.priceUsd),
            style = TextStyle(color = ColorProvider(if (holdings == null) Amber else Cream), fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        if (holdings != null) {
            Text("${formatTokenAmount(holdings)} BERT", style = eyebrowStyle())
        }
    }
}

@Composable
private fun UnavailableWidgetContent(compact: Boolean) {
    Text("PRICE UNAVAILABLE", style = eyebrowStyle())
    Spacer(GlanceModifier.height(4.dp))
    Text(
        "Open BERT to refresh",
        style = TextStyle(color = ColorProvider(Cream), fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun WidgetMetric(label: String, value: Double?, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        Text(label, style = eyebrowStyle(size = 8))
        Text(formatCompactUsd(value), style = TextStyle(color = ColorProvider(Cream), fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

private fun changeColor(quote: BERTQuote) = when {
    quote.change24hPct == null -> Muted
    quote.change24hPct >= 0 -> Green
    else -> Red
}

private fun trendArrow(change: Double?) = when {
    change == null -> "•"
    change >= 0 -> "↗"
    else -> "↘"
}

private fun freshnessLabel(quote: BERTQuote): String =
    "${if (quote.isStale) "DELAYED · " else ""}${formatAge(quote.observedAtEpochMillis)}".uppercase()

private fun eyebrowStyle(color: Color = Muted, size: Int = 9) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = FontWeight.Medium)

private val Navy = Color(0xFF071A2F)
private val PanelStrong = Color(0xFF123957)
private val Cream = Color(0xFFFFF5DF)
private val Muted = Color(0xFF9CB0C5)
private val Orange = Color(0xFFF25836)
private val Green = Color(0xFF45E09A)
private val Red = Color(0xFFFF6B7A)
private val Amber = Color(0xFFFFC857)

class BERTWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BERTWidget()
}

class BERTMarketWidget : BERTWidget()

class BERTMarketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BERTMarketWidget()
}

suspend fun updateAllBERTWidgets(context: Context) {
    BERTWidget().updateAll(context)
    BERTMarketWidget().updateAll(context)
}
