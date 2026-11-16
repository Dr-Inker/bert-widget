package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.data.QuoteState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * 0.15.1: on a comma-decimal phone a price alert typed as "0,015" is saved as 0.015 (0.15.0 saved it as 15, so it
 * never fired), and the Alerts screen shows the saved level back in the phone's own format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "de-rDE-w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlertLevelsLocaleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val saved = Locale.getDefault()

    @After fun restore() = Locale.setDefault(saved)

    @Test fun aGermanPhoneSavesZeroCommaZeroFifteenAndShowsItBack() {
        Locale.setDefault(Locale.GERMANY)
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        val store = BERTAlertStore(compose.activity)
        compose.setContent {
            BERTTheme {
                Column(Modifier.background(Navy).verticalScroll(rememberScrollState()).padding(20.dp)) { AlertsScreen(QuoteState.Unavailable("No connection"), store) }
            }
        }
        compose.onNodeWithText("Alert above (USD)").performScrollTo().performTextInput("0,015")
        compose.onNodeWithText("Alert below (USD)").performScrollTo().performTextInput("0,0125")
        compose.onNodeWithText("Save price alerts").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0.015, store.settings().priceAbove!!, 0.0)
        assertEquals(0.0125, store.settings().priceBelow!!, 0.0)
        compose.onNodeWithTag("alert-above-level", useUnmergedTree = true).assertTextEquals("Saved: alerts above \$0,015")
        compose.onNodeWithTag("alert-below-level", useUnmergedTree = true).assertTextEquals("Saved: alerts below \$0,0125")
        capture("alerts-saved-level-de")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
