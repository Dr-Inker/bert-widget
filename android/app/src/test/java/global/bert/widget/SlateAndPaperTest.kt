package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** 0.15 slate and paper: what each says in every state, and how they sit on the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlateAndPaperTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Europe/London")
    private fun at(hour: Int, minute: Int = 9, day: Int = 9) = ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private fun priced(now: Long, change: Double? = 1.63, observed: Long = now - 30_000, delayed: Boolean = false) = QuoteState.Available(
        BERTQuote(0.0154, change, null, null, null, "fresh", observed, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"), delayed)

    // ---- What the slate says ----

    @Test fun theSlateChalksThePriceInDollarsWithItsChangeAndTime() {
        val now = at(9)
        val face = slateFace(priced(now), now, use24Hour = false, locale = Locale.UK)
        assertEquals("\$0.0154", face.price)
        assertEquals("+1.63%", face.change)
        assertFalse(face.falling)
        assertFalse(face.delayed)
        // The phone's own clock words (formatObservedAt, in the phone's zone), in chalk's lower case.
        assertEquals("as of " + java.text.SimpleDateFormat("h:mm a", Locale.UK).format(java.util.Date(now - 30_000)).lowercase(Locale.UK), face.time)
        assertTrue(face.spoken, face.spoken.startsWith("BERT price \$0.0154, +1.63% over the past 24 hours. As of "))
        // Down is red chalk; with no change known there is no figure and no period to go with it.
        assertTrue(slateFace(priced(now, change = -7.9), now, false, Locale.UK).falling)
        val unknown = slateFace(priced(now, change = null), now, false, Locale.UK)
        assertNull(unknown.change)
        assertFalse(unknown.spoken, unknown.spoken.contains("24 hours"))
    }

    @Test fun aLatePriceSaysSoOnTheSlate() {
        val now = at(9)
        for (late in listOf(priced(now, observed = now - 42 * 60_000), priced(now, delayed = true))) {
            val face = slateFace(late, now, false, Locale.UK)
            assertTrue(face.delayed)
            assertTrue(face.time!!, face.time!!.startsWith("delayed · updated"))
            assertTrue(face.spoken, face.spoken.contains("Delayed · Updated"))
        }
        assertEquals("delayed · updated 42m ago", slateFace(priced(now, observed = now - 42 * 60_000), now, false, Locale.UK).time)
    }

    @Test fun withoutAPriceTheSlateSaysWhy() {
        val now = at(9)
        val gone = slateFace(QuoteState.Unavailable("No connection"), now, false)
        assertNull(gone.price); assertEquals("price unavailable", gone.status); assertEquals("check your connection.", gone.time)
        assertEquals("Price unavailable. Check your connection.", gone.spoken)
        val coming = slateFace(QuoteState.Loading, now, false)
        assertNull(coming.price); assertEquals("checking the price…", coming.status); assertEquals("Checking the price…", coming.spoken)
    }

    // ---- What the note says ----

    private val text = "woofmornin. the ladybug holds\nthe screen door."
    private fun feed(now: Long, dispatch: String? = text) = ActivityState.Available(BERTActivity(now - 60_000, dispatch, "content", null))

    @Test fun theNoteIsTodaysNoteWithWhenItWasFirstSeen() {
        val now = at(10)
        val face = noteFace(feed(now), JournalEntry(text, "content", at(8)), fresh = 0, isNew = false, boardHasNotes = true,
            slateSaysOffline = false, now = now, zone = zone, locale = Locale.UK)
        assertEquals("ON\u00A0THE\u00A0BOARD · THIS\u00A0MORNING", face.eyebrow)
        assertEquals("its lines run on as one", "woofmornin. the ladybug holds the screen door.", face.body)
        assertEquals("Read the board", face.link)
        assertEquals("On the board, this morning: woofmornin. the ladybug holds the screen door.", face.spoken)
        // New since the last visit, and earlier notes that came in too.
        val news = noteFace(feed(now), JournalEntry(text, "content", at(8)), fresh = 2, isNew = true, boardHasNotes = true,
            slateSaysOffline = false, now = now, zone = zone, locale = Locale.UK)
        assertEquals("NEW\u00A0ON\u00A0THE\u00A0BOARD · THIS\u00A0MORNING", news.eyebrow)
        assertEquals("2 new notes on the board", news.link)
        assertTrue(news.spoken, news.spoken.startsWith("New on the board, this morning:") && news.spoken.endsWith(". 2 new notes on the board"))
        assertEquals("1 new note on the board", noteFace(feed(now), null, 1, false, true, false, now, zone, Locale.UK).link)
        assertEquals("never more than the board shows", "6 new notes on the board", noteFace(feed(now), null, 9, false, true, false, now, zone, Locale.UK).link)
    }

    @Test fun offlineIsNotNoPost() {
        val now = at(10)
        val offline = noteFace(ActivityState.Unavailable, null, 0, false, boardHasNotes = false, slateSaysOffline = false, now = now)
        assertEquals("can’t reach the board right now. check your connection.", offline.body)
        assertNull("nothing saved to read: no link, no tap", offline.link)
        // The slate above already says to check the connection.
        assertEquals("can’t reach the board right now.", noteFace(ActivityState.Unavailable, null, 0, false, false, true, now).body)
        val empty = noteFace(ActivityState.Empty, null, 0, false, boardHasNotes = false, slateSaysOffline = false, now = now)
        assertEquals("no post today. the postman’s on strike.", empty.body)
        assertNull(empty.link)
        assertEquals(NO_POST, noteFace(feed(now, dispatch = null), null, 0, false, false, false, now).body)
        assertEquals("checking the board…", noteFace(ActivityState.Loading, null, 0, false, false, false, now).body)
        // With notes saved on the board, every one of them still leads there.
        for (state in listOf(ActivityState.Unavailable, ActivityState.Empty, ActivityState.Loading, feed(now, dispatch = null)))
            assertEquals("$state", "Read the board", noteFace(state, null, 0, false, boardHasNotes = true, slateSaysOffline = false, now = now).link)
    }

    @Test fun whenANoteWasSeenInWords() {
        val now = at(22)
        fun said(seen: Long, n: Long = now) = boardWhen(seen, n, zone, Locale.UK)
        assertEquals("THIS MORNING", said(at(6)))
        assertEquals("THIS AFTERNOON", said(at(13)))
        assertEquals("THIS EVENING", said(at(18)))
        assertEquals("TONIGHT", said(at(21, 30)))
        assertEquals("LAST NIGHT", said(at(2), at(9)))
        assertEquals("TONIGHT", said(at(1), at(3)))
        assertEquals("YESTERDAY", said(at(20, day = 8)))
        assertEquals("FRIDAY", said(at(10, day = 4))) // 4 September 2026 is a Friday
        assertEquals("1 SEP", boardWhen(at(10, day = 1), at(10, day = 9), zone, Locale.US))
        assertNull("ahead of the phone's clock", said(at(23, day = 10)))
    }

    // ---- On the page ----

    // Home is set once per test; later calls change what it shows (a compose rule takes one setContent).
    private var shown by mutableStateOf<Triple<Long, QuoteState, ActivityState>?>(null)
    private var scale by mutableFloatStateOf(1f)

    private fun home(hour: Int, fontScale: Float = 1f, activity: (Long) -> ActivityState = { feed(it, "woofmornin. the ladybug holds the screen door like the wind can wait its turn.") },
                     quote: (Long) -> QuoteState = { priced(it) }) {
        val now = ZonedDateTime.of(2026, 9, 9, hour, 9, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        BERTHomeVisits(compose.activity).meetBertDismissed = true
        val first = shown == null
        shown = Triple(now, quote(now), activity(now))
        scale = fontScale
        if (first) compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                val (at, q, a) = shown!!
                BERTTheme { BERTApp(q, a, emptyList(), BERTPosition(), at, false, false, {}, {}, {}) }
            }
        }
        compose.waitForIdle()
    }

    /** [tag]'s bounds in device px, unclipped (large text runs the note below the fold). */
    private fun px(tag: String, unmerged: Boolean = false): Rect = compose.onNodeWithTag(tag, useUnmergedTree = unmerged).getUnclippedBoundsInRoot().let {
        val d = compose.activity.resources.displayMetrics.density
        Rect(it.left.value * d, it.top.value * d, it.right.value * d, it.bottom.value * d)
    }

    /** The slate and the note on the scene's art-pixel grid: their edges and sizes in whole art px from the scene's corner. */
    private fun assertOnTheGrid() {
        val scene = px("berts-place")
        val k = placeFrame(scene.width, compose.activity.resources.displayMetrics.heightPixels.toFloat()).k
        for (tag in listOf("home-price", "board-row")) {
            val r = px(tag)
            for ((what, v) in listOf("left" to r.left - scene.left, "top" to r.top - scene.top, "width" to r.width, "height" to r.height))
                assertEquals("$tag $what ${v}px is not whole art px of $k", 0f, Math.floorMod(Math.round(v), k).toFloat(), 0f)
        }
        assertTrue("k is $k", k >= 2)
    }

    @Test fun theyStandOnTheScenesPixelGrid() { home(10); assertOnTheGrid() }
    /** 2.625 device px per dp, 3 per art px: page margins set in dp would land between the scene's pixels here. */
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun theyStandOnTheGridAt420dpi() { home(10); assertOnTheGrid() }
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun theyStandOnTheGridOnANarrowPhone() { home(10); assertOnTheGrid() }

    /** A pixel of [tag]'s own material, [inX] art px in from its left edge and halfway down (in the padding, clear of words). */
    private fun material(bitmap: Bitmap, tag: String, inX: Int): Int {
        val r = px(tag)
        val k = placeFrame(px("berts-place").width, compose.activity.resources.displayMetrics.heightPixels.toFloat()).k
        return bitmap.getPixel(r.left.toInt() + inX * k + k / 2, (r.top + r.height / 2).toInt())
    }
    private fun capture(): Bitmap {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    @Test fun theyAreLitForTheTimeOfDaySoTheNoteNeverGlaresAtNight() {
        for ((hour, time) in listOf(10 to PlaceTime.DAY, 19 to PlaceTime.DUSK, 23 to PlaceTime.NIGHT)) {
            home(hour)
            val shot = capture()
            // The note is the bubble's own paper in that light; the slate is its own slate.
            assertEquals("$time paper", BubbleTone.of(time).fill.toArgb(), material(shot, "board-row", 3))
            assertEquals("$time slate", SlateTone.of(time).slate.toArgb(), material(shot, "home-price", 1 + 5 + 3))
        }
    }

    @Test fun theyAreTheSameMaterialInBothColourSchemes() {
        val seen = BERTScheme.entries.map { scheme ->
            BERTColors.scheme = scheme
            try {
                home(10)
                val shot = capture()
                listOf(material(shot, "board-row", 3), material(shot, "home-price", 1 + 5 + 3), material(shot, "home-price", 3))
            } finally { BERTColors.scheme = BERTScheme.DEFAULT }
        }
        assertEquals(seen.first(), seen.last())
    }

    private fun layout(tag: String): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(out) }
        return out.single()
    }

    /** Every word on each object is inside it, clear of the frame, and the objects keep clear of each other and the links. */
    private fun assertNothingClipsOrOverlaps(where: String) {
        val slate = px("home-price"); val note = px("board-row")
        val k = placeFrame(px("berts-place").width, compose.activity.resources.displayMetrics.heightPixels.toFloat()).k
        val frame = 6f * k
        for (tag in listOf("slate-price", "slate-status", "slate-change", "slate-period", "slate-time")) {
            if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) continue
            val r = px(tag, unmerged = true)
            assertTrue("$where: $tag $r outside the slate's face $slate", r.left >= slate.left + frame && r.right <= slate.right - frame &&
                r.top >= slate.top + frame && r.bottom <= slate.bottom - frame)
            val l = layout(tag)
            // Each line's own extent (an unbounded one-line Text reports an endless paragraph, so didOverflowWidth cannot say).
            for (n in 0 until l.lineCount) assertTrue("$where: $tag line $n is cut off (${l.getLineRight(n)} of ${l.size})",
                l.getLineLeft(n) >= -0.5f && l.getLineRight(n) <= l.size.width + 0.5f && l.getLineBottom(n) <= l.size.height + 0.5f)
        }
        for (tag in listOf("note-body", "note-link")) {
            if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) continue
            val r = px(tag, unmerged = true)
            assertTrue("$where: $tag $r outside the note $note", r.left >= note.left + k && r.right <= note.right - k && r.top >= note.top && r.bottom <= note.bottom - 3 * k)
        }
        assertTrue("$where: the note ${note.top} runs into the slate ${slate.bottom}", note.top >= slate.bottom)
        val links = compose.onNodeWithText("@bertcoincto", substring = true).getUnclippedBoundsInRoot().top.value * compose.activity.resources.displayMetrics.density
        assertTrue("$where: the links $links run into the note ${note.bottom}", links >= note.bottom)
        assertTrue("$where: the slate starts under Bert's bubble", slate.top >= px("place-line").top)
    }

    @Test fun atNormalTextTheChangeSitsBesideThePrice() {
        home(10)
        assertNothingClipsOrOverlaps("1.0")
        val price = px("slate-price", true); val change = px("slate-change", true)
        assertTrue("the change is on the price's line", change.left >= price.right && change.top < price.bottom)
        assertEquals(1, layout("slate-change").lineCount)
    }

    @Test fun atLargeTextTheyGrowAndTheChangeDropsWhole() {
        for (scale in listOf(1.3f, 2f)) {
            home(10, fontScale = scale)
            assertNothingClipsOrOverlaps("$scale")
            assertEquals("the change is one piece at $scale", 1, layout("slate-change").lineCount)
            compose.onNodeWithTag("home-price").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        }
        // At 2.0 the top line cannot hold all three: the change goes under the price, whole, and its period with it.
        val price = px("slate-price", true); val change = px("slate-change", true); val period = px("slate-period", true)
        assertTrue("the change $change is under the price $price", change.top >= price.bottom - 1f)
        assertTrue("the period $period is beside the change $change", period.left >= change.right && period.top < change.bottom)
        assertEquals("the note shows three lines at most", true, layout("note-body").lineCount <= 3)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun onANarrowPhoneAtLargeTextNothingClips() {
        for (scale in listOf(1f, 1.3f, 2f)) { home(10, fontScale = scale); assertNothingClipsOrOverlaps("320dp $scale") }
        home(10, fontScale = 2f, quote = { QuoteState.Unavailable("No connection") }, activity = { ActivityState.Unavailable })
        assertNothingClipsOrOverlaps("320dp 2.0 offline")
    }

    @Test @Config(qualifiers = "w430dp-h932dp-xxhdpi") fun onAWidePhoneNothingClips() {
        home(23); assertNothingClipsOrOverlaps("430dp night")
    }

    @Test fun eachTapsWhereItsRowDid() {
        home(10)
        compose.onNodeWithTag("home-price").assertHasClickAction().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        assertEquals("Open Market", compose.onNodeWithTag("home-price").fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag("board-row").assertHasClickAction().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        assertEquals("Read the noticeboard", compose.onNodeWithTag("board-row").fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag("board-row").performClick()
        compose.onNodeWithTag("noticeboard").assertExists()
    }

    @Test fun theSlateOpensMarket() {
        home(10)
        compose.onNodeWithTag("home-price").performClick()
        compose.onNodeWithText("BERT / USD").assertIsDisplayed()
    }
}
