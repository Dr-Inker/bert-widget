package global.bert.widget.theme

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils

/**
 * "Match my Bert": widget colours from the chosen Bert's Background trait. Every palette keeps the app's dark,
 * navy-like weight (cream words on a deep ground) and passes WCAG AA (4.5:1) for every word drawn on it.
 */
object MyBertPalette {
    private const val CREAM = 0xFFFFF5DFL

    /** Hand-picked for the five solid backgrounds, from the art's own colours (sampled from the collection's PNGs). */
    private val CURATED = mapOf(
        "Green" to BERTPalette(0xFF0F1E14, 0xFF1C3526, CREAM, 0xFFB4C9AC, 0xFF9FD98A), // art #6B8B55
        "Yellow" to BERTPalette(0xFF1D190B, 0xFF352E15, CREAM, 0xFFD3C7A2, 0xFFF2CF63), // art #D1B664
        "Blue" to BERTPalette(0xFF0B1626, 0xFF182B42, CREAM, 0xFFAABBD0, 0xFF8FBBF2), // art #3D4E64
        "Pink" to BERTPalette(0xFF220F19, 0xFF3B1B2B, CREAM, 0xFFD9B5C4, 0xFFF7A1C2), // art #C46B8B
        "Red" to BERTPalette(0xFF240E11, 0xFF3E1B1F, CREAM, 0xFFDBB3B4, 0xFFFF9893), // art #C35E62
    )

    /** The palette for a Bert: curated for a solid colour, derived from [sampledArgb] for a scene, else BERT Navy. */
    fun forBackground(background: String?, sampledArgb: Int?): BERTPalette =
        CURATED[background] ?: sampledArgb?.let(::derived) ?: BERTThemePack.BERT_NAVY.palette

    /**
     * A dark palette on the sampled colour's hue. Greys, near-blacks and near-whites carry no hue worth matching, so
     * they fall back to navy. Muted and accent text are lightened until they clear 4.5:1 on both surfaces.
     */
    fun derived(argb: Int): BERTPalette? {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        val (hue, saturation, lightness) = Triple(hsl[0], hsl[1], hsl[2])
        if (saturation < 0.15f || lightness < 0.08f || lightness > 0.96f) return null
        val s = saturation.coerceIn(0.25f, 0.6f)
        val background = hslArgb(hue, s * 0.7f, 0.09f)
        val panel = hslArgb(hue, s * 0.6f, 0.16f)
        fun legible(saturation: Float, start: Float): Int {
            var l = start
            while (l < 0.97f) {
                val ink = hslArgb(hue, saturation, l)
                if (ColorUtils.calculateContrast(ink, panel) >= 4.6 && ColorUtils.calculateContrast(ink, background) >= 4.6) return ink
                l += 0.02f
            }
            return CREAM.toInt()
        }
        return BERTPalette(background.argbLong(), panel.argbLong(), CREAM, legible(0.22f, 0.72f).argbLong(),
            legible(maxOf(s, 0.55f), 0.68f).argbLong())
    }

    /**
     * The scene's dominant colour, read from the picture's top edge and the upper part of its sides (the character
     * fills the middle and runs off the bottom). Averaged in RGB, weighted towards saturated pixels so a sky's white
     * does not wash out its colour.
     */
    fun sample(bitmap: Bitmap): Int? {
        val w = bitmap.width; val h = bitmap.height
        if (w < 8 || h < 8) return null
        val ring = maxOf(1, minOf(w, h) / 16)
        var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
        val hsl = FloatArray(3)
        val step = maxOf(1, minOf(w, h) / 64)
        for (y in 0 until h step step) for (x in 0 until w step step) {
            val edge = y < ring || ((x < ring || x >= w - ring) && y < h * 3 / 5)
            if (!edge) continue
            val pixel = bitmap.getPixel(x, y)
            if (pixel ushr 24 < 0x80) continue
            ColorUtils.colorToHSL(pixel, hsl)
            val k = 0.2 + hsl[1]
            r += k * (pixel shr 16 and 0xFF); g += k * (pixel shr 8 and 0xFF); b += k * (pixel and 0xFF); weight += k
        }
        if (weight == 0.0) return null
        return (0xFF shl 24) or ((r / weight).toInt() shl 16) or ((g / weight).toInt() shl 8) or (b / weight).toInt()
    }

    private fun hslArgb(h: Float, s: Float, l: Float): Int = ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
    private fun Int.argbLong(): Long = toLong() and 0xFFFFFFFFL
}
