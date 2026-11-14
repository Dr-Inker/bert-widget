package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The garden as a place to play with Bert (0.15, the owner: "more a Bert tapping area"): a pat and its heart, his word
 * about each thing in the picture, the board's fresh note, and nothing that navigates or glints. The scene's cues are
 * read through semantics flags looked up by name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAffordanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun at(hour: Int): Long = ZonedDateTime.now(ZoneId.systemDefault()).withHour(hour).withMinute(10).toInstant().toEpochMilli()
    private val today: LocalDate get() = LocalDate.now(ZoneId.systemDefault())

    private var notes by mutableIntStateOf(0)

    private fun place(hour: Int = 10) {
        val now = at(hour)
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(now, "content", newNotes = notes)
                }
            }
        }
    }

    /** Someone who has been coming round for a while: past the first days, and has patted him. */
    private fun regular() = PlaceDiscovery(compose.activity).apply {
        for (d in FIRST_DAYS downTo 1) record(today.minusDays(d.toLong()))
        patted()
    }

    private fun flag(tag: String, name: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.firstOrNull { it.key.name == name }?.value as? Boolean ?: false

    private fun said(): String = compose.onNodeWithTag("place-line").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

    private fun snapshot(): Bitmap {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** How many pixels differ between [a] and [b] inside the node [tag]'s bounds. */
    private fun changedIn(tag: String, a: Bitmap, b: Bitmap): Int {
        val r = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        var n = 0
        for (y in r.top.toInt() until r.bottom.toInt()) for (x in r.left.toInt() until r.right.toInt()) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    /**
     * The chalkboard's slate face (place art 150,252 30x23) in window pixels, worked out from the whole scene's canvas the
     * way BertsPlace frames it (placeFrame), with where the frame-corner star (art 181,248, reach 4) begins.
     */
    private class Face(val left: Int, val top: Int, val right: Int, val bottom: Int, val starLeft: Int, val starBottom: Int)
    private fun slateFace(): Face {
        val r = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInWindow
        val res = compose.activity.resources
        val frame = placeFrame(r.right - r.left, res.configuration.screenHeightDp * res.displayMetrics.density)
        assertEquals("the canvas is the scene's height", frame.heightPx.toFloat(), r.bottom - r.top, 0.5f)
        fun x(art: Int) = (r.left + frame.left + (art - frame.x0) * frame.k).toInt()
        fun y(art: Int) = (r.top + art * frame.k).toInt()
        return Face(x(150), y(252), x(180), y(275), starLeft = x(177), starBottom = y(253))
    }

    private fun save(bitmap: Bitmap, name: String) =
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test fun theNoticeboardShowsANoteOnlyWhileThereIsNews() {
        regular()
        place()
        compose.waitForIdle()
        assertFalse("no news, no note", flag("place-board", "PlaceNews"))
        val quiet = snapshot()
        compose.runOnIdle { notes = 2 }
        compose.waitForIdle()
        assertTrue("news pins a note on the board", flag("place-board", "PlaceNews"))
        val news = snapshot()
        save(news, "afford-board-news")
        // Drawn on the board itself, not just flagged: counted over the note's own place (art 372..383 x 259..271, the
        // second note behind included).
        val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInWindow
        val res = compose.activity.resources
        val f = placeFrame(scene.right - scene.left, res.configuration.screenHeightDp * res.displayMetrics.density)
        fun x(art: Int) = (scene.left + f.left + (art - f.x0) * f.k).toInt()
        fun y(art: Int) = (scene.top + art * f.k).toInt()
        var note = 0
        for (yy in y(259) until y(272)) for (xx in x(372) until x(384)) if (quiet.getPixel(xx, yy) != news.getPixel(xx, yy)) note++
        assertTrue("the note is in the picture: $note pixels changed on it", note > 30)
        compose.runOnIdle { notes = 0 }
        compose.waitForIdle()
        assertFalse("read: the note comes down", flag("place-board", "PlaceNews"))
        assertEquals(0, changedIn("place-board", quiet, snapshot()))
    }

    /**
     * 0.15: nothing in the garden opens anything, so nothing twinkles to say it does. A newcomer's garden (where 0.14
     * glinted longest), Bert asleep and still: the chalkboard, the gate and the noticeboard do not change at all over
     * the first few seconds.
     */
    @Test fun theThingsDoNotGlint() {
        compose.mainClock.autoAdvance = false
        place(13)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val first = snapshot()
        for (t in 1..12) {
            compose.mainClock.advanceTimeBy(250)
            val now = snapshot()
            for (tag in listOf("place-tag", "place-gate", "place-board")) assertEquals("$tag changed ${t * 250} ms in", 0, changedIn(tag, first, now))
        }
        compose.mainClock.autoAdvance = true
    }

    // The things in the picture are not buttons any more: a tap on one gets a word from Bert about it.
    private var hour by mutableIntStateOf(10)

    /** Bert keeps still (animations off), so a point worked out off him stays off him until it is tapped. */
    private fun stillGarden(at: Int) {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        regular()
        hour = at
        compose.setContent {
            BERTTheme { Column(Modifier.fillMaxSize().background(Navy)) { BertsPlace(at(hour), "content", newNotes = notes) } }
        }
        compose.waitForIdle()
    }

    /** The point on [tag]'s target nearest its middle that no target drawn over it (Bert's, the smaller things') takes. */
    private fun clearOn(tag: String): Offset {
        val t = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val over = when (tag) { "place-cottage", "place-blossom" -> listOf("place-tag", "place-gate", "place-board") else -> emptyList() } + "place-bert"
        val others = over.flatMap { o -> compose.onAllNodesWithTag(o).fetchSemanticsNodes().map { it.boundsInRoot } }
        val points = (1..19).flatMap { i -> (1..19).map { j -> Offset(t.left + t.width * i / 20, t.top + t.height * j / 20) } }
            .filter { p -> others.none { it.inflate(2f).contains(p) } }
        assertTrue("no point on $tag is clear of the targets over it", points.isNotEmpty())
        return points.minBy { (it - t.center).getDistance() }
    }

    private fun tapOn(tag: String) {
        val point = clearOn(tag)
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    /**
     * Each thing has a word from Bert, and a second tap a different one, through his day out (at the gate, on his
     * rounds, napping, on gate duty, heading home). Never a pat. At night he is indoors and the things say nothing.
     */
    @Test fun eachThingGetsAWordFromBert() {
        stillGarden(7)
        // The chalkboard's first tap in a sitting is his tally (see below); after that, his remarks like everything else.
        tapOn("place-tag")
        assertEquals("day one. everyone starts somewhere.", said())
        for (h in listOf(7, 10, 13, 16, 19)) {
            compose.runOnIdle { hour = h }
            compose.waitForIdle()
            for (thing in PlaceThing.entries) {
                tapOn(thing.tag)
                val first = said()
                assertTrue("a tap on ${thing.tag} at $h:10 said \"$first\"", first in thing.remarks)
                tapOn(thing.tag)
                val second = said()
                assertTrue("a second tap on ${thing.tag} at $h:10 said \"$second\"", second in thing.remarks && second != first)
            }
        }
        save(snapshot(), "afford-remark")
        compose.runOnIdle { hour = 23 }
        compose.waitForIdle()
        for (thing in PlaceThing.entries - PlaceThing.CHALK) compose.onNodeWithTag(thing.tag).assertDoesNotExist()
        // The chalkboard is still read at night (its tally), but has nothing to say to a tap: he is indoors.
        val before = said()
        tapOn("place-tag")
        assertEquals(before, said())
    }

    /**
     * To TalkBack the picture is the scene, Bert (a button: a pat, and Meet Bert on a long press when there is one) and
     * at night the window: nothing else in it is a button, has a name or takes a stop. Every part of the day, news or
     * none.
     */
    @Test fun onlyBertAndTheWindowAreButtons() {
        stillGarden(7)
        val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInRoot
        for (h in listOf(7, 10, 13, 16, 19, 23)) for (n in listOf(0, 2)) {
            compose.runOnIdle { hour = h; notes = n }
            compose.waitForIdle()
            val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.boundsInRoot.overlaps(scene) }
                .map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "an untagged button at ${it.boundsInRoot}" }.toSet()
            assertEquals("buttons in the picture at $h:10, $n new", setOf(if (h == 23) "place-window" else "place-bert"), buttons)
            val named = compose.onAllNodes(SemanticsMatcher("has a name") { it.config.contains(SemanticsProperties.ContentDescription) })
                .fetchSemanticsNodes().filter { it.boundsInRoot.overlaps(scene) }.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.toSet()
            assertEquals("named in the picture at $h:10", setOf("berts-place", "place-tag", if (h == 23) "place-window" else "place-bert"), named)
        }
        val bert = compose.run { runOnIdle { hour = 10 }; waitForIdle(); onNodeWithTag("place-bert").fetchSemanticsNode().config }
        assertEquals("Give Bert a pat", bert[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        assertEquals(androidx.compose.ui.semantics.Role.Button, bert[SemanticsProperties.Role])
    }

    /** While the noticeboard has a fresh note, its first tap says where to read it (under the garden); then his remarks. */
    @Test fun theNoticeboardWithNewsPointsDown() {
        notes = 2
        stillGarden(10)
        tapOn("place-board")
        assertEquals(BertLines.NEWS_BELOW, said())
        tapOn("place-board")
        assertTrue(said(), said() in PlaceThing.BOARD.remarks)
        // Read: no fresh note, no pointing.
        compose.runOnIdle { notes = 0 }
        compose.waitForIdle()
        tapOn("place-board")
        assertTrue(said(), said() in PlaceThing.BOARD.remarks)
    }

    /**
     * 0.15: the chalkboard is Bert's tally of the visit streak. TalkBack reads it as plain text (not a button); its
     * first tap in a sitting has Bert say the run and the best, the next one of his chalkboard remarks.
     */
    @Test fun theChalkboardIsReadAndItsFirstTapTellsTheRun() {
        PlaceDiscovery(compose.activity).apply {
            for (d in 9 downTo 6) visit(today.minusDays(d.toLong()))
            visit(today.minusDays(2)); visit(today.minusDays(1))
        }
        stillGarden(10)
        val board = compose.onNodeWithTag("place-tag").assertContentDescriptionEquals("Opening streak: 3 days in a row. Best: 4.")
        assertFalse("the chalkboard is a button", board.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        tapOn("place-tag")
        assertEquals("3 days running. best is 4.", said())
        save(snapshot(), "afford-chalk-streak")
        tapOn("place-tag")
        assertTrue(said(), said() in PlaceThing.CHALK.remarks)
    }

    private fun tallyAt(hour: Int, days: Int): Triple<Bitmap, PlaceFrame, androidx.compose.ui.geometry.Rect> {
        PlaceDiscovery(compose.activity).apply { for (d in days - 1 downTo 1) visit(today.minusDays(d.toLong())) }
        stillGarden(hour)
        compose.onNodeWithTag("place-tag").assertContentDescriptionEquals("Opening streak: $days ${if (days == 1) "day" else "days"} in a row. Best: $days.")
        val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInWindow
        val res = compose.activity.resources
        return Triple(snapshot(), placeFrame(scene.right - scene.left, res.configuration.screenHeightDp * res.displayMetrics.density), scene)
    }

    /** The tally is chalked on whole art pixels: every art pixel of it is one flat k x k block on screen. */
    private fun theTallySitsOnTheArtGrid() {
        val (shot, f, scene) = tallyAt(10, 301)
        assertTrue("k = ${f.k}", f.k >= 2)
        var blocks = 0
        // The slate where there is no chalk (the face's corner above the tally), to tell chalk from bare slate.
        val bare = shot.getPixel((scene.left + f.left + (152 - f.x0) * f.k).toInt(), (scene.top + 254 * f.k).toInt())
        fun light(c: Int) = android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)
        for (p in chalkboardTally(301)) {
            val x0 = (scene.left + f.left + (p.x - f.x0) * f.k).toInt(); val y0 = (scene.top + p.y * f.k).toInt()
            val c = shot.getPixel(x0, y0)
            assertTrue("no chalk at art pixel (${p.x}, ${p.y})", light(c) > light(bare) + 150)
            for (dy in 0 until f.k) for (dx in 0 until f.k) assertEquals("art pixel (${p.x}, ${p.y}) is not one flat block", c, shot.getPixel(x0 + dx, y0 + dy))
            blocks++
        }
        assertTrue(blocks > 60)
        save(shot, "afford-chalk-grid-${f.k}")
    }

    @Test fun theTallySitsOnTheArtGridAt360() = theTallySitsOnTheArtGrid()
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun theTallySitsOnTheArtGridAt320() = theTallySitsOnTheArtGrid()
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun theTallySitsOnTheArtGridAt412() = theTallySitsOnTheArtGrid()
    @Test @Config(qualifiers = "w430dp-h932dp-xxhdpi") fun theTallySitsOnTheArtGridAt430() = theTallySitsOnTheArtGrid()

    /**
     * The chalk stays legible in every light: each chalked pixel stands off the slate around it by a contrast ratio of 3
     * or more (the slate's own pixels, read beside the tally on the same rows).
     */
    private fun theChalkReadsAt(hour: Int) {
        val (shot, f, scene) = tallyAt(hour, 12)
        fun at(x: Int, y: Int) = shot.getPixel((scene.left + f.left + (x - f.x0) * f.k).toInt(), (scene.top + y * f.k).toInt())
        fun lum(c: Int): Double {
            fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch(android.graphics.Color.red(c)) + 0.7152 * ch(android.graphics.Color.green(c)) + 0.0722 * ch(android.graphics.Color.blue(c))
        }
        val chalk = chalkboardTally(12).toSet()
        val slate = (153..176).flatMap { x -> (256..270).map { y -> androidx.compose.ui.unit.IntOffset(x, y) } }.filter { it !in chalk }
        val slateLum = slate.map { lum(at(it.x, it.y)) }.sorted()[slate.size / 2]
        val faintest = chalk.minOf { lum(at(it.x, it.y)) }
        val ratio = (faintest + 0.05) / (slateLum + 0.05)
        assertTrue("the chalk at $hour:10 stands off its slate by only $ratio", ratio >= 3.0)
        save(shot, "afford-chalk-$hour")
    }

    @Test fun theChalkReadsByDay() = theChalkReadsAt(10)
    @Test fun theChalkReadsAtDusk() = theChalkReadsAt(19)
    @Test fun theChalkReadsAtNight() = theChalkReadsAt(23)

    /** Where the heart can be: above his hat (art rows 170..194, his head's columns), clear of a pat's hop. */
    private fun heartRegion(bertX: Int): android.graphics.Rect {
        val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInWindow
        val res = compose.activity.resources
        val f = placeFrame(scene.right - scene.left, res.configuration.screenHeightDp * res.displayMetrics.density)
        fun x(art: Int) = (scene.left + f.left + (art - f.x0) * f.k).toInt()
        fun y(art: Int) = (scene.top + art * f.k).toInt()
        return android.graphics.Rect(x(bertX + 34), y(170), x(bertX + 56), y(195))
    }

    /** The rows that differ between [a] and [b] inside [r], top first (empty when nothing does). */
    private fun changedRows(r: android.graphics.Rect, a: Bitmap, b: Bitmap): List<Int> =
        (r.top until r.bottom).filter { y -> (r.left until r.right).any { x -> a.getPixel(x, y) != b.getPixel(x, y) } }

    private fun heartOnPat(): Pair<List<Int>, List<Int>> {
        regular()
        compose.mainClock.autoAdvance = false
        place(16) // on gate duty: he stands still
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val region = heartRegion(BertSlot.POST.bertX!!)
        val rest = snapshot()
        assertFalse(flag("place-heart", "PlaceHeart"))
        compose.onNodeWithTag("place-bert").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertTrue("a pat shows a heart", flag("place-heart", "PlaceHeart"))
        assertTrue("he answers the pat", said() in BertLines.pats)
        val early = changedRows(region, rest, snapshot())
        compose.mainClock.advanceTimeBy(360)
        val later = snapshot()
        save(later, "afford-pat-heart")
        val laterRows = changedRows(region, rest, later)
        assertTrue("the heart is drawn over his head just after the pat", early.isNotEmpty())
        assertTrue("the heart is still there half a second in", laterRows.isNotEmpty())
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertFalse("the heart fades", flag("place-heart", "PlaceHeart"))
        assertEquals("nothing left over his head", emptyList<Int>(), changedRows(region, rest, snapshot()))
        compose.mainClock.autoAdvance = true
        return early to laterRows
    }

    @Test fun aPatSendsUpAHeartThatFades() {
        val (early, later) = heartOnPat()
        assertTrue("the heart rises: from row ${early.first()} to ${later.first()}", later.first() < early.first())
    }

    @Test fun withAnimationsOffTheHeartFadesWhereItIs() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val (early, later) = heartOnPat()
        assertEquals("the heart stays where it appears", early.first(), later.first())
    }

    /** Lifetime pats, on the phone: the tenth ever gets its own line, the eleventh is an ordinary pat again. */
    @Test fun theTenthPatEverGetsItsOwnLine() {
        regular()
        val discovery = PlaceDiscovery(compose.activity)
        repeat(9) { discovery.pat() }
        place(16)
        compose.onNodeWithTag("place-bert").performClick()
        compose.waitForIdle()
        assertEquals(BertLines.milestones.getValue(10), said())
        assertEquals(10, discovery.pats)
        compose.onNodeWithTag("place-bert").performClick()
        compose.waitForIdle()
        assertTrue(said(), said() in BertLines.pats)
        assertEquals(11, discovery.pats)
    }

    /** The chalkboard catches the light under a finger, and then Bert reads out his tally: it opens nothing. */
    @Test fun aPressedThingLightsUpThenBertRemarks() {
        regular()
        place()
        compose.waitForIdle()
        val rest = snapshot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-tag").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertTrue("the chalkboard shows it is pressed", flag("place-tag", "PlacePressed"))
        assertFalse(flag("place-board", "PlacePressed"))
        val down = snapshot()
        save(down, "afford-press-chalk")
        val slate = slateFace()
        var changed = 0; var counted = 0
        for (y in slate.top until slate.bottom) for (x in slate.left until slate.right) {
            counted++
            if (rest.getPixel(x, y) != down.getPixel(x, y)) changed++
        }
        assertTrue("the slate lit up: $changed of $counted pixels changed", changed > counted / 2)
        compose.onNodeWithTag("place-tag").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse("the press lets go", flag("place-tag", "PlacePressed"))
        // The first chalkboard tap in a sitting is Bert's tally, not a remark.
        assertEquals("day one. everyone starts somewhere.", said())
        compose.mainClock.autoAdvance = true
    }
    /**
     * On a right-to-left phone the picture is drawn as it is (a Canvas does not mirror), so its tap targets must not
     * mirror either: the chalkboard's target sits over the chalkboard (CHALK 138,248 48x36, padded 6).
     */
    @Test fun inARightToLeftLanguageTheTargetsStayOnTheirArt() {
        regular()
        val now = at(10)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                BERTTheme {
                    Column(Modifier.fillMaxSize().background(Navy)) {
                        BertsPlace(now, "content", newNotes = 0)
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInRoot
        val res = compose.activity.resources
        val f = placeFrame(scene.width, res.configuration.screenHeightDp * res.displayMetrics.density)
        fun x(art: Int) = scene.left + f.left + (art - f.x0) * f.k
        val chalk = compose.onNodeWithTag("place-tag").fetchSemanticsNode().boundsInRoot
        assertEquals("the chalkboard's target left", x(132), chalk.left, 1f)
        assertEquals("the chalkboard's target right", x(192), chalk.right, 1f)
        assertEquals("the chalkboard's target top", scene.top + 242f * f.k, chalk.top, 1f)
    }

    /**
     * ART-9 (critique 12 November), kept for 0.15: a tap anywhere on him, his own pixels as the sprite draws them, is a
     * pat, also where he stands in front of the gate (07:00) or on gate duty (16:00): never a word about the gate.
     */
    @Test fun aPatAnywhereOnBertIsAPat() {
        stillGarden(7)
        val sprite = javax.imageio.ImageIO.read(File("src/main/res/drawable-nodpi/bert_mood_content.png"))
        val replies = BertLines.pats + BertLines.TOO_MANY + BertLines.milestones.values
        for ((at, bertX) in listOf(7 to BertSlot.GATE.bertX!!, 16 to BertSlot.POST.bertX!!)) {
            compose.runOnIdle { hour = at }
            compose.waitForIdle()
            val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInRoot
            val res = compose.activity.resources
            val f = placeFrame(scene.width, res.configuration.screenHeightDp * res.displayMetrics.density)
            var taps = 0
            // Every opaque pixel of his pose on a 4 art px grid, tapped in the middle of the art pixel.
            for (y in 0 until sprite.height step 4) for (x in 0 until sprite.width step 4) {
                if (sprite.getRGB(x, y) ushr 24 < 128) continue
                val point = Offset(scene.left + f.left + (bertX + x - f.x0 + 0.5f) * f.k, scene.top + (198 + y + 0.5f) * f.k)
                compose.onRoot().performTouchInput { click(point) }
                compose.waitForIdle()
                taps++
                assertTrue("a tap on Bert at his pixel ($x, $y), $at:10, was not a pat: ${said()}", said() in replies)
            }
            assertTrue("too few taps on Bert: $taps", taps > 100)
            save(snapshot(), "afford-pat-anywhere-$at")
        }
    }
}

/**
 * The scene's cues by eye, at each light: a newcomer's garden and a regular's, news on the board, and
 * a press on each thing.
 * render them with `./gradlew :app:testDebugUnitTest -Pshots --tests '*HomeAffordanceShotsTest'` (PNGs in
 * app/build/outputs/host-ui/afford-*.png).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAffordanceShotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(hour: Int, regular: Boolean, notes: Int, name: String, press: String? = null) {
        val now = ZonedDateTime.of(2026, 9, 9, hour, 13, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 9, 9)
        if (regular) PlaceDiscovery(compose.activity).apply {
            for (d in FIRST_DAYS downTo 1) record(today.minusDays(d.toLong()))
            patted()
        }
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(now, "content", newNotes = notes)
                }
            }
        }
        compose.waitForIdle()
        if (press != null) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag(press).performTouchInput { down(Offset(width * 0.3f, height * 0.5f)) }
            compose.mainClock.advanceTimeBy(200)
        }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        if (press != null) { compose.onNodeWithTag(press).performTouchInput { cancel() }; compose.mainClock.autoAdvance = true }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Home as the owner sees it (the app, the You button, the bubble), at [hour], text at [font]; saved as [name]. */
    private fun home(hour: Int, font: Float, name: String, notes: Boolean = false) {
        val now = ZonedDateTime.of(2026, 9, 9, hour, 13, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 9, 9)
        global.bert.widget.data.BERTHomeVisits(compose.activity).meetBertDismissed = true
        PlaceDiscovery(compose.activity).apply { for (d in FIRST_DAYS downTo 1) record(today.minusDays(d.toLong())); patted() }
        val activity = global.bert.widget.data.ActivityState.Available(global.bert.widget.data.BERTActivity(now - 60_000, "woofmornin. the ladybug holds the screen door.", "content", null))
        val quote = global.bert.widget.data.QuoteState.Available(global.bert.widget.data.BERTQuote(0.0154, 2.1, null, null, null, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"))
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, font)) {
                BERTTheme { BERTApp(quote, activity, emptyList(), global.bert.widget.data.BERTPosition(), now, false, false, {}, {}, {}) }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/home/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home360DayFont10() = home(10, 1f, "home-360-day-font1.0")
    @Test fun home360NightFont10() = home(23, 1f, "home-360-night-font1.0")
    @Test fun home360DayFont16() = home(10, 1.6f, "home-360-day-font1.6")
    @Test fun home360NightFont16() = home(23, 1.6f, "home-360-night-font1.6")
    @Test fun home360GoldenFont10() = home(16, 1f, "home-360-golden-font1.0")
    @Test fun home360MorningFont10() = home(7, 1f, "home-360-morning-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411MorningFont10() = home(7, 1f, "home-411-morning-font1.0")
    @Test fun home360DuskFont10() = home(19, 1f, "home-360-dusk-font1.0")
    // 412dp at 420dpi is 1080 px across, as the owner's S25 is (411dp there; Robolectric's 411dp rounds to 1079 px).
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411DayFont10() = home(10, 1f, "home-411-day-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411NightFont10() = home(23, 1f, "home-411-night-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411DayFont16() = home(10, 1.6f, "home-411-day-font1.6")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411NightFont16() = home(23, 1.6f, "home-411-night-font1.6")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411PostFont10() = home(16, 1f, "home-411-golden-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun home411NapFont10() = home(13, 1f, "home-411-nap-font1.0")

    @Test fun newcomerDay() = shot(10, regular = false, notes = 0, "afford-newcomer-day")
    @Test fun newcomerNight() = shot(23, regular = false, notes = 0, "afford-newcomer-night")
    @Test fun regularDayNews() = shot(10, regular = true, notes = 1, "afford-regular-day-news")
    @Test fun regularGoldenNews() = shot(16, regular = true, notes = 3, "afford-regular-golden-news")
    @Test fun regularDuskNews() = shot(19, regular = true, notes = 1, "afford-regular-dusk-news")
    @Test fun regularNightNews() = shot(23, regular = true, notes = 1, "afford-regular-night-news")
    @Test fun regularDawn() = shot(7, regular = true, notes = 0, "afford-regular-dawn")
    @Test fun pressedGate() = shot(13, regular = true, notes = 0, "afford-press-gate", press = "place-gate")
    @Test fun pressedBoard() = shot(13, regular = true, notes = 1, "afford-press-board", press = "place-board")
    @Test fun pressedBoardNight() = shot(23, regular = true, notes = 1, "afford-press-board-night", press = "place-board")
    @Test fun pressedChalkNight() = shot(23, regular = true, notes = 0, "afford-press-chalk-night", press = "place-tag")

}
