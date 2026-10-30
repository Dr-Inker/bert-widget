package global.bert.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 0.14, "You": one calm page that gathers what already belongs to the person (their Bert, their style, their visits,
 * their cards, their game) and an optional name for Bert to call them by. Nothing here is an account: the name is kept on this
 * phone and goes nowhere.
 */

/** Opens the You page over whatever tab is showing; set by [BERTApp], a no-op anywhere else. */
internal val LocalOpenYou = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The person's face on the You page and on Home's button: their chosen Bert NFT when there is one, otherwise the
 * head and hat cut from pixel Bert (the same F/10 still as the Home garden, so the button belongs to the scene).
 */
@Composable
internal fun YouAvatar(size: Dp, picture: ImageBitmap?, modifier: Modifier = Modifier) {
    val pixelBert = ImageBitmap.imageResource(R.drawable.bert_f10)
    Box(modifier.size(size).clip(CircleShape).background(PanelStrong)) {
        if (picture != null) Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Canvas(Modifier.fillMaxSize()) {
            // The head, hat and collar of the 84 px still, scaled up as hard-edged pixels like everything in the garden.
            drawImage(pixelBert, srcOffset = IntOffset(PIXEL_HEAD_X, PIXEL_HEAD_Y), srcSize = IntSize(PIXEL_HEAD, PIXEL_HEAD),
                dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.None)
        }
    }
}

// Where the head sits in bert_f10.png (84 x 84): a square that holds the hat brim, the face and the top of the coat.
private const val PIXEL_HEAD_X = 22
private const val PIXEL_HEAD_Y = 1
private const val PIXEL_HEAD = 42

/**
 * Home's way in, floated over the sky at the top right: a 48dp-tall pill, the avatar and the word "You". On its own the
 * avatar read as part of the picture (the owner's S25, 0.14 preview); the word makes it a button. The page's panel
 * colour with a cream ring and a soft shadow keeps it readable on the pale day sky and on the dark night one.
 */
