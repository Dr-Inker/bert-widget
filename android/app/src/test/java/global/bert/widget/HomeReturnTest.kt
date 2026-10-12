package global.bert.widget

import android.app.Application
import global.bert.widget.data.BERTHomeVisits
import global.bert.widget.data.HomeVisit
import global.bert.widget.data.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeReturnTest {
    private val hour = 3_600_000L

    @Test fun `day N counts distinct local days, never resets and ignores a clock set backwards`() {
        val visits = BERTHomeVisits(RuntimeEnvironment.getApplication())
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(1, visits.recordOpen(monday))
        assertEquals(1, visits.recordOpen(monday)) // same day, many opens
        assertEquals(2, visits.recordOpen(monday.plusDays(1)))
        assertEquals(2, visits.recordOpen(monday)) // clock moved back: no count, no rewind
        assertEquals(3, visits.recordOpen(monday.plusDays(9))) // a gap is not a reset
        assertEquals(3, BERTHomeVisits(RuntimeEnvironment.getApplication()).visit(1_000L, monday.plusDays(9)).days)
    }

    @Test fun `a sitting's baseline is the previous look at Home, and the first look ever is not news`() {
        val visits = BERTHomeVisits(RuntimeEnvironment.getApplication())
        val day = LocalDate.of(2026, 10, 12)
        val start = 1_789_000_000_000L
        assertEquals(start, visits.visit(start, day).since) // an upgrade's journal is not "new"
        assertEquals(start, visits.visit(start + 10 * 60_000, day).since) // same sitting: baseline holds
        assertEquals(start, visits.visit(start + 20 * 60_000, day).since)
        val back = start + 20 * 60_000 + 2 * hour // away two hours
        assertEquals(start + 20 * 60_000, visits.visit(back, day).since)
        assertEquals(start + 20 * 60_000, visits.visit(back + 60_000, day).since)
    }

    @Test fun `today's update is New only while young and unseen in an earlier sitting`() {
        val now = 1_789_000_000_000L
        val visit = HomeVisit(since = now - 3 * hour, days = 4)
        fun seenAt(at: Long) = JournalEntry("post", null, at)
        assertTrue(BERTHomeVisits.isNew(seenAt(now - hour), visit, now))
        assertFalse(BERTHomeVisits.isNew(seenAt(now - 4 * hour), visit, now)) // seen before the last sitting ended
        assertFalse(BERTHomeVisits.isNew(seenAt(now - 7 * hour), HomeVisit(now - 8 * hour, 4), now)) // older than 6h
        assertFalse(BERTHomeVisits.isNew(null, visit, now))
        assertFalse(BERTHomeVisits.isNew(seenAt(now - hour), null, now))
    }

    @Test fun `the new-from-Bert count leaves out the update Today already shows`() {
        val now = 1_789_000_000_000L
        val entries = listOf(JournalEntry("today", null, now - hour), JournalEntry("earlier", "giddy", now - 2 * hour),
            JournalEntry("older", null, now - 30 * hour))
        val visit = HomeVisit(since = now - 5 * hour, days = 2)
        assertEquals(listOf("earlier"), BERTHomeVisits.newSince(entries, visit, current = "today").map { it.text })
        assertEquals(listOf("today", "earlier"), BERTHomeVisits.newSince(entries, visit, current = null).map { it.text })
        assertTrue(BERTHomeVisits.newSince(entries, null, "today").isEmpty())
    }

    @Test fun `mood reads as a phrase in Bert's voice, never a reading`() {
        assertEquals("feeling a bit melancholy", moodPhrase("melancholy"))
        assertEquals("content", moodPhrase("content"))
        assertEquals("giddy", moodPhrase("GIDDY"))
        assertEquals("a little smug", moodPhrase("smug"))
        assertEquals("a touch anxious", moodPhrase(" anxious "))
        assertEquals("a bit restless", moodPhrase("restless"))
        assertEquals("curious", moodPhrase("Curious")) // unknown: the plain label
    }

    @Test fun `the date shortens to MON 12 OCT at large text`() {
        val at = LocalDate.of(2026, 10, 12).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("MONDAY 12 OCTOBER", todayLabel(at, large = false, zone = ZoneOffset.UTC, locale = Locale.UK))
        assertEquals("MON 12 OCT", todayLabel(at, large = true, zone = ZoneOffset.UTC, locale = Locale.UK))
    }
}
