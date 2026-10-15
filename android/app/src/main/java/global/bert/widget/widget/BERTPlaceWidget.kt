package global.bert.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import global.bert.widget.BERTEntry
import global.bert.widget.BertSlot
import global.bert.widget.MainActivity
import global.bert.widget.R
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.moodStill
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

/**
 * “Bert’s garden” (0.12): a small window onto Home’s scene at the same time of day, with Bert where his routine has
 * him and in his mood's pose. No text and no numbers; the price widgets are there for numbers. Tapping opens Home.
 * It moves to the next part of his day on the regular widget refresh.
 */
class BERTPlaceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val revision = currentState(REVISION) ?: 0
            val mood = remember(revision) { BERTActivityRepository(context).load()?.mood }
            PlaceContent(context, mood)
        }
    }
}

class BERTPlaceWidgetReceiver : BERTWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = BERTPlaceWidget()
}

@Composable
private fun PlaceContent(context: Context, mood: String?) {
    val size: DpSize = LocalSize.current
    val density = context.resources.displayMetrics.density
    val slot = BertSlot.at(ZonedDateTime.now().hour)
    val bitmap = remember(slot, mood, size) { PlaceStamp.render(context, slot, mood, (size.width.value * density).toInt(), (size.height.value * density).toInt()) }
    Image(ImageProvider(bitmap), contentDescription = PlaceStamp.describe(slot), contentScale = ContentScale.Crop,
        modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp).clickable(actionStartActivity<MainActivity>(entryParameters(BERTEntry.HOME))))
}

/** Renders the widget's picture: a crop of the place image around Bert and the gate, at a whole number of pixels per art pixel. */
internal object PlaceStamp {
    private const val PLACE_W = 400
    private const val PLACE_H = 298
    private const val WIDE_W = 580       // place_wide_*: world x 590..1170
    private const val WIDE_SHIFT = 180   // 770 - 590
    private const val FEET_TOP = 198
    private const val NAP_TOP = 232

    fun render(context: Context, slot: BertSlot, mood: String?, widthPx: Int, heightPx: Int): Bitmap {
        val w = widthPx.coerceIn(80, 1200); val h = heightPx.coerceIn(80, 1200)
        val options = BitmapFactory.Options().apply { inScaled = false }
        val aspect = w.toFloat() / h
        // Wide widgets use a wider bake of the same scene, so the whole roof and both gateposts stay in frame.
        val wide = aspect >= 1.4f
        val scene = BitmapFactory.decodeResource(context.resources, if (wide) slot.time.wide else slot.time.image, options)
        val artW = if (wide) WIDE_W else PLACE_W
        val shift = if (wide) WIDE_SHIFT else 0          // place x -> wide image x
        var cropH = PLACE_H
        var cropW = (cropH * aspect).toInt()
        if (cropW > artW) { cropW = artW; cropH = (cropW / aspect).toInt().coerceAtMost(PLACE_H) }
        // Square-ish: frame the garden left of the gate when he's out on the lawn, or the gate and the board when he's
        // there; never cut a gatepost in half.
        var left = if (wide) artW - cropW else if ((slot.bertX ?: 300) < 150) 0 else PLACE_W - cropW
        if (!wide && left == 0 && cropW > 248) { cropW = 248; cropH = (cropW / aspect).toInt().coerceAtMost(PLACE_H) }
        // A tall, narrow widget: if that frame would miss him, centre it on him instead (his sprite is x+10..x+74).
        slot.bertX?.let { x -> val bx = x + shift
            if (bx + 10 < left || bx + 74 > left + cropW) left = (bx + 42 - cropW / 2).coerceIn(0, artW - cropW)
        }
        val top = PLACE_H - cropH
        val k = max(1, min(w / cropW, h / cropH).coerceAtLeast(1))
        val out = Bitmap.createBitmap(cropW * k, cropH * k, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val crisp = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        canvas.drawBitmap(scene, Rect(left, top, left + cropW, top + cropH), Rect(0, 0, out.width, out.height), crisp)
        // The chalkboard carries a chalk paw here (the widget has no numbers); its slate is at place (148..180, 251..275).
        val chalk = Paint().apply { color = android.graphics.Color.argb(if (slot.time.light) 225 else 140, 237, 235, 228) }
        global.bert.widget.ChalkFont.PAW.forEachIndexed { row, bits -> bits.forEachIndexed { col, on ->
            if (on == '#') {
                val x = 148 + 12 + col + shift - left; val y = 251 + 8 + row - top
                canvas.drawRect((x * k).toFloat(), (y * k).toFloat(), ((x + 1) * k).toFloat(), ((y + 1) * k).toFloat(), chalk)
            }
        } }
        val pose = when {
            slot.bertX == null -> null
            slot == BertSlot.NAP -> R.drawable.bert_mood_asleep
            else -> moodStill(mood)
        }
        if (pose != null && slot.bertX != null) {
            val bert = BitmapFactory.decodeResource(context.resources, pose, options)
            val y = if (slot == BertSlot.NAP) NAP_TOP else FEET_TOP
            val paint = Paint(crisp).apply { colorFilter = filter(slot) }
            canvas.drawBitmap(bert, Rect(0, 0, bert.width, bert.height),
                Rect((slot.bertX + shift - left) * k, (y - top) * k, (slot.bertX + shift - left + bert.width) * k, (y - top + bert.height) * k), paint)
        }
        return out
    }

    /** The same light Home draws him in (PlaceTime's grade at his height). */
    private fun filter(slot: BertSlot): ColorMatrixColorFilter? {
        val (tint, desat) = when (slot.time) {
            global.bert.widget.PlaceTime.DAWN -> floatArrayOf(1.0f, 0.848f, 0.796f) to 0.05f
            global.bert.widget.PlaceTime.DAY -> return null
            global.bert.widget.PlaceTime.GOLDEN -> floatArrayOf(1.0f, 0.876f, 0.692f) to 0f
            global.bert.widget.PlaceTime.DUSK -> floatArrayOf(0.496f, 0.408f, 0.576f) to 0.18f
            global.bert.widget.PlaceTime.NIGHT -> floatArrayOf(0.12f, 0.128f, 0.244f) to 0.4f
        }
        val m = ColorMatrix().apply { setSaturation(1f - desat) }
        m.postConcat(ColorMatrix().apply { setScale(tint[0], tint[1], tint[2], 1f) })
        return ColorMatrixColorFilter(m)
    }

    fun describe(slot: BertSlot): String = when (slot) {
        BertSlot.GATE -> "Bert’s garden in the morning. Bert is out by the gate. Opens BERT."
        BertSlot.ROUNDS -> "Bert’s garden. Bert is on his rounds. Opens BERT."
        BertSlot.NAP -> "Bert’s garden. Bert is asleep in the sun. Opens BERT."
        BertSlot.POST -> "Bert’s garden in the golden afternoon. Bert is on gate duty. Opens BERT."
        BertSlot.EVENING -> "Bert’s garden at dusk, the lamp lit. Opens BERT."
        BertSlot.BED -> "Bert’s garden at night. He’s asleep inside; the window is lit. Opens BERT."
    }
}
