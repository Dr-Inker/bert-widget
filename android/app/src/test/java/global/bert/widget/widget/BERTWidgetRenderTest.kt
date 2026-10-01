package global.bert.widget.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import global.bert.widget.data.BERTHoldingsStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * Renders the real Glance widgets to RemoteViews at launcher sizes, inflates them on the host and
 * measures every text line. Host rendering, not a launcher: it proves layout, not placement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalGlanceApi::class)
class BERTWidgetRenderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun seed() = seed(observedAgoMillis = 60_000, holdings = 1_000_000.0, history = true)

    private fun seed(observedAgoMillis: Long, holdings: Double?, history: Boolean) {
        val envelope = JSONObject()
            .put("asset", JSONObject().put("chain", "solana").put("mint", "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump"))
            .put("quote", JSONObject().put("priceUsd", 0.015372).put("change24hPct", 1.43).put("marketCapUsd", 15_067_640.0)
                .put("volume24hUsd", 137_480.0).put("liquidityUsd", 1_178_452.0))
            .put("source", JSONObject().put("name", "dexscreener").put("pairAddress", "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY")
                .put("dex", "raydium").put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy")
                .put("observedAt", Instant.ofEpochMilli(System.currentTimeMillis() - observedAgoMillis).toString()))
            .put("meta", JSONObject().put("freshness", "fresh"))
        context.getSharedPreferences("bert_quote", Context.MODE_PRIVATE).edit().putString("last_valid_quote", envelope.toString()).commit()
        BERTHoldingsStore(context).save(holdings)
        val prefs = context.getSharedPreferences("bert_market_history", Context.MODE_PRIVATE).edit()
        if (history) {
            val now = System.currentTimeMillis() / 1000; val open = now - now % 300
            val points = org.json.JSONArray().apply { for (i in 287 downTo 0) put(org.json.JSONArray().put(open - i * 300L).put(0.0150 + 0.0004 * kotlin.math.sin(i / 20.0))) }
            prefs.putString("last_valid_history", JSONObject()
                .put("asset", JSONObject().put("chain", "solana").put("mint", "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump"))
                .put("source", JSONObject().put("name", "geckoterminal").put("intervalSeconds", 300)).put("points", points).toString())
        } else prefs.remove("last_valid_history")
        prefs.commit()
    }

    private data class Rendered(val root: View, val texts: List<TextView>)

    private fun render(widget: GlanceAppWidget, widthDp: Int, heightDp: Int, name: String): Rendered {
        val views = runBlocking { widget.compose(context, size = DpSize(widthDp.dp, heightDp.dp)) }
        val density = context.resources.displayMetrics.density
        val host = FrameLayout(context)
        val root = views.apply(context, host)
        host.addView(root)
        val w = (widthDp * density).toInt(); val h = (heightDp * density).toInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))
        File("build/outputs/host-ui/widget-$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val texts = mutableListOf<TextView>()
        fun walk(v: View) { if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) texts += v; if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(host)
        return Rendered(host, texts)
    }

    /** True when any ancestor's bounds cut off part of this view (Glance containers clip silently). */
    private fun clippedByAncestor(view: View): Boolean {
        var x = view.left; var y = view.top; var right = view.right; var bottom = view.bottom
        var parent = view.parent as? View
        while (parent != null) {
            if (x < 0 || y < 0 || right > parent.width + 1 || bottom > parent.height + 1) return true
            x += parent.left - parent.scrollX; y += parent.top - parent.scrollY
            right += parent.left - parent.scrollX; bottom += parent.top - parent.scrollY
            parent = parent.parent as? View
        }
        return false
    }

    /** Every visible text fits: one line, not ellipsized, fully inside the widget bounds. */
    private fun problems(r: Rendered): List<String> {
        val loc = IntArray(2); val rootLoc = IntArray(2); r.root.getLocationInWindow(rootLoc)
        return r.texts.mapNotNull { tv ->
            tv.getLocationInWindow(loc)
            val left = loc[0] - rootLoc[0]; val top = loc[1] - rootLoc[1]
            val layout = tv.layout
            val lines = layout?.lineCount ?: 0
            val ellipsized = layout != null && (0 until lines).any { layout.getEllipsisCount(it) > 0 }
            val widest = layout?.let { l -> (0 until lines).maxOfOrNull { l.getLineWidth(it) } } ?: 0f
            val clippedH = widest > tv.width - tv.totalPaddingLeft - tv.totalPaddingRight + 1
            val outside = left < 0 || top < 0 || left + tv.width > r.root.width || top + tv.height > r.root.height || clippedByAncestor(tv)
            val issue = listOfNotNull("wraps to $lines lines".takeIf { lines > 1 }, "ellipsized".takeIf { ellipsized },
                "clipped".takeIf { clippedH }, "outside widget".takeIf { outside })
            if (issue.isEmpty()) null else "'${tv.text}': ${issue.joinToString()}"
        }
    }

    @Test fun compactWidgetFitsFromMinimumToLarge() {
        val report = listOf(120 to 120, 150 to 150, 180 to 180).associate { (w, h) ->
            "compact ${w}x$h" to problems(render(BERTWidget(), w, h, "compact-${w}x$h"))
        }
        println("WIDGET REPORT compact: $report")
        assertTrue(report.toString(), report.values.all { it.isEmpty() })
    }

    @Test fun marketWidgetFitsFromMinimumToSpacious() {
        val report = listOf(240 to 120, 280 to 120, 320 to 150, 360 to 200).associate { (w, h) ->
            "market ${w}x$h" to problems(render(BERTMarketWidget(), w, h, "market-${w}x$h"))
        }
        println("WIDGET REPORT market: $report")
        assertTrue(report.toString(), report.values.all { it.isEmpty() })
    }

    @Test fun delayedQuoteWithoutHoldingsStillFitsAtMinimumSizes() {
        seed(observedAgoMillis = 26 * 3_600_000L, holdings = null, history = false)
        val report = listOf(Triple(BERTWidget(), 120, 120), Triple(BERTWidget(), 180, 180), Triple(BERTMarketWidget(), 240, 120), Triple(BERTMarketWidget(), 360, 200))
            .associate { (widget, w, h) -> "${widget::class.simpleName} ${w}x$h" to problems(render(widget, w, h, "delayed-${widget::class.simpleName}-${w}x$h")) }
        println("WIDGET REPORT delayed: $report")
        assertTrue(report.toString(), report.values.all { it.isEmpty() })
    }

    @Test fun sparklineColourFollowsTheTwentyFourHourChangeNotItsOwnSlope() {
        val falling = (0 until 20).map { global.bert.widget.data.BERTPriceSample(1_000_000L + it * 60_000L, 2.0 - it * 0.05) }
        fun colours(rising: Boolean?) = BERTSparkline.render(falling, rising = rising)!!.let { b ->
            (0 until b.width step 3).flatMap { x -> (0 until b.height step 3).map { y -> b.getPixel(x, y) } }.toSet()
        }
        val green = android.graphics.Color.parseColor("#45E09A"); val red = android.graphics.Color.parseColor("#FF6B7A")
        assertTrue(green in colours(rising = true) && red !in colours(rising = true))
        assertTrue(red in colours(rising = null))
    }
}
