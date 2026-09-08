package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
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
        capture("home-activity")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        waitForPreview()
        val preview = compose.onNode(hasContentDescription("Caption card preview:", substring = true)).getUnclippedBoundsInRoot()
        val navigation = compose.onNode(hasText("Create") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue("The complete preview must fit above navigation on first arrival", preview.bottom <= navigation.top)
        capture("create")
        File("build/outputs/host-ui/create-measurements.json").writeText(org.json.JSONObject()
            .put("previewTopDp", preview.top.value).put("previewBottomDp", preview.bottom.value)
            .put("previewHeightDp", (preview.bottom - preview.top).value).put("navigationTopDp", navigation.top.value).toString(2))
        compose.onNodeWithText("Share caption card").performScrollTo().assertIsEnabled()
        capture("caption")
    }

    @Test fun largeTextHomeRemainsNavigable() {
        app(fontScale = 2f)
        val measurements = org.json.JSONArray()
        for (label in listOf("Home", "Explore", "Create", "Tools")) {
            compose.onNode(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val result = layout.single()
            assertEquals("$label must stay on one line at 2x text", 1, result.lineCount)
            // MultiParagraph.width is the offered width, which may exceed Text's wrapped size.
            // Check actual line extents against the measured text box instead of that allocation.
            val left = result.getLineLeft(0)
            val right = result.getLineRight(0)
            val bottom = result.getLineBottom(0)
            assertTrue("$label glyph bounds $left..$right exceed ${result.size}", left >= -0.5f && right <= result.size.width + 0.5f)
            assertTrue("$label is clipped vertically", bottom <= result.size.height + 0.5f)
            measurements.put(org.json.JSONObject().put("label", label).put("lineCount", result.lineCount)
                .put("textBoxWidthPx", result.size.width).put("lineLeftPx", left).put("lineRightPx", right)
                .put("textBoxHeightPx", result.size.height).put("lineBottomPx", bottom))
        }
        capture("home-large")
        File("build/outputs/host-ui/large-nav-measurements.json").writeText(measurements.toString(2))
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().assertIsDisplayed()
        capture("create-large")
    }

    @Test fun coldOfflineHomeKeepsAllDestinationsUsable() {
        app()
        compose.onNodeWithText("Bert’s update couldn’t load.").assertIsDisplayed()
        capture("home-offline")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Flappy Bert").assertIsDisplayed()
        capture("explore")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("BERT amount").assertExists()
        capture("holdings")
    }

    @Test fun createSaveOpenAndDeleteIsACompleteOfflineJourney() {
        app()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("A tiny mayor. A giant weekend.")
        compose.onNodeWithText("Lilac", useUnmergedTree = true).performScrollTo().performClick()
        waitForPreview("A tiny mayor. A giant weekend.")
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("View collection").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View collection").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Open card").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("saved-card-thumbnail", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("saved-collection")
        compose.onNodeWithText("Open card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Share saved card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share saved card").performScrollTo().assertIsEnabled()
        capture("saved-card")
        val persisted = CaptionLibrary(compose.activity).list().single()
        assertEquals("A tiny mayor. A giant weekend.", persisted.caption)
        assertEquals(2, persisted.palette)
        compose.onNodeWithText("Delete card").performScrollTo().performClick()
        compose.onNodeWithText("Keep card").performClick()
        assertEquals(1, CaptionLibrary(compose.activity).list().size)
        compose.onNodeWithText("Delete card").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your first card belongs here.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(CaptionLibrary(compose.activity).list().isEmpty())
    }

    @Test fun captionDraftAndPaletteSurviveSwitchingSections() {
        app()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("Keep my unfinished idea")
        compose.onNodeWithText("Cream", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Keep my unfinished idea").assertExists()
        compose.onNodeWithText("Cream").assertIsSelected()
        waitForPreview("Keep my unfinished idea")
    }

    @Test fun longDispatchCanBeReadWithoutLosingNavigation() {
        val dispatch = "A long dispatch for the pack. ".repeat(20)
        app(ActivityState.Available(BERTActivity(1_789_000_000_000, dispatch, null, null)))
        compose.onNodeWithText("Read full update").performScrollTo().performClick()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(dispatch).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse("Expanded update must expose its full content", layout.single().hasVisualOverflow)
        assertTrue(layout.single().lineCount > 4)
        compose.onNodeWithText("Read less").performScrollTo().performClick()
        compose.onNodeWithText("Tools", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun waitForPreview(caption: String? = null) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Caption card preview:" + (caption?.let { " $it" } ?: ""), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
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
