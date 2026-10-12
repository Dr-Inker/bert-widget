package global.bert.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BERTAlertRulesTest {
    private val now = 1_790_000_000_000L
    private fun quote(price: Double, observed: Long = now - 60_000, freshness: String = "fresh", change: Double? = 2.5) = BERTQuote(
        price, change, null, null, null, freshness, observed, "dexscreener", "raydium",
        "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY")
    private fun activity(dispatch: String? = "woofmornin.", endsIn: Long = 5 * 3_600_000L, updated: Long = now - 60_000) =
        BERTActivity(updated, dispatch, "content", BERTEvent("The Autumn Arc", now + endsIn, "live", pool = 250,
            standings = listOf(BERTStanding(1, "LamexCrypt", 143, 100))))

    @Test fun `nothing fires while everything is off`() {
        val (notices, _) = BERTAlertRules.evaluate(AlertSettings(), AlertMemory(), quote(1.0), activity(), now)
        assertTrue(notices.isEmpty())
    }

    @Test fun `tournament reminder fires once inside the last day and never for stale or distant events`() {
        val on = AlertSettings(tournament = true)
        val (first, memory) = BERTAlertRules.evaluate(on, AlertMemory(), null, activity(), now)
        assertEquals(listOf("The Autumn Arc ends in 5h"), first.map { it.title })
        assertTrue(first.single().text.contains("LamexCrypt leads with 143") && first.single().text.contains("$250 prize pool"))
        assertTrue(BERTAlertRules.evaluate(on, memory, null, activity(), now + 60_000).first.isEmpty())
        assertTrue(BERTAlertRules.evaluate(on, AlertMemory(), null, activity(endsIn = 30 * 3_600_000L), now).first.isEmpty())
        assertTrue(BERTAlertRules.evaluate(on, AlertMemory(), null, activity(updated = now - 3 * 3_600_000L), now).first.isEmpty())
    }

    @Test fun `the first dispatch after switching on is a baseline and later ones are news once`() {
        val on = AlertSettings(dispatch = true)
        val (baseline, memory) = BERTAlertRules.evaluate(on, AlertMemory(), null, activity("one"), now)
        assertTrue(baseline.isEmpty()); assertEquals("one", memory.lastDispatch)
        val (news, after) = BERTAlertRules.evaluate(on, memory, null, activity("two"), now)
        assertEquals("two", news.single().text)
        assertTrue(BERTAlertRules.evaluate(on, after, null, activity("two"), now).first.isEmpty())
    }

    @Test fun `price alerts fire once on fresh quotes only`() {
        val on = AlertSettings(priceAbove = 0.016, priceBelow = 0.012)
        assertTrue(BERTAlertRules.evaluate(on, AlertMemory(), quote(0.0155), null, now).first.isEmpty())
        val (up, memory) = BERTAlertRules.evaluate(on, AlertMemory(), quote(0.0162), null, now)
        assertEquals("BERT is above $0.016", up.single().title)
        assertTrue(up.single().text.startsWith("Now $0.0162"))
        assertTrue(BERTAlertRules.evaluate(on, memory, quote(0.0170), null, now).first.isEmpty())
        assertTrue(BERTAlertRules.evaluate(on, AlertMemory(), quote(0.0170, freshness = "stale"), null, now).first.isEmpty())
        assertEquals("BERT is below $0.012", BERTAlertRules.evaluate(on, AlertMemory(), quote(0.0119), null, now).first.single().title)
    }

    // --- re-arming ---
    private val levels = AlertSettings(priceAbove = 0.016, priceBelow = 0.012)

    @Test fun `a fired above level stays quiet inside the 3 percent band and re-arms outside it`() {
        val fired = AlertMemory(aboveFired = true)
        // 0.016 * 0.97 = 0.01552: above that the alert stays spent, however much the price wobbles.
        for (price in listOf(0.0170, 0.0160, 0.0158, 0.0156)) {
            val (notices, memory) = BERTAlertRules.evaluate(levels, fired, quote(price), null, now)
            assertTrue(notices.isEmpty()); assertTrue("$price", memory.aboveFired)
        }
        val (retreat, rearmed) = BERTAlertRules.evaluate(levels, fired, quote(0.0155), null, now)
        assertTrue(retreat.isEmpty()); assertFalse(rearmed.aboveFired)
        assertEquals("BERT is above $0.016", BERTAlertRules.evaluate(levels, rearmed, quote(0.0161), null, now).first.single().title)
    }

    @Test fun `a fired below level re-arms 3 percent above it`() {
        val fired = AlertMemory(belowFired = true)
        assertTrue(BERTAlertRules.evaluate(levels, fired, quote(0.0123), null, now).second.belowFired)
        assertFalse(BERTAlertRules.evaluate(levels, fired, quote(0.01237), null, now).second.belowFired)
        assertEquals("BERT is below $0.012", BERTAlertRules.evaluate(levels, AlertMemory(), quote(0.0119), null, now).first.single().title)
    }

    @Test fun `a stale quote neither fires nor re-arms`() {
        val fired = AlertMemory(aboveFired = true, belowFired = true)
        val stale = quote(0.010, freshness = "stale")
        val (notices, memory) = BERTAlertRules.evaluate(levels, fired, stale, null, now)
        assertTrue(notices.isEmpty()); assertEquals(fired, memory)
        assertEquals(fired, BERTAlertRules.evaluate(levels, fired, quote(0.010, observed = now - 31 * 60_000L), null, now).second)
    }

    @Test fun `a level crossed at save time is reported only while it will really fire`() {
        val settings = AlertSettings(priceAbove = 0.016, priceBelow = 0.012)
        assertEquals(true to false, BERTAlertRules.alreadyCrossed(settings, AlertMemory(), quote(0.0170), now))
        assertEquals(false to true, BERTAlertRules.alreadyCrossed(settings, AlertMemory(), quote(0.0110), now))
        assertEquals(false to false, BERTAlertRules.alreadyCrossed(settings, AlertMemory(), quote(0.0140), now))
        assertEquals("already announced, so no promise of another alert", false to false,
            BERTAlertRules.alreadyCrossed(settings, AlertMemory(aboveFired = true), quote(0.0170), now))
        assertEquals(false to false, BERTAlertRules.alreadyCrossed(settings, AlertMemory(), quote(0.0170, freshness = "stale"), now))
        assertEquals(false to false, BERTAlertRules.alreadyCrossed(settings, AlertMemory(), null, now))
    }

    // --- big move ---
    private val big = AlertSettings(bigMovePct = 10)
    private fun bigMove(memory: AlertMemory, change: Double?, at: Long = now, price: Double = 0.0135, freshness: String = "fresh") =
        BERTAlertRules.evaluate(big, memory, quote(price, observed = at - 60_000, freshness = freshness, change = change), null, at, java.time.ZoneOffset.UTC)

    @Test fun `big move is off by default and below the threshold`() {
        assertTrue(BERTAlertRules.evaluate(AlertSettings(), AlertMemory(), quote(0.0135, change = 40.0), null, now).first.isEmpty())
        assertTrue(bigMove(AlertMemory(), 9.99).first.isEmpty())
        assertTrue(bigMove(AlertMemory(), null).first.isEmpty())
    }

    @Test fun `big move says what happened in plain words with the quote time and source`() {
        val at = 1_790_000_000_000L // 2026-09-21T14:13:20Z
        val (up, memory) = bigMove(AlertMemory(), 12.4, at)
        val notice = up.single()
        assertEquals(AlertChannel.PRICE, notice.channel); assertEquals(1005, notice.id)
        assertEquals("BERT is up 12% in 24h", notice.title)
        assertEquals("Now $0.0135 · as of 14:12 · DEX Screener", notice.text)
        assertEquals(1, memory.bigMoveSide); assertEquals(at, memory.bigMoveAtEpochMillis)
        assertEquals("BERT is down 15% in 24h", bigMove(AlertMemory(), -14.6, at).first.single().title)
    }

    @Test fun `big move notifies once, then only after the move fades and the cooldown has passed`() {
        val sixHours = BERTAlertRules.BIG_MOVE_COOLDOWN_MILLIS
        val (_, announced) = bigMove(AlertMemory(), 12.0)
        // Still above the threshold: nothing more, however long it lasts.
        assertTrue(bigMove(announced, 15.0, now + 7 * 3_600_000L).first.isEmpty())
        // Fades below the threshold: re-armed, cooldown clock untouched.
        val (quiet, faded) = bigMove(announced, 4.0, now + 3_600_000L)
        assertTrue(quiet.isEmpty()); assertEquals(0, faded.bigMoveSide); assertEquals(announced.bigMoveAtEpochMillis, faded.bigMoveAtEpochMillis)
        // Back over the threshold inside the cooldown: held back, and still pending.
        val (held, stillArmed) = bigMove(faded, 11.0, now + 2 * 3_600_000L)
        assertTrue(held.isEmpty()); assertEquals(0, stillArmed.bigMoveSide)
        // Same condition once six hours have passed: announced.
        assertEquals("BERT is up 11% in 24h", bigMove(stillArmed, 11.0, now + sixHours).first.single().title)
        assertTrue(bigMove(stillArmed, 11.0, now + sixHours - 1).first.isEmpty())
    }

    @Test fun `a swing from up to down is a new move but still waits out the cooldown`() {
        val (_, up) = bigMove(AlertMemory(), 12.0)
        val (soon, unchanged) = bigMove(up, -13.0, now + 3_600_000L)
        assertTrue(soon.isEmpty()); assertEquals(up, unchanged)
        val later = bigMove(up, -13.0, now + BERTAlertRules.BIG_MOVE_COOLDOWN_MILLIS)
        assertEquals("BERT is down 13% in 24h", later.first.single().title)
        assertEquals(-1, later.second.bigMoveSide)
    }

    @Test fun `a stale quote cannot raise or reset a big move`() {
        assertTrue(bigMove(AlertMemory(), 30.0, freshness = "stale").first.isEmpty())
        val announced = AlertMemory(bigMoveSide = 1, bigMoveAtEpochMillis = now - 1_000)
        assertEquals(announced, bigMove(announced, 1.0, freshness = "stale").second)
    }

    @Test fun `price crossing and big move can both notify from one fresh quote`() {
        val both = AlertSettings(priceAbove = 0.016, bigMovePct = 10)
        val (notices, _) = BERTAlertRules.evaluate(both, AlertMemory(), quote(0.0170, change = 18.0), null, now)
        assertEquals(listOf(1003, 1005), notices.map { it.id })
    }
}
