package global.bert.widget.data

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val LATE_START_SHARE = 50

/**
 * How a long chart range is fetched from GeckoTerminal. [timeframe] and [aggregate] pick the candle size
 * (`hour` x 4 is a 4-hour candle); [limit] is how many candles one page asks for, and [maxPages] caps how far
 * back-paging may go for the ranges that need more than one page.
 */
class BERTCandleSpec(val timeframe: String, val aggregate: Int, val limit: Int, val maxPages: Int, val freshMillis: Long) {
    /** The unit candles line up on: every candle opens on a whole hour (or a whole UTC day) whatever the aggregate. */
    val alignSeconds: Long = if (timeframe == "day") 86_400L else 3_600L
    val intervalSeconds: Long = alignSeconds * aggregate
}

enum class BERTChartWindow(val label: String, val durationMillis: Long, val candles: BERTCandleSpec? = null) {
    HOUR("1H", 60 * 60_000L),
    SIX_HOURS("6H", 6 * 60 * 60_000L),
    DAY("24H", 24 * 60 * 60_000L),
    // The Berthalla server only serves the 24-hour history, so the ranges below have no server fallback: when
    // GeckoTerminal cannot be reached the chart says so instead of showing a different range.
    WEEK("1W", 7 * DAY_MILLIS, BERTCandleSpec("hour", 1, 169, 1, HOUR_MILLIS)),
    MONTH("1M", 30 * DAY_MILLIS, BERTCandleSpec("hour", 4, 181, 1, 4 * HOUR_MILLIS)),
    // A daily request returns about half a year however large the limit is, so a year needs two pages. There is no
    // "All": GeckoTerminal's free API refuses (HTTP 401) any page that starts more than about 183 days back, so two pages
    // are as far as it reaches, and BERT's pool (from 2024-09-30) is older than that.
    YEAR("1Y", 365 * DAY_MILLIS, BERTCandleSpec("day", 1, 1_000, 2, DAY_MILLIS));

    /** True for the ranges that come from their own GeckoTerminal request rather than the 5-minute history. */
    val isLongRange: Boolean get() = candles != null

    /** Gaps wider than this are drawn as breaks: 30 minutes for the 5-minute history, 2.5 candles for long ranges. */
    val maxJoinGapMillis: Long get() = candles?.let { (it.intervalSeconds * 2_500L) } ?: (30 * 60_000L)

    // Input is already combined and validated; normalize() would re-apply the 192-sample device cap
    // and drop the oldest hours of server history.
    fun samples(history: List<BERTPriceSample>, nowEpochMillis: Long): List<BERTPriceSample> {
        if (isLongRange) return longSamples(history, nowEpochMillis)
        return BERTPriceHistory.combine(history, emptyList(), nowEpochMillis)
            .filter { it.observedAtEpochMillis >= nowEpochMillis - durationMillis }
    }

    /** The part of a fetched long series inside this window. */
    fun longSamples(series: List<BERTPriceSample>, nowEpochMillis: Long): List<BERTPriceSample> =
        series.filter { it.observedAtEpochMillis >= nowEpochMillis - durationMillis }

    /**
     * Whether a long range's prices start noticeably after the window does, so the chart has to name its real first date
     * instead of "1 year ago". The free API reaches back about 361 days, so a year that opens a few days late still reads
     * as a year: the allowance is a fiftieth of the window (about a week of a year, three hours of a week). Half a year,
     * from a second page that was refused, is not.
     */
    fun startsLate(series: List<BERTPriceSample>, nowEpochMillis: Long): Boolean {
        val spec = candles ?: return false
        val first = series.firstOrNull() ?: return false
        val firstOpen = first.observedAtEpochMillis - spec.intervalSeconds * 1_000
        return firstOpen > nowEpochMillis - durationMillis + durationMillis / LATE_START_SHARE
    }

    /** The chart's left edge: the window's length ago, or the first price when the prices [startsLate]. */
    fun originEpochMillis(series: List<BERTPriceSample>, nowEpochMillis: Long): Long =
        if (startsLate(series, nowEpochMillis)) series.first().observedAtEpochMillis else nowEpochMillis - durationMillis

    /** Change from the first to the last price on the chart, in percent; null when the first price is zero or there is no span. */
    fun changePct(series: List<BERTPriceSample>): Double? {
        if (series.size < 2) return null
        val first = series.first().priceUsd
        return if (first > 0) (series.last().priceUsd - first) / first * 100 else null
    }
}
