package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Navy = Color(0xFF071A2F)
internal val Panel = Color(0xFF0D2942)
internal val PanelStrong = Color(0xFF123957)
internal val Cream = Color(0xFFFFF5DF)
internal val Muted = Color(0xFF9CB0C5)
// One orange for actions, labels and links (owner preferred orange over the earlier salmon). It is light enough
// for text on every navy surface (8.0 / 6.8 / 5.5:1, 4.6:1 inside a tinted status pill), which #F25836 is not.
internal val Orange = Color(0xFFFF9433)
internal val AccentText = Orange
internal val Green = Color(0xFF45E09A)
internal val Red = Color(0xFFFF6B7A)
internal val Amber = Color(0xFFFFC857)

@Composable
internal fun LoadingPanel() {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Orange, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("Try again") }
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
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AccentText, onPrimary = Navy, background = Navy, surface = Panel,
        onBackground = Cream, onSurface = Cream, secondary = Green, error = Red,
        primaryContainer = PanelStrong, onPrimaryContainer = Cream,
        secondaryContainer = PanelStrong, onSecondaryContainer = Cream,
        surfaceVariant = PanelStrong, onSurfaceVariant = Muted, outline = Muted,
        surfaceContainer = Panel, surfaceContainerHigh = PanelStrong,
        surfaceContainerHighest = PanelStrong, surfaceContainerLow = Navy,
    ), content = content)
}
