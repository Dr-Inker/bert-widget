package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** 0.14: Bert's line as speech, and the newcomer's hints and what they have found so far. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BertSaysTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun at(hour: Int): Long = ZonedDateTime.now(ZoneId.systemDefault()).withHour(hour).withMinute(10).toInstant().toEpochMilli()
    private val today: LocalDate get() = LocalDate.now(ZoneId.systemDefault())

    private fun place(hour: Int, fontScale: Float = 1f) {
        val now = at(hour)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BERTTheme {
                    Column(Modifier.fillMaxSize().background(Navy)) {
                        BertsPlace(now, "content", 0.0154, newNotes = 0, openBoard = {}, openMarket = {}, openTrail = {})
                    }
                }
            }
        }
    }

    /** Someone who has been coming round for a while: past the first days, and has patted him. */
    private fun regular() = PlaceDiscovery(compose.activity).apply {
        for (d in FIRST_DAYS downTo 1) record(today.minusDays(d.toLong()))
        patted()
    }

    private fun said(): String = compose.onNodeWithTag("place-line").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    /** The spots a newcomer has still to find (Bert hints at them); none once the first days are over. */
    private fun untried(): Set<PlaceSpot> = PlaceDiscovery(compose.activity).record(today).let { if (it.showingRound) PlaceSpot.entries.toSet() - it.tapped else emptySet() }

    @Test fun theLineIsBertSpeaking() {
        regular()
        place(10)
        val line = BertLines.of(BertSlot.ROUNDS, today)
        compose.onNodeWithTag("place-line").assertTextEquals(line).assertContentDescriptionEquals("Bert says: $line").assertIsDisplayed()
        // A pat reply is speech too, and replaces the line in the same bubble.
        compose.onNodeWithTag("place-bert").performClick()
        val reply = said()
        assertTrue(reply, reply in BertLines.pats)
        compose.onNodeWithTag("place-line").assertContentDescriptionEquals("Bert says: $reply")
    }

    @Test fun aNewcomerHearsHintsUntilTheyPatHim() {
        place(10)
        // Day one: the first thing he says is how to pat him.
        compose.onNodeWithTag("place-line").assertTextEquals("pat me. i permit it.")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-bert").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(said() in BertLines.pats)
        compose.mainClock.autoAdvance = true
        // Once the reply has been said he never asks for the pat again: he moves on to the spots (see
        // HomeAffordanceTest for the walk through them), and his own line comes back once each has been found.
        compose.waitUntil(5_000) { said() != "pat me. i permit it." && said() !in BertLines.pats }
        assertEquals(PlaceSpot.GATE.hint, said())
        val firsts = PlaceDiscovery(compose.activity).record(today)
        assertTrue(firsts.patted)
    }

    @Test fun someoneUpdatingFrom013IsNotGreetedAsANewcomer() {
        // 0.13 left paw prints (bert_place month/days) but none of the 0.14 discovery keys.
        BertVisits(compose.activity).record(today.minusDays(3))
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.of(BertSlot.ROUNDS, today))
        assertEquals("someone who has been before has nothing left to find", emptySet<PlaceSpot>(), untried())
        val firsts = PlaceDiscovery(compose.activity).record(today)
        assertTrue(firsts.patted); assertEquals(PlaceSpot.entries.toSet(), firsts.tapped)
    }

    @Test fun homeOpenedOnAnEarlierDayAlsoCountsAsBeenBefore() {
        // A month change wipes last month's paw prints from bert_place, but Home's own day count goes back to 0.9: what
        // 0.13 left there, written as 0.13 wrote it.
        compose.activity.getSharedPreferences("bert_home", android.content.Context.MODE_PRIVATE).edit()
            .putLong("last_open_day", today.minusDays(40).toEpochDay()).putInt("open_days", 12).commit()
        BERTHomeVisits(compose.activity).recordOpen(today)
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.of(BertSlot.ROUNDS, today))
        assertEquals(emptySet<PlaceSpot>(), untried())
    }

    @Test fun aFreshInstallIsStillShownRoundOnItsFirstDay() {
        // Everything a first day leaves behind, Home opened and a paw print, is today's: still a newcomer.
        BERTHomeVisits(compose.activity).recordOpen(today)
        BertVisits(compose.activity).record(today)
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals("pat me. i permit it.")
        assertEquals(PlaceSpot.entries.toSet(), untried())
        // And the seeding only ever happens on the first record: day two of a fresh install is still a newcomer's.
        val firsts = PlaceDiscovery(compose.activity).record(today.plusDays(1))
        assertFalse(firsts.patted); assertTrue(BertLines.hint(BertSlot.ROUNDS, firsts, gate = true) != null)
    }

    @Test fun aFirstOpenStraightIntoMarketStillGetsShownRoundTheNextDay() {
        // Day one of a fresh install: a widget tap opens Market, which counts the day but never shows Home.
        BERTHomeVisits(compose.activity).recordOpen(today.minusDays(1))
        // Day two, Home at last: that open was this version's own, not an older one's, so they are still a newcomer.
        BERTHomeVisits(compose.activity).recordOpen(today)
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals("pat me. i permit it.")
        assertEquals(PlaceSpot.entries.toSet(), untried())
        val firsts = PlaceDiscovery(compose.activity).record(today)
        assertFalse(firsts.patted); assertTrue(firsts.tapped.isEmpty())
    }

    @Test fun atNightTheWindowKnockEndsTheHintLikeAPat() {
        place(23)
        compose.onNodeWithTag("place-line").assertTextEquals("tap the window. i’m a light sleeper.")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-window").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.WOKEN)
        compose.mainClock.autoAdvance = true
        // Back to sleep, he does not ask for the knock again: he moves on to the spots still to find.
        compose.waitUntil(5_000) { said() != BertLines.WOKEN }
        assertEquals(PlaceSpot.GATE.hint, said())
        assertTrue(PlaceDiscovery(compose.activity).record(today).patted)
    }

    @Test fun hintsTakeTurnsAndOnlyForTheFirstDays() {
        val fresh = PlaceFirsts(1, false, emptySet())
        assertEquals("pat me. i permit it.", BertLines.hint(BertSlot.ROUNDS, fresh, gate = true))
        assertEquals("asleep. pats still accepted.", BertLines.hint(BertSlot.NAP, fresh, gate = true))
        // At night he's indoors: no pat to give, so the window.
        assertEquals("tap the window. i’m a light sleeper.", BertLines.hint(BertSlot.BED, fresh, gate = true))
        // On later days the hints walk the spots not yet found.
        assertEquals(PlaceSpot.GATE.hint, BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = 2), gate = true))
        assertEquals(PlaceSpot.BOARD.hint, BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = 2, tapped = setOf(PlaceSpot.GATE, PlaceSpot.CHALK)), gate = true))
        assertEquals(PlaceSpot.CHALK.hint, BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = 2), gate = false))
        assertTrue(BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = FIRST_DAYS), gate = true) != null)
        assertEquals(null, BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = FIRST_DAYS + 1), gate = true))
        // Once patted, the next spot not yet tried, whatever the day; with all found, nothing (his own line).
        val patted = fresh.copy(patted = true)
        assertEquals(PlaceSpot.GATE.hint, BertLines.hint(BertSlot.ROUNDS, patted, gate = true))
        assertEquals(PlaceSpot.CHALK.hint, BertLines.hint(BertSlot.BED, patted, gate = false))
        assertEquals(PlaceSpot.BOARD.hint, BertLines.hint(BertSlot.ROUNDS, patted.copy(days = 3, tapped = setOf(PlaceSpot.GATE, PlaceSpot.CHALK)), gate = true))
        assertEquals(null, BertLines.hint(BertSlot.ROUNDS, patted.copy(tapped = PlaceSpot.entries.toSet()), gate = true))
        // None of them leans on lore: no hat, no hoodie, no mayor.
        for (h in PlaceSpot.entries.map { it.hint } + BertSlot.entries.mapNotNull { BertLines.hint(it, fresh, gate = true) })
            for (lore in listOf("hat", "hoodie", "mayor")) assertFalse("$h: $lore", Regex("\\b$lore\\b").containsMatchIn(h))
    }

    @Test fun eachSpotIsFoundForGoodOnceTapped() {
        place(10)
        assertEquals(PlaceSpot.entries.toSet(), untried())
        compose.onNodeWithTag("place-board").performClick()
        compose.waitForIdle()
        assertEquals(setOf(PlaceSpot.GATE, PlaceSpot.CHALK), untried())
        // Kept: the next visit finds the board already known.
        assertEquals(setOf(PlaceSpot.BOARD), PlaceDiscovery(compose.activity).record(today).tapped)
    }

    @Test fun theShowingRoundStopsAfterTheFirstDaysAnyway() {
        val discovery = PlaceDiscovery(compose.activity)
        for (d in FIRST_DAYS downTo 1) discovery.record(today.minusDays(d.toLong()))
        place(10)
        assertEquals("still shown round on day ${FIRST_DAYS + 1}", emptySet<PlaceSpot>(), untried())
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.of(BertSlot.ROUNDS, today))
        // Days are counted once each: coming back later today doesn't count again.
        assertEquals(FIRST_DAYS + 1, discovery.record(today).days)
    }

    @Test fun everyLineFitsAtLargeText() {
        var line by mutableStateOf(BertLines.every.first())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                BERTTheme {
                    Column(Modifier.fillMaxSize().background(Navy)) {
                        BertSays(line, line, PlaceTime.DAY, androidx.compose.ui.graphics.Color(0xFF704A26), toWindow = false, anchor = 82, speaker = { 82 })
                    }
                }
            }
        }
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        for (each in BertLines.every) {
            compose.runOnIdle { line = each }
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(each, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val result = layout.single()
            // MultiParagraph.width is the offered width, not the wrapped one, so check each line's own extent instead.
            for (n in 0 until result.lineCount) assertTrue("\"$each\" line $n is clipped",
                result.getLineLeft(n) >= -0.5f && result.getLineRight(n) <= result.size.width + 0.5f && result.getLineBottom(n) <= result.size.height + 0.5f)
            assertTrue("\"$each\" takes ${result.lineCount} lines", result.lineCount <= 3)
            val words = compose.onNodeWithText(each, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("\"$each\" runs off the page", words.left >= 16.dp && words.right <= root.right - 16.dp)
        }
    }

    @Test fun theBubbleIsBuiltOnceUntilItsShapeChanges() {
        val cache = BubbleCache()
        val day = BubbleTone.of(PlaceTime.DAY)
        val first = cache.get(80, 20, 30, toWindow = false, tone = day)
        // Drawn again with nothing changed (another frame, a glint elsewhere): the same bitmap, not a new one.
        assertTrue(first === cache.get(80, 20, 30, toWindow = false, tone = BubbleTone.of(PlaceTime.DAY)))
        // Bert takes a step: the tail moves, so the picture is new.
        val stepped = cache.get(80, 20, 32, toWindow = false, tone = day)
        assertFalse(stepped === first)
        // Past the corner the tail is clamped, so further steps draw the same bubble.
        val clamped = cache.get(80, 20, 200, toWindow = false, tone = day)
        assertTrue(clamped === cache.get(80, 20, 240, toWindow = false, tone = day))
        // The light changing, the words resizing it, or night's puffs each make a new one.
        assertFalse(clamped === cache.get(80, 20, 240, toWindow = false, tone = BubbleTone.of(PlaceTime.DUSK)))
        assertFalse(cache.get(80, 20, 240, toWindow = false, tone = day) === cache.get(80, 26, 240, toWindow = false, tone = day))
        assertFalse(cache.get(80, 26, 240, toWindow = false, tone = day) === cache.get(80, 26, 240, toWindow = true, tone = day))
    }

    @Test fun theTailKeepsTheSpaceForTheTallerLine() {
        // A short pat reply in place of a two-line daily line must not pull the page below up.
        var line by mutableStateOf("rounds. every post, every corner, every smell that matters.")
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertSays(line, "rounds. every post, every corner, every smell that matters.", PlaceTime.DAY,
                        androidx.compose.ui.graphics.Color(0xFF704A26), toWindow = false, anchor = 82, speaker = { 82 })
                }
            }
        }
        val before = compose.onNodeWithTag("place-line").getUnclippedBoundsInRoot().bottom
        compose.runOnIdle { line = "ears. yes. ears." }
        assertEquals(before, compose.onNodeWithTag("place-line").getUnclippedBoundsInRoot().bottom)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/**
 * Home with the bubble, at the times of day and widths that matter, for review by eye: the join, the bubble against
 * each light, and the page underneath. Review fixtures only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BertSaysHomeShotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun home(hour: Int, regular: Boolean = true, fontScale: Float = 1f) {
        val now = ZonedDateTime.of(2026, 9, 9, hour, 13, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 9, 9)
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        if (regular) PlaceDiscovery(compose.activity).apply {
            for (d in FIRST_DAYS downTo 1) record(today.minusDays(d.toLong()))
            patted()
        }
        val activity = ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the ladybug holds the screen door.", "content", null))
        val quote = QuoteState.Available(BERTQuote(0.0154, 2.1, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BERTTheme { BERTApp(quote, activity, emptyList(), BERTPosition(), now, false, false, {}, {}, {}) }
            }
        }
        compose.onNodeWithTag("place-line").assertIsDisplayed()
    }

    @Test fun rounds360() { home(10); capture("home-says-rounds-360") }
    @Test fun nap360() { home(13); capture("home-says-nap-360") }
    @Test fun post360() { home(16); capture("home-says-post-360") }
    @Test fun bed360() { home(23); capture("home-says-bed-360") }
    @Test fun newcomer360() { home(10, regular = false); capture("home-says-newcomer-360") }
    @Test fun largeText360() { home(10, fontScale = 1.3f); capture("home-says-rounds-360-large") }
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun rounds412() { home(10); capture("home-says-rounds-412") }
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun nap412() { home(13); capture("home-says-nap-412") }
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun post412() { home(16); capture("home-says-post-412") }
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun bed412() { home(23); capture("home-says-bed-412") }
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun newcomer412() { home(10, regular = false); capture("home-says-newcomer-412") }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
