package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
import global.bert.widget.widget.BERTDailyWidget
import global.bert.widget.widget.BERTMarketWidget
import global.bert.widget.widget.BERTPlaceWidget
import global.bert.widget.widget.BERTWidget
import global.bert.widget.widget.renderWidgetOnHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Google Play phone screenshots: 1080x1920 (9:16), each a short caption band above a scaled app screen.
 * Review fixtures only: no live quotes, no one's holdings, and fictional player names.
 * Gameplay shots come from a real device, not from here; the home-screen shot (0-widgets) is composed from the real widget renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayStoreScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val graphics = NativeGraphicsOnTestThread()
    private val zone = ZoneId.of("Europe/London")
    // Monday 12 October 2026, 09:10 in London: a weekday morning, so Home shows Bert's morning greeting.
    private val now = LocalDateTime.of(2026, 10, 12, 9, 10).atZone(zone).toInstant().toEpochMilli()
    private val originalZone = TimeZone.getDefault()
    private val output = File("build/outputs/play")

    @Before fun localClock() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After fun restoreClock() { TimeZone.setDefault(originalZone) }

    /**
     * The app is rendered taller than the store frame (1080x2400), then cut down to a 1080x1920 phone screen at a section
     * boundary: the first full-width row of plain page colour at or above where the navigation bar would start, and
     * through no text, chart or picture. The page colour fills the rest, and the navigation bar goes back at the
     * bottom. So no shot slices a label, a pill or the chart in half (critique 2026-11-12 ART-6).
     */
    private fun shot(name: String, caption: String, cardTallerThanTheScreen: Boolean = false) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val tall = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(tall)) }
        check(tall.width == SCREEN_W && tall.height >= SCREEN_H + 400) { "Screens render taller than the frame, got ${tall.width}x${tall.height}" }
        val navTop = navigationTop().toInt()
        val limit = SCREEN_H - (tall.height - navTop)
        val said = described()
        val (cut, inCard) = sectionBoundary(tall, limit, said)
        val crossed = said.filter { (_, r) -> r.top < cut - 1 && r.bottom > cut + 1 }
        assertTrue("$name: the crop at y=$cut cuts through ${crossed.map { it.first }}", crossed.isEmpty())
        assertTrue("$name: the crop keeps most of the screen (y=$cut of $limit)", cut >= limit * 3 / 4)
        // Nothing is half hidden under the pinned header either: the scrolling area's top edge crosses no text.
        val viewportTop = scrollingTop()
        val underHeader = said.filter { (_, r) -> r.top < viewportTop - 1 && r.bottom > viewportTop + 1 }
        assertTrue("$name: the scrolling area's top at y=$viewportTop cuts through ${underHeader.map { it.first }}", underHeader.isEmpty())
        // Cut between sections, not through a card: only a card taller than the whole screen may be cut, and it fades.
        assertTrue("$name: the crop at y=$cut falls inside a card, not in a gap between sections", !inCard || cardTallerThanTheScreen)
        // Nor does the bottom edge of a card scrolled away above peek out under the header with nothing in it.
        val sliver = sliverUnderHeader(tall, viewportTop.toInt(), said)
        assertEquals("$name: a ${sliver}px strip of a card's edge shows under the header", 0, sliver)
        val screen = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888)
        Canvas(screen).apply {
            val page = tall.getPixel(0, cut)
            drawColor(page)
            drawBitmap(tall, Rect(0, 0, SCREEN_W, cut), Rect(0, 0, SCREEN_W, cut), null)
            // A card cut short fades out into the page over its last blank rows, so it reads as "more below", not a slice.
            // It starts below the last thing above the cut that says something, so it never dims a word.
            val fadeTop = maxOf(cut - FADE, said.filter { (_, r) -> r.bottom <= cut + 1 }.maxOfOrNull { it.second.bottom } ?: 0f)
            if (inCard) drawRect(0f, fadeTop, SCREEN_W.toFloat(), cut.toFloat(), Paint().apply {
                shader = LinearGradient(0f, fadeTop, 0f, cut.toFloat(), page and 0x00FFFFFF, page, Shader.TileMode.CLAMP)
            })
            drawBitmap(tall, Rect(0, navTop, SCREEN_W, tall.height), Rect(0, SCREEN_H - (tall.height - navTop), SCREEN_W, SCREEN_H), null)
        }
        save(name, screen, caption)
    }

    private fun save(name: String, screen: Bitmap, caption: String) {
        check(screen.width == SCREEN_W && screen.height == SCREEN_H) { "Screens are 1080x1920, got ${screen.width}x${screen.height}" }
        val framed = framed(screen, caption)
        check(framed.width * 16 == framed.height * 9 && framed.width == 1080) { "Play screenshots must be 1080x1920" }
        File(output, "$name.png").apply { parentFile?.mkdirs() }.outputStream().use { framed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The bare screen as well, for the website's Workshop prints (berthalla.io).
        File(output, "screen/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Where to cut, at or above [limit], crossing nothing that says something: the lowest row of plain page colour right
     * across the screen (a gap between sections) in the bottom quarter; failing that, the lowest blank row inside a card
     * (page and card colour only), which is true of Market, whose chart card is taller than the space. [Cut.inCard] then.
     */
    private data class Cut(val y: Int, val inCard: Boolean)

    private fun sectionBoundary(tall: Bitmap, limit: Int, said: List<Pair<String, RectF>>): Cut {
        val row = IntArray(tall.width)
        fun clear(y: Int) = said.none { (_, r) -> r.top < y - 1 && r.bottom > y + 1 }
        fun colours(y: Int): List<Int> {
            tall.getPixels(row, 0, tall.width, 0, y, tall.width, 1)
            return row.groupBy { it }.values.map { it.size }.sortedDescending()
        }
        for (y in limit downTo limit * 3 / 4) if (colours(y).size == 1 && clear(y)) return Cut(y, false)
        // A card's straight sides can leave a few blended pixels; anything more is a picture or a line.
        for (y in limit downTo limit * 3 / 4) if (colours(y).drop(2).sum() <= 6 && clear(y)) return Cut(y, true)
        return Cut(limit, false) // none: the assertions in shot() say so
    }

    /**
     * How many rows of a card's edge, with nothing in them, show just under the scrolling area's top: the last of a card
     * scrolled away above. Past a few plain rows, rows that are not plain page and end in plain page again before any
     * words are such a strip; a card whose words follow, or a picture taller than [SLIVER_DP], is content.
     */
    private fun sliverUnderHeader(tall: Bitmap, top: Int, said: List<Pair<String, RectF>>): Int {
        val density = compose.activity.resources.displayMetrics.density
        val row = IntArray(tall.width)
        fun plain(y: Int): Boolean { tall.getPixels(row, 0, tall.width, 0, y, tall.width, 1); return row.all { it == row[0] } }
        fun words(y: Int) = said.any { (_, r) -> r.top <= y && r.bottom > y }
        var y = top + 1
        val leading = y + (24 * density).toInt()
        while (y < leading && plain(y)) y++
        val start = y
        while (y < tall.height && !plain(y)) { if (words(y)) return 0; y++ }
        return (y - start).takeIf { it in 1..(SLIVER_DP * density).toInt() } ?: 0
    }

    /** The top of the screen's main vertical scrolling area (below any pinned header), in the window. */
    private fun scrollingTop(): Float = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNodes().maxByOrNull { it.boundsInWindow.height }?.boundsInWindow?.top ?: 0f

    /**
     * Bert on a home screen: the four real widgets, rendered by the same host harness as their render tests and launcher
     * previews (renderWidgetOnHost), laid on a plain wallpaper with a status-bar clock and a gesture handle. The
     * widgets read the phone's real clock, so the zone is set to wherever it is now mid-morning: Bert is out on his
     * rounds in the garden, as in the morning app shots.
     */
    private fun widgetShot(name: String, caption: String) {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val realNow = System.currentTimeMillis()
        var offset = 10 - Instant.ofEpochMilli(realNow).atZone(java.time.ZoneOffset.UTC).hour
        if (offset < -12) offset += 24
        if (offset > 14) offset -= 24
        val morning = java.time.ZoneOffset.ofHours(offset)
        TimeZone.setDefault(TimeZone.getTimeZone(morning))
        seedWidgetFixtures(context, realNow)
        val gap = 48
        val wide = SCREEN_W - 2 * gap
        val small = (wide - gap) / 2
        val density = context.resources.displayMetrics.density
        fun dp(px: Int) = (px / density).toInt()
        val top = 200
        val placed = listOf(
            Triple("Bert’s day", renderWidgetOnHost(context, BERTDailyWidget(), dp(wide), 170).bitmap, android.graphics.Point(gap, top)),
            Triple("BERT price", renderWidgetOnHost(context, BERTWidget(), dp(small), dp(small)).bitmap, android.graphics.Point(gap, top + 510 + gap)),
            Triple("Bert’s garden", renderWidgetOnHost(context, BERTPlaceWidget(), dp(small), dp(small)).bitmap, android.graphics.Point(gap + small + gap, top + 510 + gap)),
            Triple("BERT market", renderWidgetOnHost(context, BERTMarketWidget(), dp(wide), 150).bitmap, android.graphics.Point(gap, top + 510 + gap + small + gap)),
        )
        val screen = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        // A plain wallpaper: deep violet to near black, the brand kit's own ends.
        canvas.drawRect(0f, 0f, SCREEN_W.toFloat(), SCREEN_H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, SCREEN_H.toFloat(), 0xFF3A2160.toInt(), NAVY, Shader.TileMode.CLAMP)
        })
        val clock = java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(realNow), morning)
        canvas.drawText("%d:%02d".format(clock.hour, clock.minute), 54f, 84f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt(); textSize = 40f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        // The gesture handle: with the clock, what makes a wallpaper read as a home screen.
        canvas.drawRoundRect(RectF(SCREEN_W / 2f - 108f, 1884f, SCREEN_W / 2f + 108f, 1896f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt() })
        placed.forEach { (_, bitmap, at) -> canvas.drawBitmap(bitmap, at.x.toFloat(), at.y.toFloat(), null) }
        // The shot carries the widgets exactly as they render: every opaque pixel of each is on the screen where it was put.
        placed.forEach { (label, bitmap, at) ->
            assertTrue("$label: rendered at ${bitmap.width}x${bitmap.height}", bitmap.width >= 300 && bitmap.height >= 300)
            assertTrue("$label: inside the screen", at.x + bitmap.width <= SCREEN_W && at.y + bitmap.height < SCREEN_H - 120)
            var opaque = 0; var same = 0
            for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) != 255) continue
                opaque++
                if (screen.getPixel(at.x + x, at.y + y) == pixel) same++
            }
            assertTrue("$label: the widget drew something ($opaque opaque samples)", opaque > bitmap.width * bitmap.height / 8)
            assertEquals("$label: every opaque pixel is the widget's own", opaque, same)
            val distinct = (0 until bitmap.width step 9).flatMap { x -> (0 until bitmap.height step 9).map { y -> bitmap.getPixel(x, y) } }.toSet()
            assertTrue("$label: not a blank panel (${distinct.size} colours)", distinct.size > 12)
        }
        val a = placed.map { (_, b, at) -> Rect(at.x, at.y, at.x + b.width, at.y + b.height) }
        for (i in a.indices) for (j in i + 1 until a.size) assertFalse("widgets ${placed[i].first} and ${placed[j].first} overlap", Rect.intersects(a[i], a[j]))
        save(name, screen, caption)
    }

    /** Invented widget data matching the app shots: the same price, change, feed and fictional tournament, on the real clock. */
    private fun seedWidgetFixtures(context: android.content.Context, realNow: Long) {
        val asset = org.json.JSONObject().put("chain", "solana").put("mint", "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump")
        val fixture = org.json.JSONObject(requireNotNull(javaClass.classLoader).getResource("history-sample.json").readText()).getJSONArray("minutesBeforeNow")
        val points = org.json.JSONArray()
        for (i in 0 until fixture.length()) points.put(org.json.JSONArray().put(realNow / 1000 - fixture.getJSONArray(i).getLong(0) * 60).put(fixture.getJSONArray(i).getDouble(1)))
        val price = fixture.getJSONArray(fixture.length() - 1).getDouble(1)
        val envelope = org.json.JSONObject().put("asset", asset)
            .put("quote", org.json.JSONObject().put("priceUsd", price).put("change24hPct", 1.63).put("marketCapUsd", 15_067_640.0)
                .put("volume24hUsd", 136_801.0).put("liquidityUsd", 1_178_452.0))
            .put("source", org.json.JSONObject().put("name", "dexscreener").put("pairAddress", "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY").put("dex", "raydium")
                .put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy").put("observedAt", Instant.ofEpochMilli(realNow - 30_000).toString()))
            .put("meta", org.json.JSONObject().put("freshness", "fresh"))
        context.getSharedPreferences("bert_quote", android.content.Context.MODE_PRIVATE).edit().putString("last_valid_quote", envelope.toString()).commit()
        context.getSharedPreferences("bert_market_history", android.content.Context.MODE_PRIVATE).edit().putString("last_valid_history", org.json.JSONObject()
            .put("asset", asset).put("source", org.json.JSONObject().put("name", "geckoterminal").put("intervalSeconds", 300)).put("points", points).toString()).commit()
        val top = org.json.JSONArray()
        FICTIONAL_PLAYERS.forEachIndexed { index, (player, score) ->
            top.put(org.json.JSONObject().put("rank", index + 1).put("name", player).put("score", score).put("prize", listOf(100, 60, 40, 30, 20)[index]))
        }
        val status = org.json.JSONObject().put("updated_at", realNow / 1000 - 60).put("mood", "content")
            .put("latest_post", "woofmornin. the ladybug holds the screen door like the wind can wait its turn.")
            .put("flappy", org.json.JSONObject().put("name", "The Autumn Arc").put("ends_at", Instant.ofEpochMilli(realNow + 31 * 3_600_000L).toString())
                .put("status", "live").put("pool", 250).put("top", top))
        context.getSharedPreferences("bert_activity", android.content.Context.MODE_PRIVATE).edit().putString("last_valid_activity", status.toString()).commit()
    }

    /** The caption band sits above the app screen, which is scaled down (never cropped) to make room for it. */
    private fun framed(screen: Bitmap, caption: String): Bitmap {
        val (width, height, band, bottom) = listOf(1080, 1920, 300, 44)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(), NAVY, PANEL_STRONG, Shader.TileMode.CLAMP)
        })
        val bold = Typeface.createFromFile(File("src/main/res/font/outfit_bold.ttf"))
        canvas.drawRoundRect(RectF(width / 2f - 36f, 92f, width / 2f + 36f, 100f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE })
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = CREAM; typeface = bold; textSize = 76f; letterSpacing = -0.01f }
        val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, text, width - 140).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        check(layout.lineCount == 1) { "\"$caption\" must fit on one line" }
        canvas.save(); canvas.translate(70f, 128f + (band - 128f - layout.height) / 2f - 8f); layout.draw(canvas); canvas.restore()
        val scale = (height - band - bottom).toFloat() / screen.height
        val w = screen.width * scale; val left = (width - w) / 2f
        val frame = RectF(left, band.toFloat(), left + w, (height - bottom).toFloat())
        canvas.drawRoundRect(RectF(frame.left - 4f, frame.top - 4f, frame.right + 4f, frame.bottom + 4f), 52f, 52f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFF5DF })
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(frame, 48f, 48f, Path.Direction.CW) })
        canvas.drawBitmap(screen, Rect(0, 0, screen.width, screen.height), frame, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        return out
    }

    /**
     * Every node that says something (text, or a description such as the chart's), with its bounds in the window, and
     * every tappable group that says something, so a cut never parts a heading from the card it introduces.
     */
    private fun described(): List<Pair<String, RectF>> {
        val says = SemanticsMatcher("has text or a description") {
            SemanticsProperties.Text in it.config || SemanticsProperties.ContentDescription in it.config
        }
        // Both trees: each label on its own, and each tappable group (a label, its picture and its title) as one whole.
        return listOf(true, false).flatMap { compose.onAllNodes(says, useUnmergedTree = it).fetchSemanticsNodes() }.map { node ->
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            // Unclipped: a label half under the navigation bar is clipped to the part on show, which would hide the cut.
            val at = node.positionInWindow
            label.orEmpty() to RectF(at.x, at.y, at.x + node.size.width, at.y + node.size.height)
        }.filter { it.second.height() > 0f }
    }

    /** The top of the bottom navigation bar: its four destinations, in the window. */
    private fun navigationTop(): Float = BERTDestination.entries.minOf { destination ->
        compose.onAllNodes(hasText(destination.label) and isSelectable()).fetchSemanticsNodes().maxOf { it.boundsInWindow.top }
    }

    /** Scrolls the screen so [node]'s top sits just under the top of its scrolling area. */
    private fun scrollToTop(node: SemanticsNodeInteraction, marginDp: Float = 16f) {
        val scroller = node.onAncestors().filter(hasScrollAction()).onFirst()
        val target = node.fetchSemanticsNode().boundsInRoot.top
        val viewport = scroller.fetchSemanticsNode().boundsInRoot.top
        val margin = marginDp * compose.activity.resources.displayMetrics.density
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target - viewport - margin) }
        compose.waitForIdle()
    }

    /** berthalla.io/app/'s four tab pictures (720x1600, the size the page lays out), at the same Monday morning. */
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun websiteScreens() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        BERTOnboarding.hide(compose.activity.applicationContext)
        global.bert.widget.data.BERTHomeVisits(compose.activity.applicationContext).meetBertDismissed = true
        val fixture = org.json.JSONObject(requireNotNull(javaClass.classLoader).getResource("history-sample.json").readText()).getJSONArray("minutesBeforeNow")
        val history = (0 until fixture.length()).map { BERTPriceSample(now - fixture.getJSONArray(it).getLong(0) * 60_000, fixture.getJSONArray(it).getDouble(1)) }
        val quote = QuoteState.Available(BERTQuote(history.last().priceUsd, 1.63, 15_067_640.0, 136_801.0, 1_178_452.0, "fresh", now - 30_000,
            "dexscreener", "raydium", "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            change1hPct = 0.42, change6hPct = -0.87, buys1h = 46, sells1h = 31, buys24h = 1_112, sells24h = 968))
        val activity = ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the ladybug holds the screen door like the wind can wait its turn.", "content", null))
        compose.setContent { BERTTheme { BERTApp(quote, activity, history, BERTPosition(), now, false, false, {}, {}, {}) } }
        fun save(name: String) {
            compose.waitForIdle()
            val view = compose.activity.window.decorView
            val screen = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(screen)) }
            check(screen.width == 720 && screen.height == 1600) { "Website screens are 720x1600, got ${screen.width}x${screen.height}" }
            File(output, "screen/web-$name.png").apply { parentFile?.mkdirs() }.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        save("home")
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        save("play")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Caption card preview:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        save("create")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        save("tools")
    }

    @Test fun phoneScreenshots() {
        // No stale shots from an older set. Only this test writes the framed set, so only it clears it: a shared
        // @Before let websiteScreens, run after this one, delete the listing shots (critique 2026-11-04, 1.4.1).
        output.listFiles { file -> file.extension == "png" }?.forEach { it.delete() }
        assertEquals(DayOfWeek.MONDAY, Instant.ofEpochMilli(now).atZone(zone).dayOfWeek)
        assertEquals("WOOFmornin, pack.", greetingAt(now))
        CAPTIONS.values.forEach { caption ->
            assertTrue("$caption: five words at most", caption.split(' ').size <= 5)
            assertFalse("$caption: no exclamation marks", '!' in caption)
        }
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        BERTOnboarding.hide(compose.activity.applicationContext) // the store shows the product, not a first-run checklist
        global.bert.widget.data.BERTHomeVisits(compose.activity.applicationContext).meetBertDismissed = true // the store shows Home as people use it, not the one-time intro
        // Alerts are shown working: two switched on and a big-move threshold chosen.
        BERTAlertStore(compose.activity.applicationContext).save(AlertSettings(tournament = true, dispatch = true, priceAbove = 0.0175, bigMovePct = 20))
        // As on a phone where the person allowed BERT's notifications when switching those alerts on.
        org.robolectric.Shadows.shadowOf(compose.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val fixture = org.json.JSONObject(requireNotNull(javaClass.classLoader).getResource("history-sample.json").readText()).getJSONArray("minutesBeforeNow")
        val history = (0 until fixture.length()).map { BERTPriceSample(now - fixture.getJSONArray(it).getLong(0) * 60_000, fixture.getJSONArray(it).getDouble(1)) }
        val quote = QuoteState.Available(BERTQuote(history.last().priceUsd, 1.63, 15_067_640.0, 136_801.0, 1_178_452.0, "fresh", now - 30_000,
            "dexscreener", "raydium", "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
            change1hPct = 0.42, change6hPct = -0.87, buys1h = 46, sells1h = 31, buys24h = 1_112, sells24h = 968))
        val event = BERTEvent("The Autumn Arc", now + (60 * 24 + 7) * 3_600_000L, "live", pool = 250,
            standings = FICTIONAL_PLAYERS.mapIndexed { index, (name, score) -> BERTStanding(index + 1, name, score, listOf(100, 60, 40, 30, 20)[index]) })
        val activity = ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the ladybug holds the screen door like the wind can wait its turn.", "content", event))
        compose.setContent {
            BERTTheme {
                var position by remember { mutableStateOf(BERTPosition()) }
                BERTApp(quote, activity, history, position, now, false, false, {}, {}, { position = it })
            }
        }
        shot("1-today", CAPTIONS.getValue("1-today"))
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNode(hasContentDescription(CardArt.PHOTO.description)).assertIsSelected()
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Caption card preview:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("2-create", CAPTIONS.getValue("2-create"))
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        // The price history card from its top, so its range pills and the whole chart are on show (the price card above
        // and the chart card do not both fit a 9:16 phone at this size). The label sits 18dp into its card, and cards are
        // 18dp apart: 30dp puts the card's top 12dp under the tabs and the price card's last edge out of sight.
        scrollToTop(compose.onNodeWithText("PRICE HISTORY", useUnmergedTree = true), marginDp = 30f)
        // The chart card, with its source line, is taller than the space under the tabs: it alone may be cut, and fades.
        shot("3-market", CAPTIONS.getValue("3-market"), cardTallerThanTheScreen = true)
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        // At 2x the picture is 240dp tall, so the Flappy card below ends within a few dp of the cut: the "through the
        // gate" line starts 4dp under the top, not the usual 16dp, and the whole card stays in the shot.
        scrollToTop(compose.onNodeWithText("through the gate"), marginDp = 4f)
        shot("4-play", CAPTIONS.getValue("4-play"))
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Alerts", useUnmergedTree = true).performClick()
        compose.onNode(hasContentDescription("Tournament ending") and isToggleable()).assertIsOn()
        compose.onAllNodesWithText("Notifications are off for BERT").assertCountEquals(0)
        // The whole price-alerts card, from its top to its Save button: the levels people set, not a card cut flat.
        // Its label sits 18dp into the card, 30dp leaves the card above (the switches) out of sight.
        scrollToTop(compose.onNodeWithText("PRICE ALERTS", useUnmergedTree = true), marginDp = 30f)
        compose.onNodeWithText("Save price alerts").assertIsDisplayed()
        shot("5-alerts", CAPTIONS.getValue("5-alerts"))
        widgetShot("0-widgets", CAPTIONS.getValue("0-widgets"))
        // Exactly the listing set, in order.
        val shots = output.listFiles { file -> file.extension == "png" }.orEmpty().map { it.name }.sorted()
        assertEquals(CAPTIONS.keys.map { "$it.png" }.sorted(), shots)
    }

    private companion object {
        // The brand kit (0.11): bg-deep to the strong surface, white captions in Outfit Bold, and the kit's display
        // violet (#992EFF, "for display type and social headlines") as the small rule.
        const val NAVY = 0xFF100B16.toInt()
        const val PANEL_STRONG = 0xFF302042.toInt()
        const val CREAM = 0xFFFFFFFF.toInt()
        const val ORANGE = 0xFF992EFF.toInt()
        val FICTIONAL_PLAYERS = listOf("PawPrint" to 143, "Hollow_Hopper" to 115, "SirBarksALot" to 114, "MayorFan" to 112, "LanternLad" to 108)
        const val SCREEN_W = 1080
        const val SCREEN_H = 1920
        /** A strip of card edge at most this tall (dp) under the header is a stray edge, not content. */
        const val SLIVER_DP = 30
        /** At most this many rows of a card cut short fade into the page. */
        const val FADE = 36f
        val CAPTIONS = linkedMapOf(
            "0-widgets" to "Bert on your home screen",
            "1-today" to "Bert’s day, every day",
            "2-create" to "Make it a card",
            "3-market" to "Know the market",
            "4-play" to "Play the Lost Trail",
            "5-alerts" to "Alerts that don’t nag",
        )
    }
}
