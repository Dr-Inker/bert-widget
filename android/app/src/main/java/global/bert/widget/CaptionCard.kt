package global.bert.widget

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Where Bert comes from on the card; crops are fractions of the source (centre x/y, width covered). */
internal enum class CardArt(val label: String, val drawable: Int?, val raw: Int?, val cx: Float, val cy: Float, val width: Float, val pixelArt: Boolean = false) {
    PHOTO("Photo", R.drawable.bert_icon, null, .50f, .50f, 1.00f),
    MAYOR("Mayor", null, R.raw.wallpaper_mayor_purple_lock, .53f, .63f, .98f),
    PACK("Pack", null, R.raw.wallpaper_woofhub_night_lock, .50f, .58f, .85f),
    MOON("Moonlight", null, R.raw.wallpaper_berthalla_nights_lock, .50f, .50f, .75f),
    TRAIL("Trail", R.drawable.lost_trail, null, .47f, .62f, .36f, pixelArt = true),
}

internal enum class CardLayout(val label: String) { NOTE("Note"), POSTER("Poster") }

internal enum class CardFormat(val label: String, val width: Int, val height: Int) { SQUARE("Square", 1080, 1080), STORY("Story", 1080, 1920) }

internal data class CardStyle(val art: CardArt = CardArt.PHOTO, val layout: CardLayout = CardLayout.NOTE, val format: CardFormat = CardFormat.SQUARE)

internal data class RenderedCaption(val caption: String, val palette: Int, val bitmap: Bitmap, val style: CardStyle = CardStyle())

private fun loadArt(context: Context, art: CardArt): Bitmap {
    val options = BitmapFactory.Options().apply { inScaled = false; if (art.raw != null) inSampleSize = 2 }
    return requireNotNull(if (art.raw != null) context.resources.openRawResource(art.raw).use { BitmapFactory.decodeStream(it, null, options) }
        else BitmapFactory.decodeResource(context.resources, requireNotNull(art.drawable), options)) { "Card art unavailable" }
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
internal fun renderCaption(context: Context, caption: String, palette: Int, style: CardStyle = CardStyle()): RenderedCaption {
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
    try {
        if (style.layout == CardLayout.NOTE) {
            val side = if (style.format == CardFormat.STORY) 900f else 620f
            val top = if (style.format == CardFormat.STORY) 260f else 64f
            val bounds = RectF((W - side) / 2, top, (W + side) / 2, top + side)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(bounds, 64f, 64f, Path.Direction.CW) })
            canvas.drawBitmap(art, artCrop(style.art, art.width, art.height, 1f), bounds, artPaint)
            canvas.restore()
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = 32f; typeface = bold; textAlign = Paint.Align.CENTER }
            canvas.drawText("A NOTE FROM BERT", W / 2f, bounds.bottom + 66f, label)
            val boxTop = bounds.bottom + 116f
            val boxHeight = (if (style.format == CardFormat.STORY) H - 200f else H - 50f) - boxTop
            textPaint.color = foreground
            val text = fit(920, boxHeight.toInt(), if (style.format == CardFormat.STORY) 80f else 64f)
            canvas.save()
            canvas.translate(80f, boxTop + (boxHeight - text.height).coerceAtLeast(0f) / 2f)
            text.draw(canvas)
            canvas.restore()
        } else {
            // Poster: Bert fills the area above the caption band (plus the fade), so the band never covers him.
            val bandHeight = if (style.format == CardFormat.STORY) 620f else 420f
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
            val boxHeight = bandHeight - 76f - 60f
            val text = fit(940, boxHeight.toInt(), if (style.format == CardFormat.STORY) 84f else 70f)
            canvas.save()
            canvas.translate(70f, boxTop + (boxHeight - text.height).coerceAtLeast(0f) / 2f)
            text.draw(canvas)
            canvas.restore()
        }
    } finally {
        art.recycle()
    }
    return RenderedCaption(caption, palette, bitmap, style)
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
    return Intent.createChooser(send, "Share your BERT card")
}
