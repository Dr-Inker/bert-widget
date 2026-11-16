package global.bert.widget.data

import org.junit.Assert.*
import org.junit.Test

class BERTCompanionTest {
    private fun quote(time: Long, freshness: String = "fresh") = BERTQuote(
        0.005, 5.0, null, null, null, freshness, time, "dexscreener", "raydium",
        "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    )

    @Test fun `saved fresh quote becomes delayed at 30 minutes`() {
        assertFalse(quote(10_000_000).isStaleAt(11_799_999))
        assertTrue(quote(10_000_000).isStaleAt(11_800_000))
    }

    @Test fun `upstream stale and implausibly future observations cannot be fresh`() {
        assertTrue(quote(10_000_000, "stale").isStaleAt(10_000_000))
        assertTrue(quote(10_060_001).isStaleAt(10_000_000))
        assertTrue(quote(10_000_000, "unknown").isStaleAt(10_000_000))
    }

    @Test fun `position reports a known gain and a known loss`() {
        val position = BERTPosition(250_000.0, 1000.0)
        assertEquals(1250.0, position.valueUsd(0.005)!!, 0.00001)
        assertEquals(250.0, position.gainUsd(0.005)!!, 0.00001)
        assertEquals(25.0, position.gainPercent(0.005)!!, 0.00001)
        assertEquals(-500.0, position.gainUsd(0.002)!!, 0.00001)
        assertEquals(-50.0, position.gainPercent(0.002)!!, 0.00001)
    }

    @Test fun `missing price or cost never implies zero gains`() {
        assertNull(BERTPosition(250_000.0).gainUsd(0.005))
        assertNull(BERTPosition(250_000.0, 1000.0).gainUsd(null))
        assertNull(BERTPosition().valueUsd(0.005))
        assertNull(BERTPosition(250_000.0, 0.0).gainPercent(0.005))
        assertEquals(0.0, BERTPosition(0.0, 0.0).valueUsd(0.005)!!, 0.0)
    }

    @Test fun `invalid quotes and overflow do not produce an estimate`() {
        val position = BERTPosition(10.0, 5.0)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertNull(position.valueUsd(it)) }
        assertNull(BERTPosition(Double.MAX_VALUE).valueUsd(2.0))
    }

    private val us = java.util.Locale.US
    private val de = java.util.Locale.GERMANY
    private val fr = java.util.Locale.FRANCE
    private val india = java.util.Locale("en", "IN")

    @Test fun `number input accepts explicit decimal and grouped thousands`() {
        assertEquals(250000.5, parsePositionNumber("250,000.5", us)!!, 0.0)
        assertEquals(0.5, parsePositionNumber(".5", us)!!, 0.0)
        assertEquals(0.0, parsePositionNumber(" 0 ", us)!!, 0.0)
    }

    @Test fun `ambiguous and malformed amounts are rejected instead of silently changed`() {
        listOf("1,000,", "1e8", "NaN", "Infinity", "-1", "", " ", "1.2.3", "012,345", "00,015", "1,00,000", "1,5,0", "1,,5", "1.5.", "1 500").forEach {
            assertNull("Should reject $it", parsePositionNumber(it, us))
        }
    }

    // 0.15.1: "0,015" was read as grouped thousands and saved as 15, so the alert never fired. A first group of 0 is not
    // grouping, so it can only be a decimal comma, on any phone.
    @Test fun `0,015 is 0,015 on every phone and never 15`() {
        for (locale in listOf(us, de, fr, india)) assertEquals("$locale", 0.015, parsePositionNumber("0,015", locale)!!, 0.0)
    }

