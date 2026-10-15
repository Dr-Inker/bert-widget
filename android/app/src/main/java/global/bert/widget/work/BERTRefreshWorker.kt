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
import global.bert.widget.data.BERTActivity
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
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches Bert's feed once per run when the Bert widget or a feed-based alert needs it. A failed fetch
 * falls back to the stored copy, so tournament and dispatch alerts still see the last valid feed.
 */
internal suspend fun refreshActivityIfNeeded(
    context: Context,
    fetch: suspend () -> BERTActivity = { BERTActivityRepository(context).refresh() },
): BERTActivity? {
    val alerts = BERTAlertStore(context).settings()
    if (!hasPlacedDailyWidget(context) && !alerts.tournament && !alerts.dispatch) return null
    return try { fetch() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { BERTActivityRepository(context).load() }
}

private fun hasPlacedDailyWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    // Both Bert widgets need his feed: the day widget for the update, the garden widget for his mood.
    return listOf(BERTDailyWidgetReceiver::class.java, global.bert.widget.widget.BERTPlaceWidgetReceiver::class.java)
        .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
}

/** Evaluates switched-on alerts against the stored quote and the given (or stored) copy of Bert's feed. */
internal fun checkAlerts(
    context: Context,
    activity: BERTActivity? = BERTActivityRepository(context).load(),
    now: Long = System.currentTimeMillis(),
) {
    val store = BERTAlertStore(context)
    val settings = store.settings()
    if (!settings.anyEnabled) return
    val (notices, memory) = BERTAlertRules.evaluate(settings, store.memory(), BERTQuoteRepository(context).load(), activity, now,
        use24Hour = android.text.format.DateFormat.is24HourFormat(context))
    // Memory only advances for notices that were actually shown (or need no showing), so a missing
    // permission does not silently consume an alert.
    if (notices.isEmpty() || BERTNotifier.post(context, notices) > 0) store.saveMemoryIfUnchanged(memory, settings)
}

/** Runs one refresh step; cancellation propagates, any other failure is reported as false. */
private inline fun bestEffort(step: () -> Unit): Boolean =
    try { step(); true }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }

class BERTRefreshWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // Each step stands alone: a failed quote must not suppress feed alerts, the Bert widget or a re-render.
        val quoteRefreshed = bestEffort { BERTQuoteRepository(applicationContext).refresh() }
        val activity = refreshActivityIfNeeded(applicationContext)
        bestEffort { checkAlerts(applicationContext, activity) }
        // Best effort: the sparkline falls back to cached history plus device quotes.
        bestEffort { BERTMarketHistoryRepository(applicationContext).refresh() }
        val rendered = bestEffort { updateAllBERTWidgets(applicationContext) }
        // Periodic-work retries back off up to 5 hours and replace the 15-minute cadence, so retry only briefly;
        // after that the next period tries again on schedule.
        return if ((quoteRefreshed && rendered) || runAttemptCount >= 2) Result.success() else Result.retry()
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
            return listOf(BERTWidgetReceiver::class.java, BERTMarketWidgetReceiver::class.java, BERTDailyWidgetReceiver::class.java,
                global.bert.widget.widget.BERTPlaceWidgetReceiver::class.java).any {
                manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
            }
        }
    }
}

/**
 * Re-renders widgets when the displayed quote or Bert's feed crosses its staleness threshold, and at local
 * midnight for the Bert widget's time. The refresh worker needs a network, so without this an offline phone
 * would keep showing "UPDATED" over old data, or yesterday's time without its date.
 */
class BERTWidgetStaleRenderWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // Re-arms itself: every delay below is at least SLACK_MILLIS, and midnight comes once a day.
        updateAllBERTWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val STALE_RENDER_WORK = "bert-widget-stale-render"
        private const val SLACK_MILLIS = 5_000L

        internal fun delayUntilStaleMillis(quote: BERTQuote?, nowEpochMillis: Long): Long? {
            if (quote == null || quote.isStaleAt(nowEpochMillis)) return null
            return quote.observedAtEpochMillis + BERTQuote.STALE_AFTER_MILLIS - nowEpochMillis + SLACK_MILLIS
        }

        internal fun delayUntilFeedStaleMillis(activity: BERTActivity?, nowEpochMillis: Long): Long? {
            if (activity == null || activity.isStaleAt(nowEpochMillis)) return null
            return activity.updatedAtEpochMillis + 30 * 60_000L - nowEpochMillis + SLACK_MILLIS
        }

        internal fun delayUntilMidnightMillis(nowEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
            val midnight = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
            return midnight.toInstant().toEpochMilli() - nowEpochMillis + SLACK_MILLIS
        }

        /** The soonest moment a placed widget's words go out of date: quote or feed turning stale, or a new day. */
        internal fun nextRenderDelayMillis(quote: BERTQuote?, activity: BERTActivity?, nowEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
            listOfNotNull(
                delayUntilStaleMillis(quote, nowEpochMillis),
                delayUntilFeedStaleMillis(activity, nowEpochMillis),
                activity?.let { delayUntilMidnightMillis(nowEpochMillis, zone) },
            ).minOrNull()

        fun schedule(context: Context, quote: BERTQuote?, nowEpochMillis: Long = System.currentTimeMillis()) {
            val delay = nextRenderDelayMillis(quote, BERTActivityRepository(context).load(), nowEpochMillis) ?: return
            if (!BERTRefreshWorker.hasPlacedWidgets(context)) return
            // No network constraint: the point is to run while offline.
            val request = OneTimeWorkRequestBuilder<BERTWidgetStaleRenderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(STALE_RENDER_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
