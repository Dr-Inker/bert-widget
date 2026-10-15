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
    fun unknownOrMissingThemeUsesTheBrandKitsViolet() {
        assertEquals(BERTThemePack.BERT_VIOLET, BERTThemePack.fromId(null))
        assertEquals(BERTThemePack.BERT_VIOLET, BERTThemePack.fromId("future-theme"))
        assertEquals(BERTThemePack.BERT_VIOLET, BERTThemePack.DEFAULT)
        assertEquals(0xFF100B16L, BERTThemePack.DEFAULT.backgroundArgb)
        assertEquals(0xFF8531D5L, BERTThemePack.DEFAULT.accentArgb)
        // Someone who chose the old navy keeps it.
        assertEquals(BERTThemePack.BERT_NAVY, BERTThemePack.fromId("bert-navy"))
    }
}
