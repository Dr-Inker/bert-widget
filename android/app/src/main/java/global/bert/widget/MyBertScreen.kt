package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.ChosenBert
import global.bert.widget.data.MyBert
import global.bert.widget.data.MyBertList
import global.bert.widget.data.MyBertLookupException
import global.bert.widget.data.MyBertRepository
import global.bert.widget.data.MyBertStore
import global.bert.widget.data.cleanWalletInput
import global.bert.widget.data.isSolanaPubkey
import global.bert.widget.widget.updateAllBERTWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory

/** The chosen Bert, shared as state so Home, Create and Personalise change together; [open] shows the My Bert screen. */
internal class MyBertHandle(val chosen: ChosenBert?, val open: () -> Unit, val update: (ChosenBert?) -> Unit)

internal val LocalMyBert = compositionLocalOf { MyBertHandle(null, {}, {}) }

/** Lets host tests hand My Bert a repository with a fake network; the app leaves it unset. */
internal val LocalMyBertRepository = staticCompositionLocalOf<MyBertRepository?> { null }

/** One plain line on what leaves the phone, shown above the address field. */
internal const val MY_BERT_PRIVACY = "Your address goes to Magic Eden and the Solana network to find your Berts. Nothing is signed; nothing can be spent."

/** The chosen Bert's own picture, read from the phone (it never needs the network once chosen). */
@Composable
internal fun rememberMyBertPicture(chosen: ChosenBert?): ImageBitmap? {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(null, chosen?.bert?.mint) {
        value = chosen?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(MyBertStore(context.applicationContext).imageFile.path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return picture
}

/** Widget refreshes outlive the screen: leaving My Bert straight after a choice must not leave the widget behind. */
private val widgetRefreshes = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private val Tile = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(20.dp)

@Composable
internal fun MyBertScreen(modifier: Modifier, close: () -> Unit, repository: MyBertRepository? = null) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val handle = LocalMyBert.current
    val injected = LocalMyBertRepository.current
    val repo = remember { repository ?: injected ?: MyBertRepository(context.applicationContext) }
    val store = repo.store
    val scope = rememberCoroutineScope()
    val saved = remember { store.wallet() }
    var input by rememberSaveable { mutableStateOf(saved.orEmpty()) }
    var wallet by rememberSaveable { mutableStateOf(saved) }
    var editing by rememberSaveable { mutableStateOf(saved == null) }
    var inputError by rememberSaveable { mutableStateOf(false) }
    // A recent list comes back without a lookup; an older one waits for "Refresh my Berts" (no lookup without a tap).
    var list by remember { mutableStateOf(saved?.let { store.cachedList(it, System.currentTimeMillis()) }) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<MyBertLookupException.Reason?>(null) }
    var picked by rememberSaveable { mutableStateOf(handle.chosen?.bert?.mint) }
    var applying by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // The lookup and the download in flight, so Cancel, classic Bert and Forget can stop them.
    val jobs = remember { object { var lookup: Job? = null; var use: Job? = null } }

    fun refreshWidgets() = widgetRefreshes.launch {
        try { updateAllBERTWidgets(context.applicationContext) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* widgets catch up on their next refresh */ }
    }

    fun find(address: String) {
        if (loading) return // one lookup per tap
        if (!isSolanaPubkey(address)) { inputError = true; return }
        inputError = false; failure = null; notice = null; loading = true
        jobs.lookup = scope.launch {
            try {
                val found = repo.lookup(address)
                wallet = address; input = address; editing = false; list = found
                if (found.berts.none { it.mint == picked }) picked = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: MyBertLookupException) {
                failure = error.reason
            } catch (_: Exception) {
                failure = MyBertLookupException.Reason.FAILED
            } finally {
                loading = false
            }
        }
    }

    fun use(bert: MyBert) {
        if (applying) return
        applying = true; notice = null
        jobs.use = scope.launch {
            try {
                repo.choose(bert)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                notice = "${bert.name} is now your Bert on Home, your Bert widget and your cards."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = "${bert.name}’s picture couldn’t be downloaded. Try again."
            } finally {
                applying = false
                // Home, cards and widgets follow what the phone now holds, even when leaving the screen cancelled
                // this after the picture was saved.
                handle.update(store.chosen())
                refreshWidgets()
            }
        }
    }

    fun classic() {
        jobs.use?.cancel(); applying = false
        store.clearChosen()
        clearMyBertArtThumbnails()
        handle.update(null)
        notice = "Classic Bert is back."
        refreshWidgets()
    }

    fun forget() {
        jobs.lookup?.cancel(); jobs.use?.cancel(); loading = false; applying = false
        store.forget()
        clearMyBertArtThumbnails()
        MyBertRepository.clearMemory()
        handle.update(null)
        wallet = null; input = ""; list = null; picked = null; failure = null; editing = true; inputError = false
        notice = "Address forgotten. Its Berts and their pictures are gone from this phone, and classic Bert is back."
        refreshWidgets()
    }

    BERTScreenContent(modifier) {
        TextButton(onClick = close, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Back", fontWeight = FontWeight.SemiBold) }
        Text("My Bert", color = Cream, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
        Text("Hold a Bert from the official BERT NFT collection? Show it on Home, on your Bert widget and on your cards.",
            color = Cream, fontSize = 16.sp, lineHeight = 23.sp)

        handle.chosen?.let { CurrentBert(it, ::classic) }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = CardShape) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(MY_BERT_PRIVACY, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                val shown = wallet
                if (editing || shown == null) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (it.length <= 120) { input = cleanWalletInput(it); inputError = false } },
                        label = { Text("Your Solana wallet address") },
                        singleLine = true,
                        isError = inputError,
                        supportingText = {
                            if (inputError) Text("That isn’t a Solana wallet address. Check it and paste it again.",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            else Text("Paste a public address. Never a recovery phrase or private key.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { find(input) }),
                        modifier = Modifier.fillMaxWidth().testTag("mybert-address"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(colors = actionButtonColors(), border = actionButtonBorder(!loading && input.isNotBlank()), onClick = { find(input) }, enabled = !loading && input.isNotBlank()) {
                            Text(if (loading) "Looking…" else "Find my Berts", fontWeight = FontWeight.Bold)
                        }
                        if (shown != null) TextButton(onClick = { jobs.lookup?.cancel(); loading = false; editing = false; input = shown; inputError = false }) { Text("Cancel") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Wallet", color = Muted, fontSize = 13.sp)
                            Text(shortAddress(shown), color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.semantics { contentDescription = "Wallet ending ${shown.takeLast(4)}" })
                        }
                        TextButton(onClick = { editing = true }) { Text("Change") }
                    }
                }
            }
        }

        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = AccentText, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text("Looking for your Berts…", color = Cream)
        }
        failure?.let { reason -> LookupProblem(reason) { wallet?.takeIf { !editing }?.let(::find) ?: find(input) } }
        notice?.let {
            Text(it, color = Cream, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        val current = list
        if (current != null && !loading) {
            if (current.berts.isEmpty()) EmptyWallet(current.gap)
            else Berts(current, picked, handle.chosen?.bert?.mint, applying, repo, pick = { picked = it }, use = ::use)
        }

        if (wallet != null || handle.chosen != null) {
            HorizontalDivider(color = Muted.copy(alpha = 0.2f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wallet?.let { address ->
                    TextButton(onClick = { find(address) }, enabled = !loading) { Text("Refresh my Berts") }
                }
                TextButton(onClick = ::forget, colors = ButtonDefaults.textButtonColors(contentColor = Red)) { Text("Forget this address") }
            }
            Text("Forgetting deletes the address, the list and every Bert picture from this phone.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private fun shortAddress(address: String) = "${address.take(4)}…${address.takeLast(4)}"

@Composable
private fun CurrentBert(chosen: ChosenBert, classic: () -> Unit) {
    val picture = rememberMyBertPicture(chosen)
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = CardShape) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(Tile).background(Panel)) {
                picture?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Your Bert", color = Muted, fontSize = 13.sp)
                Text(chosen.bert.name, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = classic, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Go back to classic Bert") }
            }
        }
    }
}

@Composable
private fun LookupProblem(reason: MyBertLookupException.Reason, retry: () -> Unit) {
    val message = when (reason) {
        MyBertLookupException.Reason.OFFLINE -> "You’re offline, so your Berts couldn’t be checked. Connect and try again."
        MyBertLookupException.Reason.BUSY -> "Magic Eden or the Solana network is busy. Try again in a minute."
        MyBertLookupException.Reason.FAILED -> "Your Berts couldn’t be loaded just now. Try again."
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = Amber, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            OutlinedButton(onClick = retry) { Text("Try again") }
        }
    }
}

@Composable
private fun GapNote(gap: MyBertList.Gap?) {
    val text = when (gap) {
        MyBertList.Gap.HELD -> "Magic Eden didn’t answer, so Berts held or listed here may be missing. Refresh to check again."
        MyBertList.Gap.STAKED -> "The Solana network didn’t answer, so staked Berts may be missing. Refresh to check again."
        null -> return
    }
    Text(text, color = Amber, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun EmptyWallet(gap: MyBertList.Gap?) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = CardShape) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No Berts in this wallet", color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Berts held, listed or staked from this address show up here. Check that it’s the right address.",
                color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            GapNote(gap)
        }
    }
}

@Composable
private fun Berts(list: MyBertList, picked: String?, inUse: String?, applying: Boolean, repository: MyBertRepository,
                  pick: (String) -> Unit, use: (MyBert) -> Unit) {
    val count = list.berts.size
    Text(if (count == 1) "1 Bert in this wallet" else "$count Berts in this wallet", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() })
    GapNote(list.gap)
    list.berts.firstOrNull { it.mint == picked }?.let { Preview(it, it.mint == inUse, applying, repository, use) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        list.berts.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { bert -> BertTile(bert, bert.mint == picked, bert.mint == inUse, repository, Modifier.weight(1f)) { pick(bert.mint) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun rememberThumbnail(bert: MyBert, repository: MyBertRepository): ImageBitmap? {
    val picture by produceState<ImageBitmap?>(null, bert.number) { value = repository.thumbnail(bert)?.asImageBitmap() }
    return picture
}

private fun tagFor(bert: MyBert) = when { bert.staked -> "Staked"; bert.listed -> "Listed"; else -> null }

@Composable
private fun BertTile(bert: MyBert, selected: Boolean, inUse: Boolean, repository: MyBertRepository, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val picture = rememberThumbnail(bert, repository)
    val spoken = listOfNotNull(bert.name, tagFor(bert), "your Bert".takeIf { inUse }).joinToString(", ")
    Column(modifier.clip(Tile).selectable(selected, role = Role.RadioButton) {
        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onClick()
    }.semantics { contentDescription = spoken }.testTag("mybert-${bert.number}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Tile).background(PanelStrong)
            .then(if (selected) Modifier.border(3.dp, Selected, Tile) else Modifier)) {
            picture?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(bert.name, color = Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val tag = tagFor(bert)
        if (tag != null || inUse) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tag?.let { HomeTag(it, if (bert.staked) Green else Muted) }
            if (inUse) HomeTag("Yours", AccentText)
        }
    }
}

@Composable
private fun Preview(bert: MyBert, inUse: Boolean, applying: Boolean, repository: MyBertRepository, use: (MyBert) -> Unit) {
    val picture = rememberThumbnail(bert, repository)
    val view = remember { BringIntoViewRequester() }
    LaunchedEffect(bert.mint) { view.bringIntoView() }
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = CardShape, modifier = Modifier.bringIntoViewRequester(view)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp).clip(Tile).background(Panel)) {
                picture?.let { Image(it, "${bert.name}, preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bert.name, color = Cream, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                val detail = listOfNotNull(bert.background?.let { "$it background" }, tagFor(bert)).joinToString(". ")
                if (detail.isNotEmpty()) Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                if (inUse) Text("This is your Bert.", color = AccentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                else Button(onClick = { use(bert) }, enabled = !applying, modifier = Modifier.testTag("mybert-use"),
                    colors = solidActionButtonColors()) {
                    Text(if (applying) "Saving…" else "Use this Bert", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Personalise's way in: the chosen Bert's picture, or a quiet invitation when there is none. */
@Composable
internal fun MyBertRow() {
    val handle = LocalMyBert.current
    val chosen = handle.chosen
    val picture = rememberMyBertPicture(chosen)
    Card(onClick = handle.open, colors = CardDefaults.cardColors(containerColor = Panel), shape = CardShape,
        modifier = Modifier.testTag("mybert-row")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(Tile).background(PanelStrong), contentAlignment = Alignment.Center) {
                if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else PixelBertPortrait(pixelBertSize(48.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("My Bert", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(chosen?.let { "${it.bert.name} is your Bert. Change it, or go back to classic Bert." }
                    ?: "Hold a Bert from the NFT collection? Show it on Home, your Bert widget and your cards.",
                    color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** Today's header chip: the holder's Bert, small and crisp. Tapping it opens My Bert. */
@Composable
internal fun MyBertChip(chosen: ChosenBert, modifier: Modifier = Modifier) {
    val handle = LocalMyBert.current
    val picture = rememberMyBertPicture(chosen)
    Surface(onClick = handle.open, color = Panel, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(chosen.palette.accentArgb).copy(alpha = 0.55f)),
        modifier = modifier.semantics { contentDescription = "Your Bert, ${chosen.bert.name}. Opens My Bert." }) {
        Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(PanelStrong)) {
                picture?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Text("Your Bert #${chosen.bert.number}", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
