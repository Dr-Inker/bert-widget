package global.bert.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.ActivityState
import global.bert.widget.data.BERTHomeVisits
import global.bert.widget.data.BERTJournal
import global.bert.widget.data.BERTPosition
import global.bert.widget.data.HomeVisit
import global.bert.widget.data.JournalEntry
import global.bert.widget.data.LostTrailProgress
import global.bert.widget.data.QuoteState
import global.bert.widget.data.BERTEvent
import global.bert.widget.data.BERTStanding
import global.bert.widget.data.EventPhase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Home uses three radii only.
private val Small = RoundedCornerShape(12.dp)
private val Medium = RoundedCornerShape(20.dp)
private val Large = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(modifier: Modifier, state: ActivityState, now: Long, refreshing: Boolean, retry: () -> Unit,
                        quote: QuoteState = QuoteState.Loading, position: BERTPosition = BERTPosition(),
                        openMarket: () -> Unit = {}, makeCard: (String) -> Unit = {}, openStep: (GettingStartedStep) -> Unit = {}) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Pull state is ours, not the feed's: the five-minute background refresh must not flash a spinner.
    var pulled by remember { mutableStateOf(false) }
    val busy by rememberUpdatedState(refreshing)
    LaunchedEffect(pulled) {
        if (!pulled) return@LaunchedEffect
        delay(800) // one whole jump, and time for the refresh to begin
        snapshotFlow { busy }.first { !it }
        pulled = false
    }
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(pulled, onRefresh = { pulled = true; retry() }, modifier, state = pull, indicator = {
        PullToRefreshDefaults.Indicator(pull, pulled, Modifier.align(Alignment.TopCenter), containerColor = PanelStrong, color = Orange)
    }) {
        // Pull-to-refresh has no TalkBack gesture; the same refresh is offered as an action on the screen.
        BERTScreenContent(Modifier.fillMaxSize().semantics { customActions = listOf(CustomAccessibilityAction("Refresh") { retry(); true }) }, scroll) {
            AppHeader(BERTDestination.HOME)
            OfficialChips()
            HomeContent(state, now, pulled, quote, position, openMarket, makeCard, openStep,
                toTop = { scope.launch { scroll.animateScrollTo(0) } }, toY = { y -> scope.launch { scroll.animateScrollTo(y) } })
        }
    }
}

@Composable
private fun ColumnScope.HomeContent(state: ActivityState, now: Long, pacing: Boolean, quote: QuoteState, position: BERTPosition,
                                    openMarket: () -> Unit, makeCard: (String) -> Unit, openStep: (GettingStartedStep) -> Unit,
                                    toTop: () -> Unit, toY: (Int) -> Unit) {
    val context = LocalContext.current
    val visits = remember { BERTHomeVisits(context.applicationContext) }
    val journalStore = remember { BERTJournal(context.applicationContext) }
    val day = { at: Long -> Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate() }
    // Small local reads, done for the first frame so the cards never jump in after it.
    var visit by remember { mutableStateOf(visits.visit(now, day(now))) }
    // A sitting is 30+ minutes apart, so a 5-minute check is plenty (not a prefs rewrite on every 15 s tick).
    LaunchedEffect(now / 300_000) { visit = withContext(Dispatchers.IO) { visits.visit(now, day(now)) } }
    var meetBert by remember { mutableStateOf(!visits.meetBertDismissed) }
    val activity = (state as? ActivityState.Available)?.activity
    val current = activity?.dispatch
    // First frame: one small synchronous read (and record) so "first seen" and "New" never pop in after it.
    var journal by remember { mutableStateOf(activity?.let { journalStore.record(it, now) }.let { journalStore.entries() }) }
    LaunchedEffect(current) {
        // The feed has no post time, so "first seen" is the honest one; record here too in case Home saw it first.
        journal = withContext(Dispatchers.IO) {
            activity?.let { journalStore.record(it, now) }
            journalStore.entries()
        }
    }
    val fresh = BERTHomeVisits.newSince(journal, visit, current)
    var earlierY by remember { mutableIntStateOf(0) }

    var exitReport by rememberExitReport()
    exitReport?.let { report -> ExitReportCard(report) { exitReport = null } }
    if (meetBert) MeetBertCard { visits.meetBertDismissed = true; meetBert = false }
    TodayCard(state, now, pacing, journal.firstOrNull { it.text == current }, visit, fresh.size.coerceAtMost(6), makeCard,
        showNew = { toY(earlierY) }, meetBert = { meetBert = true; toTop() })
    PriceStrip(quote, position, now, openMarket)
    val trail by produceState<LostTrailProgress?>(null, now) {
        value = withContext(Dispatchers.IO) { LostTrailProgress.load(context.applicationContext) }
        // Leaving the game copies its save asynchronously; look once more so the card is current on return.
        delay(1_500)
        value = withContext(Dispatchers.IO) { LostTrailProgress.load(context.applicationContext) }
    }
    LostTrailContinueCard(trail) { openLostTrail(context) }

    if (state is ActivityState.Available) state.activity.event?.let { event ->
        TournamentCard(event, now, delayed = state.updateDelayed || state.activity.isStaleAt(now), updatedAt = state.activity.updatedAtEpochMillis)
    }

    // Re-read with the clock tick, so steps finished elsewhere (a placed widget, a saved card) tick off on return.
    var hidden by remember { mutableStateOf(false) }
    val progress by produceState<GettingStarted?>(null, now) {
        if (value?.visible == false) return@produceState // hidden or finished: no more checks this session
        value = withContext(Dispatchers.IO) { BERTOnboarding.progress(context.applicationContext) }
    }
    progress?.takeIf { !hidden && it.visible }?.let { GettingStartedCard(it, openStep) {
        BERTOnboarding.hide(context.applicationContext)
        hidden = true
    } }

    val earlier = journal.filter { it.text != current }
    EarlierFromBert(earlier.take(maxOf(3, fresh.size).coerceAtMost(6)), fresh.map { it.firstSeenEpochMillis }.toSet(), makeCard,
        Modifier.onGloballyPositioned { earlierY = it.positionInParent().y.toInt() })
}

