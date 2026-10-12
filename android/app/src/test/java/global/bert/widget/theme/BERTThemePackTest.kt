package global.bert.widget.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class BERTThemePackTest {
    @Test
    fun knownIdLoadsRequestedTheme() {
        assertEquals(BERTThemePack.MAYOR_PURPLE, BERTThemePack.fromId("mayor-purple"))
        assertEquals(BERTThemePack.WOOFHUB_NIGHT, BERTThemePack.fromId("woofhub-night"))
        assertEquals(BERTThemePack.BERTHALLA_NIGHTS, BERTThemePack.fromId("berthalla-nights"))
    }

    @Test
    fun unknownOrMissingThemeUsesTheAppsOwnNavyAndOrange() {
        assertEquals(BERTThemePack.BERT_NAVY, BERTThemePack.fromId(null))
        assertEquals(BERTThemePack.BERT_NAVY, BERTThemePack.fromId("future-theme"))
        assertEquals(BERTThemePack.BERT_NAVY, BERTThemePack.DEFAULT)
        assertEquals(0xFF071A2FL, BERTThemePack.DEFAULT.backgroundArgb)
        assertEquals(0xFFFF9433L, BERTThemePack.DEFAULT.accentArgb)
    }
}
