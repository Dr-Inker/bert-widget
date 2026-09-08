package global.bert.widget

import android.icu.text.BreakIterator
import java.util.Locale

internal const val CAPTION_CHARACTER_LIMIT = 96
// Bound saved-state and metadata size even for text made of many combining marks.
internal const val MAX_CAPTION_DRAFT_UNITS = 4096
private val captionWhitespace = Regex("[\\r\\n\\t]+")

internal fun normalizeCaption(input: String): String =
    input.replace(captionWhitespace, " ").filterNot { it.isISOControl() }

internal fun captionCharacterCount(text: String): Int {
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
    boundaries.setText(text)
    var count = 0
    while (boundaries.next() != BreakIterator.DONE) count++
    return count
}

internal fun isValidSavedCaption(text: String): Boolean =
    text.length <= MAX_CAPTION_DRAFT_UNITS && text.isNotBlank() && captionCharacterCount(text) <= CAPTION_CHARACTER_LIMIT
