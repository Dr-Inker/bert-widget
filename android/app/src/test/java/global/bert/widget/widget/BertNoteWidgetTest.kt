package global.bert.widget.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.data.MyBert
import global.bert.widget.data.MyBertStore
import global.bert.widget.theme.BERTThemePack
import global.bert.widget.theme.BERTThemeStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * Bert's day as a note from Bert, on paper (0.15), judged on the real widget: composed by Glance, inflated on the host
 * and drawn. Every check reads the view tree or the pixels the launcher would show, so each could fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BertNoteWidgetTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After fun reset() {
        RuntimeEnvironment.setFontScale(1f)
        MyBertStore(context).forget()
        BERTThemeStore(context).save(BERTThemePack.DEFAULT)
    }

    // ---- Fixtures ------------------------------------------------------------------------------------------------------------

    private fun seed(post: String? = LONG_POST, mood: String? = "content", event: Boolean = true, agoMillis: Long = 60_000L) {
        val now = System.currentTimeMillis()
        val status = JSONObject().put("updated_at", (now - agoMillis) / 1000)
        post?.let { status.put("latest_post", it) }
        mood?.let { status.put("mood", it) }
        if (event) status.put("flappy", JSONObject().put("name", "The Autumn Arc").put("ends_at", Instant.ofEpochMilli(now + (60L * 24 + 6) * 3_600_000 + 1_800_000).toString())
            .put("status", "live").put("pool", 250).put("top", JSONArray().put(JSONObject().put("rank", 1).put("name", "LamexCrypt").put("score", 143).put("prize", 100))))
        context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit().putString("last_valid_activity", status.toString()).commit()
    }

    private fun seedNothing() = context.getSharedPreferences("bert_activity", Context.MODE_PRIVATE).edit().clear().commit()

    private fun chooseMyBert() {
        val picture = BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mybert/bert-2206.png"))
        MyBertStore(context).saveChosen(MyBert("Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc", 2206, "Forest", staked = true), picture)
    }

    // ---- Rendering and reading the tree --------------------------------------------------------------------------------------

    private class Piece(val view: ImageView, val bitmap: Bitmap, val description: String?, val rect: Rect)
    private class Note(val root: View, val bitmap: Bitmap, val pieces: List<Piece>, val texts: List<TextView>) {
        fun described(prefix: String) = pieces.firstOrNull { it.description?.startsWith(prefix) == true }
        val sprite get() = pieces.firstOrNull { it.description == null }
    }

    private fun render(w: Int, h: Int, name: String): Note {
        val out = renderWidgetOnHost(context, BERTDailyWidget(), w, h)
        File("build/outputs/host-ui/widget-note-$name.png").apply { parentFile?.mkdirs() }.outputStream().use { out.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pieces = mutableListOf<Piece>(); val texts = mutableListOf<TextView>()
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is TextView && v.text.isNotEmpty()) texts += v
            if (v is ImageView && v.id == global.bert.widget.R.id.note_image) {
                val bitmap = (v.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val (x, y) = offsetIn(v, out.host)
                    // Shown unscaled and centred in its view.
                    val left = x + (v.width - bitmap.width) / 2; val top = y + (v.height - bitmap.height) / 2
                    pieces += Piece(v, bitmap, v.contentDescription?.toString(), Rect(left, top, left + bitmap.width, top + bitmap.height))
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(out.host)
        return Note(out.host, out.bitmap, pieces, texts)
    }

    private fun offsetIn(view: View, root: View): Pair<Int, Int> {
        var x = 0; var y = 0; var v: View? = view
        while (v != null && v !== root) { x += v.left - v.scrollX; y += v.top - v.scrollY; v = v.parent as? View }
        return x to y
    }

    private fun clippedByAncestor(view: View): Boolean {
        var x = view.left; var y = view.top; var right = view.right; var bottom = view.bottom
        var parent = view.parent as? View
        while (parent != null) {
            if (x < 0 || y < 0 || right > parent.width + 1 || bottom > parent.height + 1) return true
            x += parent.left - parent.scrollX; y += parent.top - parent.scrollY
            right += parent.left - parent.scrollX; bottom += parent.top - parent.scrollY
            parent = parent.parent as? View
        }
        return false
    }

    private fun isWords(p: Piece) = p.description != null && !p.description.startsWith("Your Bert") && !p.description.startsWith("Flappy Bert tournament")

    /** Everything drawn whole: shown unscaled and uncropped, inside the widget, no two pieces overlapping, no glyph cut at an edge. */
    private fun fitProblems(n: Note): List<String> {
        val problems = mutableListOf<String>()
        for (p in n.pieces) {
            val label = p.description?.take(24) ?: "pixel Bert"
            if (p.view.width < p.bitmap.width || p.view.height < p.bitmap.height) problems += "'$label' cropped: view ${p.view.width}x${p.view.height}, drawn ${p.bitmap.width}x${p.bitmap.height}"
            if (p.rect.left < 0 || p.rect.top < 0 || p.rect.right > n.root.width || p.rect.bottom > n.root.height || clippedByAncestor(p.view)) problems += "'$label' outside the widget"
            val m = FloatArray(9).also { p.view.imageMatrix.getValues(it) }
            if (m[0] != 1f || m[4] != 1f) problems += "'$label' resampled x${m[0]}"
            if (isWords(p)) {
                val b = p.bitmap
                val edge = (0 until b.width).any { x -> android.graphics.Color.alpha(b.getPixel(x, 0)) > 24 || android.graphics.Color.alpha(b.getPixel(x, b.height - 1)) > 24 } ||
                    (0 until b.height).any { y -> android.graphics.Color.alpha(b.getPixel(0, y)) > 24 || android.graphics.Color.alpha(b.getPixel(b.width - 1, y)) > 24 }
                if (edge) problems += "'$label' has ink at its edge (a glyph cut off)"
            }
        }
        for (i in n.pieces.indices) for (j in i + 1 until n.pieces.size)
            if (Rect.intersects(n.pieces[i].rect, n.pieces[j].rect)) problems += "'${n.pieces[i].description?.take(16) ?: "pixel Bert"}' overlaps '${n.pieces[j].description?.take(16) ?: "pixel Bert"}'"
        if (n.texts.isNotEmpty()) problems += "system-font text left: ${n.texts.map { it.text }}"
        return problems
    }

    /** What the plan shows of the post: all of it, or a start that ends at a word with an ellipsis, or at a sentence's end. */
    private fun cutProblems(w: Int, h: Int, post: String): List<String> {
        val plan = planNote(context, w.toFloat(), h.toFloat(), BERTActivityRepository(context).load(), System.currentTimeMillis(), WidgetBert.load(context), false)
        val shown = plan.post?.shown ?: return listOf("post not shown")
        if (shown == post) return emptyList()
        val body = shown.removeSuffix("…")
        val atWord = post.startsWith(body.trimEnd(',', ';', ':')) && (body.length >= post.length || post.getOrNull(body.length)?.let { it.isWhitespace() || it in ",;:" } == true)
        val words = body.split(Regex("\\s+")).count { it.isNotEmpty() }
        return listOfNotNull(
            "cut mid-word: '$shown'".takeIf { !atWord || (!shown.endsWith("…") && body.last() !in ".!?") },
            "only $words words: '$shown'".takeIf { words < 4 })
    }

    // ---- What it is -------------------------------------------------------------------------------------------------------

    /** Paper, pixel Bert in the mood's pose, his mood and his words in his hand; no system-font text and no stock photo. */
    @Test fun bertsDayIsANoteFromBertOnPaper() {
        seed(mood = "content")
        for ((w, h) in listOf(180 to 180, 360 to 200)) {
            val n = render(w, h, "what-${w}x$h")
            val paper = (0 until n.bitmap.height step 4).sumOf { y -> (0 until n.bitmap.width step 4).count { x -> n.bitmap.getPixel(x, y) == PAPER } }
            val total = (n.bitmap.height / 4 + 1) * (n.bitmap.width / 4 + 1)
            assertTrue("${w}x$h: paper is ${paper * 100 / total}% of the widget", paper * 100 / total >= 35)
            assertTrue("${w}x$h: the post as Bert's handwriting, read in full by TalkBack", n.pieces.any { it.description == LONG_POST })
            assertEquals("${w}x$h: the mood in words", "Bert is feeling content", n.described("Bert is")?.description)
            assertTrue("${w}x$h: no system-font text: ${n.texts.map { it.text }}", n.texts.isEmpty())
            val sprite = n.sprite ?: error("${w}x$h: no pixel Bert")
            assertTrue("${w}x$h: pixel Bert is not the content still", spriteMatches(sprite.bitmap, "bert_mood_content") != null)
        }
    }

    /** Each mood's own still (his nap too), and F/10 for no mood or one the app does not know. */
    @Test fun pixelBertActsEachMood() {
        val cases = mapOf("content" to "bert_mood_content", "giddy" to "bert_mood_giddy", "smug" to "bert_mood_smug", "anxious" to "bert_mood_anxious",
            "melancholy" to "bert_mood_melancholy", "restless" to "bert_mood_restless", "asleep" to "bert_mood_asleep", "bewildered" to "bert_f10", null to "bert_f10")
        val wrong = mutableListOf<String>()
        for ((mood, still) in cases) {
            seed(mood = mood)
            for ((w, h) in listOf(180 to 180, 360 to 200)) {
                val n = render(w, h, "mood-${mood ?: "none"}-${w}x$h")
                val sprite = n.sprite
                if (sprite == null || spriteMatches(sprite.bitmap, still) == null) wrong += "$mood at ${w}x$h is not $still"
                val caption = n.described("Bert is")?.description
                if (mood == null && caption != null) wrong += "no mood, yet a caption '$caption'"
                if (mood != null && (caption == null || !caption.contains(if (mood == "asleep") "asleep" else mood))) wrong += "$mood at ${w}x$h captioned '$caption'"
                fitProblems(n).forEach { wrong += "$mood ${w}x$h: $it" }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    // ---- Pixel craft -------------------------------------------------------------------------------------------------------

    /** The opaque box of a still: the union over the standing stills, so every mood stands on the same feet line. */
    private fun artBox(still: String): Rect {
        fun box(name: String): Rect {
            val b = art(name); var l = b.width; var t = b.height; var r = 0; var bt = 0
            for (y in 0 until b.height) for (x in 0 until b.width) if (android.graphics.Color.alpha(b.getPixel(x, y)) > 0) { l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x + 1); bt = maxOf(bt, y + 1) }
            return Rect(l, t, r, bt)
        }
        if (still == "bert_mood_asleep") return box(still)
        return listOf("bert_f10", "bert_mood_content", "bert_mood_giddy", "bert_mood_smug", "bert_mood_anxious", "bert_mood_melancholy", "bert_mood_restless")
            .map(::box).reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
    }

    private fun art(name: String): Bitmap = BitmapFactory.decodeFile("src/main/res/drawable-nodpi/$name.png")

    /** The whole number k when [drawn] is exactly [still] enlarged k times with no smoothing, else null. */
    private fun spriteMatches(drawn: Bitmap, still: String): Int? {
        val box = artBox(still); val src = art(still)
        if (drawn.width % box.width() != 0 || drawn.height % box.height() != 0) return null
        val k = drawn.width / box.width()
        if (k < 1 || drawn.height / box.height() != k) return null
        for (y in 0 until drawn.height) for (x in 0 until drawn.width)
            if (drawn.getPixel(x, y) != src.getPixel(box.left + x / k, box.top + y / k)) return null
        return k
    }

    /** At 320, 420, 480 and 560 dpi pixel Bert is his still at a whole number of device pixels per art pixel, never resampled. */
    @Test fun pixelBertIsAWholeNumberOfDevicePixelsPerArtPixelAtEveryDensity() {
        val wrong = mutableListOf<String>(); val seen = sortedSetOf<String>()
        for (density in listOf("xhdpi", "420dpi", "xxhdpi", "560dpi")) {
            RuntimeEnvironment.setQualifiers("+$density")
            for ((mood, still) in listOf("giddy" to "bert_mood_giddy", "asleep" to "bert_mood_asleep")) {
                seed(mood = mood)
                for ((w, h) in listOf(120 to 110, 180 to 180, 250 to 110, 360 to 200)) {
                    val n = render(w, h, "density-$density-$mood-${w}x$h")
                    val sprite = n.sprite ?: run { wrong += "$density ${w}x$h: no sprite"; null } ?: continue
                    val k = spriteMatches(sprite.bitmap, still)
                    if (k == null) wrong += "$density $mood ${w}x$h: not a whole-number enlargement" else seen += "$density:k$k"
                    val m = FloatArray(9).also { sprite.view.imageMatrix.getValues(it) }
                    if (m[0] != 1f || m[4] != 1f || sprite.view.width < sprite.bitmap.width || sprite.view.height < sprite.bitmap.height) wrong += "$density ${w}x$h: resampled on screen"
                }
            }
        }
        println("NOTE REPORT pixel scales: $seen")
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    // ---- Fit and the ladder --------------------------------------------------------------------------------------------------

    /**
     * At every size from the 120x110 minimum to a large 4x2, at text 1.0, 1.3 and 2.0, with a long and a short post, with
     * and without the tournament, stale, and with My Bert: everything drawn whole and apart, and the post cut only at a word.
     */
    @Test fun theNoteFitsAtEverySizeAndTextSize() {
        val report = linkedMapOf<String, List<String>>()
        for (scale in listOf(1f, 1.3f, 2f)) {
            RuntimeEnvironment.setFontScale(scale)
            for ((kind, setup) in listOf<Pair<String, () -> String>>(
                "long-event" to { seed(LONG_POST, "melancholy", event = true); LONG_POST },
                "short" to { seed(SHORT_POST, "giddy", event = false); SHORT_POST },
                "short-event" to { seed(SHORT_POST, "anxious", event = true); SHORT_POST },
                "stale" to { seed(LONG_POST, "smug", event = true, agoMillis = 3 * 3_600_000L); LONG_POST },
                "mybert" to { chooseMyBert(); seed(LONG_POST, "restless", event = true); LONG_POST },
            )) {
                val post = setup()
                for ((w, h) in SIZES) {
                    val n = render(w, h, "fit-$kind-s${(scale * 10).toInt()}-${w}x$h")
                    // Beyond Android's 1.3 step the post may be cut anywhere, but a note with room for it still shows some.
                    val beyond = if (w * h >= 150 * 150 && n.pieces.none { it.description == post }) listOf("post gone at x$scale") else emptyList()
                    report["$kind x$scale ${w}x$h"] = fitProblems(n) + (if (scale <= 1.3f) cutProblems(w, h, post) else beyond) +
                        listOfNotNull("no mood caption".takeIf { n.described("Bert is") == null })
                }
                MyBertStore(context).forget()
            }
        }
        val bad = report.filterValues { it.isNotEmpty() }
        assertTrue(bad.entries.joinToString("\n") { "${it.key}: ${it.value}" }, bad.isEmpty())
    }

    /**
     * The ladder: the slate and the eyebrow give way before Bert's words do. The 4x2 sizes show everything at text 1.0 and
     * 1.3; the 2x2 sizes keep the time (the listing promises it); a slate never names an event without its time.
     */
    @Test fun theSlateAndTheEyebrowGiveWayBeforeThePost() {
        val wrong = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            RuntimeEnvironment.setFontScale(scale)
            seed(LONG_POST, "melancholy", event = true)
            for ((w, h) in SIZES) {
                val n = render(w, h, "ladder-s${(scale * 10).toInt()}-${w}x$h")
                val plan = planNote(context, w.toFloat(), h.toFloat(), BERTActivityRepository(context).load(), System.currentTimeMillis(), null, false)
                val post = plan.post
                val slate = n.described("Flappy Bert tournament"); val stamp = n.described("From Bert")
                if (post == null) { wrong += "x$scale ${w}x$h: no post"; continue }
                if ((slate != null || stamp != null) && post.shown != LONG_POST && post.lines < 2) wrong += "x$scale ${w}x$h: extras kept while the post has ${post.lines} line"
                if ((w to h) in FOUR_BY_TWO && (slate == null || stamp == null)) wrong += "x$scale ${w}x$h: 4x2 dropped ${listOfNotNull("slate".takeIf { slate == null }, "eyebrow".takeIf { stamp == null })}"
                if ((w to h) in TWO_BY_TWO && stamp == null) wrong += "x$scale ${w}x$h: 2x2 without the time"
                plan.slate?.shown?.let { if (!Regex("\\d+[dh]").containsMatchIn(it) || !(it.startsWith("The Autumn Arc ·") || it.startsWith("Flappy ·"))) wrong += "slate '$it'" }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    // ---- States ---------------------------------------------------------------------------------------------------------------

    /** A copy the feed has not refreshed for 30 minutes says SAVED and its time, in rust; the unconfirmed tournament is not chalked. */
    @Test fun aStaleCopySaysWhenItIsFrom() {
        seed(LONG_POST, "content", event = true, agoMillis = 3 * 3_600_000L)
        for ((w, h) in listOf(120 to 110, 180 to 180, 360 to 200)) {
            val n = render(w, h, "stale-${w}x$h")
            val stamp = n.described("Saved copy from")
            if ((w to h) != (120 to 110)) assertTrue("${w}x$h: no saved stamp in ${n.pieces.map { it.description }}", stamp != null)
            stamp?.let { s ->
                val ink = (0 until s.bitmap.height).flatMap { y -> (0 until s.bitmap.width).map { x -> s.bitmap.getPixel(x, y) } }.filter { android.graphics.Color.alpha(it) == 255 }
                assertTrue("${w}x$h: the saved stamp is rust", ink.isNotEmpty() && ink.groupingBy { it }.eachCount().maxBy { it.value }.key == STALE)
            }
            assertTrue("${w}x$h: an unconfirmed tournament is chalked", n.described("Flappy Bert tournament") == null)
            assertTrue("${w}x$h: ${fitProblems(n)}", fitProblems(n).isEmpty())
        }
    }

    /** Fresh, the time is in the eyebrow over the post at 4x2 and beside Bert at 2x2. */
    @Test fun aFreshNoteSaysWhenItIsFrom() {
        seed(LONG_POST, "content", event = true)
        for ((w, h) in TWO_BY_TWO + FOUR_BY_TWO) {
            val n = render(w, h, "fresh-${w}x$h")
            assertTrue("${w}x$h: no time in ${n.pieces.map { it.description }}", n.described("From Bert, ") != null)
        }
    }

    /** No feed yet: F/10 and the app's own line (not in Bert's hand); no mood, time or slate. With a mood but no post, the mood still shows. */
    @Test fun noFeedYetAndNoPostAreDesigned() {
        seedNothing()
        for ((w, h) in listOf(120 to 110, 180 to 180, 360 to 200)) {
            val n = render(w, h, "nothing-${w}x$h")
            assertTrue("${w}x$h: ${n.pieces.map { it.description }}", n.pieces.any { it.description == NO_NOTE_YET })
            assertTrue("${w}x$h: F/10", n.sprite?.let { spriteMatches(it.bitmap, "bert_f10") } != null)
            assertTrue("${w}x$h: nothing else", n.pieces.size == 2)
            assertTrue("${w}x$h: ${fitProblems(n)}", fitProblems(n).isEmpty())
        }
        seed(post = null, mood = "smug", event = true)
        for ((w, h) in listOf(120 to 110, 180 to 180, 360 to 200)) {
            val n = render(w, h, "nopost-${w}x$h")
            assertTrue("${w}x$h: mood", n.described("Bert is feeling a little smug") != null)
            assertTrue("${w}x$h: placeholder", n.pieces.any { it.description == NO_NOTE_YET })
            assertTrue("${w}x$h: ${fitProblems(n)}", fitProblems(n).isEmpty())
        }
    }

    /** My Bert: the holder's Bert, a rounded square, in pixel Bert's place, with the handwritten mood still under or beside it. */
    @Test fun myBertTakesPixelBertsPlaceAndKeepsTheMood() {
        chooseMyBert(); seed(LONG_POST, "giddy", event = true)
        for ((w, h) in SIZES) {
            val n = render(w, h, "mybert-${w}x$h")
            val pic = n.described("Your Bert #2206") ?: error("${w}x$h: no holder's Bert")
            assertTrue("${w}x$h: pixel Bert still drawn", n.sprite == null)
            assertTrue("${w}x$h: mood", n.described("Bert is feeling giddy") != null)
            assertTrue("${w}x$h: square", pic.bitmap.width == pic.bitmap.height)
            assertTrue("${w}x$h: rounded", android.graphics.Color.alpha(pic.bitmap.getPixel(0, 0)) == 0)
        }
    }

    /** Paper and slate are fixed materials: every theme pack draws the same note. */
    @Test fun theNoteLooksTheSameUnderEveryThemePack() {
        seed(LONG_POST, "content", event = true)
        BERTThemeStore(context).save(BERTThemePack.DEFAULT)
        val reference = render(360, 200, "theme-default").bitmap
        for (pack in BERTThemePack.entries) {
            BERTThemeStore(context).save(pack)
            assertTrue("${pack.id} changes the note", render(360, 200, "theme-${pack.id}").bitmap.sameAs(reference))
        }
    }

    // ---- Reading it -----------------------------------------------------------------------------------------------------------

    /** TalkBack: the mood, then when it is from, then the post in full, then the tournament. Pixel Bert is decorative. */
    @Test fun talkBackReadsTheMoodTheTimeThePostAndTheEvent() {
        seed(LONG_POST, "content", event = true)
        for ((w, h) in listOf(180 to 180, 360 to 200)) {
            val n = render(w, h, "talkback-${w}x$h")
            val spoken = n.pieces.mapNotNull { it.description }
            assertEquals("${w}x$h", listOf("Bert is feeling content", "From Bert, ", LONG_POST, "Flappy Bert tournament, The Autumn Arc, 60 days 6 hours left"),
                spoken.map { if (it.startsWith("From Bert, ")) "From Bert, " else it })
            assertTrue("pixel Bert is not announced", n.sprite!!.view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        }
    }

    /** Tapping anywhere on the note opens the app at Home. */
    @Test fun tappingTheNoteOpensHome() {
        seed()
        val n = render(360, 200, "tap")
        var clickable: View? = null
        fun walk(v: View) { if (clickable == null && v.hasOnClickListeners()) clickable = v; if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(n.root)
        val target = clickable ?: error("nothing to tap")
        assertTrue("the tap target covers the note", target.width == n.root.width && target.height == n.root.height)
        target.performClick()
        val started = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(global.bert.widget.MainActivity::class.java.name, started?.component?.className)
    }

    /** Every pair of words and ground is at least 4.5:1, measured on the drawn pixels. */
    @Test fun everyWordIsLegibleOnItsGround() {
        seed(LONG_POST, "content", event = true)
        val failures = mutableListOf<String>()
        fun lum(c: Int) = listOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c)).map { it / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }.let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }
        fun ratio(a: Int, b: Int) = (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)
        fun mode(px: List<Int>) = px.groupingBy { it }.eachCount().maxBy { it.value }.key
        for ((w, h) in listOf(180 to 180, 360 to 200)) {
            val n = render(w, h, "contrast-${w}x$h")
            for (p in n.pieces.filter(::isWords)) {
                val ink = mode((0 until p.bitmap.height).flatMap { y -> (0 until p.bitmap.width).map { x -> p.bitmap.getPixel(x, y) } }.filter { android.graphics.Color.alpha(it) == 255 })
                val ground = mode((0 until p.bitmap.height).flatMap { y -> (0 until p.bitmap.width).mapNotNull { x ->
                    if (android.graphics.Color.alpha(p.bitmap.getPixel(x, y)) == 0) n.bitmap.getPixel(p.rect.left + x, p.rect.top + y) else null } })
                val r = ratio(ink, ground)
                if (r < 4.5) failures += "${w}x$h '${p.description?.take(20)}' ${"%.2f".format(r)}:1"
            }
            val slate = n.described("Flappy Bert tournament") ?: error("${w}x$h: no slate")
            val px = (0 until slate.bitmap.height).flatMap { y -> (0 until slate.bitmap.width).map { x -> slate.bitmap.getPixel(x, y) } }
            val board = mode(px)
            val chalk = mode(px.filter { lum(it) > lum(board) + 0.3 })
            if (ratio(chalk, board) < 4.5) failures += "${w}x$h chalk ${"%.2f".format(ratio(chalk, board))}:1"
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }

    /** The bitmaps one render hands the launcher stay a small share of its budget (1.5 x the screen's ARGB pixels). */
    @Test fun bitmapsStayWellInsideTheLaunchersBudget() {
        chooseMyBert(); seed(LONG_POST, "melancholy", event = true)
        RuntimeEnvironment.setFontScale(1.3f)
        for (density in listOf("xhdpi", "560dpi")) {
            RuntimeEnvironment.setQualifiers("+$density")
            val metrics = context.resources.displayMetrics
            val budget = 1.5 * metrics.widthPixels * metrics.heightPixels * 4
            // The wide note and a tall stacked one, where pixel Bert is at his largest.
            for ((w, h) in listOf(400 to 220, 200 to 420)) {
                val n = render(w, h, "budget-$density-${w}x$h")
                val bytes = n.pieces.sumOf { it.bitmap.allocationByteCount.toLong() }
                assertTrue("$density ${w}x$h: ${bytes / 1024} KB of a ${(budget / 1024).toInt()} KB budget", bytes <= budget / 6)
            }
        }
    }

    private companion object {
        val PAPER = 0xFFF7EEDA.toInt()
        val STALE = 0xFF9A3B1A.toInt()
        const val SHORT_POST = "the blanket settled heavier on its own. like the season finally taught it how to hold still."
        const val LONG_POST = "woofmornin. the ladybug holds the screen door like the wind can wait its turn, and the mayor is in no hurry either. " +
            "somewhere a kettle is thinking about it. the pack has opinions about breakfast and none of them are quiet."
        val TWO_BY_TWO = listOf(150 to 150, 180 to 180, 180 to 190)
        val FOUR_BY_TWO = listOf(360 to 200, 380 to 190, 400 to 220)
        /** The 0.14.2 ladder (minimum 2x1 to a large 4x2; 380x190 and 180x190 are the S25's cells) and the odd shapes from the mood test. */
        val SIZES = listOf(120 to 110, 140 to 110, 150 to 150, 180 to 180, 180 to 190, 120 to 160, 130 to 200, 200 to 110, 210 to 120,
            250 to 110, 300 to 130, 320 to 150, 360 to 200, 380 to 190, 400 to 220)
    }
}
