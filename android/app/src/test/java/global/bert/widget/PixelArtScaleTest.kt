package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * ART-2 and visual-art-added-1 (critique 12 November): the You avatar and the Lost Trail picture on Home are pixel art
 * drawn hard-edged. They used to be stretched to fit, so on a 1080 px phone (density 2.625) their pixels came out
 * uneven, 2 and 3 device px side by side, while the 720 px test width happened to land on whole pixels. Drawn here at
 * 2.625, every run of one colour across the avatar must be a whole number of the same pixel size, and the picture
 * must be the art pixel for pixel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PixelArtScaleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** The lengths of the runs of one colour along row [y], from [x0] to [x1], leaving out the two cut by the ends. */
    private fun runs(bitmap: Bitmap, y: Int, x0: Int, x1: Int): List<Int> {
        val out = mutableListOf<Int>()
        var start = x0
        for (x in x0 + 1..x1) if (x == x1 || bitmap.getPixel(x, y) != bitmap.getPixel(x - 1, y)) { out += x - start; start = x }
        return out.drop(1).dropLast(1)
    }

    private fun capture(name: String): Bitmap {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    @Test fun theAvatarAndTheTrailPictureKeepWholePixelsAt2625() {
        compose.setContent {
            BERTTheme {
                Column(Modifier.fillMaxSize().background(Navy).padding(horizontal = 20.dp)) {
                    YouAvatar(40.dp, null, Modifier.testTag("avatar-40"))
                    YouAvatar(72.dp, null, Modifier.testTag("avatar-72"))
                    LostTrailContinueCard(null) {}
                }
            }
        }
        val bitmap = capture("pixel-scale-2625")
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(2.625f, density, 0.001f)
        for (tag in listOf("avatar-40", "avatar-72")) {
            val r = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
            // Across the middle, inside the circle's edge, row by row.
            val inset = (r.width * 0.2f).toInt()
            val all = (r.top.toInt() + inset until r.bottom.toInt() - inset).flatMap { y -> runs(bitmap, y, r.left.toInt() + inset, r.right.toInt() - inset) }
            assertTrue("$tag: too few runs to judge: $all", all.size >= 8)
            val unit = all.reduce(::gcd)
            assertTrue("$tag: its pixels are uneven at 2.625, runs $all", unit >= 2)
        }
        // The picture: whole device pixels per art pixel, the full height of the art, and as wide as the column to within
        // one art pixel. Every device pixel is exactly the art pixel that trailFit's window puts there, so no art pixel is
        // wider than another. At 2.625 the column is 976 px: 2x, art pixels 40 to 527 (the gate through the volcano).
        val art = compose.onNodeWithTag("trail-art", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val column = compose.activity.window.decorView.width - 2 * 20 * density
        val fit = trailFit(kotlin.math.round(art.width).toInt())
        assertEquals("a 1080 px phone draws the picture at 2x", 2, fit.k)
        assertTrue("from the gate to the volcano: $fit", fit.start in 36..TRAIL_GATE_X && fit.start + fit.shown in TRAIL_VOLCANO_X..TRAIL_AURORA_X)
        assertTrue("the picture fills the column: ${art.width} of $column", column - art.width < fit.k + 1.5f && art.width <= column + 0.5f)
        assertEquals("the picture's full height", 360f * fit.k, art.height, 0.5f)
        assertEquals("centred in the column", compose.activity.window.decorView.width / 2f, art.center.x, 1f)
        val source = android.graphics.BitmapFactory.decodeResource(compose.activity.resources, R.drawable.lost_trail,
            android.graphics.BitmapFactory.Options().apply { inScaled = false })
        val cell = source.width / 640
        var checked = 0; var same = 0
        for (y in 0 until 360 * fit.k step 3) for (x in 0 until fit.shown * fit.k step 3) {
            val drawn = bitmap.getPixel(kotlin.math.round(art.left).toInt() + x, kotlin.math.round(art.top).toInt() + y)
            val want = source.getPixel((fit.start + x / fit.k) * cell, y / fit.k * cell)
            checked++
            if (listOf(16, 8, 0).all { shift -> kotlin.math.abs((drawn shr shift and 0xFF) - (want shr shift and 0xFF)) <= 3 }) same++
        }
        assertTrue("the trail picture is the art pixel for pixel: $same of $checked samples match", same >= checked * 99 / 100)
    }

    /**
     * The whole-pixel fit, at the densities of common phones (2.0, 2.625, 3.0, 3.5): the You button's 40dp and 72dp
     * avatar from its 42 art px head. Every art pixel is k x k whole device px, and the head fills its box to within one
     * art pixel unless all of it is shown.
     */
    @Test fun pixelFitIsWholePixelsAtCommonDensities() {
        fun px(dp: Int, density: Float) = kotlin.math.floor(dp * density + 0.5f).toInt()
        // Worked by hand: 80/42 = 1.9 -> 2 (40 shown); 105/42 = 2.5 -> 3 (35); 120/42 = 2.86 -> 3 (40); 140/42 = 3.33 -> 3 (42, centred).
        val avatar = mapOf(2f to PixelFit(2, 40), 2.625f to PixelFit(3, 35), 3f to PixelFit(3, 40), 3.5f to PixelFit(3, 42))
        for ((density, want) in avatar) assertEquals("40dp avatar at $density", want, pixelFit(px(40, density), 42))
        for (density in listOf(2f, 2.625f, 3f, 3.5f)) for ((box, source) in listOf(px(40, density) to 42, px(72, density) to 42)) {
            val fit = pixelFit(box, source)
            assertTrue("$box from $source at $density: $fit is bigger than its box", fit.size <= box)
            assertTrue("$box from $source at $density: $fit leaves more than an art pixel", fit.shown == source || box - fit.size < fit.k)
            assertTrue("$box from $source at $density: $fit is not near the box's own scale", kotlin.math.abs(fit.k - box.toFloat() / source) <= 0.5f)
        }
    }

    /**
     * Review of 0.14.2, then the lead's look at the renders. A centred 2x crop on a 1080 px phone sliced the moon and
     * lost the gate's wall and the castle; the whole picture at 1x was a stamp two-thirds of the column wide. So the
     * picture fills the column at whole pixels, its full height shows (the moon is never cut), and the window is chosen:
     * the gate and the volcano are always in, and the castle is either whole or left out, never cut through its towers.
     */
    @Test fun theTrailPictureFillsTheColumnAndKeepsTheGateAndTheVolcano() {
        fun px(dp: Int, density: Float) = kotlin.math.floor(dp * density + 0.5f).toInt()
        // 320dp and 371dp columns (360dp and 411dp phones). 640 at 2.0 -> whole at 1x; 840 -> whole at 1x (the one stamp
        // left: 1.31 is nearer 1 than 2); 960 (the S25) and 974 -> 2x from the gate to the volcano; 1113 -> 2x from the
        // left edge, ending in the aurora before the castle's towers; 1299 -> the whole picture at 2x.
        val want = mapOf(px(320, 2f) to TrailFit(1, 0, 640), px(320, 2.625f) to TrailFit(1, 0, 640),
            px(320, 3f) to TrailFit(2, 48, 480), px(371, 2.625f) to TrailFit(2, 41, 487),
            px(371, 3f) to TrailFit(2, 0, 556), px(371, 3.5f) to TrailFit(2, 0, 640))
        for ((box, fit) in want) assertEquals("trail in a $box px column", fit, trailFit(box))
        val towers = 574..626 // the castle's towers, measured on the art
        for (box in 640..3000) {
            val fit = trailFit(box)
            val end = fit.start + fit.shown
            assertTrue("$box: $fit runs outside the art", fit.start >= 0 && end <= TRAIL_ART_W)
            assertTrue("$box: $fit is bigger than its box", fit.shown * fit.k <= box)
            assertTrue("$box: $fit leaves the column unfilled though it crops", fit.shown == TRAIL_ART_W || box - fit.shown * fit.k < fit.k)
            assertTrue("$box: $fit is under two-thirds of the column", fit.shown * fit.k * 3 >= box * 2)
            assertTrue("$box: $fit loses the gate", fit.start <= TRAIL_GATE_X)
            assertTrue("$box: $fit loses the volcano", end >= TRAIL_VOLCANO_X)
            assertTrue("$box: $fit cuts through the castle's towers", end <= towers.first || end > towers.last)
        }
    }
}
