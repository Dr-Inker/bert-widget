package global.bert.widget.data

data class BERTQuote(
    val priceUsd: Double,
    val change24hPct: Double?,
    val marketCapUsd: Double?,
    val volume24hUsd: Double?,
    val liquidityUsd: Double?,
    val freshness: String,
    val observedAtEpochMillis: Long,
    val sourceName: String,
    val dex: String,
    val pairUrl: String,
    /** Case-preserved pool address; null for quotes saved before it was stored. */
    val pairAddress: String? = null,
    // Pool activity from DEX Screener. Optional: the Berthalla fallback server and quotes stored by 0.8.x omit them.
    val change1hPct: Double? = null,
    val change6hPct: Double? = null,
    val buys1h: Int? = null,
    val sells1h: Int? = null,
    val buys24h: Int? = null,
    val sells24h: Int? = null,
) {
    /** True when any activity figure is known; the Market activity card hides otherwise. */
    val hasActivity: Boolean get() = change1hPct != null || change6hPct != null || buys1h != null || buys24h != null

    val isStale: Boolean get() = isStaleAt(System.currentTimeMillis())

    fun isStaleAt(nowEpochMillis: Long): Boolean =
        freshness != "fresh" || nowEpochMillis - observedAtEpochMillis >= STALE_AFTER_MILLIS ||
            observedAtEpochMillis > nowEpochMillis + 60_000L

    companion object {
        const val STALE_AFTER_MILLIS = 30L * 60L * 1_000L
        private const val MAX_TXN_COUNT = 10_000_000L

        /** A price change cannot fall below -100%; the ceiling only rejects garbage, not real pumps. */
        fun validChange(value: Double?): Double? = value?.takeIf { it.isFinite() && it > -100.0 && it <= 1_000_000.0 }

        /** Whole, non-negative and plausible; anything else is dropped rather than failing the quote. */
        fun validCount(value: Double?): Int? =
            value?.takeIf { it.isFinite() && it >= 0 && it <= MAX_TXN_COUNT && it == Math.floor(it) }?.toInt()
    }
}

sealed interface QuoteState {
    data object Loading : QuoteState
    data class Available(val quote: BERTQuote, val updateDelayed: Boolean = false) : QuoteState
    data class Unavailable(val message: String) : QuoteState
}
