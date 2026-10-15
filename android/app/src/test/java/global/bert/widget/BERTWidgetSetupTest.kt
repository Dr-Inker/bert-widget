package global.bert.widget

import androidx.compose.foundation.verticalScroll
import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Personalise: each widget shows its own preview image above its Add button, under the same name the launcher picker uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h1900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BERTWidgetSetupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyWidgetHasItsPreviewItsNameAndAnAddButton() {
        compose.setContent { BERTTheme { Column(Modifier.background(Navy).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp)) { WidgetSetup(compose.activity) } } }
        for (name in listOf("Bert’s garden", "Bert’s day", "BERT price", "BERT market desk")) {
            compose.onNode(hasContentDescription("Preview of the $name widget")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(name).assertExists()
            compose.onNode(hasText("Add $name") and hasClickAction()).assertExists()
        }
        // The launcher label and the in-app name are the same words.
        val context = compose.activity
        assertEquals(WidgetChoices.map { it.name }.toSet(), setOf(context.getString(R.string.widget_place_name), context.getString(R.string.widget_daily_name), context.getString(R.string.widget_compact_name), context.getString(R.string.widget_market_name)))
        File("build/outputs/host-ui").mkdirs()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(image)) }
        File("build/outputs/host-ui/personalise-widgets.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theWidgetNamesAreUkSpelledPlainAndDistinct() {
        val context = compose.activity
        val names = listOf(R.string.widget_daily_name, R.string.widget_compact_name, R.string.widget_market_name).map { context.getString(it) }
        assertEquals(listOf("Bert’s day", "BERT price", "BERT market desk"), names)
        assertTrue(names.none { it.contains("Compact") || it.contains("·") })
    }
}
