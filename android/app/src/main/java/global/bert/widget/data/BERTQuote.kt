package global.bert.widget.data

data class BERTQuote(
    val priceUsd: Double,
    val change24hPct: Double?,
    val marketCapUsd: Double?,
    val volume24hUsd: Double?,
    val liquidityUsd: Double?,
    val freshness: String,
) {
    val isStale: Boolean get() = freshness != "fresh"
}

sealed interface QuoteState {
    data object Loading : QuoteState
    data class Available(val quote: BERTQuote, val updateDelayed: Boolean = false) : QuoteState
    data class Unavailable(val message: String) : QuoteState
}
