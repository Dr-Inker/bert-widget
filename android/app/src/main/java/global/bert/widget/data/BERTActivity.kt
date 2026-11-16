package global.bert.widget.data

import org.json.JSONObject
import java.time.Instant

data class BERTActivity(
    val updatedAtEpochMillis: Long,
    val dispatch: String?,
    val mood: String?,
    val event: BERTEvent?,
    /**
     * When Berthalla wrote the post (latest_post_at), if the feed says: the post's own date, never the refresh's. Null
     * for an older feed without it, or a value the parser does not trust.
     */
    val postedAtEpochMillis: Long? = null,
    /** The post's id on X (latest_post_x_id), digits only; null when the post was never published on X. */
    val postXId: String? = null,
) {
    /** Berthalla's post is on X (it has an X id); a fallback post that was never published is not. */
    val postIsOnX: Boolean get() = postXId != null

    fun isStaleAt(now: Long): Boolean =
        updatedAtEpochMillis > now + 60_000 || now - updatedAtEpochMillis >= 30 * 60_000L

    companion object {
        // This timestamp describes the source refresh, not the dispatch publication date.
        // Financial fields from this source are never consumed by the app.
        fun parse(raw: String, now: Long = System.currentTimeMillis()): BERTActivity {
            require(raw.toByteArray(Charsets.UTF_8).size <= 32 * 1024) { "Activity response too large" }
            val root = JSONObject(raw)
            val seconds = root.opt("updated_at") as? Number ?: error("Missing activity timestamp")
            val value = seconds.toDouble()
            // status.json is stamped by the server's clock; a phone up to ten minutes slow reads it as written just now
            // (critique 2026-11-12 ENG-6), and anything further ahead is not trusted.
            require(value.isFinite() && value > 0 && value <= (now + MAX_CLOCK_LAG_MILLIS) / 1000.0 && value % 1.0 == 0.0) {
                "Invalid activity timestamp"
            }
            // Shown on Home and sent as a notification: no bidirectional controls, like player names (SEC-2).
            val dispatch = root.boundedText("latest_post", 800, multiline = true)?.let(::withoutBidi)?.trim()?.takeIf { it.isNotEmpty() }
            val mood = root.boundedText("mood", 32)
            // A bad optional event must not hide an otherwise valid dispatch.
            val event = root.optJSONObject("flappy")?.let { json ->
                runCatching {
                    val name = json.boundedText("name", 100) ?: error("Missing event name")
                    val end = Instant.parse(json.getString("ends_at")).toEpochMilli()
                    require(end > 0)
                    BERTEvent(
                        name, end, json.boundedText("status", 24)?.lowercase().orEmpty(),
                        pool = json.boundedInt("pool", 1_000_000),
                        sponsor = json.boundedText("sponsor", 60)?.let(::playerText),
                        standings = json.optJSONArray("top")?.let(::parseStandings).orEmpty(),
                    )
                }.getOrNull()
            }
            if (dispatch == null && mood == null && event == null) throw EmptyActivityException()
            // Optional (feeds before server 8f64c5a lack both); a bad value is dropped, never a reason to hide the post.
            val postedAt = postTime(root.opt("latest_post_at"), now)
            val xId = (root.opt("latest_post_x_id") as? String)?.takeIf { it.length in 1..MAX_X_ID && it.all { c -> c in '0'..'9' } }
            return BERTActivity(minOf(seconds.toLong() * 1000, now), dispatch, mood, event, postedAt, xId)
        }

        private const val MAX_X_ID = 25

        /**
         * The post's time: a positive whole number of seconds, not ahead of the phone by more than the feed's own
         * allowance ([MAX_CLOCK_LAG_MILLIS]; read as now if it is ahead within it). Anything else is null.
         */
        private fun postTime(raw: Any?, now: Long): Long? {
            val value = (raw as? Number)?.toDouble() ?: return null
            if (!value.isFinite() || value <= 0 || value % 1.0 != 0.0 || value > (now + MAX_CLOCK_LAG_MILLIS) / 1000.0) return null
            return minOf(value.toLong() * 1000, now)
        }

        private const val MAX_CLOCK_LAG_MILLIS = 10 * 60_000L

        private fun isBidiControl(char: Char) =
            char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069' || char == '\u200F' || char == '\u200E'

        private fun withoutBidi(text: String) = text.filterNot(::isBidiControl)

        // Player names are untrusted text: bounded, no control or bidirectional-override characters.
        internal fun playerText(raw: String): String? = raw
            .filterNot { it.isISOControl() || isBidiControl(it) }
            .trim().take(32).takeIf { it.isNotEmpty() }

        private fun parseStandings(array: org.json.JSONArray): List<BERTStanding> = buildList {
            for (index in 0 until minOf(array.length(), 10)) {
                val row = array.optJSONObject(index) ?: continue
                val rank = row.boundedInt("rank", 100)?.takeIf { it >= 1 } ?: continue
                val name = (row.opt("name") as? String)?.let(::playerText) ?: continue
                val score = row.boundedInt("score", 10_000_000) ?: continue
                add(BERTStanding(rank, name, score, row.boundedInt("prize", 1_000_000)))
            }
        }.distinctBy { it.rank }.sortedBy { it.rank }.take(5)

        private fun JSONObject.boundedInt(key: String, max: Int): Int? {
            val value = (opt(key) as? Number)?.toDouble() ?: return null
            return value.takeIf { it.isFinite() && it >= 0 && it <= max && it % 1.0 == 0.0 }?.toInt()
        }

        private fun JSONObject.boundedText(key: String, limit: Int, multiline: Boolean = false): String? {
            val value = (opt(key) as? String)?.trim() ?: return null
            return value.takeIf {
                it.isNotEmpty() && it.length <= limit && it.none { char ->
                    char.isISOControl() && !(multiline && char == '\n')
                }
            }
        }
    }
}

