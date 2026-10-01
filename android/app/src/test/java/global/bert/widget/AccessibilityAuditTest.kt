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
        fun scrollThrough(screen: String) {
            findings += audit(screen)
            repeat(6) { i ->
                runCatching { compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() } }
                findings += audit("$screen (scrolled ${i + 1})")
            }
        }
        scrollThrough("Home")
        for ((destination, tabs) in listOf("Explore" to listOf<String>(), "Create" to listOf("Art", "Saved", "Personalize"), "Tools" to listOf("Market", "Holdings", "Alerts"))) {
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
