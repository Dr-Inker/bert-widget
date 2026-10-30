package global.bert.widget.data

import global.bert.widget.formatGapLimit
import global.bert.widget.formatPercent
import global.bert.widget.formatWindowStart
import global.bert.widget.rangeDateFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class BERTChartWindowTest {
    private val now = 1_793_340_000_000L // 2026-10-29T06:00:00Z
    private val day = 86_400_000L

    @Test fun `the options read 1H 6H 24H 1W 1M 1Y`() {
        // No "All": GeckoTerminal's free API refuses anything older than about 183 days, so all time cannot be had.
        assertEquals(listOf("1H", "6H", "24H", "1W", "1M", "1Y"), BERTChartWindow.entries.map { it.label })
        assertEquals(listOf(false, false, false, true, true, true), BERTChartWindow.entries.map { it.isLongRange })
    }

    @Test fun `edge labels name the span`() {
        assertEquals("1h ago", formatWindowStart(BERTChartWindow.HOUR, now - 3_600_000L))
        assertEquals("24h ago", formatWindowStart(BERTChartWindow.DAY, now - day))
        assertEquals("1 week ago", formatWindowStart(BERTChartWindow.WEEK, now - 7 * day))
        assertEquals("1 month ago", formatWindowStart(BERTChartWindow.MONTH, now - 30 * day))
        assertEquals("1 year ago", formatWindowStart(BERTChartWindow.YEAR, now - 365 * day))
        // A range that starts late names the date of its first price instead.
        val firstClose = 1_727_740_800_000L // 2024-10-01 00:00 UTC, the close of the 30 September candle
        assertEquals("1 Oct 2024", formatWindowStart(BERTChartWindow.YEAR, firstClose, startsLate = true, dates = rangeDateFormat(BERTChartWindow.YEAR, Locale.US)))
        assertEquals("1 Oct", formatWindowStart(BERTChartWindow.WEEK, firstClose, startsLate = true, dates = rangeDateFormat(BERTChartWindow.WEEK, Locale.US)))
    }

    @Test fun `long range dates are UTC wherever the phone is`() {
        // A daily close lands on UTC midnight; in the phone's own zone west of UTC it would read as the day before.
        val firstClose = 1_727_740_800_000L // 2024-10-01 00:00 UTC
        val zone = TimeZone.getDefault()
        try {
            for (id in listOf("America/New_York", "America/Los_Angeles", "Pacific/Auckland", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(id, "1 Oct 2024", rangeDateFormat(BERTChartWindow.YEAR, Locale.US).format(java.util.Date(firstClose)))
            }
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test fun `gaps scale with the candle interval`() {
        assertEquals(30 * 60_000L, BERTChartWindow.DAY.maxJoinGapMillis) // the 5-minute history keeps its 30 minutes
        assertEquals(9_000_000L, BERTChartWindow.WEEK.maxJoinGapMillis) // 2.5 hours
        assertEquals(36_000_000L, BERTChartWindow.MONTH.maxJoinGapMillis) // 10 hours
        assertEquals(216_000_000L, BERTChartWindow.YEAR.maxJoinGapMillis) // 2.5 days
        assertEquals(listOf("30 minutes", "2.5 hours", "10 hours", "2.5 days"), listOf(30 * 60_000L, 9_000_000L, 36_000_000L, 216_000_000L).map(::formatGapLimit))
    }

    @Test fun `a long series is cut to its window`() {
        val series = (0..400).map { BERTPriceSample(now - it * day, 1.0 + it) }.reversed()
        assertEquals(8, BERTChartWindow.WEEK.longSamples(series, now).size)
        assertEquals(31, BERTChartWindow.MONTH.longSamples(series, now).size)
        assertEquals(366, BERTChartWindow.YEAR.longSamples(series, now).size)
        assertEquals(366, BERTChartWindow.YEAR.samples(series, now).size) // samples() routes long ranges here
    }

    @Test fun `the left edge is the window length ago unless the prices start late`() {
        fun daily(days: Int) = (0 until days).map { BERTPriceSample(now - it * day, 0.01) }.reversed()
        assertEquals(now - 7 * day, BERTChartWindow.WEEK.originEpochMillis(daily(8), now))
        assertEquals(now - 24 * 3_600_000L, BERTChartWindow.DAY.originEpochMillis(emptyList(), now))
        // What two free pages reach, about 361 days, still reads as a year: the allowance is a fiftieth of the window.
        assertFalse(BERTChartWindow.YEAR.startsLate(daily(361), now))
        assertEquals(now - 365 * day, BERTChartWindow.YEAR.originEpochMillis(daily(361), now))
        assertFalse(BERTChartWindow.YEAR.startsLate(daily(358), now))
        // Only the first page, half a year: the chart starts at its first price.
        val half = daily(183)
        assertTrue(BERTChartWindow.YEAR.startsLate(half, now))
        assertEquals(half.first().observedAtEpochMillis, BERTChartWindow.YEAR.originEpochMillis(half, now))
        // The 5-minute ranges have no late start, and no prices is not late either.
        assertFalse(BERTChartWindow.DAY.startsLate(listOf(BERTPriceSample(now, 0.01)), now))
        assertFalse(BERTChartWindow.YEAR.startsLate(emptyList(), now))
    }

    @Test fun `change is first close to last close`() {
        val up = listOf(BERTPriceSample(1, 0.010), BERTPriceSample(2, 0.020), BERTPriceSample(3, 0.0125))
        assertEquals(25.0, BERTChartWindow.WEEK.changePct(up)!!, 1e-9)
        assertEquals("+25.00%", formatPercent(BERTChartWindow.WEEK.changePct(up)))
        assertEquals(-50.0, BERTChartWindow.YEAR.changePct(listOf(BERTPriceSample(1, 0.02), BERTPriceSample(2, 0.01)))!!, 1e-9)
        assertNull(BERTChartWindow.WEEK.changePct(up.take(1)))
        assertNull(BERTChartWindow.WEEK.changePct(emptyList()))
        assertTrue(BERTChartWindow.WEEK.changePct(listOf(BERTPriceSample(1, 0.0001), BERTPriceSample(2, 1.0)))!! > 999_000)
    }
}
