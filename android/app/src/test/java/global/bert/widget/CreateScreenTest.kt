package global.bert.widget

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** Create (0.9.1): the art thumbnails, Share as the primary action, haptics and a preview that never blanks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreateScreenTest {
    @Before @After fun freshShareCache() = forgetFileProviderPaths()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val haptics = mutableListOf<HapticFeedbackType>()

    private fun openCreate(fontScale: Float = 1f) {
        val recorder = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BERTTheme {
                    var position by remember { mutableStateOf(BERTPosition()) }
                    BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Unavailable, emptyList(), position,
                        1_789_000_000_000, false, false, {}, {}, { position = it })
                }
            }
        }
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
    }

    private fun waitForPreview(caption: String? = null) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasContentDescription("Caption card preview:" + (caption?.let { " $it" } ?: ""), substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test fun artThumbnailsAreLabelledTargetsAndChooseTheCardArt() {
        openCreate()
        waitForPreview()
        for (art in CardArt.BUILT_IN) {
            compose.onNode(hasContentDescription(art.description) and hasClickAction())
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription(CardArt.PHOTO.description).assertIsSelected()
        compose.onNodeWithContentDescription(CardArt.MAYOR.description).performScrollTo().performClick()
        compose.onNodeWithContentDescription(CardArt.MAYOR.description).assertIsSelected()
        compose.onNodeWithContentDescription(CardArt.PHOTO.description).assertIsNotSelected()
        assertTrue(HapticFeedbackType.SegmentTick in haptics)
        compose.onNodeWithText("Poster", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Poster").assertIsSelected()
        compose.onNodeWithText("Note").assertIsNotSelected()
        // The chosen thumbnail is the art the saved card carries.
        waitForPreview()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("art-thumbnail", useUnmergedTree = true).fetchSemanticsNodes().size == CardArt.BUILT_IN.size }
        compose.onNodeWithText("Lilac").performScrollTo()
        capture("create-art-picker")
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) { CaptionLibrary(compose.activity).list().isNotEmpty() }
        assertEquals(CardStyle(CardArt.MAYOR, CardLayout.POSTER, CardFormat.SQUARE), CaptionLibrary(compose.activity).list().single().style)
    }

    @Test fun shareIsTheFilledPrimaryActionAndBothActionsConfirmWithAHaptic() {
        openCreate()
        waitForPreview()
        compose.onNodeWithTag("create-save").performScrollTo()
        val shareBounds = compose.onNodeWithTag("create-share").assertIsEnabled().assertIsDisplayed().getUnclippedBoundsInRoot()
        val saveBounds = compose.onNodeWithTag("create-save").getUnclippedBoundsInRoot()
        assertTrue("Share comes before Save", shareBounds.bottom <= saveBounds.top)
        assertTrue("Share is the larger button", shareBounds.bottom - shareBounds.top > saveBounds.bottom - saveBounds.top)
        // Filled in the scheme's action colour (BERT Violet by default); Save is the quieter tonal button.
        val screen = capture("create-actions")
        val density = compose.activity.resources.displayMetrics.density
        fun colourAt(bounds: androidx.compose.ui.unit.DpRect) = screen.getPixel(((bounds.left.value + 14) * density).toInt(), ((bounds.top.value + bounds.bottom.value) / 2 * density).toInt())
        fun near(actual: Int, expected: Long) = listOf(16, 8, 0).all { shift -> abs((actual shr shift and 0xFF) - (expected.toInt() shr shift and 0xFF)) <= 6 }
        assertTrue("Share is filled with the action colour", near(colourAt(shareBounds), Orange.toArgb().toLong() and 0xFFFFFFFFL))
        assertTrue("Save is tonal", near(colourAt(saveBounds), PanelStrong.toArgb().toLong() and 0xFFFFFFFFL))

        compose.onNodeWithText("Share caption card").performClick()
        compose.waitUntil(15_000) { HapticFeedbackType.Confirm in haptics }
        val started = requireNotNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        haptics.clear()
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Saved to your collection").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(HapticFeedbackType.Confirm in haptics)
    }

    @Test fun previewKeepsThePreviousCardWhileTheNextRenders() {
        openCreate()
        waitForPreview("woofmornin. the mayor is in.")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("A hat for every weather.")
        compose.mainClock.advanceTimeByFrame()
        // Mid-edit: the previous card stays, nothing blanks, and the stale card cannot be saved.
        compose.onNodeWithContentDescription("Updating card preview").assertExists()
        compose.onAllNodesWithText("Updating preview…").assertCountEquals(0)
        compose.onNodeWithTag("create-save").assertIsNotEnabled()
        compose.mainClock.autoAdvance = true
        waitForPreview("A hat for every weather.")
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Caption card preview:", substring = true)).assertCountEquals(1)
        compose.onNodeWithTag("create-save").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun choicesStackReadablyAtLargeText() {
        openCreate(fontScale = 2f)
        waitForPreview()
        for (label in listOf("Midnight", "Cream", "Lilac", "Note", "Poster", "Square", "Story")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Lilac").performScrollTo()
        capture("create-choices-large")
    }
}
