package global.bert.widget

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 0.14: where the place meets the page, and Bert's line as speech.
 *
 * A tester read the old line, the biggest text on Home with nothing saying who said it, as a cryptic headline, and the
 * scene stopped in a hard edge just above it. Now the loam fades into the page in dithered pixel steps, through darker
 * loam, and the line is a speech bubble standing in that fade with its tail up at Bert. Everything here is drawn on the
 * scene's own art-pixel grid (k device px per art px, from placeFrame), so the bubble's corners, outline and tail are
 * pixel art like the garden, not a rounded card laid over it.
 */

// The fade, from the scene's last row of loam to the page: four bands, each taller than the last, so the dark end
// lingers the way WoofHub's hero sinks into its page.
private val BANDS = intArrayOf(7, 9, 12, 16)
/** The fade's height in art px. */
internal val JOIN_ROWS = BANDS.sum()
// The bubble's top edge is a little into the scene's loam (place rows; the scene is 298 tall), and its tail reaches
// up to the row just under Bert's feet on the grass line (row 282), so it points at him without touching him.
private const val BUBBLE_TOP = 295
private const val TAIL_TIP = 283
// At night the line comes from indoors: three puffs rise this far above the bubble, the way a dream does in a comic.
private const val PUFF_RISE = 26
private const val SCENE_ROWS = 298
// Inside the bubble, in art px: room round the words, and the stepped corner (how far in each of the top rows starts).
private const val PAD_X = 9
private const val PAD_Y = 5
private val CORNER = intArrayOf(3, 1, 1)
// The tail is a stepped wedge: one edge straight up under Bert, the other a clean 2:1 pixel diagonal, two pixels wide at
// the tip and TAIL_W where it opens into the bubble. (A one-pixel tip of seven read as a notch in the paper, not a
// tail, on the first renders: it needs paper inside its outline to be seen against the loam.)
private const val TAIL_W = 8

/** The bubble's paper in the scene's light: bright by day, dimmer at dusk, moonlit at night so it never glares. */
internal data class BubbleTone(val outline: Color, val fill: Color, val shade: Color, val ink: Color) {
    companion object {
        fun of(time: PlaceTime) = when (time) {
            PlaceTime.DUSK -> BubbleTone(Color(0xFF24130F), Color(0xFFE6D8BE), Color(0xFFD2C0A0), Color(0xFF24160E))
            PlaceTime.NIGHT -> BubbleTone(Color(0xFF07050A), Color(0xFFC3B7A1), Color(0xFFAFA28A), Color(0xFF1D120B))
            else -> BubbleTone(Color(0xFF3A2416), Color(0xFFF7EEDA), Color(0xFFE6D5B5), Color(0xFF2A1A0F))
        }
    }
}

/**
 * [line] in a bubble from Bert, standing in the fade from the scene to the page. The bubble is sized to the words and
 * set under [anchor] (place art x); the tail follows [speaker] (read at draw time, so it swings as he walks without
 * moving the words). [toWindow]: he is indoors, and the bubble trails puffs up towards the lit window instead.
 * [resting] is the line he goes back to: the space is kept for whichever of the two is taller, so a pat never makes the
 * page below jump.
 */
