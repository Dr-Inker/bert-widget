package global.bert.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
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
    } else if (tab == "Saved") {
        SavedCardsScreen { selectTab("Art") }
    } else {
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
    val library = remember { CaptionLibrary(context.applicationContext) }
    var sharing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val text = caption.trim()
    var saved by remember(text, palette) { mutableStateOf(false) }
    var previewError by remember { mutableStateOf(false) }
    val rendered by produceState<RenderedCaption?>(null, text, palette) {
        previewError = false
        delay(200)
        try {
            value = withContext(Dispatchers.Default) { renderCaption(context.applicationContext, text, palette) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            previewError = true
        }
    }
    val ready = rendered?.takeIf { it.caption == text && it.palette == palette }

    Text("Your words. Bert’s face.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Make a card for the pack. No account needed.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    if (ready != null) {
        Image(ready.bitmap.asImageBitmap(), "Caption card preview: $text", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)))
    } else {
        Surface(color = Panel, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(if (previewError) "Preview unavailable. Edit the caption to try again." else "Updating preview…", color = Muted, modifier = Modifier.padding(20.dp))
            }
        }
    }
    OutlinedTextField(
        value = caption,
        onValueChange = {
            val input = it.replace(Regex("[\\r\\n\\t]+"), " ").filterNot { char -> char.isISOControl() }
            if (input.length <= 96) { caption = input; error = null }
        },
        label = { Text("Your caption") },
        supportingText = { Text("${caption.length}/96") },
        minLines = 2, maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionTabs(listOf("Midnight", "Cream", "Lilac"), listOf("Midnight", "Cream", "Lilac")[palette]) {
        palette = listOf("Midnight", "Cream", "Lilac").indexOf(it)
    }

    Button(enabled = ready != null && text.isNotEmpty() && !saving && !sharing && !saved,
        onClick = {
            val snapshot = ready ?: return@Button
            saving = true; error = null
            scope.launch {
                try { withContext(Dispatchers.IO) { library.save(snapshot) }; saved = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "The card couldn’t be saved. Check space on your device or remove a card if your collection is full." }
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
