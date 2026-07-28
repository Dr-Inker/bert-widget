package global.bert.widget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BERTPriceSample(
    val observedAtEpochMillis: Long,
    val priceUsd: Double,
)

class BERTPriceHistory(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun record(quote: BERTQuote) {
        val updated = normalize(load() + BERTPriceSample(quote.observedAtEpochMillis, quote.priceUsd))
        val encoded = JSONArray().apply {
            updated.forEach { sample ->
                put(JSONObject().put("t", sample.observedAtEpochMillis).put("p", sample.priceUsd))
            }
        }
        preferences.edit().putString(KEY, encoded.toString()).apply()
    }

    fun load(nowEpochMillis: Long = System.currentTimeMillis()): List<BERTPriceSample> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        val decoded = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(BERTPriceSample(item.getLong("t"), item.getDouble("p")))
                }
            }
        }.getOrElse { return emptyList() }
        return normalize(decoded, nowEpochMillis)
    }

    companion object {
        private const val PREFERENCES = "bert_price_history"
        private const val KEY = "samples"
        private const val WINDOW_MILLIS = 24L * 60L * 60L * 1_000L
        private const val MAX_SAMPLES = 192

        internal fun normalize(
            samples: List<BERTPriceSample>,
            nowEpochMillis: Long = samples.maxOfOrNull { it.observedAtEpochMillis } ?: System.currentTimeMillis(),
        ): List<BERTPriceSample> {
            val cutoff = nowEpochMillis - WINDOW_MILLIS
            return samples.asSequence()
                .filter { it.observedAtEpochMillis in cutoff..nowEpochMillis && it.priceUsd.isFinite() && it.priceUsd > 0 }
                .associateBy { it.observedAtEpochMillis }
                .values
                .sortedBy { it.observedAtEpochMillis }
                .takeLast(MAX_SAMPLES)
        }
    }
}
