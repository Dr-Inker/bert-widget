package global.bert.widget

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Parcelable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptionCardExportTest {
    @Before @After fun freshShareCache() = forgetFileProviderPaths()

    private val context get() = RuntimeEnvironment.getApplication()
    private val backgrounds = intArrayOf(0xFF071A2F.toInt(), 0xFFFFF5DF.toInt(), 0xFFE3CEFF.toInt())
    // The longest caption the editor allows, in wide letters, so every card is at its most crowded.
    private val longest = "WOOF ".repeat(19) + "W"

    @Test fun everyExportCarriesTheFootBandBelowTheCaption() {
        assertEquals(CAPTION_CHARACTER_LIMIT, captionCharacterCount(longest))
        val sheet = mutableListOf<Bitmap>()
        for (format in CardFormat.entries) for (layout in CardLayout.entries) for (palette in 0..2) for (caption in listOf("woofmornin.", longest)) {
            val style = CardStyle(CardArt.PHOTO, layout, format)
            val (card, bounds) = renderCaptionWithBounds(context, caption, palette, style)
            val label = "$layout $format palette $palette \"$caption\""
            assertTrue("$label: the caption must end above the foot band", bounds.caption.bottom <= bounds.footer.top)
            assertTrue("$label: the foot band stays on the card", bounds.footer.top > 0 && bounds.footer.bottom <= format.height)
            // The strip between the caption and the band is untouched card background: nothing overlaps.
            val gapTop = bounds.caption.bottom.toInt() + 1
            for (y in gapTop until bounds.footer.top.toInt()) for (x in 0 until format.width step 6) {
                assertEquals("$label: pixel ($x, $y) above the band", backgrounds[palette], card.bitmap.getPixel(x, y))
            }
            // The band itself carries the wordmark and the address: plenty of non-background pixels, centred on the card.
            val inked = mutableListOf<Int>()
            for (y in bounds.footer.top.toInt() until bounds.footer.bottom.toInt()) for (x in 0 until format.width) {
                if (card.bitmap.getPixel(x, y) != backgrounds[palette]) inked += x
            }
            assertTrue("$label: the band must carry the wordmark and address (${inked.size} inked pixels)", inked.size > 2_000)
            val centre = (inked.min() + inked.max()) / 2f
            assertTrue("$label: the band is centred (centre $centre)", kotlin.math.abs(centre - format.width / 2f) < 16f)
            assertTrue("$label: the band is unobtrusive", inked.max() - inked.min() < format.width * 0.45f)
            if (caption == longest && palette != 1) sheet += card.bitmap
        }
        // Review sheet: the foot of each crowded card (square note/poster, story note/poster; Midnight then Lilac).
        val crop = 360
        val out = Bitmap.createBitmap(1080, crop * sheet.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        sheet.forEachIndexed { i, b -> canvas.drawBitmap(b, Rect(0, b.height - crop, 1080, b.height), Rect(0, i * crop, 1080, (i + 1) * crop), null) }
        File("build/outputs/host-ui/caption-card-footers.png").apply { parentFile?.mkdirs() }.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun thumbnailsAreSquareCropsOfEveryArtwork() {
        for (art in CardArt.entries) {
            val thumbnail = artThumbnail(context, art, 216)
            assertEquals(216, thumbnail.width); assertEquals(216, thumbnail.height)
            val distinct = (0 until 216 step 12).flatMap { x -> (0 until 216 step 12).map { y -> thumbnail.getPixel(x, y) } }.toSet()
            assertTrue("$art thumbnail looks blank", distinct.size > 20)
        }
    }

    @Test fun telegramAndXComeFirstOnlyWhenInstalled() {
        val send = Intent(Intent.ACTION_SEND).setType("image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val plain = preferredShareChooser(send, "Share") { false }
        assertEquals(Intent.ACTION_CHOOSER, plain.action)
        assertNull(plain.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Parcelable::class.java))

        val both = preferredShareChooser(send, "Share") { it == "org.telegram.messenger.web" || it == "com.twitter.android" || it == "org.telegram.messenger" }
        val first = requireNotNull(both.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Parcelable::class.java)).map { it as Intent }
        // One Telegram (the Play build wins over the direct build), then X; never more than the two Android shows.
        assertEquals(listOf("org.telegram.messenger", "com.twitter.android"), first.map { it.`package` })
        first.forEach {
            assertEquals(Intent.ACTION_SEND, it.action)
            assertEquals("image/png", it.type)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, it.flags)
        }
        val onlyX = preferredShareChooser(send, "Share") { it == "com.twitter.android" }
        assertEquals(listOf("com.twitter.android"),
            requireNotNull(onlyX.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Parcelable::class.java)).map { (it as Intent).`package` })
    }

    @Test fun realShareIntentAsksThePackageManager() {
        val card = renderCaption(context, "A note for the pack", 0)
        assertNull(captionShareIntent(context, card).getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Parcelable::class.java))
        val telegram = Intent(Intent.ACTION_SEND).setType("image/png").setPackage("org.telegram.messenger")
        shadowOf(context.packageManager).addResolveInfoForIntent(telegram, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "org.telegram.messenger"; name = "org.telegram.ui.LaunchActivity" }
        })
        val chooser = captionShareIntent(context, card)
        val first = requireNotNull(chooser.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS, Parcelable::class.java)).map { it as Intent }
        assertEquals(listOf("org.telegram.messenger"), first.map { it.`package` })
        val send = requireNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java), first.single().getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
    }
}
