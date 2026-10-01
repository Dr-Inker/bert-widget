package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Server-provided 24-hour price history (5-minute closes), cached for offline display. */
class BERTMarketHistoryRepository(context: Context) {
    private val preferences = context.getSharedPreferences("bert_market_history", Context.MODE_PRIVATE)

    suspend fun refresh(nowEpochMillis: Long = System.currentTimeMillis()): List<BERTPriceSample> = withContext(Dispatchers.IO) {
        val connection = URL(BuildConfig.BERT_HISTORY_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("History service returned HTTP ${connection.responseCode}")
            val raw = connection.inputStream.use { BERTQuoteRepository.readUtf8WithLimit(it, MAX_RESPONSE_BYTES) }
            val samples = parse(raw, nowEpochMillis)
            preferences.edit().putString(KEY, raw).apply()
            samples
        } finally {
            connection.disconnect()
        }
    }

    fun load(nowEpochMillis: Long = System.currentTimeMillis()): List<BERTPriceSample> =
        preferences.getString(KEY, null)?.let { raw -> runCatching { parse(raw, nowEpochMillis) }.getOrNull() }.orEmpty()

    companion object {
        private const val KEY = "last_valid_history"
        private const val MAX_RESPONSE_BYTES = 96 * 1_024
        private const val MAX_POINTS = 400
        private const val INTERVAL_SECONDS = 300L

        /**
         * Each point is [candleOpenEpochSeconds, closeUsd]. A close describes the end of its candle, so it is
         * placed at open + interval, capped at now for the candle still in progress.
         */
        fun parse(raw: String, nowEpochMillis: Long): List<BERTPriceSample> {
            val root = JSONObject(raw)
            val asset = root.getJSONObject("asset")
            require(asset.getString("chain") == "solana") { "Unexpected chain" }
            require(asset.getString("mint") == BERTQuoteRepository.BERT_MINT) { "Unexpected BERT mint" }
            require(root.getJSONObject("source").getLong("intervalSeconds") == INTERVAL_SECONDS) { "Unexpected interval" }
            val points = root.getJSONArray("points")
            require(points.length() <= MAX_POINTS) { "Too many history points" }
            val samples = buildList {
                for (index in 0 until points.length()) {
                    val point = points.optJSONArray(index) ?: continue
                    val openSeconds = (point.opt(0) as? Number)?.toLong() ?: continue
                    val close = (point.opt(1) as? Number)?.toDouble() ?: continue
                    if (openSeconds <= 0 || !close.isFinite() || close <= 0) continue
                    val closedAt = minOf((openSeconds + INTERVAL_SECONDS) * 1_000, nowEpochMillis)
                    add(BERTPriceSample(closedAt, close))
                }
            }
            return BERTPriceHistory.combine(samples, emptyList(), nowEpochMillis)
        }
    }
}
