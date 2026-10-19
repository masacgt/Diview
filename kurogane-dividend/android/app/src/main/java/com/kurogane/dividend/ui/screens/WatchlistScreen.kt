package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.DiviewSnackbarHost
import com.kurogane.dividend.ui.components.DiviewSnackbarTone
import com.kurogane.dividend.ui.components.showDiviewSnackbar
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale
import java.util.WeakHashMap
import com.kurogane.dividend.ui.components.rememberNotificationAccess
import com.kurogane.dividend.notifications.NotificationKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class WatchlistDerivedMetrics(
    val currentYields: Map<String, Double>,
    val averageYield: Double,
    val currentUnitInvestment: Double,
    val potentialDiv: Double,
    val readyCount: Int,
    val japaneseCount: Int,
    val usCount: Int,
    val badgeWidthCandidates: List<String>
)

private class WatchlistDerivedMetricsCache {
    private class StockMetrics(
        private val dps: Double,
        private val price: Double,
        private val targetYield: Double,
        private val currencyFactor: Double,
        private val category: String,
        val currentYield: Double,
        val unitInvestment: Double,
        val potentialDiv: Double,
        val isReady: Boolean,
        val japanese: Boolean,
        val us: Boolean,
        val standardBadgeWidthCandidate: String?,
        val dynamicBadgeWidthCandidate: String?
    ) {
        fun matches(stock: WatchlistStock): Boolean =
            dps == stock.dps &&
                price == stock.price &&
                targetYield == stock.targetYield &&
                currencyFactor == stock.currencyFactor &&
                category == stock.category
    }

    private var sourceStocks: List<WatchlistStock>? = null
    private var sourceResources: android.content.res.Resources? = null
    private var cachedMetrics: WatchlistDerivedMetrics? = null
    private var stockMetricsByCode: Map<String, StockMetrics> = emptyMap()

    private fun createStockMetrics(
        stock: WatchlistStock,
        resources: android.content.res.Resources
    ): StockMetrics {
        val currentYield = com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
        var standardBadgeWidthCandidate: String? = null
        var dynamicBadgeWidthCandidate: String? = null
        when {
            currentYield >= stock.targetYield ->
                standardBadgeWidthCandidate = resources.getString(R.string.watchlist_badge)
            currentYield >= stock.targetYield - 0.25 ->
                standardBadgeWidthCandidate = resources.getString(R.string.watchlist_badge_3)
            currentYield >= 4.0 -> {
                val dynamicBadge = resources.getString(
                    R.string.watchlist_badge_2,
                    String.format(Locale.JAPAN, "%.2f", currentYield)
                )
                dynamicBadgeWidthCandidate = dynamicBadge.map { character ->
                    if (character in '0'..'9') '8' else character
                }.joinToString("")
            }
            else -> standardBadgeWidthCandidate = resources.getString(R.string.watchlist_badge_4)
        }
        val unitShares = if (stock.category == "us") 1 else 100
        return StockMetrics(
            dps = stock.dps,
            price = stock.price,
            targetYield = stock.targetYield,
            currencyFactor = stock.currencyFactor,
            category = stock.category,
            currentYield = currentYield,
            unitInvestment = stock.price * unitShares * stock.currencyFactor,
            potentialDiv = stock.dps * unitShares * stock.currencyFactor,
            isReady = currentYield >= stock.targetYield,
            japanese = stock.category == "jp",
            us = stock.category == "us",
            standardBadgeWidthCandidate = standardBadgeWidthCandidate,
            dynamicBadgeWidthCandidate = dynamicBadgeWidthCandidate
        )
    }

    @Synchronized
    fun get(
        stocks: List<WatchlistStock>,
        resources: android.content.res.Resources
    ): WatchlistDerivedMetrics {
        if (sourceStocks === stocks && sourceResources === resources) {
            return checkNotNull(cachedMetrics)
        }

        val nextStockMetrics = HashMap<String, StockMetrics>(stocks.size)
        val currentYields = HashMap<String, Double>(stocks.size)
        val badgeTexts = linkedSetOf<String>()
        var yieldTotal = 0.0
        var currentUnitInvestment = 0.0
        var potentialDiv = 0.0
        var readyCount = 0
        var japaneseCount = 0
        var usCount = 0
        var widestDynamicBadge: String? = null
        var widestDynamicLength = -1
        var widestDynamicYield = Double.NEGATIVE_INFINITY
        val canReuseStockMetrics = sourceResources === resources

        stocks.forEach { stock ->
            val stockMetrics = stockMetricsByCode[stock.code]
                ?.takeIf { canReuseStockMetrics && it.matches(stock) }
                ?: createStockMetrics(stock, resources)
            nextStockMetrics[stock.code] = stockMetrics
            currentYields[stock.code] = stockMetrics.currentYield
            yieldTotal += stockMetrics.currentYield
            currentUnitInvestment += stockMetrics.unitInvestment
            potentialDiv += stockMetrics.potentialDiv
            if (stockMetrics.isReady) readyCount++
            if (stockMetrics.japanese) japaneseCount++
            if (stockMetrics.us) usCount++
            stockMetrics.standardBadgeWidthCandidate?.let(badgeTexts::add)
            stockMetrics.dynamicBadgeWidthCandidate?.let { candidate ->
                if (candidate.length > widestDynamicLength ||
                    (candidate.length == widestDynamicLength && stockMetrics.currentYield > widestDynamicYield)
                ) {
                    widestDynamicBadge = candidate
                    widestDynamicLength = candidate.length
                    widestDynamicYield = stockMetrics.currentYield
                }
            }
        }
        widestDynamicBadge?.let(badgeTexts::add)

        val metrics = WatchlistDerivedMetrics(
            currentYields = currentYields,
            averageYield = if (stocks.isEmpty()) 0.0 else yieldTotal / stocks.size,
            currentUnitInvestment = currentUnitInvestment,
            potentialDiv = potentialDiv,
            readyCount = readyCount,
            japaneseCount = japaneseCount,
            usCount = usCount,
            badgeWidthCandidates = badgeTexts.sorted()
        )
        sourceStocks = stocks
        sourceResources = resources
        cachedMetrics = metrics
        stockMetricsByCode = nextStockMetrics
        return metrics
    }
}

private data class WatchlistMetricsSnapshot(
    val stockCodes: List<String>,
    val metrics: WatchlistDerivedMetrics
)

private data class WatchlistProjectionSnapshot(
    val stocks: List<WatchlistStock>,
    val projection: com.kurogane.dividend.model.WatchlistProjection
)

private data class WatchlistListSnapshot(
    val sourceStocks: List<WatchlistStock>,
    val searchQuery: String,
    val filterState: String,
    val appliedOrderCodes: List<String>?,
    val sortCriteria: String,
    val sortDirection: String,
    val metrics: WatchlistDerivedMetrics,
    val filteredStocks: List<WatchlistStock>,
    val sortedStocks: List<WatchlistStock>
) {
    fun matches(
        stocks: List<WatchlistStock>,
        query: String,
        filter: String,
        orderCodes: List<String>?,
        criteria: String,
        direction: String,
        currentMetrics: WatchlistDerivedMetrics
    ): Boolean {
        return sourceStocks === stocks &&
            searchQuery == query &&
            filterState == filter &&
            appliedOrderCodes == orderCodes &&
            sortCriteria == criteria &&
            sortDirection == direction &&
            metrics === currentMetrics
    }
}

private val EmptyWatchlistDerivedMetrics = WatchlistDerivedMetrics(
    currentYields = emptyMap(),
    averageYield = 0.0,
    currentUnitInvestment = 0.0,
    potentialDiv = 0.0,
    readyCount = 0,
    japaneseCount = 0,
    usCount = 0,
    badgeWidthCandidates = emptyList()
)

private object WatchlistDerivedMetricsCacheStore {
    private val caches = WeakHashMap<DividendViewModel, WatchlistDerivedMetricsCache>()

    @Synchronized
    fun forViewModel(viewModel: DividendViewModel): WatchlistDerivedMetricsCache =
        caches.getOrPut(viewModel) { WatchlistDerivedMetricsCache() }
}

