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
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.data.BERTHoldingsStore
import global.bert.widget.formatCompactUsd
import global.bert.widget.formatAge
import global.bert.widget.formatHoldingsUsd
import global.bert.widget.formatTokenAmount
import global.bert.widget.formatPercent
import global.bert.widget.formatPrice

open class BERTWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quote = BERTQuoteRepository(context).load()
        val holdings = BERTHoldingsStore(context).load()
        provideContent {
            val size = LocalSize.current
            val compact = size.height < 140.dp
            val expanded = size.width >= 280.dp && size.height >= 160.dp
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Navy))
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(if (compact) 12.dp else 16.dp),
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Image(
                        provider = ImageProvider(R.drawable.bert_token),
                        contentDescription = "BERT token",
                        modifier = GlanceModifier.width(if (compact) 30.dp else 40.dp).height(if (compact) 30.dp else 40.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text("\$BERT", style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        if (!compact) Text("BERTRAM THE POMERANIAN", style = eyebrowStyle())
                    }
                    if (expanded && quote != null) {
                        Text(
                            formatAge(quote.observedAtEpochMillis).uppercase(),
                            style = TextStyle(
                                color = ColorProvider(if (quote.isStale) Amber else Muted),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
                Spacer(GlanceModifier.height(if (expanded) 14.dp else if (compact) 6.dp else 10.dp))
                if (quote == null) {
                    Text("PRICE UNAVAILABLE", style = eyebrowStyle())
                    Spacer(GlanceModifier.height(4.dp))
                    Text("Open BERT to refresh", style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                } else QuoteWidgetContent(quote, holdings, expanded, compact)
            }
        }
    }
}

@Composable
private fun QuoteWidgetContent(quote: BERTQuote, holdings: Double?, expanded: Boolean, compact: Boolean) {
    val changeColor = when {
        quote.change24hPct == null -> Muted
        quote.change24hPct >= 0 -> Green
        else -> Red
    }
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = if (expanded) GlanceModifier.defaultWeight() else GlanceModifier) {
            Text(formatPrice(quote.priceUsd), style = TextStyle(color = ColorProvider(Color.White), fontSize = if (expanded) 30.sp else 26.sp, fontWeight = FontWeight.Bold))
            Text("${formatPercent(quote.change24hPct)}  ·  24H", style = TextStyle(color = ColorProvider(changeColor), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        if (expanded) {
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("YOUR BERT", style = eyebrowStyle())
                Text(
                    if (holdings == null) "SET HOLDINGS" else formatCompactUsd(holdings * quote.priceUsd),
                    style = TextStyle(color = ColorProvider(if (holdings == null) Amber else Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                if (holdings != null) Text("${formatTokenAmount(holdings)} BERT", style = eyebrowStyle())
            }
        }
    }
    if (expanded) {
        Spacer(GlanceModifier.height(14.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetMetric("MARKET CAP", quote.marketCapUsd, GlanceModifier.defaultWeight())
            WidgetMetric("24H VOLUME", quote.volume24hUsd, GlanceModifier.defaultWeight())
            WidgetMetric("LIQUIDITY", quote.liquidityUsd, GlanceModifier.defaultWeight())
        }
    }
    if (!compact && !expanded) {
        Spacer(GlanceModifier.height(8.dp))
        Text(
            "${if (quote.isStale) "DELAYED  ·  " else ""}${formatAge(quote.observedAtEpochMillis)}",
            style = TextStyle(color = ColorProvider(if (quote.isStale) Amber else Muted), fontSize = 10.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun WidgetMetric(label: String, value: Double?, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) { Text(label, style = eyebrowStyle()); Text(formatCompactUsd(value), style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
}

private fun eyebrowStyle() = TextStyle(color = ColorProvider(Muted), fontSize = 9.sp, fontWeight = FontWeight.Medium)

private val Navy = Color(0xFF071A2F)
private val Muted = Color(0xFF9CB0C5)
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
