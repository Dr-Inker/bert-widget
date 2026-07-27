package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class BERTQuoteRepository(context: Context) {
    private val preferences = context.getSharedPreferences("bert_quote", Context.MODE_PRIVATE)

    suspend fun refresh(): BERTQuote = withContext(Dispatchers.IO) {
        val connection = URL(BuildConfig.BERT_QUOTE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                error("Quote service returned HTTP ${connection.responseCode}")
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            val quote = parse(raw)
            preferences.edit().putString(KEY, raw).apply()
            quote
        } finally {
            connection.disconnect()
        }
    }

    fun load(): BERTQuote? = preferences.getString(KEY, null)
        ?.let { runCatching { parse(it) }.getOrNull() }

    private fun parse(raw: String): BERTQuote {
        val root = JSONObject(raw)
        val asset = root.getJSONObject("asset")
        require(asset.getString("chain") == "solana") { "Unexpected chain" }
        require(asset.getString("mint") == BERT_MINT) { "Unexpected BERT mint" }
        val quote = root.getJSONObject("quote")
        val source = root.getJSONObject("source")
        val price = quote.getDouble("priceUsd")
        require(price.isFinite() && price > 0) { "Invalid BERT price" }

        return BERTQuote(
            priceUsd = price,
            change24hPct = quote.optionalDouble("change24hPct"),
            marketCapUsd = quote.optionalDouble("marketCapUsd"),
            volume24hUsd = quote.optionalDouble("volume24hUsd"),
            liquidityUsd = quote.optionalDouble("liquidityUsd"),
            freshness = root.getJSONObject("meta").getString("freshness"),
            observedAtEpochMillis = Instant.parse(source.getString("observedAt")).toEpochMilli(),
            sourceName = source.getString("name"),
            dex = source.getString("dex"),
            pairUrl = source.getString("pairUrl"),
        )
    }

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    companion object {
        const val BERT_MINT = "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump"
        private const val KEY = "last_valid_quote"
    }
}
