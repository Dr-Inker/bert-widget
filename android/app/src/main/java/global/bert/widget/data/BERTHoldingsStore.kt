package global.bert.widget.data

import android.content.Context

class BERTHoldingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("bert_holdings", Context.MODE_PRIVATE)

    fun load(): Double? = preferences.getString(KEY, null)
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 }

    fun save(amount: Double?) {
        preferences.edit().apply {
            if (amount == null) remove(KEY) else putString(KEY, amount.toString())
        }.apply()
    }

    companion object {
        private const val KEY = "token_amount"
    }
}
