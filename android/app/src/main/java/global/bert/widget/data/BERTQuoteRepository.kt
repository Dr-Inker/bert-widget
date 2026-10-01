package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject
import java.io.InputStream
import java.net.URI
import java.time.Instant

class BERTQuoteRepository(
    private val context: Context,
    private val http: BERTHttp = BERTHttp.DEFAULT,
    private val directUrl: String = BuildConfig.BERT_DIRECT_QUOTE_URL,
    private val serverUrl: String = BuildConfig.BERT_QUOTE_URL,
) {
    private val preferences = context.getSharedPreferences("bert_quote", Context.MODE_PRIVATE)

    /**
     * Asks DEX Screener directly; the Berthalla server is only a fallback for when the direct source fails
     * or stops matching the expected format, so installed apps keep working until they are updated.
     */
    suspend fun refresh(nowEpochMillis: Long = System.currentTimeMillis()): BERTQuote = withContext(Dispatchers.IO) {
        val direct = directUrl.isNotEmpty()
        val directResult = if (direct) {
            try {
                val envelope = BERTDirectSources.quoteEnvelope(http.get(directUrl, BERTDirectSources.MAX_QUOTE_BYTES, 8_000), nowEpochMillis)
                envelope to parse(envelope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null
        val (envelope, quote) = directResult ?: http.get(fallbackUrl(serverUrl, direct), MAX_RESPONSE_BYTES, 8_000).let { it to parse(it) }
        preferences.edit().putString(KEY, envelope).apply()
        BERTPriceHistory(context).record(quote)
        quote
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
            pairUrl = requireValidDexScreenerPairUrl(source.getString("pairUrl")),
            pairAddress = source.optString("pairAddress").takeIf { SOLANA_ADDRESS.matches(it) },
        )
    }

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    companion object {
        const val BERT_MINT = "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump"
        private const val KEY = "last_valid_quote"
        private const val MAX_RESPONSE_BYTES = 128 * 1_024

        fun readUtf8WithLimit(input: InputStream, maxBytes: Int = MAX_RESPONSE_BYTES): String =
            BERTHttp.readUtf8WithLimit(input, maxBytes)

        fun requireValidDexScreenerPairUrl(raw: String): String {
            val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Invalid market URL") }
            val host = uri.host?.lowercase()
            require(uri.scheme.equals("https", ignoreCase = true)) { "Market URL must use HTTPS" }
            require(host == "dexscreener.com" || host == "www.dexscreener.com") { "Unexpected market host" }
            require(uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Unsafe market URL" }
            require(Regex("^/solana/[A-Za-z0-9]{32,64}/?$").matches(uri.path.orEmpty())) { "Unexpected market path" }
            return uri.toASCIIString()
        }
    }
}
