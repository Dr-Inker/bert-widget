package global.bert.widget

import global.bert.widget.data.BERTChartWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class FormattersTest {
    @Test fun `compact values switch unit where the smaller unit would round to 1000`() {
        assertEquals("$999", formatCompactUsd(999.4))
        assertEquals("$1.0K", formatCompactUsd(999.5))
        assertEquals("$999.9K", formatCompactUsd(999_949.0))
        assertEquals("$1.0M", formatCompactUsd(999_950.0))
        assertEquals("$14.9M", formatCompactUsd(14_925_038.0))
        assertEquals("$1.0B", formatCompactUsd(999_950_000.0))
    }

    @Test fun `observed time is absolute and adds the date when not today`() {
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val observed = 1_790_867_520_000L // 2026-10-01 15:12 UTC
            assertEquals("As of 15:12", formatObservedAt(observed, observed + 3 * 3_600_000L, Locale.US))
            assertEquals("As of 1 Oct 15:12", formatObservedAt(observed, observed + 24 * 3_600_000L, Locale.US))
            assertEquals("As of 3:12 PM", formatObservedAt(observed, observed, Locale.US, use24Hour = false))
            assertEquals("As of 1 Oct", formatObservedAt(observed, observed + 24 * 3_600_000L, Locale.US, dateOnlyWhenOld = true))
            assertEquals("As of 15:12", formatObservedAt(observed, observed + 3_600_000L, Locale.US, dateOnlyWhenOld = true))
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    private fun at(instant: String) = Instant.parse(instant).toEpochMilli()

    /** Runs [check] with this phone set to each of Sydney, New York and UTC in turn, by name. */
    private fun inEachZone(check: (place: String) -> Unit) {
        val zone = TimeZone.getDefault()
        try {
            for (place in listOf("Australia/Sydney", "America/New_York", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(place))
                check(place)
            }
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    private fun span(window: BERTChartWindow, first: Long, last: Long, lastIsFetch: Boolean = true) = formatRangeSpan(
        first, last, lastIsFetch, rangeDateFormat(window, Locale.US), rangeDateFormat(window, Locale.US, TimeZone.getDefault()))

    /**
     * 1W's and 1M's prices are hourly and 4-hourly closes, moments on the clock, so both ends of the span are this phone's
     * dates. Seen at 08:00 on 30 October in Sydney, the week starts at 08:00 on the 23rd there (still the 22nd in UTC, which
     * read "22 Oct–30 Oct", eight days); seen at 22:00 on 29 October in New York, it starts at 22:00 on the 22nd there
     * (already the 23rd in UTC, which read "23 Oct–29 Oct", six days).
     */
    @Test fun `a week's or a month's span is dated on the phone's calendar at both ends`() {
        val sydneyBreakfast = at("2026-10-29T21:00:00Z") // 08:00 on the 30th in Sydney, 17:00 on the 29th in New York
        val newYorkEvening = at("2026-10-30T02:00:00Z") // 22:00 on the 29th in New York, 13:00 on the 30th in Sydney
        val week = 7 * 86_400_000L
        inEachZone { place ->
            val expected = mapOf(
                "Australia/Sydney" to listOf("23 Oct–30 Oct", "23 Oct–30 Oct", "30 Sep–30 Oct"),
                "America/New_York" to listOf("22 Oct–29 Oct", "22 Oct–29 Oct", "29 Sep–29 Oct"),
                "UTC" to listOf("22 Oct–29 Oct", "23 Oct–30 Oct", "30 Sep–29 Oct"),
            ).getValue(place)
            assertEquals(place, expected[0], span(BERTChartWindow.WEEK, sydneyBreakfast - week, sydneyBreakfast))
            assertEquals(place, expected[1], span(BERTChartWindow.WEEK, newYorkEvening - week, newYorkEvening))
            // A month's first 4-hour close, the first at or after 21:00 UTC on 29 September: midnight UTC on the 30th.
            assertEquals(place, expected[2], span(BERTChartWindow.MONTH, at("2026-09-30T00:00:00Z"), sydneyBreakfast))
            // A last price that is a closed candle, not the fetch, is a moment too, so it is dated the same way.
            assertEquals(place, expected[1], span(BERTChartWindow.WEEK, newYorkEvening - week, newYorkEvening, lastIsFetch = false))
            // A week whose older prices are missing names its first date on the same calendar as the span.
            assertEquals(place, expected[1].substringBefore("–"),
                formatWindowStart(BERTChartWindow.WEEK, newYorkEvening - week, startsLate = true, rangeDateFormat(BERTChartWindow.WEEK, Locale.US)))
        }
    }

    /**
     * 1Y's prices are daily candles, which open and close at UTC midnight, so their dates stay UTC's everywhere; only the
     * moment the copy was fetched, its last price, is this phone's date. The first close here is midnight UTC on 30 October
     * 2025, which is still the 29th in New York, and must not read as the day before.
     */
    @Test fun `a year's candles keep UTC's days and its fetch is dated on the phone's calendar`() {
        val firstClose = at("2025-10-30T00:00:00Z")
        val fetched = at("2026-10-29T21:00:00Z") // 08:00 on the 30th in Sydney, 17:00 on the 29th in New York
        inEachZone { place ->
            val end = if (place == "Australia/Sydney") "30 Oct 2026" else "29 Oct 2026"
            assertEquals(place, "30 Oct 2025–$end", span(BERTChartWindow.YEAR, firstClose, fetched))
            // The last daily candle, when the copy's newest had closed, keeps UTC's date like the others.
            assertEquals(place, "30 Oct 2025–29 Oct 2026", span(BERTChartWindow.YEAR, firstClose, at("2026-10-29T00:00:00Z"), lastIsFetch = false))
            // The left edge of a year that starts late, and "Change since", read the same UTC date as the span's start.
            assertEquals(place, "30 Oct 2025", formatWindowStart(BERTChartWindow.YEAR, firstClose, startsLate = true, rangeDateFormat(BERTChartWindow.YEAR, Locale.US)))
        }
    }

    @Test fun `chart window start reads as elapsed hours`() {
        assertEquals("24h ago", formatWindowStart(24 * 3_600_000L))
        assertEquals("1h ago", formatWindowStart(3_600_000L))
    }

    @Test fun `tournament countdown reads naturally and never goes negative`() {
        val now = 1_000_000_000L
        assertEquals("Ends in 60 days 7h", formatTimeLeft(now + (60 * 24 + 7) * 3_600_000L, now))
        assertEquals("Ends in 1 day 0h", formatTimeLeft(now + 24 * 3_600_000L, now))
        assertEquals("Ends in 3h 12m", formatTimeLeft(now + (3 * 60 + 12) * 60_000L, now))
        assertEquals("Ends in 4m", formatTimeLeft(now + 4 * 60_000L, now))
        assertEquals("Ends in 0m", formatTimeLeft(now - 60_000L, now))
    }

    @Test fun `entered amounts keep every digit and gain grouping`() {
        assertEquals("3,000,000", formatExactTokenAmount(3_000_000.0))
        assertEquals("1.5", formatExactTokenAmount(1.500))
        assertEquals("0.001", formatExactTokenAmount(0.001))
        assertEquals("250,000.123456", formatExactTokenAmount(250_000.123456))
    }
}
