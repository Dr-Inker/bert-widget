package global.bert.widget

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import global.bert.widget.data.BERTChartWindow
import kotlin.math.max

fun formatPrice(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).apply {
    maximumFractionDigits = if (value < 0.01) 6 else 4
}.format(value)

fun formatPercent(value: Double?): String = value?.let {
    String.format(Locale.US, "%+.2f%%", it)
} ?: "—"

fun formatCompactUsd(value: Double?): String = value?.let {
    // Each threshold is where the smaller unit would round up to 1000 (999,950 is $1.0M, not $1000.0K).
    when {
        it >= 999_950_000 -> "${'$'}${String.format(Locale.US, "%.1fB", it / 1_000_000_000)}"
        it >= 999_950 -> "${'$'}${String.format(Locale.US, "%.1fM", it / 1_000_000)}"
        it >= 999.5 -> "${'$'}${String.format(Locale.US, "%.1fK", it / 1_000)}"
        else -> "${'$'}${String.format(Locale.US, "%.0f", it)}"
    }
} ?: "—"

fun formatHoldingsUsd(value: Double?): String = value?.takeIf { it.isFinite() }?.let {
    NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(it)
} ?: "—"

fun formatTokenAmount(value: Double?): String = value?.takeIf { it.isFinite() }?.let {
    NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(it)
} ?: "—"

fun formatAge(observedAtEpochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): String {
    val minutes = max(0, (nowEpochMillis - observedAtEpochMillis) / 60_000)
    return when {
        minutes < 1 -> "Updated now"
        minutes < 60 -> "Updated ${minutes}m ago"
        minutes < 1_440 -> "Updated ${minutes / 60}h ago"
        else -> "Updated ${minutes / 1_440}d ago"
    }
}

/**
 * Absolute observation time for surfaces that are not re-rendered continuously (home-screen widgets).
 * Unlike [formatAge], it stays true however long the rendered text sits on screen.
 */
fun formatObservedAt(
    observedAtEpochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    use24Hour: Boolean = true,
    dateOnlyWhenOld: Boolean = false,
): String {
    val observed = Calendar.getInstance().apply { timeInMillis = observedAtEpochMillis }
    val now = Calendar.getInstance().apply { timeInMillis = nowEpochMillis }
    val sameDay = observed.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        observed.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val time = if (use24Hour) "HH:mm" else "h:mm a"
    val pattern = if (sameDay) time else if (dateOnlyWhenOld) "d MMM" else "d MMM $time"
    return "As of ${SimpleDateFormat(pattern, locale).format(Date(observedAtEpochMillis))}"
}

/** Every significant digit of an entered amount, grouped for reading: 3000000 → "3,000,000", 0.001 → "0.001". */
fun formatExactTokenAmount(value: Double): String =
    java.text.DecimalFormat("#,##0.##################", java.text.DecimalFormatSymbols(Locale.US)).format(value.toBigDecimal().stripTrailingZeros())

/** Relative axis label for a chart window edge: "24h ago", "6h ago", "1h ago". */
fun formatWindowStart(durationMillis: Long): String = "${durationMillis / 3_600_000}h ago"

/**
 * The zone a long range's dates are read in. 1Y's prices are daily candles, which GeckoTerminal opens and closes at UTC
 * midnight, so they keep UTC's calendar: a daily close at UTC midnight is never read as the day before west of Greenwich
 * (or a 1 October candle as "30 Sep" in New York). 1W's and 1M's are hourly and 4-hourly closes, moments like any other,
 * so they take this phone's calendar: in UTC, a week seen at 08:00 on 30 October in Sydney began on "22 Oct" and looked
 * like eight days, and one seen at 22:00 on 29 October in New York began on "23 Oct" and looked like six.
 */
fun rangeDateZone(window: BERTChartWindow, phoneZone: TimeZone = TimeZone.getDefault()): TimeZone =
    if (window.candles?.timeframe == "day") TimeZone.getTimeZone("UTC") else phoneZone

/**
 * Dates on a long range's chart: "3 Nov" across a week or a month, "3 Nov 2025" across a year, in the range's own zone
 * (see [rangeDateZone]) unless [zone] says otherwise. Callers keep one per range; SimpleDateFormat is not cheap.
 */
fun rangeDateFormat(window: BERTChartWindow, locale: Locale = Locale.getDefault(), zone: TimeZone = rangeDateZone(window)): SimpleDateFormat =
    SimpleDateFormat(if (window == BERTChartWindow.YEAR) "d MMM yyyy" else "d MMM", locale).apply { timeZone = zone }

/**
 * "23 Oct–30 Oct" under a long range's chart, from its first price to its last. The first is dated by [dates], the range's
 * own calendar (see [rangeDateZone]). The last usually is not a candle: it is the price of the candle still in progress at
 * the moment the copy was fetched ([lastIsFetch]), so it is dated on this phone's calendar by [localDates]. That only
 * differs on 1Y, whose candles keep UTC's days: otherwise at breakfast on 30 October in Sydney (still the 29th in UTC)
 * the line would end "–29 Oct" right beside "Now".
 */
fun formatRangeSpan(firstEpochMillis: Long, lastEpochMillis: Long, lastIsFetch: Boolean, dates: SimpleDateFormat, localDates: SimpleDateFormat): String =
    "${dates.format(Date(firstEpochMillis))}–${(if (lastIsFetch) localDates else dates).format(Date(lastEpochMillis))}"

/**
 * The chart's left-edge label. The short windows read as elapsed hours and the long ones as a plain span, unless the
 * prices start late (a year whose older half was refused), when it is the date of the first price, from [dates]: the
 * range's own calendar, the same one the span under the chart starts on.
 */
fun formatWindowStart(window: BERTChartWindow, originEpochMillis: Long, startsLate: Boolean = false,
                      dates: SimpleDateFormat = rangeDateFormat(window)): String = when {
    startsLate -> dates.format(Date(originEpochMillis))
    window == BERTChartWindow.WEEK -> "1 week ago"
    window == BERTChartWindow.MONTH -> "1 month ago"
    window == BERTChartWindow.YEAR -> "1 year ago"
    else -> formatWindowStart(window.durationMillis)
}

/** "30 minutes", "2.5 hours", "2.5 days": the longest gap a chart line is drawn across, for the spoken summary. */
fun formatGapLimit(millis: Long): String {
    fun plain(value: Double) = if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    return when {
        millis < 3_600_000L -> "${plain(millis / 60_000.0)} minutes"
        millis < 48 * 3_600_000L -> "${plain(millis / 3_600_000.0)} hours"
        else -> "${plain(millis / 86_400_000.0)} days"
    }
}
