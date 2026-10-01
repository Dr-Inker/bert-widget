package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject

/** Server-provided 24-hour price history (5-minute closes), cached for offline display. */
class BERTMarketHistoryRepository(
    private val context: Context,
    private val http: BERTHttp = BERTHttp.DEFAULT,
    private val directBase: String = BuildConfig.BERT_DIRECT_HISTORY_BASE,
    private val serverUrl: String = BuildConfig.BERT_HISTORY_URL,
) {
    private val preferences = context.getSharedPreferences("bert_market_history", Context.MODE_PRIVATE)

    /**
     * Fetches GeckoTerminal directly for the pool of the latest quote; the Berthalla server is the fallback.
     * A cold GeckoTerminal response can take 10-20 s, hence the long read timeout on a background call.
     */
    suspend fun refresh(nowEpochMillis: Long = System.currentTimeMillis()): List<BERTPriceSample> = withContext(Dispatchers.IO) {
        val direct = directBase.isNotEmpty()
        val pool = BERTQuoteRepository(context).load()?.pairAddress
        val directResult = if (direct && pool != null) {
            try {
                val raw = http.get(BERTDirectSources.historyUrl(directBase, pool), BERTDirectSources.MAX_HISTORY_BYTES, 30_000)
                val envelope = BERTDirectSources.historyEnvelope(raw, pool, nowEpochMillis)
                envelope to parse(envelope, nowEpochMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null
        val (envelope, samples) = directResult
            ?: http.get(fallbackUrl(serverUrl, direct), MAX_RESPONSE_BYTES, 8_000).let { it to parse(it, nowEpochMillis) }
        preferences.edit().putString(KEY, envelope).apply()
        samples
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
