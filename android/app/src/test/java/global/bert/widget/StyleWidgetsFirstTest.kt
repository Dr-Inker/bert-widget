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
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Tools › Style puts the home-screen widgets first, and Home's checklist step "Put Bert on your home screen" lands with
 * them on screen, not on a colour picker (critique 12 Nov: UX-3, product-ux-added-2). A 360 × 640 dp phone is the small
 * common case where the old order left the widgets below the fold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StyleWidgetsFirstTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val graphics = NativeGraphicsOnTestThread()

    @Before fun clean() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        BERTHomeVisits(compose.activity).meetBertDismissed = true
    }

    private fun app(fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BERTTheme {
                    BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Unavailable, emptyList(), BERTPosition(),
                        1_789_000_000_000, false, false, {}, {}, {})
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun top(matcher: SemanticsMatcher) = compose.onNode(matcher, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    @Test fun widgetsComeFirstOnStyle() {
        app()
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Style", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val sections = listOf("intro" to hasText("A little BERT. Everywhere."), "widgets" to hasText("HOME-SCREEN WIDGETS"),
            "app colours" to hasText("APP COLOURS"), "my bert" to hasTestTag("mybert-row"), "phone themes" to hasText("PHONE THEMES"))
        val tops = sections.map { (name, matcher) -> name to top(matcher) }
        assertEquals("Style's sections top to bottom: $tops", sections.map { it.first }, tops.sortedBy { it.second }.map { it.first })
    }

    @Test fun widgetStepLandsOnTheWidgets() = checkWidgetStep(1f, "style-from-checklist")

    @Test fun widgetStepLandsOnTheWidgetsAtLargerText() = checkWidgetStep(1.3f, "style-from-checklist-large")

    /** The smallest common phone at the app's largest text, where the intro wraps the most. */
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun widgetStepLandsOnTheWidgetsAtDoubleText() = checkWidgetStep(2f, "compact-style-from-checklist-large")

    /**
     * Style keeps where it was scrolled for the rest of the session. Scrolled to its end, past the widgets, the step
     * must still land with the widgets heading at the top, not with the card's lower edge lined up at the bottom.
     */
    @Test fun widgetStepLandsOnTheWidgetsAfterStyleWasScrolledPastThem() = checkWidgetStep(1f, "style-from-checklist-after-scroll") {
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Style", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("PHONE THEMES")))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        compose.onNodeWithText("HOME-SCREEN WIDGETS", useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNode(hasText("Home") and isSelectable()).performClick()
        compose.waitForIdle()
    }

    private fun checkWidgetStep(fontScale: Float, render: String, before: () -> Unit = {}) {
        app(fontScale)
        before()
        // The checklist reads its progress off the main thread, so on a loaded host it can land after Home first draws.
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Put Bert on your home screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Put Bert on your home screen").performScrollTo().performClick()
        compose.onNode(hasText("Style") and hasClickAction()).assertIsSelected()
        compose.waitForIdle()
        val heading = compose.onNodeWithText("HOME-SCREEN WIDGETS", useUnmergedTree = true).assertIsDisplayed()
        val viewport = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("HOME-SCREEN WIDGETS"))).getBoundsInRoot()
        val bounds = heading.getUnclippedBoundsInRoot()
        assertTrue("the widgets heading ($bounds) must sit wholly in the first screen ($viewport) at ${fontScale}x",
            bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
        assertTrue("the widgets must open at the top of the screen, not part way down ($bounds in $viewport)",
            bounds.top <= viewport.top + (viewport.bottom - viewport.top) / 4)
        // A widget itself is in view too, not only the label above it.
        compose.onNodeWithContentDescription("Preview of the ${WidgetChoices.first().name} widget").assertIsDisplayed()
        capture(render)
    }
}
