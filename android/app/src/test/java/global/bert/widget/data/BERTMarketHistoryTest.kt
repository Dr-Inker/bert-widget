package global.bert.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BERTMarketHistoryTest {
    private val now = 1_790_869_620_000L // 2026-10-01T15:47:00Z
    private val nowSeconds = now / 1_000
    private val aligned = nowSeconds - nowSeconds % 300

    private fun body(points: String, mint: String = BERTQuoteRepository.BERT_MINT, interval: Int = 300) = """{
        "asset":{"chain":"solana","mint":"$mint","name":"Bertram The Pomeranian","symbol":"Bert"},
        "source":{"name":"geckoterminal","pairAddress":"BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY","intervalSeconds":$interval},
        "points":$points,
        "meta":{"freshness":"fresh","ageSeconds":0}
    }"""

    @Test fun `closes sit at the end of their candle and the live candle is capped at now`() {
        val samples = BERTMarketHistoryRepository.parse(body("[[${aligned - 300},0.015],[$aligned,0.0153]]"), now)
        assertEquals(listOf(aligned * 1_000, now), samples.map { it.observedAtEpochMillis })
        assertEquals(listOf(0.015, 0.0153), samples.map { it.priceUsd })
    }

    @Test fun `history for another mint or interval is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BERTMarketHistoryRepository.parse(body("[]", mint = "So11111111111111111111111111111111111111112"), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BERTMarketHistoryRepository.parse(body("[]", interval = 900), now)
        }
    }

    @Test fun `malformed and non-positive points are skipped`() {
        val samples = BERTMarketHistoryRepository.parse(body("""[[${aligned - 600},0],[${aligned - 300},"x"],"junk",[${aligned - 900},0.014]]"""), now)
        assertEquals(listOf(0.014), samples.map { it.priceUsd })
    }

    @Test fun `device quotes extend server history only after its last point`() {
        val market = listOf(BERTPriceSample(now - 600_000, 1.0), BERTPriceSample(now - 300_000, 2.0))
        val device = listOf(
            BERTPriceSample(now - 400_000, 9.0), // overlaps server history: dropped
            BERTPriceSample(now - 60_000, 3.0),
            BERTPriceSample(now - 25 * 3_600_000L, 4.0), // outside the 24h window
        )
        val combined = BERTPriceHistory.combine(market, device, now)
        assertEquals(listOf(1.0, 2.0, 3.0), combined.map { it.priceUsd })
    }

    @Test fun `without server history the device quotes are the chart`() {
        val device = listOf(BERTPriceSample(now - 120_000, 1.0), BERTPriceSample(now - 60_000, 2.0))
        assertEquals(device, BERTPriceHistory.combine(emptyList(), device, now))
    }

    @Test fun `a full day of 5-minute closes is kept, including by the 24H chart window`() {
        val market = (0 until 288).map { BERTPriceSample(now - it * 300_000L, 1.0 + it) }
        assertEquals(288, BERTPriceHistory.combine(market, emptyList(), now).size)
        assertEquals(288, BERTChartWindow.DAY.samples(market, now).size)
        assertEquals(13, BERTChartWindow.HOUR.samples(market, now).size) // both window edges included
    }
}
