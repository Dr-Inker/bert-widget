package global.bert.widget

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Home's scene tap targets against its floating You button, swept across phones: page widths, densities, status-bar
 * heights, text sizes (the button's width), every time of day (Bert at every place he stands or walks to, the night
 * window). Pure (placeNaturals, placeTargets, youOverlay), so it is exhaustive and fast.
 *
 * Invariants:
 * - every target is inside the scene;
 * - a target the button doesn't cover, and Bert's always, is exactly where the art puts it;
 * - a moved target meets neither the button nor any other target (other than Bert's: he may stand in front of anything,
 *   and a tap on him is a pat), where that target finally is or where its art is; and keeps a finger's size (MIN_TARGET,
 *   32dp, or its own size if smaller);
 * - no two targets other than Bert's meet;
 * - the result does not depend on the order the targets are listed in.
 */
class PlaceTargetsSweepTest {
    private val widths = listOf(320, 359, 360, 393, 411, 412, 480, 915)
    private val densities = listOf(1.5f, 2f, 2.625f, 3f, 4f)
    private val statusBars = listOf(0, 24, 32, 48, 56, 60, 63, 80)
    private val fontScales = listOf(1f, 1.3f, 1.6f, 2f)

    /** Where Bert can be in [slot]: where he stands, and every other art px along his walk. */
    private fun bertXs(slot: BertSlot): List<Int> = when {
        slot.bertX == null -> listOf(0)
        slot.walk != null -> (slot.walk!!.first..slot.walk!!.last step 2).toList() + slot.bertX!!
        else -> listOf(slot.bertX!!)
    }

    private fun IntRect.meets(o: IntRect) = left < o.right && right > o.left && top < o.bottom && bottom > o.top

    /** One phone, as Home lays it out. */
    private class Phone(val w: Int, val d: Float, val status: Int, val font: Float) {
        fun px(dp: Float) = (dp * d).roundToInt()
        val widthPx = w * d
        val frame = placeFrame(widthPx, (if (w > 600) 412 else (w * 2.17f).roundToInt()) * d)
        // The You button: avatar, gap, "You" (about 30.5dp at normal text) and its padding, as YouButton lays it out.
        val overlay = youOverlay(widthPx.roundToInt(), px(status.toFloat()), px(4f + 40f + 8f + 30.5f * font + 16f), Density(d, font))
        val minTarget = px(32f)
        override fun toString() = "w=${w}dp d=$d status=$status font=$font"
    }

    private fun phones() = sequence {
        for (w in widths) for (d in densities) for (status in statusBars) for (font in fontScales) yield(Phone(w, d, status, font))
    }

    @Test fun noPhoneLetsTheYouButtonPushATargetOntoAnother() {
        var cases = 0
        var moved = 0; var coveredStayed = 0
        val failures = mutableListOf<String>()
        for (phone in phones()) for (slot in BertSlot.entries) for (bertX in bertXs(slot)) {
            cases++
            val naturals = placeNaturals(phone.frame, slot, bertX)
            val scene = phone.frame.sceneRect
            val overlay = phone.overlay
            val finals = placeTargets(naturals, overlay, scene, phone.minTarget)
            val where = "$phone $slot bertX=$bertX"
            // Listed the other way round, or rotated: the same answer.
            val keys = naturals.keys.toList()
            for (order in listOf(keys.reversed(), keys.drop(1) + keys.take(1))) {
                val again = placeTargets(order.associateWith { naturals.getValue(it) }, overlay, scene, phone.minTarget)
                if (again != finals) failures += "$where: order $order gives $again, not $finals"
            }
            for ((tag, target) in naturals) {
                val n = target.natural
                val f = finals.getValue(tag)
                if (!(f.left >= scene.left && f.top >= scene.top && f.right <= scene.right && f.bottom <= scene.bottom))
                    failures += "$where: $tag $f outside the scene $scene"
                val covered = n.meets(overlay)
                if ((!covered || !target.movable) && f != n) failures += "$where: $tag moved from $n to $f though it ${if (target.movable) "was clear of the button" else "never moves"}"
                if (f != n) moved++ else if (covered) coveredStayed++
                if (f != n) {
                    if (f.meets(overlay)) failures += "$where: moved $tag $f is under the button $overlay"
                    for ((other, o) in finals) if (other != tag && other != "place-bert") {
                        if (f.meets(o)) failures += "$where: moved $tag $f meets $other $o"
                        val art = naturals.getValue(other).natural
                        if (f.meets(art)) failures += "$where: moved $tag $f lands on $other's art $art"
                    }
                    if (f.width < minOf(phone.minTarget, n.width) || f.height < minOf(phone.minTarget, n.height)) failures += "$where: moved $tag shrank to $f"
                }
            }
            val still = finals.filterKeys { it != "place-bert" }.entries.toList()
            for (i in still.indices) for (j in i + 1 until still.size)
                if (still[i].value.meets(still[j].value)) failures += "$where: ${still[i].key} ${still[i].value} meets ${still[j].key} ${still[j].value}"
        }
        println("PlaceTargetsSweep: $cases cases, $moved moved targets, $coveredStayed covered targets left where they are, ${failures.size} failures")
        assertTrue("${failures.size} failures in $cases cases, e.g.:\n" + failures.distinct().take(12).joinToString("\n"), failures.isEmpty())
        assertTrue("the sweep should be wide, was $cases", cases > 5_000)
        // The rules for moved and covered targets are only tested if the sweep reaches them.
        assertTrue("no target was ever moved", moved > 100)
        assertTrue("no covered target was ever left where it is", coveredStayed > 100)
    }

