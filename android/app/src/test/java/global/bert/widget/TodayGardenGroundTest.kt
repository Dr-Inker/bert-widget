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
    private val stills = listOf("bert_f10", "bert_mood_content", "bert_mood_giddy", "bert_mood_smug", "bert_mood_anxious",
        "bert_mood_melancholy", "bert_mood_restless").map { it to ImageIO.read(File("src/main/res/drawable-nodpi/$it.png")) }

    @Test fun `the banner art is the size the code draws`() {
        assertEquals(GARDEN_W, garden.width)
        assertEquals(GARDEN_H, garden.height)
    }

    @Test fun `his feet land on grass with soil beneath, in every mood and across his walk`() {
        for ((name, bert) in stills) {
            // Lowest opaque row of the still: where his feet are. It must sit on the grass line, not above it.
            val feet = (FRAME - 1 downTo 0).first { y -> (0 until FRAME).any { x -> (bert.getRGB(x, y) ushr 24) > 0 } }
            val feetRow = FEET_TOP + feet
            assertTrue("$name: feet on row $feetRow float above the grass", feetRow >= 101)
            val stance = (0 until FRAME).filter { x -> (bert.getRGB(x, feet) ushr 24) > 0 }.map { REST + it }
            for (dx in listOf(-28, -24, 0)) for (x in stance) { // walk-in start, pacing end, rest
                // Grass may carry flowers, outlines and tall tufts (up to ~13 rows deep here); what must be there is soil beneath it.
                val below = (feetRow + 1..minOf(feetRow + 16, GARDEN_H - 1)).map { garden.getRGB(x + dx, it) }
                assertTrue("$name x=${x + dx}: no soil under his feet", below.any(::isSoil))
            }
        }
    }

    @Test fun `every feed mood has its own still and unknown moods fall back to F10`() {
        val moods = listOf("content", "giddy", "smug", "anxious", "melancholy", "restless")
        assertEquals(moods.size, moods.map(::moodStill).toSet().size)
        assertEquals(R.drawable.bert_f10, moodStill("sleepy"))
        assertEquals(R.drawable.bert_f10, moodStill(null))
        assertEquals(R.drawable.bert_mood_anxious, moodStill(" Anxious "))
    }

    // Soil is a warm mid brown; sky, fence pickets, water and petals are not.
    private fun isSoil(argb: Int): Boolean {
        val r = argb shr 16 and 0xFF; val g = argb shr 8 and 0xFF; val b = argb and 0xFF
        return r in 100..215 && r > g && g > b && r - b > 60
    }
}
