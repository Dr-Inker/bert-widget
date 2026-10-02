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

    /** Top-left of [view] inside [root]. The host is never attached to a window, so getLocationInWindow reports 0,0. */
    private fun offsetIn(view: View, root: View): Pair<Int, Int> {
        var x = 0; var y = 0; var v: View? = view
        while (v != null && v !== root) { x += v.left - v.scrollX; y += v.top - v.scrollY; v = v.parent as? View }
        return x to y
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
    private fun problems(r: Rendered, multiline: Set<String> = emptySet()): List<String> {
        return r.texts.mapNotNull { tv ->
            val (left, top) = offsetIn(tv, r.root)
            val layout = tv.layout
            val lines = layout?.lineCount ?: 0
            val ellipsized = layout != null && (0 until lines).any { layout.getEllipsisCount(it) > 0 }
            // getLineWidth counts a wrapped line's trailing space, which is never drawn: measure the visible glyphs.
            // An ellipsized line draws only up to its ellipsis start, then the ellipsis.
            fun shown(l: android.text.Layout, i: Int): String = l.getLineStart(i).let { start ->
                if (l.getEllipsisCount(i) > 0) tv.text.substring(start, start + l.getEllipsisStart(i)).trimEnd() + "…"
                else tv.text.substring(start, l.getLineEnd(i)).trimEnd()
            }
            val widest = layout?.let { l -> (0 until lines).maxOfOrNull { tv.paint.measureText(shown(l, it)) } } ?: 0f
            val clippedH = widest > tv.width - tv.totalPaddingLeft - tv.totalPaddingRight + 1
            // Lines laid out beyond the view's own height are cut mid-glyph without any ellipsis.
            val overflowsBox = layout != null && layout.height > tv.height - tv.totalPaddingTop - tv.totalPaddingBottom + 1
            val outside = left < 0 || top < 0 || left + tv.width > r.root.width || top + tv.height > r.root.height || clippedByAncestor(tv)
            val prose = tv.text.toString() in multiline // e.g. Bert's dispatch: wrapping and a final ellipsis are intended
            val issue = listOfNotNull("wraps to $lines lines".takeIf { lines > 1 && !prose }, "ellipsized".takeIf { ellipsized && !prose },
                "clipped".takeIf { clippedH }, "taller than its box".takeIf { overflowsBox }, "outside widget".takeIf { outside })
            if (issue.isEmpty()) null else "'${tv.text}': ${issue.joinToString()}"
        }
    }

    /** Largest vertical stretch with no laid-out text, as a share of the widget height: a big void reads as broken. */
    private fun emptiestBand(r: Rendered): Float {
        val spans = r.texts.map { tv ->
            val top = offsetIn(tv, r.root).second + tv.totalPaddingTop
            top to top + minOf(tv.layout?.height ?: 0, tv.height - tv.totalPaddingTop - tv.totalPaddingBottom)
        }.sortedBy { it.first }
        var edge = 0; var widest = 0
        for ((top, bottom) in spans) { widest = maxOf(widest, top - edge); edge = maxOf(edge, bottom) }
        widest = maxOf(widest, r.root.height - edge)
        return widest.toFloat() / r.root.height
    }

    /** Share of the widget covered by Bert's own picture (the gradient overlay is not "Bert"). */
    private fun bertArtShare(r: Rendered): Float {
        var largest = 0
        fun walk(v: View) {
            if (v is android.widget.ImageView && v.visibility == View.VISIBLE && v.contentDescription == "Bert") largest = maxOf(largest, v.width * v.height)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(r.root)
        return largest.toFloat() / (r.root.width * r.root.height)
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

    @Test fun bertWidgetFitsAtEverySizeWithDispatchAndTournament() {
        val dispatch = "woofmornin. the ladybug holds the screen door like the wind can wait its turn, and the mayor is in no hurry either."
        val now = System.currentTimeMillis()
        val status = JSONObject().put("updated_at", now / 1000 - 60).put("mood", "content").put("latest_post", dispatch)
            .put("flappy", JSONObject().put("name", "The Autumn Arc").put("ends_at", Instant.ofEpochMilli(now + (60L * 24 + 7) * 3_600_000).toString())
                .put("status", "live").put("pool", 250).put("top", org.json.JSONArray().put(JSONObject().put("rank", 1).put("name", "LamexCrypt").put("score", 143).put("prize", 100))))
        context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit().putString("last_valid_activity", status.toString()).commit()
        val report = BERT_SIZES.associate { (w, h) ->
            "bert ${w}x$h" to problems(render(BERTDailyWidget(), w, h, "bert-${w}x$h"), multiline = setOf(dispatch))
        }
        println("WIDGET REPORT bert: $report")
        assertTrue(report.toString(), report.values.all { it.isEmpty() })
    }

    /** Owner on the S25 at 4x2: "looks empty and weird". Bert fills the widget at every size, long post or short. */
    @Test fun bertWidgetIsFilledAndShowsBertAtEverySize() {
        val now = System.currentTimeMillis()
        val posts = mapOf(
            "short" to "the blanket settled heavier on its own. like the season finally taught it how to hold still.",
            "long" to "woofmornin. the ladybug holds the screen door like the wind can wait its turn, and the mayor is in no hurry either. " +
                "somewhere a kettle is thinking about it. the pack has opinions about breakfast and none of them are quiet.",
        )
        val report = posts.flatMap { (kind, post) ->
            listOf(true, false).flatMap { tournament ->
                val status = JSONObject().put("updated_at", now / 1000 - 60).put("mood", "giddy").put("latest_post", post)
                if (tournament) status.put("flappy", JSONObject().put("name", "The Autumn Arc").put("ends_at", Instant.ofEpochMilli(now + 30 * 3_600_000L).toString())
                    .put("status", "live").put("pool", 250).put("top", org.json.JSONArray().put(JSONObject().put("rank", 1).put("name", "LamexCrypt").put("score", 143).put("prize", 100))))
                context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit().putString("last_valid_activity", status.toString()).commit()
                BERT_SIZES.map { (w, h) ->
                    val tag = "$kind${if (tournament) "-event" else ""}-${w}x$h"
                    val r = render(BERTDailyWidget(), w, h, "bert-fill-$tag")
                    val band = emptiestBand(r); val art = bertArtShare(r)
                    val issues = problems(r, multiline = setOf(post)) +
                        listOfNotNull("empty band ${(band * 100).toInt()}%".takeIf { band > 0.25f },
                            "Bert art only ${(art * 100).toInt()}%".takeIf { w >= 200 && art < 0.18f })
                    tag to issues
                }
            }
        }.toMap()
        println("WIDGET REPORT bert fill: $report")
        assertTrue(report.filterValues { it.isNotEmpty() }.toString(), report.values.all { it.isEmpty() })
    }

    private companion object {
        /** Minimum 2x1 up to a large 4x2; 380x190 is roughly the S25's 4x2 cell. */
        val BERT_SIZES = listOf(120 to 110, 180 to 180, 250 to 110, 300 to 130, 320 to 150, 360 to 200, 380 to 190, 400 to 220)
    }
}
