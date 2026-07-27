package global.bert.widget

import java.text.NumberFormat
import java.util.Locale

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
