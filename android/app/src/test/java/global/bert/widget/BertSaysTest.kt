package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
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
                        BertsPlace(now, "content", newNotes = 0)
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
        // Once the reply has been said he never asks for the pat again: his own line comes back.
        compose.waitUntil(5_000) { said() != "pat me. i permit it." && said() !in BertLines.pats }
        assertEquals(BertLines.of(BertSlot.ROUNDS, today), said())
        val firsts = PlaceDiscovery(compose.activity).record(today)
        assertTrue(firsts.patted)
    }

    @Test fun someoneUpdatingFrom013IsNotGreetedAsANewcomer() {
        // 0.13 left paw prints (bert_place month/days) but none of the 0.14 discovery keys.
        BertVisits(compose.activity).record(today.minusDays(3))
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.of(BertSlot.ROUNDS, today))
        assertTrue("someone who has been before is not asked for a pat", PlaceDiscovery(compose.activity).record(today).patted)
    }

    @Test fun homeOpenedOnAnEarlierDayAlsoCountsAsBeenBefore() {
        // A month change wipes last month's paw prints from bert_place, but Home's own day count goes back to 0.9: what
        // 0.13 left there, written as 0.13 wrote it.
        compose.activity.getSharedPreferences("bert_home", android.content.Context.MODE_PRIVATE).edit()
            .putLong("last_open_day", today.minusDays(40).toEpochDay()).putInt("open_days", 12).commit()
        BERTHomeVisits(compose.activity).recordOpen(today)
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.of(BertSlot.ROUNDS, today))
    }

    @Test fun aFreshInstallIsStillShownRoundOnItsFirstDay() {
        // Everything a first day leaves behind, Home opened and a paw print, is today's: still a newcomer.
        BERTHomeVisits(compose.activity).recordOpen(today)
        BertVisits(compose.activity).record(today)
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals("pat me. i permit it.")
        // And the seeding only ever happens on the first record: day two of a fresh install is still a newcomer's.
        val firsts = PlaceDiscovery(compose.activity).record(today.plusDays(1))
        assertFalse(firsts.patted); assertTrue(BertLines.hint(BertSlot.ROUNDS, firsts) != null)
    }

    @Test fun aFirstOpenStraightIntoMarketStillGetsShownRoundTheNextDay() {
        // Day one of a fresh install: a widget tap opens Market, which counts the day but never shows Home.
        BERTHomeVisits(compose.activity).recordOpen(today.minusDays(1))
        // Day two, Home at last: that open was this version's own, not an older one's, so they are still a newcomer.
        BERTHomeVisits(compose.activity).recordOpen(today)
        place(10)
        compose.onNodeWithTag("place-line").assertTextEquals("pat me. i permit it.")
        assertFalse(PlaceDiscovery(compose.activity).record(today).patted)
    }

    @Test fun atNightTheWindowKnockEndsTheHintLikeAPat() {
        place(23)
        compose.onNodeWithTag("place-line").assertTextEquals("tap the window. i’m a light sleeper.")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-window").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.WOKEN)
        compose.mainClock.autoAdvance = true
        // Back to sleep, he does not ask for the knock again: his own line.
        compose.waitUntil(5_000) { said() != BertLines.WOKEN }
        assertEquals(BertLines.of(BertSlot.BED, today), said())
        assertTrue(PlaceDiscovery(compose.activity).record(today).patted)
    }

    @Test fun heAsksForAPatOnlyUntilPattedAndOnlyForTheFirstDays() {
        val fresh = PlaceFirsts(1, false)
        assertEquals("pat me. i permit it.", BertLines.hint(BertSlot.ROUNDS, fresh))
        assertEquals("asleep. pats still accepted.", BertLines.hint(BertSlot.NAP, fresh))
        // At night he's indoors: no pat to give, so the window.
        assertEquals("tap the window. i’m a light sleeper.", BertLines.hint(BertSlot.BED, fresh))
        assertEquals("pat me. i permit it.", BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = FIRST_DAYS)))
        assertEquals(null, BertLines.hint(BertSlot.ROUNDS, fresh.copy(days = FIRST_DAYS + 1)))
        // Once patted, nothing to ask: his own line.
        for (slot in BertSlot.entries) assertEquals(null, BertLines.hint(slot, fresh.copy(patted = true)))
        // None of them leans on lore: no hat, no hoodie, no mayor.
        for (h in BertSlot.entries.mapNotNull { BertLines.hint(it, fresh) })
            for (lore in listOf("hat", "hoodie", "mayor")) assertFalse("$h: $lore", Regex("\\b$lore\\b").containsMatchIn(h))
    }

    /**
     * Lifetime pats, kept in bert_place on the phone: counted one by one across sittings, and each milestone (10, 50,
     * 100, 500, 1000) is reached exactly once.
     */
    @Test fun patsAreCountedForGoodAndEachMilestoneComesOnce() {
        val fired = mutableListOf<Int>()
        repeat(600) { PlaceDiscovery(compose.activity).pat().let { n -> if (BertLines.milestones[n] != null) fired += n } }
        // A new sitting reads the same count.
        assertEquals(600, PlaceDiscovery(compose.activity).pats)
        repeat(600) { PlaceDiscovery(compose.activity).pat().let { n -> if (BertLines.milestones[n] != null) fired += n } }
        assertEquals(listOf(10, 50, 100, 500, 1000), fired)
        assertEquals(1200, PlaceDiscovery(compose.activity).pats)
        // Kept with the rest of the place, and never sent anywhere: a plain int in bert_place.
        assertEquals(1200, compose.activity.getSharedPreferences("bert_place", android.content.Context.MODE_PRIVATE).getInt("pats_ever", -1))
    }

    /** His words about the things: lowercase and dry, nothing to buy or sell, no price but his own chalk. */
    @Test fun hisRemarksAreInHisVoice() {
        val all = PlaceThing.entries.flatMap { it.remarks } + BertLines.milestones.values
        assertEquals("every remark is his own", all.size, all.toSet().size)
        for (r in all) {
            assertEquals("$r: lowercase", r.lowercase(), r)
            assertFalse("$r: no exclamation", '!' in r)
            assertFalse("$r: no dollars or cents", '\$' in r || '\u00a2' in r)
            for (word in listOf("price", "buy", "sell", "token", "coin", "invest", "moon", "pump", "money", "ai", "bot", "mascot"))
                assertFalse("$r: $word", Regex("\\b$word\\b").containsMatchIn(r))
        }
        for (thing in PlaceThing.entries) assertTrue("${thing.tag}: two or three lines", thing.remarks.size in 2..3)
    }

    @Test fun theShowingRoundStopsAfterTheFirstDaysAnyway() {
        val discovery = PlaceDiscovery(compose.activity)
        for (d in FIRST_DAYS downTo 1) discovery.record(today.minusDays(d.toLong()))
        place(10)
        assertFalse("still shown round on day ${FIRST_DAYS + 1}", discovery.record(today).showingRound)
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.of(BertSlot.ROUNDS, today))
        // Days are counted once each: coming back later today doesn't count again.
        assertEquals(FIRST_DAYS + 1, discovery.record(today).days)
    }

    /** Home keeps its scroll: with the garden scrolled away he does not spend the day's word; he says it once it is back in view. */
    @Test fun theDaysWordWaitsUntilTheGardenIsInView() {
        regular()
        val discovery = PlaceDiscovery(compose.activity)
        for (d in 6 downTo 0) discovery.visit(today.minusDays(d.toLong()))
        val away = androidx.compose.runtime.mutableStateOf(true)
        val now = at(10)
        compose.mainClock.autoAdvance = false
        compose.setContent { BERTTheme { Column(Modifier.fillMaxSize().background(Navy)) { BertsPlace(now, "content", newNotes = 0, skyCovered = away.value) } } }
        compose.mainClock.advanceTimeBy(7_000)
        compose.waitForIdle()
        assertEquals("his own line while nobody can see him", BertLines.of(BertSlot.ROUNDS, today), said())
        assertEquals("and the day's word is not spent", null, discovery.daySaidOn())
        compose.runOnIdle { away.value = false }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertEquals(BertLines.streakDays.getValue(7), said())
        compose.mainClock.advanceTimeBy(7_000)
        compose.waitForIdle()
        assertEquals(today, discovery.daySaidOn())
        compose.mainClock.autoAdvance = true
    }

    /**
     * A run of six days before today, and the app opened today (counted by the app, not by the garden): day seven. Home
     * on screen has Bert say so; once it has been up long enough to be read it counts as said, and his own line comes back.
     */
    @Test fun theFirstHomeOnANewDayGetsBertsWordOnIt() {
        regular()
        val discovery = PlaceDiscovery(compose.activity)
        for (d in 6 downTo 0) discovery.visit(today.minusDays(d.toLong()))
        compose.mainClock.autoAdvance = false
        place(10)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(BertLines.streakDays.getValue(7), said())
        assertEquals("not said until it has been up a moment", null, discovery.daySaidOn())
        compose.mainClock.advanceTimeBy(7_000)
        compose.waitForIdle()
        assertEquals(today, discovery.daySaidOn())
        assertEquals(BertLines.of(BertSlot.ROUNDS, today), said())
        compose.mainClock.autoAdvance = true
        // The garden counts nothing itself: the stored run is the app's.
        assertEquals(VisitStreak(7, 7, today), discovery.streak())
    }

    /** Sent away before the line was read: he says it again on coming back, and then it counts. */
    @Test fun aLineCutShortIsSaidAgain() {
        regular()
        val discovery = PlaceDiscovery(compose.activity)
        for (d in 2 downTo 0) discovery.visit(today.minusDays(d.toLong()))
        compose.mainClock.autoAdvance = false
        place(10)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(BertLines.streakDays.getValue(3), said())
        // Away before it was read (the screen off, another app; a rotation stops Home the same way).
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeBy(500)
        assertEquals("not counted as said", null, discovery.daySaidOn())
        compose.mainClock.advanceTimeBy(7_000)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("said again on coming back", BertLines.streakDays.getValue(3), said())
        compose.mainClock.advanceTimeBy(3_000)
        assertEquals(today, discovery.daySaidOn())
        compose.mainClock.autoAdvance = true
    }

    @Test fun aRunThatStartsAgainIsWelcomed() {
        regular()
        PlaceDiscovery(compose.activity).apply { visit(today.minusDays(9)); visit(today.minusDays(8)); visit(today.minusDays(3)) }
        place(10)
        compose.waitForIdle()
        assertTrue(said(), said() in BertLines.streakBack)
        assertEquals(VisitStreak(1, 2, today), PlaceDiscovery(compose.activity).streakOn(today).streak)
    }

    /** The very first open: the newcomer's ask for a pat, not a word about day one. */
    @Test fun theFirstOpenEverIsTheNewcomersHint() {
        PlaceDiscovery(compose.activity).visit(today)
        place(10)
        compose.waitForIdle()
        assertEquals("pat me. i permit it.", said())
        assertEquals(StreakStep(VisitStreak(1, 1, today), StreakChange.FIRST), PlaceDiscovery(compose.activity).streakOn(today))
    }

    /** An install from before 0.15 has a day count but no streak and no paw prints this month: it starts at one. */
    @Test fun anOlderInstallStartsItsRunAtOne() {
        val discovery = PlaceDiscovery(compose.activity)
        for (d in 40 downTo 1) discovery.record(today.minusDays(d.toLong()))
        assertEquals(null, discovery.streak())
        assertEquals(VisitStreak(1, 1, today), discovery.visit(today).streak)
    }

    /** An upgrader whose paw prints show days in a row ending yesterday starts on that run, today counted in. */
    @Test fun anUpgraderWithPawPrintsKeepsTheirRun() {
        val day = LocalDate.of(2026, 11, 14)
        BertVisits(compose.activity).apply { for (d in listOf(3, 10, 11, 12, 13)) record(day.withDayOfMonth(d)) }
        val step = PlaceDiscovery(compose.activity).visit(day)
        assertEquals(StreakStep(VisitStreak(5, 5, day), StreakChange.FIRST), step)
        assertEquals(VisitStreak(5, 5, day), PlaceDiscovery(compose.activity).streak())
        // And it goes on from there.
        assertEquals(VisitStreak(6, 6, day.plusDays(1)), PlaceDiscovery(compose.activity).visit(day.plusDays(1)).streak)
    }

    /** The newcomer day count no longer freezes when the clock was set forward and comes back. */
    @Test fun theNewcomerDaysGoOnAfterAClockSetForward() {
        val discovery = PlaceDiscovery(compose.activity)
        discovery.record(today.minusDays(2))
        assertEquals(2, discovery.record(today.plusDays(30)).days)
        assertEquals("back to today: nothing counted, nothing lost", 2, discovery.record(today).days)
        assertEquals("the next day counts again", 3, discovery.record(today.plusDays(1)).days)
    }

    private fun bubble(line: () -> String, font: () -> Float, reserve: List<String>) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font())) {
                BERTTheme {
                    Column(Modifier.fillMaxSize().background(Navy)) {
                        BertSays(line(), BertLines.of(BertSlot.ROUNDS, today), PlaceTime.DAY, androidx.compose.ui.graphics.Color(0xFF704A26),
                            from = BubbleFrom.GARDEN, anchor = 82, speaker = { 82 }, reserve = reserve)
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(10.dp).testTag("below"))
                    }
                }
            }
        }
    }

    /**
     * S3: whatever he says, the page under his bubble (the slate, the note) stays where it is: the bubble's space is kept
     * for the tallest thing he can say, at every text size. Every line he can say by day, one after another, at 1.0, 1.3
     * and 2.0.
     */
    private val LONGER_THAN_HIS = "the hedge, the gate, the board, the lawn and the cottage have all been told, twice, and the daisies after them."
    private fun nothingUnderTheBubbleMoves() {
        var line by mutableStateOf(BertLines.of(BertSlot.ROUNDS, today))
        var font by mutableStateOf(1f)
        // With one line longer than any of his, so the check still goes red if the reserve stops working (LONGER_THAN_HIS
        // moves the page without it: withoutTheReserveALongLineMovesThePage).
        val reserve = BertLines.sayable(BertSlot.ROUNDS, VisitStreak(998, 999, today), today) + LONGER_THAN_HIS
        bubble({ line }, { font }, reserve)
        for (f in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { font = f; line = BertLines.of(BertSlot.ROUNDS, today) }
            compose.waitForIdle()
            val rest = compose.onNodeWithTag("below").getUnclippedBoundsInRoot().top
            for (each in reserve) {
                compose.runOnIdle { line = each }
                compose.waitForIdle()
                assertEquals("\"$each\" at x$f moved the page", rest, compose.onNodeWithTag("below").getUnclippedBoundsInRoot().top)
            }
        }
    }

    @Test fun nothingUnderTheBubbleMovesAt360() = nothingUnderTheBubbleMoves()
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun nothingUnderTheBubbleMovesAt320() = nothingUnderTheBubbleMoves()
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun nothingUnderTheBubbleMovesAt412() = nothingUnderTheBubbleMoves()

    /**
     * The check above can fail: without the reserve, a line longer than the resting one is taller than the space the
     * resting line keeps, and the page moves. (His own lines are short enough that the fade's height absorbs nearly all
     * of them, so this uses one longer than anything he says.)
     */
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun withoutTheReserveALongLineMovesThePage() = withoutTheReserve()
    private fun withoutTheReserve() {
        var line by mutableStateOf(BertLines.of(BertSlot.ROUNDS, today))
        var font by mutableStateOf(1f)
        bubble({ line }, { font }, emptyList())
        val moved = mutableListOf<String>()
        for (f in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { font = f; line = BertLines.of(BertSlot.ROUNDS, today) }
            compose.waitForIdle()
            val rest = compose.onNodeWithTag("below").getUnclippedBoundsInRoot().top
            for (each in listOf(LONGER_THAN_HIS)) {
                compose.runOnIdle { line = each }
                compose.waitForIdle()
                if (compose.onNodeWithTag("below").getUnclippedBoundsInRoot().top != rest) moved += "x$f \"$each\""
            }
        }
        assertTrue("a long line did not move the page without the reserve", moved.isNotEmpty())
    }

    private fun everyLineFits(font: Float, maxLines: Int) {
        var line by mutableStateOf(BertLines.every.first())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, font)) {
                BERTTheme {
                    Column(Modifier.fillMaxSize().background(Navy)) {
                        BertSays(line, line, PlaceTime.DAY, androidx.compose.ui.graphics.Color(0xFF704A26), from = BubbleFrom.GARDEN, anchor = 82, speaker = { 82 })
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
            for (n in 0 until result.lineCount) assertTrue("\"$each\" line $n is clipped at x$font",
                result.getLineLeft(n) >= -0.5f && result.getLineRight(n) <= result.size.width + 0.5f && result.getLineBottom(n) <= result.size.height + 0.5f)
            assertTrue("\"$each\" takes ${result.lineCount} lines at x$font", result.lineCount <= maxLines)
            val words = compose.onNodeWithText(each, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("\"$each\" runs off the page at x$font", words.left >= 16.dp && words.right <= root.right - 16.dp)
        }
    }

    @Test fun everyLineFitsAtLargeText() = everyLineFits(1.3f, 3)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun everyLineFitsAtLargeTextAt320() = everyLineFits(1.3f, 3)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun everyLineFitsAtTheLargestTextAt320() = everyLineFits(2f, 4)

    @Test fun theBubbleIsBuiltOnceUntilItsShapeChanges() {
        val cache = BubbleCache()
        val day = BubbleTone.of(PlaceTime.DAY)
        val first = cache.get(80, 20, 30, from = BubbleFrom.GARDEN, tone = day)
        // Drawn again with nothing changed (another frame, a glint elsewhere): the same bitmap, not a new one.
        assertTrue(first === cache.get(80, 20, 30, from = BubbleFrom.GARDEN, tone = BubbleTone.of(PlaceTime.DAY)))
        // Bert takes a step: the tail moves, so the picture is new.
        val stepped = cache.get(80, 20, 32, from = BubbleFrom.GARDEN, tone = day)
        assertFalse(stepped === first)
        // Past the corner the tail is clamped, so further steps draw the same bubble.
        val clamped = cache.get(80, 20, 200, from = BubbleFrom.GARDEN, tone = day)
        assertTrue(clamped === cache.get(80, 20, 240, from = BubbleFrom.GARDEN, tone = day))
        // The light changing, the words resizing it, or night's puffs each make a new one.
        assertFalse(clamped === cache.get(80, 20, 240, from = BubbleFrom.GARDEN, tone = BubbleTone.of(PlaceTime.DUSK)))
        assertFalse(cache.get(80, 20, 240, from = BubbleFrom.GARDEN, tone = day) === cache.get(80, 26, 240, from = BubbleFrom.GARDEN, tone = day))
        assertFalse(cache.get(80, 26, 240, from = BubbleFrom.GARDEN, tone = day) === cache.get(80, 26, 240, from = BubbleFrom.DREAM, tone = day))
    }

    @Test fun theTailKeepsTheSpaceForTheTallerLine() {
        // A short pat reply in place of a two-line daily line must not pull the page below up.
        var line by mutableStateOf("rounds. every post, every corner, every smell that matters.")
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertSays(line, "rounds. every post, every corner, every smell that matters.", PlaceTime.DAY,
                        androidx.compose.ui.graphics.Color(0xFF704A26), from = BubbleFrom.GARDEN, anchor = 82, speaker = { 82 })
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
 * each light, and the page underneath. Review fixtures only: build.gradle.kts keeps them out of the gate, -Pshots runs them.
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