    @Test fun `a comma-decimal phone reads its own decimals and groups`() {
        for (locale in listOf(de, fr)) {
            assertEquals("$locale", 1.5, parsePositionNumber("1,5", locale)!!, 0.0)
            assertEquals("$locale", 0.5, parsePositionNumber(",5", locale)!!, 0.0)
            assertEquals("$locale", 250000.0, parsePositionNumber("250000", locale)!!, 0.0)
        }
        assertEquals(1500.5, parsePositionNumber("1.500,5", de)!!, 0.0)
        assertEquals(1250000.0, parsePositionNumber("1.250.000", de)!!, 0.0)
        assertEquals("the phone's own grouping wins", 1500.0, parsePositionNumber("1.500", de)!!, 0.0)
        // French groups with a (narrow) no-break space; a typed space is read the same.
        assertEquals(1500.5, parsePositionNumber("1 500,5", fr)!!, 0.0)
        assertEquals(1500.5, parsePositionNumber("1 500,5", fr)!!, 0.0)
        assertEquals(1500.5, parsePositionNumber("1 500,5", fr)!!, 0.0)
        listOf("1.2.3", "1,5,0", "1.000,", "012.345").forEach { assertNull("de should reject $it", parsePositionNumber(it, de)) }
        listOf("1 50,5", "1.500", "1.2.3").forEach { assertNull("fr should reject $it", parsePositionNumber(it, fr)) }
    }

    // A point typed on a comma-decimal phone (some keyboards offer nothing else), or a comma on a point-decimal one, is
    // read as the decimal it must be, and only where it cannot be grouping.
    @Test fun `the other habit is read only where it can mean one thing`() {
        for (locale in listOf(de, fr)) {
            assertEquals("$locale", 0.015, parsePositionNumber("0.015", locale)!!, 0.0)
            assertEquals("$locale", 1.5, parsePositionNumber("1.5", locale)!!, 0.0)
            assertEquals("$locale", 1.5, parsePositionNumber("1.50", locale)!!, 0.0)
            assertEquals("$locale", 0.5, parsePositionNumber(".5", locale)!!, 0.0)
            assertEquals("$locale", 250000.5, parsePositionNumber("250000.5", locale)!!, 0.0)
            assertEquals("$locale", 1500.5, parsePositionNumber("1,500.5", locale)!!, 0.0)
            assertEquals("$locale", 1250000.0, parsePositionNumber("1,250,000", locale)!!, 0.0)
        }
        assertEquals(1500.5, parsePositionNumber("1.500,5", fr)!!, 0.0)
        assertEquals(1250000.0, parsePositionNumber("1.250.000", fr)!!, 0.0)
        for (locale in listOf(us, india)) {
            assertEquals("$locale", 1.5, parsePositionNumber("1,5", locale)!!, 0.0)
            assertEquals("$locale", 12.34, parsePositionNumber("12,34", locale)!!, 0.0)
            assertEquals("$locale", 0.5, parsePositionNumber(",5", locale)!!, 0.0)
            assertEquals("$locale", 1234.5, parsePositionNumber("1234,5", locale)!!, 0.0)
            assertEquals("$locale", 1500.5, parsePositionNumber("1.500,5", locale)!!, 0.0)
            assertEquals("$locale", 1250000.0, parsePositionNumber("1.250.000", locale)!!, 0.0)
            assertEquals("the phone's own grouping wins", 1500.0, parsePositionNumber("1,500", locale)!!, 0.0)
        }
        // A separator is only ever dropped when it forms real groups of three; "1,5" is never 15 and "1.500" never 1.5 by a guess.
        for (locale in listOf(us, de, fr, india)) for (text in listOf("1,5", "1.5", "0,015", "0.015", "12,34", "1,50")) {
            val digitsOnly = text.filter(Char::isDigit).toDouble()
            assertTrue("$locale $text", parsePositionNumber(text, locale).let { it == null || it != digitsOnly })
        }
    }

    // Three digits after a lone other separator could be a group that lost its fellows ("1234,567" for 1,234,567).
    @Test fun `a lone other separator before three digits is refused unless the whole part is 0`() {
        for (locale in listOf(us, india)) listOf("1234,567", "12,345678,901", "012,345").forEach { assertNull("$locale $it", parsePositionNumber(it, locale)) }
        for (locale in listOf(de, fr)) listOf("1234.567", "12345.678").forEach { assertNull("$locale $it", parsePositionNumber(it, locale)) }
        assertEquals("its own decimal comma, whatever the zeros in front", 12.345, parsePositionNumber("012,345", de)!!, 0.0)
    }

    private val egypt = java.util.Locale("ar", "EG")
    private val iran = java.util.Locale("fa", "IR")
    private val swiss = java.util.Locale("de", "CH")

