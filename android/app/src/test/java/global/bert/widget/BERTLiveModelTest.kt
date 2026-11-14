package global.bert.widget

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import global.bert.widget.data.*
import global.bert.widget.widget.WidgetPushGate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Critique 2026-11-12 ENG-1 and code-tests-added-1: what the open app shows when a fetch fails, and that a recreated
 * screen (rotation, dark mode, font size) keeps its data instead of fetching all three sources again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTLiveModelTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val now = 1_790_869_620_000L

    /**
     * Answers nothing: every request fails as a phone with no signal would. Counts what was asked for. While [hold] is
     * set, a request waits for it, so a test can look at the screen mid-fetch.
     */
    private class OfflineHttp : BERTHttp {
        val asked = ConcurrentHashMap<String, AtomicInteger>()
        @Volatile var hold: CountDownLatch? = null
        fun count(source: String) = asked[source]?.get() ?: 0
        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
            asked.getOrPut(url.substringAfterLast('/')) { AtomicInteger() }.incrementAndGet()
            hold?.await(10, TimeUnit.SECONDS)
            throw IOException("offline")
        }
    }

    private fun model(http: BERTHttp, clock: () -> Long = { now }, widgets: WidgetPushGate = WidgetPushGate {}) = BERTLiveModel(
        BERTQuoteRepository(context, http, directUrl = "", serverUrl = "https://server.test/quote"),
        BERTActivityRepository(context, http, url = "https://server.test/feed"),
        BERTPriceHistory(context),
        BERTMarketHistoryRepository(context, http, directBase = "", serverUrl = "https://server.test/history"),
        BERTHoldingsStore(context), BERTAlertStore(context), widgets, clock,
    )

    /** Answers the quote and Bert's feed as the server would; anything else fails as if offline. */
    private class OnlineHttp : BERTHttp {
        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String = when (url.substringAfterLast('/')) {
            "quote" -> JSONObject()
                .put("asset", JSONObject().put("chain", "solana").put("mint", BERTQuoteRepository.BERT_MINT))
                .put("quote", JSONObject().put("priceUsd", 0.0154).put("change24hPct", 2.1))
                .put("source", JSONObject().put("name", "dexscreener").put("dex", "raydium")
                    .put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy")
                    .put("observedAt", Instant.now().minusSeconds(30).toString()))
                .put("meta", JSONObject().put("freshness", "fresh")).toString()
            "feed" -> """{"updated_at":${System.currentTimeMillis() / 1000 - 60},"latest_post":"a quiet lap of the duck pond."}"""
            else -> throw IOException("offline")
        }
    }

    private fun seedQuote() {
        val envelope = JSONObject()
            .put("asset", JSONObject().put("chain", "solana").put("mint", BERTQuoteRepository.BERT_MINT))
            .put("quote", JSONObject().put("priceUsd", 0.0154).put("change24hPct", 2.1))
            .put("source", JSONObject().put("name", "dexscreener").put("dex", "raydium")
                .put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy")
                .put("observedAt", Instant.ofEpochMilli(now - 30_000).toString()))
            .put("meta", JSONObject().put("freshness", "fresh"))
        context.getSharedPreferences("bert_quote", Context.MODE_PRIVATE).edit().putString("last_valid_quote", envelope.toString()).commit()
    }

    /** Runs the main thread (where the model's coroutines resume) until [done], or fails after ten seconds. */
    private fun settle(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            Thread.sleep(5)
        }
    }

    @After fun restoreFactory() { MainActivity.liveModelFactory = BERTLiveModel::factory }

    @Test fun aFailedFetchWithASavedQuoteShowsTheSavedOneAsDelayed() {
        seedQuote()
        val saved = BERTQuoteRepository(context).load()!!
        val live = model(OfflineHttp())
        assertEquals(QuoteState.Available(saved), live.quote)
        val job = live.refresh()
        settle { job.isCompleted }
        assertEquals(QuoteState.Available(saved, updateDelayed = true), live.quote)
    }

    @Test fun aFailedFetchWithNothingSavedIsUnavailableAndRefreshingEnds() {
        val http = OfflineHttp()
        val live = model(http)
        assertEquals(QuoteState.Loading, live.quote)
        http.hold = CountDownLatch(1)
        val job = live.refresh()
        assertTrue("refreshing while the fetch is out", live.refreshing)
        assertTrue("a second tap joins the fetch under way", live.refresh() === job)
        http.hold?.countDown()
        settle { job.isCompleted }
        assertEquals(QuoteState.Unavailable("BERT market data is unavailable right now."), live.quote)
        assertFalse("refreshing resets after a failure", live.refreshing)
        assertEquals(1, http.count("quote"))
        // The feed's failure, with nothing saved, is the connection, and its flag resets too.
        http.hold = CountDownLatch(1)
        val feed = live.refreshActivity()
        assertTrue(live.activityRefreshing)
        http.hold?.countDown()
        settle { feed.isCompleted }
        assertEquals(ActivityState.Unavailable, live.activity)
        assertFalse(live.activityRefreshing)
    }

    @Test fun recreatingTheActivityDoesNotFetchAgain() {
        val http = OfflineHttp()
        MainActivity.liveModelFactory = {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = model(http) as T
            }
        }
        val screen = Robolectric.buildActivity(MainActivity::class.java).setup()
        settle { http.count("quote") >= 1 && http.count("feed") >= 1 && http.count("history") >= 1 }
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)); Thread.sleep(5) }
        val before = listOf("quote", "feed", "history").associateWith(http::count)
        assertEquals(mapOf("quote" to 1, "feed" to 1, "history" to 1), before)

        screen.recreate() // a rotation, a dark-mode switch or a new font size
        repeat(40) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)); Thread.sleep(5) }
        assertEquals("no source is fetched again", before, listOf("quote", "feed", "history").associateWith(http::count))
        screen.pause().stop().destroy()
    }

    /**
     * Critique 2026-11-12 ENG-4, through the app's own fetch path: while the app is on screen its once-a-minute quotes
     * and the feed only mark the widgets' data as changed, and one push follows when the app leaves the screen. A fetch
     * that lands after that (Home pressed mid-fetch) still reaches the widgets, at once.
     */
    @Test fun theAppsFetchesReachTheWidgetsOnceWhenItStopsAndALateOneAtOnce() {
        var pushes = 0
        val gate = WidgetPushGate { pushes++ }
        val screen = object : LifecycleOwner {
            val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        screen.registry.addObserver(gate)
        screen.registry.currentState = Lifecycle.State.RESUMED
        val live = model(OnlineHttp(), widgets = gate)
        repeat(3) {
            val job = live.refresh()
            settle { job.isCompleted }
            assertTrue("the quote arrived", live.quote is QuoteState.Available && !(live.quote as QuoteState.Available).updateDelayed)
        }
        val feed = live.refreshActivity()
        settle { feed.isCompleted }
        assertTrue("the feed arrived", live.activity is ActivityState.Available)
        assertEquals("no widget push while the app covers the home screen", 0, pushes)
        screen.registry.currentState = Lifecycle.State.CREATED // ON_PAUSE, ON_STOP
        assertEquals("one push when the app leaves the screen", 1, pushes)
        val late = live.refresh()
        settle { late.isCompleted }
        assertEquals("a quote that lands after the app stopped is pushed straight away", 2, pushes)
    }
}
