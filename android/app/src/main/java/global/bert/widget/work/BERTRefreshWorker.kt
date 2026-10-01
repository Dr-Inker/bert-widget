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
import global.bert.widget.data.BERTQuote
import global.bert.widget.data.BERTQuoteRepository
import global.bert.widget.widget.BERTMarketWidgetReceiver
import global.bert.widget.widget.BERTWidgetReceiver
import global.bert.widget.widget.updateAllBERTWidgets
import java.util.concurrent.TimeUnit

class BERTRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        BERTQuoteRepository(applicationContext).refresh()
        updateAllBERTWidgets(applicationContext)
        Result.success()
    } catch (_: Exception) {
        updateAllBERTWidgets(applicationContext)
        Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK = "bert-periodic-quote-refresh"

        /** Background refresh exists only for placed widgets; the open app refreshes itself. */
        fun syncSchedule(context: Context) {
            if (hasPlacedWidgets(context)) schedule(context)
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
            return listOf(BERTWidgetReceiver::class.java, BERTMarketWidgetReceiver::class.java).any {
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
