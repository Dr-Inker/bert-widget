package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import global.bert.widget.data.MyBert
import global.bert.widget.data.MyBertStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 0.15.1: cards saved by 0.15.0 or earlier carry the old Bert label baked into their stored image, and "Share saved
 * card" sent that file. Each is relabelled before it is shown or shared, and its artwork is never replaced:
 *  a. the label alone is painted over where it is proven to sit on flat colour (every other pixel kept);
 *  b. else, for the art that ships with the app, the card is drawn again (the style fully determines that picture);
 *  c. else (My Bert art) the card is kept as it is, shown with a note, and never shared.
 * Old cards are built here as 0.15.0 drew them: the label "A NOTE FROM BERT" at its place, and no source recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedCardRelabelTest {
    @Before @After fun freshShareCache() = forgetFileProviderPaths()
    @After fun noBert() { MyBertStore(context).forget() }

    private val context get() = RuntimeEnvironment.getApplication()
    private val caption = "woofmornin. the mayor is in."
    private val palette = 2
    private val ground = 0xFFE3CEFF.toInt()

    // ---- 0.15.0's card ----

    /** The rows the label owns, as 0.15.0 laid the card out. */
    private fun labelRows(style: CardStyle): IntRange = if (style.layout == CardLayout.NOTE) {
        val artBottom = if (style.format == CardFormat.STORY) 1160 else 636
        artBottom + 4 until artBottom + 112
    } else {
        val bandTop = style.format.height - if (style.format == CardFormat.STORY) 720 else 450
        bandTop + 2 until bandTop + 72
    }

    /** The card as 0.15.0 drew it: today's card with its label rows painted over with 0.15.0's label. */
    private fun oldImage(style: CardStyle): Bitmap {
        val card = renderCaption(context, caption, palette, style).bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(card)
        val rows = labelRows(style)
        canvas.drawRect(0f, rows.first.toFloat(), card.width.toFloat(), (rows.last + 1).toFloat(), Paint().apply { color = ground })
        val bold = CardType.of(context).bold
        val (paint, baseline) = if (style.layout == CardLayout.NOTE)
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF071A2F.toInt(); textSize = 32f; typeface = bold; textAlign = Paint.Align.CENTER } to rows.first - 4 + 66f
        else Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8452B.toInt(); textSize = 30f; typeface = bold; textAlign = Paint.Align.CENTER; letterSpacing = 0.12f } to rows.first - 2 + 40f
        canvas.drawText("A NOTE FROM BERT", card.width / 2f, baseline, paint)
        return card
    }

    /** A card as 0.15.0 left it: version 2 metadata with no source, and [image]. */
    private fun saveOld(style: CardStyle, image: Bitmap = oldImage(style)): Pair<String, File> {
        val saved = CaptionLibrary(context).save(RenderedCaption(caption, palette, image, style))
        val directory = File(context.filesDir, "caption-library/${saved.id}")
        File(directory, "card.json").writeText(JSONObject().put("version", 2).put("caption", caption).put("palette", palette).put("savedAt", saved.savedAt)
            .put("art", style.art.name).put("layout", style.layout.name).put("format", style.format.name).toString())
        File(directory, "card.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return saved.id to directory
    }

    // ---- Looking at the result ----

    /** Review renders: build/outputs/host-ui/relabel/. */
    private fun keep(name: String, image: Bitmap) =
        File("build/outputs/host-ui/relabel/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun stored(directory: File) = BitmapFactory.decodeFile(File(directory, "card.png").path)

    /** Rows of [a] and [b] that differ. */
    private fun differingRows(a: Bitmap, b: Bitmap): Set<Int> {
        val x = IntArray(a.width); val y = IntArray(b.width)
        return (0 until a.height).filter { row -> a.getPixels(x, 0, a.width, 0, row, a.width, 1); b.getPixels(y, 0, b.width, 0, row, b.width, 1); !x.contentEquals(y) }.toSet()
    }

    private fun sharedImage(card: RenderedCaption): Bitmap {
        captionShareIntent(context, card)
        return BitmapFactory.decodeFile(File(context.cacheDir, "caption-cards").listFiles().orEmpty().maxBy { it.lastModified() }.path)
    }

    /** The label rows of a card with the holder's label: what any relabelled card must show there. */
    private fun holderLabelRows(style: CardStyle): Bitmap {
        val card = renderCaption(context, caption, palette, style.copy(source = CaptionSource.HOLDER)).bitmap
        val rows = labelRows(style)
        return Bitmap.createBitmap(card, 0, rows.first, card.width, rows.count())
    }

    private fun labelRowsOf(card: Bitmap, style: CardStyle) = labelRows(style).let { Bitmap.createBitmap(card, 0, it.first, card.width, it.count()) }

    private class Counting(context: Application) {
        var redraws = 0; var repaints = 0
        val library = CaptionLibrary(context,
            redraw = { c, p, s -> redraws++; renderCaption(context, c, p, s) },
            repaint = { b, p, s -> repaints++; repaintCardLabel(b, p, s, CardType.of(context).bold) })
    }

    private fun chooseBert(number: Int, picture: Bitmap) =
        MyBertStore(context).saveChosen(MyBert("Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc", number, "Forest", staked = true), picture)

    private fun bertPicture(name: String) = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mybert/$name"))

    // ---- (a) the label alone ----

    @Test fun everyLayoutHasItsLabelRepaintedAndNothingElse() {
        for (format in CardFormat.entries) for (layout in CardLayout.entries) {
            val style = CardStyle(CardArt.MOON, layout, format)
            val old = oldImage(style)
            val (id, directory) = saveOld(style, old)
            val counting = Counting(context)
            val opened = counting.library.open(id)
            assertFalse("$layout $format", opened.oldLabel)
            keep("stock-${layout.name.lowercase()}-${format.name.lowercase()}-before", old); keep("stock-${layout.name.lowercase()}-${format.name.lowercase()}-after", opened.bitmap)
            assertEquals("$layout $format: repainted, not drawn again", 0 to 1, counting.redraws to counting.repaints)
            // Only the label's rows changed, and they now say MADE WITH THE BERT APP: the old label is gone.
            assertTrue("$layout $format", differingRows(old, opened.bitmap).all { it in labelRows(style) })
            assertTrue("$layout $format: the old label is still there", labelRowsOf(opened.bitmap, style).sameAs(holderLabelRows(style)))
            // Stored once: the new image on disk, the source recorded, no temporary file left.
            assertTrue(stored(directory).sameAs(opened.bitmap))
            assertEquals(CaptionSource.HOLDER, CaptionLibrary(context).list().single { it.id == id }.style.source)
            assertEquals(setOf("card.png", "card.json"), directory.list()!!.toSet())
            // Opening it again, or relabelling the collection, does no more work.
            counting.library.open(id); counting.library.relabelOld()
            assertEquals("$layout $format: worked on twice", 0 to 1, counting.redraws to counting.repaints)
            CaptionLibrary(context).delete(id)
        }
    }

    /** The old label's text is gone from what leaves the phone: the shared file's label rows are the holder's. */
    @Test fun whatIsSharedNoLongerSaysANoteFromBert() {
        val style = CardStyle(CardArt.PHOTO, CardLayout.NOTE, CardFormat.SQUARE)
        val (id, _) = saveOld(style)
        val shared = sharedImage(CaptionLibrary(context).open(id))
        assertTrue(labelRowsOf(shared, style).sameAs(holderLabelRows(style)))
        assertFalse(labelRowsOf(shared, style).sameAs(labelRowsOf(oldImage(style), style)))
    }

    /**
     * A My Bert card keeps its exact picture whatever the holder's pick is now: another Bert chosen since, or none. Every
     * pixel outside the label's rows is the stored one; never the new Bert, never the stock photo.
     */
    @Test fun aMyBertCardKeepsItsOwnBertWhateverTheCurrentPick() {
        for (layout in CardLayout.entries) for (now in listOf("another Bert", "none")) {
            chooseBert(2206, bertPicture("bert-2206.png"))
            val style = CardStyle(CardArt.MY_BERT, layout, CardFormat.SQUARE)
            val old = oldImage(style)
            val (id, directory) = saveOld(style, old)
            if (now == "none") MyBertStore(context).forget() else chooseBert(1506, bertPicture("bert-1506.png"))
            val counting = Counting(context)
            val opened = counting.library.open(id)
            assertFalse("$layout, $now", opened.oldLabel)
            val tag = "mybert-${layout.name.lowercase()}-now-${now.replace(' ', '-')}"
            keep("$tag-before", old); keep("$tag-after", opened.bitmap)
            assertEquals("$layout, $now: the picture was drawn again", 0, counting.redraws)
            assertTrue("$layout, $now: pixels outside the label changed", differingRows(old, opened.bitmap).all { it in labelRows(style) })
            assertTrue(differingRows(old, stored(directory)).all { it in labelRows(style) })
            assertEquals(CardArt.MY_BERT, CaptionLibrary(context).list().single { it.id == id }.style.art)
            if (now == "none") assertFalse("the stock photo", opened.bitmap.sameAs(renderCaption(context, caption, palette, style.copy(art = CardArt.PHOTO)).bitmap))
            CaptionLibrary(context).delete(id)
        }
    }

    // ---- (b) and (c): where the label cannot be proven to sit alone on flat colour ----

    /** Something across the label's rows (a card this does not recognise): the label is not painted over. */
    private fun unprovable(style: CardStyle) = oldImage(style).also {
        Canvas(it).drawRect(0f, labelRows(style).first + 10f, it.width.toFloat(), labelRows(style).first + 14f, Paint().apply { color = 0xFF00AA00.toInt() })
    }

    @Test fun stockArtThatCannotBeRepaintedIsDrawnAgainAsTheSamePicture() {
        val style = CardStyle(CardArt.MAYOR, CardLayout.POSTER, CardFormat.SQUARE)
        val (id, _) = saveOld(style, unprovable(style))
        val counting = Counting(context)
        val opened = counting.library.open(id)
        assertFalse(opened.oldLabel)
        assertEquals(1 to 1, counting.redraws to counting.repaints)
        assertTrue(opened.bitmap.sameAs(renderCaption(context, caption, palette, style).bitmap))
        assertEquals(CardArt.MAYOR, opened.style.art)
    }

    /** Never lose a card, never share the old label: a My Bert card that cannot be repainted is kept exactly as it was. */
    @Test fun aMyBertCardThatCannotBeRepaintedIsKeptButNeverShared() {
        chooseBert(2206, bertPicture("bert-2206.png"))
        val style = CardStyle(CardArt.MY_BERT, CardLayout.NOTE, CardFormat.SQUARE)
        val image = unprovable(style)
        val (id, directory) = saveOld(style, image)
        val before = File(directory, "card.json").readText() to File(directory, "card.png").readBytes()
        val counting = Counting(context)
        val opened = counting.library.open(id)
        assertTrue(opened.oldLabel)
        assertEquals("never drawn again from the current pick", 0, counting.redraws)
        assertTrue(opened.bitmap.sameAs(image))
        assertThrows(IllegalArgumentException::class.java) { captionShareIntent(context, opened) }
        assertEquals(before.first, File(directory, "card.json").readText())
        assertTrue(before.second.contentEquals(File(directory, "card.png").readBytes()))
        assertEquals(setOf("card.png", "card.json"), directory.list()!!.toSet())
    }

    /** A write that fails leaves the old card whole and no temporary file behind. */
    @Test fun aFailedWriteLeavesNoTemporaryFile() {
        val style = CardStyle(CardArt.MOON, CardLayout.NOTE, CardFormat.SQUARE)
        val (id, directory) = saveOld(style)
        val before = File(directory, "card.png").readBytes()
        // The new image cannot be written (its pixels are gone by then): the write starts, and fails part way.
        val failing = CaptionLibrary(context, repaint = { _, _, _ -> Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).also { it.recycle() } })
        assertTrue(failing.open(id).oldLabel)
        assertEquals(setOf("card.png", "card.json"), directory.list()!!.toSet())
        assertTrue(before.contentEquals(File(directory, "card.png").readBytes()))
    }
}