private fun sortWatchlist(
    stocks: List<WatchlistStock>,
    criteria: String,
    direction: String,
    currentYields: Map<String, Double>
): List<WatchlistStock> = stocks.sortedWith { a, b ->
    val yieldA = currentYields[a.code]
        ?: com.kurogane.dividend.model.PortfolioMath.yieldPercent(a.dps, a.price)
    val yieldB = currentYields[b.code]
        ?: com.kurogane.dividend.model.PortfolioMath.yieldPercent(b.dps, b.price)
    val rateA = yieldA / a.targetYield
    val rateB = yieldB / b.targetYield

    val comparison = when (criteria) {
        "ach" -> rateA.compareTo(rateB)
        "yield" -> yieldA.compareTo(yieldB)
        "price" -> a.price.compareTo(b.price)
        else -> a.code.compareTo(b.code)
    }

    if (direction == "desc") -comparison else comparison
}

private fun applyWatchlistOrder(
    stocks: List<WatchlistStock>,
    orderCodes: List<String>?
): List<WatchlistStock> {
    if (orderCodes == null) return stocks

    val stocksByCode = stocks.associateBy { it.code }
    val orderedCodeSet = orderCodes.toHashSet()
    return buildList(stocks.size) {
        orderCodes.forEach { code -> stocksByCode[code]?.let(::add) }
        stocks.forEach { stock -> if (stock.code !in orderedCodeSet) add(stock) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {},
    scrollToTopRequest: Int = 0
) {
    val feedbackView = LocalView.current
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val incomeAdviceCatalog by viewModel.incomeAdviceCatalog.collectAsStateWithLifecycle()
    val incomeAdviceFetchFailed by viewModel.incomeAdviceFetchFailed.collectAsStateWithLifecycle()
    val savedSortSelection by viewModel.watchlistSortSelection.collectAsStateWithLifecycle()
    val localDataRestored by viewModel.localDataRestored.collectAsStateWithLifecycle()
    val watchlistMetricsCache = WatchlistDerivedMetricsCacheStore.forViewModel(viewModel)
    var watchlistMetricsSnapshot by remember(viewModel) { mutableStateOf<WatchlistMetricsSnapshot?>(null) }
    LaunchedEffect(watchlist, resources) {
        val sourceStocks = watchlist
        val metrics = withContext(Dispatchers.Default) {
            watchlistMetricsCache.get(sourceStocks, resources)
        }
        if (viewModel.watchlist.value === sourceStocks) {
            watchlistMetricsSnapshot = WatchlistMetricsSnapshot(
                stockCodes = sourceStocks.map { it.code },
                metrics = metrics
            )
        }
    }
    val watchlistCodes = remember(watchlist) { watchlist.map { it.code } }
    val displayedWatchlistMetrics = watchlistMetricsSnapshot
        ?.takeIf { it.stockCodes == watchlistCodes }
    val hasWatchlistMetrics = displayedWatchlistMetrics != null
    val watchlistMetrics = displayedWatchlistMetrics
        ?.metrics
        ?: EmptyWatchlistDerivedMetrics
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    var alertSnackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun showAlertSnackbar(message: String) {
        alertSnackbarJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        alertSnackbarJob = screenScope.launch {
            snackbarHostState.showDiviewSnackbar(message, tone = DiviewSnackbarTone.Success)
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationAccess = rememberNotificationAccess { action ->
        val code = action.substringAfter(':', "")
        if (viewModel.watchlist.value.any { it.code == code && !it.alertEnabled }) {
            viewModel.toggleWatchlistStock(code)
            showAlertSnackbar(resources.getString(R.string.watchlist_alert_registered))
        }
        viewModel.setNotificationEnabled(NotificationKind.PRICE, true)
    }
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    var revealedWatchlistCode by remember { mutableStateOf<String?>(null) }
    var exitingWatchlistStocks by remember { mutableStateOf<List<WatchlistStock>>(emptyList()) }
    var exitingNextWatchlistCodes by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var showAddWatchlist by remember { mutableStateOf(false) }
    var isAddingWatchlist by remember { mutableStateOf(false) }

    fun addWatchlist(code: String, onAdded: () -> Unit = {}) {
        if (isAddingWatchlist) return
        isAddingWatchlist = true
        screenScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            try {
                val result = com.kurogane.dividend.data.runCatchingCancellable { viewModel.registerWatchlistStock(code) }
                if (result.getOrDefault(false)) {
                    showAddWatchlist = false
                    onAdded()
                    android.widget.Toast.makeText(context, R.string.watchlist_added, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, result.exceptionOrNull()?.let {
                        com.kurogane.dividend.ui.networkErrorMessage(resources, it)
                    } ?: resources.getString(R.string.watchlist_add_failed), android.widget.Toast.LENGTH_LONG).show()
                }
            } finally { isAddingWatchlist = false }
        }
    }

    com.kurogane.dividend.ui.components.SearchSheet(
        visible = showAddWatchlist,
        onDismiss = { if (!isAddingWatchlist) showAddWatchlist = false },
        onSelectStock = { addWatchlist(it) },
        onSearch = viewModel::searchCloudflareSecurities,
        onLoadQuotes = viewModel::loadCloudflareSearchQuotes
    )
    val deleteActionWidth = 112.dp
    val deleteActionVisibleWidth = 80.dp
    val deleteActionVisibleWidthPx = with(LocalDensity.current) { deleteActionVisibleWidth.toPx() }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.watchlistScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.watchlistScrollOffset
    )
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.scrollToTopIn200ms()
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.watchlistScrollIndex = index
                viewModel.watchlistScrollOffset = offset
            }
    }

    // Dynamic Watchlist Calculations
    val averageYield = watchlistMetrics.averageYield
    val currentUnitInvestment = watchlistMetrics.currentUnitInvestment
    val potentialDiv = watchlistMetrics.potentialDiv

    fun yen(value: Double) = "¥${currencyFormat.format(value.toLong())}"
    fun percent(value: Double) = String.format(Locale.JAPAN, "%.1f%%", value)
    var filterState by remember { mutableStateOf("all") } // "all", "jp", "us", "ready"
    var showProjectionInfo by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showSortInfo by remember { mutableStateOf(false) }
    val sortCriteria = savedSortSelection?.criteria ?: "ach"
    val sortDirection = savedSortSelection?.direction ?: "desc"
    var draftSortCriteria by remember { mutableStateOf("ach") }
    var draftSortDirection by remember { mutableStateOf("desc") }
    var appliedOrderCodes by rememberSaveable(viewModel) { mutableStateOf<List<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Bottom Sheet States for ex-dividend matrix and payout calendar
    var showMatrixBottomSheet by remember { mutableStateOf(false) }
    val matrixSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showGraphBottomSheet by remember { mutableStateOf(false) }
    var selectedProjectionQuarterIndex by remember { mutableStateOf<Int?>(null) }
    var showCalendarInfo by remember { mutableStateOf(false) }
    var showHeatmapInfo by remember { mutableStateOf(false) }
    val graphSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var showPayoutRiskBottomSheet by remember { mutableStateOf(false) }
    val payoutRiskSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var watchlistProjectionSnapshot by remember(viewModel) { mutableStateOf<WatchlistProjectionSnapshot?>(null) }
    val shouldPrepareProjection = showProjectionInfo || showMatrixBottomSheet || showGraphBottomSheet || showPayoutRiskBottomSheet
    LaunchedEffect(watchlist, shouldPrepareProjection) {
        if (!shouldPrepareProjection) return@LaunchedEffect
        val sourceStocks = watchlist
        val projection = withContext(Dispatchers.Default) {
            viewModel.watchlistProjectionFor(sourceStocks)
        }
        if (viewModel.watchlist.value === sourceStocks) {
            watchlistProjectionSnapshot = WatchlistProjectionSnapshot(sourceStocks, projection)
        }
    }
    val hasWatchlistProjection = watchlistProjectionSnapshot?.stocks === watchlist
    val projection = watchlistProjectionSnapshot
        ?.takeIf { it.stocks === watchlist }
        ?.projection
        ?: remember { com.kurogane.dividend.model.WatchlistProjection(emptyList()) }
    val projectionNote = resources.getString(R.string.watchlist_projection_note, projection.unallocatedCount) +
        if (projection.missingExchangeRate) resources.getString(R.string.watchlist_projection_fx_missing) else ""
    val incomeAdviceCandidates = remember(incomeAdviceCatalog, projection.emptyMonths, watchlistCodes) {
        com.kurogane.dividend.model.rankIncomeAdviceCandidates(
            candidates = incomeAdviceCatalog?.candidates.orEmpty(),
            emptyMonths = projection.emptyMonths,
            watchlistCodes = watchlistCodes.toSet()
        )
    }

    // Preserve the current Watchlist order unless the user has selected a sort.
    // Wait for local restoration so seeded fallback cards cannot determine it.
    val appliedCodes = if (savedSortSelection == null) null else appliedOrderCodes
    LaunchedEffect(watchlistCodes, localDataRestored, savedSortSelection, appliedCodes) {
        if (!localDataRestored) return@LaunchedEffect
        val sourceStocks = watchlist
        val existingOrder = appliedCodes
        val selection = savedSortSelection
        val nextOrder = withContext(Dispatchers.Default) {
            when {
                selection == null || sourceStocks.isEmpty() -> null
                existingOrder == null -> sortWatchlist(
                    sourceStocks,
                    selection.criteria,
                    selection.direction,
                    emptyMap()
                ).map { it.code }
                else -> {
                    val currentCodes = sourceStocks.mapTo(mutableSetOf()) { it.code }
                    existingOrder.filter { it in currentCodes } +
                        sourceStocks.map { it.code }.filterNot { it in existingOrder }
                }
            }
        }
        if (viewModel.watchlist.value === sourceStocks) appliedOrderCodes = nextOrder
    }

    val effectiveOrderCodes = remember(watchlist, appliedCodes, savedSortSelection) {
        appliedCodes ?: savedSortSelection?.let { selection ->
            sortWatchlist(watchlist, selection.criteria, selection.direction, emptyMap()).map { it.code }
        }
    }
    val immediateFilteredStocks = remember(watchlist, searchQuery, filterState, watchlistMetrics) {
        filterWatchlistStocks(
            stocks = watchlist,
            query = searchQuery,
            filter = filterState,
            currentYields = watchlistMetrics.currentYields
        )
    }
    val immediateSortedStocks = remember(immediateFilteredStocks, effectiveOrderCodes) {
        applyWatchlistOrder(immediateFilteredStocks, effectiveOrderCodes)
    }
    var watchlistListSnapshot by remember(viewModel) { mutableStateOf<WatchlistListSnapshot?>(null) }
    LaunchedEffect(watchlist, searchQuery, filterState, effectiveOrderCodes, sortCriteria, sortDirection, watchlistMetrics) {
        val sourceStocks = watchlist
        val sourceMetrics = watchlistMetrics
        val sourceFilteredStocks = immediateFilteredStocks
        val query = searchQuery
        val filter = filterState
        val orderCodes = effectiveOrderCodes
        val criteria = sortCriteria
        val direction = sortDirection
        val prepared = withContext(Dispatchers.Default) {
            val filtered = sourceFilteredStocks
            val sorted = applyWatchlistOrder(filtered, orderCodes)
            WatchlistListSnapshot(
                sourceStocks = sourceStocks,
                searchQuery = query,
                filterState = filter,
                appliedOrderCodes = orderCodes,
                sortCriteria = criteria,
                sortDirection = direction,
                metrics = sourceMetrics,
                filteredStocks = filtered,
                sortedStocks = sorted
            )
        }
        if (viewModel.watchlist.value === sourceStocks) watchlistListSnapshot = prepared
    }
    val currentListSnapshot = watchlistListSnapshot?.takeIf {
        it.matches(watchlist, searchQuery, filterState, effectiveOrderCodes, sortCriteria, sortDirection, watchlistMetrics)
    }
    val filteredList = currentListSnapshot?.filteredStocks ?: immediateFilteredStocks
    val sortedList = currentListSnapshot?.sortedStocks ?: immediateSortedStocks
    val activeWatchlistStocks = remember(sortedList, exitingWatchlistStocks) {
        sortedList.filterNot { stock -> exitingWatchlistStocks.any { it.code == stock.code } }
    }
    val animatedWatchlistStocks = remember(activeWatchlistStocks, exitingWatchlistStocks, exitingNextWatchlistCodes) {
        buildList {
            val insertions = exitingWatchlistStocks.associate { exiting ->
                exiting.code to exitingNextWatchlistCodes[exiting.code]
            }
            var insertedNextCodes = emptySet<String>()
            activeWatchlistStocks.forEach { candidate ->
                val exiting = exitingWatchlistStocks.firstOrNull { insertions[it.code] == candidate.code }
                if (exiting != null) {
                    add(exiting)
                    add(candidate)
                    insertedNextCodes = insertedNextCodes + candidate.code
                } else if (candidate.code !in insertedNextCodes) {
                    add(candidate)
                }
            }
            addAll(exitingWatchlistStocks.filter { exiting ->
                activeWatchlistStocks.none { candidate -> insertions[exiting.code] == candidate.code }
            })
        }
    }

    fun deleteWatchlistStock(stock: WatchlistStock) {
        revealedWatchlistCode = null
        val indexBeforeDeletion = sortedList.indexOfFirst { it.code == stock.code }
        val nextStockCode = sortedList.getOrNull(indexBeforeDeletion + 1)?.code
        val removed = viewModel.removeWatchlistStock(stock.code) ?: return
        exitingNextWatchlistCodes = exitingNextWatchlistCodes + (removed.code to nextStockCode)
        exitingWatchlistStocks = exitingWatchlistStocks + removed
        screenScope.launch {
            kotlinx.coroutines.delay(420)
            exitingWatchlistStocks = exitingWatchlistStocks.filterNot { it.code == removed.code }
            exitingNextWatchlistCodes = exitingNextWatchlistCodes - removed.code
        }
        screenScope.launch {
            kotlinx.coroutines.delay(900)
            val result = snackbarHostState.showDiviewSnackbar(
                message = "${removed.name}をWatchlistから削除しました",
                actionLabel = "元に戻す",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                tone = DiviewSnackbarTone.Success
            )
            if (result == SnackbarResult.ActionPerformed && viewModel.restoreWatchlistStock(removed)) {
                appliedOrderCodes?.let { currentOrder ->
                    val restoredOrder = currentOrder.filterNot { it == removed.code }.toMutableList()
                    val insertionIndex = nextStockCode
                        ?.let(restoredOrder::indexOf)
                        ?.takeIf { it >= 0 }
                        ?: restoredOrder.size
                    restoredOrder.add(insertionIndex, removed.code)
                    appliedOrderCodes = restoredOrder
                }
            }
        }
    }

    val badgeTextFor: (WatchlistStock) -> String = { stock ->
        val currentYield = watchlistMetrics.currentYields[stock.code]
            ?: com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
        when {
            currentYield >= stock.targetYield -> resources.getString(R.string.watchlist_badge)
            currentYield >= stock.targetYield - 0.25 -> resources.getString(R.string.watchlist_badge_3)
            currentYield >= 4.0 -> resources.getString(
                R.string.watchlist_badge_2,
                String.format(Locale.JAPAN, "%.2f", currentYield)
            )
            else -> resources.getString(R.string.watchlist_badge_4)
        }
    }
    val badgeTextMeasurer = rememberTextMeasurer()
    val badgeTextStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
    val badgeWidthCandidates = watchlistMetrics.badgeWidthCandidates
    val widestBadgeTextPx = remember(badgeWidthCandidates, resources, badgeTextMeasurer, badgeTextStyle) {
        badgeWidthCandidates.maxOfOrNull { text ->
            badgeTextMeasurer.measure(AnnotatedString(text), style = badgeTextStyle, maxLines = 1).size.width
        } ?: 0
    }
    val badgeWidth = with(LocalDensity.current) { widestBadgeTextPx.toDp() } + 34.dp

    val readyCount = watchlistMetrics.readyCount

    Scaffold(
        containerColor = BackgroundDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { DiviewSnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Card
            item {
                val holdings by viewModel.holdings.collectAsStateWithLifecycle()
                val heldCodes = remember(holdings.map { it.code }) { holdings.mapTo(mutableSetOf()) { it.code } }
                val heldCount = remember(watchlistCodes, heldCodes) {
                    watchlistCodes.count { it in heldCodes }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerHigh,
                                border = BorderStroke(0.5.dp, BorderBlue)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(
                                            R.string.watchlist_text,
                                            if (hasWatchlistMetrics) readyCount.toString() else "—"
                                        ),
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.dense
                                    )
                                    Text(stringResource(R.string.watchlist_text_2 , watchlist.size), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.watchlist_text_4), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.annual_dividend_text_70), fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = OnSurface)
                                Text(
                                    if (hasWatchlistMetrics) currencyFormat.format(currentUnitInvestment.toLong()) else "—",
                                    fontSize = AppTextSize.metric,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontFamily = AppNumericFontFamily
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(stringResource(R.string.watchlist_text_5), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (hasWatchlistMetrics) String.format(java.util.Locale.JAPAN, "%.2f", averageYield) else "—",
                                            fontSize = AppTextSize.metric,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryEmerald,
                                            fontFamily = AppNumericFontFamily
                                        )
                                        Text(stringResource(R.string.annual_dividend_text_4), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                    }
                                    Text(stringResource(R.string.watchlist_text_6), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.watchlist_text_7), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Box {
                                            IconButton(
                                                onClick = { showProjectionInfo = true },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = stringResource(R.string.watchlist_projection_info_content_description),
                                                    tint = OnSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showProjectionInfo,
                                                onDismissRequest = { showProjectionInfo = false },
                                                modifier = Modifier.widthIn(max = 300.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                containerColor = SurfaceContainerLow,
                                                tonalElevation = 0.dp,
                                                shadowElevation = 12.dp
                                            ) {
                                                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = PrimaryEmerald.copy(alpha = 0.14f),
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.Info,
                                                                    contentDescription = null,
                                                                    tint = PrimaryEmerald,
                                                                    modifier = Modifier.size(17.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.width(10.dp))
                                                        Text(
                                                            stringResource(R.string.watchlist_projection_info_title),
                                                            color = OnSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = AppTextSize.body
                                                        )
                                                    }
                                                    Spacer(Modifier.height(12.dp))
                                                    HorizontalDivider(color = BorderColor, thickness = 0.75.dp)
                                                    Spacer(Modifier.height(12.dp))
                                                    Text(
                                                        stringResource(R.string.watchlist_text_9, heldCount),
                                                        color = OnSurfaceVariant,
                                                        fontSize = AppTextSize.caption,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        projectionNote,
                                                        color = OnSurfaceVariant,
                                                        fontSize = AppTextSize.caption,
                                                        lineHeight = 20.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.annual_dividend_text_70), fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text(
                                            if (hasWatchlistMetrics) currencyFormat.format(potentialDiv.toLong()) else "—",
                                            fontSize = AppTextSize.metric,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface,
                                            fontFamily = AppNumericFontFamily
                                        )
                                        Text(stringResource(R.string.watchlist_text_8), fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Batch Simulation of Watchlist Stocks Card (Image 1)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val batchSimulationTitle = stringResource(R.string.watchlist_text_10)
                            val compactBatchSimulationTitle = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.7f
                            Text(
                                text = if (compactBatchSimulationTitle) "一括シミュレーション" else batchSimulationTitle,
                                modifier = if (compactBatchSimulationTitle) {
                                    Modifier.semantics { contentDescription = batchSimulationTitle }
                                } else Modifier,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body
                                ),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showMatrixBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.watchlist_bulk_ex_dividend_month_button), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showGraphBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.BarChart, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.watchlist_bulk_monthly_estimate_button), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showPayoutRiskBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.watchlist_payout_diagnosis_button), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Filters & Sorting Panel (Matching Web layout exactly)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            Pair("all", resources.getString(R.string.watchlist_label_9, (watchlist.size).toString())),
                            Pair("jp", resources.getString(R.string.watchlist_label_10, if (hasWatchlistMetrics) watchlistMetrics.japaneseCount.toString() else "—")),
                            Pair("us", resources.getString(R.string.watchlist_label_11, if (hasWatchlistMetrics) watchlistMetrics.usCount.toString() else "—")),
                            Pair("ready", resources.getString(R.string.watchlist_label_12, if (hasWatchlistMetrics) readyCount.toString() else "—"))
                        )
                        filters.forEach { (id, label) ->
                            val isSelected = filterState == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) PrimaryEmerald else SurfaceContainerLow)
                                    .clickable { filterState = id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) OnPrimary else SoftGray,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Sorting Button and Add Holding Button Row
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentLabel = when (sortCriteria) {
                            "ach" -> resources.getString(R.string.watchlist_label_15)
                            "yield" -> resources.getString(R.string.watchlist_label_16)
                            "price" -> resources.getString(R.string.watchlist_label_17)
                            else -> resources.getString(R.string.watchlist_label_18)
                        }
                        val currentDirLabel = if (sortDirection == "desc") resources.getString(R.string.watchlist_label_19) else resources.getString(R.string.watchlist_label_20)

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .clickable {
                                    draftSortCriteria = sortCriteria
                                    draftSortDirection = sortDirection
                                    showSortBottomSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.watchlist_1_s_2_s , currentLabel, currentDirLabel),
                                color = SoftGray,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = SoftGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Add stock button (matches layout header)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceContainerLow)
                                .border(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .clickable { showAddWatchlist = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.watchlist_text_13),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Search Field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.watchlist_text_14), color = SoftGray, fontSize = AppTextSize.dense) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FocusRingColor,
                        unfocusedBorderColor = ControlOutline,
                        focusedContainerColor = SurfaceContainerLow,
                        unfocusedContainerColor = SurfaceContainerLow,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )
            }

            // Stock Cards
            items(items = animatedWatchlistStocks, key = { it.code }) { stock ->
                val isExiting = exitingWatchlistStocks.any { it.code == stock.code }
                val exitOffset by animateIntOffsetAsState(
                    targetValue = if (isExiting) IntOffset(-listState.layoutInfo.viewportSize.width, 0) else IntOffset.Zero,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                    label = "watchlist-exit-${stock.code}"
                )
                val exitAlpha by animateFloatAsState(
                    targetValue = if (isExiting) 0f else 1f,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                    label = "watchlist-exit-alpha-${stock.code}"
                )
                val currentYield = watchlistMetrics.currentYields[stock.code]
                    ?: com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
                val isReached = currentYield >= stock.targetYield
                val isClose = !isReached && (currentYield >= (stock.targetYield - 0.25))
                val yieldAchievement = (currentYield / stock.targetYield * 100.0).coerceIn(0.0, 100.0)
                val targetPriceGap = kotlin.math.abs(stock.price - stock.targetPrice)
                val targetPriceGapText = when {
                    stock.price > stock.targetPrice -> resources.getString(R.string.watchlist_label_21, (stock.currency).toString(), com.kurogane.dividend.model.formatFinancialAmountDisplay(targetPriceGap))
                    stock.price < stock.targetPrice -> resources.getString(R.string.watchlist_label_22, (stock.currency).toString(), com.kurogane.dividend.model.formatFinancialAmountDisplay(targetPriceGap))
                    else -> resources.getString(R.string.watchlist_label_23)
                }
                
                val dynamicBadge = badgeTextFor(stock)

                val badgeColor = if (isReached) {
                    StatusSuccess
                } else if (isClose) {
                    StatusWarning
                } else if (currentYield >= 4.0) {
                    ChartPurple
                } else {
                    OnSurfaceVariant // Slate
                }

                val swipeOffset = remember(stock.code) { Animatable(0f) }
                LaunchedEffect(revealedWatchlistCode, stock.code) {
                    if (revealedWatchlistCode != stock.code) swipeOffset.animateTo(0f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("watchlist_card_${stock.code}")
                        .animateItemPlacement(animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { exitOffset }
                        .graphicsLayer { alpha = exitAlpha }
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Row(modifier = Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier.width(deleteActionWidth).fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Surface(
                                onClick = { deleteWatchlistStock(stock) },
                                modifier = Modifier.width(deleteActionWidth).fillMaxHeight(),
                                shape = RoundedCornerShape(0.dp),
                                color = ErrorRed
                            ) {
                                Column(
                                    modifier = Modifier.offset(x = (deleteActionWidth - deleteActionVisibleWidth) / 2),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                    Text("削除", color = Color.White, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Card(
                        onClick = { onNavigateToDetail(stock.code) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                            .pointerInput(stock.code, deleteActionVisibleWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        if (revealedWatchlistCode != stock.code) {
                                            revealedWatchlistCode = null
                                            screenScope.launch { swipeOffset.snapTo(0f) }
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        screenScope.launch {
                                            swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(-deleteActionVisibleWidthPx, 0f))
                                        }
                                    },
                                    onDragEnd = {
                                        val shouldReveal = swipeOffset.value <= -deleteActionVisibleWidthPx / 2f
                                        screenScope.launch {
                                            swipeOffset.animateTo(if (shouldReveal) -deleteActionVisibleWidthPx else 0f)
                                            revealedWatchlistCode = if (shouldReveal) stock.code else null
                                        }
                                    },
                                    onDragCancel = {
                                        screenScope.launch { swipeOffset.animateTo(0f) }
                                        if (revealedWatchlistCode == stock.code) revealedWatchlistCode = null
                                    }
                                )
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = if (isReached) {
                            BorderStroke(1.5.dp, PrimaryEmerald)
                        } else {
                            BorderStroke(1.dp, BorderBlue)
                        }
                    ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, BorderBlue),
                            modifier = Modifier.padding(end = 6.dp).width(badgeWidth)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isReached) Icons.Default.Verified else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    dynamicBadge,
                                    color = badgeColor,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Button(
                            onClick = { onOpenAddHolding(stock.code) },
                            modifier = Modifier.widthIn(min = 82.dp).height(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = OnPrimary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = OnPrimary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(stringResource(R.string.watchlist_text_18), fontSize = AppTextSize.chartLabel, fontWeight = FontWeight.Bold, color = OnPrimary, maxLines = 1, softWrap = false)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                if (stock.alertEnabled) {
                                    viewModel.toggleWatchlistStock(stock.code)
                                    showAlertSnackbar(resources.getString(R.string.watchlist_alert_removed))
                                }
                                else notificationAccess.request("price:${stock.code}")
                            },
                            modifier = Modifier
                                .widthIn(min = 72.dp)
                                .height(32.dp)
                                .semantics {
                                    stateDescription = resources.getString(
                                        if (stock.alertEnabled) R.string.watchlist_alert_enabled
                                        else R.string.watchlist_alert_disabled
                                    )
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = OnSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = OnSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            border = BorderStroke(0.5.dp, BorderBlue)
                        ) {
                            if (stock.alertEnabled) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = OnSurface,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = OnSurface,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.watchlist_alert_short),
                                color = if (stock.alertEnabled) PrimaryEmerald else OnSurfaceVariant,
                                fontSize = AppTextSize.chartLabel,
                                fontWeight = if (stock.alertEnabled) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        }
                        }

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isReached) PrimaryEmerald else SurfaceContainerHighest
                                ) {
                                    Text(
                                        stock.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReached) OnPrimary else OnSurface,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stock.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body,
                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.add_holding_1_s_2_s_2 , stock.currency, com.kurogane.dividend.model.formatStockPriceDisplay(stock.price, stock.code)),
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.section,
                                    fontFamily = AppNumericFontFamily
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stock.dailyChange,
                                    fontWeight = FontWeight.Bold,
                                    color = if (stock.isPositive) StatusSuccess else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        // Stepper Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark.copy(alpha = 0.85f),
                            border = BorderStroke(0.5.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.stock_detail_text_4), color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            com.kurogane.dividend.model.formatDividendYieldDisplay(
                                                stock.currentYield,
                                                stock.isNoDividendConfirmed
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = if (stock.isReady) PrimaryEmerald else OnSurface,
                                            fontSize = AppTextSize.body,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(R.string.watchlist_a11y),
                                            tint = OnSurfaceVariant,
                                            modifier = Modifier.padding(start = 5.dp).size(15.dp)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceContainer)
                                            .padding(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text(stringResource(R.string.watchlist_text_15), color = OnSurfaceVariant, fontSize = AppTextSize.section, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TargetYieldAdjustButton(
                                            icon = Icons.Default.Remove,
                                            contentDescription = stringResource(R.string.watchlist_a11y_2),
                                            feedbackView = feedbackView,
                                            onAdjust = { viewModel.updateTargetYield(stock.code, -0.05) }
                                        )
                                        Text(
                                            stringResource(R.string.settings_1_s , String.format(Locale.JAPAN, "%.2f", stock.targetYield)),
                                            color = PrimaryEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.section,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        TargetYieldAdjustButton(
                                            icon = Icons.Default.Add,
                                            contentDescription = stringResource(R.string.watchlist_a11y_3),
                                            feedbackView = feedbackView,
                                            onAdjust = { viewModel.updateTargetYield(stock.code, 0.05) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.roadmap_text_9 , String.format(Locale.JAPAN, "%.1f", yieldAchievement)),
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isReached) resources.getString(R.string.watchlist_label_5) else resources.getString(R.string.watchlist_label_6, (String.format(Locale.JAPAN, "%.2f", stock.targetYield - currentYield)).toString()),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                LinearProgressIndicator(
                                    progress = { (yieldAchievement / 100.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = if (stock.isReady) PrimaryEmerald else PrimaryEmeraldContainer,
                                    trackColor = SurfaceContainerHighest
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Paid, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.watchlist_text_16 , stock.currency, com.kurogane.dividend.model.formatStockPriceDisplay(stock.targetPrice, stock.code), targetPriceGapText),
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense,
                                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(Icons.Default.Savings, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(12.dp))
                                            Text(
                                                stringResource(R.string.watchlist_text_17 , stock.currency, com.kurogane.dividend.model.formatFinancialAmountDisplay(stock.dps)),
                                                color = PrimaryEmerald,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    }
                }
                }
            }

            // Big Green Button: Add New Stock to Watchlist
            item {
                Button(
                    onClick = {
                        showAddWatchlist = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.watchlist_text_20),
                        color = OnPrimary,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSortInfo = false
                showSortBottomSheet = false
            },
            containerColor = CardDark,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Header
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.watchlist_text_21),
                            color = OnSurface,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            IconButton(
                                onClick = { showSortInfo = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = stringResource(R.string.watchlist_sort_info_content_description),
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortInfo,
                                onDismissRequest = { showSortInfo = false },
                                modifier = Modifier.widthIn(max = 300.dp),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = SurfaceContainerLow,
                                tonalElevation = 0.dp,
                                shadowElevation = 12.dp
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PrimaryEmerald.copy(alpha = 0.14f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = PrimaryEmerald,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            stringResource(R.string.watchlist_text_21),
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.body
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = BorderColor, thickness = 0.75.dp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.watchlist_text_22),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.caption,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            showSortInfo = false
                            showSortBottomSheet = false
                        },
                        modifier = Modifier.minimumInteractiveComponentSize().size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Direction Switcher
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.watchlist_text_23),
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundDark)
                            .padding(4.dp)
                    ) {
                        val directions = listOf(
                            Pair("desc", resources.getString(R.string.watchlist_label_13)),
                            Pair("asc", resources.getString(R.string.watchlist_label_14))
                        )
                        directions.forEach { (dir, label) ->
                            val isSelected = draftSortDirection == dir
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryEmerald else Color.Transparent)
                                    .clickable { draftSortDirection = dir }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (dir == "desc") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isSelected) OnPrimary else SoftGray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        color = if (isSelected) OnPrimary else SoftGray,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Criteria Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.watchlist_text_24),
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    val criteriaOptions = listOf(
                        Triple(stringResource(R.string.watchlist_ach), stringResource(R.string.watchlist_label), Icons.Default.Bolt),
                        Triple(stringResource(R.string.watchlist_yield), stringResource(R.string.watchlist_label_2), Icons.AutoMirrored.Filled.TrendingUp),
                        Triple(stringResource(R.string.watchlist_price), stringResource(R.string.watchlist_label_3), Icons.Default.AttachMoney),
                        Triple(stringResource(R.string.watchlist_code), stringResource(R.string.watchlist_label_4), Icons.Default.Bookmark)
                    )
                    
                    criteriaOptions.forEach { (crit, label, icon) ->
                        val isSelected = draftSortCriteria == crit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SurfaceContainerHigh else BackgroundDark)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { draftSortCriteria = crit }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) PrimaryEmerald else SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    label,
                                    color = if (isSelected) OnSurface else SoftGray,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Action apply buttons
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showSortInfo = false
                            showSortBottomSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.caption
                        )
                    }
                    Button(
                        onClick = {
                            val sourceStocks = watchlist
                            val sourceFilteredStocks = filteredList
                            val criteria = draftSortCriteria
                            val direction = draftSortDirection
                            val currentYields = watchlistMetrics.currentYields
                            screenScope.launch {
                                val newlySortedCodes = withContext(Dispatchers.Default) {
                                    sortWatchlist(sourceFilteredStocks, criteria, direction, currentYields).map { it.code }
                                }
                                if (viewModel.watchlist.value !== sourceStocks) return@launch
                                val previousAppliedOrder = appliedOrderCodes
                                if (localDataRestored) {
                                    val visibleCodes = newlySortedCodes.toHashSet()
                                    val previousOrder = appliedOrderCodes ?: sourceStocks.map { it.code }
                                    appliedOrderCodes = newlySortedCodes + previousOrder.filterNot { it in visibleCodes }
                                } else {
                                    // Let restoration apply this selection to the durable Watchlist,
                                    // rather than carrying an order derived from startup placeholders.
                                    appliedOrderCodes = null
                                }
                                if (!viewModel.saveWatchlistSortSelection(criteria, direction)) {
                                    appliedOrderCodes = previousAppliedOrder
                                    return@launch
                                }
                                showSortInfo = false
                                showSortBottomSheet = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            stringResource(R.string.apply),
                            color = OnPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = AppTextSize.caption
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet 1: 権利落ち月マトリクス (Image 2)
    if (showMatrixBottomSheet && hasWatchlistProjection) {
        ModalBottomSheet(
            onDismissRequest = { showMatrixBottomSheet = false },
            sheetState = matrixSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            val dividendEventRevision by viewModel.dividendEventRevision.collectAsStateWithLifecycle()
            val latestRightsDateInfo = remember(watchlist, dividendEventRevision) {
                val eventsByStock = watchlist.map { stock ->
                    stock to viewModel.dividendEventsFor(stock.code)
                }
                val date = eventsByStock.asSequence()
                    .flatMap { (_, events) -> events.asSequence() }
                    .mapNotNull { it.finalRightsDate }
                    .maxOrNull()
                val stockNames = date?.let { latestDate ->
                    eventsByStock
                        .filter { (_, events) -> events.any { it.finalRightsDate == latestDate } }
                        .map { (stock, _) -> stock.name }
                        .distinct()
                }.orEmpty()
                date to stockNames
            }
            val latestCalculatedRightsDate = latestRightsDateInfo.first
            val latestCalculatedRightsStockNames = latestRightsDateInfo.second
            val totalAmountTextMeasurer = rememberTextMeasurer()
            val totalAmountCellWidthPx = with(LocalDensity.current) { 52.dp.roundToPx() }
            val totalAmountTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.minimumInteractiveComponentSize().size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.watchlist_text_11), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        stringResource(R.string.watchlist_text_25),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.watchlist_projection_header, watchlist.size),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showMatrixBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = BorderColor)

                // KPI Sub-Grid Row (3 Cards)
                val kpiCardsInSingleRow = LocalDensity.current.fontScale < 1.4f
                val kpiValueColor = OnSurface
                val kpiValueFontSize = AppTextSize.section
                val kpiValueFontWeight = FontWeight.Bold
                val kpiValueFontFamily = AppNumericFontFamily
                AdaptiveRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (kpiCardsInSingleRow) Modifier.height(IntrinsicSize.Min) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (kpiCardsInSingleRow) Modifier.fillMaxHeight() else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_text_27), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                projection.paymentCount.toString(),
                                color = kpiValueColor,
                                fontSize = kpiValueFontSize,
                                fontWeight = kpiValueFontWeight,
                                fontFamily = kpiValueFontFamily
                            )
                            Text(
                                stringResource(R.string.watchlist_text_29),
                                color = kpiValueColor,
                                fontSize = kpiValueFontSize,
                                fontWeight = kpiValueFontWeight,
                                fontFamily = kpiValueFontFamily
                            )
                        }
                    }

                    // Card 2
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .then(if (kpiCardsInSingleRow) Modifier.fillMaxHeight() else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, PrimaryEmerald.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_text_31), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            latestCalculatedRightsDate?.let { stringResource(R.string.watchlist_next_ex_date_value, it.monthValue, it.dayOfMonth) }
                                ?: stringResource(R.string.not_available),
                            color = kpiValueColor,
                            fontSize = kpiValueFontSize,
                            fontWeight = kpiValueFontWeight,
                            fontFamily = kpiValueFontFamily
                        )
                        if (latestCalculatedRightsDate == null) {
                            Text(
                                stringResource(R.string.watchlist_ex_date_missing),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        } else if (latestCalculatedRightsStockNames.isNotEmpty()) {
                            Text(
                                latestCalculatedRightsStockNames.joinToString("、"),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Card 3
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .then(if (kpiCardsInSingleRow) Modifier.fillMaxHeight() else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_text_34), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            yen(projection.annualTotal),
                            color = kpiValueColor,
                            fontSize = kpiValueFontSize,
                            fontWeight = kpiValueFontWeight,
                            fontFamily = kpiValueFontFamily
                        )
                        Text(
                            stringResource(
                                R.string.watchlist_potential_yield,
                                percent(com.kurogane.dividend.model.PortfolioMath.portfolioYield(projection.annualTotal, projection.investment, 0.0, "eval"))
                            ),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    }
                }

                // Table Section Title
                val maxMonthlyDividend = watchlist.maxOfOrNull { stock ->
                    projection.months(stock).maxOfOrNull { month -> projection.monthly(stock, month) } ?: 0.0
                } ?: 0.0
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.watchlist_text_37),
                            color = OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            IconButton(onClick = { showHeatmapInfo = true }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = stringResource(R.string.watchlist_heatmap_info_content_description),
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showHeatmapInfo,
                                onDismissRequest = { showHeatmapInfo = false },
                                modifier = Modifier.widthIn(max = 300.dp),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = SurfaceContainerLow,
                                tonalElevation = 0.dp,
                                shadowElevation = 12.dp
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PrimaryEmerald.copy(alpha = 0.14f),
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(17.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            stringResource(R.string.watchlist_text_42),
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.body
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = BorderColor, thickness = 0.75.dp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        projectionNote,
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.caption,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    // Small Legend indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_text_38), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PrimaryEmerald.copy(alpha = 0.12f), PrimaryEmerald.copy(alpha = 0.9f))
                                    )
                                )
                        )
                        Text(stringResource(R.string.watchlist_text_39), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }

                // Heatmap Scrollable Row Table
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column {
                        // Header Row
                        Row(
                            modifier = Modifier.background(SurfaceContainerHigh).padding(vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.watchlist_text_40), modifier = Modifier.width(80.dp).padding(start = 8.dp), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            (1..12).forEach { m ->
                                Text(
                                    text = stringResource(R.string.ex_dividend_matrix_dialog_text_4, m),
                                    modifier = Modifier.width(52.dp),
                                    color = if (m == 3 || m == 9 || m == 6 || m == 12) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Each cell's color strength follows its projected dividend amount.
                        watchlist.forEach { stock ->
                            HeatmapRow(stock.code, stock.name, projection.months(stock).associateWith { month ->
                                val amount = projection.monthly(stock, month)
                                val intensity = if (maxMonthlyDividend > 0.0) (amount / maxMonthlyDividend).toFloat().coerceIn(0f, 1f) else 0f
                                yen(amount) to PrimaryEmerald.copy(alpha = 0.12f + 0.78f * intensity)
                            })
                        }

                        // Row 5: 想定受取計 (買付時)
                        Row(
                            modifier = Modifier.background(SurfaceContainerHigh.copy(alpha = 0.5f)).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.watchlist_text_41), modifier = Modifier.width(80.dp).padding(start = 8.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            (1..12).forEach { m ->
                                val sumText = if (projection.monthlyTotals[m - 1] > 0) yen(projection.monthlyTotals[m - 1]) else "—"
                                val amountFontSize = remember(sumText, totalAmountCellWidthPx, totalAmountTextStyle) {
                                    (AppTextSize.dense.value.toInt() downTo 6).firstOrNull { sizeSp ->
                                        !totalAmountTextMeasurer.measure(
                                            text = sumText,
                                            style = totalAmountTextStyle.copy(fontSize = sizeSp.sp),
                                            softWrap = false,
                                            maxLines = 1,
                                            constraints = Constraints(maxWidth = totalAmountCellWidthPx)
                                        ).didOverflowWidth
                                    }?.sp ?: 6.sp
                                }
                                Text(
                                    text = sumText,
                                    modifier = Modifier.width(52.dp),
                                    color = if (sumText != "-") OnSurface else OnSurfaceVariant,
                                    style = totalAmountTextStyle.copy(fontSize = amountFontSize),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.watchlist_peak_share, percent(projection.peakShare)),
                            color = SecondaryIndigo,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.watchlist_jp_projection, yen(watchlist.filter { it.category != "us" }.sumOf(projection::annual))),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.watchlist_us_projection, yen(watchlist.filter { it.category == "us" }.sumOf(projection::annual))),
                            color = SecondaryIndigo,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dismiss Matrix Button
                Button(
                    onClick = { showMatrixBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.watchlist_text_47), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal Bottom Sheet 2: 受取月別グラフ試算 (Image 3)
    if (showGraphBottomSheet && hasWatchlistProjection) {
        ModalBottomSheet(
            onDismissRequest = { showGraphBottomSheet = false },
            sheetState = graphSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.minimumInteractiveComponentSize().size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.watchlist_text_12), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SkyBlue.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        stringResource(R.string.watchlist_text_48),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = SkyBlue,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.watchlist_projection_header, watchlist.size),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showGraphBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = BorderColor)

                // Card 2: 12-month calendar bar graph Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = 320.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridOn, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.watchlist_text_66), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Box {
                                    IconButton(
                                        onClick = { showCalendarInfo = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = stringResource(R.string.watchlist_calendar_info_content_description),
                                            tint = OnSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showCalendarInfo,
                                        onDismissRequest = { showCalendarInfo = false },
                                        modifier = Modifier.widthIn(max = 300.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = SurfaceContainerLow,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 12.dp
                                    ) {
                                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = PrimaryEmerald.copy(alpha = 0.14f),
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(17.dp))
                                                    }
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    stringResource(R.string.watchlist_calendar_info_title),
                                                    color = OnSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = AppTextSize.body
                                                )
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider(color = BorderColor, thickness = 0.75.dp)
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                projectionNote,
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.caption,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }
                            }

                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Match the analysis screen's stacked monthly bars and amount labels.
                        val calendarFontScale = LocalDensity.current.fontScale
                        val calendarChartCanvasHeight = 136.dp
                        val calendarBarAreaBottomFraction = .90f
                        val calendarBarAreaHeightFraction = .73f
                        val calendarPlotHeight = calendarChartCanvasHeight * calendarBarAreaHeightFraction
                        val calendarAmountLabelTopOffset = 19.dp * calendarFontScale
                        val calendarAmountLabelSlotHeight = 16.dp * calendarFontScale
                        val calendarAxisGap = 3.dp
                        val calendarAxisLabelHeight = calendarAmountLabelSlotHeight
                        val calendarMaximumAmount = projection.monthlyTotals.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                        val calendarGridTargetStep = calendarMaximumAmount / 3.0
                        val calendarGridMagnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(calendarGridTargetStep)))
                        val calendarNormalizedGridStep = calendarGridTargetStep / calendarGridMagnitude
                        val calendarGridStepMultiplier = when {
                            calendarNormalizedGridStep >= 5.0 -> 5.0
                            calendarNormalizedGridStep >= 2.0 -> 2.0
                            else -> 1.0
                        }
                        val calendarGridStep = calendarGridMagnitude * calendarGridStepMultiplier
                        val calendarGridCount = kotlin.math.floor(calendarMaximumAmount / calendarGridStep).toInt()
                        val calendarGridColor = BorderColor
                        val calendarMonthlyByCategory = remember(projection, watchlist) {
                            (1..12).map { month ->
                                listOf(
                                    watchlist.filter { it.category != "us" }.sumOf { projection.monthly(it, month) },
                                    watchlist.filter { it.category == "us" }.sumOf { projection.monthly(it, month) }
                                )
                            }
                        }
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth().drawBehind {
                                val plotBottom = calendarChartCanvasHeight.toPx() * calendarBarAreaBottomFraction
                                val plotHeight = calendarPlotHeight.toPx()
                                (0..calendarGridCount).forEach { guideIndex ->
                                    val guideAmount = guideIndex * calendarGridStep
                                    val y = plotBottom - plotHeight * (guideAmount / calendarMaximumAmount).toFloat()
                                    drawLine(calendarGridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                                }
                            },
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            (1..12).forEach { month ->
                                val amount = projection.monthlyTotals[month - 1]
                                val amountLabel = when {
                                    amount >= 1_000_000 -> String.format(Locale.JAPAN, "%.0f万", amount / 10_000.0)
                                    amount >= 10_000 -> String.format(Locale.JAPAN, "%.1f万", amount / 10_000.0)
                                    amount > 0 -> String.format(Locale.JAPAN, "%.1f千", amount / 1_000.0)
                                    else -> "—"
                                }
                                val heightFraction = (amount / calendarMaximumAmount).toFloat().coerceIn(0f, 1f)

                                Column(
                                    modifier = Modifier.weight(1f)
                                        .height(calendarChartCanvasHeight + calendarAxisGap + calendarAxisLabelHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().height(calendarChartCanvasHeight)) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth()
                                                .height(calendarChartCanvasHeight * calendarBarAreaBottomFraction)
                                                .align(Alignment.TopCenter),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            if (amount > 0.0) {
                                                Column(
                                                    modifier = Modifier.widthIn(max = 32.dp)
                                                        .fillMaxWidth()
                                                        .height(calendarPlotHeight * heightFraction)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    verticalArrangement = Arrangement.Bottom
                                                ) {
                                                    val categories = calendarMonthlyByCategory[month - 1]
                                                    listOf(1, 0).forEach { categoryIndex ->
                                                        val categoryAmount = categories[categoryIndex]
                                                        if (categoryAmount > 0.0) {
                                                            val categoryColor = if (categoryIndex == 0) PrimaryEmeraldContainer else SecondaryIndigo
                                                            Box(
                                                                Modifier.fillMaxWidth()
                                                                    .weight((categoryAmount / amount).toFloat())
                                                                    .background(Brush.verticalGradient(listOf(categoryColor, categoryColor.copy(alpha = .45f))))
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Preserve the existing empty-month marker when no payout is projected.
                                                Box(
                                                    modifier = Modifier.widthIn(max = 32.dp)
                                                        .fillMaxWidth()
                                                        .height(12.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .border(1.dp, BorderColor, RoundedCornerShape(2.dp))
                                                )
                                            }
                                        }
                                        Text(
                                            text = amountLabel,
                                            modifier = Modifier.fillMaxWidth()
                                                .height(calendarAmountLabelSlotHeight)
                                                .align(Alignment.TopCenter)
                                                .offset(y = calendarChartCanvasHeight * (calendarBarAreaBottomFraction - heightFraction * calendarBarAreaHeightFraction) - calendarAmountLabelTopOffset),
                                            fontSize = AppTextSize.chartLabel,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            maxLines = if (calendarFontScale >= 1.4f) Int.MAX_VALUE else 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(calendarAxisGap))
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(calendarAxisLabelHeight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.annual_dividend_1_s, month),
                                            modifier = Modifier.fillMaxWidth(),
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            maxLines = if (calendarFontScale >= 1.4f) Int.MAX_VALUE else 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(stringResource(R.string.watchlist_empty_months, projection.emptyMonths.joinToString("・").ifEmpty { "—" }), color = ErrorRed, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // KPI Row (3 cards side-by-side)
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_annual_estimate), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(yen(projection.annualTotal), color = PrimaryEmerald, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = AppNumericFontFamily)
                    }

                    // Card 2
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_monthly_estimate), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(yen(projection.annualTotal / 12), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = AppNumericFontFamily)
                    }

                    // Card 3
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(stringResource(R.string.watchlist_text_54), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(stringResource(R.string.watchlist_empty_month_count, projection.emptyMonths.size), color = ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }

                // Quarterly cash-flow comparison aligned with the analysis screen.
                val projectionQuarterColors = listOf(PrimaryEmeraldContainer, SecondaryIndigo, SkyBlue, AccentAmber)
                val projectionQuarterTotal = projection.quarterlyTotals.sum()
                val projectionMaxMonthShare = if (projectionQuarterTotal > 0.0) {
                    projection.monthlyTotals.maxOrNull()?.div(projectionQuarterTotal)?.times(100.0) ?: 0.0
                } else 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PieChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.annual_dividend_quarterly_cashflow_title), modifier = Modifier.weight(1f), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SecondaryIndigo.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    stringResource(R.string.annual_dividend_quarterly_max_month_share, String.format(Locale.JAPAN, "%.1f%%", projectionMaxMonthShare)),
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(15.dp)
                                .clip(CircleShape)
                                .background(BorderBlue)
                        ) {
                            if (projectionQuarterTotal > 0.0) {
                                Row(Modifier.fillMaxSize()) {
                                    projection.quarterlyTotals.forEachIndexed { index, amount ->
                                        if (amount > 0.0) {
                                            val isSelected = selectedProjectionQuarterIndex == index
                                            val emphasisProgress by animateFloatAsState(
                                                targetValue = if (isSelected) 1f else 0f,
                                                label = "Watchlist quarterly cash flow emphasis $index"
                                            )
                                            Box(
                                                Modifier.weight((amount / projectionQuarterTotal).toFloat())
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 0.5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    Modifier.fillMaxWidth()
                                                        .height((9f + 6f * emphasisProgress).dp)
                                                        .clip(CircleShape)
                                                        .background(projectionQuarterColors[index])
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            projection.quarterlyTotals.forEachIndexed { index, amount ->
                                val firstMonth = index * 3 + 1
                                val lastMonth = firstMonth + 2
                                val sharePercent = if (projectionQuarterTotal > 0.0) amount / projectionQuarterTotal * 100.0 else 0.0
                                Surface(
                                    modifier = Modifier.weight(1f).clickable {
                                        selectedProjectionQuarterIndex = if (selectedProjectionQuarterIndex == index) null else index
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(stringResource(R.string.annual_dividend_quarterly_quarter_label, index + 1, firstMonth, lastMonth), color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1, softWrap = false)
                                        Spacer(Modifier.height(2.dp))
                                        Text("¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(amount)}", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = AppNumericFontFamily, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                                        Spacer(Modifier.height(2.dp))
                                        Text(String.format(Locale.JAPAN, "%.1f%%", sharePercent), color = projectionQuarterColors[index], fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 3: V2-backed income-gap advice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.watchlist_income_advice_title),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.watchlist_income_advice_explanation),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        when {
                            projection.emptyMonths.isEmpty() -> Text(
                                stringResource(R.string.watchlist_income_advice_no_gaps),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                            incomeAdviceCandidates.isNotEmpty() -> incomeAdviceCandidates.forEach { ranked ->
                                val candidate = ranked.candidate
                                val adviceControlWidth = maxOf(140.dp, (78 + 31 * ranked.coveredMonths.size).dp)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                        .clickable { onNavigateToDetail(candidate.code) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(candidate.code, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                Spacer(Modifier.width(8.dp))
                                                Text(candidate.name, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(
                                                stringResource(R.string.watchlist_income_advice_monthly_estimate, yen(ranked.estimatedAmountPerCoveredMonth)),
                                                color = PrimaryEmerald,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                stringResource(
                                                    if (candidate.annualDividendBasis == "next_fiscal_year_forecast") R.string.watchlist_income_advice_annual_forecast
                                                    else R.string.watchlist_income_advice_annual_reference,
                                                    yen(candidate.annualDividendPerShare * 100)
                                                ),
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Button(
                                                onClick = { addWatchlist(candidate.code) { showGraphBottomSheet = false } },
                                                enabled = !isAddingWatchlist,
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.width(adviceControlWidth).height(29.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text(stringResource(R.string.watchlist_income_advice_add), color = OnPrimary, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = SurfaceContainerLow,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.width(adviceControlWidth).height(29.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 6.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        stringResource(R.string.watchlist_income_advice_covered_months_label),
                                                        color = OnSurfaceVariant,
                                                        fontSize = AppTextSize.caption,
                                                        maxLines = 1
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    ranked.coveredMonths.forEach { month ->
                                                        Surface(
                                                            shape = RoundedCornerShape(5.dp),
                                                            color = PrimaryEmerald.copy(alpha = 0.12f)
                                                        ) {
                                                            Text(
                                                                "${month}月",
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                                color = PrimaryEmerald,
                                                                fontSize = AppTextSize.caption,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1
                                                            )
                                                        }
                                                        Spacer(Modifier.width(3.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            incomeAdviceCatalog == null && incomeAdviceFetchFailed -> Text(
                                stringResource(R.string.watchlist_income_advice_fetch_failed),
                                color = ErrorRed,
                                fontSize = AppTextSize.dense
                            )
                            incomeAdviceCatalog == null -> Text(
                                stringResource(R.string.watchlist_income_advice_loading),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            else -> Text(
                                stringResource(R.string.watchlist_income_advice_no_candidates),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }
                }

                // Row 4: Stocks contribution card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.watchlist_text_80), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.watchlist_projection_header, watchlist.size), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        watchlist.forEach { stock ->
                            AdaptiveRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${stock.code} ${stock.name}", color = OnSurface, fontSize = AppTextSize.dense)
                                    Text(projection.months(stock).joinToString(" / ") { "${it}月" }.ifEmpty { resources.getString(R.string.watchlist_months_missing) }, color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                                Text(yen(projection.annual(stock)), color = PrimaryEmerald, fontSize = AppTextSize.dense)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPayoutRiskBottomSheet && hasWatchlistProjection) {
        ModalBottomSheet(
            onDismissRequest = { showPayoutRiskBottomSheet = false },
            sheetState = payoutRiskSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            val financialData by viewModel.financialData.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.watchlist_payout_diagnosis_button), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.watchlist_payout_diagnosis_count, watchlist.count { it.category == "jp" }),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showPayoutRiskBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = BorderColor)

                WatchlistDividendPayoutRiskContent(
                    watchlist = watchlist,
                    financialData = financialData,
                    onNavigateToDetail = onNavigateToDetail
                )

                Button(
                    onClick = { showPayoutRiskBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.close), color = OnSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TargetYieldAdjustButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    feedbackView: android.view.View,
    onAdjust: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var repeatedDuringPress by remember { mutableStateOf(false) }
    val latestOnAdjust by rememberUpdatedState(onAdjust)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) repeatedDuringPress = false
        }
    }
    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        delay(450)
        repeatedDuringPress = true
        while (true) {
            feedbackView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            latestOnAdjust()
            delay(100)
        }
    }

    IconButton(
        onClick = {
            if (!repeatedDuringPress) {
                feedbackView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                latestOnAdjust()
            }
        },
        modifier = Modifier.minimumInteractiveComponentSize().size(36.dp),
        interactionSource = interactionSource
    ) {
        Icon(icon, contentDescription = contentDescription, tint = OnSurface, modifier = Modifier.size(12.dp))
    }
}

// Custom cell row for Ex-dividend heatmap matrix (Image 2)
@Composable
private fun HeatmapRow(
    code: String,
    name: String,
    cells: Map<Int, Pair<String, Color>>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderColor)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(80.dp).padding(start = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SurfaceContainerHighest,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(name, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
        }

        (1..12).forEach { m ->
            val cellData = cells[m]
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .heightIn(min = 48.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cellData?.second ?: Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (cellData != null) {
                    Text(
                        text = cellData.first,
                        color = OnSurface,
                        fontSize = AppTextSize.tiny,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text("—", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this
