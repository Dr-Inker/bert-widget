package global.bert.widget.data

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTDirectSourcesTest {
    private val mint = BERTQuoteRepository.BERT_MINT
    private val pool = "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"
    private val now = 1_790_869_620_000L // 2026-10-01T15:47:00Z
    private val aligned = now / 1_000 - (now / 1_000) % 300
    private val direct = "https://direct.test/pairs"
    private val server = "https://server.test/quote"
    private val gecko = "https://gecko.test/api/v2"
    private val serverHistory = "https://server.test/history"

    private fun pair(address: String, liquidity: Double, price: String = "0.0153", tokenMint: String = mint, chain: String = "solana") =
        JSONObject().put("chainId", chain).put("dexId", "raydium").put("pairAddress", address)
            .put("url", "https://dexscreener.com/solana/${address.lowercase()}")
            .put("baseToken", JSONObject().put("address", tokenMint).put("symbol", "Bert"))
            .put("priceUsd", price).put("marketCap", 15_067_640).put("priceChange", JSONObject().put("h24", 2.01))
            .put("volume", JSONObject().put("h24", 136_801.77)).put("liquidity", JSONObject().put("usd", liquidity))

    private fun pairs(vararg items: JSONObject) = JSONArray().apply { items.forEach { put(it) } }.toString()

    private fun gecko(vararg candles: Pair<Long, Double>, base: String = mint) = JSONObject()
        .put("data", JSONObject().put("attributes", JSONObject().put("ohlcv_list",
            JSONArray().apply { candles.forEach { (t, c) -> put(JSONArray().put(t).put(c).put(c).put(c).put(c).put(1.0)) } })))
        .put("meta", JSONObject().put("base", JSONObject().put("address", base))).toString()

    private fun serverQuote(price: Double) = JSONObject()
        .put("asset", JSONObject().put("chain", "solana").put("mint", mint))
        .put("quote", JSONObject().put("priceUsd", price))
        .put("source", JSONObject().put("name", "dexscreener").put("pairAddress", pool).put("dex", "raydium")
            .put("pairUrl", "https://dexscreener.com/solana/${pool.lowercase()}").put("observedAt", "2026-10-01T15:47:00Z"))
        .put("meta", JSONObject().put("freshness", "fresh")).toString()

    private class FakeHttp(private val responses: Map<String, () -> String>) : BERTHttp {
        val requested = mutableListOf<String>()
        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
            requested += url
            return (responses.entries.firstOrNull { url.startsWith(it.key) }?.value ?: error("unexpected $url")).invoke()
        }
    }

    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        listOf("bert_quote", "bert_market_history", "bert_price_history").forEach {
            context.getSharedPreferences(it, 0).edit().clear().commit()
        }
    }

    @Test fun `deepest-liquidity BERT pool wins and keeps its case-sensitive address`() {
        val other = "EiPnoq5tF5dM7jkpubLZGbbrCwvKKiqcxqN7qUPFxCtz"
        val envelope = JSONObject(BERTDirectSources.quoteEnvelope(pairs(
            pair(other, 60_000.0, "0.0160"),
            pair(pool, 1_178_452.0),
            pair("So11111111111111111111111111111111111111112", 9e9, tokenMint = "So11111111111111111111111111111111111111112"),
            pair("7NMTdDkHW8J2fx7VvuqkttR13ZHV4ZP1VtbA9JT2k4GZ", 9e9, chain = "ethereum"),
        ), now))
        assertEquals(pool, envelope.getJSONObject("source").getString("pairAddress"))
        assertEquals(0.0153, envelope.getJSONObject("quote").getDouble("priceUsd"), 0.0)
        assertEquals(2.01, envelope.getJSONObject("quote").getDouble("change24hPct"), 0.0)
    }

    @Test fun `no valid BERT pool or a foreign pair URL is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BERTDirectSources.quoteEnvelope(pairs(pair(pool, 1.0, tokenMint = "So11111111111111111111111111111111111111112")), now)
        }
        val evil = pair(pool, 1.0).put("url", "https://evil.test/solana/abc")
        assertThrows(IllegalArgumentException::class.java) { BERTDirectSources.quoteEnvelope(pairs(evil), now) }
    }

    @Test fun `history for another token is rejected and bad candles dropped`() {
        assertThrows(IllegalArgumentException::class.java) {
            BERTDirectSources.historyEnvelope(gecko(aligned to 1.0, aligned - 300 to 1.0, base = "So11111111111111111111111111111111111111112"), pool, now)
        }
        val envelope = JSONObject(BERTDirectSources.historyEnvelope(gecko(
            aligned to 0.02, aligned - 300 to 0.019, aligned - 301 to 0.5, aligned - 600 to 0.0, aligned - 90_000 to 0.5,
        ), pool, now))
        assertEquals(2, envelope.getJSONArray("points").length())
        assertEquals(2, BERTMarketHistoryRepository.parse(envelope.toString(), now).size)
    }

    @Test fun `quote comes from the direct source without touching the server`() = runBlocking {
        val http = FakeHttp(mapOf(direct to { pairs(pair(pool, 1.0)) }, server to { error("server called") }))
        val quote = BERTQuoteRepository(context, http, direct, server).refresh(now)
        assertEquals(listOf(direct), http.requested)
        assertEquals(pool, quote.pairAddress)
        assertEquals(pool, BERTQuoteRepository(context, http, direct, server).load()?.pairAddress)
    }

    @Test fun `server is tagged fallback when the direct source fails or changes format`() = runBlocking {
        for (failure in listOf<() -> String>({ error("offline") }, { """{"unexpected":"shape"}""" })) {
            val http = FakeHttp(mapOf(direct to failure, server to { serverQuote(0.0151) }))
            val quote = BERTQuoteRepository(context, http, direct, server).refresh(now)
            assertEquals(listOf(direct, "$server?via=fallback"), http.requested)
            assertEquals(0.0151, quote.priceUsd, 0.0)
        }
    }

    @Test fun `builds without a direct source use the server untagged`() = runBlocking {
        val http = FakeHttp(mapOf(server to { serverQuote(0.0151) }))
        BERTQuoteRepository(context, http, "", server).refresh(now)
        assertEquals(listOf(server), http.requested)
    }

    @Test fun `history goes direct for the saved quote's pool and falls back to the server`() = runBlocking {
        val quoteHttp = FakeHttp(mapOf(direct to { pairs(pair(pool, 1.0)) }))
        BERTQuoteRepository(context, quoteHttp, direct, server).refresh(now)

        val ok = FakeHttp(mapOf(gecko to { gecko(aligned to 0.02, aligned - 300 to 0.019) }))
        val samples = BERTMarketHistoryRepository(context, ok, gecko, serverHistory).refresh(now)
        assertEquals(1, ok.requested.size)
        assertEquals(true, ok.requested.single().startsWith("$gecko/networks/solana/pools/$pool/ohlcv/minute?"))
        assertEquals(2, samples.size)

        val serverBody = BERTDirectSources.historyEnvelope(gecko(aligned to 0.03, aligned - 300 to 0.031), pool, now)
        val failing = FakeHttp(mapOf(gecko to { error("cold timeout") }, serverHistory to { serverBody }))
        val fallback = BERTMarketHistoryRepository(context, failing, gecko, serverHistory).refresh(now)
        assertEquals("$serverHistory?via=fallback", failing.requested.last())
        assertEquals(0.03, fallback.last().priceUsd, 0.0)
    }
}
