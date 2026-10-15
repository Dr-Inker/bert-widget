package global.bert.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import global.bert.widget.theme.BERTThemePack
import global.bert.widget.widget.Red as WidgetRed
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Explicit text/surface bindings, checked against independent Android contrast thresholds.
 * Does not cover raster artwork, disabled controls, or a physical display's appearance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BERTContrastTest {
    @Test fun appAndWidgetTextContrast() {
        val measurements = JSONArray()
        val failures = mutableListOf<String>()
        fun check(label: String, ink: Color, background: Color, minimum: Double = 4.5) {
            val ratio = ColorUtils.calculateContrast(ink.toArgb(), background.toArgb())
            measurements.put(JSONObject().put("pair", label).put("foregroundArgb", "%08X".format(ink.toArgb()))
                .put("backgroundArgb", "%08X".format(background.toArgb())).put("ratio", ratio).put("minimum", minimum))
            if (ratio < minimum) failures += "$label: $ratio < $minimum"
        }
        // Both app schemes: BERT Violet (brand kit, the default) and Berthalla Navy.
        for (scheme in BERTScheme.entries) try {
            BERTColors.scheme = scheme
            val id = scheme.id
            for ((surface, background) in listOf("canvas" to Navy, "card" to Panel, "strong card" to PanelStrong)) {
                // BERTComponents, Home, Market, Holdings and section/navigation text.
                for ((name, ink) in listOf("body" to Cream, "secondary" to Muted, "accent" to AccentText, "warning" to Amber)) {
                    check("app $id $surface / $name", ink, background)
                }
            }
            check("app $id market negative change, 17sp bold", Red, Panel)
            check("app $id holdings loss, 18sp bold", Red, PanelStrong, minimum = 3.0)
            check("app $id action button text", OnAction, Orange)
            if (scheme.brandType) for ((surface, background) in listOf("canvas" to Navy, "card" to Panel, "strong card" to PanelStrong)) {
                // The kit's glass button: white text over violet at 20%, on whatever surface holds it.
                check("app $id glass button on $surface", Color.White, Orange.copy(alpha = 0.2f).compositeOver(background))
            }
            // Text buttons and Material's primary draw in the accent, so it must read as text on every surface.
            check("app $id text button", AccentText, Navy)
            // A chosen tile's border and a spinner are non-text: 3:1 against the surfaces they sit on.
            check("app $id selected border", Selected, Panel, minimum = 3.0)
            check("app $id spinner on strong card", AccentText, PanelStrong, minimum = 3.0)
            for ((name, ink, parent) in listOf(Triple("mood", AccentText, PanelStrong), Triple("event open", Green, Panel),
                Triple("event unconfirmed or ended", Muted, Panel), Triple("quote delayed", Amber, Panel))) {
                check("app $id status $name", ink, ink.copy(alpha = 0.12f).compositeOver(parent))
            }
        } finally {
            BERTColors.scheme = BERTScheme.DEFAULT
        }
        // WidgetPalette uses these theme colors directly; negative 24h change is 12sp bold.
        for (theme in BERTThemePack.entries) {
            val background = Color(theme.backgroundArgb)
            val panel = Color(theme.panelArgb)
            check("widget ${theme.id} / negative change", WidgetRed, background)
            check("widget ${theme.id} / body", Color(theme.textArgb), background)
            check("widget ${theme.id} / secondary", Color(theme.mutedArgb), background)
            check("widget ${theme.id} / panel body", Color(theme.textArgb), panel)
            check("widget ${theme.id} / panel secondary", Color(theme.mutedArgb), panel)
            // The Bert widget's mood pill (10sp bold) on its own fill.
            check("widget ${theme.id} / mood pill", Color(theme.onHighlightArgb), Color(theme.highlightArgb))
        }
        val target = File("build/outputs/host-ui/contrast-measurements.json")
        requireNotNull(target.parentFile).mkdirs()
        target.writeText(measurements.toString(2))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
