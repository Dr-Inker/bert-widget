package global.bert.widget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BERTPriceHistoryTest {
    @Test
    fun `normalization sorts deduplicates and rejects invalid samples`() {
        val now = 100_000_000L
        val normalized = BERTPriceHistory.normalize(
            listOf(
                BERTPriceSample(now - 2_000, 0.9),
                BERTPriceSample(now - 1_000, 1.0),
                BERTPriceSample(now - 2_000, 1.1),
                BERTPriceSample(now, Double.NaN),
            ),
            now,
        )

        assertEquals(listOf(now - 2_000, now - 1_000), normalized.map { it.observedAtEpochMillis })
        assertEquals(1.1, normalized.first().priceUsd, 0.0)
    }

    @Test
    fun `normalization retains only the rolling 24 hour window`() {
        val day = 24L * 60L * 60L * 1_000L
        val now = day * 2
        val normalized = BERTPriceHistory.normalize(
            listOf(
                BERTPriceSample(now - day - 1, 1.0),
                BERTPriceSample(now - day, 2.0),
                BERTPriceSample(now, 3.0),
            ),
            now,
        )

        assertEquals(listOf(2.0, 3.0), normalized.map { it.priceUsd })
    }
}
