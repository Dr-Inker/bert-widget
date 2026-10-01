package global.bert.widget.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import global.bert.widget.alerts.BERTNotifier
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.data.BERTAlertRules
import global.bert.widget.data.BERTAlertStore
import global.bert.widget.data.BERTMarketHistoryRepository
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.widget.BERTDailyWidgetReceiver
import global.bert.widget.widget.BERTMarketWidgetReceiver
import global.bert.widget.widget.BERTWidgetReceiver
import global.bert.widget.widget.updateAllBERTWidgets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** The Bert widget shows Bert's feed, so keep it current while one is placed. */
internal suspend fun refreshActivityForDailyWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    if (manager.getAppWidgetIds(ComponentName(context, BERTDailyWidgetReceiver::class.java)).isEmpty()) return
    try { BERTActivityRepository(context).refresh() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { }
}

/** Evaluates switched-on alerts against the freshly stored quote and (when needed) Bert's activity feed. */
internal suspend fun checkAlerts(context: Context, now: Long = System.currentTimeMillis()) {
    val store = BERTAlertStore(context)
    val settings = store.settings()
    if (!settings.anyEnabled) return
    val activity = if (settings.tournament || settings.dispatch) {
        // Refreshed below for the Bert widget too; this read uses the stored copy when that already ran.
        try { BERTActivityRepository(context).refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { BERTActivityRepository(context).load() }
    } else null
    val (notices, memory) = BERTAlertRules.evaluate(settings, store.memory(), BERTQuoteRepository(context).load(), activity, now)
    // Memory only advances for notices that were actually shown (or need no showing), so a missing
    // permission does not silently consume an alert.
    if (notices.isEmpty() || BERTNotifier.post(context, notices) > 0) store.saveMemory(memory)
}

class BERTRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        BERTQuoteRepository(applicationContext).refresh()
        refreshActivityForDailyWidget(applicationContext)
        checkAlerts(applicationContext)
        // Best effort: the sparkline falls back to cached history plus device quotes.
        try { BERTMarketHistoryRepository(applicationContext).refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        updateAllBERTWidgets(applicationContext)
        Result.success()
    } catch (_: Exception) {
        updateAllBERTWidgets(applicationContext)
        Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK = "bert-periodic-quote-refresh"

        /** Background refresh runs for placed widgets or switched-on alerts; the open app refreshes itself. */
        fun syncSchedule(context: Context) {
            if (hasPlacedWidgets(context) || BERTAlertStore(context).settings().anyEnabled) schedule(context)
            else WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BERTRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun hasPlacedWidgets(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            return listOf(BERTWidgetReceiver::class.java, BERTMarketWidgetReceiver::class.java, BERTDailyWidgetReceiver::class.java).any {
                manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
            }
        }
    }
}

/**
 * Re-renders widgets when the displayed quote crosses the staleness threshold. The refresh worker
 * needs a network, so without this an offline phone would keep showing "UPDATED" over an old price.
 */
class BERTWidgetStaleRenderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        updateAllBERTWidgets(applicationContext, scheduleStaleRender = false)
        return Result.success()
    }

    companion object {
        private const val STALE_RENDER_WORK = "bert-widget-stale-render"
        private const val SLACK_MILLIS = 5_000L

        internal fun delayUntilStaleMillis(quote: BERTQuote?, nowEpochMillis: Long): Long? {
            if (quote == null || quote.isStaleAt(nowEpochMillis)) return null
            return quote.observedAtEpochMillis + BERTQuote.STALE_AFTER_MILLIS - nowEpochMillis + SLACK_MILLIS
        }

        fun schedule(context: Context, quote: BERTQuote?, nowEpochMillis: Long = System.currentTimeMillis()) {
            val delay = delayUntilStaleMillis(quote, nowEpochMillis) ?: return
            if (!BERTRefreshWorker.hasPlacedWidgets(context)) return
            // No network constraint: the point is to run while offline.
            val request = OneTimeWorkRequestBuilder<BERTWidgetStaleRenderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(STALE_RENDER_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
