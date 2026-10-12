package global.bert.widget

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Parcelable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Where Bert comes from on the card; crops are fractions of the source (centre x/y, width covered).
 * [description] is what the art picker says aloud for each thumbnail.
 */
internal enum class CardArt(val label: String, val description: String, val drawable: Int?, val raw: Int?, val cx: Float, val cy: Float, val width: Float, val pixelArt: Boolean = false) {
    PHOTO("Photo", "Photo of Bert", R.drawable.bert_icon, null, .50f, .50f, 1.00f),
    MAYOR("Mayor", "Mayor Bert", null, R.raw.wallpaper_mayor_purple_lock, .53f, .63f, .98f),
    PACK("Pack", "Bert and the pack", null, R.raw.wallpaper_woofhub_night_lock, .50f, .58f, .85f),
    MOON("Moonlight", "Bert by moonlight", null, R.raw.wallpaper_berthalla_nights_lock, .50f, .50f, .75f),
    TRAIL("Trail", "Pixel Bert on the Lost Trail", R.drawable.lost_trail, null, .47f, .62f, .36f, pixelArt = true),
}

internal enum class CardLayout(val label: String) { NOTE("Note"), POSTER("Poster") }

internal enum class CardFormat(val label: String, val width: Int, val height: Int) { SQUARE("Square", 1080, 1080), STORY("Story", 1080, 1920) }

internal data class CardStyle(val art: CardArt = CardArt.PHOTO, val layout: CardLayout = CardLayout.NOTE, val format: CardFormat = CardFormat.SQUARE)

internal data class RenderedCaption(val caption: String, val palette: Int, val bitmap: Bitmap, val style: CardStyle = CardStyle())

private fun decodeArt(context: Context, art: CardArt, options: BitmapFactory.Options): Bitmap? =
    if (art.raw != null) context.resources.openRawResource(art.raw).use { BitmapFactory.decodeStream(it, null, options) }
    else BitmapFactory.decodeResource(context.resources, requireNotNull(art.drawable), options)

private fun loadArt(context: Context, art: CardArt): Bitmap =
    requireNotNull(decodeArt(context, art, BitmapFactory.Options().apply { inScaled = false; if (art.raw != null) inSampleSize = 2 })) { "Card art unavailable" }

/** A square picker thumbnail showing the same crop the square note card uses, decoded no larger than needed. */
internal fun artThumbnail(context: Context, art: CardArt, sizePx: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
    decodeArt(context, art, bounds)
    val cropWidth = artCrop(art, bounds.outWidth, bounds.outHeight, 1f).width()
    var sample = 1
    // Pixel art keeps every source pixel so its nearest-neighbour scaling stays even.
    if (!art.pixelArt) while (cropWidth / (sample * 2) >= sizePx) sample *= 2
    val source = requireNotNull(decodeArt(context, art, BitmapFactory.Options().apply { inScaled = false; inSampleSize = sample })) { "Card art unavailable" }
    try {
        val thumbnail = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(thumbnail).drawBitmap(source, artCrop(art, source.width, source.height, 1f), Rect(0, 0, sizePx, sizePx),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = !art.pixelArt })
        return thumbnail
    } finally {
        source.recycle()
    }
}

/** The web address printed at the foot of every exported card, beside the BERT wordmark. */
internal const val CARD_FOOTER_ADDRESS = "berthalla.io/app"

/** Where the caption was drawn and where the foot band (wordmark plus address) sits, in card pixels. */
internal data class CardBounds(val caption: RectF, val footer: RectF)

/** The foot band: kept clear of the caption, and above the reply bar that story viewers lay over the bottom of a story. */
internal fun cardFooter(format: CardFormat): RectF = when (format) {
    CardFormat.SQUARE -> RectF(0f, format.height - 84f, format.width.toFloat(), format.height - 20f)
    CardFormat.STORY -> RectF(0f, format.height - 220f, format.width.toFloat(), format.height - 150f)
}

