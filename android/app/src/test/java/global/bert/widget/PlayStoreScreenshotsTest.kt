package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Google Play phone screenshots: 9:16 (720x1280), review fixtures only — never live quotes or anyone's holdings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayStoreScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_789_000_000_000

    private fun shot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        check(bitmap.width * 16 == bitmap.height * 9) { "Play screenshots must be 9:16, got ${bitmap.width}x${bitmap.height}" }
        File("build/outputs/play/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun phoneScreenshots() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        val fixture = org.json.JSONObject(requireNotNull(javaClass.classLoader).getResource("history-sample.json").readText()).getJSONArray("minutesBeforeNow")
        val history = (0 until fixture.length()).map { BERTPriceSample(now - fixture.getJSONArray(it).getLong(0) * 60_000, fixture.getJSONArray(it).getDouble(1)) }
        val quote = QuoteState.Available(BERTQuote(history.last().priceUsd, 1.63, 15_067_640.0, 136_801.0, 1_178_452.0, "fresh", now - 30_000,
            "dexscreener", "raydium", "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"))
        val event = BERTEvent("The Autumn Arc", now + (60 * 24 + 7) * 3_600_000L, "live", pool = 250, sponsor = "Dr. Inker LABS",
            standings = listOf(BERTStanding(1, "LamexCrypt", 143, 100), BERTStanding(2, "DrInker", 115, 60), BERTStanding(3, "Md. Shaiful Islam", 114, 40),
                BERTStanding(4, "De_general", 112, 30), BERTStanding(5, "Hameed", 108, 20)))
        val activity = ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the ladybug holds the screen door like the wind can wait its turn.", "content", event))
        compose.setContent {
            BERTTheme {
                var position by remember { mutableStateOf(BERTPosition()) }
                BERTApp(quote, activity, history, position, now, false, false, {}, {}, { position = it })
            }
        }
        shot("1-home")
        compose.onNodeWithText("Hameed").performScrollTo() // all five standings in view
        shot("2-tournament")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        shot("3-explore")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Low ", substring = true).performScrollTo() // the whole chart in view
        shot("4-market")
        compose.onNodeWithText("Alerts", useUnmergedTree = true).performClick()
        shot("5-alerts")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Moonlight").performScrollTo().performClick()
        compose.onNodeWithText("Poster").performScrollTo().performClick()
        compose.onNodeWithText("Story").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Caption card preview:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Caption card preview:", substring = true)).performScrollTo()
        shot("6-create")
    }
}
