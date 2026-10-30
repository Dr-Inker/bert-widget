package global.bert.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import global.bert.widget.widget.BERTDailyWidgetReceiver
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
    val stackActions = LocalDensity.current.fontScale > 1.3f
    val store = remember { BERTThemeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(store.load()) }
    var applying by remember { mutableStateOf(false) }
    var wallpaperMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // "Match my Bert" sits beside the packs only while a Bert is chosen; it colours widgets and has no wallpaper.
    val chosen = LocalMyBert.current.chosen
    var matching by remember(chosen) { mutableStateOf(chosen != null && store.matchMyBert) }
    var previewId by rememberSaveable { mutableStateOf(if (matching) MATCH_MY_BERT else selected.id) }
    val previewingMatch = previewId == MATCH_MY_BERT && chosen != null
    val preview = BERTThemePack.fromId(previewId.takeIf { !previewingMatch } ?: selected.id)
    // Which screen the pack cards show: the Lock artwork by default, the Home companion one tap away.
    var previewScreen by rememberSaveable { mutableStateOf(PREVIEW_LOCK) }

    fun refreshWidgets() {
        scope.launch {
            try { updateAllBERTWidgets(context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(context, "Palette saved. Widgets will update on their next refresh.", Toast.LENGTH_LONG).show() }
        }
    }

    fun choose(theme: BERTThemePack) {
        selected = theme
        matching = false
        store.save(theme)
        refreshWidgets()
    }

    fun matchMyBert() {
        matching = true
        store.matchMyBert = true
        refreshWidgets()
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
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Orange.copy(alpha = 0.34f)),
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("PHONE THEMES", color = AccentText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Make your phone unmistakably BERT.", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Each pack includes a clock-safe Lock Screen and a quieter Home Screen. Preview a pack, then choose a wallpaper or widget palette.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            // Wrapping, not single-row: a single-row SectionTabs scrolls its chosen pill into view, which would pull the
            // Style page down to here every time it opens.
            Box(Modifier.padding(horizontal = 18.dp)) {
                SectionTabs(listOf(PREVIEW_LOCK, PREVIEW_HOME), previewScreen) { previewScreen = it }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (chosen != null) MatchMyBertCard(chosen, selected = previewingMatch, onClick = { if (!applying) previewId = MATCH_MY_BERT })
                BERTThemePack.entries.forEach { theme ->
                    ThemePackCard(theme = theme, home = previewScreen == PREVIEW_HOME, selected = !previewingMatch && preview == theme,
                        onClick = { if (!applying) previewId = theme.id })
                }
            }
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Previewing ${if (previewingMatch) "Match my Bert" else preview.displayName}", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val active = if (previewingMatch) matching else !matching && selected == preview
                OutlinedButton(
                    onClick = { if (previewingMatch) matchMyBert() else choose(preview) },
                    enabled = !active && !applying,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (active) "Widget palette active" else "Use palette on widgets", color = Cream) }
                if (previewingMatch && chosen != null) Text(
                    "Your widgets take their colours from ${chosen.bert.name}’s ${chosen.bert.background?.lowercase() ?: "picture"} background. " +
                        "Wallpapers come with the packs.",
                    color = Muted, fontSize = 13.sp, lineHeight = 18.sp,
                ) else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = if (stackActions) 1 else 2, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { applyWallpaper(BERTWallpaperInstaller.BOTH, "${preview.displayName} wallpapers applied") },
                        enabled = !applying,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(preview.accentArgb), // White on the violet accents (the kit: never dark text on violet); dark ink on the warm ones.
                        contentColor = if (preview == BERTThemePack.WOOFHUB_NIGHT || preview == BERTThemePack.BERT_VIOLET) Color.White else Color(0xFF071A2F)),
                    ) { Text(if (applying) "Applying…" else "Apply both", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { applyWallpaper(BERTWallpaperInstaller.HOME, "Home screen updated") },
                        enabled = !applying,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                    ) { Text("Home only", color = Cream, fontWeight = FontWeight.Bold) }
                }
                if (!previewingMatch) OutlinedButton(
                    onClick = { applyWallpaper(BERTWallpaperInstaller.LOCK, "Lock screen updated") },
                    enabled = !applying,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) { Text("Lock screen only", color = Muted) }
                wallpaperMessage?.let {
                    Text(it, color = Cream, fontSize = 14.sp, lineHeight = 21.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (!previewingMatch) Text(
                    "Android may crop artwork slightly to fit your display. Wallpaper changes stay on your device.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

private const val MATCH_MY_BERT = "match-my-bert"
private const val PREVIEW_LOCK = "Lock screen"
private const val PREVIEW_HOME = "Home screen"

/** "Match my Bert" as a pack-sized card: the holder's Bert on the colours the widgets will take. */
@Composable
private fun MatchMyBertCard(chosen: global.bert.widget.data.ChosenBert, selected: Boolean, onClick: () -> Unit) {
    val palette = chosen.palette
    val picture = rememberMyBertPicture(chosen)
    Column(
        modifier = Modifier.width(156.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton).testTag("theme-match-my-bert"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier.width(156.dp).height(300.dp).then(if (selected) Modifier.shadow(12.dp, RoundedCornerShape(22.dp)) else Modifier),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(palette.backgroundArgb)),
            border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color(palette.accentArgb) else Color.White.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color(palette.panelArgb))) {
                    picture?.let { Image(it, "${chosen.bert.name} on its matching colours", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Text(chosen.bert.name, color = Color(palette.textArgb), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("woofmornin. the mayor is in.", color = Color(palette.mutedArgb), fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(palette.accentArgb)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selected) Text("✓", color = Color(palette.accentArgb), fontWeight = FontWeight.ExtraBold)
            Text("Match my Bert", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("${chosen.bert.name.uppercase()} · ${(chosen.bert.background ?: "Picture").uppercase()}", color = Muted, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun ThemePackCard(theme: BERTThemePack, home: Boolean, selected: Boolean, onClick: () -> Unit) {
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
                painter = painterResource(if (home) theme.homePreviewRes else theme.previewRes),
                contentDescription = "${theme.displayName} ${if (home) "Home screen" else "Lock screen"} wallpaper preview",
                modifier = Modifier.fillMaxSize().background(Color(theme.backgroundArgb)),
                contentScale = ContentScale.Crop,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // The highlight, not the accent: BERT Violet's accent is a border colour, too dark for a tick.
            if (selected) Text("✓", color = Color(theme.highlightArgb), fontWeight = FontWeight.ExtraBold)
            Text(theme.displayName, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(theme.subtitle, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

/** What the picker offers for one widget: its own name, the preview image the launcher shows, and the receiver to pin. */
internal class WidgetChoice(
    val name: String, val detail: String, @DrawableRes val preview: Int, val receiver: Class<*>,
)

internal val WidgetChoices = listOf(
    WidgetChoice("Bert\u2019s garden", "2×2 · A window onto his garden at your time of day. No words, no numbers.", R.drawable.widget_preview_place, global.bert.widget.widget.BERTPlaceWidgetReceiver::class.java),
    WidgetChoice("Bert’s day", "2×2 or 4×2 · Bert’s mood and Berthalla’s latest update.", R.drawable.widget_preview_bert, BERTDailyWidgetReceiver::class.java),
    WidgetChoice("BERT price", "2×2 · The price and its 24-hour move.", R.drawable.widget_preview_compact, BERTWidgetReceiver::class.java),
    WidgetChoice("BERT market desk", "4×2 · Price, your position, market cap, volume and liquidity.", R.drawable.widget_preview_market, BERTMarketWidgetReceiver::class.java),
)

@Composable
internal fun WidgetSetup(context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionLabel("HOME-SCREEN WIDGETS")
            Text("Bert himself, the quick price view or the full market desk.", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            WidgetChoices.forEach { choice -> WidgetChoiceCard(choice) { pinWidget(context, choice.receiver) } }
            Text("If your launcher does not support one-tap placement, long-press the home screen and choose Widgets → BERT.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/** The picture is the widget itself (rendered by the same code the launcher runs), shown above its Add button. */
@Composable
private fun WidgetChoiceCard(choice: WidgetChoice, onAdd: () -> Unit) {
    val painter = painterResource(choice.preview)
    val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 2f }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(
            painter = painter, contentDescription = "Preview of the ${choice.name} widget", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(if (ratio < 1.3f) 0.5f else 1f).aspectRatio(ratio).clip(RoundedCornerShape(16.dp)),
        )
        Text(choice.name, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(choice.detail, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
            Text("Add ${choice.name}", color = Cream, fontWeight = FontWeight.Bold)
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

/**
 * The app's own colours: BERT Violet from the official brand kit (the default), or Berthalla Navy, the app's original
 * look. Widgets and wallpapers keep their own palette below.
 */
@Composable
internal fun AppColours() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("APP COLOURS")
            Text("BERT Violet follows the official brand kit. Berthalla Navy is the app’s original look.",
                color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            // Equal heights, whichever name wraps.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (scheme in BERTScheme.entries) {
                    SchemeTile(scheme, selected = BERTColors.scheme == scheme, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        BERTThemeStore(context.applicationContext).appSchemeId = scheme.id
                        BERTColors.scheme = scheme
                    }
                }
            }
        }
    }
}

@Composable
private fun SchemeTile(scheme: BERTScheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
        .border(if (selected) 2.dp else 1.dp, if (selected) Selected else Color.White.copy(alpha = 0.14f), shape)
        .background(scheme.background).padding(12.dp).testTag("scheme-${scheme.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A small picture of the scheme: a card, its text, a link and a button, in that scheme's own colours.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.text))
            Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.muted))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(44.dp).height(16.dp).clip(RoundedCornerShape(8.dp)).background(scheme.action))
                Box(Modifier.width(28.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.accentText))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selected) Text("✓", color = scheme.accentText, fontWeight = FontWeight.ExtraBold)
            Text(scheme.displayName, color = scheme.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                fontFamily = if (scheme.brandType) Outfit else null)
        }
        Text(scheme.subtitle, color = scheme.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}
