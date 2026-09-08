package global.bert.widget

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import org.junit.Assert.*
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
class CaptionLibraryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun savedCardSurvivesStoreRecreationWithExactPixelsAndMetadata() {
        val original = renderCaption(context, "A very good dog. A very big hat.", 2)
        val saved = CaptionLibrary(context).save(original)
        val reopened = CaptionLibrary(context)
        assertEquals(listOf(saved), reopened.list())
        val card = reopened.open(saved.id)
        assertEquals(original.caption, card.caption)
        assertEquals(2, card.palette)
        assertTrue("Persisted PNG must preserve the preview's pixels", original.bitmap.sameAs(card.bitmap))
        assertEquals(270, reopened.thumbnail(saved.id).width)
    }

    @Test fun deletingOneCardPreservesOtherWorkAndRejectsPathTraversal() {
        val store = CaptionLibrary(context)
        val first = store.save(renderCaption(context, "First card", 0))
        val second = store.save(renderCaption(context, "Keep this one", 1))
        assertThrows(IllegalArgumentException::class.java) { store.delete("../caption-library") }
        assertThrows(IllegalArgumentException::class.java) { store.open("../caption-library") }
        store.delete(first.id)
        assertEquals(listOf(second), CaptionLibrary(context).list())
        assertEquals("Keep this one", store.open(second.id).caption)
    }

    @Test fun incompleteSaveIsInvisibleAndInvalidSaveDoesNotEraseExistingCard() {
        val store = CaptionLibrary(context)
        val saved = store.save(renderCaption(context, "Already safe", 0))
        File(context.filesDir, "caption-library/.pending-test").mkdirs()
        assertThrows(IllegalArgumentException::class.java) { store.save(renderCaption(context, "", 0)) }
        assertEquals(listOf(saved), CaptionLibrary(context).list())
        assertEquals("Already safe", store.open(saved.id).caption)
    }

    @Test fun shareIntentGrantsOnlyReadAccessToExactPreviewPng() {
        val rendered = renderCaption(context, "A note for the pack 🐾", 1)
        val chooser = captionShareIntent(context, rendered)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags)
        val uri = requireNotNull(send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".captions", uri.authority)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        context.contentResolver.openInputStream(uri).use { stream ->
            val png = requireNotNull(BitmapFactory.decodeStream(stream))
            assertEquals(1080, png.width)
            assertEquals(1080, png.height)
            assertTrue(rendered.bitmap.sameAs(png))
        }
    }

    @Test fun joinedEmojiCaptionsReopenWithoutErasingOlderCards() {
        val store = CaptionLibrary(context)
        val older = store.save(renderCaption(context, "Keep my first card.", 0))
        val original = renderCaption(context, "👨‍👩‍👧‍👦".repeat(96), 2)
        val saved = store.save(original)
        val reopened = CaptionLibrary(context).open(saved.id)
        assertEquals(original.caption, reopened.caption)
        assertTrue(original.bitmap.sameAs(reopened.bitmap))
        assertThrows(IllegalArgumentException::class.java) {
            store.save(RenderedCaption("🐾".repeat(97), 2, original.bitmap))
        }
        assertEquals(setOf(older.id, saved.id), CaptionLibrary(context).list().map { it.id }.toSet())
        assertEquals("Keep my first card.", store.open(older.id).caption)
    }
}
