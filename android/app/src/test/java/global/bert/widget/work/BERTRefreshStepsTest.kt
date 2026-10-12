package global.bert.widget.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import global.bert.widget.data.AlertSettings
import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTAlertStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTRefreshStepsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun activity(dispatch: String) = BERTActivity(System.currentTimeMillis() - 60_000, dispatch, "content", null)

    @Test fun `the feed is not fetched when nothing needs it`() = runBlocking {
        var fetched = false
        assertNull(refreshActivityIfNeeded(context) { fetched = true; activity("unused") })
        assertEquals(false, fetched)
    }

    @Test fun `a failed feed fetch falls back to the stored copy`() = runBlocking {
        BERTAlertStore(context).save(AlertSettings(dispatch = true))
        context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit()
            .putString("last_valid_activity", """{"updated_at":${System.currentTimeMillis() / 1000 - 60},"latest_post":"Saved post"}""").commit()
        val result = refreshActivityIfNeeded(context) { error("offline") }
        assertEquals("Saved post", result?.dispatch)
    }

    @Test fun `dispatch alerts still arrive when there is no price at all`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val store = BERTAlertStore(context)
        store.save(AlertSettings(dispatch = true))
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))

        checkAlerts(context, activity("First post")) // baseline, not news
        checkAlerts(context, activity("Second post"))

        assertEquals(1, manager.allNotifications.size)
        assertEquals("dispatch", manager.allNotifications.single().channelId)
    }
}
