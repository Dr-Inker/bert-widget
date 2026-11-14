package global.bert.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The visit streak's rule (visitStreakOn), pure: dates in, the run out. */
class VisitStreakTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun on(was: VisitStreak?, today: String) = visitStreakOn(was, d(today))

    @Test fun theFirstOpenEverIsDayOne() {
        val step = on(null, "2026-11-14")
        assertEquals(StreakChange.FIRST, step.change)
        assertEquals(VisitStreak(1, 1, d("2026-11-14")), step.streak)
    }

    @Test fun theSameDayChangesNothing() {
        val was = VisitStreak(4, 9, d("2026-11-14"))
        val step = on(was, "2026-11-14")
        assertEquals(StreakChange.SAME_DAY, step.change)
        assertEquals(was, step.streak)
    }

    @Test fun theNextDayAddsOneAndRaisesTheBestWhenItPassesIt() {
        assertEquals(StreakStep(VisitStreak(5, 9, d("2026-11-15")), StreakChange.NEXT_DAY), on(VisitStreak(4, 9, d("2026-11-14")), "2026-11-15"))
        assertEquals(StreakStep(VisitStreak(10, 10, d("2026-11-15")), StreakChange.NEXT_DAY), on(VisitStreak(9, 9, d("2026-11-14")), "2026-11-15"))
    }

    @Test fun aGapStartsAgainAtOneAndKeepsTheBest() {
        // Two days later: one day missed.
        assertEquals(StreakStep(VisitStreak(1, 12, d("2026-11-16")), StreakChange.RESTARTED), on(VisitStreak(12, 12, d("2026-11-14")), "2026-11-16"))
        // Months later.
        assertEquals(StreakStep(VisitStreak(1, 31, d("2027-03-01")), StreakChange.RESTARTED), on(VisitStreak(3, 31, d("2026-11-14")), "2027-03-01"))
    }

    /** A day or two back (a time zone, a nudge): nothing changes, nothing resets. */
    @Test fun aClockMovedBackALittleChangesNothing() {
        val was = VisitStreak(6, 8, d("2026-11-14"))
        for (back in listOf("2026-11-13", "2026-11-12")) {
            val step = on(was, back)
            assertEquals(StreakChange.CLOCK_BACK, step.change)
            assertEquals("back to $back", was, step.streak)
        }
        assertEquals(VisitStreak(7, 8, d("2026-11-15")), on(was, "2026-11-15").streak)
    }

    /**
     * A last day far ahead of today: the clock was set forward and has come back, or it is behind for now. Nothing is
     * decided on first sight: the run and its last day are kept and today is remembered (the anchor).
     */
    @Test fun aClockFarBehindTheLastDayDecidesNothingAtFirst() {
        val run = VisitStreak(30, 30, d("2026-11-14"))
        val step = on(run, "2026-11-01")
        assertEquals(StreakChange.REANCHORED, step.change)
        assertEquals(VisitStreak(30, 30, d("2026-11-14"), anchor = d("2026-11-01")), step.streak)
        // The same wrong day again changes nothing; an even earlier one moves the anchor back and nothing else.
        assertEquals(StreakStep(step.streak, StreakChange.SAME_DAY), visitStreakOn(step.streak, d("2026-11-01")))
        assertEquals(VisitStreak(30, 30, d("2026-11-14"), anchor = d("2026-10-20")), visitStreakOn(step.streak, d("2026-10-20")).streak)
        // A day back from the anchor is a time zone, not another jump: the anchor stays, and the day after it still counts.
        assertEquals(StreakStep(step.streak, StreakChange.CLOCK_BACK), visitStreakOn(step.streak, d("2026-10-31")))
        assertEquals(StreakStep(VisitStreak(31, 31, d("2026-11-02")), StreakChange.NEXT_DAY), visitStreakOn(step.streak, d("2026-11-02")))
        // The slack is two days and no more: one or two days behind is a time zone (nothing remembered), three is not.
        assertEquals(StreakStep(run, StreakChange.CLOCK_BACK), on(run, "2026-11-13"))
        assertEquals(StreakStep(run, StreakChange.CLOCK_BACK), on(run, "2026-11-12"))
        assertEquals(StreakChange.REANCHORED, on(run, "2026-11-11").change)
    }

    /** The clock was behind (set back for something else, or a boot before network time) and is put right: the run is whole. */
    @Test fun aClockSetBackAndPutRightKeepsTheRun() {
        val behind = on(VisitStreak(30, 30, d("2026-11-14")), "2026-11-01").streak
        // Back on the last day counted: the same day, the anchor forgotten.
        assertEquals(StreakStep(VisitStreak(30, 30, d("2026-11-14")), StreakChange.SAME_DAY), visitStreakOn(behind, d("2026-11-14")))
        // Or put right overnight: the next day counts as the next day.
        assertEquals(StreakStep(VisitStreak(31, 31, d("2026-11-15")), StreakChange.NEXT_DAY), visitStreakOn(behind, d("2026-11-15")))
    }

    /** The clock was set forward (which restarts the run there) and has come back: the run goes on from the real calendar, not frozen. */
    @Test fun aClockSetForwardAndBackDoesNotFreezeTheRun() {
        val ahead = on(VisitStreak(6, 8, d("2026-11-13")), "2027-11-14")
        assertEquals(StreakStep(VisitStreak(1, 8, d("2027-11-14")), StreakChange.RESTARTED), ahead)
        val back = visitStreakOn(ahead.streak, d("2026-11-14"))
        assertEquals(StreakChange.REANCHORED, back.change)
        val next = visitStreakOn(back.streak, d("2026-11-15"))
        assertEquals(StreakStep(VisitStreak(2, 8, d("2026-11-15")), StreakChange.NEXT_DAY), next)
        assertEquals(StreakStep(VisitStreak(3, 8, d("2026-11-16")), StreakChange.NEXT_DAY), visitStreakOn(next.streak, d("2026-11-16")))
        // Back in the real calendar but a day was missed: a new run, as anywhere.
        assertEquals(StreakStep(VisitStreak(1, 8, d("2026-11-17")), StreakChange.RESTARTED), visitStreakOn(back.streak, d("2026-11-17")))
    }

    /** Upgraders' paw prints: the run of days in a row ending today or yesterday, today counted in. */
    @Test fun aRunIsSeededFromThisMonthsPawPrints() {
        val day = d("2026-11-14")
        assertEquals(VisitStreak(5, 5, day), seededStreak(setOf(3, 10, 11, 12, 13), day))   // ends yesterday: + today
        assertEquals(VisitStreak(3, 3, day), seededStreak(setOf(12, 13, 14), day))         // ends today
        assertEquals(VisitStreak(2, 2, day), seededStreak(setOf(13), day))                 // yesterday alone, and today
        assertEquals(null, seededStreak(setOf(14), day))                                   // today alone: just day one
        assertEquals(null, seededStreak(setOf(1, 2, 3, 11, 12), day))                      // ended two days ago
        assertEquals(null, seededStreak(emptySet(), day))
        // The first of the month: last month's prints are not kept, so nothing can be proved.
        assertEquals(null, seededStreak(setOf(1), d("2026-12-01")))
    }

    @Test fun monthAndYearEndsAreJustTheNextDay() {
        for ((a, b) in listOf("2026-01-31" to "2026-02-01", "2026-02-28" to "2026-03-01", "2028-02-28" to "2028-02-29", "2028-02-29" to "2028-03-01",
                "2026-04-30" to "2026-05-01", "2026-12-31" to "2027-01-01")) {
            val step = on(VisitStreak(2, 2, d(a)), b)
            assertEquals("$a to $b", StreakChange.NEXT_DAY, step.change)
            assertEquals(3, step.streak.days)
        }
        // A non-leap February has no 29th: the 28th to 1 March is the next day (above); the 27th to 1 March is not.
        assertEquals(StreakChange.RESTARTED, on(VisitStreak(2, 2, d("2026-02-27")), "2026-03-01").change)
    }

    /**
     * Daylight saving: the phone's date is what counts, not 24 hours. London's clocks went forward on 29 March 2026 (a
     * 23-hour day) and back on 25 October 2026 (25 hours); an open late on the evening before and one late on the
     * evening of the change are next-day opens either way, and two opens either side of midnight are too.
     */
    @Test fun aDaylightSavingChangeNeitherMakesNorBreaksADay() {
        val london = ZoneId.of("Europe/London")
        fun day(instant: String) = Instant.parse(instant).atZone(london).toLocalDate()
        for ((a, b) in listOf("2026-03-28T23:30:00Z" to "2026-03-29T22:30:00Z", // 23 h apart, next date
                "2026-10-24T22:30:00Z" to "2026-10-25T23:59:00Z",               // 25.5 h apart, next date
                "2026-03-28T23:50:00Z" to "2026-03-29T00:10:00Z")) {            // 20 minutes apart, either side of midnight
            val step = visitStreakOn(VisitStreak(1, 1, day(a)), day(b))
            assertEquals("$a to $b", StreakChange.NEXT_DAY, step.change)
        }
        // Two opens on the same date across the change are the same day.
        assertEquals(StreakChange.SAME_DAY, visitStreakOn(VisitStreak(1, 1, day("2026-10-24T23:10:00Z")), day("2026-10-25T00:30:00Z")).change)
    }

    @Test fun aYearOfDaysThenAGap() {
        var s: VisitStreak? = null
        var date = d("2026-01-01")
        repeat(400) { s = visitStreakOn(s, date).streak; date = date.plusDays(1) }
        assertEquals(VisitStreak(400, 400, d("2027-02-04")), s)
        s = visitStreakOn(s, date.plusDays(3)).streak
        assertEquals(1, s!!.days); assertEquals(400, s!!.best)
    }

    @Test fun theMarkerAndTalkBack() {
        assertEquals("Opening streak: 12 days in a row. Best: 31.", streakSpoken(VisitStreak(12, 31, d("2026-11-14"))))
        assertEquals("Opening streak: 1 day in a row. Best: 1.", streakSpoken(VisitStreak(1, 1, d("2026-11-14"))))
        assertEquals("365", streakShown(365))
        assertEquals("999", streakShown(999))
        assertEquals("999+", streakShown(1_204))
        assertFalse(streakShown(1).isEmpty())
        assertTrue(streakShown(12).all { it.isDigit() })
    }

    /**
     * The chalkboard's tally: "day" on the top row and the count under it, each centred, on the slate's face with at least
     * one art pixel of margin all round, never touching, for every count; past 999 it reads "999+".
     */
    @Test fun theTallyFitsOnTheSlate() {
        val face = SLATE_FACE_RECT
        val day = chalkboardTally(1).filter { it.y < chalkboardTally(1).minOf { p -> p.y } + ChalkFont.HEIGHT }.toSet()
        for (n in (1..999) + listOf(1_000, 12_345)) {
            val chalk = chalkboardTally(n)
            for (p in chalk) assertTrue("$n: chalk at $p is not inside the face $face by a pixel",
                p.x > face.left && p.x < face.right - 1 && p.y > face.top && p.y < face.bottom - 1)
            val top = chalk.minOf { it.y }
            val word = chalk.filter { it.y < top + ChalkFont.HEIGHT }
            val count = chalk.filter { it.y >= top + ChalkFont.HEIGHT }
            assertEquals("$n: the word \"day\" is the same every time", day, word.toSet())
            assertTrue("$n: a gap between the rows", count.minOf { it.y } - word.maxOf { it.y } >= 2)
            // Centred: the count's ink is as far from the face's left as from its right, give or take a pixel.
            val left = count.minOf { it.x } - face.left; val right = face.right - 1 - count.maxOf { it.x }
            assertTrue("$n: count off centre ($left, $right)", kotlin.math.abs(left - right) <= 1)
        }
        assertEquals("999+", streakShown(1_000))
        assertEquals(chalkboardTally(5_000), chalkboardTally(1_000))
        assertTrue("past 999 the count is wider than 999", chalkboardTally(1_000).maxOf { it.x } > chalkboardTally(999).maxOf { it.x })
    }

    /** Bert's word on a new day of a run comes once a day, each special length once over a long run, and never scolds. */
    @Test fun bertsDayLines() {
        var s: VisitStreak? = null
        var date = LocalDate.parse("2026-01-01")
        val said = mutableListOf<String>()
        repeat(400) {
            val step = visitStreakOn(s, date)
            BertLines.dayLine(step, date)?.let { said += it }
            // Coming back the same day says nothing more.
            assertEquals(null, BertLines.dayLine(visitStreakOn(step.streak, date), date))
            s = step.streak; date = date.plusDays(1)
        }
        assertEquals("one line a day after the first", 399, said.size)
        for ((n, line) in BertLines.streakDays) assertEquals("day $n's line", 1, said.count { it == line })
        assertEquals("the first open ever leaves the newcomer's hint alone", null, BertLines.dayLine(visitStreakOn(null, date), date))
        // A restart is welcomed, never scolded.
        val back = BertLines.dayLine(visitStreakOn(s, date.plusDays(5)), date.plusDays(5))!!
        assertTrue(back, back in BertLines.streakBack)
        // Every streak line he can say: the day lines, the everyday rotation, a restart, and the chalkboard's tally.
        val everyStreakLine = BertLines.streakBack + BertLines.streakDays.values + (2..400).flatMap { n -> (0..2).map { BertLines.streakDay(n, it) } } +
            listOf(VisitStreak(1, 1, date), VisitStreak(1, 31, date), VisitStreak(12, 31, date), VisitStreak(31, 31, date)).map { BertLines.streakTold(it) }
        for (line in everyStreakLine)
            for (word in listOf("lost", "missed", "miss", "broke", "broken", "streak", "tomorrow", "don\u2019t forget", "should", "habit", "!"))
                assertFalse("$line: $word", line.contains(word))
        for (line in everyStreakLine) assertEquals(line.lowercase(), line)
        assertEquals("day one. everyone starts somewhere.", BertLines.streakTold(VisitStreak(1, 1, date)))
        assertEquals("12 days running. best is 31.", BertLines.streakTold(VisitStreak(12, 31, date)))
        assertEquals("31 days running. your best yet.", BertLines.streakTold(VisitStreak(31, 31, date)))
        assertEquals("day one of a new run. best is 31.", BertLines.streakTold(VisitStreak(1, 31, date)))
    }

    /**
     * Nothing about the streak or the pats needs the network or a new permission: the manifest asks for what it did. The
     * source check is a tripwire on the two files that hold the rule and its store, not proof about the whole app (the
     * app counts the visit from MainActivity, which also handles alerts and so mentions notifications).
     */
    @Test fun theStreakStaysOnThePhone() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val asked = Regex("uses-permission android:name=\"([^\"]+)\"(?! tools:node=\"remove\")").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.SET_WALLPAPER",
            "android.permission.POST_NOTIFICATIONS"), asked)
        // The rule and its store use no network, no notifications and no work scheduling.
        for (file in listOf("src/main/java/global/bert/widget/VisitStreak.kt", "src/main/java/global/bert/widget/BertsPlace.kt")) {
            val source = java.io.File(file).readText()
            for (word in listOf("java.net", "okhttp", "HttpURLConnection", "Notification", "WorkManager", "AlarmManager"))
                assertFalse("$file uses $word", source.contains(word, ignoreCase = true))
        }
    }
}
