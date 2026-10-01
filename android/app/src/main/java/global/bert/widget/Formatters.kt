package global.bert.widget

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
