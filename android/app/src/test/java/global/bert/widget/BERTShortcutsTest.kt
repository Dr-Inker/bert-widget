package global.bert.widget

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import global.bert.widget.data.ActivityState
import global.bert.widget.data.BERTPosition
import global.bert.widget.data.QuoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** The launcher's long-press shortcuts: what shortcuts.xml declares, that the manifest wires it, and that each entry lands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BERTShortcutsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val graphics = NativeGraphicsOnTestThread()

    private class Shortcut(val id: String, val icon: Int, val shortLabel: Int, val longLabel: Int, val targetPackage: String, val targetClass: String, val entry: String?)

    private fun declared(): List<Shortcut> {
        val resources = RuntimeEnvironment.getApplication().resources
        val xml = resources.getXml(R.xml.shortcuts)
        val out = mutableListOf<Shortcut>()
        var id = ""; var icon = 0; var short = 0; var long = 0; var pkg = ""; var cls = ""; var entry: String? = null
        val android = "http://schemas.android.com/apk/res/android"
        while (xml.next() != XmlPullParser.END_DOCUMENT) {
            if (xml.eventType == XmlPullParser.START_TAG) when (xml.name) {
                "shortcut" -> {
                    id = xml.getAttributeValue(android, "shortcutId"); icon = xml.getAttributeResourceValue(android, "icon", 0)
                    short = xml.getAttributeResourceValue(android, "shortcutShortLabel", 0); long = xml.getAttributeResourceValue(android, "shortcutLongLabel", 0)
                    entry = null
                }
                "intent" -> {
                    pkg = xml.getAttributeValue(android, "targetPackage"); cls = xml.getAttributeValue(android, "targetClass")
                    assertEquals("android.intent.action.VIEW", xml.getAttributeValue(android, "action"))
                }
                "extra" -> if (xml.getAttributeValue(android, "name") == BERTEntry.EXTRA) entry = xml.getAttributeValue(android, "value")
            } else if (xml.eventType == XmlPullParser.END_TAG && xml.name == "shortcut") out += Shortcut(id, icon, short, long, pkg, cls, entry)
        }
        return out
    }

    @Test fun threeStaticShortcutsPointAtMainActivityWithAKnownEntry() {
        val context = RuntimeEnvironment.getApplication()
        val shortcuts = declared()
        assertEquals(listOf("continue_lost_trail", "bert_price", "make_a_card"), shortcuts.map { it.id })
        assertEquals(listOf("Continue the Lost Trail", "BERT price", "Make a card"), shortcuts.map { context.getString(it.longLabel) })
        assertEquals(listOf(BERTEntry.GAME, BERTEntry.MARKET, BERTEntry.CREATE), shortcuts.map { BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, it.entry)) })
        for (s in shortcuts) {
            assertEquals("${s.id}: the intent must target this build’s own package (the preview build has a suffix)", context.packageName, s.targetPackage)
            assertEquals("${s.id} target", MainActivity::class.java.name, s.targetClass)
            assertTrue("${s.id} short label '${context.getString(s.shortLabel)}' should be 12 characters or fewer", context.getString(s.shortLabel).length <= 12)
        }
    }

    @Test fun mainActivityDeclaresTheShortcutsAndTheTargetIsAnExportedLauncherActivity() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), PackageManager.GET_META_DATA)
        assertEquals(R.xml.shortcuts, info.metaData.getInt("android.app.shortcuts"))
        assertTrue(info.exported)
        // The game activity stays private: shortcuts reach it only through MainActivity.
        assertFalse(context.packageManager.getActivityInfo(ComponentName(context, LostTrailActivity::class.java), 0).exported)
    }

    /** Adaptive-safe: each icon is an adaptive-icon with a navy background and a vector foreground drawn inside the 66dp safe zone. */
    @Test fun shortcutIconsAreAdaptiveAndTheirArtStaysInsideTheSafeZone() {
        for (name in listOf("ic_shortcut_game", "ic_shortcut_price", "ic_shortcut_create")) {
            val icon = File("src/main/res/drawable/$name.xml").readText()
            assertTrue("$name must be an adaptive-icon", icon.contains("<adaptive-icon"))
            assertTrue("$name background", icon.contains("@color/bert_navy"))
            val foreground = File("src/main/res/drawable/${name}_foreground.xml").readText()
            val group = Regex("""scaleX="([\d.]+)".*?translateX="([\d.]+)".*?translateY="([\d.]+)"""", RegexOption.DOT_MATCHES_ALL).find(foreground)!!
            val (scale, tx, ty) = group.destructured.let { (a, b, c) -> Triple(a.toFloat(), b.toFloat(), c.toFloat()) }
            // Art is authored in a 24-unit box; its four corners must sit inside the 66dp circle centred on the 108dp canvas.
            for ((x, y) in listOf(3f to 3f, 21f to 3f, 3f to 21f, 21f to 21f)) {
                val px = tx + x * scale - 54f; val py = ty + y * scale - 54f
                assertTrue("$name corner ($x,$y) at (${px + 54f},${py + 54f}) is outside the 66dp safe circle", px * px + py * py <= 33f * 33f)
            }
        }
    }

    @Test fun gameAndCreateEntriesAreRead() {
        assertEquals(BERTEntry.GAME, BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "GAME")))
        assertEquals(BERTEntry.CREATE, BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "CREATE")))
        assertNull(BERTEntry.from(Intent().putExtra(BERTEntry.EXTRA, "game")))
    }

    private fun app(entry: BERTEntry?, consumed: () -> Unit) = compose.setContent {
        BERTTheme {
            BERTApp(QuoteState.Unavailable("No connection"), ActivityState.Unavailable, emptyList(), BERTPosition(), 1_789_000_000_000, false, false, {}, {}, {},
                entry = entry, consumeEntry = consumed)
        }
    }

    @Test fun theGameEntryOpensTheLostTrailOnceAndIsConsumed() {
        var consumed = 0
        app(BERTEntry.GAME) { consumed++ }
        compose.waitForIdle()
        val shadow = shadowOf(compose.activity)
        val started = shadow.nextStartedActivity
        assertNotNull("Lost Trail should have been started", started)
        assertEquals(LostTrailActivity::class.java.name, started.component?.className)
        assertNull("opened exactly once", shadow.nextStartedActivity)
        assertEquals(1, consumed)
    }

    @Test fun theCreateEntryOpensTheCreateTab() {
        var consumed = 0
        app(BERTEntry.CREATE) { consumed++ }
        compose.waitForIdle()
        compose.onNodeWithText("Share caption card").assertExists()
        assertEquals(1, consumed)
        assertNull(shadowOf(compose.activity).nextStartedActivity)
    }

    @Test fun theMarketEntryStillOpensTheMarket() {
        var consumed = 0
        app(BERTEntry.MARKET) { consumed++ }
        compose.waitForIdle()
        assertEquals(1, consumed)
        compose.onNode(hasText("Market")).assertIsDisplayed()
    }
}
