package global.bert.widget.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Match my Bert" palettes: every word the widgets draw clears WCAG AA on every surface, for every background. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MyBertPaletteTest {
    private fun ratio(ink: Long, ground: Long) = ColorUtils.calculateContrast(ink.toInt(), ground.toInt())

    /** The same pairs BERTContrastTest and the widget render test hold the theme packs to. */
    private fun failures(label: String, p: BERTPalette): List<String> = listOf(
        "text/background" to ratio(p.textArgb, p.backgroundArgb), "text/panel" to ratio(p.textArgb, p.panelArgb),
        "muted/background" to ratio(p.mutedArgb, p.backgroundArgb), "muted/panel" to ratio(p.mutedArgb, p.panelArgb),
        "accent/background" to ratio(p.accentArgb, p.backgroundArgb), "accent/panel" to ratio(p.accentArgb, p.panelArgb),
        "gold/panel" to ratio(0xFFFFC857, p.panelArgb), "negative change/background" to ratio(0xFFFF7583, p.backgroundArgb),
        "app body on panel" to ratio(0xFFFFF5DF, p.panelArgb), "app muted on panel" to ratio(0xFF9CB0C5, p.panelArgb),
        "app orange on panel" to ratio(0xFFFF9433, p.panelArgb),
    ).filter { it.second < 4.5 }.map { "$label ${it.first} ${"%.2f".format(it.second)}" }

    @Test fun `each solid background has its own curated palette that passes AA`() {
        val palettes = listOf("Green", "Yellow", "Blue", "Pink", "Red").map { it to MyBertPalette.forBackground(it, null) }
        assertEquals("five distinct palettes", 5, palettes.map { it.second }.toSet().size)
        assertTrue(palettes.none { it.second == BERTThemePack.BERT_NAVY.palette })
        val failed = palettes.flatMap { (name, palette) -> failures(name, palette) }
        assertTrue(failed.toString(), failed.isEmpty())
    }

    @Test fun `derived palettes pass AA for every hue, saturation and lightness`() {
        val failed = mutableListOf<String>()
        var derived = 0
        for (hue in 0 until 360 step 7) for (s in listOf(0.16f, 0.3f, 0.5f, 0.75f, 1f)) for (l in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) {
            val argb = ColorUtils.HSLToColor(floatArrayOf(hue.toFloat(), s, l))
            val palette = MyBertPalette.derived(argb) ?: continue
            derived++
            failed += failures("h$hue s$s l$l", palette)
        }
        assertTrue("most colours give a palette ($derived)", derived > 1_000)
        assertTrue(failed.take(10).toString(), failed.isEmpty())
    }

    @Test fun `greys, blacks and whites fall back to BERT Navy, as do unknown or missing backgrounds`() {
        for (argb in listOf(Color.GRAY, Color.BLACK, Color.WHITE, Color.rgb(250, 250, 252))) assertNull(MyBertPalette.derived(argb))
        val navy = BERTThemePack.BERT_NAVY.palette
        assertEquals(navy, MyBertPalette.forBackground("Moon", null))
        assertEquals(navy, MyBertPalette.forBackground(null, Color.GRAY))
        assertEquals(navy, MyBertPalette.forBackground("Plaid", null))
    }

    @Test fun `a scene's colour is read from the picture's edge, not from the character`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.rgb(70, 140, 60)); drawRect(30f, 30f, 170f, 200f, Paint().apply { color = Color.rgb(200, 120, 60) }) }
        val sampled = requireNotNull(MyBertPalette.sample(bitmap))
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(sampled, it) }
        assertTrue("green hue, got ${hsl[0]}", hsl[0] in 90f..130f)

        // The recorded Forest Bert (#2206) gives a real palette, not navy, and it passes.
        val forest = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mybert/bert-2206.png"))
        val palette = MyBertPalette.forBackground("Forest", MyBertPalette.sample(forest))
        assertNotNull(MyBertPalette.sample(forest))
        assertTrue(palette != BERTThemePack.BERT_NAVY.palette)
        assertTrue(failures("forest", palette).toString(), failures("forest", palette).isEmpty())
    }
}
