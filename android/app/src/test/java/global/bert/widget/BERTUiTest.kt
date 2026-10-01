package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    private fun app(activity: ActivityState = ActivityState.Unavailable, fontScale: Float = 1f, restoration: StateRestorationTester? = null,
                    quote: QuoteState = QuoteState.Unavailable("No connection"), history: List<BERTPriceSample> = emptyList(),
                    initialPosition: BERTPosition = BERTPosition(), holdingsStore: BERTHoldingsStore? = null) {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
              BERTTheme {
                var position by remember { mutableStateOf(holdingsStore?.loadPosition() ?: initialPosition) }
                BERTApp(quote, activity, history, position,
                    1_789_000_000_000, false, false, {}, {}, { holdingsStore?.savePosition(it); position = it })
              }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    // Deterministic review fixtures, never current market quotes or the user's holdings.
    private fun marketFixture() = QuoteState.Available(BERTQuote(
        0.0042, -12.5, 123_456_789.0, 2_345_678.0, 345_678.0, "fresh",
        1_789_000_000_000 - 42 * 60_000, "dexscreener", "raydium",
        "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
    ), updateDelayed = true)

    private fun marketHistoryFixture() = listOf(1_200L to 0.008, 300L to 0.003, 120L to 0.005, 50L to 0.004, 42L to 0.0042)
        .map { (minutes, price) -> BERTPriceSample(1_789_000_000_000 - minutes * 60_000, price) }

    @Test fun populatedToolsKeepPricesAndControlsReadable() = checkPopulatedTools()

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun compactPinnedNavigationKeepsQuoteAgeVisible() {
        app(fontScale = 2f, quote = marketFixture(), history = marketHistoryFixture())
        val measurements = org.json.JSONObject()
        fun recordSection(name: String, labels: List<String>, content: SemanticsMatcher) {
            val viewport = compose.onNode(content).onAncestors().filter(hasScrollAction()).onFirst().getBoundsInRoot()
            val tabs = org.json.JSONArray()
            for (label in labels) {
                val node = compose.onNode(hasText(label) and hasClickAction())
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().touchBoundsInRoot
                val density = compose.activity.resources.displayMetrics.density
                val text = measureLabel(label, singleLine = true)
                assertEquals("Section labels must retain their 16sp size", 16.0, text.getDouble("fontSizeSp"), 0.01)
                assertTrue("Section tabs need 48dp targets", bounds.height / density >= 48 && bounds.width / density >= 48)
                assertTrue("The full section target must fit above content", bounds.top / density >= 0 && bounds.bottom / density <= viewport.top.value)
                assertTrue("Section targets must fit across the screen", bounds.left / density >= 0 && bounds.right / density <= 320)
                tabs.put(text.put("touchTopDp", bounds.top / density).put("touchBottomDp", bounds.bottom / density)
                    .put("touchLeftDp", bounds.left / density).put("touchRightDp", bounds.right / density))
            }
            measurements.put(name, org.json.JSONObject().put("viewportTopDp", viewport.top.value)
                .put("viewportBottomDp", viewport.bottom.value).put("viewportHeightDp", (viewport.bottom - viewport.top).value).put("tabs", tabs))
        }
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        waitForPreview()
        recordSection("create", listOf("Art", "Saved", "Personalize"), hasContentDescription("Caption card preview:", substring = true))
        capture("pinned-create-large")
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        val freshness = "Delayed · Updated 42m ago"
        recordSection("tools", listOf("Market", "Holdings"), hasText(freshness))
        val viewport = compose.onNodeWithText(freshness).onAncestors().filter(hasScrollAction()).onFirst().getBoundsInRoot()
        for (label in listOf("$0.0042", freshness)) {
            val bounds = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            measurements.put(label, measureLabel(label).put("topDp", bounds.top.value).put("bottomDp", bounds.bottom.value))
        }
        capture("pinned-market-large")
        File("build/outputs/host-ui/pinned-navigation.json").writeText(measurements.toString(2))
        for (label in listOf("$0.0042", freshness)) {
            val bounds = measurements.getJSONObject(label)
            assertTrue("Price and complete quote age must be visible together on first entry", bounds.getDouble("topDp") >= viewport.top.value && bounds.getDouble("bottomDp") <= viewport.bottom.value)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun populatedToolsKeepPricesAndControlsReadableAtLargeText() = checkPopulatedTools(fontScale = 2f)

    private fun checkPopulatedTools(fontScale: Float = 1f) {
        app(fontScale = fontScale, quote = marketFixture(), history = marketHistoryFixture(), initialPosition = BERTPosition(250_000.0, 1250.0))
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        val suffix = if (fontScale > 1f) "-large" else ""
        val measurements = org.json.JSONArray()
        capture("market-populated$suffix")
        for (label in listOf("$0.0042", "Delayed · Updated 42m ago")) {
            compose.onNodeWithText(label).performScrollTo()
            measurements.put(measureLabel(label))
        }
        compose.onNodeWithText("Couldn't refresh. Showing the last saved quote.").assertExists()
        compose.onNode(hasContentDescription("5 prices over", substring = true)).performScrollTo()
        capture("market-ranges$suffix")
        for (label in listOf("1H", "6H", "24H")) {
            val target = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().touchBoundsInRoot
            val density = compose.activity.resources.displayMetrics.density
            measurements.put(measureLabel(label).put("touchHeightDp", target.height / density).put("touchWidthDp", target.width / density))
        }
        compose.onNodeWithText("Market cap").performScrollTo()
        capture("market-metrics-start$suffix")
        compose.onNodeWithText("Liquidity").performScrollTo()
        capture("market-metrics$suffix")
        for (label in listOf("Market cap", "$123.5M", "24h volume", "$2.3M", "Liquidity", "$345.7K")) measurements.put(measureLabel(label))
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("$1,050.00").performScrollTo()
        capture("holdings-populated$suffix")
        measurements.put(measureLabel("$1,050.00"))
        compose.onNodeWithText("Unrealized loss · -$200.00").performScrollTo()
        capture("holdings-loss$suffix")
        compose.onNodeWithText("-16.00% return on entered cost").assertExists()
        compose.onNodeWithText("Delayed · Updated 42m ago").assertExists()
        compose.onNodeWithText("Edit holdings").performScrollTo().performClick()
        compose.onNodeWithText("BERT amount").performScrollTo().performTextReplacement("25000000")
        compose.onNodeWithText("Total cost in USD (optional)").performScrollTo().performTextReplacement("125000")
        compose.onNodeWithText("Save holdings").performScrollTo().performClick()
        compose.onNodeWithText("$105,000.00").performScrollTo()
        capture("holdings-larger-value$suffix")
        measurements.put(measureLabel("$105,000.00"))
        compose.onNodeWithText("Edit holdings").performScrollTo().performClick()
        compose.onNodeWithText("BERT amount").performScrollTo().performTextReplacement("100000000000000000000")
        compose.onNodeWithText("Total cost in USD (optional)").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Save holdings").performScrollTo().performClick()
        val extremeValue = "$420,000,000,000,000,000.00"
        compose.onNodeWithText(extremeValue).performScrollTo()
        capture("holdings-extreme-value$suffix")
        val extreme = measureLabel(extremeValue)
        measurements.put(extreme)
        File("build/outputs/host-ui/populated-tools$suffix.json").writeText(measurements.toString(2))
        assertTrue("Long amounts must retain a readable font", extreme.getDouble("fontSizeSp") >= 18)
        assertEquals("Every currency digit must be laid out", extremeValue.length, extreme.getInt("lastLineEnd"))
        assertFalse("Currency digits must never be ellipsized", extreme.getBoolean("ellipsized"))
        for (index in 0 until measurements.length()) {
            val item = measurements.getJSONObject(index)
            if (item.has("touchHeightDp")) assertTrue("${item.getString("label")} needs a 48dp touch target", item.getDouble("touchHeightDp") >= 48 && item.getDouble("touchWidthDp") >= 48)
            if (item.getString("label") !in listOf("Delayed · Updated 42m ago", extremeValue))
                assertEquals("${item.getString("label")} must remain an intact label or number", 1, item.getInt("lineCount"))
        }
    }

    @Test fun marketRangesUseTheirOwnObservationsAndKeepSelection() {
        val restoration = StateRestorationTester(compose)
        app(quote = marketFixture(), history = marketHistoryFixture(), restoration = restoration)
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        fun checkChart(label: String, summary: String) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.onNodeWithText(label).assertIsSelected()
            compose.onNodeWithContentDescription(summary).assertExists()
        }
        checkChart("24H", "5 prices over the selected 24H window. Low $0.003, high $0.008. Gaps over 30 minutes are not connected.")
        checkChart("6H", "4 prices over the selected 6H window. Low $0.003, high $0.005. Gaps over 30 minutes are not connected.")
        checkChart("1H", "2 prices over the selected 1H window. Low $0.004, high $0.0042. Gaps over 30 minutes are not connected.")
        capture("market-one-hour")
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Market", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1H").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1H").assertIsSelected()
    }

    @Test fun holdingsEditsValidateCancelSaveAndConfirmRemoval() {
        val store = BERTHoldingsStore(compose.activity)
        val original = BERTPosition(250_000.0, 1250.0)
        store.savePosition(original)
        val restoration = StateRestorationTester(compose)
        app(holdingsStore = store, restoration = restoration)
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Holdings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Edit holdings").performScrollTo().performClick()
        compose.onNodeWithText("BERT amount").performScrollTo().performTextReplacement("1,5")
        compose.onNodeWithText("Save holdings").performScrollTo().performClick()
        compose.onNodeWithText("Enter zero or more, using a decimal point.").assertExists()
        assertEquals(original, store.loadPosition())
        compose.onNodeWithText("BERT amount").performScrollTo().performTextReplacement("300,000.5")
        compose.onNodeWithText("Total cost in USD (optional)").performScrollTo().performTextReplacement("1200.00")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(original, store.loadPosition())
        compose.onNodeWithText("Edit holdings").performScrollTo().performClick()
        compose.onNodeWithText("BERT amount").assertTextContains("250000")
        compose.onNodeWithText("Total cost in USD (optional)").assertTextContains("1250")
        compose.onNodeWithText("BERT amount").performScrollTo().performTextReplacement("300,000.5")
        compose.onNodeWithText("Total cost in USD (optional)").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Save holdings").performScrollTo().performClick()
        assertEquals(BERTPosition(300_000.5), BERTHoldingsStore(compose.activity).loadPosition())
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("300,000.5 BERT").assertExists()
        compose.onNodeWithText("Remove holdings").performScrollTo().performClick()
        compose.onNodeWithText("Keep holdings").performClick()
        assertEquals(BERTPosition(300_000.5), store.loadPosition())
        compose.onNodeWithText("Remove holdings").performScrollTo().performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        assertEquals(BERTPosition(), store.loadPosition())
        compose.onNodeWithText("BERT amount").assertExists()
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

    // Landing-page captures: a fresh quote and a recorded 24-hour price curve (review fixture, not live data).
    @Test fun marketingCapturesShowHealthyMarketAndExplore() {
        val now = 1_789_000_000_000
        val fixture = org.json.JSONObject(requireNotNull(javaClass.classLoader).getResource("history-sample.json").readText())
        val points = fixture.getJSONArray("minutesBeforeNow")
        val history = (0 until points.length()).map { i ->
            val point = points.getJSONArray(i)
            BERTPriceSample(now - point.getLong(0) * 60_000, point.getDouble(1))
        }
        val quote = QuoteState.Available(BERTQuote(
            history.last().priceUsd, 1.63, 15_067_640.0, 136_801.0, 1_178_452.0, "fresh", now - 30_000, "dexscreener", "raydium",
            "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY",
        ))
        app(quote = quote, history = history)
        compose.onNodeWithText("Tools", useUnmergedTree = true).performClick()
        compose.onNodeWithText("24h ago").assertExists()
        compose.onNodeWithText("Couldn't refresh. Showing the last saved quote.").assertDoesNotExist()
        capture("marketing-tools")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("The Lost Trail").assertIsDisplayed()
        capture("marketing-explore")
    }

    @Test fun coldOfflineHomeKeepsAllDestinationsUsable() {
        app()
        compose.onNodeWithText("Bert’s update couldn’t load.").assertIsDisplayed()
        capture("home-offline")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("The Lost Trail").assertIsDisplayed()
        capture("explore")
        compose.onNodeWithText("Play here · works offline").performScrollTo().performClick()
        val gameIntent = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(LostTrailActivity::class.java.name, gameIntent.component?.className)
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

    @Test fun savedCardBackRestoresTheCollectionPosition() = checkSavedCardNavigation()

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun savedCardBackRestoresTheCollectionPositionAtLargeText() = checkSavedCardNavigation(fontScale = 2f)

    private fun checkSavedCardNavigation(fontScale: Float = 1f) {
        val store = CaptionLibrary(compose.activity)
        repeat(12) { index ->
            val card = renderCaption(compose.activity, "Pack memory ${index + 1}", index % 3)
            try { store.save(card) } finally { card.bitmap.recycle() }
        }
        val restoration = StateRestorationTester(compose)
        app(fontScale = fontScale, restoration = restoration)
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Saved", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Pack memory 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pack memory 2").performScrollTo()
        val before = compose.onNodeWithText("Pack memory 2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val suffix = if (fontScale > 1f) "-large" else ""
        capture("collection-position-before$suffix")
        compose.onNodeWithText("Pack memory 2").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Saved caption card: Pack memory 2").fetchSemanticsNodes().isNotEmpty() }
        capture("collection-detail-open$suffix")
        val artwork = compose.onNodeWithContentDescription("Saved caption card: Pack memory 2")
        val picture = artwork.getUnclippedBoundsInRoot()
        val viewport = artwork.onAncestors().filter(hasScrollAction()).onFirst().getBoundsInRoot()
        val title = compose.onNodeWithText("Made by you.", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onNodeWithText("Back to collection").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your collection").fetchSemanticsNodes().isNotEmpty() }
        capture("collection-position-return$suffix")
        val afterButton = compose.onNodeWithText("Pack memory 2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Pack memory 2").fetchSemanticsNodes().isNotEmpty() }
        val afterListRestoration = compose.onNodeWithText("Pack memory 2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onNodeWithText("Pack memory 2").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Saved caption card: Pack memory 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delete card").performScrollTo()
        val detailBeforeRestoration = compose.onNodeWithContentDescription("Saved caption card: Pack memory 2").getUnclippedBoundsInRoot()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Saved caption card: Pack memory 2").fetchSemanticsNodes().isNotEmpty() }
        val detailAfterRestoration = compose.onNodeWithContentDescription("Saved caption card: Pack memory 2").getUnclippedBoundsInRoot()
        capture("collection-detail-restored$suffix")
        compose.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Saved caption card: Pack memory 2").fetchSemanticsNodes().isNotEmpty() }
        val detailAfterTabSwitch = compose.onNodeWithContentDescription("Saved caption card: Pack memory 2").getUnclippedBoundsInRoot()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Saved").assertIsSelected()
        compose.onNodeWithText("Your collection").assertExists()
        val afterBack = compose.onNodeWithText("Pack memory 2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val measurements = org.json.JSONObject().put("fontScale", fontScale)
            .put("anchorBeforeTopDp", before.top.value).put("anchorAfterButtonTopDp", afterButton.top.value)
            .put("anchorAfterBackTopDp", afterBack.top.value).put("detailTitleTopDp", title.top.value)
            .put("anchorAfterListRestorationTopDp", afterListRestoration.top.value)
            .put("scrolledDetailBeforeRestorationTopDp", detailBeforeRestoration.top.value)
            .put("scrolledDetailAfterRestorationTopDp", detailAfterRestoration.top.value)
            .put("scrolledDetailAfterTabSwitchTopDp", detailAfterTabSwitch.top.value)
            .put("artworkTopDp", picture.top.value).put("artworkBottomDp", picture.bottom.value)
            .put("viewportTopDp", viewport.top.value).put("viewportBottomDp", viewport.bottom.value)
        File("build/outputs/host-ui/collection-navigation$suffix.json").writeText(measurements.toString(2))
        assertTrue("Opened card must start with its title visible", title.top >= viewport.top && title.bottom <= viewport.bottom)
        if (fontScale == 1f) assertTrue("The complete artwork must fit on first open", picture.top >= viewport.top && picture.bottom <= viewport.bottom)
        assertEquals("Back to collection must preserve the same card position", before.top.value, afterButton.top.value, 0.5f)
        assertEquals("Android Back must preserve the same card position", before.top.value, afterBack.top.value, 0.5f)
        assertEquals("Recreating the collection must preserve its position", before.top.value, afterListRestoration.top.value, 0.5f)
        assertEquals("Recreating a scrolled detail must preserve its position", detailBeforeRestoration.top.value, detailAfterRestoration.top.value, 0.5f)
        assertEquals("Switching tabs must preserve detail position", detailBeforeRestoration.top.value, detailAfterTabSwitch.top.value, 0.5f)
        assertEquals(12, store.list().size)
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
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Saved caption card: Saved card 1").fetchSemanticsNodes().isNotEmpty() }
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
            .put("density", result.layoutInput.density.density).put("fontScaleSetting", result.layoutInput.density.fontScale)
            .put("fontSizeSp", result.layoutInput.style.fontSize.value)
            .put("fontSizePx", with(result.layoutInput.density) { result.layoutInput.style.fontSize.toPx() })
            .put("reference14spPx", with(result.layoutInput.density) { 14.sp.toPx() })
            .put("reference38spPx", with(result.layoutInput.density) { 38.sp.toPx() })
            .put("lastLineEnd", result.getLineEnd(result.lineCount - 1))
            .put("ellipsized", (0 until result.lineCount).any { result.isLineEllipsized(it) })
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
