package global.bert.widget

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CreateScreen(tab: String, seedCaption: String? = null, consumeSeed: () -> Unit = {}, selectTab: (String) -> Unit) {
    val context = LocalContext.current
    if (tab == "Art") {
        CaptionCardCreator(seedCaption, consumeSeed) { selectTab("Saved") }
        SectionLabel("KEEP CREATING")
        ExperienceLink("Draw Bert", "Describe a scene in Bert’s web Studio.", BERTSymbol.CREATE, BERTLink.DRAW)
    } else if (tab == "Personalise") {
        Text("A little BERT. Everywhere.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Widgets for a quick glance. Wallpapers for the rest of your day.", color = Muted, fontSize = 14.sp)
        WidgetSetup(context)
        ThemeStudio()
    }
}

@Composable
private fun CaptionCardCreator(seedCaption: String?, consumeSeed: () -> Unit, openSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var caption by rememberSaveable { mutableStateOf("woofmornin. the mayor is in.") }
    LaunchedEffect(seedCaption) { if (seedCaption != null) { caption = seedCaption; consumeSeed() } }
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
    // The last finished card stays on screen while the next one renders, then the two crossfade.
    var shown by remember { mutableStateOf<RenderedCaption?>(null) }
    // A progress hint appears only when a render is genuinely slow, never on every keystroke.
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(text, palette, style, canRender) {
        previewError = false
        // With nothing to render the message replaces the card, and the next card arrives without the typing delay.
        if (!canRender) { shown = null; return@LaunchedEffect }
        if (shown != null) delay(200)
        val hint = launch { delay(400); slow = true }
        try {
            shown = withContext(Dispatchers.Default) { renderCaption(context.applicationContext, text, palette, style) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            previewError = true
        } finally {
            hint.cancel()
            slow = false
        }
    }
    val ready = shown?.takeIf { canRender && it.caption == text && it.palette == palette && it.style == style }

    Text("Your words. Bert’s face.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Make a card for the pack. No account needed.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    val message = when {
        overLimit -> "Shorten your caption to see the preview."
        text.isEmpty() -> "Your caption will appear here."
        previewError -> "Preview unavailable. Edit the caption to try again."
        else -> null
    }
    CaptionPreview(if (message != null) message else shown, style.format, current = ready != null, slow = slow)
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
    ArtPicker(style.art) { artIndex = it.ordinal }
    CardChoices(layoutIndex, formatIndex, palette, { layoutIndex = it }, { formatIndex = it }, { palette = it })

    Button(
        enabled = ready != null && text.isNotEmpty() && !sharing && !saving,
        onClick = {
            val snapshot = ready ?: return@Button
            sharing = true
            error = null
            scope.launch {
                try {
                    val intent = withContext(Dispatchers.IO) { captionShareIntent(context.applicationContext, snapshot) }
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("create-share"),
    ) { Text(if (sharing) "Preparing image…" else "Share caption card", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    if (collectionFull) {
        val feedback = remember { BringIntoViewRequester() }
        LaunchedEffect(Unit) { feedback.bringIntoView() }
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(feedback), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Collection full · ${CaptionLibrary.LIMIT} cards", color = Amber, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Remove one saved card to make room.", color = Muted, fontSize = 14.sp)
            FilledTonalButton(onClick = openSaved, modifier = Modifier.fillMaxWidth()) { Text("Manage collection") }
        }
    } else FilledTonalButton(enabled = ready != null && text.isNotEmpty() && !saving && !sharing && !saved,
        onClick = {
            val snapshot = ready ?: return@FilledTonalButton
            saving = true; error = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { library.save(snapshot) }
                    saved = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: CaptionCollectionFullException) { collectionFull = true }
                catch (_: Exception) { error = "The card couldn’t be saved. Check your device’s free space and try again." }
                finally { saving = false }
            }
        }, modifier = Modifier.fillMaxWidth().testTag("create-save")) { Text(if (saving) "Saving…" else if (saved) "Saved to your collection" else "Save card") }
    if (saved) TextButton(onClick = openSaved) { Text("View collection") }
    error?.let { Text(it, color = Amber) }
    Text("Saved cards stay on this device. Sharing lets you keep a copy elsewhere.", color = Muted, fontSize = 12.sp)
}

/**
 * [content] is the card to show (the previous one while the next renders, when [current] is false),
 * or a message when there is nothing to render.
 */
@Composable
private fun CaptionPreview(content: Any?, format: CardFormat, current: Boolean, slow: Boolean) {
    // Story cards keep their true 9:16 shape at a narrower width so the preview stays on screen.
    fun frame(format: CardFormat) = Modifier.fillMaxWidth(if (format == CardFormat.STORY) 0.62f else 1f)
        .aspectRatio(format.width.toFloat() / format.height).clip(RoundedCornerShape(20.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Crossfade(content, animationSpec = tween(150), label = "caption preview") { item ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (item is RenderedCaption) {
                    // Only an up-to-date card is announced by its caption; a card fading out has no label.
                    val description = when {
                        item !== content -> null
                        current -> "Caption card preview: ${item.caption}"
                        else -> "Updating card preview"
                    }
                    Image(item.bitmap.asImageBitmap(), description, frame(item.style.format))
                } else Surface(color = Panel, modifier = frame(format)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (item is String) Text(item, color = Muted, modifier = Modifier.padding(20.dp))
                    }
                }
            }
        }
        if (slow) CircularProgressIndicator(color = Orange, strokeWidth = 2.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(18.dp).semantics { contentDescription = "Updating preview" })
    }
}

/** The five artworks as thumbnails, each showing the crop the card will use. */
@Composable
private fun ArtPicker(selected: CardArt, select: (CardArt) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sizePx = with(LocalDensity.current) { 72.dp.roundToPx() }
    val thumbnails by produceState(CardArt.entries.mapNotNull { art -> thumbnailCache[art to sizePx]?.let { art to it } }.toMap(), sizePx) {
        // Decoded side by side, once per process: returning to Create shows them at once.
        CardArt.entries.filter { !thumbnailCache.containsKey(it to sizePx) }.map { art ->
            async(Dispatchers.IO) { art to runCatching { artThumbnail(context.applicationContext, art, sizePx).asImageBitmap() }.getOrNull() }
        }.forEach { pending ->
            val (art, thumbnail) = pending.await()
            if (thumbnail != null) { thumbnailCache[art to sizePx] = thumbnail; value = value + (art to thumbnail) }
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CardArt.entries.forEach { art ->
            val isSelected = art == selected
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
                .then(if (isSelected) Modifier.border(3.dp, Orange, RoundedCornerShape(24.dp)) else Modifier)
                .selectable(isSelected, role = Role.RadioButton) { if (!isSelected) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); select(art) } }
                .semantics { contentDescription = art.description }
                .testTag("art-${art.name}"),
                contentAlignment = Alignment.Center) {
                val image = thumbnails[art]
                val inner = Modifier.size(if (isSelected) 66.dp else 72.dp).clip(RoundedCornerShape(if (isSelected) 18.dp else 20.dp))
                if (image != null) Image(image, null, inner.testTag("art-thumbnail"), filterQuality = if (art.pixelArt) FilterQuality.None else FilterQuality.Low)
                else Box(inner.background(PanelStrong))
            }
        }
    }
}

