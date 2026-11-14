package global.bert.widget

import android.app.Application
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every tappable element on every screen has a spoken label. Host semantics, not TalkBack itself.
 * Touch size is deliberately not asserted: Compose extends every clickable's touch area to 48dp (so a touch-bounds
 * check can never fail — proven by shrinking the section tabs to 30dp), and Material's visible sizes (40dp buttons,
 * 32dp chips and switches) are the intended design.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityAuditTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val graphics = NativeGraphicsOnTestThread()

    private fun audit(screen: String): List<String> {
        compose.waitForIdle()
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        return compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes().mapNotNull { node ->
            val bounds = node.boundsInRoot
            // Only judge elements fully on screen; scrolled-off ones are judged on the screen where they appear.
            if (bounds.top < viewport.top || bounds.bottom > viewport.bottom || bounds.width <= 0f) return@mapNotNull null
            val config = node.config
            val label = (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
                .joinToString(" ").trim()
            val problems = listOfNotNull("no label".takeIf { label.isEmpty() })
            if (problems.isEmpty()) null else "$screen › '${label.ifEmpty { "?" }}': ${problems.joinToString()}"
        }
    }

    @Test fun everyTappableElementHasASpokenLabel() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        val now = 1_789_000_000_000
        val quote = QuoteState.Available(BERTQuote(0.0154, 1.6, 15_000_000.0, 130_000.0, 1_100_000.0, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"))
        val history = (0 until 50).map { BERTPriceSample(now - it * 300_000L, 0.015 + it * 0.00001) }.reversed()
        val event = BERTEvent("The Autumn Arc", now + 3 * 86_400_000L, "live", pool = 250, standings = listOf(BERTStanding(1, "LamexCrypt", 143, 100)))
        compose.setContent {
            BERTTheme {
                var position by remember { mutableStateOf(BERTPosition(250_000.0, 1000.0)) }
                BERTApp(quote, ActivityState.Available(BERTActivity(now - 60_000, "woofmornin.", "content", event)), history, position, now, false, false, {}, {}, { position = it })
            }
        }
        val findings = mutableListOf<String>()
        // The screen's page scroller (not a sideways row of tabs), if it has anywhere to go.
        val page = SemanticsMatcher("scrolls vertically") { node ->
            node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { it.maxValue() > 0f } == true
        }
        fun position() = compose.onAllNodes(page).fetchSemanticsNodes().firstOrNull()
            ?.config?.get(SemanticsProperties.VerticalScrollAxisRange)?.let { it.value() to it.maxValue() }
        // Down to the end of the page, judging each viewport. A swipe that does not move the page is a failure, not a
        // shrug: otherwise the controls below it would never be judged (critique 2026-11-12 ENG-8).
        fun scrollThrough(screen: String) {
            findings += audit(screen)
            var swipes = 0
            while (true) {
                val (at, end) = position() ?: return
                if (at >= end) return
                if (++swipes > 30) { findings += "$screen: still not at the end after 30 swipes"; return }
                compose.onAllNodes(page).onFirst().performTouchInput { swipeUp() }
                compose.waitForIdle()
                val moved = position()?.first ?: at
                if (moved <= at) { findings += "$screen: a swipe did not scroll (stuck at $at of $end)"; return }
                findings += audit("$screen (scrolled $swipes)")
            }
        }
        // The You button sits on Home's sky, so it is judged at the top of Home, and the page behind it is scrolled through.
        compose.onNodeWithTag("you-button").performClick()
        scrollThrough("You")
        compose.onNodeWithText("Back").performScrollTo().performClick()
        scrollThrough("Home")
        for ((destination, tabs) in listOf("Play" to listOf<String>(), "Create" to listOf("Art", "Saved"), "Tools" to listOf("Market", "Holdings", "Alerts", "Style"))) {
            compose.onNodeWithText(destination, useUnmergedTree = true).performClick()
            if (tabs.isEmpty()) scrollThrough(destination)
            for (tab in tabs) {
                compose.onNode(hasText(tab) and hasClickAction()).performClick()
                scrollThrough("$destination › $tab")
            }
        }
        val unique = findings.distinct()
        println("A11Y AUDIT:\n" + unique.joinToString("\n"))
        assertTrue(unique.joinToString("\n"), unique.isEmpty())
    }
}
