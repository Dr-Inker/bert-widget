package global.bert.widget.widget

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Critique 2026-11-12 ENG-4: the open app re-rendered every placed widget once a minute. Now a fetch only notes that the
 * widgets' data changed; the push happens once, when the app leaves the screen (ON_STOP), and only if something changed.
 * A person's own action (holdings, the widget switch) still pushes straight away.
 */
class WidgetPushGateTest {
    private class Screen : LifecycleOwner {
        val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun fetchesWhileTheAppIsOpenPushOnceWhenItStops() {
        var pushes = 0
        val gate = WidgetPushGate { pushes++ }
        val screen = Screen().apply { registry.addObserver(gate); registry.currentState = Lifecycle.State.RESUMED }
        repeat(30) { gate.changed() } // half an hour of once-a-minute quotes
        assertEquals("no push while the app covers the home screen", 0, pushes)
        screen.registry.currentState = Lifecycle.State.CREATED // ON_PAUSE, ON_STOP
        assertEquals(1, pushes)
        // Back on screen and away again with nothing new: the widgets already show it.
        screen.registry.currentState = Lifecycle.State.RESUMED
        screen.registry.currentState = Lifecycle.State.CREATED
        assertEquals(1, pushes)
        screen.registry.currentState = Lifecycle.State.RESUMED
        gate.changed()
        screen.registry.currentState = Lifecycle.State.CREATED
        assertEquals(2, pushes)
    }

    @Test fun aPersonsOwnChangePushesNowAndCarriesEarlierFetchesWithIt() {
        var pushes = 0
        val gate = WidgetPushGate { pushes++ }
        val screen = Screen().apply { registry.addObserver(gate); registry.currentState = Lifecycle.State.RESUMED }
        gate.changed()
        gate.pushNow()
        assertEquals(1, pushes)
        screen.registry.currentState = Lifecycle.State.CREATED
        assertEquals("the push on save already carried the fetched quote", 1, pushes)
    }

    /**
     * Review of 0.14.2: a fetch still in flight when the person pressed Home lands after ON_STOP. Nobody is covering the
     * home screen any more, so it goes to the widgets at once, not at the next stop that may never come.
     */
    @Test fun aFetchThatLandsAfterTheAppStoppedPushesAtOnce() {
        var pushes = 0
        val gate = WidgetPushGate { pushes++ }
        val screen = Screen().apply { registry.addObserver(gate); registry.currentState = Lifecycle.State.RESUMED }
        screen.registry.currentState = Lifecycle.State.CREATED // Home pressed with a quote still on its way
        assertEquals("nothing had changed when it stopped", 0, pushes)
        gate.changed()
        assertEquals("the late quote reaches the widgets straight away", 1, pushes)
        // Back on screen, fetches wait for the next stop again.
        screen.registry.currentState = Lifecycle.State.RESUMED
        gate.changed()
        assertEquals(1, pushes)
        screen.registry.currentState = Lifecycle.State.CREATED
        assertEquals(2, pushes)
    }
}
