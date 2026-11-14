package global.bert.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import global.bert.widget.data.*
import global.bert.widget.widget.WidgetPushGate
import global.bert.widget.widget.pushWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The open app's live data: the quote, Bert's feed, the price history and the person's holdings, and the loops that keep
 * them fresh while the app is on screen. A ViewModel, so a rotation, a dark-mode switch or a new font size keeps all of
 * it and does not fetch the three sources again; a fetch already under way carries on and lands here
 * (critique 2026-11-12 ENG-1 and code-tests-added-1).
 */
internal class BERTLiveModel(
    private val quotes: BERTQuoteRepository,
    private val feed: BERTActivityRepository,
    private val priceHistory: BERTPriceHistory,
    private val marketHistory: BERTMarketHistoryRepository,
    private val holdings: BERTHoldingsStore,
    private val alerts: BERTAlertStore,
    /** The placed widgets get the app's fetches when it leaves the screen; the activity observes it. */
    val widgets: WidgetPushGate,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Whether a newer BERT is on the website; null where nothing should ask (tests, previews). */
    private val updates: BERTUpdateCheck? = null,
) : ViewModel() {
    var quote by mutableStateOf<QuoteState>(quotes.load()?.let { QuoteState.Available(it) } ?: QuoteState.Loading); private set
    var history by mutableStateOf(priceHistory.load()); private set
    var market by mutableStateOf(marketHistory.load()); private set
    var position by mutableStateOf(holdings.loadPosition()); private set
    var refreshing by mutableStateOf(false); private set
    var activity by mutableStateOf<ActivityState>(feed.load()?.let { ActivityState.Available(it) } ?: ActivityState.Loading); private set
    var activityRefreshing by mutableStateOf(false); private set
    var now by mutableLongStateOf(clock()); private set
    /** A newer release on the website (never on a Play install), and whether Home's row for it was hidden. */
    var update by mutableStateOf(updates?.newer()); private set
    var updateHiddenOnHome by mutableStateOf(updates?.homeNotice() == null); private set

    // A fetch in flight is joined, not repeated; each source remembers when its last fetch finished.
    private var quoteJob: Job? = null
    private var feedJob: Job? = null
    private var marketJob: Job? = null
    private var quoteRanAt: Long? = null
    private var feedRanAt: Long? = null
    private var marketRanAt: Long? = null

    fun refresh(): Job = quoteJob?.takeIf { it.isActive } ?: viewModelScope.launch {
        refreshing = true
        try {
            quote = QuoteState.Available(quotes.refresh())
            history = priceHistory.load()
            widgets.changed()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            quote = quotes.load()?.let { QuoteState.Available(it, true) }
                ?: QuoteState.Unavailable("BERT market data is unavailable right now.")
        } finally {
            now = clock()
            quoteRanAt = now
            refreshing = false
        }
    }.also { quoteJob = it }

    fun refreshActivity(): Job = feedJob?.takeIf { it.isActive } ?: viewModelScope.launch {
        activityRefreshing = true
        try {
            val fresh = feed.refresh()
            activity = ActivityState.Available(fresh)
            // Read here, so it is not news for a later dispatch alert.
            fresh.dispatch?.let { alerts.markDispatchSeen(it) }
            widgets.changed() // the Bert widget shows this feed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            activity = ActivityState.afterFailure(error, feed.load())
        } finally {
            now = clock()
            feedRanAt = now
            activityRefreshing = false
        }
    }.also { feedJob = it }

    fun refreshMarketHistory(): Job = marketJob?.takeIf { it.isActive } ?: viewModelScope.launch {
        // Optional enrichment: on failure the chart keeps the cached server history plus device quotes.
        try { market = marketHistory.refresh(); widgets.changed() } // the compact widget's chart
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { market = marketHistory.load() }
        finally { marketRanAt = clock() }
    }.also { marketJob = it }

    fun hideUpdate() {
        val shown = update ?: return
        updates?.dismiss(shown)
        updateHiddenOnHome = true
    }

    fun savePosition(next: BERTPosition) {
        holdings.savePosition(next)
        position = next
        widgets.pushNow()
    }

    /**
     * Runs while the app is on screen: the clock tick and the three refresh loops. A source fetched less than its
     * interval ago (the screen was only recreated) waits out the rest of it rather than fetching again.
     */
    suspend fun keepFresh(): Unit = coroutineScope {
        launch { while (true) { now = clock(); delay(15_000) } }
        launch { every(5 * 60_000L, { feedRanAt }) { refreshActivity() } }
        launch { every(5 * 60_000L, { marketRanAt }) { refreshMarketHistory() } }
        // The website is asked at most once a day (the check keeps its own time); an hourly look is enough to notice the day turn.
        updates?.let { check -> launch {
            while (true) {
                update = check.checkIfDue()
                updateHiddenOnHome = check.homeNotice() == null
                delay(60 * 60_000L)
            }
        } }
        every(60_000L, { quoteRanAt }) { refresh() }
    }

    private suspend fun every(interval: Long, ranAt: () -> Long?, fetch: () -> Job) {
        while (true) {
            val last = ranAt()
            // A clock set back makes the gap negative: wait one interval, never longer.
            val wait = if (last == null) 0L else (interval - (clock() - last)).coerceIn(0L, interval)
            if (wait > 0) delay(wait) else {
                fetch().join()
                if (ranAt() == last) delay(interval) // the fetch never ran (the model is closing): no busy loop
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                return BERTLiveModel(BERTQuoteRepository(app), BERTActivityRepository(app), BERTPriceHistory(app),
                    BERTMarketHistoryRepository(app), BERTHoldingsStore(app), BERTAlertStore(app),
                    WidgetPushGate { pushWidgets(app) }, updates = BERTUpdateCheck(app)) as T
            }
        }
    }
}
