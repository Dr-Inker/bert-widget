package global.bert.widget.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** What the person has switched on. Everything is off by default. */
data class AlertSettings(
    val tournament: Boolean = false,
    val dispatch: Boolean = false,
    val priceAbove: Double? = null,
    val priceBelow: Double? = null,
    /** 24h move (percent, magnitude) that triggers a "big move" notice; null = off. */
    val bigMovePct: Int? = null,
) {
    val anyEnabled: Boolean get() = tournament || dispatch || priceAbove != null || priceBelow != null || bigMovePct != null
}

/** What has already been announced, so each alert fires once per crossing. */
data class AlertMemory(
    val notifiedTournament: String? = null,
    val lastDispatch: String? = null,
    val aboveFired: Boolean = false,
    val belowFired: Boolean = false,
    /** Side (+1 up, -1 down, 0 none) of the big move already announced; cleared once the move falls below the threshold. */
    val bigMoveSide: Int = 0,
    val bigMoveAtEpochMillis: Long? = null,
)

enum class AlertChannel(val id: String, val title: String, val description: String) {
    TOURNAMENT("tournament", "Tournament reminders", "When a Flappy Bert tournament is about to end"),
    DISPATCH("dispatch", "Berthalla’s updates", "New updates from Berthalla, in Bert’s voice"),
    PRICE("price", "Price alerts", "BERT crossing a price you chose, or a big move in 24 hours"),
}

data class AlertNotice(val channel: AlertChannel, val id: Int, val title: String, val text: String)

object BERTAlertRules {
    private const val TOURNAMENT_WINDOW_MILLIS = 24L * 60 * 60 * 1_000
    /** A fired price level re-arms once the price comes back this far past it, so a wobble around the level cannot spam. */
    const val REARM_FRACTION = 0.03
    const val BIG_MOVE_COOLDOWN_MILLIS = 6L * 60 * 60 * 1_000
    val BIG_MOVE_CHOICES = listOf(10, 20, 30)

    /** Which saved levels the fresh price has already crossed and that will therefore fire at the next check (above, below). */
    fun alreadyCrossed(settings: AlertSettings, memory: AlertMemory, quote: BERTQuote?, now: Long): Pair<Boolean, Boolean> {
        if (quote == null || quote.isStaleAt(now)) return false to false
        val above = settings.priceAbove?.let { !memory.aboveFired && quote.priceUsd >= it } ?: false
        val below = settings.priceBelow?.let { !memory.belowFired && quote.priceUsd <= it } ?: false
        return above to below
    }

    fun evaluate(
        settings: AlertSettings, memory: AlertMemory, quote: BERTQuote?, activity: BERTActivity?, now: Long,
        zone: ZoneId = ZoneId.systemDefault(), use24Hour: Boolean = true,
    ): Pair<List<AlertNotice>, AlertMemory> {
        val notices = mutableListOf<AlertNotice>()
        var next = memory

        val event = activity?.event
        if (settings.tournament && event != null && !activity.isStaleAt(now) &&
            event.phaseAt(now, sourceDelayed = false) == EventPhase.OPEN) {
            val left = event.endsAtEpochMillis - now
            val key = "${event.name}@${event.endsAtEpochMillis}"
            if (left in 1..TOURNAMENT_WINDOW_MILLIS && memory.notifiedTournament != key) {
                val hours = left / 3_600_000
                val leader = event.standings.firstOrNull()?.let { " ${it.name} leads with ${it.score}." }.orEmpty()
                val pool = event.pool?.let { " $$it prize pool." }.orEmpty()
                notices += AlertNotice(AlertChannel.TOURNAMENT, 1001, "${event.name} ends in ${if (hours >= 1) "${hours}h" else "under an hour"}",
                    "Last chance for a Flappy Bert run.$leader$pool".trim())
                next = next.copy(notifiedTournament = key)
            }
        }

        val dispatch = activity?.dispatch
        if (settings.dispatch && dispatch != null && dispatch != memory.lastDispatch) {
            // The first dispatch seen after switching on is the baseline, not news.
            if (memory.lastDispatch != null) notices += AlertNotice(AlertChannel.DISPATCH, 1002, "New from Berthalla", dispatch.take(240))
            next = next.copy(lastDispatch = dispatch)
        }

        if (quote != null && !quote.isStaleAt(now)) {
            settings.priceAbove?.let { level ->
                // Fired levels re-arm only after a real retreat, judged on the same fresh quote.
                if (memory.aboveFired && quote.priceUsd <= level * (1 - REARM_FRACTION)) next = next.copy(aboveFired = false)
                if (!next.aboveFired && quote.priceUsd >= level) {
                    notices += AlertNotice(AlertChannel.PRICE, 1003, "BERT is above ${usd(level)}", "Now ${usd(quote.priceUsd)}${change(quote)}")
                    next = next.copy(aboveFired = true)
                }
            }
            settings.priceBelow?.let { level ->
                if (memory.belowFired && quote.priceUsd >= level * (1 + REARM_FRACTION)) next = next.copy(belowFired = false)
                if (!next.belowFired && quote.priceUsd <= level) {
                    notices += AlertNotice(AlertChannel.PRICE, 1004, "BERT is below ${usd(level)}", "Now ${usd(quote.priceUsd)}${change(quote)}")
                    next = next.copy(belowFired = true)
                }
            }
            val threshold = settings.bigMovePct
            val move = quote.change24hPct
            if (threshold != null && move != null) {
                if (abs(move) < threshold) {
                    // The move has faded, so the next one counts as new. The cooldown clock is left alone.
                    if (memory.bigMoveSide != 0) next = next.copy(bigMoveSide = 0)
                } else {
                    val side = if (move > 0) 1 else -1
                    val last = memory.bigMoveAtEpochMillis
                    val cooled = last == null || now - last >= BIG_MOVE_COOLDOWN_MILLIS || last > now
                    // A flip from up to down is a new move but still waits out the cooldown; side is not recorded until it is announced.
                    if (side != memory.bigMoveSide && cooled) {
                        val percent = String.format(java.util.Locale.US, "%.0f", abs(move))
                        val asOf = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", java.util.Locale.US).format(Instant.ofEpochMilli(quote.observedAtEpochMillis).atZone(zone))
                        notices += AlertNotice(AlertChannel.PRICE, 1005, "BERT is ${if (side > 0) "up" else "down"} $percent% in 24h",
                            "Now ${usd(quote.priceUsd)} · as of $asOf · ${sourceLabel(quote)}")
                        next = next.copy(bigMoveSide = side, bigMoveAtEpochMillis = now)
                    }
                }
            }
        }
        return notices to next
    }

