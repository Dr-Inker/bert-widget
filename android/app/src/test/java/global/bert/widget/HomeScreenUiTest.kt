package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
    private fun atHour(hour: Int) = java.time.ZonedDateTime.of(2026, 9, 9, hour, 13, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
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
        // The price is on the slate under the garden; tapping it opens Market.
        compose.onNodeWithTag("home-price").assertHasClickAction()
        // The update and the earlier ones are on the noticeboard, one tap from the pinned note.
        compose.onAllNodesWithText("EARLIER FROM BERTHALLA").assertCountEquals(0)
        compose.onNodeWithTag("board-row").performScrollTo().performClick()
        compose.onNodeWithTag("noticeboard").assertExists()
        compose.onNodeWithText("EARLIER FROM BERTHALLA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("an older dispatch from the town hall.").assertExists()
        compose.onAllNodesWithText("EARLIER FROM BERT").assertCountEquals(0)
        capture("home-board")
    }
    /**
     * What the app is, in the owner's words (0.15.1): forged in Berthalla, for Bertlievers; its updates from the Berthalla
     * X bot. Meet Bert and About say so, whole, at text 2.0 on a 320dp phone too.
     */
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun meetBertAndAboutSayWhatTheAppIsAtLargeText() {
        home(dispatch(), fontScale = 2f)
        compose.onNodeWithText("This app was forged in Berthalla, for Bertlievers. Its daily updates come from the Berthalla X bot. " +
            "Official BERT: @bertcoincto and bert.global.").performScrollTo().assertIsDisplayed()
        capture("home-meet-bert-320-font2.0")
        compose.onNodeWithText("About, privacy & feedback", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("An app for Bertlievers, forged in Berthalla. Its daily updates come from the Berthalla X bot, run by the BERT team, " +
            "posting in Bert’s voice.", substring = true).assertExists()
        captureSheet("about-320-font2.0")
    }

    @Test fun aboutSaysWhatTheAppIs() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch())
        compose.onNodeWithText("About, privacy & feedback", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("An app for Bertlievers, forged in Berthalla.", substring = true).assertExists()
        captureSheet("about")
    }

    @Test fun meetBertShowsOnceAndReturnsOnALongPressOfBert() {
        home(dispatch())
        compose.onNodeWithText("Meet Bert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("drawn from Bertram", substring = true).assertExists()
        // Canon (and @bertcoincto's bio): the Mayor of Solana. Berthalla is a place, not his town; its posts come from the Berthalla X bot.
        compose.onNodeWithText("Bert is the Mayor of Solana, a Pomeranian in a red hat.", substring = true).assertExists()
        compose.onNodeWithText("Mayor of Berthalla", substring = true).assertDoesNotExist()
        compose.onNodeWithText("This app was forged in Berthalla, for Bertlievers.", substring = true).assertExists()
        // UX-2: one plain sentence says what BERT is.
        compose.onNodeWithText("BERT is a community coin on Solana, named after him.", substring = true).assertExists()
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
        // Today's note is on the paper; the two that came in since the last visit are counted in its link.
        assertTrue(text("board-row"), text("board-row").contains("woofmornin. the ladybug holds the screen door"))
        compose.onNodeWithTag("note-link", useUnmergedTree = true).assertTextEquals("2 new notes on the board ›")
        compose.onNodeWithTag("board-row").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("New").onFirst().assertExists()
        compose.onNodeWithText("From the Berthalla X bot, run by the BERT team, first seen", substring = true).assertExists()
        compose.onNodeWithText("EARLIER FROM BERTHALLA").performScrollTo().assertIsDisplayed()
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
        // UX-2: today's note is shown on its paper note under the scene, not hidden behind the board; the note opens the board.
        compose.onNodeWithText("Read the noticeboard").assertDoesNotExist()
        compose.onNodeWithTag("board-row").assertIsDisplayed().assertHasClickAction()
        assertTrue(text("board-row").contains("woofmornin. the mayor is in."))
        compose.onNodeWithTag("board-row").performClick()
        compose.onNodeWithText("From the Berthalla X bot, run by the BERT team, first seen", substring = true).assertExists()
        capture("home-read-noticeboard")
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text }.replace('\u00A0', ' ')
    /** What TalkBack says for the slate or the note (each is one node with one description). */
    private fun spoken(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ")
    /** [text] on the noticeboard sheet itself, not the note on Home that may say the same. */
    private fun onBoard(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("noticeboard")))

    /**
     * 0.15: the note is pinned up on paper and says as much of itself as three lines hold (whitespace folded, then an
     * ellipsis); in 0.14.2 the row showed only its first line on one line. TalkBack hears all of it.
     */
    @Test fun theNoteShowsUpToThreeLinesOfTodaysNote() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        BERTHomeVisits(compose.activity).recordOpen(day.minusDays(1))
        BERTHomeVisits(compose.activity).visit(now - 5 * hour, day.minusDays(1))
        val note = "the mayor has inspected every single daisy on the lawn this morning and found them all in order.\nmore on the board.\n" +
            "and then a great deal more about the daisies, the clover, the bees, the dew and the shadow of the gate post, which is too long for Home."
        BERTJournal(compose.activity).record(BERTActivity(now - 60_000, note, "content", null), now = now - 6 * hour)
        home(dispatch(note, "content"))
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("note-body", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val body = layouts.single()
        assertEquals("three lines of the note", 3, body.lineCount)
        assertTrue("cut with an ellipsis", body.isLineEllipsized(2))
        val shown = body.layoutInput.text.text
        assertTrue("its lines run on as one: $shown", shown.startsWith("the mayor has inspected every single daisy on the lawn this morning and found them all in order. more on the board."))
        val said = spoken("board-row")
        assertTrue("TalkBack hears the whole note: $said", said.contains("which is too long for Home."))
        compose.onNodeWithTag("board-row").assertHasClickAction().assertHeightIsAtLeast(48.dp)
    }

    @Test fun withNoNoteTodayTheNoteStillLeadsToTheBoard() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        BERTJournal(compose.activity).record(BERTActivity(now - 30 * hour, "an older dispatch from the town hall.", "content", null), now = now - 30 * hour)
        home(ActivityState.Available(BERTActivity(now - 60_000, null, "content", null)))
        // Still a labelled way in (the 4 November response's "Read the noticeboard", now on the note), and it says why it is bare.
        compose.onNodeWithTag("board-row").assertIsDisplayed().assertHasClickAction()
        assertTrue(text("board-row"), text("board-row").contains(NO_POST))
        compose.onNodeWithTag("note-link", useUnmergedTree = true).assertTextEquals("Read the board ›")
        compose.onNodeWithTag("board-row").performClick()
        compose.onNodeWithText("EARLIER FROM BERTHALLA").performScrollTo().assertIsDisplayed()
    }

    @Test fun withNothingOnTheBoardTheNoteSaysSoAndOpensNothing() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Empty)
        assertTrue(text("board-row"), text("board-row").contains("No post from Berthalla right now."))
        compose.onNodeWithTag("board-row").assertHasNoClickAction()
        compose.onNodeWithTag("note-link", useUnmergedTree = true).assertDoesNotExist()
    }

    // UX-4: offline is not "no post". With no feed and nothing saved the row and the board say the connection is the
    // problem; "no post" is only for a feed that loaded with nothing in it.
    @Test fun offlineTheRowAndTheBoardBlameTheConnectionNotThePostman() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Unavailable, quote = quote(now - 30_000))
        assertTrue(text("board-row"), text("board-row").contains("Can’t reach the feed from Berthalla right now. Check your connection."))
        assertTrue(spoken("board-row"), spoken("board-row").contains("Can’t reach the feed from Berthalla right now. Check your connection."))
        // Nothing saved to read, so nothing to open, as in 0.14.2 (and the garden's noticeboard opens nothing since 0.15).
        compose.onNodeWithTag("board-row").assertHasNoClickAction()
        compose.onNodeWithText("No post", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("noticeboard").assertDoesNotExist()
        capture("home-offline-board")
    }

    @Test fun anEmptyFeedIsNoPostNotABadConnection() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Empty)
        compose.onNodeWithText("reach Berthalla", substring = true).assertDoesNotExist()
        assertTrue(text("board-row"), text("board-row").contains(NO_POST))
    }

    // UX-2, UX-4, UX-9: the price under Bert's bubble (0.15: chalked on a slate), in dollars like everywhere else, with how fresh it is.
    private fun quote(observed: Long, change: Double? = 1.43, delayed: Boolean = false) = QuoteState.Available(BERTQuote(0.0154, change, null, null, null,
        "fresh", observed, "dexscreener", "raydium", "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"), delayed)

    @Test fun homeSaysThePriceInPlainWordsUnderTheSceneAndOpensMarket() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch(), quote = quote(now - 30_000))
        val row = compose.onNodeWithTag("home-price").assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp)
        val shown = text("home-price")
        for (part in listOf("BERT", "\$0.0154", "+1.43%", "past 24 hours", "as of 12:12")) assertTrue("'$part' missing from '$shown'", shown.contains(part))
        val said = spoken("home-price")
        for (part in listOf("BERT", "\$0.0154", "+1.43%", "past 24 hours", "As of 12:12")) assertTrue("'$part' not spoken: '$said'", said.contains(part))
        assertFalse(said, said.contains("Delayed"))
        assertFalse("dollars, not cents: $shown", shown.contains("¢"))
        assertEquals("one tap, one place", "Open Market", row.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        // Under the scene and Bert's bubble, above the board row and the official links.
        val top = row.getUnclippedBoundsInRoot().top
        assertTrue("under the scene", top >= compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().bottom)
        assertTrue("under Bert's bubble", top >= compose.onNodeWithTag("place-line").getUnclippedBoundsInRoot().bottom)
        assertTrue("above the board row", top < compose.onNodeWithTag("board-row").getUnclippedBoundsInRoot().top)
        assertTrue("above the official links", top < compose.onNodeWithText("@bertcoincto").getUnclippedBoundsInRoot().top)
        capture("home-price")
        row.performClick()
        compose.onNodeWithText("BERT / USD").assertIsDisplayed()
    }

    @Test fun anOldOrUnrefreshedPriceSaysDelayedInAmber() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        var state by androidx.compose.runtime.mutableStateOf<QuoteState>(quote(now - 42 * 60_000))
        compose.setContent { BERTTheme { BERTApp(state, dispatch(), emptyList(), BERTPosition(), now, false, false, {}, {}, {}) } }
        assertTrue(spoken("home-price"), spoken("home-price").contains("Delayed · Updated 42m ago"))
        assertTrue(text("home-price"), text("home-price").contains("delayed · updated 42m ago"))
        // In yellow chalk, the slate's amber (Market's is amber too), not the plain chalk of a fresh time.
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("slate-time", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("'delayed' is not in yellow chalk", SlateTone.of(PlaceTime.DAY).yellow, layouts.single().layoutInput.style.color)
        capture("home-price-delayed")
        compose.runOnIdle { state = quote(now - 30_000, delayed = true) }
        compose.waitForIdle()
        assertTrue(spoken("home-price"), spoken("home-price").contains("Delayed"))
    }

    @Test fun withNoQuoteHomeSaysSoPlainly() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch(), quote = QuoteState.Unavailable("No connection"))
        assertEquals("Price unavailable. Check your connection.", spoken("home-price"))
        assertTrue(text("home-price"), text("home-price").contains("price unavailable") && text("home-price").contains("check your connection."))
        // Still one tap from Market, which says more and has a retry.
        compose.onNodeWithTag("home-price").assertHasClickAction().performClick()
        compose.onNodeWithText("No market value has been guessed", substring = true).assertExists()
    }
    @Test fun aStaleUpdateIsAmberAndNeverNew() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Available(BERTActivity(now - 60_000, "an update saved earlier.", "smug", null), updateDelayed = true))
        compose.onNodeWithTag("board-row").performScrollTo().performClick()
        compose.onNodeWithText("Saved post from the Berthalla X bot, run by the BERT team, first seen", substring = true).assertIsDisplayed()
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
    /**
     * Review of 0.14.2 (home-large.png): at the largest text, offline, the price line and the board row ran together
     * with no gap, and with a note the preview showed one cut-off word ("woofmor…").
     */
    @Test fun atLargeTextThePriceAndTheBoardRowKeepApart() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(ActivityState.Unavailable, fontScale = 2f)
        val price = compose.onNodeWithTag("home-price").getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag("board-row").getUnclippedBoundsInRoot()
        assertTrue("a gap between the slate (${price.bottom}) and the note (${row.top})", row.top - price.bottom >= 8.dp)
        capture("home-large-offline")
    }

    @Test fun atLargeTextTheBoardPreviewSaysMoreThanOneWord() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch(), fontScale = 2f)
        val node = compose.onNodeWithTag("note-body", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val shown = layout.layoutInput.text.text.substring(0, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        val note = shown
        assertTrue("the preview shows a few words of the note, not one cut short: '$shown'", note.split(' ').count { it.isNotBlank() } >= 3)
        capture("home-large-board")
    }

    /**
     * 0.15, the owner: the garden is "more a Bert tapping area", not a menu. Nothing in it opens anything: a grid of
     * taps over the whole picture, by day and at night (Bert and the window aside, which answer in place), opens no
     * Market, no board and no game.
     */
    @Test fun nothingInTheGardenOpensAnything() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        var hour by mutableIntStateOf(10)
        val activity = dispatch()
        compose.setContent {
            BERTTheme { BERTApp(quote(now - 30_000), activity, emptyList(), BERTPosition(), atHour(hour), false, false, {}, {}, {}) }
        }
        for (h in listOf(10, 16, 23)) {
            compose.runOnIdle { hour = h }
            compose.waitForIdle()
            val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInRoot
            val skip = listOf("place-bert", "place-window", "you-button").flatMap { t -> compose.onAllNodesWithTag(t).fetchSemanticsNodes().map { it.boundsInRoot.inflate(4f) } }
            var taps = 0
            for (i in 1..11) for (j in 1..9) {
                val p = androidx.compose.ui.geometry.Offset(scene.left + scene.width * i / 12, scene.top + scene.height * j / 10)
                if (skip.any { it.contains(p) }) continue
                compose.onRoot().performTouchInput { click(p) }
                compose.waitForIdle()
                taps++
                compose.onNodeWithTag("berts-place").assertExists()
                compose.onNodeWithTag("noticeboard").assertDoesNotExist()
                compose.onAllNodesWithText("BERT / USD").assertCountEquals(0)
                assertEquals("a tap at $p, $h:10, started ${org.robolectric.Shadows.shadowOf(compose.activity).peekNextStartedActivity()}",
                    null, org.robolectric.Shadows.shadowOf(compose.activity).peekNextStartedActivity())
            }
            assertTrue("too few taps at $h:10: $taps", taps > 60)
        }
    }

    /**
     * 0.15: the price lives on the slate under the garden; the garden's chalkboard no longer chalks it (it was the same
     * number in two units, one above the other). Its face is the same pixel for pixel with one price, another, and none.
     */
    @Test fun theGardenChalkboardDoesNotShowThePrice() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        var q by mutableStateOf<QuoteState>(quote(now - 30_000))
        val activity = dispatch()
        compose.setContent { BERTTheme { BERTApp(q, activity, emptyList(), BERTPosition(), now, false, false, {}, {}, {}) } }
        compose.waitForIdle()
        fun face(): List<Int> {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInWindow
            val res = compose.activity.resources
            val f = placeFrame(scene.right - scene.left, res.configuration.screenHeightDp * res.displayMetrics.density)
            return (252 until 275).flatMap { y -> (150 until 180).map { x ->
                bitmap.getPixel((scene.left + f.left + (x - f.x0) * f.k).toInt(), (scene.top + y * f.k).toInt()) } }
        }
        val first = face()
        compose.runOnIdle { q = QuoteState.Available(BERTQuote(0.9876, -3.1, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY")) }
        compose.waitForIdle()
        assertEquals("the chalkboard changed with the price", first, face())
        compose.runOnIdle { q = QuoteState.Unavailable("No connection") }
        compose.waitForIdle()
        assertEquals("the chalkboard changed with no price", first, face())
        // The price is still on the slate under the garden.
        compose.onNodeWithTag("home-price").assertExists()
    }

    // M1: the streak counts days the app was opened, from any way in, not days Home was shown.
    private val day = java.time.LocalDate.of(2026, 9, 9)
    private fun said() = compose.onNodeWithTag("place-line").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()

    private fun openedFrom(entry: BERTEntry) {
        PlaceDiscovery(compose.activity).apply { record(day.minusDays(1)); patted(); visit(day.minusDays(1)) }
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        val activity = dispatch()
        compose.setContent { BERTTheme { BERTApp(quote(now - 30_000), activity, emptyList(), BERTPosition(), now, false, false, {}, {}, {}, entry = entry) } }
        compose.waitForIdle()
        compose.onNodeWithTag("berts-place").assertDoesNotExist()
        assertEquals("opened from $entry", StreakStep(VisitStreak(2, 2, day), StreakChange.NEXT_DAY), PlaceDiscovery(compose.activity).streakOn(day))
        assertEquals(VisitStreak(2, 2, day), PlaceDiscovery(compose.activity).streak())
    }

    /** The price widget and price alerts open Market. */
    @Test fun openingStraightIntoMarketCountsTheDay() = openedFrom(BERTEntry.MARKET)
    /** The Create shortcut opens Create. */
    @Test fun openingStraightIntoCreateCountsTheDay() = openedFrom(BERTEntry.CREATE)

    /**
     * Left in Recents overnight: the app comes back to the front still holding yesterday's clock, and the clock catches
     * up a moment later. The new day counts all the same (as it does when midnight passes with the app open).
     */
    @Test fun aDayThatTurnsWhileTheAppIsInFrontCounts() {
        PlaceDiscovery(compose.activity).apply { record(day.minusDays(1)); patted(); visit(day.minusDays(1)) }
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        val activity = dispatch()
        val at = androidx.compose.runtime.mutableLongStateOf(now - 24 * 3_600_000L)
        compose.setContent { BERTTheme { BERTApp(quote(at.longValue - 30_000), activity, emptyList(), BERTPosition(), at.longValue, false, false, {}, {}, {}, entry = BERTEntry.MARKET) } }
        compose.waitForIdle()
        assertEquals("yesterday's clock counts nothing new", VisitStreak(1, 1, day.minusDays(1)), PlaceDiscovery(compose.activity).streak())
        compose.runOnIdle { at.longValue = now }
        compose.waitForIdle()
        assertEquals("the clock caught up", VisitStreak(2, 2, day), PlaceDiscovery(compose.activity).streak())
    }

    /** The store keeps the anchor and never loses the day's change to a same-day tidy-up (Bert's word depends on it). */
    @Test fun aClockFarBehindIsRememberedAndForgottenInTheStore() {
        val prefs = compose.activity.getSharedPreferences("bert_place", android.content.Context.MODE_PRIVATE)
        val discovery = PlaceDiscovery(compose.activity)
        discovery.visit(day.minusDays(1)); discovery.visit(day)
        assertEquals(StreakChange.NEXT_DAY.name, prefs.getString("streak_change", null))
        discovery.visit(day.minusDays(10))
        assertEquals(day.minusDays(10).toEpochDay(), prefs.getLong("streak_anchor_day", -1))
        assertEquals("the last day is kept", VisitStreak(2, 2, day, anchor = day.minusDays(10)), discovery.streak())
        assertEquals(StreakChange.NEXT_DAY.name, prefs.getString("streak_change", null))
        discovery.visit(day)
        assertFalse("the anchor is forgotten once the clock is right", prefs.contains("streak_anchor_day"))
        assertEquals(StreakStep(VisitStreak(2, 2, day), StreakChange.NEXT_DAY), discovery.streakOn(day))
    }

    /**
     * Home keeps its scroll across a tab switch, and comes back before it knows its own width: the garden counts as
     * scrolled away from that first frame, so Bert does not spend the day's word where nobody can see him.
     */
    @Test fun homeComingBackScrolledDownDoesNotSpendTheDaysWord() {
        openedFrom(BERTEntry.MARKET)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(300)
        // Scroll the garden away before the word has been up long enough to count, then leave Home.
        compose.onNodeWithTag("board-row").performTouchInput { swipeUp(startY = centerY, endY = centerY - 2_000f, durationMillis = 200) }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("not yet counted as said", null, PlaceDiscovery(compose.activity).daySaidOn())
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(8_000)
        compose.waitForIdle()
        assertEquals("the day's word was spent with the garden scrolled away", null, PlaceDiscovery(compose.activity).daySaidOn())
        compose.mainClock.autoAdvance = true
    }

    /** Opened into Market, Home visited later the same day: Bert says his word on the day once, and not on the next visit. */
    @Test fun homeLaterThatDaySaysTheDayOnce() {
        openedFrom(BERTEntry.MARKET)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(BertLines.streakDay(2, day.dayOfYear), said())
        compose.mainClock.advanceTimeBy(7_000)
        compose.waitForIdle()
        assertEquals(day, PlaceDiscovery(compose.activity).daySaidOn())
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals("said again on a second visit", BertLines.of(BertSlot.NAP, day), said())
        compose.mainClock.autoAdvance = true
    }

    /** What the garden's things opened in 0.14 is one tap away just below it: the slate, the note, the trail section, the Play tab. */
    @Test fun marketTheBoardAndTheTrailAreStillOneTapFromHome() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        home(dispatch(), quote = quote(now - 30_000))
        compose.onNodeWithTag("home-price").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("BERT / USD").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("board-row").performScrollTo().performClick()
        compose.onNodeWithTag("noticeboard").assertExists()
        compose.onNodeWithTag("noticeboard-close").performScrollTo().performClick()
        compose.onNodeWithTag("noticeboard").assertDoesNotExist()
        compose.onNodeWithText("through the gate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Play The Lost Trail").performScrollTo().performClick()
        assertEquals(LostTrailActivity::class.java.name, org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity.component?.className)
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Play The Lost Trail").performClick()
        assertEquals(LostTrailActivity::class.java.name, org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity.component?.className)
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

    /** A bottom sheet is a window of its own: its root, drawn on its own. */
    private fun captureSheet(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        // The newest window the app added (the sheet's), from the window manager's list of root views.
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<*>
        val sheet = views.last() as android.view.View
        val bitmap = Bitmap.createBitmap(sheet.width, sheet.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { sheet.draw(Canvas(bitmap)) }
        val target = File("build/outputs/host-ui/$name.png")
        requireNotNull(target.parentFile).mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
