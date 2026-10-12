package global.bert.widget

import android.app.Application
import global.bert.widget.theme.BERTThemePack
import global.bert.widget.theme.BERTThemeStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTThemeDefaultTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun aNewInstallGetsTheAppsOwnNavyAndOrange() {
        val theme = BERTThemeStore(context).load()
        assertEquals(BERTThemePack.BERT_NAVY, theme)
        // The widget palette is the app's own: same navy, panel, cream, muted grey and orange as the Compose theme.
        assertEquals(Navy, androidx.compose.ui.graphics.Color(theme.backgroundArgb))
        assertEquals(Panel, androidx.compose.ui.graphics.Color(theme.panelArgb))
        assertEquals(Cream, androidx.compose.ui.graphics.Color(theme.textArgb))
        assertEquals(Muted, androidx.compose.ui.graphics.Color(theme.mutedArgb))
        assertEquals(Orange, androidx.compose.ui.graphics.Color(theme.accentArgb))
        assertEquals(0xFF071A2FL, theme.backgroundArgb)
        assertEquals(0xFF0D2942L, theme.panelArgb)
        assertEquals(0xFFFF9433L, theme.accentArgb)
    }

    @Test fun anExistingUsersSavedChoiceIsKept() {
        for (saved in listOf(BERTThemePack.WOOFHUB_NIGHT, BERTThemePack.MAYOR_PURPLE, BERTThemePack.BERTHALLA_NIGHTS)) {
            BERTThemeStore(context).save(saved)
            assertEquals(saved, BERTThemeStore(context).load())
        }
        // Even someone who chose the old default keeps it.
        BERTThemeStore(context).save(BERTThemePack.WOOFHUB_NIGHT)
        assertEquals(BERTThemePack.WOOFHUB_NIGHT, BERTThemeStore(context).load())
    }
}