/** "WOOFmornin" is Bert's own good-morning; the rest follow the phone's clock. */
internal fun greetingAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    when (Instant.ofEpochMilli(epochMillis).atZone(zone).hour) {
        in 5..10 -> "WOOFmornin, pack."
        in 11..16 -> "Afternoon, pack."
        in 17..21 -> "Evening, pack."
        else -> "Up late, pack?"
    }

/** "MONDAY 12 OCTOBER"; at large text "MON 12 OCT", so the date keeps to one line instead of three. */
internal fun todayLabel(epochMillis: Long, large: Boolean, zone: ZoneId = ZoneId.systemDefault(), locale: java.util.Locale = java.util.Locale.getDefault()): String =
    DateTimeFormatter.ofPattern(if (large) "EEE d MMM" else "EEEE d MMMM", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))
        .uppercase(locale).replace(".", "")

/**
 * The feed's mood is derived from the price, so Home gives it as a character note in Bert's voice, never a reading:
 * no pill, no colour, nowhere near the price.
 */
internal fun moodPhrase(label: String): String = when (val mood = label.trim().lowercase()) {
    "melancholy" -> "feeling a bit melancholy"
    "content" -> "content"
    "giddy" -> "giddy"
    "smug" -> "a little smug"
    "anxious" -> "a touch anxious"
    "restless" -> "a bit restless"
    else -> mood
}

/** Bert's own channels, as two quiet chips under the wordmark; Berthalla is the fan project. */
@Composable
private fun OfficialChips() {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text("Official", color = Muted, fontSize = 12.sp)
        OfficialChip("@bertcoincto", "Official BERT on X, @bertcoincto") { openBERTLink(context, BERTLink.OFFICIAL_X) }
        OfficialChip("bert.global", "Official BERT website, bert.global") { openBERTLink(context, BERTLink.STORY) }
    }
}

@Composable
private fun OfficialChip(label: String, spoken: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Panel, shape = Small) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = spoken })
            BERTIcon(BERTSymbol.ARROW, AccentText, Modifier.size(14.dp))
        }
    }
}

