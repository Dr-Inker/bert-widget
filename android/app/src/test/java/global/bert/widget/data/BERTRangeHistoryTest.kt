package global.bert.widget.data

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Long chart ranges (1W, 1M, 1Y): URLs, paging, validation and the on-device cache. No network: pages are fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTRangeHistoryTest {
    private val mint = BERTQuoteRepository.BERT_MINT
    private val pool = "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"
    private val base = "https://gecko.test/api/v2"
    private val day = 86_400L
    private val nowSeconds = 1_793_318_400L // 2026-10-29T00:00:00Z, a UTC midnight
    private val now = nowSeconds * 1_000 + 7 * 3_600_000L // 07:00 that day, so the day's candle is still open
    private val context get() = RuntimeEnvironment.getApplication()

    private fun page(opens: List<Long>, closeOf: (Long) -> Double = { 0.01 + it % 1_000 * 1e-6 }, address: String = mint) = JSONObject()
        .put("data", JSONObject().put("attributes", JSONObject().put("ohlcv_list",
            // GeckoTerminal answers newest first.
            JSONArray().apply { opens.sortedDescending().forEach { t -> put(JSONArray().put(t).put(0.0).put(0.0).put(0.0).put(closeOf(t)).put(1.0)) } })))
        .put("meta", JSONObject().put("base", JSONObject().put("address", address))).toString()

    /** [count] daily opens ending at [newest]. */
    private fun days(newest: Long, count: Int) = (0 until count).map { newest - it * day }

    private class FakeHttp(private val answer: (String) -> String) : BERTHttp {
        val requested = mutableListOf<String>()
        val limits = mutableListOf<Int>()
        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
            requested += url
            limits += maxBytes
            return answer(url)
        }
    }

    private fun repository(http: BERTHttp) = BERTRangeHistoryRepository(context, http, base)

    private fun marker(url: String) = Regex("before_timestamp=(\\d+)").find(url)?.groupValues?.get(1)?.toLong()

    /**
     * A server shaped like GeckoTerminal's free API (as measured on 29 October 2026), holding daily candles from [oldest]
     * to [newest]. Without a marker it answers the newest 183 candles; with before_timestamp, the candle opening at or
     * before it and 180 more. A marker more than 183 days before [at] is refused with a 401, as the real one refuses
     * anything past its free 180-ish days: a fake that paged back forever let "All" pass its tests.
     */
    private fun dailyServer(oldest: Long, newest: Long, at: Long = now) = { url: String ->
        val before = marker(url)
        if (before != null && at / 1_000 - before > 183 * day) throw BERTHttpStatusException(401, "gecko.test")
        val last = if (before == null) newest else minOf(newest, before - Math.floorMod(before, day))
        page(generateSequence(last) { it - day }.takeWhile { it >= oldest }.take(if (before == null) 183 else 181).toList())
    }

    @Test fun `each long range asks GeckoTerminal for its own candles`() {
        fun url(window: BERTChartWindow, before: Long? = null) = BERTDirectSources.rangeUrl(base, pool, requireNotNull(window.candles), before)
        assertEquals("$base/networks/solana/pools/$pool/ohlcv/hour?aggregate=1&limit=169&currency=usd&token=base", url(BERTChartWindow.WEEK))
        assertEquals("$base/networks/solana/pools/$pool/ohlcv/hour?aggregate=4&limit=181&currency=usd&token=base", url(BERTChartWindow.MONTH))
        assertEquals("$base/networks/solana/pools/$pool/ohlcv/day?aggregate=1&limit=1000&currency=usd&token=base", url(BERTChartWindow.YEAR))
        assertEquals("$base/networks/solana/pools/$pool/ohlcv/day?aggregate=1&limit=1000&currency=usd&token=base&before_timestamp=1777000000",
            url(BERTChartWindow.YEAR, 1_777_000_000))
        assertThrows(IllegalArgumentException::class.java) { BERTDirectSources.rangeUrl(base, "../not-a-pool", requireNotNull(BERTChartWindow.WEEK.candles)) }
    }

    @Test fun `a week and a month are one request each`() = runBlocking {
        val hours = (0 until 169).map { nowSeconds - it * 3_600L }
        val http = FakeHttp { page(hours) }
        val week = repository(http).refresh(BERTChartWindow.WEEK, pool, now)
        assertEquals(1, http.requested.size)
        assertEquals(169, week.samples.size)
        val fourHours = (0 until 181).map { nowSeconds - it * 14_400L }
        val monthHttp = FakeHttp { page(fourHours) }
        assertEquals(181, repository(monthHttp).refresh(BERTChartWindow.MONTH, pool, now).samples.size)
        assertEquals(1, monthHttp.requested.size)
    }

    @Test fun `a year takes two pages that merge sorted without duplicates`() = runBlocking {
        val oldest = nowSeconds - 400 * day
        val http = FakeHttp(dailyServer(oldest, nowSeconds))
        val year = repository(http).refresh(BERTChartWindow.YEAR, pool, now)
        assertEquals(2, http.requested.size)
        assertFalse(http.requested[0].contains("before_timestamp"))
        // The first page's oldest candle opened 182 days ago and it is 07:00, so that marker would be 182 d 7 h old: too
        // near the free API's ~183-day limit. The second page starts a day newer instead, and the repeated day is merged.
        assertTrue(http.requested[1].endsWith("before_timestamp=${nowSeconds - 181 * day}"))
        assertTrue(year.complete)
        // 183 + 181 candles, one shared: today back to 361 days ago, which is as far as the free API reaches.
        assertEquals(362, year.samples.size)
        assertEquals((nowSeconds - 360 * day) * 1_000, year.samples.first().observedAtEpochMillis)
        assertFalse(BERTChartWindow.YEAR.startsLate(year.samples, now))
        assertEquals(year.samples.sortedBy { it.observedAtEpochMillis }, year.samples)
        assertEquals(year.samples.size, year.samples.map { it.observedAtEpochMillis }.distinct().size)
        assertTrue(http.limits.all { it == BERTDirectSources.MAX_RANGE_BYTES })
    }

    @Test fun `the second page stays inside the free API's reach at every hour of the day`() {
        for (hour in 0 until 24) {
            val at = nowSeconds * 1_000 + hour * 3_600_000L
            val http = FakeHttp(dailyServer(nowSeconds - 400 * day, nowSeconds, at))
            val year = runBlocking { repository(http).refresh(BERTChartWindow.YEAR, pool, at) }
            val before = marker(http.requested[1])!!
            assertTrue("at $hour:00 the marker is ${(at / 1_000 - before) / 3_600.0} hours old", at / 1_000 - before <= 182 * day)
            assertTrue("at $hour:00 the year is complete", year.complete)
            assertFalse("at $hour:00 the year starts late", BERTChartWindow.YEAR.startsLate(year.samples, at))
        }
    }

    @Test fun `overlapping pages are de-duplicated by candle open`() = runBlocking {
        var calls = 0
        val http = FakeHttp { if (calls++ == 0) page(days(nowSeconds, 183)) else page(days(nowSeconds - 180 * day, 183)) }
        val year = repository(http).refresh(BERTChartWindow.YEAR, pool, now)
        assertEquals(183 + 180, year.samples.size)
    }

    @Test fun `a young pool's year ends where its data does`() = runBlocking {
        val http = FakeHttp(dailyServer(nowSeconds - 100 * day, nowSeconds))
        val year = repository(http).refresh(BERTChartWindow.YEAR, pool, now)
        assertEquals(101, year.samples.size)
        assertTrue("nothing was refused, so the copy is whole", year.complete)
    }

    @Test fun `a page that repeats itself ends the walk`() = runBlocking {
        val http = FakeHttp { page(days(nowSeconds, 183)) }
        repository(http).refresh(BERTChartWindow.YEAR, pool, now)
        assertEquals(2, http.requested.size)
    }

    @Test fun `an empty first page is a failure and leaves the cache alone`() {
        val repo = repository(FakeHttp { page(emptyList()) })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.refresh(BERTChartWindow.YEAR, pool, now) } }
        assertNull(repo.cached(BERTChartWindow.YEAR, pool, now))
    }

    @Test fun `bad pages are rejected`() {
        val spec = requireNotNull(BERTChartWindow.YEAR.candles)
        val good = days(nowSeconds, 10)
        fun rejects(raw: String) = assertThrows(Exception::class.java) { BERTDirectSources.rangeCandles(raw, spec, now) }
        rejects(page(good, address = "So11111111111111111111111111111111111111112"))
        rejects("not json")
        rejects("""{"data":{"attributes":{}},"meta":{"base":{"address":"$mint"}}}""")
        rejects(page((0 until 1_001).map { nowSeconds - it * day })) // more candles than a page can hold
        // Mostly misaligned: more bad candles than good.
        rejects(page(good.take(3) + (1..5).map { nowSeconds - it * day + 17 }))
        // One bad candle among good ones is dropped, as in the 24-hour history.
        assertEquals(10, BERTDirectSources.rangeCandles(page(good + (nowSeconds - 20 * day + 5)), spec, now).size)
    }

    @Test fun `candles with impossible closes or times are dropped`() {
        val spec = requireNotNull(BERTChartWindow.YEAR.candles)
        val good = days(nowSeconds - day, 5)
        val raw = JSONObject(page(good))
        val list = raw.getJSONObject("data").getJSONObject("attributes").getJSONArray("ohlcv_list")
        listOf(
            JSONArray().put(nowSeconds - 30 * day).put(0).put(0).put(0).put(0.0).put(1), // zero close
            JSONArray().put(nowSeconds - 31 * day).put(0).put(0).put(0).put(-1.0).put(1), // negative close
            JSONArray().put(nowSeconds + 5 * day).put(0).put(0).put(0).put(0.01).put(1), // from the future
            JSONArray().put(86_400L * 100).put(0).put(0).put(0).put(0.01).put(1), // from before the token existed
            JSONArray().put(nowSeconds - 32 * day).put(0).put(0).put(0).put("x").put(1), // not a number
            JSONArray().put(nowSeconds - 33 * day).put(1), // too short
        ).forEach { list.put(it) }
        // Six bad candles against five good ones is a bad page; with enough good ones the bad are simply dropped.
        assertThrows(IllegalArgumentException::class.java) { BERTDirectSources.rangeCandles(raw.toString(), spec, now) }
        val roomy = JSONObject(page(days(nowSeconds - day, 8)))
        val roomyList = roomy.getJSONObject("data").getJSONObject("attributes").getJSONArray("ohlcv_list")
        for (index in 0 until list.length()) if (index >= 5) roomyList.put(list.get(index))
        assertEquals(8, BERTDirectSources.rangeCandles(roomy.toString(), spec, now).size)
    }

    @Test fun `hourly candles must open on the hour`() {
        val spec = requireNotNull(BERTChartWindow.WEEK.candles)
        val onTheHour = (0 until 5).map { nowSeconds - it * 3_600L }
        assertEquals(5, BERTDirectSources.rangeCandles(page(onTheHour), spec, now).size)
        // Four-hour candles are checked against the hour they open on, not a four-hour grid.
        assertEquals(3, BERTDirectSources.rangeCandles(page(listOf(nowSeconds - 3_600L, nowSeconds - 7_200L, nowSeconds - 10_800L)), requireNotNull(BERTChartWindow.MONTH.candles), now).size)
        assertThrows(IllegalArgumentException::class.java) { BERTDirectSources.rangeCandles(page(onTheHour.map { it + 60 }), spec, now) }
    }

    @Test fun `a refused, rate-limited or dropped second page keeps the first half year`() {
        val failures = listOf<() -> Nothing>(
            { throw BERTHttpStatusException(401, "gecko.test") }, // "You can only access data from the past 180 days"
            { throw BERTHttpStatusException(429, "gecko.test") },
            { throw java.io.IOException("connection reset") },
            { error("not json") },
        )
        for (fail in failures) {
            context.getSharedPreferences("bert_range_history", 0).edit().clear().commit()
            val server = dailyServer(nowSeconds - 400 * day, nowSeconds)
            val http = FakeHttp { url -> if (marker(url) == null) server(url) else fail() }
            val repo = repository(http)
            val year = runBlocking { repo.refresh(BERTChartWindow.YEAR, pool, now) }
            assertEquals(2, http.requested.size)
            assertEquals(183, year.samples.size)
            assertFalse(year.complete)
            // The chart labels this from its real first date, not "1 year ago".
            assertTrue(BERTChartWindow.YEAR.startsLate(year.samples, now))
            // Saved as incomplete: shown at once next time, but asked for again within the hour rather than the day.
            val saved = repo.cached(BERTChartWindow.YEAR, pool, now)!!
            assertFalse(saved.complete)
            assertEquals(183, saved.samples.size)
            assertTrue(BERTRangeHistoryRepository.isFresh(BERTChartWindow.YEAR, saved, now + 59 * 60_000L))
            assertFalse(BERTRangeHistoryRepository.isFresh(BERTChartWindow.YEAR, saved, now + 60 * 60_000L))
        }
    }

    @Test fun `a refused second page keeps the older days the saved year already had`() = runBlocking {
        val repo = repository(FakeHttp(dailyServer(nowSeconds - 400 * day, nowSeconds)))
        val whole = repo.refresh(BERTChartWindow.YEAR, pool, now)
        // A day later the second page is rate-limited; the first page is fresh, and the saved copy's older days fill in.
        val later = now + day * 1_000
        val server = dailyServer(nowSeconds - 400 * day, nowSeconds + day, later)
        val limited = repository(FakeHttp { url -> if (marker(url) == null) server(url) else throw BERTHttpStatusException(429, "gecko.test") })
        val year = limited.refresh(BERTChartWindow.YEAR, pool, later)
        assertFalse(year.complete)
        assertEquals(whole.samples.first().observedAtEpochMillis, year.samples.first().observedAtEpochMillis)
        assertEquals(whole.samples.size + 1, year.samples.size) // one new day, nothing lost
        assertFalse(BERTChartWindow.YEAR.startsLate(year.samples, later))
        // Still incomplete, so the whole range is asked for again within the hour.
        assertFalse(BERTRangeHistoryRepository.isFresh(BERTChartWindow.YEAR, limited.cached(BERTChartWindow.YEAR, pool, later)!!, later + 3_600_000L))
    }

    @Test fun `a failed first page fails the refresh and keeps the last good copy`() {
        val repo = repository(FakeHttp(dailyServer(nowSeconds - 400 * day, nowSeconds)))
        runBlocking { repo.refresh(BERTChartWindow.YEAR, pool, now) }
        val before = repo.cached(BERTChartWindow.YEAR, pool, now)!!
        val busy = repository(FakeHttp { throw BERTHttpStatusException(429, "gecko.test") })
        val thrown = assertThrows(BERTHttpStatusException::class.java) { runBlocking { busy.refresh(BERTChartWindow.YEAR, pool, now + 1_000) } }
        assertEquals(429, thrown.status)
        assertEquals(before.fetchedAtEpochMillis, repo.cached(BERTChartWindow.YEAR, pool, now)!!.fetchedAtEpochMillis)
        assertTrue(repo.cached(BERTChartWindow.YEAR, pool, now)!!.complete)
    }

    @Test fun `closes sit at the end of their candle`() = runBlocking {
        val open = nowSeconds - 3 * day
        val repo = repository(FakeHttp { page(listOf(open, open + day, nowSeconds), closeOf = { if (it == open) 0.5 else 0.25 }) })
        val samples = repo.refresh(BERTChartWindow.YEAR, pool, now).samples
        assertEquals(listOf((open + day) * 1_000, (open + 2 * day) * 1_000, now), samples.map { it.observedAtEpochMillis })
        assertEquals(listOf(0.5, 0.25, 0.25), samples.map { it.priceUsd })
    }

    @Test fun `a saved copy's open day closes when it was fetched, not now`() = runBlocking {
        val repo = repository(FakeHttp { page(days(nowSeconds, 30)) })
        repo.refresh(BERTChartWindow.YEAR, pool, now)
        // Three hours on, the saved copy's last price is still the 07:00 one: drawing it at 10:00 would pass it off as current.
        assertEquals(now, repo.cached(BERTChartWindow.YEAR, pool, now + 3 * 3_600_000L)!!.samples.last().observedAtEpochMillis)
        // A clock set back before the fetch caps at the clock instead, so nothing is drawn in the future.
        assertEquals(now - 60_000L, repo.cached(BERTChartWindow.YEAR, pool, now - 60_000L)!!.samples.last().observedAtEpochMillis)
    }

    @Test fun `results are cached per range and pool`() = runBlocking {
        val repo = repository(FakeHttp { page(days(nowSeconds, 30)) })
        assertNull(repo.cached(BERTChartWindow.YEAR, pool, now))
        val fetched = repo.refresh(BERTChartWindow.YEAR, pool, now)
        val loaded = requireNotNull(repo.cached(BERTChartWindow.YEAR, pool, now + 60_000))
        assertEquals(now, loaded.fetchedAtEpochMillis)
        assertEquals(fetched.samples.size, loaded.samples.size)
        assertNull(repo.cached(BERTChartWindow.WEEK, pool, now)) // another range
        assertNull(repo.cached(BERTChartWindow.YEAR, "Bm1111111111111111111111111111111111111111", now)) // another pool
        assertNull(repo.cached(BERTChartWindow.YEAR, null, now))
        context.getSharedPreferences("bert_range_history", 0).edit().putString("YEAR", "{broken").commit()
        assertNull(repo.cached(BERTChartWindow.YEAR, pool, now))
    }

    @Test fun `a stored copy for another token is not trusted`() = runBlocking {
        val repo = repository(FakeHttp { page(days(nowSeconds, 30)) })
        repo.refresh(BERTChartWindow.YEAR, pool, now)
        val prefs = context.getSharedPreferences("bert_range_history", 0)
        val tampered = JSONObject(prefs.getString("YEAR", null)!!)
        tampered.getJSONObject("asset").put("mint", "So11111111111111111111111111111111111111112")
        prefs.edit().putString("YEAR", tampered.toString()).commit()
        assertNull(repo.cached(BERTChartWindow.YEAR, pool, now))
    }

    @Test fun `freshness is an hour, four hours and a day`() {
        fun history(window: BERTChartWindow, age: Long) = BERTRangeHistoryRepository.isFresh(window, BERTRangeHistory(emptyList(), now - age), now)
        val minute = 60_000L
        val hour = 60 * minute
        assertTrue(history(BERTChartWindow.WEEK, 59 * minute)); assertFalse(history(BERTChartWindow.WEEK, hour))
        assertTrue(history(BERTChartWindow.MONTH, 3 * hour + 59 * minute)); assertFalse(history(BERTChartWindow.MONTH, 4 * hour))
        assertTrue(history(BERTChartWindow.YEAR, 23 * hour)); assertFalse(history(BERTChartWindow.YEAR, 24 * hour))
        // An incomplete copy is fresh for an hour at most, so the whole range is asked for again soon.
        fun partial(window: BERTChartWindow, age: Long) = BERTRangeHistoryRepository.isFresh(window, BERTRangeHistory(emptyList(), now - age, complete = false), now)
        assertTrue(partial(BERTChartWindow.YEAR, 59 * minute)); assertFalse(partial(BERTChartWindow.YEAR, hour))
        assertFalse(partial(BERTChartWindow.WEEK, hour))
        assertFalse("a clock set backwards is not fresh", history(BERTChartWindow.WEEK, -minute))
        assertFalse(history(BERTChartWindow.DAY, 0)) // the 24-hour range is not a long range
    }

    @Test fun `without a direct source or pool there is nothing to fetch`() {
        val http = FakeHttp { error("network used") }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { BERTRangeHistoryRepository(context, http, "").refresh(BERTChartWindow.WEEK, pool, now) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(http).refresh(BERTChartWindow.WEEK, null, now) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository(http).refresh(BERTChartWindow.DAY, pool, now) } }
        assertTrue(http.requested.isEmpty())
    }
}
