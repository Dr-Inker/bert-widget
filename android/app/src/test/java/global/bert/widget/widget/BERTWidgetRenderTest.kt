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
        val (host, bitmap) = renderWidgetOnHost(context, widget, widthDp, heightDp).let { it.host to it.bitmap }
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

    /**
     * Largest vertical stretch with no laid-out text, as a share of the widget height: a big void reads as broken.
     * A described picture across most of the width (Bert's banner, the price chart) counts as content; the side
     * portrait and the undescribed glow do not, so wide Bert widgets are judged on their text column.
     */
    private fun emptiestBand(r: Rendered): Float {
        val banners = mutableListOf<Pair<Int, Int>>()
        fun walk(v: View) {
            if (v is android.widget.ImageView && v.visibility == View.VISIBLE && v.contentDescription != null && v.width > r.root.width * 0.6f)
                offsetIn(v, r.root).second.let { banners += it to it + v.height }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(r.root)
        val spans = (banners + r.texts.map { tv ->
            val top = offsetIn(tv, r.root).second + tv.totalPaddingTop
            top to top + minOf(tv.layout?.height ?: 0, tv.height - tv.totalPaddingTop - tv.totalPaddingBottom)
        }).sortedBy { it.first }
        var edge = 0; var widest = 0
        for ((top, bottom) in spans) { widest = maxOf(widest, top - edge); edge = maxOf(edge, bottom) }
        widest = maxOf(widest, r.root.height - edge)
        return widest.toFloat() / r.root.height
    }

    private fun voids(r: Rendered) = emptiestBand(r).let { band -> listOfNotNull("empty band ${(band * 100).toInt()}%".takeIf { band > 0.25f }) }

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
        val report = listOf(120 to 120, 150 to 150, 160 to 140, 180 to 180).associate { (w, h) ->
            "compact ${w}x$h" to render(BERTWidget(), w, h, "compact-${w}x$h").let { problems(it) + voids(it) }
        }
        println("WIDGET REPORT compact: $report")
        assertTrue(report.toString(), report.values.all { it.isEmpty() })
    }

    @Test fun marketWidgetFitsFromMinimumToSpacious() {
        val report = listOf(240 to 120, 280 to 120, 320 to 150, 340 to 180, 360 to 200).associate { (w, h) ->
            "market ${w}x$h" to render(BERTMarketWidget(), w, h, "market-${w}x$h").let { problems(it) + voids(it) }
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
        val report = bertFillReport("fill")
        println("WIDGET REPORT bert fill: $report")
        assertTrue(report.filterValues { it.isNotEmpty() }.toString(), report.values.all { it.isEmpty() })
    }

    /** Android's largest default text step: the fit estimate must still keep whole lines inside the widget. */
    @Test fun bertWidgetFitsWithLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        try {
            val report = bertFillReport("font130")
            println("WIDGET REPORT bert font 1.3: $report")
            assertTrue(report.filterValues { it.isNotEmpty() }.toString(), report.values.all { it.isEmpty() })
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    /** The listing promises every widget says when its data is from: the S25's 2x2 and 4x2 show the feed's time. */
    @Test fun bertWidgetShowsWhenItsUpdateIsFrom() {
        seedActivity(LONG_POST, tournament = true)
        val missing = listOf(180 to 190, 380 to 190).filter { (w, h) ->
            render(BERTDailyWidget(), w, h, "bert-stamp-${w}x$h").texts.none { it.text.startsWith("UPDATED ") }
        }
        assertTrue("no UPDATED time at $missing", missing.isEmpty())
    }

    /**
     * Bug from the 0.9.0 critique: narrow Bert widgets cut the mood to 9 letters ("MELANCHOLY" showed as "MELANCHOL").
     * Every current mood, at every size and at large text, is drawn whole or not at all, and the longest one is
     * drawn at every size where the widget has room for a header (everything from the 2x2 banner up).
     */
    @Test fun moodIsNeverCutAtAnySizeOrTextScale() {
        val moods = listOf("giddy", "smug", "content", "anxious", "melancholy")
        val sizes = BERT_SIZES + listOf(130 to 200, 120 to 160, 140 to 110, 200 to 110, 210 to 120)
        val failures = mutableListOf<String>(); val dropped = sortedSetOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            RuntimeEnvironment.setFontScale(scale)
            try {
                for (mood in moods) for (tournament in listOf(false, true)) {
                    seedActivity(LONG_POST, tournament, mood)
                    for ((w, h) in sizes) {
                        val r = render(BERTDailyWidget(), w, h, "bert-mood-$mood-s${(scale * 10).toInt()}-${w}x$h")
                        val label = mood.uppercase()
                        val pills = r.texts.map { it.text.toString() }.filter { it.length >= 3 && label.startsWith(it.removeSuffix("…")) }
                        if (pills.any { it != label }) failures += "$mood ${w}x$h at x$scale: cut to $pills"
                        if (pills.isEmpty()) dropped += "$mood ${w}x$h x$scale"
                        problems(r, multiline = setOf(LONG_POST)).forEach { failures += "$mood ${w}x$h at x$scale: $it" }
                    }
                }
                // The longest word keeps its pill at the 2x2 and 4x2 sizes a phone actually offers, even at large text.
                seedActivity(LONG_POST, tournament = true, mood = "melancholy")
                for ((w, h) in listOf(180 to 190, 180 to 180, 150 to 150, 380 to 190, 400 to 220, 320 to 150))
                    if (render(BERTDailyWidget(), w, h, "bert-mood-keep-${w}x$h").texts.none { it.text.toString() == "MELANCHOLY" }) failures += "melancholy missing at ${w}x$h x$scale"
            } finally { RuntimeEnvironment.setFontScale(1f) }
        }
        println("WIDGET REPORT mood pill dropped (never cut): $dropped")
        assertTrue(failures.toString(), failures.isEmpty())
    }

    /** Freshness, status, mood and event text is never smaller than 10sp, in any widget, at any size, at default and large text. */
    @Test fun noWidgetTextIsBelowTenSp() {
        seedActivity(LONG_POST, tournament = true, mood = "melancholy")
        val small = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            RuntimeEnvironment.setFontScale(scale)
            try {
                val density = context.resources.displayMetrics.density
                val cases = BERT_SIZES.map { (w, h) -> Triple(BERTDailyWidget(), w, h) } + listOf(120 to 120, 150 to 150, 160 to 140, 180 to 180).map { (w, h) -> Triple(BERTWidget(), w, h) } +
                    listOf(240 to 120, 280 to 120, 320 to 150, 340 to 180, 360 to 200).map { (w, h) -> Triple(BERTMarketWidget(), w, h) }
                for ((widget, w, h) in cases) render(widget, w, h, "min-sp-${widget::class.simpleName}-$w-x${(scale * 10).toInt()}x$h").texts.forEach { tv ->
                    // Android scales sp by the font scale; allow half a pixel of rounding.
                    if (tv.textSize < 10f * density * scale - 0.5f) small += "${widget::class.simpleName} ${w}x$h x$scale '${tv.text}' ${tv.textSize / density / scale}sp"
                }
            } finally { RuntimeEnvironment.setFontScale(1f) }
        }
        assertTrue(small.toString(), small.isEmpty())
    }

    /** The unconfirmed-feed and no-quote states use the same status text: still 10sp or more. */
    @Test fun noQuoteAndDelayedStatesAreAlsoTenSpOrMore() {
        seed(observedAgoMillis = 26 * 3_600_000L, holdings = null, history = false)
        val density = context.resources.displayMetrics.density
        val small = listOf(Triple(BERTWidget(), 120, 120), Triple(BERTWidget(), 180, 180), Triple(BERTMarketWidget(), 240, 120), Triple(BERTMarketWidget(), 360, 200)).flatMap { (widget, w, h) ->
            render(widget, w, h, "min-sp-delayed-${widget::class.simpleName}-${w}x$h").texts.filter { it.textSize < 10f * density - 0.5f }.map { "${widget::class.simpleName} ${w}x$h '${it.text}'" }
        }
        assertTrue(small.toString(), small.isEmpty())
    }

    /** The 2x2 sizes (S25 is 180x190) say when Bert's feed is from, with a short or long post and with or without the tournament. */
    @Test fun twoByTwoBertWidgetsShowTheAsOfTime() {
        val clock = Regex("\\d{1,2}:\\d{2}")
        val missing = mutableListOf<String>()
        for (post in listOf(SHORT_POST, LONG_POST)) for (tournament in listOf(false, true)) {
            seedActivity(post, tournament)
            for ((w, h) in listOf(150 to 150, 180 to 180, 180 to 190))
                if (render(BERTDailyWidget(), w, h, "bert-2x2-stamp-${w}x$h").texts.none { clock.containsMatchIn(it.text) }) missing += "${w}x$h tournament=$tournament len=${post.length}"
        }
        assertTrue("no as-of time at $missing", missing.isEmpty())
    }

    /** Every theme pack keeps the widget's words legible (WCAG AA, 4.5:1) on its own background and panel. */
    @Test fun bertWidgetColoursAreLegibleInEveryTheme() {
        fun lum(argb: Long) = listOf(16, 8, 0).map { ((argb shr it) and 0xFF) / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }.let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }
        fun ratio(a: Long, b: Long) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)
        val failures = global.bert.widget.theme.BERTThemePack.entries.flatMap { t ->
            listOf("text/background" to ratio(t.textArgb, t.backgroundArgb), "muted/background" to ratio(t.mutedArgb, t.backgroundArgb),
                "gold/panel" to ratio(0xFFFFC857, t.panelArgb), "mood pill" to ratio(0xFF1F0E02, 0xFFFF9433))
                .filter { it.second < 4.5 }.map { "${t.id} ${it.first} ${"%.2f".format(it.second)}" }
        }
        assertTrue(failures.toString(), failures.isEmpty())
        global.bert.widget.theme.BERTThemePack.entries.forEach { t ->
            global.bert.widget.theme.BERTThemeStore(context).save(t)
            seedActivity(LONG_POST, tournament = true)
            val issues = problems(render(BERTDailyWidget(), 360, 200, "bert-theme-${t.id}"), multiline = setOf(LONG_POST))
            assertTrue("${t.id}: $issues", issues.isEmpty())
        }
    }

    /**
     * My Bert: the holder's NFT replaces the photo as the portrait (whole, square), the banner and the avatar, at every
     * size, with the same fit, fill and art-share rules as the photo; and with "Match my Bert" its palette.
     */
    @Test fun bertWidgetShowsTheHoldersBertAtEverySize() {
        val picture = android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mybert/bert-2206.png"))
        global.bert.widget.data.MyBertStore(context).saveChosen(global.bert.widget.data.MyBert("Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc", 2206, "Forest", staked = true), picture)
        try {
            val failures = mutableMapOf<String, List<String>>()
            // The same posts and tournament states as bertFillReport, with and without "Match my Bert".
            for (match in listOf(false, true)) for ((kind, post) in mapOf("short" to SHORT_POST, "long" to LONG_POST)) for (tournament in listOf(true, false)) {
                global.bert.widget.theme.BERTThemeStore(context).matchMyBert = match
                seedActivity(post, tournament)
                for ((w, h) in BERT_SIZES) {
                    val tag = "${if (match) "match-" else ""}$kind${if (tournament) "-event" else ""}-${w}x$h"
                    val r = render(BERTDailyWidget(), w, h, "bert-mybert-$tag")
                    var art = 0; var photo = 0
                    fun walk(v: View) {
                        if (v is android.widget.ImageView && v.visibility == View.VISIBLE) {
                            if (v.contentDescription == "Your Bert #2206") art = maxOf(art, v.width * v.height)
                            if (v.contentDescription == "Bert") photo++
                        }
                        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
                    }
                    walk(r.root)
                    val share = art.toFloat() / (r.root.width * r.root.height)
                    failures[tag] = problems(r, multiline = setOf(post)) + voids(r) + listOfNotNull(
                        "no holder's Bert".takeIf { art == 0 }, "stock photo still shown".takeIf { photo > 0 },
                        "Bert art only ${(share * 100).toInt()}%".takeIf { (w >= 200 || h >= 150) && share < 0.18f })
                }
            }
            println("WIDGET REPORT my bert: $failures")
            assertTrue(failures.filterValues { it.isNotEmpty() }.toString(), failures.values.all { it.isEmpty() })
        } finally {
            global.bert.widget.data.MyBertStore(context).forget()
        }
    }

    private fun seedActivity(post: String, tournament: Boolean, mood: String = "giddy") {
        val now = System.currentTimeMillis()
        val status = JSONObject().put("updated_at", now / 1000 - 60).put("mood", mood).put("latest_post", post)
        if (tournament) status.put("flappy", JSONObject().put("name", "The Autumn Arc").put("ends_at", Instant.ofEpochMilli(now + 30 * 3_600_000L).toString())
            .put("status", "live").put("pool", 250).put("top", org.json.JSONArray().put(JSONObject().put("rank", 1).put("name", "LamexCrypt").put("score", 143).put("prize", 100))))
        context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit().putString("last_valid_activity", status.toString()).commit()
    }

    /** Fit, emptiness and Bert's art at every size for a short and a long post, with and without the tournament. */
    private fun bertFillReport(prefix: String): Map<String, List<String>> =
        mapOf("short" to SHORT_POST, "long" to LONG_POST).flatMap { (kind, post) ->
            listOf(true, false).flatMap { tournament ->
                seedActivity(post, tournament)
                BERT_SIZES.map { (w, h) ->
                    val tag = "$kind${if (tournament) "-event" else ""}-${w}x$h"
                    val r = render(BERTDailyWidget(), w, h, "bert-$prefix-$tag")
                    val art = bertArtShare(r)
                    tag to problems(r, multiline = setOf(post)) +
                        voids(r) + listOfNotNull(
                            "Bert art only ${(art * 100).toInt()}%".takeIf { (w >= 200 || h >= 150) && art < 0.18f })
                }
            }
        }.toMap()

    private companion object {
        /** Minimum 2x1 up to a large 4x2; 380x190 is roughly the S25's 4x2 cell, 180x190 its 2x2. */
        const val SHORT_POST = "the blanket settled heavier on its own. like the season finally taught it how to hold still."
        const val LONG_POST = "woofmornin. the ladybug holds the screen door like the wind can wait its turn, and the mayor is in no hurry either. " +
            "somewhere a kettle is thinking about it. the pack has opinions about breakfast and none of them are quiet."
        val BERT_SIZES = listOf(120 to 110, 180 to 180, 180 to 190, 150 to 150, 250 to 110, 300 to 130, 320 to 150, 360 to 200, 380 to 190, 400 to 220)
    }
}
