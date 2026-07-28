package global.bert.widget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import global.bert.widget.data.BERTQuoteRepository
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
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BERTRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "bert-periodic-quote-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
