package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/** Host-rendered Compose evidence; does not claim physical-device or launcher coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BERTUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun app(activity: ActivityState = ActivityState.Unavailable, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              BERTTheme {
                BERTApp(QuoteState.Unavailable("No connection"), activity, emptyList(), BERTPosition(),
                    1_789_000_000_000, false, false, {}, {}, {})
              }
            }
        }
    }

    @Test fun activityAndCaptionCreationRender() {
        app(ActivityState.Available(BERTActivity(1_789_000_000_000,
            "The town is quiet. My hat is not. A very good day to make something.", "curious", null)))
        compose.onNodeWithText("The town is quiet. My hat is not. A very good day to make something.").assertIsDisplayed()
        capture("home-activity-before")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        capture("create-before")
        compose.onNodeWithText("Share caption card").performScrollTo()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Caption card preview:", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        capture("caption-before")
    }

    @Test fun largeTextHomeRemainsNavigable() {
        app(fontScale = 2f)
        capture("home-large-before")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().assertIsDisplayed()
        capture("create-large-before")
    }

    @Test fun coldOfflineHomeKeepsAllDestinationsUsable() {
        app()
        compose.onNodeWithText("Bert’s update couldn’t load.").assertIsDisplayed()
        capture("home-offline-before")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Flappy Bert").assertIsDisplayed()
        capture("explore-before")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("BERT amount").assertExists()
        capture("holdings-before")
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
