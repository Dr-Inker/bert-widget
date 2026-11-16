package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        for (format in CardFormat.entries) for (layout in CardLayout.entries) for (art in CardArt.BUILT_IN) {
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

    /**
     * Critique 2026-11-12 ART-5: the card is the one thing that leaves the app, so it is set in the app's own Outfit (Bold
     * for the caption and label, Medium for the address), not the phone's Roboto. Each card is drawn three ways: as the
     * app draws it, with Outfit read straight from the bundled font files, and with the system font. It must match the
     * first and differ from the second.
     */
    @Test fun theCardIsSetInTheBundledOutfitNotTheSystemFont() {
        val outfit = CardType(Typeface.createFromFile(File("src/main/res/font/outfit_bold.ttf")), Typeface.createFromFile(File("src/main/res/font/outfit_medium.ttf")))
        val system = CardType(Typeface.create("sans-serif", Typeface.BOLD), Typeface.create("sans-serif-medium", Typeface.NORMAL))
        for (format in CardFormat.entries) for (layout in CardLayout.entries) {
            val style = CardStyle(CardArt.PHOTO, layout, format)
            val card = renderCaption(context, caption, 0, style).bitmap
            assertTrue("$layout $format: drawn in Outfit", card.sameAs(renderCaption(context, caption, 0, style, outfit).bitmap))
            assertFalse("$layout $format: not in the system font", card.sameAs(renderCaption(context, caption, 0, style, system).bitmap))
        }
    }

    /**
     * 0.15.1: a card made from a Berthalla post on the board leaves the phone, so its label says it is from the Berthalla X bot
     * posting as Bert, never "A NOTE FROM BERT" (0.15.0 labelled it as Bert's own). The holder's own captions say MADE
     * WITH THE BERT APP. The label's full wording fits both layouts' bands at both sizes.
     */
    @Test fun aCardFromABerthallaPostSaysItIsBerthallas() {
        val post = "woofmornin. the ladybug holds the screen door like the wind can wait its turn."
        for (format in CardFormat.entries) for (layout in CardLayout.entries) {
            val holder = renderCaption(context, post, 0, CardStyle(CardArt.PHOTO, layout, format)).bitmap
            val berthalla = renderCaption(context, post, 0, CardStyle(CardArt.PHOTO, layout, format, CaptionSource.BERTHALLA))
            assertFalse("$layout $format: a Berthalla post still says A NOTE FROM BERT", holder.sameAs(berthalla.bitmap))
            assertEquals(CaptionSource.BERTHALLA, berthalla.style.source)
            File("build/outputs/host-ui/caption-card-berthalla-${layout.name.lowercase()}-${format.name.lowercase()}.png").apply { parentFile?.mkdirs() }
                .outputStream().use { berthalla.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Only the label differs: the caption, art and foot are the same card.
            val rows = (0 until format.height).count { y -> (0 until format.width step 3).any { x -> holder.getPixel(x, y) != berthalla.bitmap.getPixel(x, y) } }
            assertTrue("$layout $format: $rows rows differ, more than a label's", rows in 1..60)
        }
        // The holder's own words (the default style is the holder's).
        assertTrue(renderCaption(context, caption, 1).bitmap.sameAs(renderCaption(context, caption, 1, CardStyle(source = CaptionSource.HOLDER)).bitmap))
        assertEquals("MADE WITH THE BERT APP", CaptionSource.HOLDER.label)
        // The full wording fits the bands (Note: 32px bold in 920px; Poster: 30px bold, spaced, in 940px).
        val bold = CardType.of(context).bold
        val note = android.graphics.Paint().apply { textSize = 32f; typeface = bold }
        val poster = android.graphics.Paint().apply { textSize = 30f; typeface = bold; letterSpacing = 0.12f }
        assertEquals("FROM THE BERTHALLA X BOT", cardLabel(CaptionSource.BERTHALLA, note, 1080 - 160f))
        assertEquals("FROM THE BERTHALLA X BOT", cardLabel(CaptionSource.BERTHALLA, poster, 1080 - 140f))
        assertEquals("BERTHALLA X BOT", cardLabel(CaptionSource.BERTHALLA, poster, 400f))
    }

    /**
     * 0.15.1: a card never says "A NOTE FROM BERT", whoever wrote it: a holder's own words were put in Bert's mouth. The
     * label is one of two, and the phrase is nowhere in the app's code, layouts or strings.
     */
    @Test fun noCardSaysANoteFromBert() {
        assertEquals(setOf("MADE WITH THE BERT APP", "FROM THE BERTHALLA X BOT"), CaptionSource.entries.map { it.label }.toSet())
        // The holder's card and Berthalla's are told apart on the card itself.
        for (format in CardFormat.entries) for (layout in CardLayout.entries)
            assertFalse("$layout $format", renderCaption(context, caption, 0, CardStyle(CardArt.PHOTO, layout, format)).bitmap
                .sameAs(renderCaption(context, caption, 0, CardStyle(CardArt.PHOTO, layout, format, CaptionSource.BERTHALLA)).bitmap))
        val sources = File("src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()
        assertTrue(sources.size > 50)
        val found = sources.filter { it.readText().contains("NOTE FROM BERT", ignoreCase = true) }.map { it.name }
        assertTrue("A NOTE FROM BERT is still in $found", found.isEmpty())
    }

    @Test fun aBerthallaCardSavesAndReopensAsBerthallas() {
        val library = CaptionLibrary(context)
        val style = CardStyle(CardArt.PHOTO, CardLayout.POSTER, CardFormat.SQUARE, CaptionSource.BERTHALLA)
        val saved = library.save(renderCaption(context, caption, 0, style))
        assertEquals(style, CaptionLibrary(context).list().single { it.id == saved.id }.style)
        assertEquals(style, library.open(saved.id).style)
    }

    /**
     * A caption is Berthalla's only while it is unedited: the post as the app put it in the field (the whole post, or
     * the board's excerpt of it), spacing, case and punctuation aside. Anything else, a shorter unbroken piece too, is
     * the holder's. 0.15.1's first rules counted shared words, then accepted any piece, so a holder's own words
     * ("sell your bert" from "never sell your bert") shipped under Berthalla's name.
     */
    @Test fun aCaptionIsBerthallasOnlyWhileItIsUnedited() {
        val post = "woofmornin. the ladybug holds the screen door like the wind can wait its turn."
        assertTrue(stillBerthallas(post, post))
        assertTrue("the board's excerpt", stillBerthallas(post, captionExcerpt(post)))
        assertTrue("spacing, case and punctuation", stillBerthallas(post, "  Woofmornin — the ladybug holds the SCREEN door like the wind can wait its turn!! "))
        val long = "never sell your bert. " + "the pack keeps its word and the garden keeps its gate, whatever the chart says this week. ".repeat(2)
        assertTrue("a long post's excerpt, as the board gives it", stillBerthallas(long, captionExcerpt(long)))
        assertFalse("a shorter unbroken piece", stillBerthallas("never sell your bert", "sell your bert"))
        assertFalse("a piece of the post", stillBerthallas(post, "the ladybug holds the screen door"))
        assertFalse("the post with the holder's words after it", stillBerthallas(post, "the wind can wait its turn. sell BERT now"))
        assertFalse("its words in the holder's sentence", stillBerthallas(post, "the ladybug never holds the door"))
        assertFalse("a word changed", stillBerthallas(post, "woofmornin. the ladybug holds the garden gate like the wind can wait its turn."))
        assertFalse("emptied", stillBerthallas(post, ""))
        assertFalse("blank", stillBerthallas(post, "  "))
        assertFalse("one word of it", stillBerthallas(post, "ladybug"))
        assertTrue("a one-word post, unedited", stillBerthallas("woof.", "Woof"))
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