/** Space kept between the bottom of the caption area and the foot band. */
private const val FOOTER_GAP = 24f

private fun drawFooter(context: Context, canvas: Canvas, band: RectF, color: Int) {
    val mark = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.bert_wordmark, BitmapFactory.Options().apply { inScaled = false })) { "Wordmark unavailable" }
    try {
        val markHeight = band.height() * 0.5f
        val markWidth = markHeight * mark.width / mark.height
        val address = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = markHeight * 0.78f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); letterSpacing = 0.02f
        }
        val gap = markHeight * 0.6f
        val left = band.centerX() - (markWidth + gap + address.measureText(CARD_FOOTER_ADDRESS)) / 2f
        val top = band.centerY() - markHeight / 2f
        canvas.drawBitmap(mark, null, RectF(left, top, left + markWidth, top + markHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) })
        canvas.drawText(CARD_FOOTER_ADDRESS, left + markWidth + gap, band.centerY() - (address.ascent() + address.descent()) / 2f, address)
    } finally {
        mark.recycle()
    }
}

/** The source rectangle for a target aspect (height / width), centred on Bert and kept inside the image. */
internal fun artCrop(art: CardArt, sourceWidth: Int, sourceHeight: Int, aspect: Float, zoom: Float = 1f): Rect {
    var w = art.width * zoom * sourceWidth
    var h = w * aspect
    if (h > sourceHeight) { h = sourceHeight.toFloat(); w = h / aspect }
    if (w > sourceWidth) { w = sourceWidth.toFloat(); h = w * aspect }
    val x = (art.cx * sourceWidth - w / 2).coerceIn(0f, sourceWidth - w)
    val y = (art.cy * sourceHeight - h / 2).coerceIn(0f, sourceHeight - h)
    return Rect(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
}

// Preview and export use the same raster so wrapping, font size and crop remain identical.
internal fun renderCaption(context: Context, caption: String, palette: Int, style: CardStyle = CardStyle()): RenderedCaption =
    renderCaptionWithBounds(context, caption, palette, style).first

internal fun renderCaptionWithBounds(context: Context, caption: String, palette: Int, style: CardStyle = CardStyle()): Pair<RenderedCaption, CardBounds> {
    val backgrounds = intArrayOf(0xFF071A2F.toInt(), 0xFFFFF5DF.toInt(), 0xFFE3CEFF.toInt())
    val p = palette.coerceIn(backgrounds.indices)
    val foreground = if (p == 0) 0xFFFFF5DF.toInt() else 0xFF071A2F.toInt()
    val (W, H) = style.format.width to style.format.height
    val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgrounds[p])
    val art = loadArt(context, style.art)
    val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = !style.art.pixelArt }
    val bold = Typeface.create("sans-serif", Typeface.BOLD)
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold }
    fun fit(maxWidth: Int, maxHeight: Int, start: Float): StaticLayout {
        fun layout(size: Float): StaticLayout {
            textPaint.textSize = size
            return StaticLayout.Builder.obtain(caption, 0, caption.length, textPaint, maxWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        }
        var size = start; var text = layout(size)
        while (text.height > maxHeight && size > 20f) { size -= 2f; text = layout(size) }
        return text
    }
    val footer = cardFooter(style.format)
    val captionBottom = footer.top - FOOTER_GAP
    val drawn: RectF
    try {
        if (style.layout == CardLayout.NOTE) {
            val side = if (style.format == CardFormat.STORY) 900f else 580f
            val top = if (style.format == CardFormat.STORY) 260f else 56f
            val bounds = RectF((W - side) / 2, top, (W + side) / 2, top + side)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(bounds, 64f, 64f, Path.Direction.CW) })
            canvas.drawBitmap(art, artCrop(style.art, art.width, art.height, 1f), bounds, artPaint)
            canvas.restore()
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = 32f; typeface = bold; textAlign = Paint.Align.CENTER }
            canvas.drawText("A NOTE FROM BERT", W / 2f, bounds.bottom + 66f, label)
            val boxTop = bounds.bottom + 116f
            val boxHeight = captionBottom - boxTop
            textPaint.color = foreground
            val text = fit(920, boxHeight.toInt(), if (style.format == CardFormat.STORY) 80f else 64f)
            val textTop = boxTop + (boxHeight - text.height).coerceAtLeast(0f) / 2f
            canvas.save()
            canvas.translate(80f, textTop)
            text.draw(canvas)
            canvas.restore()
            drawn = RectF(80f, textTop, 80f + text.width, textTop + text.height)
        } else {
            // Poster: Bert fills the area above the caption band (plus the fade), so the band never covers him.
            val bandHeight = if (style.format == CardFormat.STORY) 720f else 450f
            val artBottom = (H - bandHeight + 60f).toInt()
            canvas.drawBitmap(art, artCrop(style.art, art.width, art.height, artBottom.toFloat() / W), Rect(0, 0, W, artBottom), artPaint)
            val band = backgrounds[p]
            val shade = Paint().apply {
                shader = LinearGradient(0f, H - bandHeight - 220f, 0f, H - bandHeight, band and 0x00FFFFFF, band, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, H - bandHeight - 220f, W.toFloat(), H - bandHeight, shade)
            canvas.drawRect(0f, H - bandHeight, W.toFloat(), H.toFloat(), Paint().apply { color = band })
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF9433.toInt().takeIf { p == 0 } ?: 0xFFB8452B.toInt(); textSize = 30f; typeface = bold; textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
            canvas.drawText("A NOTE FROM BERT", W / 2f, H - bandHeight + 40f, label)
            textPaint.color = foreground
            val boxTop = H - bandHeight + 76f
            val boxHeight = captionBottom - boxTop
            val text = fit(940, boxHeight.toInt(), if (style.format == CardFormat.STORY) 84f else 70f)
            val textTop = boxTop + (boxHeight - text.height).coerceAtLeast(0f) / 2f
            canvas.save()
            canvas.translate(70f, textTop)
            text.draw(canvas)
            canvas.restore()
            drawn = RectF(70f, textTop, 70f + text.width, textTop + text.height)
        }
        // A quiet signature on the card's own background: the wordmark and the address, in the caption colour at reduced strength.
        drawFooter(context, canvas, footer, (foreground and 0x00FFFFFF) or (0xB3 shl 24))
    } finally {
        art.recycle()
    }
    return RenderedCaption(caption, palette, bitmap, style) to CardBounds(drawn, footer)
}

