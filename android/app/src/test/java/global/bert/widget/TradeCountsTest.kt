package global.bert.widget

import android.app.Application
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "· 25 sells" must never open a line (0.9.0 critique): the dot is glued to the figure before it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TradeCountsTest {
    @Test fun theSeparatorHangsOffTheFirstFigureAndTheCountsStayWhole() {
        val text = tradeCounts("Last hour:", 64, 25)
        assertEquals("Last hour: 64 buys · 25 sells", text)
        assertEquals("1 buy · 1 sell", tradeCounts("", 1, 1).trim())
        assertEquals("Past 24h: 1,204 buys · 987 sells", tradeCounts("Past 24h:", 1_204, 987))
    }

    @Test fun noWrappedLineStartsWithTheDotOrSplitsACountAtAnyWidthOrTextSize() {
        val failures = mutableListOf<String>()
        for (label in listOf("Last hour:", "Past 24h:")) for ((buys, sells) in listOf(64 to 25, 1 to 1, 1_204 to 987, 12_345 to 99_999)) {
            val text = tradeCounts(label, buys, sells)
            for (sp in listOf(13f, 15f, 20f, 30f)) for (widthPx in 120..900 step 7) {
                val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = sp * 2f; typeface = Typeface.DEFAULT_BOLD }
                // Below the width of the longest glued chunk ("12,345 buys ·") any text must break inside a word: not a real layout.
                if (paint.measureText("12,345\u00A0buys\u00A0·") > widthPx) continue
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx).setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE).build()
                for (line in 0 until layout.lineCount) {
                    val shown = text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trim()
                    if (shown.startsWith("·")) failures += "'$text' at ${sp}sp/${widthPx}px: line $line starts with a dot: '$shown'"
                    // A figure is never left at the end of a line apart from its noun (unless the glued chunk is wider than the line).
                    if (line < layout.lineCount - 1 && Regex("""(^|\s)\d[\d,]*$""").containsMatchIn(shown) &&
                        paint.measureText(text.substring(layout.getLineStart(line + 1)).substringBefore(' ')) <= widthPx) failures += "'$text' at ${sp}sp/${widthPx}px: figure parted from its noun on '$shown'"
                }
            }
        }
        assertTrue(failures.take(5).toString(), failures.isEmpty())
    }

    @Test fun theOldFormWasBreakable() {
        // Documents the bug: with ordinary spaces a break can fall before the dot.
        val old = "Last hour: 64 buys · 25 sells"
        val paint = TextPaint().apply { textSize = 30f; typeface = Typeface.DEFAULT_BOLD }
        val starts = (100..700 step 5).any { w ->
            val l = StaticLayout.Builder.obtain(old, 0, old.length, paint, w).build()
            (0 until l.lineCount).any { old.substring(l.getLineStart(it), l.getLineEnd(it)).trim().startsWith("·") }
        }
        assertTrue("the plain-space form should be able to start a line with a dot", starts)
        assertFalse(tradeCounts("Last hour:", 64, 25).contains(" · "))
    }
}
