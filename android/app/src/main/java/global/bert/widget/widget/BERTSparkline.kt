package global.bert.widget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import global.bert.widget.data.BERTPriceSample

object BERTSparkline {
    fun render(samples: List<BERTPriceSample>, width: Int = 600, height: Int = 180): Bitmap? {
        if (samples.size < 2 || width <= 0 || height <= 0) return null
        val prices = samples.map { it.priceUsd }
        val minimum = prices.min()
        val maximum = prices.max()
        val range = (maximum - minimum).takeIf { it > 0 } ?: (maximum * 0.01).coerceAtLeast(1e-12)
        val start = samples.first().observedAtEpochMillis
        val duration = (samples.last().observedAtEpochMillis - start).coerceAtLeast(1L)
        val inset = 8f
        val drawableWidth = width - inset * 2
        val drawableHeight = height - inset * 2
        val rising = samples.last().priceUsd >= samples.first().priceUsd
        val lineColor = Color.parseColor(if (rising) "#45E09A" else "#FF6B7A")

        fun x(sample: BERTPriceSample) = inset + ((sample.observedAtEpochMillis - start).toFloat() / duration) * drawableWidth
        fun y(sample: BERTPriceSample) = inset + (1f - ((sample.priceUsd - minimum) / range).toFloat()) * drawableHeight

        val line = Path().apply {
            moveTo(x(samples.first()), y(samples.first()))
            samples.drop(1).forEach { lineTo(x(it), y(it)) }
        }
        val fill = Path(line).apply {
            lineTo(x(samples.last()), height.toFloat())
            lineTo(x(samples.first()), height.toFloat())
            close()
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    lineColor and 0x00FFFFFF or (0x42 shl 24),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            })
            canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                style = Paint.Style.STROKE
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
    }
}