    // 0.15.1 review: an Arabic or Persian phone writes ٠٫٠١٥, but its number pad may only type 0.015; 0.15.0 took the latter.
    @Test fun `any script's digits are read, and a phone with its own decimal mark still takes a point or a comma`() {
        for (locale in listOf(egypt, iran)) {
            assertEquals("$locale", 0.015, parsePositionNumber("0.015", locale)!!, 0.0)
            assertEquals("$locale", 0.015, parsePositionNumber("0,015", locale)!!, 0.0)
            assertEquals("$locale", 0.015, parsePositionNumber("0٫015", locale)!!, 0.0)
            assertEquals("$locale", 1500.5, parsePositionNumber("1,500.5", locale)!!, 0.0)
            assertEquals("$locale", 1500.5, parsePositionNumber("1٬500٫5", locale)!!, 0.0)
            assertEquals("$locale", '.', positionDecimalSeparator(locale))
            assertEquals("$locale", "0.015", formatPositionInput(0.015, locale))
        }
        assertEquals(0.015, parsePositionNumber("٠٫٠١٥", egypt)!!, 0.0)          // ٠٫٠١٥
        assertEquals(0.015, parsePositionNumber("۰٫۰۱۵", iran)!!, 0.0)           // ۰٫۰۱۵
        assertEquals("native digits on any phone", 250000.5, parsePositionNumber("٢٥٠٠٠٠.٥", us)!!, 0.0)
        assertEquals(',', positionDecimalSeparator(de)); assertEquals('.', positionDecimalSeparator(us))
    }

    // 0.15.1 final review: read as a literal point and comma, Arabic 1.500 (one and a half) was saved as 1500 on a German phone.
    @Test fun `the Arabic decimal and thousands marks mean the same on every phone`() {
        val turkey = java.util.Locale("tr", "TR"); val brazil = java.util.Locale("pt", "BR")
        for (locale in listOf(us, de, fr, india, egypt, iran, swiss, turkey, brazil)) {
            assertEquals("$locale decimal", 1.5, parsePositionNumber("\u0661\u066B\u0665\u0660\u0660", locale)!!, 0.0)
            assertEquals("$locale grouped", 1500.0, parsePositionNumber("\u0661\u066C\u0665\u0660\u0660", locale)!!, 0.0)
            assertEquals("$locale both", 1500.5, parsePositionNumber("\u0661\u066C\u0665\u0660\u0660\u066B\u0665", locale)!!, 0.0)
            assertEquals("$locale small", 0.015, parsePositionNumber("\u0660\u066B\u0660\u0661\u0665", locale)!!, 0.0)
        }
    }

    // 0.15.1 last check: the fallback for the other habit could flip an Arabic mark's meaning (1.500, then the Arabic
    // thousands mark, then 000 was saved as 1500 on an English phone). An Arabic mark that does not fit the phone's own
    // rules is refused.
    @Test fun `an Arabic mark never goes through the other-habit fallback`() {
        val turkey = java.util.Locale("tr", "TR")
        for (locale in listOf(us, de, fr, india, egypt, iran, swiss, turkey)) {
            // Two decimal marks, a thousands mark before one or two digits, or before three after a lone 0: never a number.
            for (text in listOf("1\u066B500\u066B000", "1\u066C5", "1\u066C50", "0\u066C015", "1\u066B5\u066B")) {
                assertNull("$locale $text", parsePositionNumber(text, locale))
            }
        }
        // A point and an Arabic thousands mark together: refused where the point is the decimal, grouping where it groups.
        for (locale in listOf(us, india, egypt, iran)) assertNull("$locale", parsePositionNumber("1.500\u066C000", locale))
        assertEquals(1500000.0, parsePositionNumber("1.500\u066C000", de)!!, 0.0)
        for (locale in listOf(de, fr, turkey)) assertNull("$locale", parsePositionNumber("1,500\u066C000", locale))
        assertEquals(1500000.0, parsePositionNumber("1,500\u066C000", us)!!, 0.0)
        // The plain other habit is untouched.
        assertEquals(0.015, parsePositionNumber("0,015", egypt)!!, 0.0)
        assertEquals(0.015, parsePositionNumber("0,015", us)!!, 0.0)
    }

