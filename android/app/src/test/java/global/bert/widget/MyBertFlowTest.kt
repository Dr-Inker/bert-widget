package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import global.bert.widget.data.*
import global.bert.widget.theme.BERTThemeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** My Bert end to end on the host: paste, list, pick, use it on Home, Create and the theme, then forget it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MyBertFlowTest {
    @Before @After fun freshShareCache() = forgetFileProviderPaths()

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_789_000_000_000

    @Before fun clean() {
        runCatching { androidx.work.WorkManager.initialize(compose.activity.applicationContext, androidx.work.Configuration.Builder().build()) }
        MyBertStore(compose.activity).forget()
        MyBertRepository.clearMemory()
        BERTHomeVisits(compose.activity).meetBertDismissed = true
    }

    private fun app(net: MyBertNet = MyBertFixtures.FakeNet()): MyBertFixtures.FakeNet? {
        val repository = MyBertRepository(compose.activity, net)
        compose.setContent {
            CompositionLocalProvider(LocalMyBertRepository provides repository) {
                BERTTheme {
                    BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Available(BERTActivity(now - 60_000, "woofmornin. the mayor is in.", "content", null)),
                        emptyList(), BERTPosition(), now, false, false, {}, {}, {})
                }
            }
        }
        return net as? MyBertFixtures.FakeNet
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun openMyBertFromPersonalise() {
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Personalise").performClick()
        compose.onNodeWithTag("mybert-row").performScrollTo().performClick()
        waitFor("My Bert")
    }

    @Test fun pasteListPickApplyAndForget() {
        val net = app()!!
        openMyBertFromPersonalise()
        compose.onNodeWithText(MY_BERT_PRIVACY).assertIsDisplayed()
        capture("mybert-start")

        // A bad address never leaves the phone.
        compose.onNodeWithTag("mybert-address").performTextInput("0xNotASolanaAddress")
        compose.onNodeWithText("Find my Berts").performClick()
        waitFor("That isn’t a Solana wallet address", substring = true)
        assertTrue(net.requests.isEmpty())

        compose.onNodeWithTag("mybert-address").performTextReplacement("  ${MyBertFixtures.STAKER}\n")
        compose.onNodeWithText("Find my Berts").performClick()
        waitFor("3 Berts in this wallet")
        assertEquals("one lookup per tap", 3, net.requests.count { MyBertSources.IMAGE_HOST !in it })
        assertTrue(net.requests.none { "berthalla" in it })
        compose.onNodeWithContentDescription("Bert #1506, Listed").assertExists()
        compose.onNodeWithContentDescription("Bert #2206, Staked").assertExists()
        compose.onNodeWithContentDescription("Bert #3238, Staked").assertExists()
        try { compose.waitUntil(15_000) { net.requests.count { it.contains("img-width") } >= 3 } }
        catch (e: Throwable) { throw AssertionError("requests: ${net.requests}", e) }
        capture("mybert-list")

        compose.onNodeWithTag("mybert-2206").performScrollTo().performClick()
        compose.onNodeWithTag("mybert-2206").assertIsSelected()
        compose.onNodeWithText("Forest background. Staked").assertExists()
        capture("mybert-preview")
        compose.onNodeWithTag("mybert-use").performScrollTo().performClick()
        compose.waitUntil(15_000) { MyBertStore(compose.activity).chosen() != null }
        waitFor("Bert #2206 is now your Bert", substring = true)
        assertEquals(2206, MyBertStore(compose.activity).chosen()!!.bert.number)
        compose.onNodeWithText("Back").performScrollTo().performClick()

        // Personalise: the row names the Bert, and "Match my Bert" is offered beside the packs, off until chosen.
        waitFor("Bert #2206 is your Bert", substring = true)
        compose.onNodeWithTag("mybert-row").performScrollTo()
        capture("mybert-personalise")
        // The packs row scrolls sideways; bring the studio's actions up so the cards above them are on screen.
        compose.onNodeWithText("Previewing", substring = true).performScrollTo()
        compose.onNodeWithTag("theme-match-my-bert").performClick()
        compose.onNodeWithTag("theme-match-my-bert").assertIsSelected()
        assertFalse(BERTThemeStore(compose.activity).matchMyBert)
        compose.onNodeWithText("Use palette on widgets").performScrollTo().performClick()
        assertTrue(BERTThemeStore(compose.activity).matchMyBert)
        waitFor("Wallpapers come with the packs", substring = true)
        compose.onNodeWithText("Previewing", substring = true).performScrollTo()
        capture("mybert-theme")

        // Create: the holder's Bert is an art option and renders on the card.
        compose.onNodeWithText("Art").performClick()
        compose.onNodeWithContentDescription("My Bert #2206").performScrollTo().performClick()
        compose.onNodeWithContentDescription("My Bert #2206").assertIsSelected()
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Caption card preview:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("My Bert #2206").performScrollTo()
        capture("mybert-create")

        // Home: the garden Bert stays; the holder's Bert sits in Today's header.
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Your Bert, Bert #2206. Opens My Bert.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("berts-place").assertExists()
        capture("mybert-home")

        // The chip opens My Bert; forgetting removes everything and brings classic Bert back.
        compose.onNodeWithContentDescription("Your Bert, Bert #2206. Opens My Bert.").performClick()
        waitFor("Forget this address")
        compose.onNodeWithText("Forget this address").performScrollTo().performClick()
        waitFor("Address forgotten", substring = true)
        val store = MyBertStore(compose.activity)
        assertNull(store.wallet()); assertNull(store.chosen())
        assertFalse(store.imageFile.exists()); assertFalse(store.thumbnails.exists())
        assertFalse(BERTThemeStore(compose.activity).matchMyBert)
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onAllNodesWithContentDescription("Your Bert, Bert #2206. Opens My Bert.").assertCountEquals(0)
    }

    /** The card itself, at full size, for review: the holder's Bert on a note and a poster. */
    @Test fun cardsRenderTheHoldersBert() {
        val repository = MyBertRepository(compose.activity, MyBertFixtures.FakeNet())
        kotlinx.coroutines.runBlocking { repository.choose(MyBert(MyBertFixtures.STAKED_2206, 2206, "Forest", staked = true)) }
        for (layout in CardLayout.entries) {
            val card = renderCaption(compose.activity, "woofmornin. the mayor is in.", 0, CardStyle(CardArt.MY_BERT, layout, CardFormat.SQUARE))
            assertEquals(1080, card.bitmap.width)
            // The art is the holder's Bert: a pixel inside the art area matches the kept picture, not the stock photo.
            File("build/outputs/host-ui/mybert-card-${layout.name.lowercase()}.png").apply { parentFile?.mkdirs() }.outputStream()
                .use { card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val thumbnail = artThumbnail(compose.activity, CardArt.MY_BERT, 216)
        assertEquals(216, thumbnail.width)
        MyBertStore(compose.activity).clearChosen()
        assertTrue(runCatching { renderCaption(compose.activity, "x", 0, CardStyle(CardArt.MY_BERT)) }.isFailure)
    }

    @Test fun goBackToClassicKeepsTheAddress() {
        app()
        openMyBertFromPersonalise()
        compose.onNodeWithTag("mybert-address").performTextInput(MyBertFixtures.STAKER)
        compose.onNodeWithText("Find my Berts").performClick()
        waitFor("3 Berts in this wallet")
        compose.onNodeWithTag("mybert-1506").performScrollTo().performClick()
        compose.onNodeWithTag("mybert-use").performScrollTo().performClick()
        waitFor("Go back to classic Bert")
        compose.onNodeWithText("Go back to classic Bert").performScrollTo().performClick()
        waitFor("Classic Bert is back.")
        assertNull(MyBertStore(compose.activity).chosen())
        assertEquals(MyBertFixtures.STAKER, MyBertStore(compose.activity).wallet())
    }

    @Test fun emptyWalletSaysSoWithoutSendingAnyoneShopping() {
        app(MyBertFixtures.FakeNet(magicEden = { "[]".toByteArray() }, positions = { "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[]}".toByteArray() }))
        openMyBertFromPersonalise()
        compose.onNodeWithTag("mybert-address").performTextInput(MyBertFixtures.LISTER)
        compose.onNodeWithText("Find my Berts").performClick()
        waitFor("No Berts in this wallet")
        compose.onNodeWithText("Check that it’s the right address", substring = true).performScrollTo().assertIsDisplayed()
        // Play's blockchain-content rules: no nudge towards a marketplace for people who hold none.
        compose.onNodeWithText("The Bert collection on Magic Eden").assertDoesNotExist()
        capture("mybert-empty")
    }

    @Test fun offlineAndBusyAreSaidPlainly() {
        app(MyBertFixtures.FakeNet(magicEden = { throw java.net.UnknownHostException() }, positions = { throw java.net.ConnectException() }))
        openMyBertFromPersonalise()
        compose.onNodeWithTag("mybert-address").performTextInput(MyBertFixtures.LISTER)
        compose.onNodeWithText("Find my Berts").performClick()
        waitFor("You’re offline", substring = true)
        capture("mybert-offline")
    }

    @Test fun rateLimitedSaysTryLater() {
        app(MyBertFixtures.FakeNet(magicEden = { throw MyBertHttpException(429) }, positions = { throw MyBertHttpException(429) }))
        openMyBertFromPersonalise()
        compose.onNodeWithTag("mybert-address").performTextInput(MyBertFixtures.LISTER)
        compose.onNodeWithText("Find my Berts").performClick()
        waitFor("busy. Try again in a minute", substring = true)
    }
}
