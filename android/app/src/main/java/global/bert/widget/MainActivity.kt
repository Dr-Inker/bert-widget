package global.bert.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import global.bert.widget.data.*
import global.bert.widget.widget.updateAllBERTWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class BERTDestination(val label: String, val icon: BERTSymbol) {
    HOME("Home", BERTSymbol.HOME), EXPLORE("Explore", BERTSymbol.EXPLORE), CREATE("Create", BERTSymbol.CREATE), TOOLS("Tools", BERTSymbol.TOOLS)
}

class MainActivity : ComponentActivity() {
    // A widget or notification tap names a destination; a restored activity keeps where the person was.
    private var entry by mutableStateOf<BERTEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reopening from Recents replays the original intent (API 26-30); that is not a fresh tap.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) entry = BERTEntry.from(intent)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        setContent {
            BERTTheme { BERTScreen(lifecycle, entry) { entry = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entry = BERTEntry.from(intent)
    }
}

@Composable
private fun BERTScreen(lifecycle: Lifecycle, entry: BERTEntry?, consumeEntry: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { BERTQuoteRepository(context.applicationContext) }
    val activityRepository = remember { BERTActivityRepository(context.applicationContext) }
    val historyStore = remember { BERTPriceHistory(context.applicationContext) }
    val marketHistoryRepository = remember { BERTMarketHistoryRepository(context.applicationContext) }
    val holdingsStore = remember { BERTHoldingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<QuoteState>(repository.load()?.let { QuoteState.Available(it) } ?: QuoteState.Loading) }
    var history by remember { mutableStateOf(historyStore.load()) }
    var marketHistory by remember { mutableStateOf(marketHistoryRepository.load()) }
    var position by remember { mutableStateOf(holdingsStore.loadPosition()) }
    var refreshing by remember { mutableStateOf(false) }
    var activityState by remember { mutableStateOf<ActivityState>(activityRepository.load()?.let { ActivityState.Available(it) } ?: ActivityState.Loading) }
    var activityRefreshing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    suspend fun updateWidgets() {
        try { updateAllBERTWidgets(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A launcher failure must not turn a valid quote into a network error. */ }
    }

    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        try {
            state = QuoteState.Available(repository.refresh())
            history = historyStore.load()
            updateWidgets()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = repository.load()?.let { QuoteState.Available(it, true) }
                ?: QuoteState.Unavailable("BERT market data is unavailable right now.")
        } finally {
            now = System.currentTimeMillis()
            refreshing = false
        }
    }

    suspend fun refreshMarketHistory() {
        // Optional enrichment: on failure the chart keeps the cached server history plus device quotes.
        try { marketHistory = marketHistoryRepository.refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { marketHistory = marketHistoryRepository.load() }
    }

    suspend fun refreshActivity() {
        if (activityRefreshing) return
        activityRefreshing = true
        try {
            val activity = activityRepository.refresh()
            activityState = ActivityState.Available(activity)
            // Read here, so it is not news for a later dispatch alert.
            activity.dispatch?.let { BERTAlertStore(context.applicationContext).markDispatchSeen(it) }
            updateWidgets() // the Bert widget shows this feed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            activityState = activityRepository.load()?.let { ActivityState.Available(it, true) } ?: ActivityState.Unavailable
        } finally {
            now = System.currentTimeMillis()
            activityRefreshing = false
        }
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
            launch(Dispatchers.IO) { BERTHomeVisits(context.applicationContext).recordOpen() } // "Day N with Bert"
            launch { while (true) { refreshActivity(); delay(5 * 60_000) } }
            launch { while (true) { refreshMarketHistory(); delay(5 * 60_000) } }
            while (true) { refresh(); delay(60_000) }
        }
    }
    val chartHistory = remember(marketHistory, history, now) { BERTPriceHistory.combine(marketHistory, history, now) }
    BERTApp(state, activityState, chartHistory, position, now, refreshing, activityRefreshing,
        entry = entry, consumeEntry = consumeEntry,
        refresh = { scope.launch { refresh() } },
        refreshActivity = { scope.launch { refreshActivity() } },
        savePosition = {
            holdingsStore.savePosition(it)
            position = it
            scope.launch { updateWidgets() }
        },
    )
}

@Composable
internal fun BERTApp(
    state: QuoteState, activityState: ActivityState, history: List<BERTPriceSample>,
    position: BERTPosition, now: Long, refreshing: Boolean, activityRefreshing: Boolean,
    refresh: () -> Unit, refreshActivity: () -> Unit, savePosition: (BERTPosition) -> Unit,
    entry: BERTEntry? = null, consumeEntry: () -> Unit = {},
) {
    // A cold start from a widget or notification draws its screen from the first frame, not Home then a jump.
    var destination by rememberSaveable { mutableStateOf(when (entry) { BERTEntry.MARKET -> BERTDestination.TOOLS; BERTEntry.CREATE -> BERTDestination.CREATE; else -> BERTDestination.HOME }) }
    var toolsTab by rememberSaveable { mutableStateOf("Market") }
    var createTab by rememberSaveable { mutableStateOf("Art") }
    // "Make it a card" hands Create a caption once; Create consumes it so returning later keeps the person's own edits.
    var cardSeed by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(entry) {
        when (entry) {
            BERTEntry.MARKET -> { destination = BERTDestination.TOOLS; toolsTab = "Market" }
            BERTEntry.HOME -> destination = BERTDestination.HOME
            BERTEntry.CREATE -> { destination = BERTDestination.CREATE; createTab = "Art" }
            BERTEntry.GAME -> openLostTrail(context) // once: consumeEntry below clears the entry
            null -> return@LaunchedEffect
        }
        consumeEntry()
    }
    val savedScreens = rememberSaveableStateHolder()

    BackHandler(destination != BERTDestination.HOME) { destination = BERTDestination.HOME }

    Scaffold(
        containerColor = Navy,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { BERTNavigation(destination) { destination = it } },
    ) { padding ->
        // A short crossfade between destinations, and again between sections, so nothing hard-cuts.
        Crossfade(destination, Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            animationSpec = tween(180), label = "destination") { shown ->
            val hasSections = shown == BERTDestination.CREATE || shown == BERTDestination.TOOLS
            val largeText = LocalDensity.current.fontScale > 1.3f
            Column(Modifier.fillMaxSize()) {
                if (hasSections) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The selected bottom destination already names the section. At larger
                        // text sizes, prioritize its content over a repeated pinned heading.
                        if (!largeText) AppHeader(shown)
                        val tabPadding = if (largeText) 12.dp else 16.dp
                        if (shown == BERTDestination.CREATE) SectionTabs(listOf("Art", "Saved", "Personalise"), createTab, tabPadding, singleRow = true) { createTab = it }
                        else SectionTabs(listOf("Market", "Holdings", "Alerts"), toolsTab, tabPadding, singleRow = true) { toolsTab = it }
                    }
                }
                val screenKey = when (shown) {
                    BERTDestination.TOOLS -> "TOOLS-$toolsTab"
                    BERTDestination.CREATE -> "CREATE-$createTab"
                    else -> shown.name
                }
                Crossfade(screenKey, Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(150), label = "section") { key ->
                  savedScreens.SaveableStateProvider(key) {
                    val body = Modifier.fillMaxSize()
                    if (shown == BERTDestination.CREATE && key == "CREATE-Saved") {
                        SavedCardsScreen(body) { createTab = "Art" }
                    } else if (shown == BERTDestination.HOME) {
                        HomeScreen(body, activityState, now, activityRefreshing || refreshing,
                            retry = { refreshActivity(); refresh() },
                            quote = state, position = position,
                            openMarket = { destination = BERTDestination.TOOLS; toolsTab = "Market" },
                            makeCard = { caption -> cardSeed = caption; createTab = "Art"; destination = BERTDestination.CREATE },
                        ) { step ->
                            when (step) {
                                GettingStartedStep.WIDGET -> { destination = BERTDestination.CREATE; createTab = "Personalise" }
                                GettingStartedStep.GAME -> openLostTrail(context)
                                GettingStartedStep.CARD -> { destination = BERTDestination.CREATE; createTab = "Art" }
                                GettingStartedStep.ALERT -> { destination = BERTDestination.TOOLS; toolsTab = "Alerts" }
                            }
                        }
                    } else BERTScreenContent(body) {
                        if (!hasSections) AppHeader(shown)
                        when (shown) {
                            BERTDestination.EXPLORE -> ExploreScreen()
                            BERTDestination.CREATE -> CreateScreen(key.removePrefix("CREATE-"), seedCaption = cardSeed, consumeSeed = { cardSeed = null }) { createTab = it }
                            BERTDestination.TOOLS -> ToolsScreen(state, history, position, now, refreshing, key.removePrefix("TOOLS-"),
                                refresh = refresh, savePosition = savePosition)
                            BERTDestination.HOME -> Unit
                        }
                    }
                  }
                }
            }
        }
    }
}

