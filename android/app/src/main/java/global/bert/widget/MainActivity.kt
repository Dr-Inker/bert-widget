package global.bert.widget

import android.content.Context
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import global.bert.widget.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Create makes things; Tools holds the market and the app's own settings. "Style" (widgets, colours, wallpapers) is
 * Android's own word for that ("Wallpaper & style"), and short enough that four Tools sections fit across 360dp.
 */
internal val CREATE_TABS = listOf("Art", "Saved")
internal val TOOLS_TABS = listOf("Market", "Holdings", "Alerts", "Style")

internal enum class BERTDestination(val label: String, val icon: BERTSymbol) {
    HOME("Home", BERTSymbol.HOME), PLAY("Play", BERTSymbol.PLAY), CREATE("Create", BERTSymbol.CREATE), TOOLS("Tools", BERTSymbol.TOOLS)
}

class MainActivity : ComponentActivity() {
    // A widget or notification tap names a destination; a restored activity keeps where the person was.
    private var entry by mutableStateOf<BERTEntry?>(null)
    // Outlives a rotation or theme change, so the screen keeps its data rather than fetching it all again.
    private val live by lazy { ViewModelProvider(this, liveModelFactory(applicationContext))[BERTLiveModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fetches while the app is open reach the placed widgets once, when it leaves the screen (critique 2026-11-12 ENG-4).
        lifecycle.addObserver(live.widgets)
        // Reopening from Recents replays the original intent (API 26-30); that is not a fresh tap.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) entry = BERTEntry.from(intent)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(0), navigationBarStyle = SystemBarStyle.dark(0))
        setContent {
            BERTTheme { BERTScreen(lifecycle, live, entry) { entry = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entry = BERTEntry.from(intent)
    }

    internal companion object {
        /** Where the live data comes from; a test swaps in repositories with a fake network. */
        var liveModelFactory: (Context) -> ViewModelProvider.Factory = BERTLiveModel::factory
    }
}

@Composable
private fun BERTScreen(lifecycle: Lifecycle, live: BERTLiveModel, entry: BERTEntry?, consumeEntry: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch(Dispatchers.IO) { BERTHomeVisits(context.applicationContext).recordOpen() } // "Day N with Bert"
            live.keepFresh()
        }
    }
    val now = live.now
    val chartHistory = remember(live.market, live.history, now) { BERTPriceHistory.combine(live.market, live.history, now) }
    val update = remember(live.update, live.updateHiddenOnHome) { live.update?.let { AppUpdate(it, live.updateHiddenOnHome) { live.hideUpdate() } } }
    CompositionLocalProvider(LocalWidgetPush provides { live.widgets.pushNow() }, LocalAppUpdate provides update) {
        BERTApp(live.quote, live.activity, chartHistory, live.position, now, live.refreshing, live.activityRefreshing,
            entry = entry, consumeEntry = consumeEntry,
            refresh = { live.refresh() },
            refreshActivity = { live.refreshActivity() },
            savePosition = { live.savePosition(it) },
        )
    }
}

/** Re-renders the placed widgets after a person's own change; the open app provides it, previews and tests leave it a no-op. */
internal val LocalWidgetPush = staticCompositionLocalOf<() -> Unit> { {} }

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
    // The checklist's widget step asks Style once to bring its widgets to the top; Style clears it when it has.
    var showWidgets by rememberSaveable { mutableStateOf(false) }
    // The destination You sent the person to (Tools for Style or Holdings, Create for Saved) and the one You was opened
    // over: while they stay there, Back returns to You rather than past it. A bottom-bar tab or a widget tap ends it.
    var youSent by rememberSaveable { mutableStateOf<BERTDestination?>(null) }
    var youFrom by rememberSaveable { mutableStateOf(BERTDestination.HOME) }
    val context = LocalContext.current
    // 0.15, the visit streak counts days the app was opened, from any way in (Home, a widget, an alert, a shortcut):
    // here, each time the app comes to the front, once per date (VisitStreak.kt). Next to recordOpen() in spirit; here
    // rather than in BERTScreen so the app's own date (`now`) is the one the garden shows, and tests reach it.
    // Keyed on the date as well: an app left in Recents overnight comes back holding yesterday's clock until the first
    // tick, and midnight can pass with the app open. Either way the new date counts as soon as the app knows it.
    val appLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val openedOn = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    LaunchedEffect(appLifecycle, openedOn) {
        appLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { PlaceDiscovery(context.applicationContext).visit(openedOn) }
    }
    LaunchedEffect(entry) {
        when (entry) {
            BERTEntry.MARKET -> { destination = BERTDestination.TOOLS; toolsTab = "Market" }
            BERTEntry.HOME -> destination = BERTDestination.HOME
            BERTEntry.CREATE -> { destination = BERTDestination.CREATE; createTab = "Art" }
            BERTEntry.GAME -> openLostTrail(context) // once: consumeEntry below clears the entry
            null -> return@LaunchedEffect
        }
        youSent = null
        consumeEntry()
    }
    val savedScreens = rememberSaveableStateHolder()
    // My Bert: the holder's chosen NFT, shared with Home, Create and Tools › Style; its screen opens over the current one.
    val myBertStore = remember { MyBertStore(context.applicationContext) }
    var chosenBert by remember { mutableStateOf(myBertStore.chosen()) }
    var myBertOpen by rememberSaveable { mutableStateOf(false) }
    val myBert = MyBertHandle(chosenBert, open = { myBertOpen = true }, update = { chosenBert = it })
    // You: the person's own page, opened over the current tab like My Bert. My Bert opens over it, and Back returns here.
    var youOpen by rememberSaveable { mutableStateOf(false) }
    val sendFromYou = { to: BERTDestination -> youFrom = destination; youSent = to; youOpen = false; destination = to }

    BackHandler(destination != BERTDestination.HOME) { destination = BERTDestination.HOME }
    BackHandler(youSent != null && youSent == destination && !youOpen) { youSent = null; destination = youFrom; youOpen = true }
    BackHandler(youOpen) { youOpen = false }
    BackHandler(myBertOpen) { myBertOpen = false }

    CompositionLocalProvider(LocalMyBert provides myBert, LocalOpenYou provides { youOpen = true }) {
        Scaffold(
            containerColor = Navy,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = { BERTNavigation(destination) { myBertOpen = false; youOpen = false; youSent = null; destination = it } },
        ) { padding ->
            if (myBertOpen) {
                MyBertScreen(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), close = { myBertOpen = false })
            } else if (youOpen) {
                YouScreen(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), holdingsSet = position.amount != null,
                    close = { youOpen = false },
                    openStyle = { toolsTab = "Style"; sendFromYou(BERTDestination.TOOLS) },
                    openHoldings = { toolsTab = "Holdings"; sendFromYou(BERTDestination.TOOLS) },
                    openSaved = { createTab = "Saved"; sendFromYou(BERTDestination.CREATE) })
            } else {
                // A short crossfade between destinations, and again between sections, so nothing hard-cuts.
                // Home's scene runs under the status bar (it is the top of the screen); every other screen keeps the inset.
                val dir = LocalLayoutDirection.current
                // The top inset goes on each crossfading child (by the screen it shows), so the outgoing screen never
                // jumps by the status bar's height mid-fade.
                val body = PaddingValues(start = padding.calculateStartPadding(dir), end = padding.calculateEndPadding(dir),
                    bottom = padding.calculateBottomPadding())
                Crossfade(destination, Modifier.fillMaxSize().padding(body).consumeWindowInsets(padding).imePadding(),
                    animationSpec = tween(180), label = "destination") { shown ->
                    val hasSections = shown == BERTDestination.CREATE || shown == BERTDestination.TOOLS
                    val largeText = LocalDensity.current.fontScale > 1.3f
                    Column(Modifier.fillMaxSize().padding(top = if (shown == BERTDestination.HOME) 0.dp else padding.calculateTopPadding())) {
                        if (hasSections) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // The selected bottom destination already names the section. At larger
                                // text sizes, prioritize its content over a repeated pinned heading.
                                if (!largeText) AppHeader(shown)
                                // Tools has four sections: narrower pills keep all four on screen at 360dp.
                                val tabPadding = if (largeText) 12.dp else if (shown == BERTDestination.TOOLS) 10.dp else 16.dp
                                if (shown == BERTDestination.CREATE) SectionTabs(CREATE_TABS, createTab, tabPadding, singleRow = true) { createTab = it }
                                else SectionTabs(TOOLS_TABS, toolsTab, tabPadding, singleRow = true) { toolsTab = it }
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
                                        GettingStartedStep.WIDGET -> { showWidgets = true; destination = BERTDestination.TOOLS; toolsTab = "Style" }
                                        GettingStartedStep.GAME -> openLostTrail(context)
                                        GettingStartedStep.CARD -> { destination = BERTDestination.CREATE; createTab = "Art" }
                                        GettingStartedStep.ALERT -> { destination = BERTDestination.TOOLS; toolsTab = "Alerts" }
                                    }
                                }
                            } else BERTScreenContent(body) {
                                if (!hasSections) AppHeader(shown)
                                when (shown) {
                                    BERTDestination.PLAY -> PlayScreen(activityState, now)
                                    BERTDestination.CREATE -> CreateScreen(key.removePrefix("CREATE-"), seedCaption = cardSeed, consumeSeed = { cardSeed = null }) { createTab = it }
                                    BERTDestination.TOOLS -> ToolsScreen(state, history, position, now, refreshing, key.removePrefix("TOOLS-"),
                                        refresh = refresh, savePosition = savePosition, showWidgets = showWidgets) { showWidgets = false }
                                    BERTDestination.HOME -> Unit
                                }
                            }
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
                        refresh: () -> Unit, savePosition: (BERTPosition) -> Unit,
                        showWidgets: Boolean = false, widgetsShown: () -> Unit = {}) {
    when (tab) {
        "Market" -> MarketScreen(state, history, now, refreshing, refresh)
        "Alerts" -> AlertsScreen(state)
        "Style" -> PersonaliseScreen(showWidgets, widgetsShown)
        else -> HoldingsScreen(position, state, now, savePosition)
    }
}

@Composable
internal fun SectionTabs(tabs: List<String>, selected: String, horizontalPadding: Dp = 16.dp, singleRow: Boolean = false, select: (String) -> Unit) {
    // Pinned section tabs stay one row (scrolling sideways at large text) so they never push content down;
    // option pickers wrap so every choice stays visible.
    val haptics = LocalHapticFeedback.current
    val rowModifier = if (singleRow) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
    // A sideways-scrolling row opens unscrolled; bring the chosen section's pill on screen (e.g. Tools › Style at 2x text).
    val inView = remember(tabs) { tabs.associateWith { BringIntoViewRequester() } }
    if (singleRow) LaunchedEffect(selected) { inView[selected]?.bringIntoView() }
    // One selectable group, so TalkBack reads each pill as "1 of 2" and so on.
    FlowRow(rowModifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxLines = if (singleRow) 1 else Int.MAX_VALUE) {
        tabs.forEach { tab ->
            Surface(color = if (tab == selected) PanelStrong else Navy, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.bringIntoViewRequester(inView.getValue(tab)),
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

/**
 * The official BERT wordmark. BERT Violet uses the brand kit's vector with its white-to-lilac gradient (only ever on
 * dark); Berthalla Navy keeps the white artwork tinted to the text colour.
 */
@Composable
internal fun BERTWordmark(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Cream) {
    if (BERTColors.scheme.brandType) Image(painterResource(R.drawable.bert_wordmark_brand), "BERT", modifier.aspectRatio(467f / 135f))
    else Image(painterResource(R.drawable.bert_wordmark), "BERT", modifier.aspectRatio(640f / 186f),
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
