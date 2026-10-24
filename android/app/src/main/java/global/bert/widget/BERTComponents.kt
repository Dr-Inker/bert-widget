package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's colours and type. BERT Violet follows the official BERT brand kit (v1.0, July 2026, "product mode"):
 * near-black purple surfaces, white text, #CECECE for muted text, lilac #EABFFF for links and labels, violet
 * #8531D5 only as a fill or border (it is too dark for text), and Outfit throughout. Berthalla Navy is the app's
 * original navy and orange, kept as the alternative. Green, red and amber stay functional status colours in both.
 */
internal enum class BERTScheme(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val background: Color,
    val panel: Color,
    val panelStrong: Color,
    val text: Color,
    val muted: Color,
    /** Filled buttons, badges and progress. */
    val action: Color,
    /** Text and icons on [action]. */
    val onAction: Color,
    /** Links, labels and accent text. */
    val accentText: Color,
    /** The border around a chosen tile. */
    val selected: Color,
    val brandType: Boolean,
) {
    BERT_VIOLET("bert-violet", "BERT Violet", "BRAND KIT",
        background = Color(0xFF100B16), panel = Color(0xFF1A1125), panelStrong = Color(0xFF302042),
        text = Color(0xFFFFFFFF), muted = Color(0xFFCECECE), action = Color(0xFF8531D5), onAction = Color(0xFFFFFFFF),
        accentText = Color(0xFFEABFFF), selected = Color(0xFFEABFFF), brandType = true),
    // One orange for actions, labels and links (owner preferred orange over the earlier salmon). It is light enough
    // for text on every navy surface (8.0 / 6.8 / 5.5:1, 4.6:1 inside a tinted status pill), which #F25836 is not.
    BERTHALLA_NAVY("berthalla-navy", "Berthalla Navy", "ORIGINAL LOOK",
        background = Color(0xFF071A2F), panel = Color(0xFF0D2942), panelStrong = Color(0xFF123957),
        text = Color(0xFFFFF5DF), muted = Color(0xFF9CB0C5), action = Color(0xFFFF9433), onAction = Color(0xFF071A2F),
        accentText = Color(0xFFFF9433), selected = Color(0xFFFF9433), brandType = false);

    companion object {
        val DEFAULT = BERT_VIOLET
        fun fromId(id: String?): BERTScheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The scheme in use. Read through the colour names below, so every screen follows a switch in Tools › Style. */
internal object BERTColors {
    var scheme by mutableStateOf(BERTScheme.DEFAULT)
}

internal val Navy: Color get() = BERTColors.scheme.background
internal val Panel: Color get() = BERTColors.scheme.panel
internal val PanelStrong: Color get() = BERTColors.scheme.panelStrong
internal val Cream: Color get() = BERTColors.scheme.text
internal val Muted: Color get() = BERTColors.scheme.muted
internal val Orange: Color get() = BERTColors.scheme.action
internal val OnAction: Color get() = BERTColors.scheme.onAction
internal val AccentText: Color get() = BERTColors.scheme.accentText
internal val Selected: Color get() = BERTColors.scheme.selected
internal val Green = Color(0xFF45E09A)
internal val Red = Color(0xFFFF6B7A)
internal val Amber = Color(0xFFFFC857)

/**
 * Outfit, the brand kit's only typeface (SIL Open Font License 1.1). Static instances cut from the variable font at the
 * weights the app uses, so no device can fall back to the variable file's default (Thin) instance.
 */
internal val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * A filled action button's colours: orange with navy text in Berthalla Navy, solid violet with white text in BERT
 * Violet. Material's own primary stays the readable accent, because text buttons draw their text in it.
 */
@Composable
internal fun actionButtonColors() = if (BERTColors.scheme.brandType)
    ButtonDefaults.buttonColors(containerColor = Orange.copy(alpha = 0.2f), contentColor = Color.White)
else solidActionButtonColors()

/**
 * The brand kit's control (as on bert.global): a 1px white border around the 20% violet fill, dimmed when the button
 * is disabled. Berthalla Navy's filled buttons have no border.
 */
@Composable
internal fun actionButtonBorder(enabled: Boolean = true): BorderStroke? =
    if (BERTColors.scheme.brandType) BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 1f else 0.3f)) else null

/** Solid violet (orange in Berthalla Navy): only the single highest-intent action on a screen, per the kit. */
@Composable
internal fun solidActionButtonColors() = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = OnAction)

@Composable
internal fun LoadingPanel() {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = AccentText, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Column { Text("Fetching a BERT quote…", color = Cream, fontWeight = FontWeight.Bold); Text("Fetching a validated BERT quote", color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
internal fun UnavailablePanel(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusPill("UPDATE DELAYED", Amber)
            Text(message, color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Check your connection and try again. No market value has been guessed or substituted.", color = Muted, lineHeight = 20.sp)
            Button(onClick = onRetry, colors = actionButtonColors(), border = actionButtonBorder()) { Text("Try again") }
        }
    }
}

@Composable
internal fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(99.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = AccentText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
internal fun BERTTheme(content: @Composable () -> Unit) {
    val scheme = BERTColors.scheme
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AccentText, onPrimary = Navy, background = Navy, surface = Panel,
        onBackground = Cream, onSurface = Cream, secondary = Green, error = Red,
        primaryContainer = PanelStrong, onPrimaryContainer = Cream,
        secondaryContainer = PanelStrong, onSecondaryContainer = Cream,
        surfaceVariant = PanelStrong, onSurfaceVariant = Muted, outline = Muted,
        surfaceContainer = Panel, surfaceContainerHigh = PanelStrong,
        surfaceContainerHighest = PanelStrong, surfaceContainerLow = Navy,
    ), typography = if (scheme.brandType) Typography().withFamily(Outfit) else Typography(), content = content)
}
