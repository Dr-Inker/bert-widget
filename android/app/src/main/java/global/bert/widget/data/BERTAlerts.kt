package global.bert.widget.data

import android.content.Context

/** What the person has switched on. Everything is off by default. */
data class AlertSettings(
    val tournament: Boolean = false,
    val dispatch: Boolean = false,
    val priceAbove: Double? = null,
    val priceBelow: Double? = null,
) {
    val anyEnabled: Boolean get() = tournament || dispatch || priceAbove != null || priceBelow != null
}

/** What has already been announced, so each alert fires once. */
data class AlertMemory(
    val notifiedTournament: String? = null,
    val lastDispatch: String? = null,
    val aboveFired: Boolean = false,
    val belowFired: Boolean = false,
)

enum class AlertChannel(val id: String, val title: String, val description: String) {
    TOURNAMENT("tournament", "Tournament reminders", "When a Flappy Bert tournament is about to end"),
    DISPATCH("dispatch", "Bert's updates", "New dispatches from Bert"),
    PRICE("price", "Price alerts", "BERT crossing a price you chose"),
}

data class AlertNotice(val channel: AlertChannel, val id: Int, val title: String, val text: String)

object BERTAlertRules {
    private const val TOURNAMENT_WINDOW_MILLIS = 24L * 60 * 60 * 1_000

    fun evaluate(settings: AlertSettings, memory: AlertMemory, quote: BERTQuote?, activity: BERTActivity?, now: Long): Pair<List<AlertNotice>, AlertMemory> {
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
            if (memory.lastDispatch != null) notices += AlertNotice(AlertChannel.DISPATCH, 1002, "New from Bert", dispatch.take(240))
            next = next.copy(lastDispatch = dispatch)
        }

        if (quote != null && !quote.isStaleAt(now)) {
            settings.priceAbove?.let { level ->
                if (!memory.aboveFired && quote.priceUsd >= level) {
                    notices += AlertNotice(AlertChannel.PRICE, 1003, "BERT is above ${usd(level)}", "Now ${usd(quote.priceUsd)}${change(quote)}")
                    next = next.copy(aboveFired = true)
                }
            }
            settings.priceBelow?.let { level ->
                if (!memory.belowFired && quote.priceUsd <= level) {
                    notices += AlertNotice(AlertChannel.PRICE, 1004, "BERT is below ${usd(level)}", "Now ${usd(quote.priceUsd)}${change(quote)}")
                    next = next.copy(belowFired = true)
                }
            }
        }
        return notices to next
    }

    private fun usd(value: Double) = global.bert.widget.formatPrice(value)
    private fun change(quote: BERTQuote) = quote.change24hPct?.let { " · ${global.bert.widget.formatPercent(it)} today" }.orEmpty()
}

class BERTAlertStore(context: Context) {
    private val preferences = context.getSharedPreferences("bert_alerts", Context.MODE_PRIVATE)

    fun settings() = AlertSettings(
        tournament = preferences.getBoolean("tournament", false),
        dispatch = preferences.getBoolean("dispatch", false),
        priceAbove = preferences.getString("above", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
        priceBelow = preferences.getString("below", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
    )

    fun memory() = AlertMemory(
        notifiedTournament = preferences.getString("notified_tournament", null),
        lastDispatch = preferences.getString("last_dispatch", null),
        aboveFired = preferences.getBoolean("above_fired", false),
        belowFired = preferences.getBoolean("below_fired", false),
    )

    /** Changing a price level re-arms it; switching dispatches on starts from the current one. */
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
        }.commit()
    }

    fun saveMemory(memory: AlertMemory) = synchronized(lock) {
        preferences.edit().apply {
            if (memory.notifiedTournament == null) remove("notified_tournament") else putString("notified_tournament", memory.notifiedTournament)
            if (memory.lastDispatch == null) remove("last_dispatch") else putString("last_dispatch", memory.lastDispatch)
            putBoolean("above_fired", memory.aboveFired)
            putBoolean("below_fired", memory.belowFired)
        }.commit()
    }

    /** A dispatch read in the open app is not news later. */
    fun markDispatchSeen(dispatch: String) = synchronized(lock) {
        if (preferences.getBoolean("dispatch", false)) preferences.edit().putString("last_dispatch", dispatch).commit()
    }

    private companion object { val lock = Any() }
}
