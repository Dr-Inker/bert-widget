package global.bert.widget

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTEntryTest {
    @Test fun `known entries are read and anything else is ignored`() {
        assertEquals(BERTEntry.MARKET, BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "MARKET")))
        assertEquals(BERTEntry.HOME, BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "HOME")))
        assertNull(BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "market")))
        assertNull(BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "https://evil.example")))
        assertNull(BERTEntry.from(Intent()))
        assertNull(BERTEntry.from(null))
    }

    @Test fun `getting started counts only finished steps and hides when done or dismissed`() {
        val partial = GettingStarted(hidden = false, widget = true, game = false, card = true, alert = false)
        assertEquals(2, partial.done)
        assertEquals(true, partial.visible)
        assertEquals(false, partial.copy(game = true, alert = true).visible)
        assertEquals(false, partial.copy(hidden = true).visible)
    }

    @Test fun `a crash report says what it contains and asks for context`() {
        val text = BERTExitReports.reportText(ExitReport(1_760_000_000_000, "crash", "boom"))
        assert(text.contains("BERT ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) stopped: crash"))
        assert(text.contains("Android’s note: boom"))
        assert(text.trimEnd().endsWith("What were you doing just before?"))
        val uri = BERTExitReports.issueUri(ExitReport(1_760_000_000_000, "crash", null))
        assertEquals("github.com", uri.host)
        assertEquals("/Dr-Inker/bert-widget/issues/new", uri.path)
    }
}
