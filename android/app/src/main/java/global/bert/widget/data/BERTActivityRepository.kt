package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BERTActivityRepository(
    context: Context,
    private val http: BERTHttp = NO_REDIRECTS,
    private val url: String = BuildConfig.BERT_ACTIVITY_URL,
) {
    private val preferences = context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE)
    private val journal = BERTJournal(context)

    fun load(): BERTActivity? = preferences.getString("last_valid_activity", null)
        ?.let { runCatching { BERTActivity.parse(it) }.getOrNull() }

    suspend fun refresh(): BERTActivity = withContext(Dispatchers.IO) {
        val raw = http.get(url, MAX_BYTES, 8_000)
        ensureActive()
        val activity = BERTActivity.parse(raw)
        preferences.edit().putString("last_valid_activity", raw).apply()
        journal.record(activity) // every refresh path (app, worker) keeps the dispatch history
        activity
    }

    companion object {
        private const val MAX_BYTES = 32 * 1024

        /** The feed's request: the market requests' timeouts, agent and size cap, but a redirect is refused, not followed. */
        internal val NO_REDIRECTS = BERTHttp { url, maxBytes, readTimeoutMs ->
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            BERTHttp.fetch(connection, maxBytes, readTimeoutMs)
        }
    }
}
