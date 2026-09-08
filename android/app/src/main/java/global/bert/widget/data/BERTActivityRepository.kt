package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BERTActivityRepository(context: Context) {
    private val preferences = context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE)

    fun load(): BERTActivity? = preferences.getString("last_valid_activity", null)
        ?.let { runCatching { BERTActivity.parse(it) }.getOrNull() }

    suspend fun refresh(): BERTActivity = withContext(Dispatchers.IO) {
        val connection = URL(BuildConfig.BERT_ACTIVITY_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            require(connection.responseCode == 200) { "Activity source unavailable" }
            require(connection.contentLengthLong <= 32 * 1024) { "Activity response too large" }
            val raw = connection.inputStream.use { BERTQuoteRepository.readUtf8WithLimit(it, 32 * 1024) }
            ensureActive()
            val activity = BERTActivity.parse(raw)
            preferences.edit().putString("last_valid_activity", raw).apply()
            activity
        } finally {
            connection.disconnect()
        }
    }
}
