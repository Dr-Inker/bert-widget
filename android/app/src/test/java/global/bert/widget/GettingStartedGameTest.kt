package global.bert.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import global.bert.widget.data.LostTrailProgress
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Play a world of The Lost Trail" ticks once a run has started, not when the game is merely opened. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GettingStartedGameTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() {
        listOf("bert_lost_trail", "bert_onboarding").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun gameSave(progress: String?, run: String?) {
        val storage = JSONObject().put("p", progress ?: JSONObject.NULL).put("r", run ?: JSONObject.NULL).toString()
        LostTrailProgress.store(context, JSONObject.quote(storage)) // as evaluateJavascript returns it
    }

    @Test fun openingTheGameAloneDoesNotTickTheStep() {
        assertFalse(BERTOnboarding.progress(context).game)
        gameSave(null, null) // the game opened and saved nothing yet
        assertFalse(BERTOnboarding.progress(context).game)
    }

    @Test fun aRunInProgressOrAClearedWorldTicksIt() {
        gameSave(null, """{"version":1,"world":0,"checkpoint":1}""")
        assertTrue(BERTOnboarding.progress(context).game)
        gameSave("""{"version":1,"worlds":[{"time":91000,"bones":3,"coins":20,"deaths":1}]}""", null)
        assertTrue(BERTOnboarding.progress(context).game)
    }

    @Test fun theFlagFromEarlierVersionsStillCounts() {
        context.getSharedPreferences("bert_onboarding", Context.MODE_PRIVATE).edit().putBoolean("played_lost_trail", true).commit()
        assertTrue(BERTOnboarding.progress(context).game)
    }
}
