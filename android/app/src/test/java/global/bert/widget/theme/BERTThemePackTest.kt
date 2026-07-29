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
    fun unknownOrMissingThemeUsesWoofhubNight() {
        assertEquals(BERTThemePack.WOOFHUB_NIGHT, BERTThemePack.fromId(null))
        assertEquals(BERTThemePack.WOOFHUB_NIGHT, BERTThemePack.fromId("future-theme"))
    }
}
