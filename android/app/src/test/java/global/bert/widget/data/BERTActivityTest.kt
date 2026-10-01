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

    private val standingsFixture = """{
        "updated_at":1788888601,
        "flappy":{"name":"The Autumn Arc","ends_at":"2026-12-01T00:00:00Z","status":"live","sponsor":"Dr. Inker LABS","pool":250,
          "top":[
            {"rank":2,"name":"DrInker","score":115,"prize":60},
            {"rank":1,"name":"LamexCrypt","score":143,"prize":100},
            {"rank":3,"name":"\u202Eevil\u202C name\u0007","score":114,"prize":40},
            {"rank":4,"name":"${"x".repeat(80)}","score":112,"prize":30},
            {"rank":5,"name":"Hameed","score":-3,"prize":20},
            {"rank":5,"name":"Fallback","score":108.5,"prize":20},
            {"rank":6,"name":"   ","score":100},
            {"rank":7,"name":"Seventh","score":99,"prize":null}
          ]}
    }"""

    @Test fun `standings are bounded, sorted and stripped of control and bidi characters`() {
        val event = BERTActivity.parse(standingsFixture, now).event!!
        assertEquals(250, event.pool)
        assertEquals("Dr. Inker LABS", event.sponsor)
        assertEquals(listOf(1, 2, 3, 4, 7), event.standings.map { it.rank })
        assertEquals("LamexCrypt", event.standings.first().name)
        assertEquals("evil name", event.standings[2].name)
        assertEquals(32, event.standings[3].name.length)
        assertNull(event.standings.last().prizeUsd)
    }

    @Test fun `a malformed standings list never hides the tournament`() {
        val activity = BERTActivity.parse(fixture.replace("\"status\":\"live\",", "\"status\":\"live\",\"top\":\"oops\",\"pool\":-5,"), now)
        assertEquals("Fixture tournament", activity.event!!.name)
        assertTrue(activity.event.standings.isEmpty())
        assertNull(activity.event.pool)
    }
}