    /**
     * Bert's own pixels, read from the sprites the scene draws (every mood, the pat, every walk frame; asleep, the nap),
     * as one box per pose family: (left, top, right, bottom) in frame px, facing right. Not the app's constants: the art.
     */
    private fun spriteBox(names: List<String>, frames: Int = 1): IntArray {
        val box = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
        for (name in names) {
            val img = javax.imageio.ImageIO.read(java.io.File("src/main/res/drawable-nodpi/$name.png"))
            val w = img.width / frames
            for (f in 0 until frames) for (y in 0 until img.height) for (x in 0 until w) if (img.getRGB(f * w + x, y) ushr 24 > 0) {
                box[0] = minOf(box[0], x); box[1] = minOf(box[1], y); box[2] = maxOf(box[2], x + 1); box[3] = maxOf(box[3], y + 1)
            }
        }
        return box
    }
    private val standing by lazy {
        val still = spriteBox(listOf("bert_f10", "bert_pat") + listOf("anxious", "content", "giddy", "melancholy", "restless", "smug").map { "bert_mood_$it" })
        val walk = spriteBox(listOf("bert_walk"), frames = 8)
        intArrayOf(minOf(still[0], walk[0]), minOf(still[1], walk[1]), maxOf(still[2], walk[2]), maxOf(still[3], walk[3]))
    }
    private val napping by lazy { spriteBox(listOf("bert_mood_asleep")) }

    /**
     * All of Bert where he can be at [bertX] (art px): his pixels in every pose, hopping up to 4 art px on a pat, and when
     * he walks ([walking]) facing either way (the sprite flipped about its 84 px frame's middle).
     */
    private fun Phone.body(slot: BertSlot, bertX: Int, walking: Boolean): IntRect? {
        if (slot.bertX == null) return null
        val (b, top) = if (slot == BertSlot.NAP) napping to 232 else standing to 198
        val left = if (walking) minOf(b[0], 84 - b[2]) else b[0]
        val right = if (walking) maxOf(b[2], 84 - b[0]) else b[2]
        return frame.px(bertX + left, top + b[1] - 4, right - left, b[3] - b[1] + 4)
    }

    /** A pat anywhere on him is his: all of Bert, wherever he stands or walks to, is inside his own tap target, on every phone. */
    @Test fun allOfBertIsInsideHisOwnTarget() {
        var cases = 0
        val failures = mutableListOf<String>()
        for (w in widths) for (d in densities) for (slot in BertSlot.entries) for (bertX in bertXs(slot)) {
            val phone = Phone(w, d, 0, 1f)
            val body = phone.body(slot, bertX, walking = slot.walk != null) ?: continue
            cases++
            val bert = placeNaturals(phone.frame, slot, bertX).getValue("place-bert").natural
            if (body.left < bert.left || body.top < bert.top || body.right > bert.right || body.bottom > bert.bottom)
                failures += "$phone $slot: Bert $body at x=$bertX is not all inside his tap target $bert"
        }
        assertTrue("${failures.size} failures in $cases cases, e.g.:\n" + failures.take(12).joinToString("\n"), failures.isEmpty())
        assertTrue("the sweep should reach every pose, was $cases", cases > 1_000)
    }
}
