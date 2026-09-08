package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Host-rendered Compose evidence; does not claim physical-device or launcher coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BERTUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun app(activity: ActivityState = ActivityState.Unavailable, fontScale: Float = 1f, restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              BERTTheme {
                BERTApp(QuoteState.Unavailable("No connection"), activity, emptyList(), BERTPosition(),
                    1_789_000_000_000, false, false, {}, {}, {})
              }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    @Test fun activityAndCaptionCreationRender() {
        app(ActivityState.Available(BERTActivity(1_789_000_000_000,
            "The town is quiet. My hat is not. A very good day to make something.", "curious", null)))
        compose.onNodeWithText("The town is quiet. My hat is not. A very good day to make something.").assertIsDisplayed()
        capture("home-activity")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        waitForPreview()
        val preview = compose.onNode(hasContentDescription("Caption card preview:", substring = true)).getUnclippedBoundsInRoot()
        val navigation = compose.onNode(hasText("Create") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue("The complete preview must fit above navigation on first arrival", preview.bottom <= navigation.top)
        capture("create")
        File("build/outputs/host-ui/create-measurements.json").writeText(org.json.JSONObject()
            .put("previewTopDp", preview.top.value).put("previewBottomDp", preview.bottom.value)
            .put("previewHeightDp", (preview.bottom - preview.top).value).put("navigationTopDp", navigation.top.value).toString(2))
        compose.onNodeWithText("Share caption card").performScrollTo().assertIsEnabled()
        capture("caption")
    }

    @Test fun largeTextHomeRemainsNavigable() {
        app(fontScale = 2f)
        val measurements = org.json.JSONArray()
        for (label in listOf("Home", "Explore", "Create", "Tools")) {
            compose.onNode(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val result = layout.single()
            assertEquals("$label must stay on one line at 2x text", 1, result.lineCount)
            // MultiParagraph.width is the offered width, which may exceed Text's wrapped size.
            // Check actual line extents against the measured text box instead of that allocation.
            val left = result.getLineLeft(0)
            val right = result.getLineRight(0)
            val bottom = result.getLineBottom(0)
            assertTrue("$label glyph bounds $left..$right exceed ${result.size}", left >= -0.5f && right <= result.size.width + 0.5f)
            assertTrue("$label is clipped vertically", bottom <= result.size.height + 0.5f)
            measurements.put(org.json.JSONObject().put("label", label).put("lineCount", result.lineCount)
                .put("textBoxWidthPx", result.size.width).put("lineLeftPx", left).put("lineRightPx", right)
                .put("textBoxHeightPx", result.size.height).put("lineBottomPx", bottom))
        }
        capture("home-large")
        File("build/outputs/host-ui/large-nav-measurements.json").writeText(measurements.toString(2))
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().assertIsDisplayed()
        capture("create-large")
    }

    @Test fun coldOfflineHomeKeepsAllDestinationsUsable() {
        app()
        compose.onNodeWithText("Bert’s update couldn’t load.").assertIsDisplayed()
        capture("home-offline")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Flappy Bert").assertIsDisplayed()
        capture("explore")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("BERT amount").assertExists()
        capture("holdings")
    }

    @Test fun createSaveOpenAndDeleteIsACompleteOfflineJourney() {
        app()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("A tiny mayor. A giant weekend.")
        compose.onNodeWithText("Lilac", useUnmergedTree = true).performScrollTo().performClick()
        waitForPreview("A tiny mayor. A giant weekend.")
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("View collection").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("View collection").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Open card").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("saved-card-thumbnail", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        capture("saved-collection")
        compose.onNodeWithText("Open card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Share saved card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share saved card").performScrollTo().assertIsEnabled()
        capture("saved-card")
        val persisted = CaptionLibrary(compose.activity).list().single()
        assertEquals("A tiny mayor. A giant weekend.", persisted.caption)
        assertEquals(2, persisted.palette)
        compose.onNodeWithText("Delete card").performScrollTo().performClick()
        compose.onNodeWithText("Keep card").performClick()
        assertEquals(1, CaptionLibrary(compose.activity).list().size)
        compose.onNodeWithText("Delete card").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your first card belongs here.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(CaptionLibrary(compose.activity).list().isEmpty())
    }

    @Test fun captionDraftAndPaletteSurviveSwitchingSections() {
        app()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("Keep my unfinished idea")
        compose.onNodeWithText("Cream", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Keep my unfinished idea").assertExists()
        compose.onNodeWithText("Cream").assertIsSelected()
        waitForPreview("Keep my unfinished idea")
    }

    @Test fun fullCollectionExplainsTheLimitAndKeepsTheDraftThroughRecovery() {
        val store = CaptionLibrary(compose.activity)
        val existing = (1..40).map { index ->
            val card = renderCaption(compose.activity, "Saved card $index", index % 3)
            try { store.save(card) } finally { card.bitmap.recycle() }
        }
        app()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("A new memory for the pack.")
        compose.onNodeWithText("Lilac", useUnmergedTree = true).performScrollTo().performClick()
        waitForPreview("A new memory for the pack.")
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("Collection full", substring = true) or hasText("couldn’t be saved", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        capture("collection-full")
        compose.onNodeWithText("Collection full · 40 cards").assertIsDisplayed()
        assertEquals(existing.toSet(), CaptionLibrary(compose.activity).list().toSet())
        compose.onNodeWithText("Share caption card").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Manage collection").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Saved card 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("40/40 cards · stored on this device").assertIsDisplayed()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("saved-card-thumbnail", useUnmergedTree = true).fetchSemanticsNodes().size == 40 }
        capture("collection-at-capacity")
        compose.onNodeWithText("Saved card 1").performScrollTo().performClick()
        compose.onNodeWithText("Delete card").performScrollTo().performClick()
        compose.onNodeWithText("Keep card").performClick()
        assertEquals(40, store.list().size)
        compose.onNodeWithText("Delete card").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { store.list().size == 39 }
        compose.onNodeWithText("Art", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").assertTextContains("A new memory for the pack.")
        compose.onNodeWithText("Lilac").assertIsSelected()
        waitForPreview("A new memory for the pack.")
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("View collection").fetchSemanticsNodes().isNotEmpty() }
        capture("collection-recovered")
        val cards = store.list()
        assertEquals(40, cards.size)
        assertEquals(existing.drop(1).toSet(), cards.filter { it.caption != "A new memory for the pack." }.toSet())
        assertEquals(2, cards.single { it.caption == "A new memory for the pack." }.palette)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun fullCollectionAutomaticallyRevealsRecoveryAtLargeText() {
        val store = CaptionLibrary(compose.activity)
        val card = renderCaption(compose.activity, "A saved memory.", 0)
        try { repeat(40) { store.save(card) } } finally { card.bitmap.recycle() }
        app(fontScale = 2f)
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        waitForPreview()
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Manage collection").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        capture("collection-full-large")
        val measurements = org.json.JSONArray()
        for (label in listOf("Collection full · 40 cards", "Remove one saved card to make room.", "Manage collection")) {
            val node = compose.onNodeWithText(label, useUnmergedTree = true)
            val bounds = node.getUnclippedBoundsInRoot()
            val viewport = node.onAncestors().filter(hasScrollAction()).onFirst().getBoundsInRoot()
            assertTrue("$label must appear completely without an extra scroll", bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
            measurements.put(measureLabel(label).put("topDp", bounds.top.value).put("bottomDp", bounds.bottom.value)
                .put("viewportTopDp", viewport.top.value).put("viewportBottomDp", viewport.bottom.value))
        }
        compose.onNode(hasText("Manage collection") and hasClickAction()).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText("Share caption card").performScrollTo().assertIsEnabled()
        assertEquals(40, store.list().size)
        File("build/outputs/host-ui/collection-capacity-measurements.json").writeText(measurements.toString(2))
    }

    @Test fun longCaptionPasteStaysEditableAndCannotSaveThePreviousPreview() {
        val longCaption = "Bert brought his hat, a snack, and enough confidence to run this entire town before his afternoon nap."
        val restoration = StateRestorationTester(compose)
        app(restoration = restoration)
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        waitForPreview()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement(longCaption)
        capture("caption-limit")
        compose.onNodeWithText("Your caption").assertTextContains(longCaption)
        compose.onNodeWithText("102/96 · Remove 6 characters to preview, save or share.").assertExists()
        compose.onNodeWithText("Save card").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Share caption card").assertIsNotEnabled()
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").assertTextContains(longCaption)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Your caption").assertTextContains(longCaption)
        compose.onNodeWithText("Save card").assertIsNotEnabled()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("Hat. Snack. Nap. 🐾")
        waitForPreview("Hat. Snack. Nap. 🐾")
        compose.onNodeWithText("Save card").performScrollTo().assertIsEnabled()
        capture("caption-corrected")
    }

    @Test fun captionLimitCountsVisibleCharactersIncludingEmoji() {
        val caption = "🐾".repeat(96)
        app()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement(caption)
        compose.onNodeWithText("Your caption").assertTextContains(caption)
        compose.onNodeWithText("96/96").assertExists()
        waitForPreview(caption)
        capture("caption-emoji")
        compose.onNodeWithText("Save card").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("View collection").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(caption, CaptionLibrary(compose.activity).list().single().caption)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun oversizedPasteExplainsRejectionAndPreservesTheDraftAtLargeText() {
        app(fontScale = 2f)
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("Keep this idea 🐾")
        compose.onNodeWithText("Your caption").performTextReplacement("A".repeat(4097))
        compose.onNodeWithText("Your caption").assertTextContains("Keep this idea 🐾")
        val notice = "Paste a shorter passage. Your caption is unchanged."
        compose.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
        capture("caption-paste-large")
        val noticeNode = compose.onNodeWithText(notice, useUnmergedTree = true)
        val bounds = noticeNode.getUnclippedBoundsInRoot()
        val viewport = noticeNode.onAncestors().filter(hasScrollAction()).onFirst().getBoundsInRoot()
        assertTrue("The whole notice must stay inside the scrolling viewport", bounds.bottom <= viewport.bottom && bounds.top >= viewport.top)
        File("build/outputs/host-ui/caption-input-measurements.json").writeText(measureLabel(notice)
            .put("noticeTopDp", bounds.top.value).put("noticeBottomDp", bounds.bottom.value)
            .put("viewportTopDp", viewport.top.value).put("viewportBottomDp", viewport.bottom.value).toString(2))
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("good\r\n\tboy 🐾")
        compose.onNodeWithText(notice).assertDoesNotExist()
        compose.onNodeWithText("Your caption").assertTextContains("good boy 🐾")
        waitForPreview("good boy 🐾")
    }

    @Test fun longDispatchCanBeReadWithoutLosingNavigation() {
        val dispatch = "A long dispatch for the pack. ".repeat(20)
        app(ActivityState.Available(BERTActivity(1_789_000_000_000, dispatch, null, null)))
        compose.onNodeWithText("Read full update").performScrollTo().performClick()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(dispatch).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse("Expanded update must expose its full content", layout.single().hasVisualOverflow)
        assertTrue(layout.single().lineCount > 4)
        compose.onNodeWithText("Read less").performScrollTo().performClick()
        compose.onNodeWithText("Tools", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun sectionsStayReachableAfterScrollingAtLargeText() {
        app(fontScale = 2f)
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Share caption card").performScrollTo()
        compose.onNodeWithText("Saved", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Personalize", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Lock screen only").performScrollTo()
        compose.onNodeWithText("Art", useUnmergedTree = true).assertIsDisplayed()
        capture("personalize-large-scrolled")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Copy mint").performScrollTo()
        compose.onNodeWithText("Holdings", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Save holdings").performScrollTo()
        compose.onNodeWithText("Market", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun savedStateRestoresDestinationSectionAndUnfinishedCaption() {
        val restoration = StateRestorationTester(compose)
        app(restoration = restoration)
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your caption").performScrollTo().performTextReplacement("Back soon. Keep my hat.")
        compose.onNodeWithText("Lilac", useUnmergedTree = true).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Art").assertIsSelected()
        compose.onNodeWithText("Back soon. Keep my hat.").assertExists()
        compose.onNodeWithText("Lilac").assertIsSelected()
        waitForPreview("Back soon. Keep my hat.")
        compose.onNodeWithText("Saved", useUnmergedTree = true).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Saved").assertIsSelected()
        compose.onNodeWithText("Art", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Back soon. Keep my hat.").assertExists()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun compactScreenAtLargeTextKeepsControlsReadable() {
        app(fontScale = 2f)
        capture("compact-home-large")
        val measurements = org.json.JSONArray()
        for (label in listOf("Home", "Explore", "Create", "Tools")) {
            compose.onNode(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            measurements.put(measureLabel(label, singleLine = true))
        }
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Share caption card").performScrollTo().assertIsDisplayed()
        for (label in listOf("Art", "Saved", "Personalize")) {
            compose.onNode(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp)
            measurements.put(measureLabel(label, singleLine = true))
        }
        capture("compact-create-large")
        compose.onNodeWithText("Personalize", useUnmergedTree = true).performClick()
        for (label in listOf("Apply both", "Home only", "Lock screen only", "Compact", "Market")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText(label) and hasClickAction()).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            measurements.put(measureLabel(label, singleLine = label != "Lock screen only"))
            if (label == "Lock screen only") capture("compact-wallpaper-large")
        }
        capture("compact-widgets-large")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Save holdings").performScrollTo().assertIsDisplayed()
        measurements.put(measureLabel("Save holdings"))
        capture("compact-holdings-large")
        File("build/outputs/host-ui/compact-measurements.json").writeText(measurements.toString(2))
    }

    private fun measureLabel(label: String, singleLine: Boolean = false): org.json.JSONObject {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val result = layouts.single()
        if (singleLine) assertEquals("$label must stay on one line at 320 dp / 2x text", 1, result.lineCount)
        for (line in 0 until result.lineCount) {
            assertTrue("$label is clipped horizontally", result.getLineLeft(line) >= -0.5f && result.getLineRight(line) <= result.size.width + 0.5f)
            assertTrue("$label is clipped vertically", result.getLineBottom(line) <= result.size.height + 0.5f)
        }
        return org.json.JSONObject().put("label", label).put("lineCount", result.lineCount)
            .put("widthPx", result.size.width).put("heightPx", result.size.height)
            .put("maxLineRightPx", (0 until result.lineCount).maxOf { result.getLineRight(it) })
            .put("lastLineBottomPx", result.getLineBottom(result.lineCount - 1))
    }

    private fun waitForPreview(caption: String? = null) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Caption card preview:" + (caption?.let { " $it" } ?: ""), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
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
