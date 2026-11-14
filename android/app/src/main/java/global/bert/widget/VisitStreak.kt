package global.bert.widget

import java.time.LocalDate

/**
 * 0.15, the owner: "a daily app opening streak". Days in a row that the app was opened (any way in), by the phone's own calendar date,
 * kept on the phone only (bert_place, with the newcomer state and the pat count). Nothing is sent anywhere and nothing
 * reminds anyone to come back: Bert notices, and that is all.
 */
internal data class VisitStreak(val days: Int, val best: Int, val last: LocalDate, val anchor: LocalDate? = null)

/** What one opening of the app did to the streak. */
internal enum class StreakChange {
    /** The first open ever counted (a fresh install, or an install from before 0.15): one day. */
    FIRST,
    /** Again on the date already counted: nothing changes. */
    SAME_DAY,
    /** The day after the last one counted: one more. */
    NEXT_DAY,
    /** After a gap of a day or more: a new run starts at one; the best is kept. */
    RESTARTED,
    /** A date before the last one counted, by a day or two (a time zone, or the clock nudged back): nothing changes. */
    CLOCK_BACK,
    /**
     * The last day counted is more than [CLOCK_SLACK_DAYS] after today. Either the clock was set forward and has come
     * back, or it is behind for now and will be put right. Nothing is decided yet: the run, the best and the last day are
     * kept, and today is remembered as [VisitStreak.anchor]. If the clock comes back to the last day the run simply goes
     * on; if the next open is the day after the anchor, the run goes on from there instead of freezing.
     */
    REANCHORED,
}

/** How far ahead of today the last counted day may be and still be a time zone, not a clock that was set forward. */
internal const val CLOCK_SLACK_DAYS = 2L

internal data class StreakStep(val streak: VisitStreak, val change: StreakChange)

/**
 * The rule, pure: opening the app on [today] (the phone's local date) with [was] counted so far. Dates, not hours: a
 * daylight-saving change or a late-night open never makes or breaks a day.
 */
internal fun visitStreakOn(was: VisitStreak?, today: LocalDate): StreakStep = when {
    was == null -> StreakStep(VisitStreak(1, 1, today), StreakChange.FIRST)
    today == was.last -> StreakStep(was.copy(anchor = null), StreakChange.SAME_DAY)
    today.plusDays(CLOCK_SLACK_DAYS).isBefore(was.last) -> {
        val anchor = was.anchor
        when {
            // First sight of a clock far behind the last day: remember today, decide nothing.
            anchor == null -> StreakStep(was.copy(anchor = today), StreakChange.REANCHORED)
            today == anchor -> StreakStep(was, StreakChange.SAME_DAY)
            // Further back still by more than a time zone: remember that day instead. A day or two back is a time zone.
            today.plusDays(CLOCK_SLACK_DAYS).isBefore(anchor) -> StreakStep(was.copy(anchor = today), StreakChange.REANCHORED)
            today.isBefore(anchor) -> StreakStep(was, StreakChange.CLOCK_BACK)
            // A second day in a row back here: this is the real calendar, and the run goes on in it.
            today == anchor.plusDays(1) -> (was.days + 1).let { StreakStep(VisitStreak(it, maxOf(was.best, it), today), StreakChange.NEXT_DAY) }
            else -> StreakStep(VisitStreak(1, maxOf(was.best, 1), today), StreakChange.RESTARTED)
        }
    }
    today.isBefore(was.last) -> StreakStep(was, StreakChange.CLOCK_BACK)
    today == was.last.plusDays(1) -> (was.days + 1).let { StreakStep(VisitStreak(it, maxOf(was.best, it), today), StreakChange.NEXT_DAY) }
    else -> StreakStep(VisitStreak(1, maxOf(was.best, 1), today), StreakChange.RESTARTED)
}

/**
 * For an install from before 0.15, which has no streak yet: the run of days in a row in [prints] (this month's paw
 * prints, the days of [today]'s month Home was opened) that ends today or yesterday, today's open counted in. Null when
 * there is no run of two days or more (then the streak starts at one as for anyone new). Paw prints only cover the
 * current month and only days Home was shown, so a run is never longer than the prints can prove.
 */
internal fun seededStreak(prints: Set<Int>, today: LocalDate): VisitStreak? {
    val day = today.dayOfMonth
    val end = when { day in prints -> day; day - 1 in prints -> day - 1; else -> return null }
    var start = end
    while (start - 1 in prints) start--
    val days = end - start + 1 + (if (end == day) 0 else 1)
    return if (days >= 2) VisitStreak(days, days, today) else null
}

/** What TalkBack says for the chalkboard in the garden, whose tally is the streak. */
internal fun streakSpoken(s: VisitStreak): String =
    "Opening streak: ${s.days} ${if (s.days == 1) "day" else "days"} in a row. Best: ${s.best}."

/** The chalkboard's count: up to three digits, then "999+". */
internal fun streakShown(days: Int): String = if (days > 999) "999+" else days.coerceAtLeast(1).toString()
