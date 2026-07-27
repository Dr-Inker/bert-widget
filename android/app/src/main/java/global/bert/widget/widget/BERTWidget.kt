package global.bert.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
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
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.formatCompactUsd
import global.bert.widget.formatPercent
import global.bert.widget.formatPrice

class BERTWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quote = BERTQuoteRepository(context).load()
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(Color(0xFFFFF7ED)))
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(16.dp),
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text("🐾  \$BERT", style = TextStyle(color = ColorProvider(Color(0xFFC2410C)), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.width(8.dp))
                    if (quote?.isStale == true) Text("cached", style = secondaryStyle())
                }
                Spacer(GlanceModifier.height(10.dp))
                if (quote == null) Text("Open the BERT app to load a quote.", style = secondaryStyle())
                else QuoteWidgetContent(quote, LocalSize.current.width >= 240.dp)
            }
        }
    }
}

@Composable
private fun QuoteWidgetContent(quote: BERTQuote, expanded: Boolean) {
    Text(formatPrice(quote.priceUsd), style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold))
    val changeColor = if ((quote.change24hPct ?: 0.0) >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
    Text("${formatPercent(quote.change24hPct)} 24h", style = TextStyle(color = ColorProvider(changeColor), fontSize = 14.sp, fontWeight = FontWeight.Bold))
    if (expanded) {
        Spacer(GlanceModifier.height(12.dp))
        Row {
            WidgetMetric("Market cap", quote.marketCapUsd); Spacer(GlanceModifier.width(18.dp))
            WidgetMetric("Volume", quote.volume24hUsd); Spacer(GlanceModifier.width(18.dp))
            WidgetMetric("Liquidity", quote.liquidityUsd)
        }
    }
}

@Composable
private fun WidgetMetric(label: String, value: Double?) {
    Column { Text(label, style = secondaryStyle()); Text(formatCompactUsd(value), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)) }
}

private fun secondaryStyle() = TextStyle(color = ColorProvider(Color(0xFF6B7280)), fontSize = 11.sp)

class BERTWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BERTWidget()
}
