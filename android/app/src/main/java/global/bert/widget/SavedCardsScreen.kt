package global.bert.widget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SavedCardsScreen(modifier: Modifier = Modifier, makeCard: () -> Unit) {
    val context = LocalContext.current
    val library = remember { CaptionLibrary(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val collectionScroll = rememberScrollState()
    val detailScroll = key(selectedId) { rememberScrollState() }
    val entries by produceState<List<SavedCaption>?>(null, revision) {
        value = null
        try { value = withContext(Dispatchers.IO) { library.list() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Your collection couldn’t load. Try again." }
    }
    val card by produceState<RenderedCaption?>(null, selectedId) {
        value = null
        val id = selectedId ?: return@produceState
        try { value = withContext(Dispatchers.IO) { library.open(id) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "This saved image couldn’t load. Your other cards are still available." }
    }
    val backToCollection = { if (!busy) { selectedId = null; error = null } }
    BackHandler(selectedId != null, onBack = backToCollection)
    // Do not attach a restored scroll state to a short loading placeholder: that would clamp it to zero.
    val loading = error == null && if (selectedId == null) entries == null else card == null
    if (loading) {
        Box(modifier.padding(20.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    } else BERTScreenContent(modifier, if (selectedId == null) collectionScroll else detailScroll) {
    Text(if (selectedId == null) "Your collection" else "Made by you.", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    if (selectedId != null) {
        TextButton(onClick = backToCollection, enabled = !busy) { Text("Back to collection") }
        card?.let { ready ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(ready.bitmap.asImageBitmap(), "Saved caption card: ${ready.caption}",
                    Modifier.fillMaxWidth(if (ready.bitmap.height > ready.bitmap.width) 0.62f else 1f)
                        .aspectRatio(ready.bitmap.width.toFloat() / ready.bitmap.height).clip(RoundedCornerShape(20.dp)))
            }
            Text(ready.caption, color = Cream, fontSize = 17.sp, lineHeight = 24.sp)
            Button(onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val intent = withContext(Dispatchers.IO) { captionShareIntent(context.applicationContext, ready) }
                        context.startActivity(intent)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "The card couldn’t be shared. Try again." }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Preparing…" else "Share saved card") }
        }
        TextButton(onClick = { removing = true }, enabled = !busy) { Text("Delete card", color = Muted) }
    } else {
        val cards = entries
        if (cards != null) Text("${cards.size}/${CaptionLibrary.LIMIT} cards · stored on this device", color = Muted, fontSize = 12.sp)
        when {
            cards == null && error == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            cards != null && cards.isEmpty() -> {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BERTIcon(BERTSymbol.CREATE)
                        Text("Your first card belongs here.", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Make a little something. Save it here. Come back whenever you need a smile.", color = Muted, lineHeight = 23.sp)
                        Button(onClick = makeCard) { Text("Make a card") }
                    }
                }
            }
            cards != null -> cards.forEach { entry ->
                val thumbnail by produceState<android.graphics.Bitmap?>(null, entry.id) {
                    try { value = withContext(Dispatchers.IO) { library.thumbnail(entry.id) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Full card has an explicit failure state when opened. */ }
                }
                Card(onClick = { selectedId = entry.id; error = null }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(PanelStrong), contentAlignment = Alignment.Center) {
                            if (thumbnail != null) Image(requireNotNull(thumbnail).asImageBitmap(), null, Modifier.fillMaxSize().testTag("saved-card-thumbnail"),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            else BERTIcon(BERTSymbol.CREATE, Muted)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.caption, color = Cream, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.savedAt)), color = Muted, fontSize = 12.sp)
                            Text("Open card", color = AccentText, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Text("Share cards to keep a copy elsewhere. Uninstalling or clearing app data removes this collection.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
    error?.let { message ->
        Text(message, color = Amber)
        if (selectedId == null) TextButton(onClick = { error = null; revision++ }) { Text("Try again") }
    }
    }
    if (removing) AlertDialog(onDismissRequest = { if (!busy) removing = false },
        title = { Text("Delete this card?") }, text = { Text("It will be removed from this device. Copies you shared will stay where you sent them.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val id = selectedId ?: return@TextButton
            busy = true
            scope.launch {
                try { withContext(Dispatchers.IO) { library.delete(id) }; selectedId = null; revision++; removing = false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "The card couldn’t be removed. Try again."; removing = false }
                finally { busy = false }
            }
        }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { removing = false }, enabled = !busy) { Text("Keep card") } })
}
