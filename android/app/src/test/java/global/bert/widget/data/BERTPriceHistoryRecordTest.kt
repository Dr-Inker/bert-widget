package global.bert.widget.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTPriceHistoryRecordTest {
    private fun quote(time: Long, price: Double) = BERTQuote(
        price, null, null, null, null, "fresh", time, "dexscreener", "raydium",
        "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    )

    @Test fun `a server clock ahead of the device neither vanishes nor wipes history`() {
        val history = BERTPriceHistory(RuntimeEnvironment.getApplication())
        val now = 100L * 24 * 3_600_000L
        history.record(quote(now - 60_000L, 1.0), now)
        // Observed five minutes "in the future" relative to this device's clock.
        history.record(quote(now + 5 * 60_000L, 2.0), now)

        val stored = history.load(now)
        assertEquals(listOf(1.0, 2.0), stored.map { it.priceUsd })
        assertEquals(now, stored.last().observedAtEpochMillis)
    }

    @Test fun `concurrent recorders keep every sample`() {
        val history = BERTPriceHistory(RuntimeEnvironment.getApplication())
        val now = 100L * 24 * 3_600_000L
        val threads = (0 until 16).map { thread ->
            Thread { repeat(10) { step -> history.record(quote(now - (thread * 10 + step) * 1_000L, 1.0), now) } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(160, history.load(now).size)
    }
}