/** First run only (and on a long press of pixel Bert): who Bert is, and whose app this is. */
@Composable
private fun MeetBertCard(dismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = Medium) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val side = pixelBertSize(72.dp)
                Image(painterResource(R.drawable.bert_icon), "Bertram, the real dog, in a red hat and blue coat",
                    Modifier.size(side).clip(Small), contentScale = ContentScale.Crop)
                PixelBertPortrait(side, Modifier.clip(Small).background(PanelStrong))
            }
            Text("Meet Bert", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("Bert is the Mayor of Berthalla, a Pomeranian in a red hat. The pixel and cartoon Bert are drawn from Bertram, the real dog who started it all.",
                color = Cream, fontSize = 15.sp, lineHeight = 22.sp)
            Text("This app and its daily updates come from Berthalla, a fan project. Official BERT: @bertcoincto and bert.global.",
                color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            // A quiet dismissal: Today's "Make it a card" stays the one filled button on screen.
            TextButton(onClick = dismiss, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Nice to meet you", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TodayCard(state: ActivityState, now: Long, pacing: Boolean, entry: JournalEntry?, visit: HomeVisit?, newCount: Int,
                      makeCard: (String) -> Unit, showNew: () -> Unit, meetBert: () -> Unit) {
    val context = LocalContext.current
    val large = LocalDensity.current.fontScale > 1.3f
    val today = remember(now / 60_000, large) { todayLabel(now, large) }
    val activity = (state as? ActivityState.Available)?.activity
    Card(colors = CardDefaults.cardColors(containerColor = PanelStrong), shape = Large) {
        TodayGarden(pacing, onLongPress = meetBert)
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(today)
                Greeting(now, activity?.mood, visit?.days)
            }
            if (newCount > 0) Surface(onClick = showNew, color = Panel, shape = Small) {
                Text("$newCount new from Bert", color = AccentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            var dispatch: String? = null
            when (state) {
                ActivityState.Loading -> {
                    Text("Checking in with Bert…", color = Cream)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AccentText, trackColor = Panel)
                }
                ActivityState.Unavailable -> {
                    Text("Today’s update couldn’t load.", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Pull down to try again. There’s still plenty to play, make and explore.", color = Muted, lineHeight = 21.sp)
                }
                is ActivityState.Available -> {
                    dispatch = state.activity.dispatch
                    state.activity.dispatch?.let { UpdateText(it) }
                    val delayed = state.updateDelayed || state.activity.isStaleAt(now)
                    val seen = entry?.let {
                        formatObservedAt(it.firstSeenEpochMillis, now, use24Hour = android.text.format.DateFormat.is24HourFormat(context)).removePrefix("As of ")
                    }
                    val source = if (delayed) "Saved update from Berthalla" else "From Berthalla"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!delayed && BERTHomeVisits.isNew(entry, visit, now)) NewTag()
                        Text(seen?.let { "$source, first seen $it" } ?: source, color = if (delayed) Amber else Muted, fontSize = 13.sp)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                dispatch?.let { text ->
                    Button(onClick = { makeCard(captionExcerpt(text)) }, colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Navy)) {
                        Text("Make it a card", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { openBERTLink(context, BERTLink.DISPATCHES) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Berthalla on X")
                    Spacer(Modifier.width(6.dp))
                    BERTIcon(BERTSymbol.ARROW, AccentText, Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun Greeting(now: Long, mood: String?, days: Int?) {
    Text(greetingAt(now), color = Cream, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
    mood?.let { Text(moodPhrase(it), color = Muted, fontSize = 15.sp, fontStyle = FontStyle.Italic) }
    days?.takeIf { it > 0 }?.let { Text("Day $it with Bert", color = Muted, fontSize = 12.sp) }
}

/** A changed update rises 8dp into place as it fades in; the cached one never just snaps to the fresh one. */
@Composable
private fun UpdateText(text: String) {
    val rise = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedContent(text, transitionSpec = {
        (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320)) { rise }) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
    }, label = "update") { shown ->
        Column {
            var expanded by rememberSaveable(shown) { mutableStateOf(false) }
            var overflowed by remember(shown) { mutableStateOf(false) }
            Text(shown, color = Cream, fontSize = 19.sp, lineHeight = 28.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflowed = it.hasVisualOverflow })
            if (overflowed || expanded) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Read less" else "Read full update")
            }
        }
    }
}

@Composable
private fun NewTag() {
    Text("New", color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(Small).background(Orange).padding(horizontal = 7.dp, vertical = 2.dp))
}

/** A small borderless label for Home (the shared StatusPill keeps its own look elsewhere). */
@Composable
internal fun HomeTag(text: String, color: Color) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.clip(Small).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 5.dp))
}

/** The price at a glance, one tap from Market. Holders see their bag; nobody else is asked about one. */
@Composable
private fun PriceStrip(quote: QuoteState, position: BERTPosition, now: Long, openMarket: () -> Unit) {
    Card(onClick = openMarket, colors = CardDefaults.cardColors(containerColor = Panel), shape = Medium) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("\$BERT")
                when (quote) {
                    QuoteState.Loading -> Text("Fetching the price…", color = Muted, fontSize = 15.sp)
                    is QuoteState.Unavailable -> Text("Price unavailable right now", color = Muted, fontSize = 15.sp)
                    is QuoteState.Available -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.Bottom) {
                        RollingPrice(quote.quote.priceUsd)
                        val change = quote.quote.change24hPct
                        Text("${formatPercent(change)} · 24h", color = when { change == null -> Muted; change >= 0 -> Green; else -> Red },
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
                        if (quote.updateDelayed || quote.quote.isStaleAt(now)) Text("delayed", color = Amber, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            val bag = (quote as? QuoteState.Available)?.let { position.valueUsd(it.quote.priceUsd) }
            if (bag != null) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("YOUR BAG", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(formatHoldingsUsd(bag), color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            } else Text("Market ›", color = AccentText, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A new price rolls in from below on a rise and from above on a fall; an unchanged display never moves. */
@Composable
private fun RollingPrice(price: Double) {
    AnimatedContent(price, contentKey = { formatPrice(it) }, transitionSpec = {
        val up = targetState > initialState
        (slideInVertically(tween(240)) { if (up) it else -it } + fadeIn(tween(240))) togetherWith
            (slideOutVertically(tween(240)) { if (up) -it else it } + fadeOut(tween(160)))
    }, label = "price") { shown ->
        Text(formatPrice(shown), color = Cream, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Bert's own channels, kept apart from Berthalla's so nobody mistakes the fan project for the official voice. */
@Composable
internal fun OfficialBertCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Cream.copy(alpha = 0.1f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("OFFICIAL BERT")
            OfficialRow("@bertcoincto on X", "Bert’s main account") { openBERTLink(context, BERTLink.OFFICIAL_X) }
            OfficialRow("bert.global", "The official BERT website") { openBERTLink(context, BERTLink.STORY) }
        }
    }
}

@Composable
private fun OfficialRow(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button)
        .heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 13.sp)
        }
        BERTIcon(BERTSymbol.ARROW, AccentText, Modifier.size(18.dp))
    }
}

/** The game's own art, full width, with where the person is and how far they've come. */
@Composable
private fun LostTrailContinueCard(progress: LostTrailProgress?, open: () -> Unit) {
    val next = progress?.nextWorld
    val started = progress?.started == true
    val title = when {
        progress == null || !started -> "Play The Lost Trail"
        next != null -> "Continue · ${LostTrailProgress.WORLDS[next]}"
        progress.bones >= LostTrailProgress.TOTAL_BONES -> "Every world, every keepsake"
        else -> "All six worlds cleared"
    }
    Card(onClick = open, colors = CardDefaults.cardColors(containerColor = Panel), shape = Medium) {
        Image(painterResource(R.drawable.lost_trail), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (started) Text("The Lost Trail", color = Muted, fontSize = 13.sp)
                    Text(title, color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                BERTIcon(BERTSymbol.PLAY, AccentText)
            }
            if (progress != null && started) {
                WorldsBar(progress.worldsCleared, progress.worldInProgress)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${progress.worldsCleared} of ${LostTrailProgress.WORLDS.size} worlds cleared", color = Muted, fontSize = 13.sp)
                    Text("${progress.bones} of ${LostTrailProgress.TOTAL_BONES} keepsakes", color = Muted, fontSize = 13.sp)
                }
            } else Text("Six worlds of Bert’s pixel adventure. Works offline.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

/** One segment per world: cleared, under way, or still ahead. The counts beside it say the same in words. */
@Composable
private fun WorldsBar(cleared: Int, inProgress: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LostTrailProgress.WORLDS.indices.forEach { world ->
            val fill = when {
                world < cleared -> Amber
                world == inProgress -> Amber.copy(alpha = 0.4f)
                else -> Navy
            }
            Box(Modifier.weight(1f).height(6.dp).clip(Small).background(fill))
        }
    }
}

@Composable
private fun EarlierFromBert(entries: List<JournalEntry>, fresh: Set<Long>, makeCard: (String) -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Box(modifier) { SectionLabel("EARLIER FROM BERT") }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.getDefault()) }
    entries.forEach { entry ->
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = Medium) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val seen = formatter.format(Instant.ofEpochMilli(entry.firstSeenEpochMillis).atZone(ZoneId.systemDefault()))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.firstSeenEpochMillis in fresh) NewTag()
                    Text(seen, color = Muted, fontSize = 12.sp)
                    entry.mood?.let { Text(moodPhrase(it), color = Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic) }
                }
                Text(entry.text, color = Cream, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 10.dp))
                TextButton(onClick = { makeCard(captionExcerpt(entry.text)) },
                    modifier = Modifier.semantics { contentDescription = "Make a card from the update of $seen" }) { Text("Make it a card") }
            }
        }
    }
}

@Composable
internal fun FeatureLink(title: String, description: String, symbol: BERTSymbol, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Cream.copy(alpha = 0.1f))) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BERTIcon(symbol)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** "Ends in 2 days 7h", "Ends in 3h 12m", "Ends in 4m" — computed on the device clock. */
internal fun formatTimeLeft(endsAt: Long, now: Long): String {
    val minutes = ((endsAt - now) / 60_000).coerceAtLeast(0)
    val days = minutes / 1_440
    val hours = minutes % 1_440 / 60
    return when {
        days >= 2 -> "Ends in $days days ${hours}h"
        days == 1L -> "Ends in 1 day ${hours}h"
        minutes >= 60 -> "Ends in ${minutes / 60}h ${minutes % 60}m"
        else -> "Ends in ${minutes}m"
    }
}

