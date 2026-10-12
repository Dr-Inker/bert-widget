package global.bert.widget

import android.content.Intent

/** Where a widget or notification tap lands, carried as an intent extra into MainActivity. */
internal enum class BERTEntry {
    HOME, MARKET,

    /** Launcher shortcut: open the Lost Trail (LostTrailActivity is not exported, so the shortcut comes in through MainActivity). */
    GAME,

    /** Launcher shortcut: the Create tab, where a card is made. */
    CREATE;

    companion object {
        const val EXTRA = "global.bert.widget.ENTRY"

        /** Unknown or missing values open the app where it was; extras from other apps are never trusted further. */
        fun from(intent: Intent?): BERTEntry? = intent?.getStringExtra(EXTRA)?.let { value -> entries.firstOrNull { it.name == value } }
    }
}
