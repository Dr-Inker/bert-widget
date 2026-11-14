package global.bert.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import global.bert.widget.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 0.15 slate and paper, by eye: Home with the slate and the note in each light and state, at large text, on a narrow
 * and a wide phone, in both colour schemes. Review fixtures only: build.gradle.kts keeps them out of the gate, -Pshots
 * runs them. PNGs in app/build/outputs/host-ui/slate/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlateAndPaperShotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val note = "woofmornin. the ladybug holds the screen door like the wind can wait its turn."

    private fun home(name: String, hour: Int = 9, fontScale: Float = 1f, quote: (Long) -> QuoteState = { fresh(it) },
                     activity: (Long) -> ActivityState = { ActivityState.Available(BERTActivity(it - 60_000, note, "content", null)) },
                     scheme: BERTScheme = BERTScheme.DEFAULT, scrollToNote: Boolean = false, freshNotes: Int = 0,
                     press: String? = null, myBert: Boolean = false, tap: String? = null, tapAfterMs: Long = 270, streak: Int = 0) {
        val now = ZonedDateTime.of(2026, 9, 9, hour, 9, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = LocalDate.of(2026, 9, 9)
        val visits = BERTHomeVisits(compose.activity)
        visits.meetBertDismissed = true
        PlaceDiscovery(compose.activity).apply {
            for (d in FIRST_DAYS downTo 1) record(today.minusDays(d.toLong())); patted()
            // A run of [streak] days ending today: the days before it counted here, today by opening Home.
            for (d in streak - 1 downTo 1) visit(today.minusDays(d.toLong()))
        }
        if (freshNotes > 0) {
            visits.recordOpen(today.minusDays(1)); visits.visit(now - 5 * 3_600_000L, today.minusDays(1))
            val journal = BERTJournal(compose.activity)
            for (i in 1..freshNotes) journal.record(BERTActivity(now, "an earlier note, number $i.", "content", null), now = now - (4 - i) * 3_600_000L)
        }
        if (myBert) MyBertStore(compose.activity).saveChosen(MyBert("Eemxg9w6qCiAR5V9Hbqhms9p6i1snBcaTs46WGQSL6Gc", 2206, "Forest", staked = true),
            android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/mybert/bert-2206.png")))
        BERTColors.scheme = scheme
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    BERTTheme { BERTApp(quote(now), activity(now), emptyList(), BERTPosition(), now, false, false, {}, {}, {}) }
                }
            }
            compose.waitForIdle()
            if (scrollToNote) compose.onNodeWithTag("board-row").performScrollTo()
            if (press != null) {
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag(press).performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(200)
            }
            if (tap != null) {
                // A tap in the garden, caught a moment later: a pat's heart on its way up, or Bert's word about a thing.
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag(tap).performTouchInput { click(if (tap == "place-gate") androidx.compose.ui.geometry.Offset(8f, height / 2f) else center) }
                compose.mainClock.advanceTimeBy(tapAfterMs)
            }
            capture(name)
            if (tap != null) compose.mainClock.autoAdvance = true
            if (press != null) { compose.onNodeWithTag(press).performTouchInput { cancel() }; compose.mainClock.autoAdvance = true }
        } finally { BERTColors.scheme = BERTScheme.DEFAULT }
    }

    private fun fresh(now: Long, change: Double = 1.63, observed: Long = now) = QuoteState.Available(BERTQuote(0.0154, change, null, null, null, "fresh",
        observed, "dexscreener", "raydium", "https://dexscreener.com/solana/BmsZE6TkZYskyS1PatPKRyyazGdxWFxdia4BuvLg9AgY"))

    @Test fun day() = home("home-slate-day")
    // The streak on the garden's chalkboard: 1, 12 and 365 days (and 999 by day), by day, at dusk and at night, at 320 and 412dp.
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Day1() = home("home-chalk-streak-320-day-1", hour = 9, streak = 1)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Day12() = home("home-chalk-streak-320-day-12", hour = 9, streak = 12)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Day365() = home("home-chalk-streak-320-day-365", hour = 9, streak = 365)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Dusk1() = home("home-chalk-streak-320-dusk-1", hour = 19, streak = 1)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Dusk12() = home("home-chalk-streak-320-dusk-12", hour = 19, streak = 12)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Dusk365() = home("home-chalk-streak-320-dusk-365", hour = 19, streak = 365)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Night1() = home("home-chalk-streak-320-night-1", hour = 23, streak = 1)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Night12() = home("home-chalk-streak-320-night-12", hour = 23, streak = 12)
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun chalk320Night365() = home("home-chalk-streak-320-night-365", hour = 23, streak = 365)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Day1() = home("home-chalk-streak-412-day-1", hour = 9, streak = 1)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Day12() = home("home-chalk-streak-412-day-12", hour = 9, streak = 12)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Day365() = home("home-chalk-streak-412-day-365", hour = 9, streak = 365)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Dusk1() = home("home-chalk-streak-412-dusk-1", hour = 19, streak = 1)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Dusk12() = home("home-chalk-streak-412-dusk-12", hour = 19, streak = 12)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Dusk365() = home("home-chalk-streak-412-dusk-365", hour = 19, streak = 365)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Night1() = home("home-chalk-streak-412-night-1", hour = 23, streak = 1)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Night12() = home("home-chalk-streak-412-night-12", hour = 23, streak = 12)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Night365() = home("home-chalk-streak-412-night-365", hour = 23, streak = 365)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun chalk412Day999() = home("home-chalk-streak-412-day-999", streak = 999)
    @Test fun patHeart() = home("home-slate-pat-heart", hour = 16, tap = "place-bert")
    @Test fun patHeartNap() = home("home-slate-pat-heart-nap", hour = 13, tap = "place-bert")
    @Test fun patHeartDusk() = home("home-slate-pat-heart-dusk", hour = 19, tap = "place-bert")
    @Test fun remarkGate() = home("home-slate-remark-gate", tap = "place-gate", tapAfterMs = 400)
    @Test fun golden() = home("home-slate-golden", hour = 16)
    @Test fun dusk() = home("home-slate-dusk", hour = 19)
    @Test fun night() = home("home-slate-night", hour = 23)
    @Test fun falling() = home("home-slate-falling", quote = { fresh(it, change = -7.92) })
    @Test fun nightFalling() = home("home-slate-night-falling", hour = 23, quote = { fresh(it, change = -7.92) })
    @Test fun delayed() = home("home-slate-delayed", quote = { fresh(it, observed = it - 42 * 60_000) })
    @Test fun priceUnavailable() = home("home-slate-price-unavailable", quote = { QuoteState.Unavailable("No connection") })
    @Test fun priceLoading() = home("home-slate-price-loading", quote = { QuoteState.Loading })
    @Test fun noNote() = home("home-slate-no-note", activity = { ActivityState.Empty })
    @Test fun offline() = home("home-slate-offline", activity = { ActivityState.Unavailable })
    @Test fun offlineNoPrice() = home("home-slate-offline-no-price", activity = { ActivityState.Unavailable }, quote = { QuoteState.Unavailable("No connection") })
    @Test fun freshNotes() = home("home-slate-fresh-notes", freshNotes = 2)
    @Test fun pressedSlate() = home("home-slate-pressed-slate", press = "home-price")
    @Test fun pressedNote() = home("home-slate-pressed-note", press = "board-row")
    @Test fun withMyBert() = home("home-slate-mybert", myBert = true)
    @Test fun navy() = home("home-slate-navy", scheme = BERTScheme.BERTHALLA_NAVY)
    @Test fun navyNight() = home("home-slate-navy-night", hour = 23, scheme = BERTScheme.BERTHALLA_NAVY)
    @Test fun font13() = home("home-slate-font1.3", fontScale = 1.3f)
    @Test fun font13Note() = home("home-slate-font1.3-note", fontScale = 1.3f, scrollToNote = true)
    @Test fun font20() = home("home-slate-font2.0", fontScale = 2f)
    @Test fun font20Note() = home("home-slate-font2.0-note", fontScale = 2f, scrollToNote = true)
    @Test fun font20Unavailable() = home("home-slate-font2.0-unavailable", fontScale = 2f, quote = { QuoteState.Unavailable("No connection") },
        activity = { ActivityState.Unavailable })
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun narrow320() = home("home-slate-320")
    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi") fun narrow320Font20() = home("home-slate-320-font2.0", fontScale = 2f, scrollToNote = true)
    @Test @Config(qualifiers = "w412dp-h915dp-420dpi") fun wide412() = home("home-slate-412")
    @Test @Config(qualifiers = "w430dp-h932dp-xxhdpi") fun wide430() = home("home-slate-430")
    @Test @Config(qualifiers = "w430dp-h932dp-xxhdpi") fun wide430Night() = home("home-slate-430-night", hour = 23)

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        File("build/outputs/host-ui/slate/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
