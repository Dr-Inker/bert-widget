package global.bert.widget

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Home's scene tap targets against its floating You button, swept across phones: page widths, densities, status-bar
 * heights, text sizes (the button's width), every time of day (Bert at every place he stands or walks to, the night
 * window) and the gate on and off. Pure (placeNaturals, placeTargets, youOverlay), so it is exhaustive and fast.
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
    private val widths = listOf(320, 359, 360, 393, 411, 480, 915)
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
        for (phone in phones()) for (slot in BertSlot.entries) for (bertX in bertXs(slot)) for (gate in listOf(true, false)) {
            cases++
            val naturals = placeNaturals(phone.frame, slot, bertX, gate)
            val scene = phone.frame.sceneRect
            val overlay = phone.overlay
            val finals = placeTargets(naturals, overlay, scene, phone.minTarget)
            val where = "$phone $slot bertX=$bertX gate=$gate"
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
        assertTrue("the sweep should be wide, was $cases", cases > 10_000)
        // The rules for moved and covered targets are only tested if the sweep reaches them.
        assertTrue("no target was ever moved", moved > 100)
        assertTrue("no covered target was ever left where it is", coveredStayed > 100)
    }

    /**
     * A label's tag as the app measures it, near enough for the sweep: Outfit bold at 13sp (growing with the phone's text
     * size up to 1.6x), about 0.62em a character, 10dp either side and 5dp above and below.
     */
    private fun Phone.measure(text: String, widthBy: Float): androidx.compose.ui.unit.IntSize {
        val em = 13f * minOf(font, 1.6f) * d
        return androidx.compose.ui.unit.IntSize((text.length * 0.62f * em * widthBy).roundToInt() + 2 * px(10f), (em * 1.2f).roundToInt() + 2 * px(5f))
    }

    /** Bert's face where it can be at [bertX] (art px, as in labelKeepOff but per position): standing, hopping, either way. */
    private fun Phone.face(slot: BertSlot, bertX: Int): IntRect? = when {
        slot.bertX == null -> null
        slot == BertSlot.NAP -> frame.px(bertX + 7, 232 + 12, 70, 38)
        else -> frame.px(bertX + 18, 198 - 4, 48, 52)
    }

    /**
     * The labels, swept over the same phones, every time of day, the gate on and off, and no, a few and many unread notes.
     * Invariants: each label is inside the scene and ends above the loam (where Bert's bubble and its tail are); no two
     * meet; none meets the You button, Bert's face wherever it can be that part of the day, the chalked price, or at night
     * the window's target or the dream puffs; each one's tail is on its own thing; none sits on another thing's middle.
     * Shortened, left-out and floating labels are counted, and on ordinary phones (an art pixel about a dp or more, text
     * up to 1.3x) none is left out (at the text-width estimate and 15% either side of it), none floats (at the estimate),
     * and "Play" is on the gate or stands on its pickets. A label's tap target is its pill, inside what it draws; its
     * tail is not one.
     */
    /** Ordinary: an art pixel at least about a dp (most phones; the grid also pairs narrow widths with dense screens no phone has), text up to 1.3x. */
    private fun ordinary(phone: Phone) = phone.frame.k / phone.d >= 0.95f && phone.font <= 1.3f

    @Test fun noLabelEverCoversAnotherOrBertsFaceOrTheButton() {
        var cases = 0; var shortened = 0; var leftOut = 0; var leftOutOrdinary = 0; var floating = 0
        val failures = mutableListOf<String>()
        // The text widths are an estimate of the app's own measure, so the sweep holds 15% either side of it.
        for (widthBy in listOf(0.85f, 1f, 1.15f)) for (phone in phones()) for (slot in BertSlot.entries) for (gate in listOf(true, false)) for (notes in listOf(0, 3, 12)) {
            cases++
            val frame = phone.frame
            val scene = frame.sceneRect
            val finals = placeTargets(placeNaturals(frame, slot, slot.bertX ?: 0, gate), phone.overlay, scene, phone.minTarget)
            val window = finals["place-window"]
            val dims = LabelDims(phone.px(6f), phone.px(5f), phone.px(2f), phone.px(6f), phone.px(6f))
            val anchors = labelAnchors(frame, gate)
            val ground = labelGround(frame)
            val labels = placeLabels(anchors, { labelTexts(it, notes) }, { phone.measure(it, widthBy) }, scene, ground,
                labelKeepOff(frame, slot, window) + phone.overlay, dims)
            val where = "$phone $slot gate=$gate notes=$notes width x$widthBy"
            if (labels.keys != anchors.keys) failures += "$where: labels for ${labels.keys}, things ${anchors.keys}"
            val placed = labels.entries.mapNotNull { (spot, l) -> l?.let { spot to it } }
            for ((spot, l) in labels) if (l == null) {
                leftOut++
                // Ordinary: an art pixel at least about a dp (most phones; the grid also pairs narrow widths with dense
                // screens no phone has), text up to 1.3x.
                if (ordinary(phone)) { leftOutOrdinary++; failures += "$where: the ${spot.name} label is left out on an ordinary phone" }
            } else {
                if (l.text != labelTexts(spot, notes).first()) shortened++
                // At the estimate itself none floats on an ordinary phone; 15% wider, a few may stand clear (never left out).
                if (l.floating) { floating++; if (ordinary(phone) && widthBy == 1f) failures += "$where: the ${spot.name} label floats above its thing on an ordinary phone" }
            }
            val night = slot == BertSlot.BED
            val puffs = frame.px(263, 270, 25, 23) // where BertSays draws them, leaning either way
            val price = frame.px(148, 257, 32, 12)
            for ((spot, l) in placed) {
                val b = l.bounds
                if (b.left < scene.left || b.right > scene.right || b.top < scene.top || b.bottom > ground) failures += "$where: ${spot.name} label $b outside the scene $scene or below the loam $ground"
                if (b.overlaps(phone.overlay)) failures += "$where: ${spot.name} label $b under the You button ${phone.overlay}"
                if (b.overlaps(price)) failures += "$where: ${spot.name} label $b covers the price"
                for (x in bertXs(slot)) if (slot.bertX != null) {
                    val tail = frame.px(x + 42 - 2, 283, 12, 12)
                    if (b.overlaps(tail)) failures += "$where: ${spot.name} label $b over the bubble's tail $tail at x=$x"
                }
                if (night && window != null && b.overlaps(window)) failures += "$where: ${spot.name} label $b over the window's target $window"
                if (night && b.overlaps(puffs)) failures += "$where: ${spot.name} label $b over the dream puffs"
                for (x in bertXs(slot)) phone.face(slot, x)?.let { if (b.overlaps(it)) failures += "$where: ${spot.name} label $b covers Bert's face $it at x=$x" }
                // What takes its taps is its pill, which is drawn, inside what it covers; its tail is not a target.
                val bx = l.box
                if (bx.left < b.left || bx.right > b.right || bx.top < b.top || bx.bottom > b.bottom) failures += "$where: ${spot.name} label's pill $bx is outside it $b"
                if (l.side != LabelSide.OVER && (l.tip.x in bx.left + 1 until bx.right - 1 && l.tip.y in bx.top + 1 until bx.bottom - 1)) failures += "$where: ${spot.name} label's tail tip is inside its pill"
                val a = anchors.getValue(spot)
                val onIt = when (l.side) {
                    LabelSide.ABOVE, LabelSide.OVER -> l.tip.x in a.left..a.right && (l.side == LabelSide.OVER || l.floating || l.tip.y >= a.top - phone.px(4f))
                    LabelSide.RIGHT, LabelSide.LEFT -> l.tip.y in a.top..a.bottom
                }
                if (!onIt) failures += "$where: ${spot.name} label's tail ${l.tip} is off its thing $a"
                for ((other, o) in anchors) if (other != spot) {
                    val middle = androidx.compose.ui.unit.IntOffset(o.left + o.width / 2, o.top + o.height / 2)
                    if (middle.x in b.left until b.right && middle.y in b.top until b.bottom) failures += "$where: ${spot.name} label sits on the ${other.name}"
                }
            }
            for (i in placed.indices) for (j in i + 1 until placed.size)
                if (placed[i].second.bounds.overlaps(placed[j].second.bounds)) failures += "$where: ${placed[i].first.name} and ${placed[j].first.name} labels meet"
            // On ordinary phones "Play" is on the gate, or stands on it with its tail at the pickets' tops: never up in
            // the sky over the post balls (the gate itself: post to post, art 252..336, from the pickets' tops, row 240).
            if (gate && ordinary(phone)) {
                val gateArt = frame.px(252, 240, 84, 43)
                val play = labels[PlaceSpot.GATE]
                val near = when (play?.side) {
                    LabelSide.OVER -> play.box.overlaps(gateArt) && (play.box.left + play.box.right) / 2 in gateArt.left..gateArt.right
                    LabelSide.ABOVE -> play.tip.x in gateArt.left..gateArt.right && play.tip.y >= gateArt.top - phone.px(4f)
                    else -> false
                }
                if (!near) failures += "$where: the Play label ${play?.side} ${play?.bounds} tip ${play?.tip} is not on or against the gate $gateArt"
            }
        }
        println("PlaceLabelsSweep: $cases cases, $shortened labels shortened, $floating standing clear above their things, $leftOut left out ($leftOutOrdinary on ordinary phones), ${failures.size} failures")
        assertTrue("${failures.size} failures in $cases cases, e.g.:\n" + failures.distinct().take(12).joinToString("\n"), failures.isEmpty())
        assertTrue("the sweep should be wide, was $cases", cases > 10_000)
    }
}
