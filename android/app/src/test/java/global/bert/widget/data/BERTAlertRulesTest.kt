package global.bert.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BERTAlertRulesTest {
    private val now = 1_790_000_000_000L
    private fun quote(price: Double, observed: Long = now - 60_000, freshness: String = "fresh") = BERTQuote(
        price, 2.5, null, null, null, freshness, observed, "dexscreener", "raydium",
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
}
