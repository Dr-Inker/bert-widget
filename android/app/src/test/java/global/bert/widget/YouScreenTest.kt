package global.bert.widget

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import global.bert.widget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** 0.14 You: Home's button, the page behind it, the name that stays on the phone, and every card's way through. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YouScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val graphics = NativeGraphicsOnTestThread()
    private fun at(hour: Int): Long = ZonedDateTime.now(ZoneId.systemDefault()).withHour(hour).withMinute(10).toInstant().toEpochMilli()

    @Before fun clean() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        MyBertStore(compose.activity).forget()
        YouProfile(compose.activity).forget()
        BERTHomeVisits(compose.activity).meetBertDismissed = true
    }

    private fun app(hour: Int = 12, fontScale: Float = 1f, position: BERTPosition = BERTPosition()) {
        val now = at(hour)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BERTTheme {
                    BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the mayor is in.", "content", null)),
                        emptyList(), position, now, false, false, {}, {}, {})
                }
            }
        }
    }

    private fun chooseABert() {
        val picture = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mybert/bert-2206.png"))
        MyBertStore(compose.activity).saveChosen(MyBert("Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc", 2206, "Forest", staked = true), picture)
    }

    private fun openYou() {
        compose.onNodeWithTag("you-button").performClick()
        compose.onNodeWithText("What should Bert call you?").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun nameIsTidiedCappedAndRemembered() {
        val profile = YouProfile(compose.activity)
        assertEquals("Sam Smith", YouProfile.cleanName("  Sam \n  Smith\t "))
        assertEquals("", YouProfile.cleanName("   \n "))
        assertEquals(24, YouProfile.cleanName("a".repeat(40)).length)
        // Whole characters: an emoji at the cut is never split into half a surrogate pair.
        assertEquals("a".repeat(23) + "🐶", YouProfile.cleanName("a".repeat(23) + "🐶🐶"))
        profile.setName("  Sam  ")
        assertEquals("Sam", YouProfile(compose.activity).name())
        profile.setName("   ")
        assertEquals("", YouProfile(compose.activity).name())
    }

    /**
     * The cap never ends partway through a character as a person sees it. Each of these is several code points that read as
     * one, sitting across the 24th: cutting by code points kept half a flag (🇬🇧 as 🇬), the first of a family joined with
     * zero-width joiners, a thumbs-up without its skin tone, or a keycap without its mark. The whole character goes instead,
     * and the same one that just fits is kept whole.
     */
    private val acrossTheCap = listOf(
        "a".repeat(23) + "🇬🇧" to "a".repeat(23),
        "a".repeat(20) + "👨‍👩‍👧" to "a".repeat(20),
        "a".repeat(23) + "👍🏽" to "a".repeat(23),
        "a".repeat(22) + "1️⃣" to "a".repeat(22),
    )

    @Test fun theCapNeverEndsHalfwayThroughACharacter() {
        for ((typed, kept) in acrossTheCap) assertEquals(typed, kept, YouProfile.cleanName(typed))
        assertEquals("a".repeat(22) + "👍🏽", YouProfile.cleanName("a".repeat(22) + "👍🏽"))
        assertEquals("a".repeat(22) + "🇬🇧", YouProfile.cleanName("a".repeat(22) + "🇬🇧!"))
        // A paste is cut the same way, in the field and in what is saved.
        app()
        openYou()
        for ((typed, kept) in acrossTheCap) {
            compose.onNodeWithTag("you-name").performTextReplacement(typed)
            assertEquals(typed, kept, YouProfile(compose.activity).name())
            assertEquals(typed, kept, compose.onNodeWithTag("you-name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("you-name").performTextReplacement("")
        }
    }

    @Test fun homeShowsTheYouButtonAndItOpensTheScreen() {
        app()
        compose.onNodeWithTag("berts-place").assertIsDisplayed()
        compose.onNodeWithTag("you-button").assertIsDisplayed().assertHasClickAction().assertTextEquals("You")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
        assertEquals("Open your page", compose.onNodeWithTag("you-button").fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val bounds = compose.onNodeWithTag("you-button").getUnclippedBoundsInRoot()
        val wide = bounds.right.value - bounds.left.value
        val tall = bounds.bottom.value - bounds.top.value
        assertTrue("a 48dp-tall pill, wider than it is tall, was $wide x $tall", tall in 47.5f..48.5f && wide > 64f)
        assertTrue("at the top right of the screen", bounds.right.value > 330f && bounds.top.value < 24f)
        openYou()
        compose.onNodeWithTag("berts-place").assertDoesNotExist()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("berts-place").assertIsDisplayed()
    }

    /** The button says what it is: the avatar alone read as part of the picture (owner, S25, 0.14 preview). */
    @Test fun theButtonSaysYouInWordsOnDayAndNightSkies() {
        var now by androidx.compose.runtime.mutableLongStateOf(at(12))
        compose.setContent {
            BERTTheme {
                BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the mayor is in.", "content", null)),
                    emptyList(), BERTPosition(), now, false, false, {}, {}, {})
            }
        }
        for ((hour, sky) in listOf(12 to "day", 23 to "night")) {
            compose.runOnIdle { now = at(hour) }
            compose.onNodeWithTag("you-button").assertTextEquals("You").assertHasClickAction()
            capture("you-button-$sky")
        }
    }

    /** Home under a status bar [dp] tall, as on a phone with a tall cutout: the page lays itself out below it. */
    private fun underStatusBar(dp: Int) {
        val tall = (dp * compose.activity.resources.displayMetrics.density).toInt()
        compose.runOnIdle {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, tall, 0, 0)).build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
        assertTrue("the status bar is in play", compose.onNodeWithTag("you-button").getUnclippedBoundsInRoot().top.value >= dp)
    }

    /**
     * The night window's target, wherever it has had to go, keeps at least [minDp], stays inside the scene, overlaps no
     * other target in the picture and is clear of the You button; and it still answers a knock.
     */
    private fun theWindowStillAnswers(minDp: Float) {
        val window = compose.onNodeWithTag("place-window").getUnclippedBoundsInRoot()
        assertTrue("the window's target shrank to ${window.bottom - window.top}", window.bottom.value - window.top.value >= minDp)
        val scene = compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot()
        assertTrue("the window's target $window runs out of the scene $scene", window.top >= scene.top && window.bottom <= scene.bottom &&
            window.left >= scene.left && window.right <= scene.right)
        fun Rect.meets(o: Rect) = left < o.right && right > o.left && top < o.bottom && bottom > o.top
        val w = Rect(window.left.value, window.top.value, window.right.value, window.bottom.value)
        for (tag in listOf("you-button", "place-gate", "place-tag", "place-board")) {
            val o = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertFalse("the window's target $window overlaps $tag $o", w.meets(Rect(o.left.value, o.top.value, o.right.value, o.bottom.value)))
        }
        compose.onNodeWithTag("place-window").performClick()
        compose.onNodeWithTag("place-window").assertContentDescriptionContains("dark", substring = true)
    }

    /** The night window's target where the art puts it (WINDOW 262,104 28x32, padded 8), in root pixels. */
    private fun windowWhereTheArtIs(): Rect {
        val scene = compose.onNodeWithTag("berts-place").fetchSemanticsNode().boundsInRoot
        val res = compose.activity.resources
        val f = placeFrame(scene.width, res.configuration.screenHeightDp * res.displayMetrics.density)
        fun x(art: Int) = scene.left + f.left + (art - f.x0) * f.k
        return Rect(x(254), scene.top + 96f * f.k, x(298), scene.top + 144f * f.k)
    }

    /** The case under test: the button, as laid out, covers the window's art target (in root pixels). */
    private fun assertTheButtonIsOverTheWindowsArt() {
        val art = windowWhereTheArtIs()
        val you = compose.onNodeWithTag("you-button").fetchSemanticsNode().boundsInRoot
        assertTrue("the case needs the button $you over the window's art $art", art.overlaps(you))
    }

    private fun assertSameRect(message: String, expected: Rect, actual: Rect) =
        assertTrue("$message: expected $expected, was $actual", listOf(expected.left - actual.left, expected.top - actual.top,
            expected.right - actual.right, expected.bottom - actual.bottom).all { kotlin.math.abs(it) < 1f })

    /**
     * At 360dp the button's left edge reaches over the cottage window, which is a tap target at night. Under a tall status
     * bar (here 60dp) the button would come down over it; the window's target moves down below the button instead,
     * keeping a finger's height, above the gate.
     */
    @Test fun underATallStatusBarTheButtonNeverCoversTheNightWindow() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        app(23)
        underStatusBar(60)
        val you = compose.onNodeWithTag("you-button").getUnclippedBoundsInRoot()
        val window = compose.onNodeWithTag("place-window").getUnclippedBoundsInRoot()
        // Across, they share ground, so only the window moving down can keep them apart.
        assertTrue("the case needs the button over the window's columns: $you, $window", window.left < you.right && window.right > you.left)
        assertTrue("the button $you covers the window's target $window", window.top >= you.bottom)
        // Moved, it keeps the 32dp minimum a moved target is given.
        theWindowStillAnswers(31.5f)
    }

    /**
     * The sweep (PlaceTargetsSweepTest) covers the geometry; this is one phone end to end. On a 359dp xhdpi phone (k = 1)
     * under a 40dp status bar, the button covers the top of Bert waiting on the post (16:00, by the noticeboard, in front
     * of the gate's far post). His target stays his own:
     * it is not moved onto the chalkboard's, and a tap on him is a pat.
     */
    @Test @Config(qualifiers = "w359dp-h780dp-xhdpi") fun bertWaitingOnThePostKeepsHisOwnTargetUnderTheButton() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        app(16)
        underStatusBar(40)
        val bert = compose.onNodeWithTag("place-bert").fetchSemanticsNode().boundsInRoot
        val you = compose.onNodeWithTag("you-button").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("place-tag", "place-board")) {
            val o = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertFalse("Bert's target $bert meets $tag $o", bert.overlaps(o))
        }
        // Left where he is, so the button does cover the top of him: the case this is about.
        assertTrue("the case needs the button over Bert: $you, $bert", you.overlaps(bert))
        compose.onNodeWithTag("place-bert").performTouchInput { click(androidx.compose.ui.geometry.Offset(width / 2f, height - 8f)) }
        val said = compose.onNodeWithTag("place-line").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertTrue("a tap on Bert is a pat, he said: $said", said in BertLines.pats)
    }

    /**
     * Low density (hdpi, k = 1) at night under a 60dp status bar: the button sits over the window, and below it there is
     * no finger's height left before the gate's art, so the window's target goes beside the button. End to end: clear of
     * everything, and a knock still lands.
     */
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun aLowDensityNightPutsTheWindowBesideTheButton() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        app(23)
        underStatusBar(60)
        assertTheButtonIsOverTheWindowsArt()
        val you = compose.onNodeWithTag("you-button").getUnclippedBoundsInRoot()
        val window = compose.onNodeWithTag("place-window").getUnclippedBoundsInRoot()
        assertTrue("beside the button, not below it: $window, $you", window.right <= you.left && window.top < you.bottom)
        theWindowStillAnswers(31.5f)
    }

    /** Landscape: the scene is centred and the button far right, so the window is left exactly over its art. */
    @Test @Config(qualifiers = "w915dp-h412dp-land-xhdpi") fun aLandscapeNightKeepsItsWindow() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        app(23)
        underStatusBar(48)
        assertSameRect("the window moved", windowWhereTheArtIs(), compose.onNodeWithTag("place-window").fetchSemanticsNode().boundsInRoot)
        theWindowStillAnswers(23.5f)
    }

    /** A right-to-left language: the button stays at the top right, where the scene keeps its targets clear for it. */
    @Test fun inARightToLeftLanguageTheButtonStaysTopRight() {
        val now = at(12)
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                BERTTheme {
                    BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Available(BERTActivity(now - 60_000, "woofmornin.", "content", null)),
                        emptyList(), BERTPosition(), now, false, false, {}, {}, {})
                }
            }
        }
        val you = compose.onNodeWithTag("you-button").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("the button is at ${you.left}..${you.right} of ${root.right}", (root.right - you.right).value in 15.5f..16.5f)
    }

    /**
     * TalkBack's reading order as Compose hands it to Android, where each node names the one read after it. The button is
     * drawn over the page, so it comes after the page in the tree; it must still be read first, where it sits at the top.
     */
    @Test fun talkBackReachesTheButtonBeforeThePage() {
        val accessibility = compose.activity.getSystemService(AccessibilityManager::class.java)
        shadowOf(accessibility).setEnabled(true)
        shadowOf(accessibility).setTouchExplorationEnabled(true)
        // Compose builds the reading order only while a screen reader is on, which it reads from the running services.
        shadowOf(accessibility).setEnabledAccessibilityServiceList(listOf(android.accessibilityservice.AccessibilityServiceInfo().apply {
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
        }))
        app()
        compose.waitForIdle()
        val you = compose.onNodeWithTag("you-button").fetchSemanticsNode().id
        val bert = compose.onNodeWithTag("place-line").fetchSemanticsNode().id
        fun views(view: View): Sequence<View> = sequenceOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().flatMap { views(group.getChildAt(it)) } } ?: emptySequence())
        val provider = requireNotNull(views(compose.activity.window.decorView).first { it.javaClass.simpleName == "AndroidComposeView" }.accessibilityNodeProvider)
        fun readAfter(id: Int): Int? = provider.createAccessibilityNodeInfo(id)?.extras?.getInt(TRAVERSAL_BEFORE, -1)?.takeIf { it != -1 }
        val fromYou = generateSequence(you, ::readAfter).take(1_000).toList()
        assertTrue("Bert's line is read after the button, was $fromYou", bert in fromYou)
        assertFalse("the button is not read after Bert's line", you in generateSequence(bert, ::readAfter).drop(1).take(1_000).toList())
        assertEquals("nothing on the screen is read before the button", -1, provider.createAccessibilityNodeInfo(you)?.extras?.getInt(TRAVERSAL_AFTER, -1))
    }

    /**
     * TalkBack's real reading order on Home (as Compose hands it to Android): the picture is read where it sits, its
     * things before Bert's bubble and the page below it, and each label's pill straight after its thing.
     */
    @Test fun talkBackReadsThePictureInPlaceEachPillAfterItsThing() {
        val accessibility = compose.activity.getSystemService(AccessibilityManager::class.java)
        shadowOf(accessibility).setEnabled(true)
        shadowOf(accessibility).setTouchExplorationEnabled(true)
        shadowOf(accessibility).setEnabledAccessibilityServiceList(listOf(android.accessibilityservice.AccessibilityServiceInfo().apply {
            feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
        }))
        app(10)
        compose.waitForIdle()
        fun views(view: View): Sequence<View> = sequenceOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().flatMap { views(group.getChildAt(it)) } } ?: emptySequence())
        val provider = requireNotNull(views(compose.activity.window.decorView).first { it.javaClass.simpleName == "AndroidComposeView" }.accessibilityNodeProvider)
        fun readAfter(id: Int): Int? = provider.createAccessibilityNodeInfo(id)?.extras?.getInt(TRAVERSAL_BEFORE, -1)?.takeIf { it != -1 }
        val read = generateSequence(compose.onNodeWithTag("you-button").fetchSemanticsNode().id, ::readAfter).take(1_000).toList()
        fun at(tag: String): Int = read.indexOf(compose.onNodeWithTag(tag).fetchSemanticsNode().id).also { assertTrue("$tag is never read: $read", it >= 0) }
        val things = listOf("place-tag" to "label-chalk", "place-gate" to "label-gate", "place-board" to "label-board")
        for ((thing, pill) in things) assertEquals("$pill is read straight after $thing", at(thing) + 1, at(pill))
        val bubble = at("place-line")
        val page = read.indexOf(compose.onNodeWithText("@bertcoincto", substring = true).fetchSemanticsNode().id)
        assertTrue("the page's links are read", page >= 0)
        for ((thing, pill) in things) {
            assertTrue("$thing is read before Bert's bubble ($bubble): ${at(thing)}", at(thing) < bubble)
            assertTrue("$pill is read before the page below ($page): ${at(pill)}", at(pill) < page)
        }
    }

    @Test fun theButtonGoesWithTheSkyWhenHomeScrolls() {
        app()
        compose.onNodeWithTag("you-button").assertIsDisplayed()
        compose.onNodeWithText("About, privacy & feedback", substring = true).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("you-button").assertDoesNotExist()
    }

    /** A swipe that starts on a scene label scrolls Home, as anywhere else on the page; it is not a tap on the label. */
    @Test fun aSwipeThatStartsOnALabelScrollsHome() {
        app(12)
        val before = compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("label-board").performTouchInput { swipeUp(startY = centerY, endY = centerY - 600f) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().top
        assertTrue("Home did not scroll: $before -> $after", after < before - 50.dp)
        compose.onNodeWithTag("noticeboard").assertDoesNotExist()
    }

    /** A tap on a label while Home is still flinging stops the fling, as a tap anywhere on a moving page does; it opens nothing. */
    @Test fun aTapThatStopsAFlingOpensNoLabel() {
        app(12)
        compose.mainClock.autoAdvance = false
        // A short, fast flick from the page below the scene: the scene barely moves before the fling carries on.
        compose.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.8f - 160f), durationMillis = 40) }
        val tops = (0 until 6).map { compose.mainClock.advanceTimeByFrame(); compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().top }
        val a = tops[2]
        val b = tops[3]
        assertTrue("the page should still be flinging: $a -> $b", b < a)
        compose.onNodeWithTag("label-board").assertExists().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("noticeboard").assertDoesNotExist()
        // And the tap stopped it.
        val c = compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(500)
        assertEquals(c, compose.onNodeWithTag("berts-place").getUnclippedBoundsInRoot().top)
        compose.mainClock.autoAdvance = true
    }

    @Test fun theButtonIsOnlyOnHome() {
        app()
        compose.onNodeWithText("Play", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("you-button").assertDoesNotExist()
    }

    @Test fun theScreenRendersWithoutAnNftOrAName() {
        app()
        openYou()
        compose.onNodeWithText("Bert doesn’t know your name yet.").assertIsDisplayed()
        capture("you-empty")
        compose.onNodeWithText("Show your Bert NFT").assertExists()
        compose.onNodeWithText("Forget my name").assertDoesNotExist()
        compose.onNodeWithText("Everything here stays on this phone.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not set up. Optional, and only on this phone.").assertExists()
        compose.onNodeWithText("No saved cards yet. Make one in Create.").assertExists()
        capture("you-empty-lower")
    }

    @Test fun theScreenRendersWithAnNftAndAName() {
        chooseABert()
        YouProfile(compose.activity).setName("Sam")
        CaptionLibrary(compose.activity).let { library ->
            val style = CardStyle()
            val bitmap = Bitmap.createBitmap(style.format.width, style.format.height, Bitmap.Config.ARGB_8888)
            repeat(2) { library.save(RenderedCaption("A tiny mayor $it.", 0, bitmap, style)) }
        }
        BertVisits(compose.activity).apply { record(LocalDate.now().withDayOfMonth(1)); record(LocalDate.now().withDayOfMonth(2)); record(LocalDate.now().withDayOfMonth(3)) }
        app(position = BERTPosition(250_000.0, 1000.0))
        compose.onNodeWithTag("you-button").assertTextEquals("You")
        openYou()
        compose.onNodeWithText("Bert calls you Sam.").assertIsDisplayed()
        compose.onNodeWithText("Bert #2206").assertExists()
        compose.onNodeWithText("Show your Bert NFT").assertDoesNotExist()
        compose.onNodeWithText("2 saved cards").assertExists()
        compose.onNodeWithText("Set up. Kept on this phone.").assertExists()
        compose.onNodeWithTag("you-name").assertTextContains("Sam")
        capture("you-filled")
        compose.onNodeWithText("Holdings").performScrollTo()
        capture("you-filled-lower")
        compose.onNodeWithText("Back").performClick()
        capture("home-you-nft")
    }

    @Test fun theNameTrimsPersistsAndIsForgotten() {
        app()
        openYou()
        compose.onNodeWithTag("you-name").performTextInput("  Sam   Smith ")
        compose.onNodeWithText("Bert calls you Sam Smith.").assertIsDisplayed()
        assertEquals("Sam Smith", YouProfile(compose.activity).name())
        compose.onNodeWithTag("you-name").performTextReplacement("y".repeat(24))
        assertEquals("y".repeat(24), YouProfile(compose.activity).name())
        compose.onNodeWithTag("you-name").performTextReplacement("Pip")
        compose.onNodeWithText("Forget my name").performScrollTo().performClick()
        assertEquals("", YouProfile(compose.activity).name())
        compose.onNodeWithText("Bert doesn’t know your name yet.").assertExists()
        compose.onNodeWithText("Forget my name").assertDoesNotExist()
    }

    @Test fun aLongPasteIsCutToTheCapNotDropped() {
        app()
        openYou()
        // Over 24 characters: the field and the saved name take the first 24, instead of ignoring the paste.
        compose.onNodeWithTag("you-name").performTextReplacement("Bartholomew Fitzgerald-Pomeranian the Third")
        assertEquals("Bartholomew Fitzgerald-P", YouProfile(compose.activity).name())
        compose.onNodeWithTag("you-name").assertTextContains("Bartholomew Fitzgerald-P")
        compose.onNodeWithText("Bert calls you Bartholomew Fitzgerald-P.").assertExists()
        // Whole characters at the cut: an emoji is never split.
        compose.onNodeWithTag("you-name").performTextReplacement("a".repeat(23) + "🐶🐶🐶")
        assertEquals("a".repeat(23) + "🐶", YouProfile(compose.activity).name())
        // Counted as it will be saved: runs of spaces and a line break don't use up the 24, and the field keeps what was typed.
        compose.onNodeWithTag("you-name").performTextReplacement("Sam" + " ".repeat(30) + "\nSmith")
        assertEquals("Sam Smith", YouProfile(compose.activity).name())
        compose.onNodeWithTag("you-name").assertTextContains("Sam" + " ".repeat(31) + "Smith")
        assertTrue(YouProfile.fits("Sam" + " ".repeat(30) + "Smith"))
        assertFalse(YouProfile.fits("x".repeat(25)))
    }

    @Test fun typingIntoAFullNameChangesNothingAndAPasteKeepsWhatWasThere() {
        app()
        openYou()
        val field = compose.onNodeWithTag("you-name")
        fun selection() = field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        fun shown() = field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        field.performTextReplacement("Bartholomew Fitzgerald-P")
        // A letter typed after "Bart" in a full name is refused: the name, its last letter and the cursor all stay put.
        field.performTextInputSelection(TextRange(4))
        field.performTextInput("x")
        assertEquals("Bartholomew Fitzgerald-P", shown())
        assertEquals("Bartholomew Fitzgerald-P", YouProfile(compose.activity).name())
        assertEquals(TextRange(4), selection())
        // Typing still works anywhere once there is room again.
        field.performTextReplacement("Bartholomew Fitzgerald-")
        field.performTextInputSelection(TextRange(4))
        field.performTextInput("x")
        assertEquals("Bartxholomew Fitzgerald-", YouProfile(compose.activity).name())
        // A paste into the middle of a shorter name keeps all of the name and as much of the paste as fits, cursor after it.
        field.performTextReplacement("Sam Smith")
        field.performTextInputSelection(TextRange(3))
        field.performTextInput(" Bartholomew Fitzgerald")
        assertEquals("Sam Bartholomew Fi Smith", YouProfile(compose.activity).name())
        assertEquals("Sam Bartholomew Fi Smith", shown())
        assertEquals(TextRange(18), selection())
    }

    @Test fun theNoticeboardUsesTheNameAndOtherwiseStaysAsItWas() {
        app()
        compose.onNodeWithTag("place-board").performClick()
        compose.onNodeWithText("on the board").assertExists()
        compose.onNodeWithTag("noticeboard-close").performScrollTo().performClick()
        compose.waitForIdle()
        openYou()
        compose.onNodeWithTag("you-name").performTextInput("Sam")
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("place-board").performClick()
        compose.onNodeWithText("on the board for Sam").assertExists()
    }

    @Test fun everyCardGoesWhereItSays() {
        app()
        openYou()
        // Style -> Tools > Style
        compose.onNodeWithText("Style").performScrollTo().performClick()
        compose.onNodeWithText("A little BERT. Everywhere.").assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        // Holdings -> Tools > Holdings
        openYou()
        compose.onNodeWithText("Holdings").performScrollTo().performClick()
        compose.onNodeWithText("BERT amount").assertExists()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        // Your cards -> Create > Saved
        openYou()
        compose.onNodeWithText("Your cards").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your collection").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        // Your Bert -> My Bert, and Back returns to You
        openYou()
        compose.onNodeWithText("Your Bert").performScrollTo().performClick()
        compose.onNodeWithText("Hold a Bert from the official BERT NFT collection?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("What should Bert call you?").assertIsDisplayed()
        // The Lost Trail -> the game
        compose.onNodeWithText("The Lost Trail").performScrollTo().performClick()
        assertEquals(LostTrailActivity::class.java.name, shadowOf(compose.activity).nextStartedActivity.component?.className)
        // Draw Bert -> the Studio in the browser
        compose.onNodeWithText("Draw Bert").performScrollTo().performClick()
        val draw = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, draw.action)
        assertEquals(BERTLink.DRAW.url, draw.dataString)
    }

    @Test fun lookingAtYourVisitsIsNotAVisit() {
        val prefs = compose.activity.getSharedPreferences("bert_place", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val today = LocalDate.now()
        val first = today.withDayOfMonth(1)
        val second = today.withDayOfMonth(2)
        BertVisits(compose.activity).apply { record(first); record(second) }
        val before = prefs.all.toMap()
        compose.setContent {
            BERTTheme { YouScreen(androidx.compose.ui.Modifier, holdingsSet = false, close = {}, openStyle = {}, openHoldings = {}, openSaved = {}) }
        }
        compose.onNodeWithText("You’ve come round 2 days this month").assertIsDisplayed()
        assertEquals("reading must not write", before, prefs.all.toMap())
        assertEquals(setOf(1, 2), BertVisits(compose.activity).thisMonth(today))
        // Another month's days are not this month's.
        assertEquals(emptySet<Int>(), BertVisits(compose.activity).thisMonth(today.minusMonths(1)))
    }

    @Test fun noVisitsYetSaysSo() {
        compose.activity.getSharedPreferences("bert_place", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        compose.setContent {
            BERTTheme { YouScreen(androidx.compose.ui.Modifier, holdingsSet = false, close = {}, openStyle = {}, openHoldings = {}, openSaved = {}) }
        }
        compose.onNodeWithText("No visits yet this month. Come round and Bert leaves a paw print.").assertExists()
    }

    /**
     * Every piece of text on screen is laid out whole and drawn whole: its own layout does not overflow (no line lost to
     * maxLines, an ellipsis or a box too narrow or short for it), and its full bounds, from layout coordinates that neither scrolling nor a
     * card's clip can trim, sit inside the card or button that holds it and inside the screen. The clipped bounds
     * semantics reports would hide exactly the clipping this is looking for.
     */
    private fun assertNoTextClipped() {
        val screen = compose.onRoot().fetchSemanticsNode().layoutInfo.coordinates.size.width.toFloat()
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(nodes.size > 10)
        fun SemanticsNode.unclipped(): Rect = layoutInfo.coordinates.let { Rect(it.positionInRoot(), it.size.toSize()) }
        for (node in nodes) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()?.takeIf { it.isNotEmpty() }
                ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text ?: "?"
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            // Not hasVisualOverflow: MultiParagraph.width is the offered width, not the wrapped one, so a short label in a
            // button reads as overflowing. Each line's own extent, and whether any line was cut or ellipsized, is exact.
            assertFalse("\"$text\" loses lines to its box", layout.didOverflowHeight)
            for (n in 0 until layout.lineCount) {
                assertFalse("\"$text\" line $n is ellipsized", layout.isLineEllipsized(n))
                assertTrue("\"$text\" line $n is wider than its box", layout.getLineRight(n) - layout.getLineLeft(n) <= layout.size.width + 0.5f)
            }
            val own = node.unclipped()
            assertTrue("\"$text\" runs off the ${screen}px screen: $own", own.left >= -0.5f && own.right <= screen + 0.5f)
            var holder = node.parent
            while (holder != null && !holder.config.contains(SemanticsActions.OnClick) && !holder.config.contains(SemanticsProperties.TestTag)) holder = holder.parent
            // A text field's floating label sits on the field's top border by design, half outside it, unclipped.
            if (holder == null || holder.config.contains(SemanticsActions.SetText)) continue
            val box = holder.unclipped()
            assertTrue("\"$text\" spills out of what holds it: $own outside $box",
                own.left >= box.left - 0.5f && own.right <= box.right + 0.5f && own.top >= box.top - 0.5f && own.bottom <= box.bottom + 0.5f)
        }
    }

    @Test fun nothingClipsAtLargeTextOn360dp() {
        chooseABert()
        YouProfile(compose.activity).setName("Bertram Pomeranian-Smith")
        BertVisits(compose.activity).record(LocalDate.now())
        app(fontScale = 1.3f)
        openYou()
        assertNoTextClipped()
        // Every card is reachable and its words fit the card: scroll to the last and check the first line of each.
        for (title in listOf("Your Bert", "Style", "Your visits", "The Lost Trail", "Your cards", "Draw Bert", "Holdings")) {
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Everything here stays on this phone.").performScrollTo().assertIsDisplayed()
        capture("you-large-text")
    }

    private fun homeTop(hour: Int, name: String) {
        app(hour)
        compose.onNodeWithTag("you-button").assertIsDisplayed()
        capture("home-you-$name")
    }

    private companion object {
        /** Where Compose's accessibility nodes say, for tests, which node is read after them. */
        const val TRAVERSAL_BEFORE = "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL"
        /** And which node is read before them. */
        const val TRAVERSAL_AFTER = "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALAFTER_VAL"
    }

    @Test fun homeTopWithTheButtonByDay() = homeTop(10, "day")
    @Test fun homeTopWithTheButtonAtGoldenHour() = homeTop(16, "golden")
    @Test fun homeTopWithTheButtonAtDusk() = homeTop(19, "dusk")
    @Test fun homeTopWithTheButtonByNight() = homeTop(23, "night")
}
