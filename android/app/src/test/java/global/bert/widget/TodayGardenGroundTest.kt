package global.bert.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Bert must stand on the garden's ground, never float. Owner, 2026-10-12: "he's floating over a fence" (the old crop
 * put him over the pond at fence height). Checks the art under his feet is grass or soil, not sky, fence or water.
 */
class TodayGardenGroundTest {
    private val garden = ImageIO.read(File("src/main/res/drawable-nodpi/today_garden.png"))
    private val bert = ImageIO.read(File("src/main/res/drawable-nodpi/bert_f10.png"))

    @Test fun `the banner art is the size the code draws`() {
        assertEquals(GARDEN_W, garden.width)
        assertEquals(GARDEN_H, garden.height)
    }

    @Test fun `his feet land on grass with soil beneath, across his whole stance`() {
        // Lowest opaque row of the still: where his feet are.
        val feet = (FRAME - 1 downTo 0).first { y -> (0 until FRAME).any { x -> (bert.getRGB(x, y) ushr 24) > 0 } }
        val feetRow = FEET_TOP + feet
        val stance = (0 until FRAME).filter { x -> (bert.getRGB(x, feet) ushr 24) > 0 }.map { REST + it }
        for (dx in listOf(-20, 0, 20)) for (x in stance) { // at rest and at both ends of his walk
            val below = (feetRow + 1..minOf(feetRow + 12, GARDEN_H - 1)).map { garden.getRGB(x + dx, it) }
            assertTrue("x=${x + dx}: nothing solid under his feet", below.count(::isGround) >= below.size * 2 / 3)
        }
    }

    // Grass is green-dominant; soil is warm brown. Sky, fence pickets and water are blue-grey or pale.
    private fun isGround(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF; val g = argb shr 8 and 0xFF; val b = argb and 0xFF
        val grass = g > r && g > b + 15
        val soil = r > g && g > b && r - b > 40
        return grass || soil
    }
}
