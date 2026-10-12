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

/**
 * A caption-sized excerpt of one of Bert's updates: whole sentences while they fit the card's limit, otherwise the
 * first sentence cut at a word boundary with an ellipsis. Updates run to 800 characters; captions hold 96.
 */
internal fun captionExcerpt(dispatch: String, limit: Int = CAPTION_CHARACTER_LIMIT): String {
    val text = normalizeCaption(dispatch).replace(Regex(" {2,}"), " ").trim()
    if (captionCharacterCount(text) <= limit) return text
    val sentences = Regex("[^.!?…]+[.!?…]+[\"”’)]*\\s*|[^.!?…]+$").findAll(text).map { it.value }.toList()
    var excerpt = ""
    for (sentence in sentences) {
        val next = excerpt + sentence
        if (captionCharacterCount(next.trim()) > limit) break
        excerpt = next
    }
    if (excerpt.isNotBlank()) return excerpt.trim()
    // One long sentence: cut on a grapheme boundary, back to the last space, and mark the cut.
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    var end = 0
    repeat(limit - 1) { boundaries.next().takeIf { it != BreakIterator.DONE }?.let { end = it } }
    val cut = text.substring(0, end)
    return cut.substring(0, cut.lastIndexOf(' ').takeIf { it > limit / 2 } ?: cut.length).trimEnd(' ', ',', ';', ':') + "…"
}
