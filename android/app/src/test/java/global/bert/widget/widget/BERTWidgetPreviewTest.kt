package global.bert.widget.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.GlanceAppWidget
import global.bert.widget.data.BERTHoldingsStore
import global.bert.widget.theme.BERTThemeStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import javax.imageio.ImageIO

/**
 * Launcher-picker previews, made by rendering the real widgets (the same host harness as [BERTWidgetRenderTest]) over
 * invented fixture data: no live quote, a round modest holding and a made-up feed. The widgets print the time they were
 * rendered at ("a minute ago"), the one thing that differs between runs. Regenerate them with
 *
 *     BERT_WRITE_PREVIEWS=1 ./gradlew :app:testDebugUnitTest --tests '*BERTWidgetPreviewTest*'
 *
 * Without the variable the test only renders into build/outputs/host-ui and checks that the committed PNGs exist and
 * have exactly the dimensions the widgets render at, so a size change without a regenerate fails the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BERTWidgetPreviewTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    /**
     * The fixture is "a minute ago" on the real clock (Robolectric cannot freeze System.currentTimeMillis, and a frozen past
     * date would make the widgets say DELAYED). The only run-to-run difference is therefore the time of day the widgets print.
     */
    private val NOW = System.currentTimeMillis()
    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()

    private class Spec(val resource: String, val widget: () -> GlanceAppWidget, val widthDp: Int, val heightDp: Int)

    @Before fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        BERTThemeStore(context).let { it.save(global.bert.widget.theme.BERTThemePack.DEFAULT) }
        seedFixtures()
    }

    @After fun restore() { TimeZone.setDefault(savedZone); Locale.setDefault(savedLocale) }

    private fun seedFixtures() {
        val asset = JSONObject().put("chain", "solana").put("mint", "HgBRWfYxEfvPhtqkaeymCQtHCrKE46qQ43pKe8HCpump")
        // Invented numbers on purpose: round, plausible and not anyone's quote.
        val envelope = JSONObject().put("asset", asset)
            .put("quote", JSONObject().put("priceUsd", 0.0150).put("change24hPct", 2.5).put("marketCapUsd", 15_000_000.0)
                .put("volume24hUsd", 120_000.0).put("liquidityUsd", 1_200_000.0))
            .put("source", JSONObject().put("name", "dexscreener").put("pairAddress", "BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY").put("dex", "raydium")
                .put("pairUrl", "https://dexscreener.com/solana/bmsze6tkzyskys1patpkryyazgdxwfxdia4buvlg9agy").put("observedAt", Instant.ofEpochMilli(NOW - 60_000).toString()))
            .put("meta", JSONObject().put("freshness", "fresh"))
        context.getSharedPreferences("bert_quote", Context.MODE_PRIVATE).edit().putString("last_valid_quote", envelope.toString()).commit()
        BERTHoldingsStore(context).save(250_000.0)
        val open = NOW / 1000 - NOW / 1000 % 300
        val points = JSONArray().apply { for (i in 287 downTo 0) put(JSONArray().put(open - i * 300L).put(0.0146 + 0.00012 * Math.sin(i / 12.0) + 0.00018 * Math.sin(i / 41.0 + 1.0) + 0.0004 * (288 - i) / 288.0)) }
        context.getSharedPreferences("bert_market_history", Context.MODE_PRIVATE).edit().putString("last_valid_history", JSONObject().put("asset", asset)
            .put("source", JSONObject().put("name", "geckoterminal").put("intervalSeconds", 300)).put("points", points).toString()).commit()
        val status = JSONObject().put("updated_at", NOW / 1000 - 120).put("mood", "content")
            .put("latest_post", "the blanket settled heavier on its own. like the season finally taught it how to hold still.")
            .put("flappy", JSONObject().put("name", "Weekend Dash").put("ends_at", Instant.ofEpochMilli(NOW + 30 * 3_600_000L).toString()).put("status", "live").put("pool", 250)
                .put("top", JSONArray().put(JSONObject().put("rank", 1).put("name", "Biscuit").put("score", 143).put("prize", 100))))
        context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit().putString("last_valid_activity", status.toString()).commit()
    }

    @Test fun previewsAreTheRealWidgetsOverFixtureDataAndTheCommittedPngsMatch() {
        val write = System.getenv("BERT_WRITE_PREVIEWS") == "1"
        val density = context.resources.displayMetrics.density
        val problems = mutableListOf<String>()
        for (spec in SPECS) {
            val bitmap = renderWidgetOnHost(context, spec.widget(), spec.widthDp, spec.heightDp).bitmap
            assertEquals("${spec.resource} width", (spec.widthDp * density).toInt(), bitmap.width)
            File("build/outputs/host-ui/${spec.resource}.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val committed = File(RES_DIR, "${spec.resource}.png")
            if (write) committed.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!committed.isFile) { problems += "${committed.path} is missing; run with BERT_WRITE_PREVIEWS=1"; continue }
            val size = ImageIO.read(committed).let { it.width to it.height }
            if (size != bitmap.width to bitmap.height) problems += "${committed.name} is ${size.first}x${size.second}, the widget renders ${bitmap.width}x${bitmap.height}; regenerate"
            if (committed.length() !in 8_000L..900_000L) problems += "${committed.name} is ${committed.length()} bytes, outside 8 KB to 900 KB"
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    /** Each widget's picker entry points at its own committed preview image and no longer at the loading placeholder. */
    @Test fun everyWidgetInfoNamesItsPreviewImage() {
        val expected = mapOf("bert_widget_info" to "widget_preview_compact", "bert_market_widget_info" to "widget_preview_market", "bert_daily_widget_info" to "widget_preview_bert")
        for ((info, drawable) in expected) {
            val xml = File("src/main/res/xml/$info.xml").readText()
            assertTrue("$info previewImage", xml.contains("android:previewImage=\"@drawable/$drawable\""))
            assertTrue("$info must not keep the loading layout as its preview (it would hide the image on Android 12+)", !xml.contains("android:previewLayout"))
            assertTrue(File(RES_DIR, "$drawable.png").isFile)
        }
    }

    companion object {
        const val RES_DIR = "src/main/res/drawable-nodpi"
        /** Representative launcher sizes: the 2x2 and 4x2 cells of a phone (BERTWidgetRenderTest pins these sizes). */
        private val SPECS = listOf(
            Spec("widget_preview_compact", { BERTWidget() }, 180, 180),
            Spec("widget_preview_market", { BERTMarketWidget() }, 340, 180),
            Spec("widget_preview_bert", { BERTDailyWidget() }, 380, 190),
        )
    }
}
