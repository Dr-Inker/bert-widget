package global.bert.widget

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import global.bert.widget.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.net.URI

/** The Market screen's six chart ranges and its CoinMarketCap link, host-rendered with fixture pages and no network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarketRangesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val pool = "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"
    private val now = 1_789_000_000_000L
    private val nowSeconds = now / 1_000
    private val labels = BERTChartWindow.entries.map { it.label }

    /**
     * Serves hourly, 4-hourly and daily pages shaped like GeckoTerminal's free API; the price climbs steadily from the
     * pool's first day to now. Like the real one, it refuses (401) a before_timestamp more than 183 days back, and a page
     * marked with one holds the candle at or before it and 180 more. [failing] is a dropped connection; [status] answers
     * every request with that HTTP status, [laterStatus] only the requests for older pages.
     */
    private inner class FakeGecko(var failing: Boolean = false, var status: Int? = null, var laterStatus: Int? = null) : BERTHttp {
        val requested = mutableListOf<String>()
        private val launch = 1_727_654_400L // the pool's first day, 2024-09-30

        override fun get(url: String, maxBytes: Int, readTimeoutMs: Int): String {
            requested += url
            if (failing) throw IOException("offline")
            val before = Regex("before_timestamp=(\\d+)").find(url)?.groupValues?.get(1)?.toLong()
            status?.let { throw BERTHttpStatusException(it, "gecko.test") }
            if (before != null) laterStatus?.let { throw BERTHttpStatusException(it, "gecko.test") }
            if (before != null && nowSeconds - before > 183 * 86_400L) throw BERTHttpStatusException(401, "gecko.test")
            val hours = url.contains("/ohlcv/hour")
            val step = when {
                url.contains("aggregate=4") -> 14_400L
                hours -> 3_600L
                else -> 86_400L
            }
            val newest = nowSeconds - nowSeconds % step
            val last = if (before == null) newest else before - before % step
            val count = if (hours) (if (step == 14_400L) 181 else 169) else if (before == null) 183 else 181
            val opens = generateSequence(last) { it - step }.takeWhile { it >= launch }.take(count).toList()
            val closes = opens.associateWith { 0.01 + 0.005 * (it - launch).toDouble() / (nowSeconds - launch) }
            return JSONObject()
                .put("data", JSONObject().put("attributes", JSONObject().put("ohlcv_list",
                    JSONArray().apply { opens.forEach { put(JSONArray().put(it).put(0.0).put(0.0).put(0.0).put(closes.getValue(it)).put(1.0)) } })))
                .put("meta", JSONObject().put("base", JSONObject().put("address", BERTQuoteRepository.BERT_MINT))).toString()
        }
    }

    private fun quote(pairAddress: String?) = QuoteState.Available(BERTQuote(
        0.0150, 3.0, 123_456_789.0, 2_345_678.0, 345_678.0, "fresh", now - 60_000, "dexscreener", "raydium",
        "https://dexscreener.com/solana/${pool.lowercase()}", pairAddress = pairAddress,
    ))

    private fun history() = listOf(300L to 0.0145, 120L to 0.0148, 40L to 0.0149, 1L to 0.0150).map { (m, p) -> BERTPriceSample(now - m * 60_000, p) }

    /** The app's clock, which ticks every 15 s in the app; a test moves it by hand. */
    private var appNow by mutableLongStateOf(now)

    /** The pool the quote names; a test can change it while the screen is up, as a fresh quote arriving would. */
    private var quotePool by mutableStateOf<String?>(pool)

    private fun market(http: BERTHttp, fontScale: Float = 1f, pairAddress: String? = pool, base: String = "https://gecko.test/api/v2") {
        quotePool = pairAddress
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalBERTRangeHistory provides BERTRangeHistoryRepository(compose.activity.applicationContext, http, base),
            ) {
                BERTTheme {
                    var position by remember { mutableStateOf(BERTPosition()) }
                    BERTApp(quote(quotePool), ActivityState.Unavailable, history(), position, appNow, false, false, {}, {}, { position = it })
                }
            }
        }
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
    }

    private fun pill(label: String) = compose.onNode(hasText(label) and hasClickAction())

    private fun choose(label: String) {
        pill(label).performScrollTo().performClick()
        pill(label).assertIsSelected()
    }

    /**
     * Idle, then a moment for a request that was wrongly started to reach the fake: the fetch runs on Dispatchers.IO,
     * which the test's idling does not wait for, so "nothing more was asked for" needs this to be able to fail.
     */
    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(250)
        compose.waitForIdle()
    }

    private fun waitForChart(summaryStart: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription(summaryStart, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun allSixOptionsFitAndWorkAtPhoneWidthAndLargerText() = checkSixOptions(listOf(1f, 1.3f), singleRow = true)

    /** At twice the text the pills wrap into two rows of three, but every one must still be on screen and tappable. */
    @Test fun allSixOptionsStillWorkAtDoubleText() = checkSixOptions(listOf(2f), singleRow = false)

    /** A 320dp phone cannot give six pills 48dp each in one row, so they take two rows of three rather than shrink. */
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun onA320dpPhoneThePillsTakeTwoRows() = checkSixOptions(listOf(1f), singleRow = false, screenDp = 320f)

    private fun checkSixOptions(fontScales: List<Float>, singleRow: Boolean, screenDp: Float = 360f) {
        for (fontScale in fontScales) {
            val http = FakeGecko()
            compose.activity.getSharedPreferences("bert_range_history", 0).edit().clear().commit() // the first pass saved every range
            market(http, fontScale)
            val density = compose.activity.resources.displayMetrics.density
            val tops = mutableSetOf<Int>()
            for (label in labels) {
                val node = pill(label).performScrollTo().assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertTrue("$label at ${fontScale}x starts inside the screen", bounds.left >= 0f)
                assertTrue("$label at ${fontScale}x ends inside the ${screenDp}dp screen, was ${bounds.right / density}", bounds.right / density <= screenDp)
                assertTrue("$label at ${fontScale}x is at least 48dp tall to touch", bounds.height / density >= 47.5f)
                assertTrue("$label at ${fontScale}x is at least 48dp wide to touch, was ${bounds.width / density}", bounds.width / density >= 47.9f)
                tops += bounds.top.toInt()
                // The label itself must be whole: no clipping, no wrapping.
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("$label is clipped at ${fontScale}x", layouts.single().didOverflowWidth)
                assertEquals("$label wraps at ${fontScale}x", 1, layouts.single().lineCount)
            }
            // At 1.3x text all six still share one row; wrapping is the safety net for 2x text and narrow phones.
            if (singleRow) assertEquals("all six options in one row at ${fontScale}x", 1, tops.size) else assertEquals("two rows at ${fontScale}x", 2, tops.size)
            capture("market-range-pills-${if (screenDp == 360f) "" else "${screenDp.toInt()}dp-"}${fontScale}x")
            for (label in labels) {
                choose(label)
                // A long range is only fetched when its pill is chosen; the 5-minute ranges never touch it.
                if (label in listOf("1W", "1M", "1Y")) waitForChart("prices over")
            }
            assertEquals(listOf(1, 1, 2).sum(), http.requested.size) // 1W 1, 1M 1, 1Y 2
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun eachLongRangeLabelsItsEdgesChangeAndGaps() {
        val http = FakeGecko()
        market(http)
        data class Case(val label: String, val start: String, val gap: String, val scope: String, val changeLine: String)
        for (case in listOf(
            Case("1W", "1 week ago", "2.5 hours", "the selected 1W window", "Change over the past week"),
            Case("1M", "1 month ago", "10 hours", "the selected 1M window", "Change over the past month"),
            Case("1Y", "1 year ago", "2.5 days", "the selected 1Y window", "Change over the past year"),
        )) {
            choose(case.label)
            waitForChart("prices over ${case.scope}")
            compose.onNodeWithContentDescription("Gaps over ${case.gap} are not connected.", substring = true).assertExists()
            compose.onNodeWithText(case.start).assertExists()
            compose.onNodeWithText("Now").assertExists()
            compose.onNode(says(case.changeLine, substring = true)).assertExists()
            // The fixture only climbs, so every range reads as a rise, in the green the quote's own changes use.
            compose.onNode(says("${case.changeLine} +", substring = true)).assertExists()
            // TalkBack hears the change once the range's prices arrive (or another long range is chosen), politely.
            assertEquals(LiveRegionMode.Polite, compose.onNode(says(case.changeLine, substring = true)).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        }
        // The 5-minute ranges keep their own wording and 30-minute rule.
        choose("24H")
        compose.onNodeWithContentDescription("4 prices over the selected 24H window. Low $0.0145, high $0.015. Gaps over 30 minutes are not connected.").assertExists()
        compose.onNodeWithText("24h ago").assertExists()
        compose.onNode(hasText("Change over the past 24 hours", substring = true)).assertExists()
        // Its prices move every minute, so its change line is not a live region: it would talk all the time.
        assertFalse(compose.onNode(hasText("Change over the past 24 hours", substring = true)).fetchSemanticsNode().config.contains(SemanticsProperties.LiveRegion))
    }

    /**
     * A week's dates are this phone's, and follow it when its time zone changes while Market is open (Android resets the
     * default zone, and the app's clock ticks). The fixture's week runs from 01:00 UTC on 3 September to 00:26 UTC on the
     * 10th: 21:00 on the 2nd to 20:26 on the 9th in New York, 11:00 on the 3rd to 10:26 on the 10th in Sydney.
     */
    @Test fun aWeeksDatesAreThePhonesAndFollowItsTimeZone() {
        val zone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            market(FakeGecko())
            choose("1W")
            waitForChart("prices over the selected 1W window")
            compose.onNode(hasText("169 prices · 2 Sep–9 Sep")).assertExists()
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Australia/Sydney"))
            compose.runOnIdle { appNow = now + 15_000L }
            compose.waitForIdle()
            compose.onNode(hasText("169 prices · 3 Sep–10 Sep")).assertExists()
        } finally {
            java.util.TimeZone.setDefault(zone)
        }
    }

    @Test fun aRefusedOrRateLimitedSecondPageStillShowsTheHalfYearItHas() {
        for (status in listOf(401, 429)) {
            compose.activity.getSharedPreferences("bert_range_history", 0).edit().clear().commit()
            val http = FakeGecko(laterStatus = status)
            market(http)
            choose("1Y")
            // The first page's half year, labelled from its real first price (a UTC date), not "1 year ago".
            waitForChart("prices since")
            val first = rangeDateFormat(BERTChartWindow.YEAR).format(java.util.Date((nowSeconds - nowSeconds % 86_400L - 181 * 86_400L) * 1_000))
            compose.onNodeWithContentDescription("183 prices since $first.", substring = true).assertExists()
            compose.onNode(says("Change since $first +", substring = true)).assertExists()
            compose.onNodeWithText(first).assertExists()
            compose.onNodeWithText("1 year ago").assertDoesNotExist()
            compose.onNodeWithText("Couldn’t load the older prices. Showing what arrived.").assertExists()
            compose.onAllNodesWithText("connection", substring = true).assertCountEquals(0)
            assertEquals(2, http.requested.size)
            if (status == 401) capture("market-range-1y-partial")
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun aFailureNamesItsCauseAndOnlyBlamesTheConnectionWhenItWasTheConnection() {
        for ((http, message) in listOf(
            FakeGecko(failing = true) to "Couldn’t load a week of prices. Check your connection.",
            FakeGecko(status = 429) to "Couldn’t load a week of prices. GeckoTerminal is busy, so try again in a minute.",
            FakeGecko(status = 401) to "Couldn’t load a week of prices from GeckoTerminal. Try again later.",
            // A GeckoTerminal too slow to answer, once connected, is not the person's connection either...
            BERTHttp { _, _, _ -> throw BERTHttpReadTimeoutException("gecko.test", java.net.SocketTimeoutException("Read timed out")) } to
                "Couldn’t load a week of prices. GeckoTerminal is busy, so try again in a minute.",
            // ...but failing to connect in time usually is (weak signal, a captive portal), though it is a timeout too.
            BERTHttp { _, _, _ -> throw java.net.SocketTimeoutException("connect timed out") } to
                "Couldn’t load a week of prices. Check your connection.",
        )) {
            market(http)
            choose("1W")
            compose.waitUntil(10_000) { compose.onAllNodes(says(message)).fetchSemanticsNodes().isNotEmpty() }
            // Said by TalkBack when it appears, without the person having to find it.
            assertEquals(LiveRegionMode.Polite, compose.onNode(says(message)).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
            compose.activityRule.scenario.recreate()
        }
    }

    /** A long range's status line, which TalkBack reads from its description; other lines by their words. */
    private fun says(words: String, substring: Boolean = false) = hasText(words, substring) or hasContentDescription(words, substring)

    /** The range's live status node, the only live region on the screen. */
    private fun liveStatus() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).fetchSemanticsNode()

    /** What TalkBack reads for a node: its description, or its words when it has none. */
    private fun androidx.compose.ui.semantics.SemanticsNode.said() =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()

    @Test fun loadingAndStaleNotesAreAnnouncedPolitely() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val gecko = FakeGecko()
        market(BERTHttp { url, max, timeout -> gate.await(); gecko.get(url, max, timeout) })
        choose("1W")
        compose.onNode(says("Loading prices…")).assertExists()
        assertEquals(LiveRegionMode.Polite, compose.onNode(says("Loading prices…")).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        gate.countDown()
        waitForChart("prices over the selected 1W window")
        // A refresh that fails over saved prices says so in the same polite way: the status line above the chart ends
        // with the note under it. The note itself is not live, so TalkBack doesn't say it twice.
        gecko.failing = true
        compose.runOnIdle { appNow = now + 2 * 3_600_000L }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Couldn’t refresh. Showing the last saved prices.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(LiveRegionMode.Polite, liveStatus().config[SemanticsProperties.LiveRegion])
        assertTrue(liveStatus().said(), liveStatus().said().matches(Regex("Change over the past week \\+[0-9.]+% Couldn’t refresh\\. Showing the last saved prices\\.")))
        val note = compose.onNodeWithText("Couldn’t refresh. Showing the last saved prices.").fetchSemanticsNode().config
        assertFalse(note.contains(SemanticsProperties.LiveRegion))
        // The status line says it for TalkBack, so the note itself is skipped when swiping through.
        assertTrue(note.contains(SemanticsProperties.HideFromAccessibility))
    }

    /**
     * TalkBack reads out a live region when its own words change, but a live node that newly appears is reported as a
     * change to the panel around it, which is not live, so it usually goes unheard. So a long range's status is one node
     * that stays where it is while its words change, from why it can't be asked for, through a failure, loading and the
     * change once the prices arrive, to a failed refresh; each used to be a new node from its own branch.
     */
    @Test fun aLongRangesStatusIsOneLiveNodeWhoseWordsChange() {
        val gecko = FakeGecko(failing = true)
        var gate = java.util.concurrent.CountDownLatch(0)
        market(BERTHttp { url, max, timeout -> gate.await(); gecko.get(url, max, timeout) }, pairAddress = null)
        choose("1W")
        val status = liveStatus()
        assertEquals("A week of prices loads once a fresh quote arrives.", status.said())
        fun sees(words: String) = compose.waitUntil(10_000) { liveStatus().said() == words }
        // A fresh quote names the pool, the request goes out, and fails.
        compose.runOnIdle { quotePool = pool }
        sees("Couldn’t load a week of prices. Check your connection.")
        assertEquals(status.id, liveStatus().id)
        gecko.failing = false
        gate = java.util.concurrent.CountDownLatch(1)
        compose.onNodeWithText("Try again").performClick()
        sees("Loading prices…")
        assertEquals(status.id, liveStatus().id)
        gate.countDown()
        waitForChart("prices over the selected 1W window")
        val change = liveStatus().said()
        assertTrue(change, change.startsWith("Change over the past week +"))
        assertEquals(status.id, liveStatus().id)
        // The clock ticking on its own leaves the words, and so TalkBack, alone.
        compose.runOnIdle { appNow = now + 15_000L }
        compose.waitForIdle()
        assertEquals(change, liveStatus().said())
        gecko.failing = true
        compose.runOnIdle { appNow = now + 2 * 3_600_000L }
        compose.waitUntil(10_000) { liveStatus().said().endsWith("Couldn’t refresh. Showing the last saved prices.") }
        assertEquals(status.id, liveStatus().id)
    }

    /**
     * What TalkBack is actually sent when a chosen week's prices arrive: a content change from the live status node itself,
     * which it reads out. Before, the change line was a live node that newly appeared, sent only as a change to the panel
     * around it, which is not live.
     */
    @Test fun pricesArrivingReachTalkBackFromTheLiveNode() {
        val accessibility = compose.activity.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        shadowOf(accessibility).setEnabled(true)
        shadowOf(accessibility).setTouchExplorationEnabled(true)
        shadowOf(accessibility).setEnabledAccessibilityServiceList(listOf(android.accessibilityservice.AccessibilityServiceInfo().apply {
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
        }))
        val gate = java.util.concurrent.CountDownLatch(1)
        val gecko = FakeGecko()
        market(BERTHttp { url, max, timeout -> gate.await(); gecko.get(url, max, timeout) })
        // Every event the screen sends on its way up to the window, as TalkBack would receive it.
        val sent = java.util.Collections.synchronizedList(mutableListOf<AccessibilityEvent>())
        compose.runOnIdle {
            compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
                override fun onRequestSendAccessibilityEvent(host: android.view.ViewGroup, child: android.view.View, event: AccessibilityEvent): Boolean {
                    sent += AccessibilityEvent(event)
                    return super.onRequestSendAccessibilityEvent(host, child, event)
                }
            }
        }
        choose("1W")
        compose.onNode(says("Loading prices…")).assertExists()
        val status = liveStatus().id
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        sent.clear()
        gate.countDown()
        waitForChart("prices over the selected 1W window")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // The node an event is about: the virtual view id, which Compose makes the semantics id, is the source's high half.
        fun AccessibilityEvent.sourceId() = (android.view.accessibility.AccessibilityRecord::class.java.getMethod("getSourceNodeId").invoke(this) as Long).ushr(32).toInt()
        val changes = AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT or AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
        val events = sent.toList().map { "type=${it.eventType} source=${it.sourceId()} changes=${it.contentChangeTypes}" }
        assertTrue("status $status, sent $events", sent.toList().any {
            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && it.sourceId() == status && it.contentChangeTypes and changes != 0
        })
    }

    @Test fun stayingOnARangeRefreshesItOnceItGoesStale() {
        val http = FakeGecko()
        market(http)
        choose("1W")
        waitForChart("prices over the selected 1W window")
        compose.waitForIdle()
        assertEquals(1, http.requested.size)
        // Still fresh 59 minutes on: the clock ticks, nothing is asked for.
        compose.runOnIdle { appNow = now + 59 * 60_000L }
        settle()
        assertEquals(1, http.requested.size)
        // An hour on, the week is stale, and the next tick asks again without the range being chosen again.
        compose.runOnIdle { appNow = now + 61 * 60_000L }
        compose.waitUntil(10_000) { compose.waitForIdle(); http.requested.size == 2 }
        settle()
        // Fresh again: later ticks within the hour ask for nothing more.
        compose.runOnIdle { appNow = now + 90 * 60_000L }
        settle()
        assertEquals(2, http.requested.size)
    }

    @Test fun aFailedRefreshOverSavedPricesWaitsBeforeAskingAgain() {
        val http = FakeGecko()
        market(http)
        choose("1W")
        waitForChart("prices over the selected 1W window")
        http.failing = true
        compose.runOnIdle { appNow = now + 2 * 3_600_000L }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Couldn’t refresh. Showing the last saved prices.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, http.requested.size)
        // The next ticks do not hammer a failing server...
        compose.runOnIdle { appNow = now + 2 * 3_600_000L + 15_000L }
        settle()
        compose.runOnIdle { appNow = now + 2 * 3_600_000L + 4 * 60_000L }
        settle()
        assertEquals(2, http.requested.size)
        // ...but five minutes on it is asked again, and a success clears the note.
        http.failing = false
        compose.runOnIdle { appNow = now + 2 * 3_600_000L + 5 * 60_000L }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Couldn’t refresh. Showing the last saved prices.").fetchSemanticsNodes().isEmpty() }
        assertEquals(3, http.requested.size)
    }

    @Test fun savedPricesShowWithoutALoadingStep() {
        val repo = BERTRangeHistoryRepository(compose.activity.applicationContext, FakeGecko(), "https://gecko.test/api/v2")
        kotlinx.coroutines.runBlocking { repo.refresh(BERTChartWindow.WEEK, pool, now) }
        val http = FakeGecko()
        market(http)
        choose("1W")
        // The saved week is read off the main thread (while 24H shows); at no point on the way to it does "Loading" show.
        var sawLoading = false
        compose.waitUntil(5_000) {
            if (compose.onAllNodes(says("Loading prices…")).fetchSemanticsNodes().isNotEmpty()) sawLoading = true
            compose.onAllNodes(hasContentDescription("prices over the selected 1W window", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("a saved range flashed \"Loading\"", sawLoading)
        assertTrue(http.requested.isEmpty())
    }

    @Test fun aFailedRangeSaysSoInsideTheChartAndTheOthersKeepWorking() {
        val http = FakeGecko(failing = true)
        market(http)
        choose("1W")
        compose.waitUntil(10_000) { compose.onAllNodes(says("Couldn’t load a week of prices. Check your connection.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Try again").assertExists()
        choose("24H")
        compose.onNodeWithContentDescription("prices over the selected 24H window", substring = true).assertExists()
        http.failing = false
        choose("1W")
        compose.waitUntil(10_000) { compose.onAllNodes(says("Couldn’t load a week of prices. Check your connection.")).fetchSemanticsNodes().isEmpty() }
        waitForChart("prices over the selected 1W window")
    }

    @Test fun aLongRangeThatCannotBeAskedForSaysWhyAndOffersNoRetry() {
        val http = FakeGecko()
        // A quote without its pool (an old saved copy) can't name the pool to ask about; the next good quote will.
        market(http, pairAddress = null)
        choose("1W")
        compose.onNode(says("A week of prices loads once a fresh quote arrives.")).assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNode(says("Loading prices…")).assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        // A build without GeckoTerminal's address (debug's emulator fixtures) can't ask for any long range.
        market(http, base = "")
        choose("1M")
        compose.onNode(says("Longer ranges aren’t available in this build.")).assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test fun aRangeIsAskedForOncePerChoiceAndReusedWhileFresh() {
        val http = FakeGecko()
        market(http)
        assertTrue("nothing is fetched until a long range is chosen", http.requested.isEmpty())
        choose("1W")
        waitForChart("prices over the selected 1W window")
        compose.waitForIdle()
        assertEquals(1, http.requested.size)
        choose("24H")
        choose("1W")
        waitForChart("prices over the selected 1W window")
        compose.waitForIdle()
        assertEquals("the saved week is fresh, so choosing it again asks for nothing", 1, http.requested.size)
    }

    @Test fun switchingRangesMidLoadKeepsTheAnswerAndNeverAsksTwice() {
        val gecko = FakeGecko()
        val gate = java.util.concurrent.CountDownLatch(1)
        val asked = java.util.concurrent.atomic.AtomicInteger()
        market(BERTHttp { url, max, timeout -> asked.incrementAndGet(); gate.await(); gecko.get(url, max, timeout) })
        // The week is asked for, and the person goes to 24H and back while it is still on its way: one request, waited for.
        choose("1W")
        compose.waitUntil(5_000) { asked.get() == 1 }
        choose("24H")
        choose("1W")
        compose.onNode(says("Loading prices…")).assertExists()
        settle()
        assertEquals(1, asked.get())
        // They leave again before it lands. The answer is kept, so coming back shows it at once and asks for nothing.
        choose("24H")
        gate.countDown()
        compose.waitUntil(5_000) { compose.waitForIdle(); gecko.requested.size == 1 }
        settle()
        choose("1W")
        waitForChart("prices over the selected 1W window")
        settle()
        assertEquals(1, asked.get())
    }

    /**
     * A whole week that has gone stale keeps its week while its refresh is on the way. It reached a full week back when it
     * was fetched, and the part now older than the window is cut off, so hours later it still starts at the window's own
     * edge: never "Change since" a date, as a copy whose older prices were refused does.
     */
    @Test fun aStaleWholeWeekStillReadsAsAWeekWhileItRefreshes() {
        val gecko = FakeGecko()
        var gate = java.util.concurrent.CountDownLatch(0)
        market(BERTHttp { url, max, timeout -> gate.await(); gecko.get(url, max, timeout) })
        choose("1W")
        waitForChart("prices over the selected 1W window")
        choose("24H")
        gate = java.util.concurrent.CountDownLatch(1)
        for (hours in listOf(4L, 30L)) {
            compose.runOnIdle { appNow = now + hours * 3_600_000L }
            choose("1W")
            settle()
            compose.onNodeWithText("1 week ago").assertExists()
            compose.onNode(says("Change over the past week", substring = true)).assertExists()
            compose.onAllNodes(says("Change since", substring = true)).assertCountEquals(0)
            choose("24H")
        }
        gate.countDown()
        compose.waitUntil(10_000) { gecko.requested.size == 2 }
        choose("1W")
        waitForChart("prices over the selected 1W window")
        compose.onNodeWithText("1 week ago").assertExists()
    }

    @Test fun savedPricesShowAtOnceAndAStaleRangeRefreshesInTheBackground() {
        val seed = FakeGecko()
        val repo = BERTRangeHistoryRepository(compose.activity.applicationContext, seed, "https://gecko.test/api/v2")
        kotlinx.coroutines.runBlocking { repo.refresh(BERTChartWindow.WEEK, pool, now - 2 * 3_600_000L) } // two hours old: stale for a week
        val http = FakeGecko(failing = true)
        market(http)
        choose("1W")
        // The saved week is on screen straight away, and the failed refresh only adds a note.
        waitForChart("prices over the selected 1W window")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Couldn’t refresh. Showing the last saved prices.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, http.requested.size)
        compose.onNodeWithText("1 week ago").assertExists()
    }

    @Test fun coinMarketCapIsTheMainMarketLinkAndDexScreenerStaysSecondary() {
        market(FakeGecko())
        compose.onNodeWithText("View on CoinMarketCap ↗").performScrollTo().performClick()
        val cmc = requireNotNull(shadowOf(compose.activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, cmc.action)
        assertEquals("https://coinmarketcap.com/currencies/bertram-the-pomeranian/", cmc.data.toString())
        compose.onNodeWithText("Chart on DEX Screener ↗").performScrollTo().performClick()
        assertEquals("https://dexscreener.com/solana/${pool.lowercase()}", requireNotNull(shadowOf(compose.activity).nextStartedActivity).data.toString())
        compose.onAllNodesWithText("Open DEX Screener ↗").assertCountEquals(0)
        // Prices still come from DEX Screener and GeckoTerminal, and the screen keeps saying so.
        compose.onNodeWithText("Market data from DEX Screener.", substring = true).performScrollTo().assertExists()
    }

    @Test fun theCoinMarketCapLinkIsFixedHttpsAndOnItsOwnHost() {
        val uri = URI(BERTLink.COINMARKETCAP.url)
        assertEquals("https", uri.scheme)
        assertEquals("coinmarketcap.com", uri.host)
        assertEquals("/currencies/bertram-the-pomeranian/", uri.path)
        assertNull(uri.query); assertNull(uri.fragment); assertNull(uri.userInfo); assertEquals(-1, uri.port)
        assertTrue(BERTLink.entries.all { URI(it.url).scheme == "https" })
    }

    /** Review evidence: the Market screen with 1W, 1M and 1Y chosen (and a week loading), at 360dp, beside the other host-ui captures. */
    @Test fun marketScreenshotWithOneMonthSelected() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val gecko = FakeGecko()
        market(BERTHttp { url, max, timeout -> gate.await(); gecko.get(url, max, timeout) })
        choose("1W")
        compose.onNode(says("Loading prices…")).assertExists()
        compose.onNodeWithText("PRICE HISTORY").performScrollTo()
        capture("market-range-1w-loading")
        gate.countDown()
        waitForChart("prices over the selected 1W window")
        compose.onNodeWithText("PRICE HISTORY").performScrollTo()
        capture("market-range-1w")
        choose("1M")
        waitForChart("prices over the selected 1M window")
        compose.onNodeWithText("PRICE HISTORY").performScrollTo()
        capture("market-range-1m")
        compose.onNodeWithText("4-hour closes from GeckoTerminal.").performScrollTo()
        capture("market-range-1m-footer")
        choose("1Y")
        waitForChart("prices over the selected 1Y window")
        compose.onNodeWithText("PRICE HISTORY").performScrollTo()
        capture("market-range-1y")
        compose.onNodeWithText("View on CoinMarketCap ↗").performScrollTo()
        capture("market-links")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val target = File("build/outputs/host-ui/$name.png")
        requireNotNull(target.parentFile).mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
