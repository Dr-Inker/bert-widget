package global.bert.widget.data

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener

/**
 * What Home shows about The Lost Trail. The game owns its save format (bert-platformer `engine.mjs`: progress v1 =
 * per-world best results with up to 3 bones each; run v1 = the world in progress). This reads only those fields,
 * version-guarded, so a future save format degrades to "no progress shown" rather than a wrong claim.
 */
data class LostTrailProgress(val worldsCleared: Int, val bones: Int, val worldInProgress: Int?) {
    val started: Boolean get() = worldsCleared > 0 || worldInProgress != null

    /** The next world to play: the one in progress, else the first not yet cleared. Null once all six are done. */
    val nextWorld: Int? get() = worldInProgress ?: worldsCleared.takeIf { it < WORLDS.size }

    companion object {
        const val TOTAL_BONES = 18
        val WORLDS = listOf("Lantern Orchard", "Crystal Hollows", "Galepeak Gardens", "Ember Foundry", "Moonlit Fen", "Aurora Citadel")
        private const val PREFS = "bert_lost_trail"
        private const val KEY = "storage"

        /** [evaluateResult] is WebView.evaluateJavascript's JSON-encoded string of {p, r}: the two localStorage values. */
        fun store(context: Context, evaluateResult: String?) {
            val inner = runCatching { JSONTokener(evaluateResult ?: return).nextValue() as? String }.getOrNull() ?: return
            if (inner.length > 16 * 1024 || parse(inner) == null) return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, inner).apply()
        }

        fun load(context: Context): LostTrailProgress? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let(::parse)

        internal fun parse(storage: String): LostTrailProgress? = runCatching {
            val root = JSONObject(storage)
            var cleared = 0; var bones = 0
            root.optString("p").takeIf { it.isNotEmpty() && it != "null" }?.let { raw ->
                val progress = JSONObject(raw)
                if (progress.optInt("version") != 1) return null
                val worlds = progress.optJSONArray("worlds")
                for (i in 0 until minOf(worlds?.length() ?: 0, WORLDS.size)) {
                    val world = worlds!!.optJSONObject(i) ?: continue
                    if (world.optDouble("time", 0.0) > 0) { cleared++; bones += world.optInt("bones").coerceIn(0, 3) }
                }
            }
            val inProgress = root.optString("r").takeIf { it.isNotEmpty() && it != "null" }?.let { raw ->
                val run = JSONObject(raw)
                run.optInt("world", -1).takeIf { run.optInt("version") == 1 && it in WORLDS.indices }
            }
            LostTrailProgress(cleared, bones, inProgress)
        }.getOrNull()
    }
}
