package global.bert.widget.data

import android.content.Context
import java.time.LocalDate

/**
 * What one look at Home knows about earlier looks. [since] is when the person last looked at Home before this
 * sitting, so anything the journal first saw after it is news to them; [days] is how many local days the app has
 * been opened, ever.
 */
data class HomeVisit(val since: Long, val days: Int)

/** Local-only return state for Home. It counts up quietly: never resets, never notifies, never leaves the phone. */
class BERTHomeVisits(context: Context) {
    private val preferences = context.getSharedPreferences("bert_home", Context.MODE_PRIVATE)

    /** Counts [today] once. A clock set backwards neither counts nor rewinds the last day. */
    fun recordOpen(today: LocalDate = LocalDate.now()): Int = synchronized(lock) {
        val last = preferences.getLong(LAST_DAY, Long.MIN_VALUE)
        val days = preferences.getInt(DAYS, 0)
        if (today.toEpochDay() <= last) return days
        preferences.edit().putLong(LAST_DAY, today.toEpochDay()).putInt(DAYS, days + 1).apply()
        days + 1
    }

    /**
     * Called while Home is on screen. A look more than [SITTING_GAP] after the previous one starts a new sitting,
     * whose baseline is that previous look. The very first look starts at [now], so an upgrade's journal is not news.
     */
    fun visit(now: Long, today: LocalDate = LocalDate.now()): HomeVisit = synchronized(lock) {
        val days = recordOpen(today)
        val seen = preferences.getLong(SEEN_AT, 0L)
        val since = when {
            seen <= 0L -> now
            now - seen > SITTING_GAP || now < seen -> seen.coerceAtMost(now)
            else -> preferences.getLong(SINCE, seen)
        }
        preferences.edit().putLong(SEEN_AT, now).putLong(SINCE, since).apply()
        HomeVisit(since, days)
    }

    var meetBertDismissed: Boolean
        get() = preferences.getBoolean(MEET_BERT, false)
        set(value) = preferences.edit().putBoolean(MEET_BERT, value).apply()

    companion object {
        private const val LAST_DAY = "last_open_day"
        private const val DAYS = "open_days"
        private const val SEEN_AT = "home_seen_at"
        private const val SINCE = "home_since"
        private const val MEET_BERT = "meet_bert_dismissed"
        const val SITTING_GAP = 30 * 60_000L
        /** Today's update is "New" only while it is this young and the person hasn't seen it in an earlier sitting. */
        const val NEW_WINDOW = 6 * 3_600_000L
        private val lock = Any()

        fun isNew(entry: JournalEntry?, visit: HomeVisit?, now: Long): Boolean =
            entry != null && visit != null && entry.firstSeenEpochMillis > visit.since && now - entry.firstSeenEpochMillis < NEW_WINDOW

        /** Journal entries first seen since the last sitting, not counting the one Today already shows. */
        fun newSince(entries: List<JournalEntry>, visit: HomeVisit?, current: String?): List<JournalEntry> =
            if (visit == null) emptyList() else entries.filter { it.text != current && it.firstSeenEpochMillis > visit.since }
    }
}
