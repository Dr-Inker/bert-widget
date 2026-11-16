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

/**
 * A typed amount or price, read in the phone's own number format ([locale]) first: its decimal separator, and its
 * grouping separator only where it groups correctly (thousands, or lakhs and crores in India and its neighbours). In a comma-decimal locale
 * "0,015" is 0.015 and "1.500,5" is 1500.5; in a point-decimal one "1,500.5" is 1500.5.
 *
 * Then the other habit, only where it can mean one thing ([parseOtherHabit]): many holders' phones are set to a language
 * that writes numbers differently from how they type them, and some keyboards offer only a point. "0,015" on an English
 * phone is 0.015 (0.15.0 saved it as 15, so the alert never fired) and "0.015" on a German one is 0.015 too.
 *
 * Any script's digits are read ([asciiNumberChar]): an Arabic or Persian phone writes ٠٫٠١٥ but its number pad may only
 * type 0.015, so both are 0.015 there, and a phone whose decimal mark is neither a point nor a comma is read by the
 * point-decimal rules. The Arabic decimal mark (U+066B) and thousands mark (U+066C) mean the same on every phone.
 *
 * A separator is never dropped unless it forms real groups of three, and anything that could be read two ways is null:
 * "1.500" on a French phone, "1234,567" or "012,345" on an English one. The screens show the saved number back, so the
 * holder sees what was kept.
 */
fun parsePositionNumber(input: String, locale: java.util.Locale = java.util.Locale.getDefault()): Double? {
    val symbols = java.text.DecimalFormatSymbols.getInstance(locale)
    var decimal = symbols.decimalSeparator
    // French and others group with a (narrow) no-break space; a plain space typed for it is the same separator.
    var group = asciiNumberChar(symbols.groupingSeparator).let { if (it.isWhitespace()) ' ' else it }
    if (decimal != '.' && decimal != ',') { decimal = '.'; group = ',' }
    if (group == decimal) return null
    // The Arabic marks keep their meaning on any phone: U+066B is always the decimal and U+066C always groups, so
    // one, U+066B, five hundred is 1.5 on a German phone too, never read through that phone's own use of the point.
    // (Left-to-right, right-to-left and Arabic letter marks, which a formatted number may carry, are dropped.)
    val value = input.trim().filterNot { it == '\u200E' || it == '\u200F' || it == '\u061C' }
        .map { when (it) { '\u066B' -> decimal; '\u066C' -> group; else -> asciiNumberChar(it) } }.joinToString("")
    val g = Regex.escape(group.toString()); val d = Regex.escape(decimal.toString())
    // Plain digits; or groups of three after a first group of 1 to 3 that does not start with 0; or, in India, groups
    // of two before the last three (1,00,000).
    val grouped = buildList {
        add("[0-9]+")
        add("[1-9][0-9]{0,2}(?:$g[0-9]{3})+")
        if (locale.country in LAKH_COUNTRIES) add("[1-9][0-9]?(?:$g[0-9]{2})*$g[0-9]{3}")
    }.joinToString("|")
    val plain = if (Regex("(?:$grouped)(?:$d[0-9]+)?|$d[0-9]+").matches(value)) {
        value.replace(group.toString(), "").replace(decimal, '.')
    } else {
        // The other habit is for a plain point or comma only: an Arabic mark that does not fit the phone's own rules is
        // refused, or the fallback could swap its meaning (a grouped 1.500\u066C000 read as 1500).
        if (input.any { it == '\u066B' || it == '\u066C' }) return null
        parseOtherHabit(value, decimal) ?: return null
    }
    return plain.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

/** Where numbers are grouped in lakhs and crores (12,34,567): a phone there shows them so, and a holder may type them back so. */
private val LAKH_COUNTRIES = setOf("IN", "BD", "NP", "PK")

/**
 * One typed character as its plain equivalent: any script's digit as 0 to 9, a typographic apostrophe (Swiss grouping)
 * as a plain one, and the no-break and thin spaces as a space. The Arabic thousands mark, when it is a locale's own
 * grouping separator, is a comma; in typed input the caller places both Arabic marks itself.
 */
private fun asciiNumberChar(char: Char): Char = when {
    char in '0'..'9' -> char
    Character.isDigit(char) -> '0' + Character.digit(char, 10)
    char == '\u066C' -> ','
    char == '\u2019' || char == '\u02BC' -> '\''
    char == '\u00A0' || char == '\u202F' || char == '\u2009' -> ' '
    else -> char
}

/**
 * The decimal mark a holder is asked to type, and that a saved number is put back into a field with: the phone's own
 * when it is a point or a comma, otherwise a point (Arabic and Persian phones, whose own mark few number pads can type).
 */
fun positionDecimalSeparator(locale: java.util.Locale = java.util.Locale.getDefault()): Char =
    java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator.let { if (it == '.' || it == ',') it else '.' }

/**
 * [value] (already in plain digits) written with the separators the other way round from the phone's ([decimal] is the
 * phone's own), as plain digits with a point, or null when it is not that or could be read two ways. Three shapes:
 *  - one other separator that cannot be grouping: "0,015", "1,5", ",5", "1234,5" (but not "1,500", "1234,567" or
 *    "012,345": three digits after it could be a group, unless the whole part is just 0);
 *  - both separators, grouped and with a decimal part, either way round: "1.500,5", "1,500.5";
 *  - two or more groups of three and no decimal part: "1.250.000".
 */
private fun parseOtherHabit(value: String, decimal: Char): String? {
    val other = when (decimal) { '.' -> ','; ',' -> '.'; else -> return null }
    Regex("([0-9]*)${Regex.escape(other.toString())}([0-9]+)").matchEntire(value)?.let { match ->
        val (whole, fraction) = match.destructured
        val couldBeGroups = fraction.length == 3 && whole.isNotEmpty() && whole != "0"
        return if (couldBeGroups) null else "${whole.ifEmpty { "0" }}.$fraction"
    }
    for ((grp, dec) in listOf(decimal to other, other to decimal)) {
        val gq = Regex.escape(grp.toString()); val dq = Regex.escape(dec.toString())
        if (Regex("[1-9][0-9]{0,2}(?:(?:$gq[0-9]{3})+$dq[0-9]+|(?:$gq[0-9]{3}){2,})").matches(value)) {
            return value.replace(grp.toString(), "").replace(dec, '.')
        }
    }
    return null
}

/** [value] as it is put back into an input field: [positionDecimalSeparator], plain digits, no grouping, every significant digit. */
fun formatPositionInput(value: Double, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    value.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', positionDecimalSeparator(locale))

/** [value] as the phone shows a number, grouped, with every significant digit: what a holder sees was saved. */
fun formatPositionShown(value: Double, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    (java.text.NumberFormat.getNumberInstance(locale) as java.text.DecimalFormat).apply {
        maximumFractionDigits = 18; isGroupingUsed = true
    }.format(value.toBigDecimal().stripTrailingZeros())
