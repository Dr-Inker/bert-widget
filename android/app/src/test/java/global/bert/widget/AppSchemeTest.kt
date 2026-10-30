package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
import global.bert.widget.theme.BERTThemeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** App colours: BERT Violet (the brand kit) by default, Berthalla Navy one tap away in Personalise, and the choice kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSchemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_789_000_000_000

    @Before fun clean() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        BERTThemeStore(compose.activity).appSchemeId = null
        BERTColors.scheme = BERTScheme.fromId(null)
        BERTHomeVisits(compose.activity).meetBertDismissed = true
    }

    @After fun reset() {
        BERTColors.scheme = BERTScheme.DEFAULT
    }

    private fun app() = compose.setContent {
        BERTTheme {
            BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the mayor is in.", "content", null)),
                emptyList(), BERTPosition(), now, false, false, {}, {}, {})
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun brandKitIsTheDefaultAndNavyIsOneTapAwayAndKept() {
        assertNull("nobody has chosen yet", BERTThemeStore(compose.activity).appSchemeId)
        assertEquals(BERTScheme.BERT_VIOLET, BERTColors.scheme)
        app()
        capture("scheme-violet-home")

        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Style").performScrollTo().performClick()
        compose.onNodeWithTag("scheme-bert-violet").performScrollTo().assertIsSelected()
        capture("scheme-violet-personalise")

        compose.onNodeWithTag("scheme-berthalla-navy").performClick()
        compose.onNodeWithTag("scheme-berthalla-navy").assertIsSelected()
        compose.onNodeWithTag("scheme-bert-violet").assertIsNotSelected()
        assertEquals(BERTScheme.BERTHALLA_NAVY, BERTColors.scheme)
        assertEquals("berthalla-navy", BERTThemeStore(compose.activity).appSchemeId)
        // What MainActivity reads at the next launch.
        assertEquals(BERTScheme.BERTHALLA_NAVY, BERTScheme.fromId(BERTThemeStore(compose.activity).appSchemeId))
        capture("scheme-navy-personalise")

        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        capture("scheme-navy-home")

        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("scheme-bert-violet").performScrollTo().performClick()
        assertEquals(BERTScheme.BERT_VIOLET, BERTColors.scheme)
        assertEquals("bert-violet", BERTThemeStore(compose.activity).appSchemeId)
    }

    /** The pixel at a node's left, inside its card's padding (8dp to the left of the node). */
    private fun cardPixelLeftOf(text: String): Int {
        val node = compose.onNodeWithText(text).performScrollTo().fetchSemanticsNode()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val b = node.boundsInRoot
        val px = (b.left - 8 * compose.activity.resources.displayMetrics.density).toInt()
        return bitmap.getPixel(px, b.center.y.toInt())
    }

    // 0.13.2 (Antigravity critique 1.4): Phone Themes used to hard-code violet, so it stayed purple under Berthalla Navy.
    @Test fun phoneThemesFollowTheAppColours() {
        app()
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Style").performScrollTo().performClick()
        assertEquals(BERTScheme.BERT_VIOLET.panel.toArgb(), cardPixelLeftOf("PHONE THEMES"))
        compose.onNodeWithTag("scheme-berthalla-navy").performScrollTo().performClick()
        assertEquals(BERTScheme.BERTHALLA_NAVY.panel.toArgb(), cardPixelLeftOf("PHONE THEMES"))
        capture("scheme-navy-phone-themes")
    }

    // 0.13.2 (critique 1.3c): "Home only" applied art the picker never showed; the cards now switch to the Home artwork.
    @Test fun packCardsShowTheHomeArtworkOnTheHomeTab() {
        app()
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Style").performScrollTo().performClick()
        compose.onNodeWithText("Lock screen").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("BERT Navy Lock screen wallpaper preview").assertExists()
        compose.onNodeWithContentDescription("BERT Navy Home screen wallpaper preview").assertDoesNotExist()
        // The switch is one selectable group, so TalkBack reads "1 of 2".
        compose.onNode(hasText("Home screen") and hasClickAction() and
            hasAnyAncestor(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.SelectableGroup))).assertExists()
        compose.onNodeWithText("Home screen").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("BERT Navy Home screen wallpaper preview").assertExists()
        compose.onNodeWithContentDescription("BERT Navy Lock screen wallpaper preview").assertDoesNotExist()
        capture("phone-themes-home-tab")
    }
}
