package global.bert.widget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One of Berthalla's posts as this phone first saw it: [firstSeenEpochMillis] (what "new since your last visit" counts),
 * and [postedAtEpochMillis], when Berthalla wrote it, if the feed said (feeds before 0.15.1 did not).
 */
data class JournalEntry(val text: String, val mood: String?, val firstSeenEpochMillis: Long, val postedAtEpochMillis: Long? = null) {
    /** The post's date as the board shows it: when it was written, else when this phone first saw it. */
    val shownAtEpochMillis: Long get() = postedAtEpochMillis ?: firstSeenEpochMillis
}

/**
 * Every dispatch this phone has seen, newest first, kept on the device only. The feed holds just the latest post,
 * so without this yesterday's update is gone for good.
 */
class BERTJournal(context: Context) {
    private val preferences = context.getSharedPreferences("bert_journal", Context.MODE_PRIVATE)

    fun entries(): List<JournalEntry> = parse(preferences.getString(KEY, null))

    /** Adds [activity]'s dispatch if it is new; returns true when it was. */
    fun record(activity: BERTActivity, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val text = activity.dispatch ?: return false
        val current = entries()
        // A dispatch seen before keeps its place and date, even if the feed falls back to it later; it only learns its
        // post time if it was first seen without one (saved by 0.15.0, before the feed carried it).
        val seen = current.indexOfFirst { it.text == text }
        if (seen >= 0) {
            val posted = activity.postedAtEpochMillis
            if (posted != null && current[seen].postedAtEpochMillis == null)
                preferences.edit().putString(KEY, encode(current.toMutableList().also { it[seen] = it[seen].copy(postedAtEpochMillis = posted) })).apply()
            return false
        }
        val next = (listOf(JournalEntry(text, activity.mood, now, activity.postedAtEpochMillis)) + current).take(MAX_ENTRIES)
        preferences.edit().putString(KEY, encode(next)).apply()
        true
    }

    companion object {
        private const val KEY = "entries"
        const val MAX_ENTRIES = 60
        private val lock = Any()

        internal fun encode(entries: List<JournalEntry>): String = JSONArray().apply {
            entries.forEach { put(JSONObject().put("t", it.text).putOpt("m", it.mood).put("s", it.firstSeenEpochMillis).putOpt("p", it.postedAtEpochMillis)) }
        }.toString()

        internal fun parse(raw: String?): List<JournalEntry> = runCatching {
            val array = JSONArray(raw ?: return emptyList())
            (0 until minOf(array.length(), MAX_ENTRIES)).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val text = item.optString("t").takeIf { it.isNotBlank() && it.length <= 800 } ?: return@mapNotNull null
                JournalEntry(text, item.optString("m").takeIf { it.isNotBlank() && it.length <= 32 }, item.optLong("s").takeIf { it > 0 } ?: return@mapNotNull null,
                    item.optLong("p").takeIf { it > 0 })
            }
        }.getOrDefault(emptyList())
    }
}
