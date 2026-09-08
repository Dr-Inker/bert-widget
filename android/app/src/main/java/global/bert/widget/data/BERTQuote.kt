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
) {
    val isStale: Boolean get() = isStaleAt(System.currentTimeMillis())

    fun isStaleAt(nowEpochMillis: Long): Boolean =
        freshness != "fresh" || nowEpochMillis - observedAtEpochMillis >= STALE_AFTER_MILLIS ||
            observedAtEpochMillis > nowEpochMillis + 60_000L

    companion object {
        const val STALE_AFTER_MILLIS = 30L * 60L * 1_000L
    }
}

sealed interface QuoteState {
    data object Loading : QuoteState
    data class Available(val quote: BERTQuote, val updateDelayed: Boolean = false) : QuoteState
    data class Unavailable(val message: String) : QuoteState
}