    @Test fun `India's neighbours type lakhs too`() {
        for (tag in listOf("bn-BD", "ne-NP", "ur-PK", "en-IN")) {
            val locale = java.util.Locale.forLanguageTag(tag)
            assertEquals(tag, 1234567.5, parsePositionNumber("12,34,567.5", locale)!!, 0.0)
            assertEquals(tag, 1234567.5, parsePositionNumber("1,234,567.5", locale)!!, 0.0)
            assertNull(tag, parsePositionNumber("1,0,000", locale))
        }
        assertNull("not elsewhere", parsePositionNumber("12,34,567.5", us))
    }

    @Test fun `a Swiss phone groups with either apostrophe`() {
        assertEquals(1500.5, parsePositionNumber("1'500.5", swiss)!!, 0.0)
        assertEquals(1500.5, parsePositionNumber("1’500.5", swiss)!!, 0.0)
        assertEquals(1250000.0, parsePositionNumber("1'250'000", swiss)!!, 0.0)
        assertNull(parsePositionNumber("1'5", swiss))
    }

    @Test fun `what is shown back reads back as the same number on every phone`() {
        for (locale in listOf(us, de, fr, india, egypt, iran, swiss)) for (value in listOf(0.015, 1500.5, 250000.0, 0.00001234, 1234567.891)) {
            assertEquals("$locale $value input", value, parsePositionNumber(formatPositionInput(value, locale), locale)!!, 0.0)
            assertEquals("$locale $value shown", value, parsePositionNumber(formatPositionShown(value, locale), locale)!!, 0.0)
        }
    }

    @Test fun `a point-decimal phone reads grouped thousands, and India its lakhs`() {
        assertEquals(1500.5, parsePositionNumber("1,500.5", us)!!, 0.0)
        assertEquals(0.015, parsePositionNumber("0.015", us)!!, 0.0)
        assertEquals(1500.5, parsePositionNumber("1,500.5", india)!!, 0.0)
        assertEquals(100000.5, parsePositionNumber("1,00,000.5", india)!!, 0.0)
        assertEquals(12500000.0, parsePositionNumber("1,25,00,000", india)!!, 0.0)
        assertEquals(0.015, parsePositionNumber("0.015", india)!!, 0.0)
        listOf("1,0,000", "01,00,000", "012,345").forEach { assertNull("en-IN should reject $it", parsePositionNumber(it, india)) }
    }

    @Test fun `a saved number is shown back and read back in the phone's own format`() {
        assertEquals("0,015", formatPositionInput(0.015, de))
        assertEquals("0.015", formatPositionInput(0.015, us))
        assertEquals("1500,5", formatPositionInput(1500.5, de))
        assertEquals("1.500,5", formatPositionShown(1500.5, de))
        assertEquals("1,500.5", formatPositionShown(1500.5, us))
        assertEquals("0,015", formatPositionShown(0.015, de))
        assertEquals("0.00001234", formatPositionShown(0.00001234, us))
        for (locale in listOf(us, de, fr, india)) for (value in listOf(0.015, 1500.5, 250000.0, 0.00001234)) {
            assertEquals("$locale $value input", value, parsePositionNumber(formatPositionInput(value, locale), locale)!!, 0.0)
            assertEquals("$locale $value shown", value, parsePositionNumber(formatPositionShown(value, locale), locale)!!, 0.0)
        }
    }

    @Test fun `one hour window is relative to now not the last stale sample`() {
        val history = listOf(
            BERTPriceSample(6_399_999, 1.0),
            BERTPriceSample(6_400_000, 2.0),
            BERTPriceSample(9_000_000, 3.0),
            BERTPriceSample(10_000_001, 4.0),
        )
        assertEquals(listOf(2.0, 3.0), BERTChartWindow.HOUR.samples(history, 10_000_000).map { it.priceUsd })
        assertTrue(BERTChartWindow.HOUR.samples(history.take(2), 20_000_000).isEmpty())
    }

    @Test fun `chart preserves gaps and never fabricates samples`() {
        val samples = listOf(BERTPriceSample(1_000_000, 2.0), BERTPriceSample(20_000_000, 3.0))
        assertEquals(samples, BERTChartWindow.SIX_HOURS.samples(samples, 21_000_000))
        assertTrue(BERTChartWindow.DAY.samples(emptyList(), 21_000_000).isEmpty())
    }
}
