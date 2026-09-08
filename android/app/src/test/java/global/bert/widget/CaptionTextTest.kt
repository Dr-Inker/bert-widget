package global.bert.widget

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CaptionTextTest {
    @Test fun countsAccentsFlagsModifiersAndJoinedEmojiAsCharacters() {
        assertEquals(0, captionCharacterCount(""))
        assertEquals(4, captionCharacterCount("🐾e\u0301🇬🇧👨‍👩‍👧‍👦"))
        assertEquals(3, captionCharacterCount("👍🏽🐕‍🦺🏳️‍🌈"))
    }

    @Test fun normalizesLineBreaksAndControlsWithoutBreakingEmoji() {
        assertEquals("good boy 🐕‍🦺", normalizeCaption("good\r\n\tboy\u0000\u0007 🐕‍🦺"))
    }

    @Test fun permits96CharactersAndBoundsOversizedClusters() {
        assertTrue(isValidSavedCaption("🐾".repeat(96)))
        assertFalse(isValidSavedCaption("🐾".repeat(97)))
        assertTrue(isValidSavedCaption("a".repeat(96)))
        assertFalse(isValidSavedCaption("a".repeat(97)))
        assertFalse(isValidSavedCaption("   "))
        assertFalse(isValidSavedCaption("a" + "\u0301".repeat(4096)))
    }
}
