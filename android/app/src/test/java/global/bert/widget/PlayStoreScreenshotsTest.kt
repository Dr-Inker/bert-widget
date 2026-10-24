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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
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
 * Gameplay and home-screen widget shots come from a real device, not from here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayStoreScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.of("Europe/London")
    // Monday 12 October 2026, 09:10 in London: a weekday morning, so Home shows Bert's morning greeting.
    private val now = LocalDateTime.of(2026, 10, 12, 9, 10).atZone(zone).toInstant().toEpochMilli()
    private val originalZone = TimeZone.getDefault()
    private val output = File("build/outputs/play")

    @Before fun localClock() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        output.listFiles { file -> file.extension == "png" }?.forEach { it.delete() } // no stale shots from an older set
    }

    @After fun restoreClock() { TimeZone.setDefault(originalZone) }

    private fun shot(name: String, caption: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val screen = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(screen)) }
        check(screen.width == 1080 && screen.height == 1920) { "Screens render at 1080x1920, got ${screen.width}x${screen.height}" }
        val framed = framed(screen, caption)
        check(framed.width * 16 == framed.height * 9 && framed.width == 1080) { "Play screenshots must be 1080x1920" }
        File(output, "$name.png").apply { parentFile?.mkdirs() }.outputStream().use { framed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The bare screen as well, for the website's Workshop prints (berthalla.io).
        File(output, "screen/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    /** Scrolls the screen so [node]'s top sits just under the top of its scrolling area. */
    private fun scrollToTop(node: SemanticsNodeInteraction) {
        val scroller = node.onAncestors().filter(hasScrollAction()).onFirst()
        val target = node.fetchSemanticsNode().boundsInRoot.top
        val viewport = scroller.fetchSemanticsNode().boundsInRoot.top
        val margin = 16f * compose.activity.resources.displayMetrics.density
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
        // Top of Market: the price, then the activity card, never under the pinned section tabs.
        shot("3-market", CAPTIONS.getValue("3-market"))
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        scrollToTop(compose.onNodeWithText("through the gate"))
        shot("4-play", CAPTIONS.getValue("4-play"))
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Alerts", useUnmergedTree = true).performClick()
        compose.onNode(hasContentDescription("Tournament ending") and isToggleable()).assertIsOn()
        compose.onAllNodesWithText("Notifications are off for BERT").assertCountEquals(0)
        // Lead with the switches themselves rather than the explanation above them.
        scrollToTop(compose.onNodeWithText("Tournament ending", useUnmergedTree = true))
        shot("5-alerts", CAPTIONS.getValue("5-alerts"))
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
        val CAPTIONS = linkedMapOf(
            "1-today" to "Bert’s day, every day",
            "2-create" to "Make it a card",
            "3-market" to "Know the market",
            "4-play" to "Play the Lost Trail",
            "5-alerts" to "Alerts that don’t nag",
        )
    }
}
