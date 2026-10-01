package global.bert.widget

import org.junit.Assert.assertEquals
import org.junit.Test
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
}

