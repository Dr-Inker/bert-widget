package global.bert.widget

import android.app.Application
import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTJournal
import global.bert.widget.data.LostTrailProgress
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeTodayTest {
    @Test fun `caption excerpts keep whole sentences and never exceed the card limit`() {
        assertEquals("short and sweet.", captionExcerpt("short and sweet."))
        val two = "woofmornin. the ladybug holds the screen door like the wind can wait its turn, and the mayor is in no hurry either."
        assertEquals("woofmornin.", captionExcerpt(two))
        val one = "the council has ruled that every single hallway in town shall from this day forward be considered a nap zone for all good dogs"
        val cut = captionExcerpt(one)
        assertTrue(cut, cut.endsWith("…") && captionCharacterCount(cut) <= CAPTION_CHARACTER_LIMIT && !cut.contains("  "))
        assertTrue(captionCharacterCount(captionExcerpt("🐶".repeat(200))) <= CAPTION_CHARACTER_LIMIT)
        assertEquals("line one line two.", captionExcerpt("line one\nline two."))
    }

    @Test fun `lost trail progress reads the game's v1 save and ignores anything else`() {
        fun storage(p: String?, r: String?) = JSONObject().put("p", p ?: JSONObject.NULL).put("r", r ?: JSONObject.NULL).toString()
        val progress = """{"version":1,"worlds":[{"time":91000,"bones":3,"coins":20,"deaths":1},{"time":120000,"bones":2,"coins":9,"deaths":4},null]}"""
        val run = """{"version":1,"world":2,"checkpoint":1}"""
        assertEquals(LostTrailProgress(2, 5, 2), LostTrailProgress.parse(storage(progress, run)))
        assertEquals(2, LostTrailProgress.parse(storage(progress, null))!!.nextWorld)
        assertFalse(LostTrailProgress.parse(storage(null, null))!!.started)
        assertNull(LostTrailProgress.parse(storage("""{"version":2,"worlds":[]}""", null)))
        assertNull(LostTrailProgress.parse(storage(progress, run))!!.copy(worldsCleared = 6, worldInProgress = null).nextWorld)
        assertEquals(null, LostTrailProgress.parse("not json"))
    }

    @Test fun `the journal keeps each new dispatch once, newest first`() {
        val journal = BERTJournal(RuntimeEnvironment.getApplication())
        assertTrue(journal.record(BERTActivity(1, "first post", "content", null), now = 1_000))
        assertFalse(journal.record(BERTActivity(2, "first post", "giddy", null), now = 2_000))
        assertTrue(journal.record(BERTActivity(3, "second post", null, null), now = 3_000))
        assertEquals(listOf("second post", "first post"), journal.entries().map { it.text })
        assertEquals(1_000L, journal.entries().last().firstSeenEpochMillis)
        // The feed falling back to an older post does not move it or re-date it.
        assertFalse(journal.record(BERTActivity(4, "first post", null, null), now = 4_000))
        assertEquals(listOf("second post", "first post"), journal.entries().map { it.text })
        assertTrue(BERTJournal.parse("garbage").isEmpty())
    }

    @Test fun `greeting follows the local clock, with Bert's own good morning`() {
        val day = 1_760_000_000_000L - 1_760_000_000_000L % 86_400_000L // a UTC midnight
        assertEquals("WOOFmornin, pack.", greetingAt(day + 8 * 3_600_000L, ZoneOffset.UTC))
        assertEquals("Afternoon, pack.", greetingAt(day + 13 * 3_600_000L, ZoneOffset.UTC))
        assertEquals("Evening, pack.", greetingAt(day + 19 * 3_600_000L, ZoneOffset.UTC))
        assertEquals("Up late, pack?", greetingAt(day + 2 * 3_600_000L, ZoneOffset.UTC))
    }
}