@Composable
internal fun BertSays(line: String, resting: String, time: PlaceTime, earth: Color, toWindow: Boolean, anchor: Int, speaker: () -> Int) {
    val tone = BubbleTone.of(time)
    val page = Navy
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val frame = placeFrame(with(density) { maxWidth.toPx() }, LocalConfiguration.current.screenHeightDp * density.density)
        val k = frame.k
        val style = TextStyle(fontFamily = BertHand, fontSize = 19.sp, lineHeight = 24.sp, color = tone.ink)
        val margin = with(density) { 16.dp.roundToPx() }
        // Where the bubble ended up (art px from the scene's left edge, and its size): set by the layout, read only when
        // drawing, which comes after layout in the same frame.
        var box by remember { mutableStateOf(Triple(0, 0, 0)) }
        val fade = remember(earth, page, frame) { fadeBitmap(earth, page, frame.shown) }
        val bubbles = remember { BubbleCache() }
        Layout(content = {
            Text(line, style = style)
            Text(resting, style = style, modifier = Modifier.clearAndSetSemantics {})
        }, modifier = Modifier.fillMaxWidth().testTag("place-line")
            .semantics(mergeDescendants = true) { contentDescription = "Bert says: $line"; liveRegion = LiveRegionMode.Polite }
            .drawBehind {
                val (left, w, h) = box
                run {
                    // The fade, under the scene only (it is narrower than the page in landscape), then the page colour.
                    drawImage(fade, dstOffset = IntOffset(frame.left, 0), dstSize = IntSize(fade.width * k, fade.height * k),
                        filterQuality = FilterQuality.None)
                    if (w <= 0) return@run
                    val bubble = bubbles.get(w, h, speaker() - frame.x0 - left, toWindow, tone)
                    // The bitmap's extra rows (tail or puffs) stand above the bubble's top edge, which is in the loam.
                    val top = -(SCENE_ROWS - BUBBLE_TOP) - (bubble.height - h)
                    drawImage(bubble, dstOffset = IntOffset(frame.left + left * k, top * k),
                        dstSize = IntSize(bubble.width * k, bubble.height * k), filterQuality = FilterQuality.None)
                }
            }) { measurables, constraints ->
            val areaLeft = frame.left
            val areaWidth = minOf(frame.shown * k, (maxWidth.value * density.density).toInt())
            val textMax = (areaWidth - 2 * margin - 2 * PAD_X * k).coerceAtLeast(k)
            val said = measurables[0].measure(Constraints(maxWidth = textMax))
            val rest = measurables[1].measure(Constraints(maxWidth = textMax))
            fun art(px: Int) = (px + k - 1) / k
            val w = art(said.width) + 2 * PAD_X
            val h = art(said.height) + 2 * PAD_Y
            val tallest = maxOf(h, art(rest.height) + 2 * PAD_Y)
            // Centred under its anchor, kept off the page's edges, on whole art pixels.
            val minLeft = art(margin); val maxLeft = areaWidth / k - art(margin) - w
            val left = (anchor - frame.x0 - w / 2).coerceAtMost(maxLeft).coerceAtLeast(minLeft)
            box = Triple(left, w, h)
            val top = -(SCENE_ROWS - BUBBLE_TOP) * k
            val height = maxOf(JOIN_ROWS * k, top + tallest * k + 4 * k)
            layout(constraints.maxWidth, height) {
                // The words centred in the bubble's whole-pixel box.
                said.place(areaLeft + left * k + (w * k - said.width) / 2, top + (h * k - said.height) / 2)
            }
        }
    }
}

/**
 * The fade, one bitmap pixel per art px: the scene's loam, two darker loams, a near-page brown, then the page colour.
 * Each band holds solid for its first few rows and then dithers (4x4 ordered) into the next, an eighth or so at a time,
 * so it reads as pixel steps, not a blur.
 */