private val thumbnailCache = java.util.concurrent.ConcurrentHashMap<Pair<CardArt, Int>, ImageBitmap>()

/** Layout and size side by side, colours below: compact segmented controls instead of rows of loose chips. */
@Composable
private fun CardChoices(layout: Int, format: Int, palette: Int, selectLayout: (Int) -> Unit, selectFormat: (Int) -> Unit, selectPalette: (Int) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (largeText) {
            Segmented("Layout", CardLayout.entries.map { it.label }, layout, select = selectLayout)
            Segmented("Size", CardFormat.entries.map { it.label }, format, select = selectFormat)
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented("Layout", CardLayout.entries.map { it.label }, layout, Modifier.weight(1f), select = selectLayout)
            Segmented("Size", CardFormat.entries.map { it.label }, format, Modifier.weight(1f), select = selectFormat)
        }
        Segmented("Colours", CARD_PALETTE_NAMES, palette, swatches = CARD_PALETTE_SWATCHES, select = selectPalette)
    }
}

private val CARD_PALETTE_NAMES = listOf("Midnight", "Cream", "Lilac")
private val CARD_PALETTE_SWATCHES = listOf(Color(0xFF071A2F), Color(0xFFFFF5DF), Color(0xFFE3CEFF))

@Composable
private fun Segmented(label: String, options: List<String>, selected: Int, modifier: Modifier = Modifier,
                      swatches: List<Color>? = null, select: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    // At large text the options stack, so a long name never breaks inside a segment.
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Surface(color = Navy, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Muted.copy(alpha = 0.3f))) {
            val segment: @Composable (Int, Modifier) -> Unit = { index, segmentModifier ->
                val isSelected = index == selected
                Box(segmentModifier.clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) PanelStrong else Color.Transparent)
                    .then(if (isSelected) Modifier.border(1.dp, Orange, RoundedCornerShape(11.dp)) else Modifier)
                    .selectable(isSelected, role = Role.RadioButton) { if (!isSelected) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); select(index) } }
                    .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        swatches?.let { Box(Modifier.size(12.dp).clip(CircleShape).background(it[index]).border(1.dp, Muted.copy(alpha = 0.6f), CircleShape)) }
                        Text(options[index], color = if (isSelected) Cream else Muted, fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
            if (stacked) Column(Modifier.padding(3.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.indices.forEach { segment(it, Modifier.fillMaxWidth()) }
            } else Row(Modifier.padding(3.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                options.indices.forEach { segment(it, Modifier.weight(1f)) }
            }
        }
    }
}
