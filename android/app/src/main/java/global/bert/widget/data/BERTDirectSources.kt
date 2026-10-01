package global.bert.widget.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Converts direct DEX Screener and GeckoTerminal responses into the same envelopes the Berthalla
 * service returns, so storage, parsing and validation have one format whichever source answered.
 * Ports src/normalize.js and src/history-service.js; keep the rules in step.
 */
object BERTDirectSources {
    const val MAX_QUOTE_BYTES = 512 * 1_024
    const val MAX_HISTORY_BYTES = 256 * 1_024
    const val HISTORY_INTERVAL_SECONDS = 300L
    private const val WINDOW_SECONDS = 24L * 60 * 60
    private const val HISTORY_LIMIT = WINDOW_SECONDS / HISTORY_INTERVAL_SECONDS + 1

    fun historyUrl(base: String, pool: String): String {
        require(SOLANA_ADDRESS.matches(pool)) { "Invalid pool address" }
        return "$base/networks/solana/pools/$pool/ohlcv/minute?aggregate=${HISTORY_INTERVAL_SECONDS / 60}" +
            "&limit=$HISTORY_LIMIT&currency=usd&token=base"
    }

    /** DEX Screener token-pairs array → quote envelope for the deepest-liquidity BERT base-token pool. */
    fun quoteEnvelope(raw: String, nowEpochMillis: Long): String {
        val pairs = JSONArray(raw)
        var best: JSONObject? = null
        var bestLiquidity = Double.NEGATIVE_INFINITY
        for (index in 0 until pairs.length()) {
            val pair = pairs.optJSONObject(index) ?: continue
            val base = pair.optJSONObject("baseToken") ?: continue
            if (pair.optString("chainId") != "solana") continue
            if (base.optString("address") != BERTQuoteRepository.BERT_MINT) continue
            if (!base.optString("symbol").equals("bert", ignoreCase = true)) continue
            if (number(pair, "priceUsd") == null) continue
            val liquidity = number(pair.optJSONObject("liquidity"), "usd") ?: -1.0
            if (liquidity > bestLiquidity) {
                best = pair
                bestLiquidity = liquidity
            }
        }
        val pair = requireNotNull(best) { "No valid BERT base-token pool found" }
        val price = requireNotNull(number(pair, "priceUsd"))
        require(price > 0) { "priceUsd must be positive" }
        val pairAddress = pair.getString("pairAddress")
        require(SOLANA_ADDRESS.matches(pairAddress)) { "Invalid pair address" }
        val dex = pair.getString("dexId")
        require(dex.isNotEmpty() && dex.length <= 64) { "Invalid DEX id" }

        return JSONObject()
            .put("asset", JSONObject().put("chain", "solana").put("mint", BERTQuoteRepository.BERT_MINT)
                .put("name", "Bertram The Pomeranian").put("symbol", "Bert"))
            .put("quote", JSONObject()
                .put("priceUsd", price)
                .putOpt("change24hPct", number(pair.optJSONObject("priceChange"), "h24"))
                .putOpt("marketCapUsd", number(pair, "marketCap"))
                .putOpt("volume24hUsd", number(pair.optJSONObject("volume"), "h24"))
                .putOpt("liquidityUsd", number(pair.optJSONObject("liquidity"), "usd")))
            .put("source", JSONObject()
                .put("name", "dexscreener")
                .put("pairAddress", pairAddress)
                .put("dex", dex)
                .put("pairUrl", BERTQuoteRepository.requireValidDexScreenerPairUrl(pair.getString("url")))
                .put("observedAt", Instant.ofEpochMilli(nowEpochMillis).toString()))
            .put("meta", JSONObject().put("freshness", "fresh").put("ageSeconds", 0))
            .toString()
    }

    /** GeckoTerminal OHLCV response → history envelope of [openEpochSeconds, close] in the trailing 24h. */
    fun historyEnvelope(raw: String, pool: String, nowEpochMillis: Long): String {
        val root = JSONObject(raw)
        val base = root.getJSONObject("meta").getJSONObject("base")
        require(base.getString("address") == BERTQuoteRepository.BERT_MINT) { "History is not for the BERT mint" }
        val list = root.getJSONObject("data").getJSONObject("attributes").getJSONArray("ohlcv_list")
        require(list.length() <= 1_000) { "Too many candles" }
        val nowSeconds = nowEpochMillis / 1_000
        val byTime = sortedMapOf<Long, Double>()
        for (index in 0 until list.length()) {
            val candle = list.optJSONArray(index) ?: continue
            if (candle.length() < 5) continue
            val time = (candle.opt(0) as? Number)?.toLong() ?: continue
            val close = (candle.opt(4) as? Number)?.toDouble() ?: continue
            if (time % HISTORY_INTERVAL_SECONDS != 0L) continue
            if (time < nowSeconds - WINDOW_SECONDS || time > nowSeconds + 60) continue
            if (!close.isFinite() || close <= 0) continue
            byTime[time] = close
        }
        require(byTime.size >= 2) { "Too few valid candles" }
        val points = JSONArray().apply { byTime.forEach { (time, close) -> put(JSONArray().put(time).put(close)) } }
        return JSONObject()
            .put("asset", JSONObject().put("chain", "solana").put("mint", BERTQuoteRepository.BERT_MINT))
            .put("source", JSONObject().put("name", "geckoterminal").put("pairAddress", pool)
                .put("intervalSeconds", HISTORY_INTERVAL_SECONDS))
            .put("points", points)
            .put("meta", JSONObject().put("freshness", "fresh"))
            .toString()
    }

    private fun number(json: JSONObject?, key: String): Double? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        val value = when (val raw = json.opt(key)) {
            is Number -> raw.toDouble()
            is String -> raw.takeIf { it.isNotBlank() }?.toDoubleOrNull()
            else -> null
        }
        return value?.takeIf { it.isFinite() }
    }
}
