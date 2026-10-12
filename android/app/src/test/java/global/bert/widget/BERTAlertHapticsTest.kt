package global.bert.widget

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.data.QuoteState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Switching an alert on or off gives the toggle haptic, and each press gives exactly one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BERTAlertHapticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun alertSwitchesGiveToggleFeedbackOnAndOff() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        val felt = mutableListOf<HapticFeedbackType>()
        val recorder = object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { felt += hapticFeedbackType } }
        val store = BERTAlertStore(compose.activity)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                Column(Modifier.verticalScroll(rememberScrollState())) { AlertsScreen(QuoteState.Unavailable("No connection"), store) }
            }
        }
        val tournament = hasContentDescription("Tournament ending") and isToggleable()
        compose.onNode(tournament).performScrollTo().performClick()
        assertEquals(listOf(HapticFeedbackType.ToggleOn), felt)
        compose.onNode(tournament).performScrollTo().performClick()
        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff), felt)
        val bigMove = hasContentDescription("Big move") and isToggleable()
        compose.onNode(bigMove).performScrollTo().performClick()
        assertEquals(HapticFeedbackType.ToggleOn, felt.last())
        assertEquals(3, felt.size)
    }
}
