package global.bert.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CreateScreen(tab: String, selectTab: (String) -> Unit) {
    val context = LocalContext.current
    if (tab == "Art") {
        CaptionCardCreator { selectTab("Saved") }
        SectionLabel("KEEP CREATING")
        ExperienceLink("Draw Bert", "Describe a scene in Bert’s web Studio.", BERTSymbol.CREATE, BERTLink.DRAW)
    } else if (tab == "Personalize") {
        Text("A little BERT. Everywhere.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Widgets for a quick glance. Wallpapers for the rest of your day.", color = Muted, fontSize = 14.sp)
        WidgetSetup(context)
        ThemeStudio()
    }
}

@Composable
private fun CaptionCardCreator(openSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by rememberSaveable { mutableStateOf("woofmornin. the mayor is in.") }
    var palette by rememberSaveable { mutableIntStateOf(0) }
    var artIndex by rememberSaveable { mutableIntStateOf(0) }
    var layoutIndex by rememberSaveable { mutableIntStateOf(0) }
    var formatIndex by rememberSaveable { mutableIntStateOf(0) }
    val style = CardStyle(CardArt.entries[artIndex], CardLayout.entries[layoutIndex], CardFormat.entries[formatIndex])
    val library = remember { CaptionLibrary(context.applicationContext) }
    var sharing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var collectionFull by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val characterCount = remember(caption) { captionCharacterCount(caption) }
    val overLimit = characterCount > CAPTION_CHARACTER_LIMIT
    val text = caption.trim()
    val canRender = !overLimit && text.isNotEmpty()
    var saved by remember(text, palette, style) { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    val rendered by produceState<RenderedCaption?>(null, text, palette, style, canRender) {
        previewError = false
        value = null
        if (!canRender) return@produceState
        delay(200)
        try {
            value = withContext(Dispatchers.Default) { renderCaption(context.applicationContext, text, palette, style) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            previewError = true
        }
    }
    val ready = rendered?.takeIf { canRender && it.caption == text && it.palette == palette && it.style == style }
    // Story cards keep their true 9:16 shape at a narrower width so the preview stays on screen.
    val previewModifier = Modifier.fillMaxWidth(if (style.format == CardFormat.STORY) 0.62f else 1f)
        .aspectRatio(style.format.width.toFloat() / style.format.height).clip(RoundedCornerShape(20.dp))

    Text("Your words. Bert’s face.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Make a card for the pack. No account needed.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    if (ready != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Image(ready.bitmap.asImageBitmap(), "Caption card preview: $text", previewModifier)
        }
    } else {
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { Surface(color = Panel, modifier = previewModifier) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                val message = when {
                    overLimit -> "Shorten your caption to see the preview."
                    text.isEmpty() -> "Your caption will appear here."
                    previewError -> "Preview unavailable. Edit the caption to try again."
                    else -> "Updating preview…"
                }
                Text(message, color = Muted, modifier = Modifier.padding(20.dp))
            }
        } }
    }
    OutlinedTextField(
        value = caption,
        onValueChange = {
            if (it.length > MAX_CAPTION_DRAFT_UNITS) {
                inputNotice = "Paste a shorter passage. Your caption is unchanged."
            } else {
                caption = normalizeCaption(it)
                inputNotice = null
                error = null
            }
        },
        label = { Text("Your caption") },
        isError = overLimit || inputNotice != null,
        supportingText = {
            val remaining = characterCount - CAPTION_CHARACTER_LIMIT
            Text(inputNotice ?: if (overLimit) "$characterCount/$CAPTION_CHARACTER_LIMIT · Remove $remaining ${if (remaining == 1) "character" else "characters"} to preview, save or share."
                else "$characterCount/$CAPTION_CHARACTER_LIMIT",
                modifier = Modifier.semantics { if (overLimit || inputNotice != null) liveRegion = LiveRegionMode.Polite })
        },
        minLines = 2, maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionLabel("BERT")
    SectionTabs(CardArt.entries.map { it.label }, style.art.label) { label -> artIndex = CardArt.entries.indexOfFirst { it.label == label } }
    SectionLabel("LAYOUT")
    SectionTabs(CardLayout.entries.map { it.label }, style.layout.label) { label -> layoutIndex = CardLayout.entries.indexOfFirst { it.label == label } }
    SectionLabel("SIZE")
    SectionTabs(CardFormat.entries.map { it.label }, style.format.label) { label -> formatIndex = CardFormat.entries.indexOfFirst { it.label == label } }
    SectionLabel("COLOURS")
    SectionTabs(listOf("Midnight", "Cream", "Lilac"), listOf("Midnight", "Cream", "Lilac")[palette]) {
        palette = listOf("Midnight", "Cream", "Lilac").indexOf(it)
    }

    if (collectionFull) {
        val feedback = remember { BringIntoViewRequester() }
        LaunchedEffect(Unit) { feedback.bringIntoView() }
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(feedback), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Collection full · ${CaptionLibrary.LIMIT} cards", color = Amber, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Remove one saved card to make room.", color = Muted, fontSize = 14.sp)
            Button(onClick = openSaved, modifier = Modifier.fillMaxWidth()) { Text("Manage collection") }
        }
    } else Button(enabled = ready != null && text.isNotEmpty() && !saving && !sharing && !saved,
        onClick = {
            val snapshot = ready ?: return@Button
            saving = true; error = null
            scope.launch {
                try { withContext(Dispatchers.IO) { library.save(snapshot) }; saved = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: CaptionCollectionFullException) { collectionFull = true }
                catch (_: Exception) { error = "The card couldn’t be saved. Check your device’s free space and try again." }
                finally { saving = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Saving…" else if (saved) "Saved to your collection" else "Save card") }
    if (saved) TextButton(onClick = openSaved) { Text("View collection") }
    OutlinedButton(
        enabled = ready != null && text.isNotEmpty() && !sharing && !saving,
        onClick = {
            val snapshot = ready ?: return@OutlinedButton
            sharing = true
            error = null
            scope.launch {
                try {
                    val intent = withContext(Dispatchers.IO) { captionShareIntent(context.applicationContext, snapshot) }
                    context.startActivity(intent)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "The card couldn’t be shared. Please try again."
                } finally {
                    sharing = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (sharing) "Preparing image…" else "Share caption card") }
    error?.let { Text(it, color = Amber) }
    Text("Saved cards stay on this device. Sharing lets you keep a copy elsewhere.", color = Muted, fontSize = 12.sp)
}
