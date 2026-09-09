package global.bert.widget

import android.app.Application
import android.net.Uri
import android.webkit.WebSettings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LostTrailTest {
    @Test fun packagedDocumentIsAvailableOfflineAndRemoteContentIsDenied() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val response = LostTrailContent.response(context, Uri.parse(LostTrailContent.URL))
        assertEquals(200, response.statusCode)
        val html = response.data.bufferedReader().readText()
        assertTrue(html.contains("The Lost Trail"))
        assertTrue(html.contains("const ART="))
        assertTrue(html.contains("window.BertAppGame="))
        assertTrue(response.responseHeaders["Content-Security-Policy"]!!.contains("connect-src 'none'"))
        for (url in listOf("https://example.com", "file:///etc/passwd", "content://global.bert.widget.captions/a", LostTrailContent.URL + "&qa", "https://appassets.androidplatform.net/lost-trail/../index.html?app")) {
            assertFalse(LostTrailContent.allows(Uri.parse(url)))
            assertEquals(403, LostTrailContent.response(context, Uri.parse(url)).statusCode)
        }
    }

    @Test fun activityUsesIsolatedDocumentAndPausesAcrossBackgroundEntry() {
        val controller = Robolectric.buildActivity(LostTrailActivity::class.java).setup()
        val activity = controller.get()
        val view = activity.gameView
        val shadow = shadowOf(view)
        assertEquals(LostTrailContent.URL, shadow.lastLoadedUrl)
        assertTrue(view.settings.javaScriptEnabled)
        assertTrue(view.settings.domStorageEnabled)
        assertTrue(view.settings.blockNetworkLoads)
        assertFalse(view.settings.allowFileAccess)
        assertFalse(view.settings.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(shadow.lastEvaluatedJavascript.contains("BertAppGame.back()"))
        controller.pause()
        assertEquals("window.BertAppGame?.pause()", shadow.lastEvaluatedJavascript)
        controller.resume()
        assertEquals("window.BertAppGame?.pause()", shadow.lastEvaluatedJavascript)
        controller.pause().stop().destroy()
    }
}
