package global.bert.widget.data

data class BERTPosition(val amount: Double? = null, val totalCostUsd: Double? = null) {
    init {
        require(amount == null || (amount.isFinite() && amount >= 0))
        require(totalCostUsd == null || (totalCostUsd.isFinite() && totalCostUsd >= 0))
    }

    fun valueUsd(priceUsd: Double?): Double? = priceUsd?.takeIf { it.isFinite() && it > 0 }
        ?.let { price -> amount?.let { it * price } }?.takeIf { it.isFinite() }

    fun gainUsd(priceUsd: Double?): Double? = valueUsd(priceUsd)
        ?.let { value -> totalCostUsd?.let { value - it } }?.takeIf { it.isFinite() }

    fun gainPercent(priceUsd: Double?): Double? = totalCostUsd?.takeIf { it > 0 }
        ?.let { cost -> gainUsd(priceUsd)?.let { it / cost * 100 } }?.takeIf { it.isFinite() }
}

/** Decimal point or correctly grouped thousands; never silently reinterpret 1,5 as 15. */
fun parsePositionNumber(input: String): Double? {
    val value = input.trim()
    if (!Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?|\\.[0-9]+").matches(value)) return null
    return value.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}
