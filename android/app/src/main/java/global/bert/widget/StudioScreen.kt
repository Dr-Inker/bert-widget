package global.bert.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.theme.BERTThemePack
import global.bert.widget.theme.BERTThemeStore
import global.bert.widget.theme.BERTWallpaperInstaller
import global.bert.widget.widget.BERTMarketWidgetReceiver
import global.bert.widget.widget.BERTWidgetReceiver
import global.bert.widget.widget.updateAllBERTWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ThemeStudio() {
    val context = LocalContext.current
    val store = remember { BERTThemeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(store.load()) }
    var applying by remember { mutableStateOf(false) }
    var wallpaperMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf(selected.id) }
    val preview = BERTThemePack.fromId(previewId)

    fun choose(theme: BERTThemePack) {
        selected = theme
        store.save(theme)
        scope.launch {
            try { updateAllBERTWidgets(context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, "Palette saved. Widgets will update on their next refresh.", Toast.LENGTH_LONG).show() }
        }
    }

    fun applyWallpaper(destination: Int, success: String) {
        val themeToApply = preview
        scope.launch {
            applying = true
            wallpaperMessage = null
            try {
                wallpaperMessage = withContext(Dispatchers.IO) {
                    if (destination == BERTWallpaperInstaller.BOTH) BERTWallpaperInstaller.applyPair(context, themeToApply).message
                    else { BERTWallpaperInstaller.apply(context, themeToApply, destination); success }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                wallpaperMessage = when (destination) {
                    BERTWallpaperInstaller.HOME -> "Home wallpaper couldn’t be changed. Try again or choose another pack."
                    BERTWallpaperInstaller.LOCK -> "Lock screen wallpaper couldn’t be changed. Try again or choose another pack."
                    else -> "Wallpaper changes couldn’t be confirmed. Check Home and Lock screens before trying again."
                }
            } finally {
                applying = false
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF120B1B)),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color(0xFF8B37F7).copy(alpha = 0.34f)),
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("PHONE THEMES", color = Color(0xFFB96DFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Make your phone unmistakably BERT.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Each pack includes a clock-safe Lock Screen and a quieter Home Screen. Preview a pack, then choose a wallpaper or widget palette.", color = Color(0xFFCAB5D6), fontSize = 12.sp, lineHeight = 17.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BERTThemePack.entries.forEach { theme ->
                    ThemePackCard(theme = theme, selected = preview == theme, onClick = { if (!applying) previewId = theme.id })
                }
            }
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Previewing ${preview.displayName}", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = { choose(preview) },
                    enabled = selected != preview && !applying,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (selected == preview) "Widget palette active" else "Use palette on widgets", color = Cream) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { applyWallpaper(BERTWallpaperInstaller.BOTH, "${preview.displayName} wallpapers applied") },
                        enabled = !applying,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(preview.accentArgb), contentColor = if (preview == BERTThemePack.WOOFHUB_NIGHT) Color.White else Navy),
                    ) { Text(if (applying) "Applying…" else "Apply both", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { applyWallpaper(BERTWallpaperInstaller.HOME, "Home screen updated") },
                        enabled = !applying,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                    ) { Text("Home only", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(
                    onClick = { applyWallpaper(BERTWallpaperInstaller.LOCK, "Lock screen updated") },
                    enabled = !applying,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) { Text("Lock screen only", color = Color(0xFFCAB5D6)) }
                wallpaperMessage?.let {
                    Text(it, color = Cream, fontSize = 14.sp, lineHeight = 21.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Text(
                    "Android may crop artwork slightly to fit your display. Wallpaper changes stay on your device.",
                    color = Color(0xFF8E7C99),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ThemePackCard(theme: BERTThemePack, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(156.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier.width(156.dp).height(300.dp).then(if (selected) Modifier.shadow(12.dp, RoundedCornerShape(22.dp)) else Modifier),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color(theme.accentArgb) else Color.White.copy(alpha = 0.12f)),
        ) {
            Image(
                painter = painterResource(theme.previewRes),
                contentDescription = "${theme.displayName} wallpaper preview",
                modifier = Modifier.fillMaxSize().background(Color(theme.backgroundArgb)),
                contentScale = ContentScale.Crop,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selected) Text("✓", color = Color(theme.accentArgb), fontWeight = FontWeight.ExtraBold)
            Text(theme.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(theme.subtitle, color = Color(0xFF9E8CAA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
internal fun WidgetSetup(context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("HOME-SCREEN WIDGETS")
            Text("Choose the quick price view or the full market desk.", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WidgetButton("Compact", "2×2", Modifier.weight(1f)) { pinWidget(context, BERTWidgetReceiver::class.java) }
                WidgetButton("Market", "4×2", Modifier.weight(1f)) { pinWidget(context, BERTMarketWidgetReceiver::class.java) }
            }
            Text("If your launcher does not support one-tap placement, long-press the home screen and choose Widgets → BERT.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun WidgetButton(name: String, size: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = Cream, fontWeight = FontWeight.Bold)
            Text(size, color = Muted, fontSize = 12.sp)
        }
    }
}

private fun pinWidget(context: Context, receiver: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    } else {
        Toast.makeText(context, "Open your launcher’s widget picker to add BERT", Toast.LENGTH_LONG).show()
    }
}
