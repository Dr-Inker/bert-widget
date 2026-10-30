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
 * 0.14 afford pass (owner on the S25: "its not very obvious that things are there to be clicked on"): what on Home
 * says it can be tapped. The scene's cues are read through semantics flags looked up by name, so these tests compile
 * against a build without them and fail there, rather than not compiling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAffordanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun at(hour: Int): Long = ZonedDateTime.now(ZoneId.systemDefault()).withHour(hour).withMinute(10).toInstant().toEpochMilli()
    private val today: LocalDate get() = LocalDate.now(ZoneId.systemDefault())

    private var notes by mutableIntStateOf(0)
    private var markets = 0

    private fun place(hour: Int = 10) {
        val now = at(hour)
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(now, "content", 0.0154, newNotes = notes, openBoard = {}, openMarket = { markets++ }, openTrail = {})
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
        compose.waitUntil(5_000) { !flag("place-tag", "PlaceTwinkle") }
        assertFalse("no news, no note", flag("place-board", "PlaceNews"))
        val quiet = snapshot()
        compose.runOnIdle { notes = 2 }
        compose.waitForIdle()
        assertTrue("news pins a note on the board", flag("place-board", "PlaceNews"))
        val news = snapshot()
        save(news, "afford-board-news")
        // Drawn on the board itself, not just announced: counted over the note's own place (art 372..383 x 259..271, the
        // second note behind included), so a wider "News · 2" label can't pass this for it.
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

    @Test fun aRegularGetsOneTwinklePassWhenHomeComesBackThenRest() {
        regular()
        compose.mainClock.autoAdvance = false
        // Early afternoon: Bert naps under the blossom and keeps still, so only the stars change the picture.
        place(13)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        for (tag in listOf("place-gate", "place-tag", "place-board")) {
            assertTrue("$tag twinkles when Home opens", flag(tag, "PlaceTwinkle"))
        }
        // Half a second in, the pass starts at the chalkboard (its star at full stretch about 0.9 s in) and reaches the
        // noticeboard last (about 1.7 s in).
        compose.mainClock.advanceTimeBy(900)
        val chalkLit = snapshot()
        compose.mainClock.advanceTimeBy(800)
        val boardLit = snapshot()
        save(boardLit, "afford-twinkle-pass")
        compose.mainClock.advanceTimeBy(4_000)
        for (tag in listOf("place-gate", "place-tag", "place-board")) assertFalse("$tag still twinkling: it should rest", flag(tag, "PlaceTwinkle"))
        val rest = snapshot()
        // Drawn, not only flagged: each star was in the picture during the pass and is gone after it.
        assertTrue("no star on the chalkboard during the pass", changedIn("place-tag", chalkLit, rest) > 0)
        assertTrue("no star on the noticeboard during the pass", changedIn("place-board", boardLit, rest) > 0)
        // And stays at rest: nothing loops.
        compose.mainClock.advanceTimeBy(30_000)
        assertFalse(flag("place-tag", "PlaceTwinkle"))
        val later = snapshot()
        assertEquals("the chalkboard moved at rest", 0, changedIn("place-tag", rest, later))
        assertEquals("the noticeboard moved at rest", 0, changedIn("place-board", rest, later))
        // Away and back (another app, the screen off): one more pass.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue("a pass on coming back", flag("place-tag", "PlaceTwinkle"))
        compose.mainClock.advanceTimeBy(5_000)
        assertFalse(flag("place-tag", "PlaceTwinkle"))
        compose.mainClock.autoAdvance = true
    }

    @Test fun withAnimationsOffThereIsNoTwinkle() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        regular()
        compose.mainClock.autoAdvance = false
        place()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        for (tag in listOf("place-gate", "place-tag", "place-board")) assertFalse("$tag twinkles with animations off", flag(tag, "PlaceTwinkle"))
        compose.mainClock.autoAdvance = true
    }

    @Test fun aNewcomersHintsWalkThroughTheSpotsAfterThePat() {
        place()
        assertEquals("pat me. i permit it.", said())
        compose.onNodeWithTag("place-bert").performClick()
        // The pat's reply, then straight on to the first spot not yet tried: no waiting for tomorrow.
        compose.waitUntil(5_000) { said() !in BertLines.pats && said() != BertLines.TOO_MANY }
        assertEquals(PlaceSpot.GATE.hint, said())
        compose.onNodeWithTag("place-gate").performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, height / 2f)) }
        compose.waitUntil(5_000) { said() == PlaceSpot.CHALK.hint }
        compose.onNodeWithTag("place-tag").performClick()
        compose.waitUntil(5_000) { said() == PlaceSpot.BOARD.hint }
        compose.onNodeWithTag("place-board").performClick()
        // Everything found: his own line.
        compose.waitUntil(5_000) { said() == BertLines.of(BertSlot.ROUNDS, today) }
    }

    // The labels (0.14, after the owner's S25: pixel cues alone were too small to see). Read by name, so these compile
    // against a build without labels and fail there.
    private fun labelText(tag: String): String? =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.firstOrNull { it.key.name == "PlaceLabelText" }?.value as? String

    private var boards = 0
    private var trips = 0
    private var gateOpen by mutableStateOf(true)
    private var hour by mutableIntStateOf(10)

    private fun labelled(at: Int = 10) {
        regular()
        hour = at
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy)) {
                    BertsPlace(at(hour), "content", 0.0154, newNotes = notes, openBoard = { boards++ }, openMarket = { markets++ },
                        openTrail = if (gateOpen) ({ trips++ }) else null)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun eachThingThatOpensSomethingSaysSoInALabel() {
        labelled()
        assertEquals("Price \u203a", labelText("label-chalk"))
        assertEquals("Play \u203a", labelText("label-gate"))
        assertEquals("News", labelText("label-board"))
        save(snapshot(), "afford-labels-day")
    }

    @Test fun theNewsLabelCountsTheUnreadNotesOnlyWhileThereAreSome() {
        labelled()
        assertEquals("News", labelText("label-board"))
        compose.runOnIdle { notes = 2 }
        compose.waitForIdle()
        assertEquals("News \u00b7 2", labelText("label-board"))
        compose.runOnIdle { notes = 0 }
        compose.waitForIdle()
        assertEquals("News", labelText("label-board"))
    }

    @Test fun thePlayLabelIsThereOnlyWhenTheGateGoesSomewhere() {
        gateOpen = false
        labelled()
        assertEquals(null, labelText("label-gate"))
        assertEquals("Price \u203a", labelText("label-chalk"))
        compose.runOnIdle { gateOpen = true }
        compose.waitForIdle()
        assertEquals("Play \u203a", labelText("label-gate"))
    }

    /**
     * What you see is what you tap, through Bert's day: at 07:00 he stands at the gate (by "Price"), at 10:00 he walks
     * his rounds, at 16:00 he waits on the post in front of the gate (by "Play" and "News"), at 19:00 he walks home. A tap
     * on a label opens its thing and never pats him; a tap on him right beside a label's pill (its tail's space
     * included) is a pat.
     */
    @Test fun aTapOnALabelOpensItsThingAndATapOnBertBesideItIsAPat() {
        labelled()
        val opened = mapOf("label-chalk" to { markets }, "label-board" to { boards }, "label-gate" to { trips })
        fun box(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        var edgesOnBert = 0
        for (h in listOf(7, 10, 16, 19)) {
            compose.runOnIdle { hour = h }
            compose.waitForIdle()
            for ((tag, count) in opened) {
                val before = count()
                compose.onNodeWithTag(tag).performTouchInput { click(center) }
                compose.waitForIdle()
                assertEquals("a tap on $tag at $h:00 opens its thing", before + 1, count())
                assertFalse("a tap on $tag at $h:00 patted Bert: ${said()}", said() in BertLines.pats || said() == BertLines.TOO_MANY)
            }
            // Bert beside a label: a point on him just off a label's pill (in its tail's space, or beside it), or else the
            // point on him nearest a label but clear of every pill.
            val bert = box("place-bert")
            val labels = opened.keys.map { box(it) }
            val edge = labels.flatMap { r ->
                listOf(Offset(r.center.x, r.bottom + 4), Offset(r.center.x, r.top - 4), Offset(r.left - 4, r.center.y), Offset(r.right + 4, r.center.y),
                    Offset(r.left + 4, r.bottom + 4), Offset(r.right - 4, r.bottom + 4))
            }.firstOrNull { p -> bert.contains(p) && labels.none { it.contains(p) } }
            val beside = edge ?: run {
                val points = (0..20).flatMap { i -> (0..20).map { j -> Offset(bert.left + bert.width * i / 20, bert.top + bert.height * j / 20) } }
                    .filter { p -> bert.contains(p) && labels.none { it.inflate(1f).contains(p) } }
                points.minBy { p -> labels.minOf { r -> (p - r.center).getDistance() } }
            }
            if (edge != null) edgesOnBert++
            compose.onRoot().performTouchInput { click(beside) }
            compose.waitForIdle()
            assertTrue("a tap on Bert at $beside beside a label at $h:00 should pat him, he said: ${said()}", said() in BertLines.pats || said() == BertLines.TOO_MANY)
            compose.waitUntil(5_000) { said() !in BertLines.pats && said() != BertLines.TOO_MANY }
        }
        // Right against a label is the case that needs the label's target to be its pill alone; make sure the day reached it.
        assertTrue("no point on Bert right beside a label at any of these hours", edgesOnBert > 0)
        // And counts as finding the thing, as a tap on it would.
        assertEquals(setOf(PlaceSpot.CHALK, PlaceSpot.BOARD, PlaceSpot.GATE), PlaceDiscovery(compose.activity).record(today).tapped)
    }

    /** A press on a label that the system takes back (a back swipe, a parent's intercept) opens nothing, and lets go. */
    @Test fun aCancelledPressOnALabelOpensNothing() {
        labelled()
        compose.onNodeWithTag("label-chalk").performTouchInput { down(center); cancel() }
        compose.waitForIdle()
        assertEquals("a cancelled press opened Market", 0, markets)
        compose.waitUntil(2_000) { !flag("place-tag", "PlacePressed") }
        assertEquals(emptySet<PlaceSpot>(), PlaceDiscovery(compose.activity).record(today).tapped)
    }

    /**
     * To TalkBack a label's pill does what its thing does (a button, the thing's action) and is read right after it.
     * Its name leads with the label's word, for Voice Access ("tap Price"), and the thing's own name does not, so
     * there is one match for the word and no name read twice. Its words are not a second piece of text.
     */
    @Test fun toTalkBackAPillIsItsThingByItsLabel() {
        labelled()
        for ((label, thing) in listOf("label-chalk" to "place-tag", "label-gate" to "place-gate", "label-board" to "place-board")) {
            val l = compose.onNodeWithTag(label).fetchSemanticsNode().config
            val t = compose.onNodeWithTag(thing).fetchSemanticsNode().config
            assertTrue("$label's name is not its thing's", t[SemanticsProperties.ContentDescription] != l[SemanticsProperties.ContentDescription])
            assertEquals(androidx.compose.ui.semantics.Role.Button, l[SemanticsProperties.Role])
            assertEquals(t[androidx.compose.ui.semantics.SemanticsActions.OnClick].label, l[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
            assertEquals("$label is read right after its thing", t[SemanticsProperties.TraversalIndex] + 0.5f, l[SemanticsProperties.TraversalIndex])
            assertFalse("$label exposes its words as text", l.contains(SemanticsProperties.Text))
        }
        // What is shown is in the pill's name; the thing says what it is without the word.
        compose.onNodeWithTag("label-chalk").assertContentDescriptionEquals("Price: chalkboard, BERT \$0.0154")
        compose.onNodeWithTag("place-tag").assertContentDescriptionEquals("Chalkboard: BERT \$0.0154")
        compose.onNodeWithTag("label-gate").assertContentDescriptionEquals("Play: the garden gate to The Lost Trail")
        compose.onNodeWithTag("place-gate").assertContentDescriptionEquals("The garden gate to The Lost Trail")
        compose.onNodeWithTag("label-board").assertContentDescriptionEquals("News: today\u2019s note from Berthalla")
        compose.onNodeWithTag("place-board").assertContentDescriptionEquals("Noticeboard: today\u2019s note from Berthalla")
        compose.runOnIdle { notes = 2 }
        compose.onNodeWithTag("label-board").assertContentDescriptionEquals("News, 2 new: noticeboard")
        compose.onNodeWithTag("place-board").assertContentDescriptionEquals("Noticeboard, 2 new")
        // In the picture, one node answers to each word. (On the full Home page "Play" also matches the Lost Trail card's
        // "Play The Lost Trail", which opens the same thing.)
        for (word in listOf("Price", "Play", "News"))
            compose.onAllNodes(hasContentDescription(word, substring = true)).assertCountEquals(1)
        // A double-tap on the pill does what the thing does.
        compose.onNodeWithTag("label-chalk").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(1, markets) }
    }

    /**
     * Explore by touch: a finger resting on a label's pill is announced as the pill, by its own name, never as what is
     * beneath it (the thing itself, the scene, the gate's edge, Bert at 16:00). Through the real Compose accessibility
     * hit test, by hovering.
     */
    @Test fun touchingAPillToExploreFindsItsThing() {
        val accessibility = compose.activity.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        org.robolectric.Shadows.shadowOf(accessibility).setEnabled(true)
        org.robolectric.Shadows.shadowOf(accessibility).setTouchExplorationEnabled(true)
        org.robolectric.Shadows.shadowOf(accessibility).setEnabledAccessibilityServiceList(listOf(android.accessibilityservice.AccessibilityServiceInfo().apply {
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
        }))
        labelled()
        fun views(view: android.view.View): Sequence<android.view.View> = sequenceOf(view) +
            ((view as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).asSequence().flatMap { views(g.getChildAt(it)) } } ?: emptySequence())
        val composeView = views(compose.activity.window.decorView).first { it.javaClass.simpleName == "AndroidComposeView" }
        for (h in listOf(10, 16)) {
            compose.runOnIdle { hour = h }
            compose.waitForIdle()
            for ((label, name) in listOf("label-chalk" to "Price: chalkboard, BERT \$0.0154", "label-gate" to "Play: the garden gate to The Lost Trail",
                    "label-board" to "News: today\u2019s note from Berthalla")) {
                val pill = compose.onNodeWithTag(label).fetchSemanticsNode().boundsInRoot
                val at = IntArray(2).also { composeView.getLocationInWindow(it) }
                val x = pill.center.x; val y = pill.center.y
                val seen = org.robolectric.Shadows.shadowOf(accessibility).sentAccessibilityEvents.size
                compose.runOnIdle {
                    for (action in listOf(android.view.MotionEvent.ACTION_HOVER_ENTER, android.view.MotionEvent.ACTION_HOVER_MOVE)) {
                        val e = android.view.MotionEvent.obtain(0, 0, action, x, y, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
                        composeView.dispatchGenericMotionEvent(e); e.recycle()
                    }
                }
                compose.waitForIdle()
                val entered = org.robolectric.Shadows.shadowOf(accessibility).sentAccessibilityEvents.drop(seen)
                    .filter { it.eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_ENTER }
                    .map { event ->
                        // The event names the virtual node hovered; ask the view's own provider what that node is called.
                        val node = android.view.accessibility.AccessibilityRecord::class.java.getMethod("getSourceNodeId").invoke(event) as Long
                        val virtual = (node ushr 32).toInt()
                        composeView.accessibilityNodeProvider?.createAccessibilityNodeInfo(virtual)?.contentDescription?.toString().orEmpty()
                    }
                assertTrue("touching the \"$name\" pill at $h:00 (${at.toList()}) announced $entered", entered.isNotEmpty() && entered.last() == name)
            }
        }
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
                        BertsPlace(now, "content", 0.0154, newNotes = 0, openBoard = {}, openMarket = {}, openTrail = {})
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

    @Test fun aPressedSpotShowsItsPressBeforeItOpens() {
        regular()
        place()
        compose.waitUntil(5_000) { !flag("place-tag", "PlaceTwinkle") }
        val rest = snapshot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("place-tag").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertTrue("the chalkboard shows it is pressed", flag("place-tag", "PlacePressed"))
        assertFalse(flag("place-board", "PlacePressed"))
        val down = snapshot()
        save(down, "afford-press-chalk")
        // The slate itself brightens: counted on the slate's face alone, leaving out the corner the star flashes on, so
        // the star cannot pass this by itself.
        val slate = slateFace()
        var changed = 0; var counted = 0
        for (y in slate.top until slate.bottom) for (x in slate.left until slate.right) {
            if (x >= slate.starLeft && y < slate.starBottom) continue
            counted++
            if (rest.getPixel(x, y) != down.getPixel(x, y)) changed++
        }
        assertTrue("the slate lit up: $changed of $counted pixels changed", changed > counted / 2)
        compose.onNodeWithTag("place-tag").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse("the press lets go", flag("place-tag", "PlacePressed"))
        assertEquals("one tap, one Market", 1, markets)
        compose.mainClock.autoAdvance = true
    }
}

/**
 * The scene's cues by eye, at each light: a newcomer's garden and a regular's, the labels on each, news on the board, and
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
                    BertsPlace(now, "content", 0.0154, newNotes = notes, openBoard = {}, openMarket = {}, openTrail = {})
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
        File("build/outputs/host-ui/labels/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun labels360DayFont10() = home(10, 1f, "home-360-day-font1.0")
    @Test fun labels360NightFont10() = home(23, 1f, "home-360-night-font1.0")
    @Test fun labels360DayFont16() = home(10, 1.6f, "home-360-day-font1.6")
    @Test fun labels360NightFont16() = home(23, 1.6f, "home-360-night-font1.6")
    @Test fun labels360GoldenFont10() = home(16, 1f, "home-360-golden-font1.0")
    @Test fun labels360MorningFont10() = home(7, 1f, "home-360-morning-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411MorningFont10() = home(7, 1f, "home-411-morning-font1.0")
    @Test fun labels360DuskFont10() = home(19, 1f, "home-360-dusk-font1.0")
    // 412dp at 420dpi is 1080 px across, as the owner's S25 is (411dp there; Robolectric's 411dp rounds to 1079 px).
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411DayFont10() = home(10, 1f, "home-411-day-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411NightFont10() = home(23, 1f, "home-411-night-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411DayFont16() = home(10, 1.6f, "home-411-day-font1.6")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411NightFont16() = home(23, 1.6f, "home-411-night-font1.6")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411PostFont10() = home(16, 1f, "home-411-golden-font1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun labels411NapFont10() = home(13, 1f, "home-411-nap-font1.0")

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
