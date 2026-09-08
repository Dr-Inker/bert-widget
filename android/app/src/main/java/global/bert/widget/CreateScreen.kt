package global.bert.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
    Text("Make it yours.", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    SectionTabs(listOf("Art", "Personalize"), tab, selectTab)
    if (tab == "Art") {
        ExperienceLink("Draw Bert", "Describe a scene in Bert’s web Studio and see what he paints.", "✦", BERTLink.DRAW)
        CaptionCardCreator()
    } else {
        Text("A little BERT. Everywhere.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Widgets for a quick glance. Wallpapers for the rest of your day.", color = Muted, fontSize = 14.sp)
        WidgetSetup(context)
        ThemeStudio()
    }
}

@Composable
private fun CaptionCardCreator() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by rememberSaveable { mutableStateOf("woofmornin. the mayor is in.") }
    var palette by rememberSaveable { mutableIntStateOf(0) }
    var sharing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val text = caption.trim()
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

    SectionLabel("YOUR WORDS. BERT’S FACE.")
    Text("Make a caption card", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text("Made on your device. Add a line, pick a color, then export a square image.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    OutlinedTextField(
        value = caption,
        onValueChange = {
            val input = it.replace(Regex("[\\r\\n\\t]+"), " ").filterNot { char -> char.isISOControl() }
            if (input.length <= 96) { caption = input; error = null }
        },
        label = { Text("Your caption") },
        supportingText = { Text("${caption.length}/96") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionTabs(listOf("Midnight", "Cream", "Lilac"), listOf("Midnight", "Cream", "Lilac")[palette]) {
        palette = listOf("Midnight", "Cream", "Lilac").indexOf(it)
    }
    if (ready != null) {
        Image(ready.bitmap.asImageBitmap(), "Caption card preview: $text", Modifier.fillMaxWidth().aspectRatio(1f))
    } else {
        Surface(color = Panel, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(if (previewError) "Preview unavailable. Edit the caption to try again." else "Updating preview…", color = Muted, modifier = Modifier.padding(20.dp))
            }
        }
    }
    Button(
        enabled = ready != null && text.isNotEmpty() && !sharing,
        onClick = {
            val snapshot = ready ?: return@Button
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
    Text("Choose where to send or save it in the share sheet.", color = Muted, fontSize = 12.sp)
}
