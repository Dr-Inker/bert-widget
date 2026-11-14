package global.bert.widget.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "Show my holdings on the Market widget" (critique 2026-11-12 PRIV-1) is kept next to the holdings themselves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BERTHoldingsStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun someoneWhoAlreadyEnteredHoldingsStillSeesThemOnTheWidget() {
        // Holdings saved by 0.14.0, before the switch existed: only the amount and cost are in the file.
        context.getSharedPreferences("bert_holdings", Context.MODE_PRIVATE).edit()
            .putString("token_amount", "250000.0").putString("total_cost_usd", "1250.0").commit()
        val store = BERTHoldingsStore(context)
        assertEquals(BERTPosition(250_000.0, 1250.0), store.loadPosition())
        assertTrue(store.showOnWidget)
    }

    @Test fun switchingItOffIsKeptBesideTheHoldingsAndSurvivesEditsAndRemoval() {
        val store = BERTHoldingsStore(context)
        store.savePosition(BERTPosition(1_000.0))
        store.showOnWidget = false
        assertFalse(BERTHoldingsStore(context).showOnWidget)
        assertEquals(false, context.getSharedPreferences("bert_holdings", Context.MODE_PRIVATE).all["show_on_widget"])
        // Editing the amount, or removing and entering it again, keeps the person's choice.
        store.savePosition(BERTPosition(2_000.0, 10.0))
        assertFalse(BERTHoldingsStore(context).showOnWidget)
        store.savePosition(BERTPosition())
        store.save(5.0)
        assertFalse(BERTHoldingsStore(context).showOnWidget)
        store.showOnWidget = true
        assertTrue(BERTHoldingsStore(context).showOnWidget)
        assertEquals(BERTPosition(5.0), BERTHoldingsStore(context).loadPosition())
    }
}
