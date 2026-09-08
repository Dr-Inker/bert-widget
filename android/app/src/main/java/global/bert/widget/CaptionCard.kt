package global.bert.widget

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal data class RenderedCaption(val caption: String, val palette: Int, val bitmap: Bitmap)

// Preview and export use the same raster so wrapping, font size and crop remain identical.
internal fun renderCaption(context: Context, caption: String, palette: Int): RenderedCaption {
    val backgrounds = intArrayOf(0xFF071A2F.toInt(), 0xFFFFF5DF.toInt(), 0xFFE3CEFF.toInt())
    val foreground = if (palette == 0) 0xFFFFF5DF.toInt() else 0xFF071A2F.toInt()
    val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgrounds[palette.coerceIn(backgrounds.indices)])
    val portrait = BitmapFactory.decodeResource(context.resources, R.drawable.bert_icon, BitmapFactory.Options().apply { inScaled = false })
    try {
        val bounds = RectF(230f, 64f, 850f, 684f)
        val clip = Path().apply { addRoundRect(bounds, 64f, 64f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(portrait, null, bounds, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } finally {
        portrait.recycle()
    }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground; textSize = 32f; typeface = Typeface.create("sans-serif", Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    canvas.drawText("A NOTE FROM BERT", 540f, 750f, label)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = foreground; typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    fun layout(size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(caption, 0, caption.length, paint, 920)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
    }
    var size = 64f
    var text = layout(size)
    while (text.height > 230 && size > 20f) { size -= 2f; text = layout(size) }
    canvas.save()
    canvas.translate(80f, 800f + (230 - text.height).coerceAtLeast(0) / 2f)
    text.draw(canvas)
    canvas.restore()
    return RenderedCaption(caption, palette, bitmap)
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
