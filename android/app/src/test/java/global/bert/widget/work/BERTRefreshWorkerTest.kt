package global.bert.widget.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Looper
import java.time.Duration
import global.bert.widget.widget.BERTWidget
import android.content.ComponentName
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import global.bert.widget.widget.BERTWidgetReceiver
import global.bert.widget.widget.REVISION
import global.bert.widget.data.AlertSettings
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.data.BERTHttp
import global.bert.widget.data.BERTMarketHistoryRepository
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.widget.BERTDailyWidgetReceiver
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
import java.io.IOException
import java.time.Instant
import java.util.Collections

/**
 * Critique 2026-11-12 ENG-3: the background run that keeps widgets and alerts alive once the app is closed. Each step
 * stands alone, it retries only briefly, and it is scheduled exactly while something needs it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTRefreshWorkerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Answers each source from [answers] (by the last part of its URL); a source left out fails as if offline. */
    private class FakeHttp(private val answers: Map<String, () -> String>) : BERTHttp {
        val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
            val source = url.substringAfterLast('/')
            asked += source
            return answers[source]?.invoke() ?: throw IOException("offline")
        }
    }

    private fun quote() = JSONObject()
        .put("asset", JSONObject().put("chain", "solana").put("mint", BERTQuoteRepository.BERT_MINT))
        .put("quote", JSONObject().put("priceUsd", 0.0154))
        .put("source", JSONObject().put("name", "dexscreener").put("dex", "raydium")
            .put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy")
            .put("observedAt", Instant.now().minusSeconds(30).toString()))
        .put("meta", JSONObject().put("freshness", "fresh")).toString()

    private fun feed(post: String) = """{"updated_at":${System.currentTimeMillis() / 1000 - 60},"latest_post":"$post"}"""

    private fun run(http: BERTHttp, attempt: Int = 0, steps: MutableList<String> = mutableListOf(), render: () -> Unit = {}) = runBlocking {
        refreshInBackground(context, attempt,
            quotes = BERTQuoteRepository(context, http, directUrl = "", serverUrl = "https://server.test/quote"),
            feed = BERTActivityRepository(context, http, url = "https://server.test/feed"),
            history = BERTMarketHistoryRepository(context, http, directBase = "", serverUrl = "https://server.test/history"),
            render = { steps += "render"; render() })
    }

    @Test fun aFailedQuoteStillFetchesTheFeedSendsItsAlertAndRendersTheWidgets() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val store = BERTAlertStore(context)
        store.save(AlertSettings(dispatch = true))
        store.saveMemory(store.memory().copy(lastDispatch = "First post"))
        val http = FakeHttp(mapOf("feed" to { feed("Second post") }))
        val steps = mutableListOf<String>()

        val result = run(http, steps = steps)

        assertEquals(listOf("quote", "feed", "history"), http.asked)
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals("the feed's alert still arrives", listOf("dispatch"), shown.map { it.channelId })
        assertEquals("Second post", store.memory().lastDispatch)
        assertEquals("the widgets are still rendered", listOf("render"), steps)
        assertEquals("a failed quote retries", ListenableWorker.Result.retry(), result)
    }

    @Test fun theWidgetsAreRenderedAfterEverySourceOnASuccessfulRun() {
        val steps = Collections.synchronizedList(mutableListOf<String>())
        val http = object : BERTHttp {
            override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
                steps += url.substringAfterLast('/')
                if (url.endsWith("quote")) return quote()
                throw IOException("offline")
            }
        }
        BERTAlertStore(context).save(AlertSettings(dispatch = true)) // so the feed is wanted
        assertEquals(ListenableWorker.Result.success(), run(http, steps = steps))
        assertEquals(listOf("quote", "feed", "history", "render"), steps)
        assertEquals("the fetched quote is what the widgets read", 0.0154, BERTQuoteRepository(context).load()!!.priceUsd, 0.0)
    }

    @Test fun itRetriesOnlyBrieflyAndOnlyWhenTheQuoteOrTheRenderFailed() {
        val online = FakeHttp(mapOf("quote" to ::quote))
        val offline = FakeHttp(emptyMap())
        assertEquals(ListenableWorker.Result.success(), run(online))
        assertEquals("a render that fails retries", ListenableWorker.Result.retry(), run(online) { error("launcher said no") })
        assertEquals(ListenableWorker.Result.retry(), run(offline, attempt = 0))
        assertEquals(ListenableWorker.Result.retry(), run(offline, attempt = 1))
        assertEquals("the third attempt gives up until the next period", ListenableWorker.Result.success(), run(offline, attempt = 2))
    }

    @Test fun backgroundRefreshIsScheduledOnlyWhileAWidgetIsPlacedOrAnAlertIsOn() {
        // As the UI tests do: the host test application does not initialise WorkManager itself.
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
        val work = WorkManager.getInstance(context)
        fun scheduled() = work.getWorkInfosForUniqueWork(BERTRefreshWorker.PERIODIC_WORK).get().any { !it.state.isFinished }

        BERTRefreshWorker.syncSchedule(context)
        assertFalse("nothing needs it", scheduled())

        BERTAlertStore(context).save(AlertSettings(priceBelow = 0.01))
        BERTRefreshWorker.syncSchedule(context)
        assertTrue("an alert is on, no widget placed", scheduled())

        BERTAlertStore(context).save(AlertSettings())
        BERTRefreshWorker.syncSchedule(context)
        assertFalse("the last alert switched off", scheduled())

        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(7, ComponentName(context, BERTDailyWidgetReceiver::class.java))
        BERTRefreshWorker.syncSchedule(context)
        assertTrue("a widget is placed, no alert on", scheduled())
    }

    /**
     * The real render, not a stand-in, after a successful run (review of 0.14.2, item 5.3): a placed widget's revision
     * goes up, so a live widget reloads the fresh quote, and the re-render for when that quote turns stale is set.
     */
    @Test fun aSuccessfulRunRendersThePlacedWidgetsForReal() = runBlocking {
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
        // Placed as a launcher places one: the provider is installed, an id is bound to it and the receiver hears of it,
        // which is how Glance learns which of its widgets that id belongs to.
        val receiver = ComponentName(context, BERTWidgetReceiver::class.java)
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        manager.addInstalledProvider(AppWidgetProviderInfo().apply { provider = receiver })
        manager.bindAppWidgetId(11, receiver)
        context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(receiver)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(11)))
        val deadline = System.currentTimeMillis() + 15_000
        while (GlanceAppWidgetManager(context).getGlanceIds(BERTWidget::class.java).isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "the receiver never registered the widget" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            Thread.sleep(10)
        }
        val http = FakeHttp(mapOf("quote" to ::quote))
        val result = refreshInBackground(context, 0,
            quotes = BERTQuoteRepository(context, http, directUrl = "", serverUrl = "https://server.test/quote"),
            feed = BERTActivityRepository(context, http, url = "https://server.test/feed"),
            history = BERTMarketHistoryRepository(context, http, directBase = "", serverUrl = "https://server.test/history"))
        assertEquals("the quote came and the render did not fail", ListenableWorker.Result.success(), result)
        val stale = WorkManager.getInstance(context).getWorkInfosForUniqueWork("bert-widget-stale-render").get()
        assertTrue("the re-render for when the quote turns stale is set: $stale", stale.any { it.state == WorkInfo.State.ENQUEUED })
        val id = GlanceAppWidgetManager(context).getGlanceIdBy(11)
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        assertEquals("the placed widget was rendered once", 1, state[REVISION])
    }
}