/** Apps offered first in the share sheet, one per group: Telegram (Play or direct build), then X. */
internal val PREFERRED_SHARE_TARGETS = listOf(listOf("org.telegram.messenger", "org.telegram.messenger.web"), listOf("com.twitter.android"))

/**
 * The system share sheet, with Telegram and X placed first when they are installed and accept the image.
 * Android shows at most two initial targets; with neither app installed this is the plain chooser.
 */
internal fun preferredShareChooser(send: Intent, title: String, canHandle: (String) -> Boolean): Intent {
    val chooser = Intent.createChooser(send, title)
    val first = PREFERRED_SHARE_TARGETS.mapNotNull { group -> group.firstOrNull(canHandle) }.map { Intent(send).setPackage(it) }
    if (first.isNotEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, first.toTypedArray<Parcelable>())
    return chooser
}

internal fun captionShareIntent(context: Context, card: RenderedCaption): Intent {
    val directory = File(context.cacheDir, "caption-cards").apply { check(mkdirs() || isDirectory) }
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60_000L
    directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    val file = File(directory, "bert-${UUID.randomUUID()}.png")
    file.outputStream().use { check(card.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.captions", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("BERT caption card", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return preferredShareChooser(send, "Share your BERT card") { pkg ->
        runCatching { Intent(send).setPackage(pkg).resolveActivity(context.packageManager) != null }.getOrDefault(false)
    }
}