private fun fadeBitmap(earth: Color, page: Color, cols: Int): ImageBitmap {
    val levels = listOf(0f, 0.34f, 0.62f, 0.84f, 1f).map { lerp(earth, page, it).toArgb() }
    val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    val pixels = IntArray(cols * JOIN_ROWS)
    var row = 0
    BANDS.forEachIndexed { band, rows ->
        val solid = (rows * 3 + 5) / 10
        for (i in 0 until rows) {
            // How much of the next colour this row has: none while solid, then up to just short of all of it.
            val mix = if (i < solid) 0f else (i - solid + 1f) / (rows - solid + 1f)
            for (c in 0 until cols) {
                val next = (bayer[(row % 4) * 4 + c % 4] + 0.5f) / 16f < mix
                pixels[row * cols + c] = levels[band + if (next) 1 else 0]
            }
            row++
        }
    }
    return Bitmap.createBitmap(pixels, cols, JOIN_ROWS, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Where the tail (or the puffs) leaves the bubble for a [target] in art px: kept clear of the stepped corners, so it always opens out of a straight edge. */
private fun tailX(w: Int, target: Int): Int {
    val safe = CORNER[0] + TAIL_W + 1
    return target.coerceIn(safe, (w - 1 - safe).coerceAtLeast(safe))
}

/**
 * The last bubble drawn, reused until something that shapes it changes. The tail follows Bert while he walks (a new
 * target every 110 ms step) and the draw runs on every frame the node is invalidated; building a bitmap each time, for a
 * bubble whose size, tail and paper are unchanged, was waste. Keyed on what actually reaches the pixels: the tail's
 * clamped position (a target past the corner draws the same tail) and, for the night puffs, which way they lean.
 */
internal class BubbleCache {
    private data class Key(val w: Int, val h: Int, val tail: Int, val leanLeft: Boolean, val toWindow: Boolean, val tone: BubbleTone)
    private var key: Key? = null
    private var image: ImageBitmap? = null

    fun get(w: Int, h: Int, target: Int, toWindow: Boolean, tone: BubbleTone): ImageBitmap {
        val tail = tailX(w, target)
        val next = Key(w, h, tail, toWindow && target < tail, toWindow, tone)
        image?.let { if (next == key) return it }
        return bubbleBitmap(w, h, tail, next.leanLeft, toWindow, tone).also { key = next; image = it }
    }
}

/**
 * The bubble, one bitmap pixel per art px: [w] x [h] with stepped corners, plus the rows above it for the tail (or, at
 * night, the puffs). [edgeX] is where the tail leaves the bubble, in art px from its left edge (see [tailX]); [leanLeft]
 * turns the puffs the other way. Drawn as a mask first, then every masked pixel with an unmasked neighbour is outline, so
 * the tail and the bubble share one unbroken line.
 */
private fun bubbleBitmap(w: Int, h: Int, edgeX: Int, leanLeft: Boolean, toWindow: Boolean, tone: BubbleTone): ImageBitmap {
    val rise = if (toWindow) PUFF_RISE else BUBBLE_TOP - TAIL_TIP
    val rows = h + rise
    val mask = BooleanArray(w * rows)
    val puff = BooleanArray(w * rows)
    fun set(x: Int, y: Int) { if (x in 0 until w && y in 0 until rows) mask[y * w + x] = true }
    for (y in 0 until h) {
        val edge = minOf(y, h - 1 - y)
        val inset = if (edge < CORNER.size) CORNER[edge] else 0
        for (x in inset until w - inset) set(x, rise + y)
    }
    // The diagonal falls away towards the bubble's middle, so the wedge opens into it.
    val dir = if (edgeX < w / 2) 1 else -1
    if (!toWindow) {
        // Row i up from the bubble (1 = just above its top edge, rise = the tip).
        for (i in 1..rise + 1) {
            val width = (2 + (rise + 1 - i) / 2).coerceAtMost(TAIL_W)
            for (d in 0 until width) set(edgeX + dir * d, rise - i + 1)
        }
    } else {
        // Three puffs, each smaller and higher, leaning towards the window: a voice from indoors, asleep. They are kept
        // apart from the bubble and each other (they are paper of their own, not part of its outline), and drawn
        // without one: at night the outline is the colour of the dark grass and a puff that small would vanish into it.
        val lean = if (leanLeft) -1 else 1
        var bottom = rise - 3
        for ((at, size) in listOf(1 to (13 to 8), 5 to (8 to 6), 8 to (5 to 4))) {
            val (pw, ph) = size
            val x0 = edgeX + lean * at - pw / 2
            for (y in 0 until ph) {
                // Stepped in at the top and bottom rows (two then one on the big puff), so each is round in pixel terms.
                val edge = minOf(y, ph - 1 - y)
                val inset = when { edge == 0 -> if (ph >= 7) 2 else 1; edge == 1 && ph >= 7 -> 1; else -> 0 }
                for (x in inset until pw - inset) {
                    val px = x0 + x; val py = bottom - ph + y
                    if (px in 0 until w && py in 0 until rows) puff[py * w + px] = true
                }
            }
            bottom -= ph + 2
        }
    }
    val pixels = IntArray(w * rows)
    val outline = tone.outline.toArgb(); val fill = tone.fill.toArgb(); val shade = tone.shade.toArgb()
    fun on(x: Int, y: Int) = x in 0 until w && y in 0 until rows && mask[y * w + x]
    for (y in 0 until rows) for (x in 0 until w) {
        if (puff[y * w + x]) {
            // Each puff's bottom row is the paper's shade, so it has the bubble's thickness too.
            pixels[y * w + x] = if (y + 1 < rows && puff[(y + 1) * w + x]) fill else shade
            continue
        }
        if (!on(x, y)) continue
        val edge = !on(x - 1, y) || !on(x + 1, y) || !on(x, y - 1) || !on(x, y + 1)
        // The two rows above each bottom line are a shade darker: the paper's thickness, stepped, not blurred.
        pixels[y * w + x] = when {
            edge -> outline
            !on(x, y + 2) || !on(x, y + 3) -> shade
            else -> fill
        }
    }
    return Bitmap.createBitmap(pixels, w, rows, Bitmap.Config.ARGB_8888).asImageBitmap()
}
