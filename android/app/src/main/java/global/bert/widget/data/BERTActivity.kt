package global.bert.widget.data

import org.json.JSONObject
import java.time.Instant

data class BERTActivity(
    val updatedAtEpochMillis: Long,
    val dispatch: String?,
    val mood: String?,
    val event: BERTEvent?,
) {
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
            require(value.isFinite() && value > 0 && value <= (now + 60_000) / 1000.0 && value % 1.0 == 0.0) {
                "Invalid activity timestamp"
            }
            val dispatch = root.boundedText("latest_post", 800, multiline = true)
            val mood = root.boundedText("mood", 32)
            // A bad optional event must not hide an otherwise valid dispatch.
            val event = root.optJSONObject("flappy")?.let { json ->
                runCatching {
                    val name = json.boundedText("name", 100) ?: error("Missing event name")
                    val end = Instant.parse(json.getString("ends_at")).toEpochMilli()
                    require(end > 0)
                    BERTEvent(name, end, json.boundedText("status", 24)?.lowercase().orEmpty())
                }.getOrNull()
            }
            require(dispatch != null || mood != null || event != null) { "Empty activity" }
            return BERTActivity(seconds.toLong() * 1000, dispatch, mood, event)
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

data class BERTEvent(val name: String, val endsAtEpochMillis: Long, val status: String) {
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

sealed interface ActivityState {
    data object Loading : ActivityState
    data class Available(val activity: BERTActivity, val updateDelayed: Boolean = false) : ActivityState
    data object Unavailable : ActivityState
}
