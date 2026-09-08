package global.bert.widget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BERTActivityTest {
    private val now = 1_788_888_601_000L // 2026-09-08T17:30:01Z
    private val fixture = """{
        "updated_at":1788888601,
        "mood":"smug",
        "latest_post":"A test dispatch from Bert.",
        "bert":{"price":-999},
        "flappy":{"name":"Fixture tournament","ends_at":"2026-12-01T00:00:00Z","status":"live",
                  "play_url":"intent://untrusted/#Intent;end"}
    }"""

    @Test fun `public shape parses source refresh time and independently dated event`() {
        val activity = BERTActivity.parse(fixture, now)
        assertEquals(1_788_888_601_000L, activity.updatedAtEpochMillis)
        assertEquals("A test dispatch from Bert.", activity.dispatch)
        assertEquals("smug", activity.mood)
        assertEquals("Fixture tournament", activity.event!!.name)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z").toEpochMilli(), activity.event.endsAtEpochMillis)
        assertEquals(EventPhase.OPEN, activity.event.phaseAt(now, false))
    }

    @Test fun `missing fractional coerced and future source timestamps fail closed`() {
        listOf("null", "-1", "0", "1788888601.5", "\"1788888601\"", "1788888662").forEach { invalid ->
            assertTrue(invalid, runCatching { BERTActivity.parse(fixture.replace("1788888601", invalid), now) }.isFailure)
        }
        assertTrue(runCatching { BERTActivity.parse("{}", now) }.isFailure)
    }

    @Test fun `malformed optional event preserves the dispatch`() {
        val activity = BERTActivity.parse(fixture.replace("2026-12-01T00:00:00Z", "tomorrow"), now)
        assertNull(activity.event)
        assertEquals("A test dispatch from Bert.", activity.dispatch)
    }

    @Test fun `unbounded or invalid display text is omitted rather than coerced`() {
        val activity = BERTActivity.parse("""{"updated_at":1788888601,"mood":12,"latest_post":"${"x".repeat(801)}",
            "flappy":{"name":"Fixture tournament","ends_at":"2026-12-01T00:00:00Z","status":"live"}}""", now)
        assertNull(activity.mood)
        assertNull(activity.dispatch)
        assertNotNull(activity.event)
        assertTrue(runCatching { BERTActivity.parse("""{"updated_at":1788888601,"latest_post":""}""", now) }.isFailure)
    }

    @Test fun `oversized activity documents are rejected before parsing`() {
        assertTrue(runCatching { BERTActivity.parse(fixture + " ".repeat(32768), now) }.isFailure)
    }

    @Test fun `saved activity becomes stale at thirty minutes even when a refresh was successful`() {
        val activity = BERTActivity.parse(fixture, now)
        assertFalse(activity.isStaleAt(now + 1_799_999))
        assertTrue(activity.isStaleAt(now + 1_800_000))
        assertTrue(activity.isStaleAt(now - 60_001))
    }

    @Test fun `event expires at its deadline even when the source still says live`() {
        val event = BERTEvent("Fixture", now + 1000, "live")
        assertEquals(EventPhase.OPEN, event.phaseAt(now + 999, false))
        assertEquals(EventPhase.ENDED, event.phaseAt(now + 1000, false))
        assertEquals(EventPhase.ENDED, event.phaseAt(now + 1001, true))
    }

    @Test fun `failed refresh and unknown status never claim an open tournament`() {
        assertEquals(EventPhase.UNCONFIRMED, BERTEvent("Fixture", now + 1000, "live").phaseAt(now, true))
        assertEquals(EventPhase.UNCONFIRMED, BERTEvent("Fixture", now + 1000, "surprise").phaseAt(now, false))
        assertEquals(EventPhase.ENDED, BERTEvent("Fixture", now + 1000, "closed").phaseAt(now, false))
        assertEquals(EventPhase.UPCOMING, BERTEvent("Fixture", now + 1000, "scheduled").phaseAt(now, false))
    }
}
