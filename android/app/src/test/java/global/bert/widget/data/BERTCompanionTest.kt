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

    @Test fun `number input accepts explicit decimal and grouped thousands`() {
        assertEquals(250000.5, parsePositionNumber("250,000.5")!!, 0.0)
        assertEquals(0.5, parsePositionNumber(".5")!!, 0.0)
        assertEquals(0.0, parsePositionNumber(" 0 ")!!, 0.0)
    }

    @Test fun `ambiguous and malformed amounts are rejected instead of silently changed`() {
        listOf("1,5", "12,34", "1,000,", "1e8", "NaN", "Infinity", "-1", "", " ", "1.2.3").forEach {
            assertNull("Should reject $it", parsePositionNumber(it))
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
