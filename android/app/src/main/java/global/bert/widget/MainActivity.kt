package global.bert.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class BERTDestination(val label: String, val icon: BERTSymbol) {
    HOME("Home", BERTSymbol.HOME), EXPLORE("Explore", BERTSymbol.EXPLORE), CREATE("Create", BERTSymbol.CREATE), TOOLS("Tools", BERTSymbol.TOOLS)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        setContent {
            BERTTheme { BERTScreen(lifecycle) }
        }
    }
}

@Composable
private fun BERTScreen(lifecycle: Lifecycle) {
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
            launch { while (true) { refreshActivity(); delay(5 * 60_000) } }
            launch { while (true) { refreshMarketHistory(); delay(5 * 60_000) } }
            while (true) { refresh(); delay(60_000) }
        }
    }
    val chartHistory = remember(marketHistory, history, now) { BERTPriceHistory.combine(marketHistory, history, now) }
    BERTApp(state, activityState, chartHistory, position, now, refreshing, activityRefreshing,
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
) {
    var destination by rememberSaveable { mutableStateOf(BERTDestination.HOME) }
    var toolsTab by rememberSaveable { mutableStateOf("Market") }
    var createTab by rememberSaveable { mutableStateOf("Art") }
    val savedScreens = rememberSaveableStateHolder()

    BackHandler(destination != BERTDestination.HOME) { destination = BERTDestination.HOME }

    Scaffold(
        containerColor = Navy,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { BERTNavigation(destination) { destination = it } },
    ) { padding ->
        val screenKey = when (destination) {
            BERTDestination.TOOLS -> "TOOLS-$toolsTab"
            BERTDestination.CREATE -> "CREATE-$createTab"
            else -> destination.name
        }
        val hasSections = destination == BERTDestination.CREATE || destination == BERTDestination.TOOLS
        val largeText = LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (hasSections) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The selected bottom destination already names the section. At larger
                    // text sizes, prioritize its content over a repeated pinned heading.
                    if (!largeText) AppHeader(destination)
                    val tabPadding = if (largeText) 12.dp else 16.dp
                    if (destination == BERTDestination.CREATE) SectionTabs(listOf("Art", "Saved", "Personalize"), createTab, tabPadding, singleRow = true) { createTab = it }
                    else SectionTabs(listOf("Market", "Holdings", "Alerts"), toolsTab, tabPadding, singleRow = true) { toolsTab = it }
                }
            }
            savedScreens.SaveableStateProvider(screenKey) {
                val body = Modifier.weight(1f).fillMaxWidth()
                if (destination == BERTDestination.CREATE && createTab == "Saved") {
                    SavedCardsScreen(body) { createTab = "Art" }
                } else BERTScreenContent(body) {
                    if (!hasSections) AppHeader(destination)
                    when (destination) {
                        BERTDestination.HOME -> HomeScreen(activityState, now, activityRefreshing, refreshActivity) { destination = it }
                        BERTDestination.EXPLORE -> ExploreScreen()
                        BERTDestination.CREATE -> CreateScreen(createTab) { createTab = it }
                        BERTDestination.TOOLS -> ToolsScreen(state, history, position, now, refreshing, toolsTab,
                            refresh = refresh, savePosition = savePosition)
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
        Text("BERT · v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
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
    val rowModifier = if (singleRow) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
    FlowRow(rowModifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxLines = if (singleRow) 1 else Int.MAX_VALUE) {
        tabs.forEach { tab ->
            Surface(color = if (tab == selected) PanelStrong else Navy, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (tab == selected) AccentText else Muted.copy(alpha = 0.35f))) {
                Box(Modifier.selectable(tab == selected, onClick = { select(tab) }, role = Role.Tab)
                    .heightIn(min = 48.dp).padding(horizontal = horizontalPadding, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(tab, color = if (tab == selected) Cream else Muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(destination: BERTDestination) {
    if (destination == BERTDestination.CREATE || destination == BERTDestination.TOOLS) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.bert_token), null, Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)))
            Text(destination.label.uppercase(), color = Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.semantics { heading() })
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.bert_token), "Bertram the Pomeranian", Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
        Column {
            Text("BERT", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(if (destination == BERTDestination.HOME) "GOOD DOG. BIG WORLD." else destination.label.uppercase(), color = Muted, fontSize = 11.sp, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun BERTNavigation(destination: BERTDestination, select: (BERTDestination) -> Unit) {
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
