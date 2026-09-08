package global.bert.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import global.bert.widget.data.*
import global.bert.widget.widget.updateAllBERTWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class BERTDestination(val label: String, val icon: String) {
    HOME("Home", "⌂"), EXPLORE("Explore", "↗"), CREATE("Create", "✦"), TOOLS("Tools", "◎")
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
    val holdingsStore = remember { BERTHoldingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<QuoteState>(repository.load()?.let { QuoteState.Available(it) } ?: QuoteState.Loading) }
    var history by remember { mutableStateOf(historyStore.load()) }
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

    suspend fun refreshActivity() {
        if (activityRefreshing) return
        activityRefreshing = true
        try {
            activityState = ActivityState.Available(activityRepository.refresh())
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
            while (true) { refresh(); delay(60_000) }
        }
    }
    BERTApp(state, activityState, history, position, now, refreshing, activityRefreshing,
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
        bottomBar = {
            NavigationBar(containerColor = Navy, contentColor = Cream) {
                BERTDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Text(item.icon, fontSize = 23.sp) },
                        label = { Text(item.label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Orange, selectedTextColor = Cream,
                            unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = PanelStrong,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val screenKey = when (destination) {
            BERTDestination.TOOLS -> "TOOLS-$toolsTab"
            BERTDestination.CREATE -> "CREATE-$createTab"
            else -> destination.name
        }
        savedScreens.SaveableStateProvider(screenKey) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                AppHeader(destination)
                when (destination) {
                    BERTDestination.HOME -> HomeScreen(activityState, now, activityRefreshing, refreshActivity) { destination = it }
                    BERTDestination.EXPLORE -> ExploreScreen()
                    BERTDestination.CREATE -> CreateScreen(createTab) { createTab = it }
                    BERTDestination.TOOLS -> ToolsScreen(state, history, position, now, refreshing, toolsTab, { toolsTab = it },
                        refresh = refresh,
                        savePosition = savePosition,
                    )
                }
                Text("BERT · v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ToolsScreen(state: QuoteState, history: List<BERTPriceSample>, position: BERTPosition, now: Long,
                        refreshing: Boolean, tab: String, selectTab: (String) -> Unit,
                        refresh: () -> Unit, savePosition: (BERTPosition) -> Unit) {
    SectionTabs(listOf("Market", "Holdings"), tab, selectTab)
    if (tab == "Market") MarketScreen(state, history, now, refreshing, refresh)
    else HoldingsScreen(position, state, now, savePosition)
}

@Composable
internal fun SectionTabs(tabs: List<String>, selected: String, select: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(selected = tab == selected, onClick = { select(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size)) { Text(tab) }
        }
    }
}

@Composable
private fun AppHeader(destination: BERTDestination) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.bert_token), "Bertram the Pomeranian", Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
        Column {
            Text("BERT", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(if (destination == BERTDestination.HOME) "GOOD DOG. BIG WORLD." else destination.label.uppercase(), color = Muted, fontSize = 11.sp, letterSpacing = 0.8.sp)
        }
    }
}
