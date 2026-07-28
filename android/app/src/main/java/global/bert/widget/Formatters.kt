package global.bert.widget

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

fun formatPrice(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).apply {
    maximumFractionDigits = if (value < 0.01) 6 else 4
}.format(value)

fun formatPercent(value: Double?): String = value?.let {
    String.format(Locale.US, "%+.2f%%", it)
} ?: "—"

fun formatCompactUsd(value: Double?): String = value?.let {
    when {
        it >= 1_000_000_000 -> "${'$'}${String.format(Locale.US, "%.1fB", it / 1_000_000_000)}"
        it >= 1_000_000 -> "${'$'}${String.format(Locale.US, "%.1fM", it / 1_000_000)}"
        it >= 1_000 -> "${'$'}${String.format(Locale.US, "%.1fK", it / 1_000)}"
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