@Composable
internal fun YouButton(modifier: Modifier = Modifier) {
    val open = LocalOpenYou.current
    val picture = rememberMyBertPicture(LocalMyBert.current.chosen)
    Row(modifier.height(YOU_HEIGHT).shadow(6.dp, CircleShape).clip(CircleShape).background(PanelStrong)
        .clickable(onClickLabel = "Open your page", role = Role.Button, onClick = open).border(2.dp, Cream, CircleShape)
        .padding(start = 4.dp, end = 16.dp).testTag("you-button"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        YouAvatar(40.dp, picture)
        // The word is also its TalkBack label (the avatar inside has none), read once: "You, button".
        Text("You", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun YouScreen(modifier: Modifier, holdingsSet: Boolean, close: () -> Unit,
                       openStyle: () -> Unit, openHoldings: () -> Unit, openSaved: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { YouProfile(context.applicationContext) }
    val myBert = LocalMyBert.current
    val picture = rememberMyBertPicture(myBert.chosen)
    val focus = LocalFocusManager.current
    // The field keeps what was typed (so a space between two words survives), and where the cursor is, so an edit that
    // is refused can leave both as they were; what is saved is the tidied name.
    var typed by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(profile.name())) }
    val name = YouProfile.cleanName(typed.text)

    BERTScreenContent(modifier) {
        TextButton(onClick = close, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Back", fontWeight = FontWeight.SemiBold) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            YouAvatar(72.dp, picture, Modifier.border(2.dp, Cream.copy(alpha = 0.6f), CircleShape).padding(2.dp).testTag("you-avatar"))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("You", color = Cream, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
                Text(if (name.isEmpty()) "Bert doesn’t know your name yet." else "Bert calls you $name.", color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        OutlinedTextField(
            value = typed,
            // Counted as it will be saved (tidied), so spaces never use up the 24; see [fitName] for an edit that goes over.
            onValueChange = { edit ->
                val before = typed.text
                typed = fitName(typed, edit)
                if (typed.text != before) profile.setName(typed.text)
            },
            label = { Text("What should Bert call you?") },
            supportingText = { Text("Optional. Bert uses it on the noticeboard.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("you-name"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureLink("Your Bert", myBert.chosen?.bert?.name ?: "Show your Bert NFT", BERTSymbol.HEART, myBert.open)
            FeatureLink("Style", "Widgets, wallpapers and colours.", BERTSymbol.TOOLS, openStyle)
            VisitsCard()
            TrailLink(openTrail = { openLostTrail(context) })
            val cards by produceState<Int?>(null) {
                value = withContext(Dispatchers.IO) { runCatching { CaptionLibrary(context.applicationContext).list().size }.getOrDefault(0) }
            }
            FeatureLink("Your cards", when (cards) {
                null -> "The cards you’ve made and saved."
                0 -> "No saved cards yet. Make one in Create."
                1 -> "1 saved card"
                else -> "$cards saved cards"
            }, BERTSymbol.CREATE, openSaved)
            FeatureLink("Draw Bert", "Describe a scene in Bert’s web Studio. Opens in your browser.", BERTSymbol.CREATE) { openBERTLink(context, BERTLink.DRAW) }
            FeatureLink("Holdings", if (holdingsSet) "Set up. Kept on this phone." else "Not set up. Optional, and only on this phone.",
                BERTSymbol.TOOLS, openHoldings)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Everything here stays on this phone.", color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.testTag("you-footer"))
            if (name.isNotEmpty()) TextButton(onClick = { profile.forget(); typed = TextFieldValue("") }, contentPadding = PaddingValues(horizontal = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Red)) { Text("Forget my name") }
        }
    }
}

/**
 * The name field's next value after [edit]. An edit that fits the cap once tidied is kept as typed (a line break from a
 * paste becomes a space). One that goes over keeps everything that was already there and only as much of the new text as
 * fits: a letter typed into the middle of a full name is refused, leaving the name and the cursor where they were (cutting
 * the end instead used to delete the last letter, out of sight), and a long paste is cut to the cap rather than dropped.
 */
private fun fitName(previous: TextFieldValue, edit: TextFieldValue): TextFieldValue {
    if (YouProfile.fits(edit.text)) return edit.copy(text = edit.text.replace('\n', ' '))
    val (head, added, tail) = replacement(previous, edit.text)
    // What was there already fits (it is what this function kept last time); if not, the old rule: the tidied name.
    if (!YouProfile.fits(head + tail)) return YouProfile.cleanName(edit.text).let { TextFieldValue(it, TextRange(it.length)) }
    // The most of the new text that still fits, cut only where a character as a person sees it ends (read with what is
    // before it, so a skin tone added to a thumbs-up stays with it), so a flag, a family or a keycap is never left half
    // there. Adding characters never shortens the tidied name, so a binary search finds it, even in a long paste of spaces.
    val ends = YouProfile.characterEnds(head + added).filter { it > head.length }.map { it - head.length }
    fun addedUpTo(count: Int) = if (count == 0) "" else added.substring(0, ends[count - 1])
    var low = 0
    var high = ends.size
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (YouProfile.fits(head + addedUpTo(middle) + tail)) low = middle else high = middle - 1
    }
    if (low == 0) return previous
    val kept = addedUpTo(low)
    return TextFieldValue((head + kept + tail).replace('\n', ' '), TextRange(head.length + kept.length))
}

/**
 * [new] as one replacement in [previous]: the text kept before and after it, and what was put between. Typing and pasting
 * replace the selection (or insert at the cursor), so that reading is tried first; any other edit (a keyboard rewriting a
 * word, say) is read from what the two texts share at each end, never stopping between the two halves of an emoji.
 */
private fun replacement(previous: TextFieldValue, new: String): Triple<String, String, String> {
    val old = previous.text
    val before = old.substring(0, previous.selection.min)
    val after = old.substring(previous.selection.max)
    if (new.length >= before.length + after.length && new.startsWith(before) && new.endsWith(after))
        return Triple(before, new.substring(before.length, new.length - after.length), after)
    var start = 0
    while (start < old.length && start < new.length && old[start] == new[start]) start++
    if (start > 0 && Character.isHighSurrogate(new[start - 1])) start--
    var end = 0
    while (end < old.length - start && end < new.length - start && old[old.length - 1 - end] == new[new.length - 1 - end]) end++
    if (end > 0 && Character.isLowSurrogate(new[new.length - end])) end--
    return Triple(new.substring(0, start), new.substring(start, new.length - end), new.substring(new.length - end))
}

/** The days this month the person came round, as Home's paw prints in the soil: read only, never a visit of its own. */
@Composable
private fun VisitsCard() {
    val context = LocalContext.current
    val days = remember { BertVisits(context.applicationContext).thisMonth(LocalDate.now()).sorted() }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cream.copy(alpha = 0.1f)),
        modifier = Modifier.testTag("you-visits")) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BERTIcon(BERTSymbol.HOME)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Your visits", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(when (days.size) {
                    0 -> "No visits yet this month. Come round and Bert leaves a paw print."
                    1 -> "You’ve come round 1 day this month"
                    else -> "You’ve come round ${days.size} days this month"
                }, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                // One print per day, a little uneven like the ones in the garden. The words above say it all for TalkBack.
                if (days.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { day -> PawPrint(AccentText, Modifier.offset(y = if (day % 2 == 0) 3.dp else 0.dp)) }
                }
            }
        }
    }
}

/** One paw print in a 5 x 5 pixel grid: three toes over a pad, the same shape Home presses into the soil. */
@Composable
private fun PawPrint(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val unit = size.width / 5
        fun dot(x: Int, y: Int, w: Int = 1, h: Int = 1) = drawRect(color, Offset(x * unit, y * unit), Size(w * unit, h * unit))
        dot(0, 1); dot(2, 0); dot(4, 1); dot(1, 3, 3, 2)
    }
}

/** The Lost Trail's progress in one line, from the game's own saved results; opening it goes straight to the game. */
@Composable
private fun TrailLink(openTrail: () -> Unit) {
    val progress = rememberLostTrailProgress(remember { System.currentTimeMillis() })
    FeatureLink("The Lost Trail", if (progress != null && progress.started)
        "${progress.worldsCleared} of ${LostTrailProgress.WORLDS.size} worlds cleared · ${progress.bones} of ${LostTrailProgress.TOTAL_BONES} keepsakes"
    else "Bert’s platform game. Six worlds to clear.", BERTSymbol.PLAY, openTrail)
}
