package global.bert.widget.work

import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTQuote
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BERTWidgetStaleRenderWorkerTest {
    private fun quote(time: Long, freshness: String = "fresh") = BERTQuote(
        0.005, 5.0, null, null, null, freshness, time, "dexscreener", "raydium",
        "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    )

    @Test fun `re-render lands just after the quote turns delayed`() {
        val observed = 10_000_000L
        val now = observed + 10 * 60_000L
        val delay = BERTWidgetStaleRenderWorker.delayUntilStaleMillis(quote(observed), now)!!
        assertEquals(20 * 60_000L + 5_000L, delay)
        val rendersStale = quote(observed).isStaleAt(now + delay)
        assertEquals(true, rendersStale)
    }

    @Test fun `nothing is scheduled for missing or already delayed quotes`() {
        assertNull(BERTWidgetStaleRenderWorker.delayUntilStaleMillis(null, 0))
        assertNull(BERTWidgetStaleRenderWorker.delayUntilStaleMillis(quote(0, "stale"), 1))
        assertNull(BERTWidgetStaleRenderWorker.delayUntilStaleMillis(quote(0), BERTQuote.STALE_AFTER_MILLIS))
    }

    @Test fun `the Bert widget re-renders when its feed turns stale, and at midnight for its time`() {
        val zone = ZoneOffset.UTC
        val now = 1_760_000_000_000L // 09:33:20 UTC
        val feed = BERTActivity(now - 10 * 60_000L, "post", "content", null)
        assertEquals(20 * 60_000L + 5_000L, BERTWidgetStaleRenderWorker.nextRenderDelayMillis(null, feed, now, zone))
        // Feed already stale: only the next local midnight remains.
        val old = BERTActivity(now - 3_600_000L, "post", "content", null)
        val midnight = BERTWidgetStaleRenderWorker.nextRenderDelayMillis(null, old, now, zone)!!
        assertEquals(0L, (now + midnight - 5_000L) % 86_400_000L)
        // No feed and a stale quote: nothing to schedule, so the worker cannot loop.
        assertNull(BERTWidgetStaleRenderWorker.nextRenderDelayMillis(quote(0, "stale"), null, now, zone))
    }
}
