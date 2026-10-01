package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionCardV2Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private val caption = "woofmornin. the mayor is in."

    @Test fun everyArtLayoutAndSizeRendersAtItsExactSize() {
        val cards = mutableListOf<Bitmap>()
        for (format in CardFormat.entries) for (layout in CardLayout.entries) for (art in CardArt.entries) {
            val card = renderCaption(context, caption, 0, CardStyle(art, layout, format))
            assertEquals(format.width, card.bitmap.width); assertEquals(format.height, card.bitmap.height)
            // Not blank: the art drew something other than the background colour.
            val distinct = (0 until 40).map { card.bitmap.getPixel(it * 27, format.height / 3) }.toSet()
            assertTrue("$art $layout $format looks blank", distinct.size > 3)
            cards += card.bitmap
        }
        // Contact sheet for review: 4 rows (square note/poster, story note/poster) x 5 arts, scaled down.
        val cell = 216; val sheet = Bitmap.createBitmap(cell * 5, cell * 2 + cell * 16 / 9 * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); var y = 0
        cards.chunked(5).forEachIndexed { row, group ->
            val h = if (row < 2) cell else cell * 16 / 9
            group.forEachIndexed { i, b -> canvas.drawBitmap(Bitmap.createScaledBitmap(b, cell, h, true), (i * cell).toFloat(), y.toFloat(), null) }
            y += h
        }
        File("build/outputs/host-ui/caption-cards-v2.png").apply { parentFile?.mkdirs() }.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun storyCardRoundTripsWithItsStyle() {
        val library = CaptionLibrary(context)
        val style = CardStyle(CardArt.MOON, CardLayout.POSTER, CardFormat.STORY)
        val card = renderCaption(context, caption, 2, style)
        val saved = library.save(card)
        assertEquals(style, saved.style)
        val reopened = library.open(saved.id)
        assertEquals(style, reopened.style)
        assertTrue(card.bitmap.sameAs(reopened.bitmap))
        assertEquals(style, CaptionLibrary(context).list().single { it.id == saved.id }.style)
    }

    @Test fun versionOneCardsStillOpenAsPhotoNoteSquare() {
        val library = CaptionLibrary(context)
        val saved = library.save(renderCaption(context, caption, 1))
        val metadata = File(context.filesDir, "caption-library/${saved.id}/card.json")
        metadata.writeText(JSONObject().put("version", 1).put("caption", caption).put("palette", 1).put("savedAt", saved.savedAt).toString())
        val reopened = CaptionLibrary(context).open(saved.id)
        assertEquals(CardStyle(), reopened.style)
        assertEquals(1080, reopened.bitmap.height)
    }
}