    private fun sourceLabel(quote: BERTQuote) = if (quote.sourceName == "dexscreener") "DEX Screener" else quote.sourceName
    private fun usd(value: Double) = global.bert.widget.formatPrice(value)
    private fun change(quote: BERTQuote) = quote.change24hPct?.let { " · ${global.bert.widget.formatPercent(it)} in 24h" }.orEmpty()
}

class BERTAlertStore(context: Context) {
    private val preferences = context.getSharedPreferences("bert_alerts", Context.MODE_PRIVATE)

    fun settings() = AlertSettings(
        tournament = preferences.getBoolean("tournament", false),
        dispatch = preferences.getBoolean("dispatch", false),
        priceAbove = preferences.getString("above", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
        priceBelow = preferences.getString("below", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
        bigMovePct = preferences.getInt("big_move", 0).takeIf { it in 1..100 },
    )

    fun memory() = AlertMemory(
        notifiedTournament = preferences.getString("notified_tournament", null),
        lastDispatch = preferences.getString("last_dispatch", null),
        aboveFired = preferences.getBoolean("above_fired", false),
        belowFired = preferences.getBoolean("below_fired", false),
        bigMoveSide = preferences.getInt("big_move_side", 0),
        bigMoveAtEpochMillis = if (preferences.contains("big_move_at")) preferences.getLong("big_move_at", 0) else null,
    )

    /** Changing a price level or the big-move threshold re-arms it; switching dispatches on starts from the current one. */
    fun save(settings: AlertSettings) = synchronized(lock) {
        val old = settings()
        preferences.edit().apply {
            putBoolean("tournament", settings.tournament)
            putBoolean("dispatch", settings.dispatch)
            if (settings.priceAbove == null) remove("above") else putString("above", settings.priceAbove.toString())
            if (settings.priceBelow == null) remove("below") else putString("below", settings.priceBelow.toString())
            if (settings.priceAbove != old.priceAbove) putBoolean("above_fired", false)
            if (settings.priceBelow != old.priceBelow) putBoolean("below_fired", false)
            if (settings.dispatch && !old.dispatch) remove("last_dispatch")
            if (settings.bigMovePct == null) remove("big_move") else putInt("big_move", settings.bigMovePct)
            // A new threshold (or switching on) starts unannounced; the 6-hour cooldown clock is kept.
            if (settings.bigMovePct != old.bigMovePct) putInt("big_move_side", 0)
        }.commit()
    }

    /**
     * Saves what a check announced only if the settings it judged are still current. A level or threshold saved
     * mid-check re-arms itself in [save]; writing the old check's memory over it would undo that.
     */
    fun saveMemoryIfUnchanged(memory: AlertMemory, evaluatedWith: AlertSettings): Boolean = synchronized(lock) {
        if (settings() != evaluatedWith) return false
        saveMemory(memory)
        true
    }

    fun saveMemory(memory: AlertMemory) = synchronized(lock) {
        preferences.edit().apply {
            if (memory.notifiedTournament == null) remove("notified_tournament") else putString("notified_tournament", memory.notifiedTournament)
            if (memory.lastDispatch == null) remove("last_dispatch") else putString("last_dispatch", memory.lastDispatch)
            putBoolean("above_fired", memory.aboveFired)
            putBoolean("below_fired", memory.belowFired)
            putInt("big_move_side", memory.bigMoveSide)
            if (memory.bigMoveAtEpochMillis == null) remove("big_move_at") else putLong("big_move_at", memory.bigMoveAtEpochMillis)
        }.commit()
    }

    /** A dispatch read in the open app is not news later. */
    fun markDispatchSeen(dispatch: String) = synchronized(lock) {
        if (preferences.getBoolean("dispatch", false)) preferences.edit().putString("last_dispatch", dispatch).commit()
    }

    private companion object { val lock = Any() }
}
