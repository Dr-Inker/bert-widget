package global.bert.widget

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Home's update row and About's line, for an install from the website that has a newer BERT waiting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateNoticeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = java.time.ZonedDateTime.of(2026, 9, 9, 12, 13, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val quote = QuoteState.Available(BERTQuote(0.0154, 2.1, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
        "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"))
    private val feed = ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the ladybug holds the screen door.", "content", null))

    private fun app(update: @androidx.compose.runtime.Composable () -> AppUpdate?) {
        compose.setContent {
            CompositionLocalProvider(LocalAppUpdate provides update()) {
                BERTTheme { BERTApp(quote, feed, emptyList(), BERTPosition(), now, false, false, {}, {}, {}) }
            }
        }
    }

    @Test fun homeSaysANewerBertIsOutAndGetItOpensTheWebsite() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        var hidden = 0
        app { AppUpdate(BERTRelease("0.14.3", 35), hiddenOnHome = false) { hidden++ } }
        compose.onNodeWithText("BERT 0.14.3 is out").assertIsDisplayed()
        // One quiet row under the price and the board (0.15: the slate and the note), above the rest of Home.
        val row = compose.onNodeWithTag("update-row").getUnclippedBoundsInRoot()
        val price = compose.onNodeWithTag("home-price").getUnclippedBoundsInRoot()
        val note = compose.onNodeWithTag("board-row").getUnclippedBoundsInRoot()
        assert(row.top > price.bottom && row.top > note.bottom) { "the row sits below the slate and the note" }
        capture("home-update-row")
        compose.onNodeWithText("Get it").performClick()
        val opened = requireNotNull(shadowOf(compose.activity).nextStartedActivity) { "Get it opens the browser" }
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://berthalla.io/app/", opened.dataString)
        assertNull("nothing else is started, and nothing is downloaded in the app", shadowOf(compose.activity).nextStartedActivity)
        assertEquals(0, hidden)
    }

    @Test fun hidingTheRowRemovesItFromHomeButAboutStillSaysSo() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        app {
            var hiddenOnHome by remember { mutableStateOf(false) }
            AppUpdate(BERTRelease("0.14.3", 35), hiddenOnHome) { hiddenOnHome = true }
        }
        compose.onNode(hasText("Hide") and hasAnyAncestor(hasTestTag("update-row"))).performClick()
        compose.onAllNodesWithText("BERT 0.14.3 is out").assertCountEquals(0)
        compose.onNodeWithText("About, privacy & feedback", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("BERT 0.14.3 is out", substring = true).assertExists()
        capture("about-update-line")
    }

    @Test fun withNothingNewerHomeAndAboutSayNothing() {
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        app { null }
        compose.onAllNodesWithTag("update-row").assertCountEquals(0)
        compose.onNodeWithText("About, privacy & feedback", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("About BERT").assertExists()
        compose.onAllNodesWithText("is out", substring = true).assertCountEquals(0)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
