package global.bert.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * How the app speaks of Berthalla (owner, 0.15.1). Berthalla is a place, like Valhalla: not a person, not a maker, and
 * not a fan project. Its posts come from the Berthalla X bot, run by the BERT team; things come from Berthalla.
 */
class BerthallaWordingTest {
    private val app = File("src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()

    /** Nothing in the app's code, layouts or strings, comments included, calls Berthalla (or the app) a fan anything. */
    @Test fun berthallaIsNeverCalledAFanBotOrAFanProject() {
        val wrong = Regex("fan[ -]?bot|fan project|fan-run|fan run|fan[ -]?app|fan[ -]?made", RegexOption.IGNORE_CASE)
        assertTrue("found ${app.size} files", app.size > 50)
        val found = app.flatMap { file ->
            file.readLines().withIndex().filter { wrong.containsMatchIn(it.value) }.map { "${file.name}:${it.index + 1}: ${it.value.trim()}" }
        }
        val listing = File("../../docs/play/LISTING.md")
        assertTrue(listing.isFile)
        val inListing = listing.readLines().withIndex().filter { wrong.containsMatchIn(it.value) }.map { "LISTING.md:${it.index + 1}" }
        assertTrue((found + inListing).joinToString("\n"), (found + inListing).isEmpty())
        assertEquals("the Berthalla X bot, run by the BERT team", BERTHALLA_BOT)
    }

    /**
     * Text the app shows or speaks never makes Berthalla an actor or an owner: no "Berthalla's" (either apostrophe), no
     * "Berthalla posts", no "made by Berthalla"; and the app was forged in Berthalla, never "born in Berthalla". Scoped
     * to user-facing text: the string literals in the app's Kotlin (comment lines skipped; a comment can say how it was)
     * and the text in its layouts and string resources. Names
     * that only start with the word (Berthalla Navy, Berthalla Nights) are not possessives and pass.
     */
    @Test fun berthallaIsAPlaceInEverythingTheAppSays() {
        val actor = Regex("berthalla['’]s\\b|berthalla posts?\\b|made by berthalla|born in berthalla", RegexOption.IGNORE_CASE)
        val found = mutableListOf<String>()
        for (file in app) file.readLines().forEachIndexed { i, line ->
            val trimmed = line.trim()
            val texts = if (file.extension == "kt") {
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) emptyList() else literals(line)
            } else {
                if (trimmed.startsWith("<!--")) emptyList()
                else Regex("android:(?:text|description|label|contentDescription|hint)=\"([^\"]*)\"").findAll(line).map { it.groupValues[1] }.toList() +
                    Regex("<string[^>]*>([^<]*)</string>").findAll(line).map { it.groupValues[1] }.toList()
            }
            texts.filter { actor.containsMatchIn(it) }.forEach { found += "${file.name}:${i + 1}: $it" }
        }
        assertTrue(found.joinToString("\n"), found.isEmpty())
        // Nor anywhere else in the app's code, comments included: the owner's word is "forged".
        val born = app.filter { it.readText().contains("born in Berthalla", ignoreCase = true) }.map { it.name }
        assertTrue("born in Berthalla in $born", born.isEmpty())
        // The check can fail: it sees a possessive in a string.
        assertEquals(listOf("Berthalla’s post"), literals("val x = \"Berthalla’s post\" // Berthalla's comment").filter { actor.containsMatchIn(it) })
    }

    /** The "…" string literals on one line of Kotlin, read character by character (escapes kept, templates as text). */
    private fun literals(line: String): List<String> {
        val out = mutableListOf<String>(); var i = 0
        while (i < line.length) {
            if (line.startsWith("//", i)) break
            if (line[i] == '"') {
                val start = ++i
                while (i < line.length && line[i] != '"') { if (line[i] == '\\') i++; i++ }
                out += line.substring(start, minOf(i, line.length))
            }
            i++
        }
        return out
    }
}