@Composable
private fun TournamentCard(event: BERTEvent, now: Long, delayed: Boolean, updatedAt: Long) {
    val context = LocalContext.current
    val phase = event.phaseAt(now, delayed)
    val ended = phase == EventPhase.ENDED
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = Medium) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeTag(phase.label, if (phase == EventPhase.OPEN) Green else Muted)
                if (!ended && phase != EventPhase.UNCONFIRMED) HomeTag(formatTimeLeft(event.endsAtEpochMillis, now).uppercase(), AccentText)
            }
            Text(event.name, color = Cream, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            val sponsor = event.sponsor?.replace(Regex("dr\\.?\\s*inker\\s*labs", RegexOption.IGNORE_CASE), "DrInkerLABS")
            Text(listOfNotNull("Flappy Bert tournament", sponsor?.let { "sponsored by $it" }).joinToString(" · "), color = Muted, fontSize = 13.sp)
            event.pool?.let { Text("${'$'}$it prize pool · top 5 paid", color = Amber, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            if (event.standings.isNotEmpty()) {
                SectionLabel(if (ended) "FINAL STANDINGS" else "LEADERBOARD")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    event.standings.forEach { row -> StandingRow(row, leader = row.rank == 1) }
                }
                if (delayed) {
                    val minutes = ((now - updatedAt) / 60_000).coerceAtLeast(0)
                    Text("Standings as of ${if (minutes < 60) "${minutes}m" else "${minutes / 60}h"} ago", color = Amber, fontSize = 12.sp)
                }
            }
            if (phase == EventPhase.UNCONFIRMED) Text("Check the game for the current tournament status.", color = Amber, fontSize = 13.sp)
            val endDate = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'").withZone(ZoneId.of("UTC")).format(Instant.ofEpochMilli(event.endsAtEpochMillis))
            Text("${if (ended) "Ended" else "Scheduled end"} $endDate", color = Muted, fontSize = 12.sp)
            if (!ended) Button(onClick = { openBERTLink(context, BERTLink.FLAPPY) }) {
                Text("Play in Telegram")
                Spacer(Modifier.width(8.dp))
                BERTIcon(BERTSymbol.ARROW, Navy, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StandingRow(row: BERTStanding, leader: Boolean) {
    Row(Modifier.fillMaxWidth().clip(Small).background(if (leader) Amber.copy(alpha = 0.12f) else PanelStrong)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${row.rank}", color = if (leader) Amber else Muted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(18.dp))
        Text(row.name, color = Cream, fontSize = 15.sp, fontWeight = if (leader) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${row.score}", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        row.prizeUsd?.let { Text("${'$'}$it", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 40.dp)) }
    }
}
