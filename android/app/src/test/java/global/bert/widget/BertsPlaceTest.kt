package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** Bert's day on Home: where he is by the clock, the pat, the woken window, the chalkboard price. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BertsPlaceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun at(hour: Int): Long = ZonedDateTime.now(ZoneId.systemDefault()).withHour(hour).withMinute(10).toInstant().toEpochMilli()

    private fun place(hour: Int, mood: String? = "content") {
        val now = at(hour)
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(now, mood, 0.0154, newNotes = 0, openBoard = {}, openMarket = {})
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theRoutineFollowsTheClock() {
        assertEquals(BertSlot.GATE, BertSlot.at(6)); assertEquals(BertSlot.GATE, BertSlot.at(8))
        assertEquals(BertSlot.ROUNDS, BertSlot.at(9)); assertEquals(BertSlot.NAP, BertSlot.at(12))
        assertEquals(BertSlot.POST, BertSlot.at(15)); assertEquals(BertSlot.EVENING, BertSlot.at(18))
        assertEquals(BertSlot.BED, BertSlot.at(21)); assertEquals(BertSlot.BED, BertSlot.at(0)); assertEquals(BertSlot.BED, BertSlot.at(5))
        assertEquals("1.54¢", chalkPrice(0.0154)); assertEquals("12.3¢", chalkPrice(0.1234)); assertEquals("$1.23", chalkPrice(1.2345))
        assertEquals("0.042¢", chalkPrice(0.00042))
        // Every price form fits on the slate (32 art px wide).
        for (p in listOf(0.0154, 0.1234, 1.2345, 0.00042, 0.0999, 12.34)) assert(ChalkFont.width(chalkPrice(p)) <= 32) { chalkPrice(p) }
    }

    @Test fun everySlotRenders() {
        var hour by androidx.compose.runtime.mutableIntStateOf(7)
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(at(hour), "content", 0.0154, newNotes = 0, openBoard = {}, openMarket = {})
                }
            }
        }
        for ((h, name) in listOf(7 to "gate", 10 to "rounds", 13 to "nap", 16 to "post", 19 to "evening", 23 to "bed")) {
            compose.runOnIdle { hour = h }
            compose.onNodeWithTag("berts-place").assertExists()
            compose.onNodeWithTag("place-tag").assertContentDescriptionEquals("Chalkboard: BERT \$0.0154")
            compose.onNodeWithTag("place-line").assertIsDisplayed()
            if (h == 23) compose.onNodeWithTag("place-bert").assertDoesNotExist() else compose.onNodeWithTag("place-bert").assertHasClickAction()
            capture("place-$name")
        }
    }

    @Test fun aPatTipsTheHatAndHeSaysSo() {
        place(10)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-bert").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("place-line").assertTextContains("", substring = true)
        val said = compose.onNodeWithTag("place-line").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assert(said in BertLines.pats) { said }
        capture("place-pat")
        // Five in a row and he has had enough.
        repeat(4) { compose.onNodeWithTag("place-bert").performClick(); compose.mainClock.advanceTimeBy(100) }
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.TOO_MANY)
        compose.mainClock.autoAdvance = true
    }

    @Test fun atNightTheWindowGoesOutWhenYouKnock() {
        place(23)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-window").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.WOKEN)
        compose.onNodeWithTag("place-window").assertContentDescriptionContains("dark", substring = true)
        capture("place-woken")
        compose.mainClock.autoAdvance = true
    }

    @Test fun patsWhereHeStandsInFrontOfTheGate() {
        // At the gate in the morning and waiting on the post in the afternoon he stands in front of the gateway; a tap on
        // him is a pat, and the gate's uncovered post still goes through.
        var trips = 0
        var hour by androidx.compose.runtime.mutableIntStateOf(7)
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(at(hour), "content", 0.0154, newNotes = 0, openBoard = {}, openMarket = {}, openTrail = { trips++ })
                }
            }
        }
        for (h in listOf(7, 16)) {
            compose.runOnIdle { hour = h }
            compose.onNodeWithTag("place-bert").performClick()
            compose.waitForIdle()
            assertEquals("tapping Bert at $h:00 went through the gate", 0, trips)
            val said = compose.onNodeWithTag("place-line").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
            assert(said in BertLines.pats || said == BertLines.TOO_MANY) { said }
        }
        compose.onNodeWithTag("place-gate").performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, height / 2f)) }
        compose.runOnIdle { assertEquals(1, trips) }
    }

    @Test fun napTimeStaysAsleep() {
        place(13)
        compose.onNodeWithTag("place-bert").performClick()
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.NAPPING)
    }
}

/** The "Bert's garden" widget picture: crisp, filled, with Bert drawn where his routine has him. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BertsPlaceWidgetTest {
    @Test fun theWidgetPictureFillsEverySizeAndShowsBertByDay() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        for ((slot, size) in listOf(BertSlot.GATE to (450 to 450), BertSlot.ROUNDS to (1000 to 480), BertSlot.NAP to (450 to 450),
            BertSlot.EVENING to (1000 to 480), BertSlot.BED to (450 to 450), BertSlot.GATE to (450 to 1000), BertSlot.POST to (450 to 1000))) {
            val bitmap = global.bert.widget.widget.PlaceStamp.render(context, slot, "content", size.first, size.second)
            // Within one art pixel of the widget's shape, so Glance's crop loses at most a sliver.
            val aspect = bitmap.width.toFloat() / bitmap.height; val want = size.first.toFloat() / size.second
            assert(kotlin.math.abs(aspect - want) / want < 0.12f) { "$slot $aspect vs $want" }
            assert(bitmap.width >= size.first / 2) { "$slot too small: ${bitmap.width}" }
            if (slot.bertX != null) {
                // He is in the picture: the red of his hat (F/10 palette, in any light) shows somewhere in it.
                val px = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                // The scene has a few dozen red pixels of its own (by the noticeboard); his hat and coat trim are far more.
                val red = px.count { val r = it shr 16 and 255; val g = it shr 8 and 255; val b = it and 255; r > 60 && r > 2 * g && r > 2 * b }
                assert(red > px.size / 400) { "$slot $size: no Bert ($red red of ${px.size})" }
            }
            File("build/outputs/host-ui/widget-place-${slot.name.lowercase()}-${size.first}x${size.second}.png").apply { parentFile?.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