data class BERTStanding(val rank: Int, val name: String, val score: Int, val prizeUsd: Int?)

data class BERTEvent(
    val name: String,
    val endsAtEpochMillis: Long,
    val status: String,
    val pool: Int? = null,
    val sponsor: String? = null,
    val standings: List<BERTStanding> = emptyList(),
) {
    fun phaseAt(now: Long, sourceDelayed: Boolean): EventPhase = when {
        endsAtEpochMillis <= now || status == "ended" || status == "closed" -> EventPhase.ENDED
        sourceDelayed -> EventPhase.UNCONFIRMED
        status == "live" -> EventPhase.OPEN
        status == "upcoming" || status == "scheduled" -> EventPhase.UPCOMING
        else -> EventPhase.UNCONFIRMED
    }
}

enum class EventPhase(val label: String) {
    OPEN("TOURNAMENT OPEN"), ENDED("TOURNAMENT ENDED"), UPCOMING("COMING UP"), UNCONFIRMED("STATUS UNCONFIRMED")
}

/** The feed answered, well formed, with nothing in it: a quiet day, not a fault. */
class EmptyActivityException : IllegalArgumentException("Empty activity")

sealed interface ActivityState {
    data object Loading : ActivityState
    data class Available(val activity: BERTActivity, val updateDelayed: Boolean = false) : ActivityState
    /** The feed could not be fetched (or made no sense) and nothing is saved: the connection is the problem. */
    data object Unavailable : ActivityState
    /** The feed was fetched and is empty, and nothing is saved: there is just no post. */
    data object Empty : ActivityState

    companion object {
        /** What a failed refresh leaves: the [saved] feed, marked delayed, if there is one; else whether it was empty or unreachable. */
        fun afterFailure(error: Throwable, saved: BERTActivity?): ActivityState = when {
            saved != null -> Available(saved, updateDelayed = true)
            error is EmptyActivityException -> Empty
            else -> Unavailable
        }
    }
}
