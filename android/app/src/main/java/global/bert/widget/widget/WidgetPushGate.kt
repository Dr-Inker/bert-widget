package global.bert.widget.widget

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When the open app hands its data to the placed widgets. While the app covers the home screen nobody sees them, so a
 * fetch only notes that their data changed ([changed]), and one push follows when the app leaves the screen (ON_STOP).
 * A person's own change (holdings, the widget switch) still pushes at once ([pushNow]), and so does a fetch that lands
 * while the app is not on screen: one still in flight when the person pressed Home has no later stop to wait for.
 *
 * Keyed on "a fetch was stored since the last push", never on the quote's observedAt: a direct fetch stamps that with
 * the phone's own clock, so it changes on every fetch.
 */
internal class WidgetPushGate(private val push: () -> Unit) : DefaultLifecycleObserver {
    private val pending = AtomicBoolean(false)
    /** Between ON_START and ON_STOP: the app covers the home screen. */
    @Volatile private var started = false

    /** A quote, Bert's feed or the price history was stored anew; the widgets get it when the app stops, or now if it has. */
    fun changed() {
        if (started) pending.set(true) else pushNow()
    }

    /** Push now; it carries every earlier change with it, so nothing is left for ON_STOP. */
    fun pushNow() {
        pending.set(false)
        push()
    }

    override fun onStart(owner: LifecycleOwner) {
        started = true
    }

    override fun onStop(owner: LifecycleOwner) {
        started = false
        if (pending.getAndSet(false)) push()
    }
}

/** Outlives the activity, so a push started as the app closes still finishes. */
private val widgetPushes = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Re-renders every placed widget in the background; a launcher failure is not the app's error to show. */
internal fun pushWidgets(context: Context) {
    widgetPushes.launch {
        try { updateAllBERTWidgets(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* widgets catch up on their next refresh */ }
    }
}
