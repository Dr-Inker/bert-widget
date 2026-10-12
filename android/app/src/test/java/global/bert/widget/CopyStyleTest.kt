package global.bert.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** House style for UI copy: curly apostrophes and UK spelling. Scans the app's own string literals. */
class CopyStyleTest {
    private val ignore = Regex("evaluateJavascript|localStorage|Regex|getItem|JSONObject|\\.json")
    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    private fun literals(): List<Pair<String, String>> = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
        .flatMap { file -> file.readLines().withIndex().filterNot { ignore.containsMatchIn(it.value) }
            .flatMap { (i, line) -> literal.findAll(line).map { "${file.name}:${i + 1}" to it.groupValues[1] } } }.toList()

    @Test fun `UI copy uses curly apostrophes`() {
        val straight = literals().filter { Regex("[A-Za-z]'[a-z]").containsMatchIn(it.second) }
        assertTrue("straight apostrophes: $straight", straight.isEmpty())
    }

    @Test fun `UI copy uses UK spelling`() {
        val us = literals().filter { Regex("\\b(Personaliz|personaliz|[Cc]olor\\b|[Ff]avorite|[Cc]enter\\b)").containsMatchIn(it.second) }
        assertTrue("US spellings: $us", us.isEmpty())
    }
}
