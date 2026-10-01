package global.bert.widget.alerts

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import global.bert.widget.data.AlertSettings
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.work.checkAlerts
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTAlertDeliveryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun seedQuote(price: Double) {
        val envelope = JSONObject()
            .put("asset", JSONObject().put("chain", "solana").put("mint", "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump"))
            .put("quote", JSONObject().put("priceUsd", price).put("change24hPct", 3.1))
            .put("source", JSONObject().put("name", "dexscreener").put("dex", "raydium")
                .put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy")
                .put("observedAt", Instant.ofEpochMilli(System.currentTimeMillis() - 30_000).toString()))
            .put("meta", JSONObject().put("freshness", "fresh"))
        context.getSharedPreferences("bert_quote", Context.MODE_PRIVATE).edit().putString("last_valid_quote", envelope.toString()).commit()
    }

    @Test fun `a missing permission does not use up the alert, and granting it delivers once`() = runBlocking {
        val store = BERTAlertStore(context)
        store.save(AlertSettings(priceAbove = 0.016))
        seedQuote(0.0170)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))

        checkAlerts(context)
        assertFalse("not shown, so not consumed", store.memory().aboveFired)
        assertEquals(0, manager.allNotifications.size)

        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        checkAlerts(context)
        assertTrue(store.memory().aboveFired)
        assertEquals(1, manager.allNotifications.size)
        assertEquals("price", manager.allNotifications.single().channelId)

        checkAlerts(context)
        assertEquals("fires once", 1, manager.allNotifications.size)
    }

    @Test fun `changing a price level re-arms it`() {
        val store = BERTAlertStore(context)
        store.save(AlertSettings(priceAbove = 0.016))
        store.saveMemory(store.memory().copy(aboveFired = true))
        store.save(AlertSettings(priceAbove = 0.018))
        assertFalse(store.memory().aboveFired)
    }
}
