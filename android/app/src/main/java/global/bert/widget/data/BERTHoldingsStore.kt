package global.bert.widget.data

import android.content.Context

class BERTHoldingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("bert_holdings", Context.MODE_PRIVATE)

    fun load(): Double? = preferences.getString(KEY, null)
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 }

    fun save(amount: Double?) {
        require(amount == null || (amount.isFinite() && amount >= 0))
        preferences.edit().apply {
            if (amount == null) { remove(KEY); remove(COST_KEY) } else putString(KEY, amount.toString())
        }.apply()
    }

    fun loadPosition(): BERTPosition = BERTPosition(
        amount = load(),
        totalCostUsd = preferences.getString(COST_KEY, null)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0 },
    )

    fun savePosition(position: BERTPosition) {
        preferences.edit().apply {
            if (position.amount == null) remove(KEY) else putString(KEY, position.amount.toString())
            if (position.amount == null || position.totalCostUsd == null) remove(COST_KEY)
            else putString(COST_KEY, position.totalCostUsd.toString())
        }.apply()
    }

    /**
     * "Show my holdings on the Market widget". On unless the person switched it off, so holdings entered before the
     * switch existed keep showing as they did. Removing or editing holdings keeps the choice.
     */
    var showOnWidget: Boolean
        get() = preferences.getBoolean(SHOW_ON_WIDGET_KEY, true)
        set(value) { preferences.edit().putBoolean(SHOW_ON_WIDGET_KEY, value).apply() }

    companion object {
        private const val KEY = "token_amount"
        private const val COST_KEY = "total_cost_usd"
        private const val SHOW_ON_WIDGET_KEY = "show_on_widget"
    }
}
