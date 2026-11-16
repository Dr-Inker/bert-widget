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

    // Critique 2026-11-12 ENG-6: the feed is regenerated every five minutes from the server's clock, so a phone a few
    // minutes slow sees updated_at in its future. Up to ten minutes ahead is read (as "now"); further ahead fails closed.
    @Test fun `missing fractional coerced and far future source timestamps fail closed`() {
        listOf("null", "-1", "0", "1788888601.5", "\"1788888601\"", "1788889202").forEach { invalid ->
            assertTrue(invalid, runCatching { BERTActivity.parse(fixture.replace("1788888601", invalid), now) }.isFailure)
        }
        assertTrue(runCatching { BERTActivity.parse("{}", now) }.isFailure)
    }

    @Test fun `a phone clock up to ten minutes slow still reads the feed, as fresh`() {
        for (ahead in listOf(61, 300, 600)) {
            val activity = BERTActivity.parse(fixture.replace("1788888601", "${1_788_888_601 + ahead}"), now)
            assertEquals("$ahead s ahead is read as now", now, activity.updatedAtEpochMillis)
            assertFalse("$ahead s ahead", activity.isStaleAt(now))
            assertEquals("A test dispatch from Bert.", activity.dispatch)
        }
    }

    // Critique 2026-11-12 SEC-2: the dispatch is untrusted text, shown on Home and sent as a notification. Like player
    // names, it loses bidirectional control characters, which can make text read in a different order than it is.
    @Test fun `the dispatch is stripped of bidirectional control characters before it is shown or sent`() {
        val activity = BERTActivity.parse("""{"updated_at":1788888601,
            "latest_post":"gm from \u202Eevil\u202C \u2066Bert\u2069\u200F\u200E.\nwoof"}""", now)
        assertEquals("gm from evil Bert.\nwoof", activity.dispatch)
        val (notices, _) = BERTAlertRules.evaluate(AlertSettings(dispatch = true), AlertMemory(lastDispatch = "older"), null, activity, now)
        assertEquals("gm from evil Bert.\nwoof", notices.single().text)
        // Nothing left but the controls is no dispatch at all.
        assertNull(BERTActivity.parse("""{"updated_at":1788888601,"mood":"smug","latest_post":"\u202E\u202C"}""", now).dispatch)
    }

    // 0.15.1: the post's own time and its X id (server 8f64c5a). Both optional; a bad one is dropped, never the post.
    private fun withPost(extra: String) = BERTActivity.parse(fixture.replace("\"mood\":\"smug\",", "\"mood\":\"smug\",$extra"), now)

    @Test fun `the post's own time and X id are read when valid`() {
        val activity = withPost("\"latest_post_at\":1788800000,\"latest_post_x_id\":\"1983746501234567890\",")
        assertEquals(1_788_800_000_000L, activity.postedAtEpochMillis)
        assertEquals("1983746501234567890", activity.postXId)
        assertTrue(activity.postIsOnX)
        // Not the refresh's time: that stays the feed's.
        assertEquals(1_788_888_601_000L, activity.updatedAtEpochMillis)
        // Up to ten minutes ahead of a slow phone reads as now, like the feed's own timestamp.
        assertEquals(now, withPost("\"latest_post_at\":${1_788_888_601 + 300},").postedAtEpochMillis)
    }

    @Test fun `an older feed without the post's time or X id still parses`() {
        val activity = BERTActivity.parse(fixture, now)
        assertNull(activity.postedAtEpochMillis)
        assertNull(activity.postXId)
        assertFalse(activity.postIsOnX)
        assertEquals("A test dispatch from Bert.", activity.dispatch)
    }

    @Test fun `null post time and X id mean unknown and not on X`() {
        val activity = withPost("\"latest_post_at\":null,\"latest_post_x_id\":null,")
        assertNull(activity.postedAtEpochMillis)
        assertNull(activity.postXId)
        assertEquals("A test dispatch from Bert.", activity.dispatch)
    }

    @Test fun `a negative, zero, fractional, future or non-number post time is dropped, not the post`() {
        for (bad in listOf("-1788800000", "0", "1788800000.5", "${1_788_888_601 + 601}", "17888886010000", "\"1788800000\"", "true")) {
            val activity = runCatching { withPost("\"latest_post_at\":$bad,") }.getOrElse { error("$bad hid the post: $it") }
            assertNull(bad, activity.postedAtEpochMillis)
            assertEquals(bad, "A test dispatch from Bert.", activity.dispatch)
        }
    }

    @Test fun `an X id that is not digits, too long, empty or a number is dropped, not the post`() {
        for (bad in listOf("\"12a45\"", "\" 123\"", "\"-123\"", "\"1.5\"", "\"\"", "\"${"1".repeat(26)}\"", "\"\u0661\u0662\"", "1983746501234567890", "{}")) {
            val activity = runCatching { withPost("\"latest_post_x_id\":$bad,") }.getOrElse { error("$bad hid the post: $it") }
            assertNull(bad, activity.postXId)
            assertEquals(bad, "A test dispatch from Bert.", activity.dispatch)
        }
        assertEquals("1".repeat(25), withPost("\"latest_post_x_id\":\"${"1".repeat(25)}\",").postXId)
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

    // UX-4 (critique 12 November): a feed that loaded with nothing in it is a quiet day; a feed that could not be
    // fetched, with nothing saved, is the connection. Only a saved feed is shown as a (delayed) note.
    @Test fun `an empty feed is told apart from an unreachable one`() {
        val empty = runCatching { BERTActivity.parse("""{"updated_at":1788888601}""", now) }.exceptionOrNull()
        assertTrue("an empty feed: $empty", empty is EmptyActivityException)
        assertEquals(ActivityState.Empty, ActivityState.afterFailure(empty!!, saved = null))
        assertEquals(ActivityState.Unavailable, ActivityState.afterFailure(java.io.IOException("offline"), saved = null))
        // A bad timestamp is not an empty feed.
        val bad = runCatching { BERTActivity.parse("""{"updated_at":"soon","mood":"smug"}""", now) }.exceptionOrNull()!!
        assertEquals(ActivityState.Unavailable, ActivityState.afterFailure(bad, saved = null))
        val saved = BERTActivity.parse(fixture, now)
        for (error in listOf(empty, java.io.IOException("offline"))) assertEquals(ActivityState.Available(saved, updateDelayed = true), ActivityState.afterFailure(error, saved))
    }
}
