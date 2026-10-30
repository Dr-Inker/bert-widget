package global.bert.widget.data

import android.content.Context
import global.bert.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * A long chart range's prices and when they were fetched, so the screen can show them at once and judge how stale they are.
 * [complete] is false when a later page could not be had (refused, rate-limited or dropped): the prices are real but may
 * stop short of the window, and the copy is asked for again sooner than a whole one would be.
 */
class BERTRangeHistory(val samples: List<BERTPriceSample>, val fetchedAtEpochMillis: Long, val complete: Boolean = true)

/**
 * Price history for the ranges longer than a day (1W, 1M, 1Y), straight from GeckoTerminal for the latest quote's pool.
 * There is no Berthalla fallback: the server only serves 24 hours, so a failure surfaces to the screen, which keeps
 * showing whatever was cached. Nothing here runs from the widget worker; the Market screen asks only when a range is chosen.
 */
class BERTRangeHistoryRepository(
    context: Context,
    private val http: BERTHttp = BERTHttp.DEFAULT,
    private val directBase: String = BuildConfig.BERT_DIRECT_HISTORY_BASE,
) {
    private val preferences = context.getSharedPreferences("bert_range_history", Context.MODE_PRIVATE)

    /** Whether this build asks GeckoTerminal at all: debug builds leave its address empty (their emulator fixtures serve the quote server only). */
    val hasDirectSource: Boolean get() = directBase.isNotEmpty()

    /** The last good result for [window] and [pool], or null when none is stored or the stored copy no longer validates. */
    fun cached(window: BERTChartWindow, pool: String?, nowEpochMillis: Long): BERTRangeHistory? {
        val spec = window.candles ?: return null
        if (pool == null) return null
        return preferences.getString(window.name, null)?.let { raw -> runCatching { parse(raw, spec, pool, nowEpochMillis) }.getOrNull() }
    }

    /**
     * Fetches the range page by page, newest first, and stores the merged result. Pages are requested one after another
     * (the free API allows about 30 calls a minute). The first page failing fails the refresh. A later page failing
     * (GeckoTerminal refusing it as too old, a 429, a dropped connection, a bad answer) keeps the pages already fetched:
     * half a year of real prices, labelled from their real first date, beats "Couldn’t load". That copy is stored as
     * incomplete, so it goes stale within the hour and the whole range is asked for again; meanwhile any older days the
     * previous stored copy had fill in behind it (a finished day's close never changes), so a 429 on the second page does
     * not shrink a year that was already on the phone.
     * A cold GeckoTerminal response can take 10-20 s, hence the long read timeout on a background call.
     */
    suspend fun refresh(window: BERTChartWindow, pool: String?, nowEpochMillis: Long): BERTRangeHistory = withContext(Dispatchers.IO) {
        val spec = requireNotNull(window.candles) { "${window.label} is not a long range" }
        require(hasDirectSource && pool != null) { "Direct history is not available" }
        val byOpen = sortedMapOf<Long, Double>()
        var before: Long? = null
        var complete = true
        for (page in 1..spec.maxPages) {
            coroutineContext.ensureActive()
            val candles = try {
                val raw = http.get(BERTDirectSources.rangeUrl(directBase, pool, spec, before), BERTDirectSources.MAX_RANGE_BYTES, 30_000)
                BERTDirectSources.rangeCandles(raw, spec, nowEpochMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (page == 1) throw failure
                complete = false
                break
            }
            val added = candles.count { (open, close) -> byOpen.put(open, close) == null }
            // Stop at the end of the data (an empty, repeated or short page), or once the window is covered.
            if (added == 0 || candles.size < SHORT_PAGE) break
            if (byOpen.firstKey() * 1_000 <= nowEpochMillis - window.durationMillis) break
            before = maxOf(byOpen.firstKey(), oldestMarker(spec, nowEpochMillis))
        }
        if (!complete) {
            val oldest = byOpen.firstKey()
            val windowStart = (nowEpochMillis - window.durationMillis) / 1_000 - spec.intervalSeconds
            storedPoints(window, spec, pool, nowEpochMillis).forEach { (open, close) -> if (open < oldest && open >= windowStart) byOpen[open] = close }
        }
        require(byOpen.size >= 2) { "Too few valid candles" }
        val points = JSONArray().apply { byOpen.forEach { (open, close) -> put(JSONArray().put(open).put(close)) } }
        val envelope = JSONObject()
            .put("asset", JSONObject().put("chain", "solana").put("mint", BERTQuoteRepository.BERT_MINT))
            .put("source", JSONObject().put("name", "geckoterminal").put("pairAddress", pool).put("intervalSeconds", spec.intervalSeconds))
            .put("fetchedAt", nowEpochMillis)
            .put("complete", complete)
            .put("points", points)
            .toString()
        val result = parse(envelope, spec, pool, nowEpochMillis)
        preferences.edit().putString(window.name, envelope).apply()
        result
    }

    /** The stored copy's [open, close] points for [window], when it still validates for [pool]; empty otherwise. */
    private fun storedPoints(window: BERTChartWindow, spec: BERTCandleSpec, pool: String, nowEpochMillis: Long): Map<Long, Double> {
        val raw = preferences.getString(window.name, null) ?: return emptyMap()
        return runCatching { parse(raw, spec, pool, nowEpochMillis); validPoints(JSONObject(raw).getJSONArray("points")) }.getOrDefault(emptyMap())
    }

    companion object {
        private const val SHORT_PAGE = 30
        // About a year of daily candles is the most any range holds (two pages of ~183); this leaves room above it.
        private const val MAX_POINTS = 1_500
        /**
         * The oldest page marker sent. GeckoTerminal's free API answers 401 ("You can only access data from the past 180
         * days") once before_timestamp is more than about 183 days old (182 d 20 h passed and 183 d failed on 29 October
         * 2026), and the first daily page's oldest candle is already 182 days and some hours back: right on that edge. A
         * marker no older than 182 days, rounded up to a candle open, keeps at least most of a day's margin; the day it
         * re-reads is de-duplicated. The second page then reaches back about 361 days.
         */
        private const val MAX_MARKER_AGE_SECONDS = 182 * 86_400L
        /** How soon an incomplete copy is asked for again, at most: soon enough to fill in, not so often it adds to a 429. */
        private const val INCOMPLETE_FRESH_MILLIS = 3_600_000L

        /** The oldest before_timestamp worth sending: [MAX_MARKER_AGE_SECONDS] back, rounded up to the next candle open. */
        internal fun oldestMarker(spec: BERTCandleSpec, nowEpochMillis: Long): Long {
            val limit = nowEpochMillis / 1_000 - MAX_MARKER_AGE_SECONDS
            return Math.floorDiv(limit + spec.alignSeconds - 1, spec.alignSeconds) * spec.alignSeconds
        }

        /** Whether a stored result is recent enough to show without asking again. A clock set backwards counts as stale. */
        fun isFresh(window: BERTChartWindow, history: BERTRangeHistory, nowEpochMillis: Long): Boolean {
            val spec = window.candles ?: return false
            val age = nowEpochMillis - history.fetchedAtEpochMillis
            return age in 0 until if (history.complete) spec.freshMillis else minOf(spec.freshMillis, INCOMPLETE_FRESH_MILLIS)
        }

        /** Each valid [candleOpenEpochSeconds, closeUsd] point, by open; malformed or impossible points are skipped. */
        private fun validPoints(points: JSONArray): Map<Long, Double> {
            require(points.length() <= MAX_POINTS) { "Too many history points" }
            val byOpen = sortedMapOf<Long, Double>()
            for (index in 0 until points.length()) {
                val point = points.optJSONArray(index) ?: continue
                val open = (point.opt(0) as? Number)?.toLong() ?: continue
                val close = (point.opt(1) as? Number)?.toDouble() ?: continue
                if (open <= 0 || !close.isFinite() || close <= 0) continue
                byOpen[open] = close
            }
            return byOpen
        }

        /**
         * Each point is [candleOpenEpochSeconds, closeUsd]. A close describes the end of its candle, so it is placed at
         * open + interval, capped for the candle still in progress at the moment the copy was fetched: a saved copy's
         * last close is the price then, not now, and drawing it at now would pass old news off as the latest price.
         */
        fun parse(raw: String, spec: BERTCandleSpec, pool: String, nowEpochMillis: Long): BERTRangeHistory {
            val root = JSONObject(raw)
            val asset = root.getJSONObject("asset")
            require(asset.getString("chain") == "solana") { "Unexpected chain" }
            require(asset.getString("mint") == BERTQuoteRepository.BERT_MINT) { "Unexpected BERT mint" }
            val source = root.getJSONObject("source")
            require(source.getString("pairAddress") == pool) { "Stored history is for another pool" }
            require(source.getLong("intervalSeconds") == spec.intervalSeconds) { "Unexpected interval" }
            val fetchedAt = root.getLong("fetchedAt")
            val cap = minOf(fetchedAt, nowEpochMillis)
            val byClose = sortedMapOf<Long, Double>()
            validPoints(root.getJSONArray("points")).forEach { (open, close) -> byClose[minOf((open + spec.intervalSeconds) * 1_000, cap)] = close }
            require(byClose.size >= 2) { "Too few valid points" }
            // Copies saved before "complete" was written were all whole.
            return BERTRangeHistory(byClose.map { BERTPriceSample(it.key, it.value) }, fetchedAt, root.optBoolean("complete", true))
        }
    }
}
