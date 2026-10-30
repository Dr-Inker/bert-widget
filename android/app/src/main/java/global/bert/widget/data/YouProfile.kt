package global.bert.widget.data

import android.content.Context
import android.icu.text.BreakIterator

/**
 * What the You page keeps about the person: a name for Bert to call them by, nothing else. It lives in this phone's private app
 * storage (excluded from backup like everything else), is never sent anywhere, and [forget] removes it.
 */
class YouProfile(context: Context) {
    private val preferences = context.getSharedPreferences("bert_you", Context.MODE_PRIVATE)

    /** The saved name, tidied again on the way out so a damaged or older value can never reach the screen as-is. */
    fun name(): String = cleanName(preferences.getString(KEY_NAME, null).orEmpty())

    /** Saves [raw] as typed: tidied first, and a blank name removes it. */
    fun setName(raw: String) {
        val name = cleanName(raw)
        preferences.edit().apply { if (name.isEmpty()) remove(KEY_NAME) else putString(KEY_NAME, name) }.apply()
    }

    fun forget() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val MAX_NAME = 24
        private const val KEY_NAME = "name"

        /** One line, no control characters, runs of spaces collapsed, trimmed at both ends: the name before the cap. */
        private fun tidy(raw: String): String = raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim()

        /**
         * The name as saved: tidied, then the longest start of it that holds at most [MAX_NAME] code points and ends between
         * two whole characters as a person sees them (see [characterEnds]), so a flag, an emoji family, a skin tone or a
         * keycap is kept whole or left out, never cut in half; trimmed again in case the cut left a space at the end.
         */
        fun cleanName(raw: String): String {
            val oneLine = tidy(raw)
            if (oneLine.codePointCount(0, oneLine.length) <= MAX_NAME) return oneLine
            val cap = oneLine.offsetByCodePoints(0, MAX_NAME)
            return oneLine.substring(0, characterEnds(oneLine).lastOrNull { it <= cap } ?: 0).trim()
        }

        /**
         * Every offset in [text] where a character as a person sees it ends (a grapheme cluster: a flag is two regional
         * indicators, a family is people joined by zero-width joiners, a thumbs-up can carry a skin tone, a keycap is a digit
         * and two marks), in order and ending with the text's length. ICU's, as Android's own text handling uses: the JVM's
         * java.text.BreakIterator only learnt emoji sequences in Java 20.
         */
        internal fun characterEnds(text: String): List<Int> {
            val characters = BreakIterator.getCharacterInstance().apply { setText(text) }
            return generateSequence(characters.next()) { characters.next() }.takeWhile { it != BreakIterator.DONE }.toList()
        }

        /** Whether [raw] is within the cap once tidied as saving tidies it, so it can stay in the field exactly as typed. */
        fun fits(raw: String): Boolean = tidy(raw).let { it.codePointCount(0, it.length) <= MAX_NAME }
    }
}
