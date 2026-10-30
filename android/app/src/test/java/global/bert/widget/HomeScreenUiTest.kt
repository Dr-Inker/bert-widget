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

/** Home's 0.12 layout (Bert’s place, the noticeboard) and return rewards, host-rendered. Review fixtures only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    // 12:13 in the host zone on 9 September 2026: Bert’s sun-nap, so he is in the garden.
    private val now = java.time.ZonedDateTime.of(2026, 9, 9, 12, 13, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
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

    @Test fun scrollingPastTheSceneCoversTheStatusBar() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch())
        // At the top the sky is behind the status bar; scrolled down, the page's colour is.
        compose.onNodeWithTag("status-scrim").assertDoesNotExist()
        compose.onNodeWithText("About, privacy & feedback", substring = true).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("status-scrim").assertExists()
        capture("home-scrolled")
    }

    @Test fun homeRunsThePlaceChipsTrailTournamentChecklistAndTheBoardHoldsEarlier() {
        val journal = BERTJournal(compose.activity)
        journal.record(BERTActivity(now - 30 * hour, "an older dispatch from the town hall.", "content", null), now = now - 30 * hour)
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        val event = BERTEvent("The Autumn Arc", now + 3 * 86_400_000L, "live", pool = 250, standings = listOf(BERTStanding(1, "Pip", 143, 100)))
        home(dispatch(event = event), quote = QuoteState.Available(BERTQuote(0.0154, 2.1, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY")))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Four things worth a try").fetchSemanticsNodes().isNotEmpty() }
        // The place comes first (no header, no Today card), then the chips, then the rest of Home.
        val tops = listOf(compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().top, top("@bertcoincto"), top("Play The Lost Trail"),
            top("The Autumn Arc"), top("Four things worth a try"))
        assertEquals("Home sections out of order: $tops", tops.sorted(), tops)
        compose.onAllNodesWithText("OFFICIAL BERT").assertCountEquals(0)
        compose.onAllNodesWithText("MOOD", substring = true).assertCountEquals(0)
        // The price is chalked on the sign by the gate; tapping it opens Market.
        compose.onNodeWithTag("place-tag").assertContentDescriptionEquals("Chalkboard: BERT \$0.0154")
        // The update and the earlier ones are on the noticeboard.
        compose.onAllNodesWithText("EARLIER FROM BERT").assertCountEquals(0)
        compose.onNodeWithTag("place-board").performClick()
        compose.onNodeWithTag("noticeboard").assertExists()
        compose.onNodeWithText("EARLIER FROM BERT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("an older dispatch from the town hall.").assertExists()
        capture("home-board")
    }
    @Test fun meetBertShowsOnceAndReturnsOnALongPressOfBert() {
        home(dispatch())
        compose.onNodeWithText("Meet Bert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("drawn from Bertram", substring = true).assertExists()
        compose.onNodeWithText("Berthalla, a fan project", substring = true).assertExists()
        capture("home-meet-bert")
        compose.onNodeWithText("Nice to meet you").performScrollTo().performClick()
        compose.onNodeWithText("Meet Bert").assertDoesNotExist()
        assertTrue(BERTHomeVisits(compose.activity).meetBertDismissed)
        // Bert in his garden is a spoken button at least 48dp, pats on a tap and reopens the card on a long press.
        val bert = compose.onNodeWithTag("place-bert")
        compose.onNodeWithTag("berts-place").performScrollTo()
        bert.assertHasClickAction().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        bert.performClick()
        compose.onNodeWithTag("place-line").assertTextEquals(BertLines.NAPPING)
        capture("home-pat")
        bert.performTouchInput { longClick() }
        compose.onNodeWithText("Meet Bert").assertExists()
    }
    @Test fun returningShowsNewNotesOnTheBoard() {
        val visits = BERTHomeVisits(compose.activity)
        visits.meetBertDismissed = true
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        visits.recordOpen(day.minusDays(3)); visits.recordOpen(day.minusDays(1))
        visits.visit(now - 5 * hour, day.minusDays(1)) // the last look at Home, five hours ago
        val journal = BERTJournal(compose.activity)
        journal.record(BERTActivity(now, "the bakery opened early for the pups.", "giddy", null), now = now - 3 * hour)
        journal.record(BERTActivity(now, "a quiet lap of the duck pond.", "content", null), now = now - 2 * hour)
        home(dispatch())
        capture("home-returning")
        compose.onNodeWithText("2 new notes on the board").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("New").onFirst().assertExists()
        compose.onNodeWithText("From Berthalla, first seen", substring = true).assertExists()
        compose.onNodeWithText("EARLIER FROM BERT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("the bakery opened early for the pups.").assertExists()
        capture("home-returning-earlier")
    }
    // 0.13.2 (Antigravity critique 3.1): once its notes were read the board's link vanished, leaving only the pixel post.
    @Test fun readNotesStayOneLabelledTapAway() {
        val visits = BERTHomeVisits(compose.activity)
        visits.meetBertDismissed = true
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        visits.recordOpen(day.minusDays(1))
        visits.visit(now - 5 * hour, day.minusDays(1))
        // Today's note was already seen on the last visit, so nothing is new.
        BERTJournal(compose.activity).record(BERTActivity(now - 60_000, "woofmornin. the mayor is in.", "content", null), now = now - 6 * hour)
        home(dispatch("woofmornin. the mayor is in.", "content"))
        compose.onNodeWithText("new note", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Read the noticeboard").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("From Berthalla, first seen", substring = true).assertExists()
        capture("home-read-noticeboard")
    }
    @Test fun aStaleUpdateIsAmberAndNeverNew() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Available(BERTActivity(now - 60_000, "an update saved earlier.", "smug", null), updateDelayed = true))
        compose.onNodeWithTag("place-board").performClick()
        compose.onNodeWithText("Saved note from Berthalla, first seen", substring = true).assertIsDisplayed()
        compose.onNodeWithText("New").assertDoesNotExist()
    }
    @Test fun largeTextHomeKeepsTheLineAndTheTrailCard() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        LostTrailProgress.store(compose.activity, org.json.JSONObject.quote(org.json.JSONObject()
            .put("p", """{"version":1,"worlds":[{"time":91000,"bones":3},{"time":120000,"bones":2}]}""")
            .put("r", """{"version":1,"world":2,"checkpoint":1}""").toString()))
        home(dispatch(), fontScale = 2f)
        compose.onNodeWithTag("place-line").assertIsDisplayed()
        capture("home-large-today")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Continue · Galepeak Gardens").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Continue · Galepeak Gardens").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 of 6 worlds cleared").assertExists()
        compose.onNodeWithText("5 of 18 keepsakes").assertExists()
        capture("home-large-trail")
    }
    @Test fun theGardenGateLeadsToTheLostTrail() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch())
        compose.onNodeWithTag("place-gate").assertContentDescriptionEquals("The garden gate to The Lost Trail").performClick()
        val game = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(LostTrailActivity::class.java.name, game.component?.className)
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
