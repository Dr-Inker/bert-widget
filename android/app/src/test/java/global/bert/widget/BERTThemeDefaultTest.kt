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

    @Test fun aNewInstallGetsTheBrandKitsViolet() {
        val theme = BERTThemeStore(context).load()
        assertEquals(BERTThemePack.BERT_VIOLET, theme)
        // The default widget palette matches the app's default scheme: the brand kit's surfaces, text and muted text,
        // with the kit's violet as the accent stripe.
        val violet = BERTScheme.BERT_VIOLET
        assertEquals(violet.background, androidx.compose.ui.graphics.Color(theme.backgroundArgb))
        assertEquals(violet.panel, androidx.compose.ui.graphics.Color(theme.panelArgb))
        assertEquals(violet.text, androidx.compose.ui.graphics.Color(theme.textArgb))
        assertEquals(violet.muted, androidx.compose.ui.graphics.Color(theme.mutedArgb))
        assertEquals(violet.action, androidx.compose.ui.graphics.Color(theme.accentArgb))
        // Berthalla Navy's pack still matches the original app colours.
        val navy = BERTScheme.BERTHALLA_NAVY
        assertEquals(navy.background, androidx.compose.ui.graphics.Color(BERTThemePack.BERT_NAVY.backgroundArgb))
        assertEquals(navy.action, androidx.compose.ui.graphics.Color(BERTThemePack.BERT_NAVY.accentArgb))
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
