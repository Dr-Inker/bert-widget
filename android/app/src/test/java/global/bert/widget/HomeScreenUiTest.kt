package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

/** Home's 0.9.1 layout and return rewards, host-rendered. Review fixtures only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_789_000_000_000
    private val hour = 3_600_000L

    private fun home(activity: ActivityState, fontScale: Float = 1f, quote: QuoteState = QuoteState.Unavailable("No connection")) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BERTTheme { BERTApp(quote, activity, emptyList(), BERTPosition(), now, false, false, {}, {}, {}) }
            }
        }
    }

    private fun dispatch(text: String = "woofmornin. the ladybug holds the screen door like the wind can wait its turn.", mood: String? = "melancholy",
                         event: BERTEvent? = null) = ActivityState.Available(BERTActivity(now - 60_000, text, mood, event))

    private fun top(text: String) = compose.onNodeWithText(text, substring = true).getUnclippedBoundsInRoot().top

    @Test fun homeRunsTodayPriceTrailTournamentChecklistThenEarlier() {
        val journal = BERTJournal(compose.activity)
        journal.record(BERTActivity(now - 30 * hour, "an older dispatch from the town hall.", "content", null), now = now - 30 * hour)
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        val event = BERTEvent("The Autumn Arc", now + 3 * 86_400_000L, "live", pool = 250, standings = listOf(BERTStanding(1, "Pip", 143, 100)))
        home(dispatch(event = event), quote = QuoteState.Available(BERTQuote(0.0154, 2.1, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY")))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Four things worth a try").fetchSemanticsNodes().isNotEmpty() }
        val order = listOf("@bertcoincto", "WOOFmornin|Afternoon|Evening|Up late", "\$BERT", "Play The Lost Trail", "The Autumn Arc",
            "Four things worth a try", "EARLIER FROM BERT")
        val tops = order.map { label ->
            if ('|' in label) label.split('|').firstNotNullOf { runCatching { top(it) }.getOrNull() } else top(label)
        }
        assertEquals("Home sections out of order: ${order.zip(tops)}", tops.sorted(), tops)
        // Official BERT is two chips under the wordmark, not a card; the full card lives on Explore.
        compose.onAllNodesWithText("OFFICIAL BERT").assertCountEquals(0)
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.onNodeWithText("melancholy", substring = true).assertExists()
        compose.onAllNodesWithText("MOOD", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("OFFICIAL BERT").assertCountEquals(1)
    }

    @Test fun meetBertShowsOnceAndReturnsOnALongPressOfPixelBert() {
        home(dispatch())
        compose.onNodeWithText("Meet Bert").assertIsDisplayed()
        compose.onNodeWithText("drawn from Bertram", substring = true).assertExists()
        compose.onNodeWithText("Berthalla, a fan project", substring = true).assertExists()
        capture("home-meet-bert")
        compose.onNodeWithText("Nice to meet you").performClick()
        compose.onNodeWithText("Meet Bert").assertDoesNotExist()
        assertTrue(BERTHomeVisits(compose.activity).meetBertDismissed)
        // Bert in the Today garden woofs on a tap (a spoken button, at least 48dp) and reopens the card on a long press.
        val bert = compose.onNode(hasContentDescription("Bert in his red hat, out in the garden"))
        bert.assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.mainClock.autoAdvance = false
        bert.performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("woof", useUnmergedTree = true).assertExists()
        capture("home-woof")
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("woof", useUnmergedTree = true).assertDoesNotExist()
        bert.performTouchInput { longClick() }
        compose.onNodeWithText("Meet Bert").assertIsDisplayed()
    }

    @Test fun returningShowsDayNTheNewMarkerAndANewFromBertChipThatScrollsToEarlier() {
        val visits = BERTHomeVisits(compose.activity)
        visits.meetBertDismissed = true
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        visits.recordOpen(day.minusDays(3)); visits.recordOpen(day.minusDays(1))
        visits.visit(now - 5 * hour, day.minusDays(1)) // the last look at Home, five hours ago
        val journal = BERTJournal(compose.activity)
        journal.record(BERTActivity(now, "the bakery opened early for the pups.", "giddy", null), now = now - 3 * hour)
        journal.record(BERTActivity(now, "a quiet lap of the duck pond.", "content", null), now = now - 2 * hour)
        home(dispatch())
        compose.onNodeWithText("Day 3 with Bert").assertIsDisplayed()
        compose.onAllNodesWithText("New").onFirst().assertIsDisplayed()
        compose.onNodeWithText("From Berthalla, first seen", substring = true).assertIsDisplayed()
        capture("home-returning")
        compose.onNodeWithText("2 new from Bert").assertIsDisplayed().performClick()
        compose.onNodeWithText("EARLIER FROM BERT").assertIsDisplayed()
        compose.onNodeWithText("the bakery opened early for the pups.").assertIsDisplayed()
        capture("home-returning-earlier")
    }

    @Test fun aStaleUpdateIsAmberAndNeverNew() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Available(BERTActivity(now - 60_000, "an update saved earlier.", "smug", null), updateDelayed = true))
        compose.onNodeWithText("Saved update from Berthalla, first seen", substring = true).assertIsDisplayed()
        compose.onNodeWithText("New").assertDoesNotExist()
        compose.onNodeWithText("a little smug").assertIsDisplayed()
    }

    @Test fun largeTextHomeShortensTheDateAndKeepsTheTrailCard() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        LostTrailProgress.store(compose.activity, org.json.JSONObject.quote(org.json.JSONObject()
            .put("p", """{"version":1,"worlds":[{"time":91000,"bones":3},{"time":120000,"bones":2}]}""")
            .put("r", """{"version":1,"world":2,"checkpoint":1}""").toString()))
        home(dispatch(), fontScale = 2f)
        val date = todayLabel(now, large = true)
        assertFalse(date, date.contains(' ') && date.split(' ').size > 3)
        compose.onNodeWithText(date).assertIsDisplayed()
        capture("home-large-today")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Continue · Galepeak Gardens").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continue · Galepeak Gardens").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 of 6 worlds cleared").assertExists()
        compose.onNodeWithText("5 of 18 keepsakes").assertExists()
        capture("home-large-trail")
    }

    @Test fun trailCardShowsProgressAndOpensTheGame() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        LostTrailProgress.store(compose.activity, org.json.JSONObject.quote(org.json.JSONObject()
            .put("p", """{"version":1,"worlds":[{"time":91000,"bones":3},{"time":120000,"bones":2},{"time":99000,"bones":1}]}""")
            .put("r", org.json.JSONObject.NULL).toString()))
        home(dispatch(), quote = QuoteState.Available(BERTQuote(0.0154, -3.2, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY")))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Continue · Ember Foundry").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continue · Ember Foundry").performScrollTo()
        capture("home-trail")
        compose.onNodeWithText("Continue · Ember Foundry").performClick()
        val game = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(LostTrailActivity::class.java.name, game.component?.className)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val target = File("build/outputs/host-ui/$name.png")
        requireNotNull(target.parentFile).mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