@Composable
internal fun BERTScreenContent(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        content()
        AboutFooter()
    }
}

@Composable
private fun ToolsScreen(state: QuoteState, history: List<BERTPriceSample>, position: BERTPosition, now: Long,
                        refreshing: Boolean, tab: String,
                        refresh: () -> Unit, savePosition: (BERTPosition) -> Unit) {
    when (tab) {
        "Market" -> MarketScreen(state, history, now, refreshing, refresh)
        "Alerts" -> AlertsScreen(state)
        else -> HoldingsScreen(position, state, now, savePosition)
    }
}

@Composable
internal fun SectionTabs(tabs: List<String>, selected: String, horizontalPadding: Dp = 16.dp, singleRow: Boolean = false, select: (String) -> Unit) {
    // Pinned section tabs stay one row (scrolling sideways at large text) so they never push content down;
    // option pickers wrap so every choice stays visible.
    val haptics = LocalHapticFeedback.current
    val rowModifier = if (singleRow) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
    FlowRow(rowModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxLines = if (singleRow) 1 else Int.MAX_VALUE) {
        tabs.forEach { tab ->
            Surface(color = if (tab == selected) PanelStrong else Navy, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (tab == selected) AccentText else Muted.copy(alpha = 0.35f))) {
                Box(Modifier.selectable(tab == selected, onClick = {
                    if (tab != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    select(tab)
                }, role = Role.Tab)
                    .heightIn(min = 48.dp).padding(horizontal = horizontalPadding, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(tab, color = if (tab == selected) Cream else Muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun AppHeader(destination: BERTDestination) {
    if (destination == BERTDestination.CREATE || destination == BERTDestination.TOOLS) {
        // Text only: the photo stamped on every header made Bert wallpaper rather than a character.
        Text(destination.label.uppercase(), color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, modifier = Modifier.heightIn(min = 32.dp).wrapContentHeight().semantics { heading() })
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Home's Today card carries pixel Bert, so its header is the official wordmark alone.
        if (destination != BERTDestination.HOME) Image(painterResource(R.drawable.bert_token), "Bertram the Pomeranian", Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BERTWordmark(Modifier.height(if (destination == BERTDestination.HOME) 30.dp else 22.dp))
            Text(if (destination == BERTDestination.HOME) "GOOD DOG. BIG WORLD." else destination.label.uppercase(), color = Muted, fontSize = 11.sp, letterSpacing = 0.8.sp)
        }
    }
}

/** The official BERT wordmark (white artwork, tinted to the text colour). */
@Composable
internal fun BERTWordmark(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Cream) {
    Image(painterResource(R.drawable.bert_wordmark), "BERT", modifier.aspectRatio(640f / 186f),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color))
}

@Composable
private fun BERTNavigation(destination: BERTDestination, choose: (BERTDestination) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val select = { item: BERTDestination ->
        if (item != destination) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        choose(item)
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        Surface(color = Navy) {
            Column(Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BERTDestination.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pair.forEach { item ->
                            Surface(color = if (item == destination) PanelStrong else Navy, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                                Row(Modifier.selectable(item == destination, onClick = { select(item) }, role = Role.Tab)
                                    .heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BERTIcon(item.icon, if (item == destination) AccentText else Muted)
                                    Text(item.label, color = if (item == destination) Cream else Muted, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        NavigationBar(containerColor = Navy, contentColor = Cream) {
            BERTDestination.entries.forEach { item ->
                NavigationBarItem(selected = destination == item, onClick = { select(item) },
                    icon = { BERTIcon(item.icon, if (item == destination) AccentText else Muted) },
                    label = { Text(item.label, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedTextColor = Cream,
                        unselectedTextColor = Muted, indicatorColor = PanelStrong))
            }
        }
    }
}
