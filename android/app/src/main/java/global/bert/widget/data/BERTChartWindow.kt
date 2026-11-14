package global.bert.widget.data

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val LATE_START_SHARE = 50
/** The fewest prices a drawn line must join; fewer is a dot or a dash, not a chart. */
private const val MIN_LINE_PRICES = 3

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

    /** How far apart this range's prices are meant to be: one candle for a long range, the 5-minute history for the rest. */
    val intervalMillis: Long get() = (candles?.intervalSeconds ?: BERTDirectSources.HISTORY_INTERVAL_SECONDS) * 1_000

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
     * Whether the prices start noticeably after the window does, so the chart has to say where they really start instead
     * of "1 year ago" or "24h ago". The free API reaches back about 361 days, so a year that opens a few days late still
     * reads as a year: the allowance is a fiftieth of the window (about a week of a year, three hours of a week, half an
     * hour of a day) beyond one interval between prices. Half a year, from a second page that was refused, is not. The
     * short ranges are held to the same rule: their 5-minute history can cover far less than a day (a new install, or the
     * history unreachable and only this device's quotes kept), and three hours of prices must not read as 24 hours.
     */
    fun startsLate(series: List<BERTPriceSample>, nowEpochMillis: Long): Boolean {
        val first = series.firstOrNull() ?: return false
        val firstOpen = first.observedAtEpochMillis - intervalMillis
        return firstOpen > nowEpochMillis - durationMillis + durationMillis / LATE_START_SHARE
    }

    /** Which prices a line reaches: each one within [maxJoinGapMillis] of a neighbour. The rest would be loose dots. */
    fun joined(series: List<BERTPriceSample>): BooleanArray {
        val joined = BooleanArray(series.size)
        for (index in 0 until series.size - 1) {
            if (series[index + 1].observedAtEpochMillis - series[index].observedAtEpochMillis <= maxJoinGapMillis) {
                joined[index] = true
                joined[index + 1] = true
            }
        }
        return joined
    }

    /**
     * Whether the prices make a line worth drawing: at least [MIN_LINE_PRICES] of them joined, and at least half. Fewer is
     * a scatter of dots that reads as a broken chart, so the chart says there is not enough history yet instead.
     */
    fun hasLine(series: List<BERTPriceSample>): Boolean {
        val joinedCount = joined(series).count { it }
        return joinedCount >= MIN_LINE_PRICES && joinedCount * 2 >= series.size
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
