package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.RollingMoney
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.data.Tse33IndustryMaster
import com.kurogane.dividend.data.CloudflareMonthlyPrice
import com.kurogane.dividend.data.CloudflareMonthlyPriceSeries
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.PortfolioDiagnosis
import com.kurogane.dividend.model.PortfolioDiagnosisGenerator
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
private fun Modifier.reportViewportVisibility(
    viewportBounds: Rect?,
    onVisibilityChanged: (Boolean) -> Unit
): Modifier = onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInRoot()
    val viewport = viewportBounds
    onVisibilityChanged(
        viewport != null &&
            bounds.right > viewport.left && bounds.bottom > viewport.top &&
            bounds.left < viewport.right && bounds.top < viewport.bottom
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualDividendScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    scrollToTopRequest: Int = 0,
    isTabSelected: Boolean = true
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    var chartViewportBounds by remember { mutableStateOf<Rect?>(null) }
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val compositionThreshold by viewModel.compositionThreshold.collectAsStateWithLifecycle()
    val compositionOtherEnabled by viewModel.compositionOtherEnabled.collectAsStateWithLifecycle()

    val totalAssets = remember(holdings) { holdings.sumOf { it.totalValuation } }
    val totalCost = remember(holdings) { holdings.sumOf { it.totalCost } }
    val grossAnnualDividend = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("gross") } }
    val netAnnualDividend = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("net") } }
    val totalAnnualDiv = remember(holdings, taxRateMode) { holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) } }
    val yieldOnCost = remember(grossAnnualDividend, totalCost) { if (totalCost > 0) (grossAnnualDividend / totalCost) * 100 else 0.0 }
    val yieldOnValuation = remember(grossAnnualDividend, totalAssets) { if (totalAssets > 0) (grossAnnualDividend / totalAssets) * 100 else 0.0 }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val listStates = List(3) { page ->
        val storedPosition = viewModel.annualAnalysisScrollPositions.getOrNull(page) ?: (0 to 0)
        val legacyPosition = if (page == 0 && storedPosition == (0 to 0) && viewModel.annualScrollIndex >= 2) {
            (viewModel.annualScrollIndex - 1) to viewModel.annualScrollOffset
        } else storedPosition
        rememberLazyListState(legacyPosition.first, legacyPosition.second)
    }
    val screenScope = rememberCoroutineScope()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listStates[pagerState.currentPage].scrollToTopIn200ms()
    }
    val analysisPagePosition = (
        pagerState.currentPage + pagerState.currentPageOffsetFraction
    ).coerceIn(0f, 2f)
    val selectAnalysisTab = rememberUpdatedState<(Int) -> Unit> { index ->
        if (index == 0 && pagerState.currentPage == 0 && pagerState.isScrollInProgress.not()) {
            screenScope.launch {
                val state = listStates[0]
                val startIndex = state.firstVisibleItemIndex
                val startOffset = state.firstVisibleItemScrollOffset
                if (startIndex != 0 || startOffset != 0) {
                    animate(1f, 0f, animationSpec = tween(durationMillis = 200)) { fraction, _ ->
                        state.requestScrollToItem(
                            (startIndex * fraction).roundToInt(),
                            if (startIndex == 0) (startOffset * fraction).roundToInt() else 0
                        )
                    }
                    state.scrollToItem(0)
                }
            }
        } else if (index in 0..2 && index != pagerState.currentPage) {
            screenScope.launch { pagerState.animateScrollToPage(index) }
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var commonHeaderHeightPx by remember { mutableIntStateOf(with(density) { 112.dp.roundToPx() }) }
    var subtabRowHeightPx by remember { mutableIntStateOf(with(density) { 56.dp.roundToPx() }) }
    var lastSettledPage by remember { mutableIntStateOf(pagerState.settledPage) }
    fun headerScrollOffset(page: Int): Int = listStates[page].let { state ->
        if (state.firstVisibleItemIndex > 0) commonHeaderHeightPx
        else state.firstVisibleItemScrollOffset.coerceAtMost(commonHeaderHeightPx)
    }
    val pagePosition = (
        pagerState.currentPage + pagerState.currentPageOffsetFraction
    ).coerceIn(0f, 2f)
    val pageDistanceFromSettled = pagePosition - lastSettledPage
    val swipeHasStarted = kotlin.math.abs(pageDistanceFromSettled) > 0.001f
    val swipeDestinationPage = (lastSettledPage + when {
        pageDistanceFromSettled > 0f -> 1
        pageDistanceFromSettled < 0f -> -1
        else -> 0
    }).coerceIn(0, 2)
    val swipeProgress = kotlin.math.abs(pageDistanceFromSettled).coerceIn(0f, 1f)
    val settledHeaderOffsetPx = headerScrollOffset(lastSettledPage)
    val targetPageAtTop = swipeHasStarted &&
        swipeDestinationPage != lastSettledPage &&
        listStates[swipeDestinationPage].firstVisibleItemIndex == 0 &&
        listStates[swipeDestinationPage].firstVisibleItemScrollOffset == 0
    val sourceHeaderHidden = settledHeaderOffsetPx >= subtabRowHeightPx
    val keepHeaderForTopTarget = targetPageAtTop
    val swipeHeaderHideOffsetPx = ((commonHeaderHeightPx - settledHeaderOffsetPx) * swipeProgress).roundToInt()
    val headerScrollOffsetPx = if (keepHeaderForTopTarget) {
        (settledHeaderOffsetPx * (1f - swipeProgress)).roundToInt()
    } else {
        maxOf(settledHeaderOffsetPx, settledHeaderOffsetPx + swipeHeaderHideOffsetPx)
    }
    LaunchedEffect(pagerState, listStates) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != lastSettledPage) {
                lastSettledPage = page
            }
        }
    }
    LaunchedEffect(listStates) {
        listStates.forEachIndexed { page, state ->
            launch {
                snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                    .collect { (index, offset) ->
                        viewModel.annualAnalysisScrollPositions = viewModel.annualAnalysisScrollPositions
                            .toMutableList().also { it[page] = index to offset }
                        if (page == 0) {
                            viewModel.annualScrollIndex = if (index == 0) 0 else index + 1
                            viewModel.annualScrollOffset = offset
                        }
                    }
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
          HorizontalPager(
              state = pagerState,
              modifier = Modifier.fillMaxSize().onGloballyPositioned {
                  chartViewportBounds = it.boundsInRoot()
              },
              beyondViewportPageCount = 0,
              key = { it }
          ) { page ->
            val incomingHeaderSpacePx = if (swipeHasStarted && page == swipeDestinationPage) {
                if (keepHeaderForTopTarget && sourceHeaderHidden) {
                    (commonHeaderHeightPx * swipeProgress).roundToInt()
                } else if (keepHeaderForTopTarget) {
                    commonHeaderHeightPx
                } else {
                    commonHeaderHeightPx - headerScrollOffsetPx
                }
            } else commonHeaderHeightPx
            LazyColumn(
                state = listStates[page],
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(
                        Modifier.height(
                            with(density) {
                                incomingHeaderSpacePx.coerceAtLeast(0).toDp()
                            }
                        )
                    )
                }

            // Hero Card: Annual Expected Dividend
            if (page == 0) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.annual_dividend_text),
                                        fontSize = AppTextSize.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    AnnualDividendHistoryInfo(stringResource(R.string.annual_dividend_gross_info_description))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    RollingMoney(
                                        amount = if (isPrivacyActive) "¥••••••••" else "¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(grossAnnualDividend)}",
                                        modifier = Modifier.weight(1f, fill = false),
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = OnSurface,
                                                fontFamily = FontFamily.SansSerif,
                                                fontSize = AppTextSize.hero,
                                                letterSpacing = (-0.5).sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.annual_dividend_text_2), color = OnSurfaceVariant, fontSize = AppTextSize.caption, modifier = Modifier.padding(bottom = 4.dp))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald.copy(alpha = 0.12f))
                                        .border(0.5.dp, PrimaryEmerald.copy(alpha = 0.24f), CircleShape)
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_5) else resources.getString(R.string.annual_dividend_label_6, com.kurogane.dividend.model.formatFinancialAmountDisplay(grossAnnualDividend / 12)),
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_7) else resources.getString(R.string.annual_dividend_label_8, com.kurogane.dividend.model.formatFinancialAmountDisplay(netAnnualDividend / 12)),
                                    fontSize = AppTextSize.dense,
                                    color = OnSurfaceVariant,
                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_9) else resources.getString(R.string.annual_dividend_label_10, com.kurogane.dividend.model.formatFinancialAmountDisplay(netAnnualDividend)),
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceContainerHigh)
                                    .border(0.5.dp, BorderColor, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.annual_dividend_text_3), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1, softWrap = false)
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format(Locale.JAPAN, "%.2f", yieldOnCost),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryEmerald,
                                        fontFamily = AppNumericFontFamily,
                                        maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(stringResource(R.string.annual_dividend_text_4), fontSize = AppTextSize.dense, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_11) else resources.getString(R.string.annual_dividend_label_12, (currencyFormat.format(totalCost.toLong())).toString()),
                                    fontSize = AppTextSize.dense,
                                    color = OnSurfaceVariant,
                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                    softWrap = false
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceContainerHigh)
                                    .border(0.5.dp, BorderColor, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.annual_dividend_text_5), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1, softWrap = false)
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format(Locale.JAPAN, "%.2f", yieldOnValuation),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryEmerald,
                                        fontFamily = AppNumericFontFamily,
                                        maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(stringResource(R.string.annual_dividend_text_4), fontSize = AppTextSize.dense, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_13) else resources.getString(R.string.annual_dividend_label_14, (currencyFormat.format(totalAssets.toLong())).toString()),
                                    fontSize = AppTextSize.dense,
                                    color = OnSurfaceVariant,
                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
            // Tab 2: Sector & Composition Analysis
            if (page == 2) {
                item {
                    CompositionAnalysisSection(holdings = holdings, taxRateMode = taxRateMode, totalAssets = totalAssets, totalAnnualDiv = totalAnnualDiv, compositionThreshold = compositionThreshold, compositionOtherEnabled = compositionOtherEnabled, viewModel = viewModel, onNavigateToDetail = onNavigateToDetail)
                }
            }

            // Tab 0: Dividend Compounding Simulation
            if (page == 0) {
                item {
                    CompoundingSimulationSection(
                        viewModel = viewModel,
                        holdings = holdings,
                        taxRateMode = taxRateMode,
                        totalAnnualDiv = totalAnnualDiv,
                        totalCost = totalCost,
                        isPrivacyActive = isPrivacyActive,
                        chartViewportBounds = chartViewportBounds,
                        isTabSelected = isTabSelected
                    )
                }
                item {
                    MonthlyDividendEstimateCard(
                        viewModel = viewModel,
                        holdings = holdings,
                        taxRateMode = taxRateMode,
                        totalAnnualDividend = totalAnnualDiv,
                        selectedMonth = selectedMonth,
                        isPrivacyActive = isPrivacyActive,
                        chartViewportBounds = chartViewportBounds,
                        isTabSelected = isTabSelected
                    )
                }
                item {
                    DividendPayoutHealthAndRiskCards(
                        holdings = holdings,
                        financialData = financialData,
                        taxRateMode = taxRateMode,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }

            // Tab 1: Dividend increase/decrease
            if (page == 1) {
                item {
                    DividendChangeAnalysisCards(
                        holdings = holdings,
                        financialData = financialData,
                        isPrivacyActive = isPrivacyActive,
                        taxRateMode = taxRateMode,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
                item {
                    DividendChangeSecuritiesList(
                        holdings = holdings,
                        financialData = financialData,
                        isPrivacyActive = isPrivacyActive,
                        taxRateMode = taxRateMode,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
            }
          }
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .height(3.dp)
                  .align(Alignment.TopCenter)
                  .zIndex(2f)
          ) {
              BoxWithConstraints(
                  modifier = Modifier
                      .fillMaxSize()
                      .padding(horizontal = 21.dp)
              ) {
                  val indicatorGap = 6.dp
                  val indicatorWidth = (maxWidth - indicatorGap * 2) / 3
                  Box(
                      modifier = Modifier
                          .width(indicatorWidth)
                          .fillMaxHeight()
                          .offset {
                              val stepPx = with(density) { (indicatorWidth + indicatorGap).toPx() }
                              IntOffset((analysisPagePosition * stepPx).roundToInt(), 0)
                          }
                          .background(PrimaryEmerald)
                  )
              }
          }
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp)
                  .padding(top = 3.dp)
                  .offset { IntOffset(0, -headerScrollOffsetPx) }
                  .zIndex(1f)
                  .background(BackgroundDark)
                  .onSizeChanged { commonHeaderHeightPx = it.height + with(density) { 3.dp.roundToPx() } }
          ) {
              AdaptiveRow(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(14.dp))
                      .background(SurfaceContainerLow)
                      .padding(5.dp)
                      .onSizeChanged { subtabRowHeightPx = it.height }
                      .selectableGroup(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  AnnualDividendTab(
                      label = resources.getString(R.string.annual_dividend_label_19),
                      icon = Icons.AutoMirrored.Filled.TrendingUp,
                      selectionProgress = (1f - kotlin.math.abs(pagerState.currentPage + pagerState.currentPageOffsetFraction)).coerceIn(0f, 1f),
                      onClick = { selectAnalysisTab.value(0) },
                      modifier = Modifier.weight(1f)
                  )
                  AnnualDividendTab(
                      label = resources.getString(R.string.annual_dividend_label_49),
                      icon = Icons.Default.AccountBalance,
                      selectionProgress = (1f - kotlin.math.abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - 1f)).coerceIn(0f, 1f),
                      onClick = { selectAnalysisTab.value(1) },
                      modifier = Modifier.weight(1f)
                  )
                  AnnualDividendTab(
                      label = resources.getString(R.string.annual_dividend_label_61),
                      icon = Icons.Default.DataUsage,
                      selectionProgress = (1f - kotlin.math.abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - 2f)).coerceIn(0f, 1f),
                      onClick = { selectAnalysisTab.value(2) },
                      modifier = Modifier.weight(1f)
                  )
              }
          }
        }
    }
}

@Composable
private fun AnnualDividendTab(
    label: String,
    icon: ImageVector,
    selectionProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabShape = RoundedCornerShape(12.dp)
    val selected = selectionProgress >= 0.5f
    val backgroundColor = lerp(SurfaceContainer, PrimaryEmerald, selectionProgress)
    val borderColor = lerp(BorderColor, PrimaryEmerald, selectionProgress)
    val foregroundColor = lerp(OnSurface, OnPrimary, selectionProgress)
    Column(
        modifier = modifier
            .clip(tabShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, tabShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 23.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = lerp(OnSurfaceVariant, OnPrimary, selectionProgress),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = foregroundColor,
                fontSize = AppTextSize.dense,
                maxLines = 1
            )
        }
        Box(
            Modifier.width(22.dp).height(2.dp).clip(CircleShape)
                .background(OnPrimary.copy(alpha = selectionProgress))
        )
    }
}

@Composable
private fun MonthlyDividendEstimateCard(
    viewModel: DividendViewModel,
    holdings: List<Holding>,
    taxRateMode: String,
    totalAnnualDividend: Double,
    selectedMonth: Int?,
    isPrivacyActive: Boolean,
    chartViewportBounds: Rect?,
    isTabSelected: Boolean
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    var showDividendInfo by remember { mutableStateOf(false) }
    var selectedQuarterIndex by remember { mutableStateOf<Int?>(null) }
    val dividendEventRevision by viewModel.dividendEventRevision.collectAsStateWithLifecycle()
    val monthlyByCategory = remember(holdings, taxRateMode, dividendEventRevision) {
        (1..12).map { month ->
            listOf(Category.JAPAN, Category.US, Category.FUND).map { category ->
                holdings.filter { it.category == category }.sumOf { holding ->
                    viewModel.dividendEventsFor(holding.code).filter { it.date.monthValue == month }.sumOf { event ->
                        holding.copy(dps = event.amountPerShare).getEffectiveAnnualDividend(taxRateMode)
                    }
                }
            }
        }
    }
    val monthlyDividends = remember(monthlyByCategory) {
        monthlyByCategory.mapIndexed { index, categories ->
            val month = index + 1
            val amount = categories.sum()
            val label = when {
                amount >= 1_000_000 -> String.format(Locale.JAPAN, "%.0f万", amount / 10_000.0)
                amount >= 10_000 -> String.format(Locale.JAPAN, "%.1f万", amount / 10_000.0)
                amount > 0 -> String.format(Locale.JAPAN, "%.1f千", amount / 1_000.0)
                else -> "0"
            }
            Triple(month, amount, label)
        }
    }
    var monthlyChartVisible by remember { mutableStateOf(false) }
    val monthlyBarsProgress = remember { Animatable(1f) }
    LaunchedEffect(isTabSelected) {
        if (!isTabSelected) {
            monthlyBarsProgress.snapTo(1f)
            return@LaunchedEffect
        }
        monthlyBarsProgress.snapTo(0f)
        withFrameNanos { }
        if (monthlyChartVisible) {
            monthlyBarsProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)
            )
        } else {
            monthlyBarsProgress.snapTo(1f)
        }
    }
    val maxMonthlyAmount = remember(monthlyDividends) {
        monthlyDividends.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    }
    val quarterlyCashFlowAmounts = remember(monthlyDividends) {
        (0..3).map { quarterIndex ->
            monthlyDividends.drop(quarterIndex * 3).take(3).sumOf { it.second }
        }
    }
    val monthlyCashFlowTotal = quarterlyCashFlowAmounts.sum()
    val maxMonthSharePercent = remember(monthlyDividends, monthlyCashFlowTotal) {
        val maxMonthAmount = monthlyDividends.maxOfOrNull { it.second } ?: 0.0
        if (monthlyCashFlowTotal > 0.0) maxMonthAmount / monthlyCashFlowTotal * 100.0 else 0.0
    }
    val monthlyGridTargetStep = maxMonthlyAmount / 3.0
    val monthlyGridMagnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(monthlyGridTargetStep)))
    val monthlyNormalizedGridStep = monthlyGridTargetStep / monthlyGridMagnitude
    val monthlyGridStepMultiplier = when {
        monthlyNormalizedGridStep >= 5.0 -> 5.0
        monthlyNormalizedGridStep >= 2.0 -> 2.0
        else -> 1.0
    }
    val monthlyGridStep = monthlyGridMagnitude * monthlyGridStepMultiplier
    val monthlyGridCount = kotlin.math.floor(maxMonthlyAmount / monthlyGridStep).toInt()
    val monthlyGridColor = BorderColor
    val monthlySelectionColor = PrimaryEmerald
    val monthlyChartCanvasHeight = 136.dp
    val monthlyBarAreaBottomFraction = .90f
    val monthlyBarAreaHeightFraction = .62f
    val monthlyPlotHeight = monthlyChartCanvasHeight * monthlyBarAreaHeightFraction
    val monthlyAmountLabelTopOffset = 24.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale
    val monthlyAmountLabelSlotHeight = 16.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale
    val monthlyAxisGap = 3.dp
    val monthlyAxisLabelHeight = monthlyAmountLabelSlotHeight
    val monthlyAxisInset = monthlyAxisGap + monthlyAxisLabelHeight
    val monthlyChartHeight = monthlyChartCanvasHeight + monthlyAxisInset
    val monthlyBarStaggerStep = if (monthlyDividends.size > 1) .32f / (monthlyDividends.size - 1) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, BorderBlue)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.annual_dividend_text_6), fontSize = AppTextSize.body, fontWeight = FontWeight.ExtraBold, color = OnSurface)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box {
                        IconButton(
                            onClick = { showDividendInfo = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.annual_dividend_info_content_description),
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showDividendInfo,
                            onDismissRequest = { showDividendInfo = false },
                            modifier = Modifier.widthIn(max = 300.dp),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = CardDark,
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
                                                tint = NeonGreen,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.annual_dividend_info_title),
                                        color = OnSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.body
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = BorderBlue, thickness = 0.75.dp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.annual_dividend_info_description),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.caption,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_15) else resources.getString(R.string.annual_dividend_label_16, com.kurogane.dividend.model.formatFinancialAmountDisplay(totalAnnualDividend)),
                    fontSize = AppTextSize.caption,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonGreenLight
                )
            }

            Text(stringResource(R.string.annual_dividend_text_7 , holdings.count { it.dps > 0 && it.dividendMonths.isEmpty() }), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(monthlyChartHeight)
                    .reportViewportVisibility(chartViewportBounds) { monthlyChartVisible = it }
                    .drawBehind {
                    val plotBottom = monthlyChartCanvasHeight.toPx() * monthlyBarAreaBottomFraction
                    val plotHeight = monthlyPlotHeight.toPx()
                    (0..monthlyGridCount).forEach { guideIndex ->
                        val guideAmount = guideIndex * monthlyGridStep
                        val y = plotBottom - plotHeight * (guideAmount / maxMonthlyAmount).toFloat()
                        drawLine(monthlyGridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    selectedMonth?.takeIf { selectedQuarterIndex == null }?.let { month ->
                        val selectedIndex = monthlyDividends.indexOfFirst { it.first == month }
                        if (selectedIndex >= 0) {
                            val groupWidth = size.width / monthlyDividends.size
                            val highlightLeft = groupWidth * (selectedIndex + .05f)
                            val highlightSize = Size(groupWidth * .9f, monthlyChartCanvasHeight.toPx())
                            drawRoundRect(
                                color = monthlySelectionColor.copy(alpha = .08f),
                                topLeft = Offset(highlightLeft, 0f),
                                size = highlightSize,
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                            drawRoundRect(
                                color = monthlySelectionColor.copy(alpha = .35f),
                                topLeft = Offset(highlightLeft, 0f),
                                size = highlightSize,
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style = Stroke(1.dp.toPx())
                            )
                        }
                    }
                },
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyDividends.forEachIndexed { index, (month, amount, label) ->
                    val isSelected = selectedMonth == month
                    val heightFraction = (amount / maxMonthlyAmount).toFloat().coerceIn(0.0f, 1.0f)
                    val stagger = index * monthlyBarStaggerStep
                    val barProgress = ((monthlyBarsProgress.value - stagger) / (1f - stagger)).coerceAtLeast(0f)
                    val animatedHeightFraction = heightFraction * barProgress

                    Column(
                        modifier = Modifier.weight(1f).height(monthlyChartHeight).clickable {
                            selectedQuarterIndex = null
                            viewModel.selectMonth(month)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(monthlyChartCanvasHeight)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(monthlyChartCanvasHeight * monthlyBarAreaBottomFraction)
                                    .align(Alignment.TopCenter),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (animatedHeightFraction > 0f) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 32.dp)
                                            .fillMaxWidth()
                                            .height(monthlyPlotHeight * animatedHeightFraction)
                                            .clip(RoundedCornerShape(4.dp)),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        val categories = monthlyByCategory[month - 1]
                                        listOf(2, 1, 0).forEach { categoryIndex ->
                                            val categoryAmount = categories[categoryIndex]
                                            if (categoryAmount > 0.0 && amount > 0.0) {
                                                val categoryColor = when (categoryIndex) {
                                                    0 -> NeonGreenLight
                                                    1 -> SkyBlue
                                                    else -> ChartBlue
                                                }
                                                Box(
                                                    Modifier.fillMaxWidth()
                                                        .weight((categoryAmount / amount).toFloat())
                                                        .background(Brush.verticalGradient(listOf(categoryColor, categoryColor.copy(alpha = .45f))))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(monthlyAmountLabelSlotHeight)
                                    .align(Alignment.TopCenter)
                                    .offset(y = monthlyChartCanvasHeight * (monthlyBarAreaBottomFraction - animatedHeightFraction * monthlyBarAreaHeightFraction) - monthlyAmountLabelTopOffset),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) PrimaryEmerald else OnSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.height(monthlyAxisGap))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(monthlyAxisLabelHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.annual_dividend_1_s, month),
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = AppTextSize.dense,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) NeonGreenLight else SoftGray,
                                textAlign = TextAlign.Center,
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderBlue, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonGreenLight))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(stringResource(R.string.japanese_stocks), fontSize = AppTextSize.dense, color = SoftGray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(stringResource(R.string.us_stocks), fontSize = AppTextSize.dense, color = SoftGray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ChartBlue))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(stringResource(R.string.funds), fontSize = AppTextSize.dense, color = SoftGray)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = NeonGreen.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, NeonGreen.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = selectedMonth?.let { month ->
                            val amount = monthlyDividends.firstOrNull { it.first == month }?.second ?: 0.0
                            "${month}月: ¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(amount)}"
                        } ?: "月平均: ¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(monthlyDividends.sumOf { it.second } / 12)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = NeonGreenLight,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            QuarterlyCashFlowComparison(
                quarterlyAmounts = quarterlyCashFlowAmounts,
                totalMonthlyAmount = monthlyCashFlowTotal,
                maxMonthSharePercent = maxMonthSharePercent,
                isPrivacyActive = isPrivacyActive,
                currencyFormat = currencyFormat,
                selectedQuarterIndex = selectedQuarterIndex,
                onQuarterSelected = { selectedQuarterIndex = it }
            )
        }
    }
}

@Composable
private fun AnnualDividendHistoryInfo(
    description: String
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.annual_dividend_info_content_description),
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = CardDark,
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
                                tint = NeonGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.annual_dividend_info_title),
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderBlue, thickness = 0.75.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    description,
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.caption,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun QuarterlyCashFlowComparison(
    quarterlyAmounts: List<Double>,
    totalMonthlyAmount: Double,
    maxMonthSharePercent: Double,
    isPrivacyActive: Boolean,
    currencyFormat: NumberFormat,
    selectedQuarterIndex: Int?,
    onQuarterSelected: (Int?) -> Unit
) {
    val quarterColors = listOf(PrimaryEmeraldContainer, SecondaryIndigo, SkyBlue, AccentAmber)
    val maxMonthShareText = if (isPrivacyActive) "•••%" else String.format(Locale.JAPAN, "%.1f%%", maxMonthSharePercent)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, BorderBlue)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.PieChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.annual_dividend_quarterly_cashflow_title),
                    modifier = Modifier.weight(1f),
                    color = OnSurface,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SecondaryIndigo.copy(alpha = 0.14f)
                ) {
                    Text(
                        stringResource(R.string.annual_dividend_quarterly_max_month_share, maxMonthShareText),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = SecondaryIndigo,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth()
                    .height(15.dp)
                    .clip(CircleShape)
                    .background(BorderBlue)
            ) {
                if (totalMonthlyAmount > 0.0) {
                    Row(Modifier.fillMaxSize()) {
                        quarterlyAmounts.forEachIndexed { index, amount ->
                            if (amount > 0.0) {
                                val isSelected = selectedQuarterIndex == index
                                val emphasisProgress by animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    label = "Quarterly cash flow emphasis $index"
                                )
                                Box(
                                    Modifier.weight((amount / totalMonthlyAmount).toFloat())
                                        .fillMaxHeight()
                                        .padding(horizontal = 0.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .height((9f + 6f * emphasisProgress).dp)
                                            .clip(CircleShape)
                                            .background(quarterColors[index])
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quarterlyAmounts.forEachIndexed { index, amount ->
                    val firstMonth = index * 3 + 1
                    val lastMonth = firstMonth + 2
                    val sharePercent = if (totalMonthlyAmount > 0.0) amount / totalMonthlyAmount * 100.0 else 0.0
                    val amountText = if (isPrivacyActive) "¥••••" else "¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(amount)}"
                    val percentageText = if (isPrivacyActive) "•••%" else String.format(Locale.JAPAN, "%.1f%%", sharePercent)

                    Surface(
                        modifier = Modifier.weight(1f).clickable {
                            onQuarterSelected(if (selectedQuarterIndex == index) null else index)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = BackgroundDark,
                        border = BorderStroke(0.5.dp, BorderColor)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.annual_dividend_quarterly_quarter_label, index + 1, firstMonth, lastMonth),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                amountText,
                                color = OnSurface,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppNumericFontFamily,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                percentageText,
                                color = quarterColors[index],
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

data class DonutSliceData(
    val code: String,
    val name: String,
    val expectedDiv: Double,
    val sweepAngle: Float,
    val percentage: String,
    val color: Color
)

data class SectorGroupData(
    val name: String,
    val totalDiv: Double,
    val percentageVal: String,
    val rawPercentage: Float,
    val color: Color,
    val holdings: List<com.kurogane.dividend.model.Holding>
)

private enum class IndustryClimate { CYCLICAL, MIXED, DEFENSIVE }

private val cyclicalIndustries = setOf(
    "鉱業", "建設業", "繊維製品", "パルプ・紙", "石油・石炭製品", "ゴム製品", "ガラス・土石製品",
    "鉄鋼", "非鉄金属", "金属製品", "機械", "電気機器", "輸送用機器", "海運業", "空運業",
    "卸売業", "銀行業", "証券、商品先物取引業", "証券・商品先物", "証券・商品先物取引業", "証券業"
)
private val defensiveIndustries = setOf("食料品", "医薬品", "電気・ガス業", "陸運業", "情報・通信業", "情報・通信", "情報通信")
private val mixedIndustries = setOf(
    "水産・農林業", "化学", "精密機器", "その他製品", "倉庫・運輸関連業", "倉庫・運輸関連",
    "小売業", "保険業", "その他金融業", "不動産業", "サービス業"
)

private fun classifyIndustry(industryName: String?): IndustryClimate? {
    val name = industryName?.trim() ?: return null
    return when {
        name in cyclicalIndustries -> IndustryClimate.CYCLICAL
        name in defensiveIndustries -> IndustryClimate.DEFENSIVE
        name in mixedIndustries -> IndustryClimate.MIXED
        else -> null // ETFs and sectors outside the requested TSE classifications are excluded.
    }
}

private fun classifyStockClimate(climate: com.kurogane.dividend.model.StockClimate?): IndustryClimate? = when (climate) {
    com.kurogane.dividend.model.StockClimate.CYCLICAL -> IndustryClimate.CYCLICAL
    com.kurogane.dividend.model.StockClimate.DEFENSIVE -> IndustryClimate.DEFENSIVE
    com.kurogane.dividend.model.StockClimate.NEUTRAL -> IndustryClimate.MIXED
    null -> null
}

private data class SectorCompositionRow(
    val name: String,
    val holdings: List<com.kurogane.dividend.model.Holding>,
    val dividendTotal: Double,
    val valuationTotal: Double,
    val color: Color
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun CompositionAnalysisSection(holdings: List<com.kurogane.dividend.model.Holding>, taxRateMode: String, totalAssets: Double, totalAnnualDiv: Double, compositionThreshold: Int, compositionOtherEnabled: Boolean, viewModel: DividendViewModel, onNavigateToDetail: (String) -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf("dividend") } // "holding", "dividend", or "yield"
    var sectorValueMode by remember { mutableStateOf("valuation") } // "valuation" or "dividend"
    val context = LocalContext.current
    val industriesByCode = remember(context) { Tse33IndustryMaster.industries(context) }
    val stockClimateOverrides by viewModel.stockClimateOverrides.collectAsStateWithLifecycle()

    fun climateForHolding(holding: com.kurogane.dividend.model.Holding): IndustryClimate? {
        if (holding.category != com.kurogane.dividend.model.Category.JAPAN) return null
        val code = Tse33IndustryMaster.normalizeCode(holding.code) ?: return null
        return classifyStockClimate(stockClimateOverrides[code])
            ?: classifyIndustry(industriesByCode[code])
    }

    LaunchedEffect(chartMode) {
        isVisible = false
        withFrameNanos { }
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "donutAnimation"
    )

    // 1. Group / Map Holdings to expected dividends and valuations in JPY optimized with remember
    val mappedHoldings = remember(holdings, chartMode, taxRateMode) {
        holdings
            .map { h ->
                val rate = if (h.currency == "$") 150.0 else 1.0
                val expectedDiv = h.getEffectiveAnnualDividend(taxRateMode)
                val valuation = h.totalValuation
                Triple(h, expectedDiv, valuation)
            }
            .filter {
                when (chartMode) {
                    "dividend" -> it.second > 0.0
                    "holding" -> it.third > 0.0
                    else -> it.first.liveYield > 0.0
                }
            }
            .sortedByDescending {
                when (chartMode) {
                    "dividend" -> it.second
                    "holding" -> it.third
                    else -> it.first.liveYield
                }
            }
    }

    val totalChartValue = remember(mappedHoldings, chartMode) {
        mappedHoldings.sumOf {
            when (chartMode) {
                "dividend" -> it.second
                "holding" -> it.third
                else -> it.first.liveYield
            }
        }
    }
    val totalPortfolioDividend = remember(mappedHoldings) { mappedHoldings.sumOf { it.second } }

    // Color definitions
    val sliceColors = listOf(
        PrimaryEmeraldContainer, // Emerald
        SkyBlue, // Sky Blue
        SecondaryIndigo, // Indigo
        ErrorRed, // Rose
        AccentAmber, // Amber
        ChartPurple,
        ChartCyan,
        ErrorRed, // Pink
        OnSurfaceVariant  // Gray
    )

    // Build Donut Slice Objects
    val donutSlices = remember(mappedHoldings, chartMode, compositionThreshold, compositionOtherEnabled, totalChartValue, context) {
        val compositionValues = mappedHoldings.map {
            when (chartMode) {
                "dividend" -> it.second
                "holding" -> it.third
                else -> it.first.liveYield
            }
        }
        com.kurogane.dividend.model.PortfolioMath.compositionGroups(compositionValues, compositionThreshold, compositionOtherEnabled)
            .mapIndexed { index, group ->
                val itemVal = group.sumOf { compositionValues[it] }
                val ratio = if (totalChartValue > 0.0) itemVal / totalChartValue else 0.0
                val isOther = compositionOtherEnabled && totalChartValue > 0.0 && group.all { compositionValues[it] / totalChartValue * 100 < compositionThreshold }
                val holding = mappedHoldings[group.first()].first
                DonutSliceData(
                    code = if (isOther) "__other__" else holding.code,
                    name = if (isOther) context.getString(R.string.composition_other, group.size) else holding.name,
                    expectedDiv = itemVal,
                    sweepAngle = (ratio * 360).toFloat(),
                    percentage = String.format(Locale.JAPAN, "%.1f%%", ratio * 100),
                    color = if (isOther) sliceColors.last() else sliceColors[index % sliceColors.size]
                )
            }
    }

    var activeStockCode by remember { mutableStateOf<String?>(null) }

    // Reset active stock selection on load/mode switch to display totals by default
    LaunchedEffect(holdings, chartMode, compositionThreshold, compositionOtherEnabled) {
        activeStockCode = null
    }

    val activeSlice = donutSlices.find { it.code == activeStockCode }

    // 2. Group holdings by sector optimized with remember
    val sectorGroups = remember(holdings, taxRateMode) {
        holdings
            .map { h ->
                val expectedDiv = h.getEffectiveAnnualDividend(taxRateMode)
                h to expectedDiv
            }
            .filter { it.second > 0.0 }
            .groupBy { (holding, _) ->
                val fullSector = holding.sector
                if (fullSector.contains("•")) {
                    fullSector.split("•").last().trim()
                } else if (fullSector.contains("·")) {
                    fullSector.split("·").first().trim()
                } else {
                    fullSector
                }
            }
            .map { (sectorName, list) ->
                val totalDiv = list.sumOf { it.second }
                sectorName to (totalDiv to list.map { it.first })
            }
            .sortedByDescending { it.second.first }
    }

    val totalSectorDividend = remember(sectorGroups) { sectorGroups.sumOf { it.second.first } }

    val formattedSectors = remember(sectorGroups) {
        sectorGroups.mapIndexed { index, (sectorName, pair) ->
        val (totalDiv, secHoldings) = pair
        val ratio = if (totalSectorDividend > 0.0) totalDiv / totalSectorDividend else 0.0
        val percentageVal = String.format(java.util.Locale.JAPAN, "%.1f%%", ratio * 100.0)
        val color = sliceColors[index % sliceColors.size]

        SectorGroupData(
            name = sectorName,
            totalDiv = totalDiv,
            percentageVal = percentageVal,
            rawPercentage = (ratio * 100.0).toFloat(),
            color = color,
            holdings = secHoldings
        )
        }
    }

    val sectorCompositionRows = remember(holdings, taxRateMode) {
        holdings.groupBy { holding ->
            val rawSector = holding.sector
            when {
                rawSector.contains("•") -> rawSector.split("•").last().trim()
                rawSector.contains("·") -> rawSector.split("·").first().trim()
                else -> rawSector.trim().ifBlank { "その他" }
            }
        }.entries.mapIndexed { index, entry ->
            val name = entry.key
            val sectorHoldings = entry.value
            SectorCompositionRow(
                name = name,
                holdings = sectorHoldings,
                dividendTotal = sectorHoldings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) },
                valuationTotal = sectorHoldings.sumOf { it.totalValuation },
                color = sliceColors[index % sliceColors.size]
            )
        }
    }
    val sectorCompositionTotal = sectorCompositionRows.sumOf {
        if (sectorValueMode == "valuation") it.valuationTotal else it.dividendTotal
    }

    // Sectors Expanded Accordion State
    val expandedSectors = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. Donut Composition Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.DonutLarge,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.annual_dividend_text_8),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                        )
                    }

                    // Pill Toggle Switch: 構成比率 / 配当比率 / 利回り率
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHigh)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (chartMode == "holding") PrimaryEmerald else Color.Transparent)
                                .animateContentSize(animationSpec = tween(190))
                                .clickable { chartMode = "holding" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                stringResource(R.string.annual_dividend_text_9),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (chartMode == "holding") OnPrimary else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (chartMode == "dividend") PrimaryEmerald else Color.Transparent)
                                .animateContentSize(animationSpec = tween(190))
                                .clickable { chartMode = "dividend" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                stringResource(R.string.annual_dividend_text_10),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (chartMode == "dividend") OnPrimary else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (chartMode == "yield") PrimaryEmerald else Color.Transparent)
                                .animateContentSize(animationSpec = tween(190))
                                .clickable { chartMode = "yield" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "利回り率",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (chartMode == "yield") OnPrimary else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (donutSlices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.annual_dividend_text_11),
                            style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceVariant)
                        )
                    }
                } else {
                    // Donut Chart Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(160.dp)
                                .pointerInput(donutSlices, activeStockCode) {
                                    detectTapGestures { tap ->
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val dx = tap.x - centerX
                                        val dy = tap.y - centerY
                                        val distance = sqrt(dx * dx + dy * dy)
                                        val ringCenterRadius = minOf(size.width, size.height) / 2f - 12.dp.toPx()
                                        val hitTolerance = 16.dp.toPx()

                                        if (kotlin.math.abs(distance - ringCenterRadius) <= hitTolerance) {
                                            val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                            val chartAngle = ((degrees + 90f) % 360f + 360f) % 360f
                                            var accumulatedAngle = 0f
                                            val tappedSlice = donutSlices.firstOrNull { slice ->
                                                val containsAngle = chartAngle >= accumulatedAngle &&
                                                    chartAngle < accumulatedAngle + slice.sweepAngle
                                                accumulatedAngle += slice.sweepAngle
                                                containsAngle
                                            }
                                            tappedSlice?.let { slice ->
                                                activeStockCode = if (activeStockCode == slice.code) null else slice.code
                                            }
                                        }
                                    }
                                }
                        ) {
                            val normalStrokeWidth = 24.dp.toPx()
                            val activeStrokeWidth = 32.dp.toPx()
                            val centerOffset = Offset(size.width / 2, size.height / 2)

                            var currentStartAngle = -90f

                            donutSlices.forEach { slice ->
                                val isActive = slice.code == activeStockCode
                                val currentStrokeWidth = if (isActive) activeStrokeWidth else normalStrokeWidth
                                val radius = (size.minDimension - currentStrokeWidth) / 2

                                drawArc(
                                    color = slice.color,
                                    startAngle = currentStartAngle,
                                    sweepAngle = slice.sweepAngle * animatedProgress,
                                    useCenter = false,
                                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(currentStrokeWidth, cap = StrokeCap.Butt)
                                )
                                currentStartAngle += slice.sweepAngle
                            }
                        }

                        // Centered Focus text (Default is portfolio totals, toggleable to active stock on selection)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                activeSlice?.name ?: when (chartMode) {
                                    "holding" -> "資産総額"
                                    "yield" -> "利回り構成"
                                    else -> "年間配当合計"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val centerVal = activeSlice?.expectedDiv ?: when (chartMode) {
                                "holding" -> totalAssets
                                "yield" -> if (totalAssets > 0.0) totalPortfolioDividend / totalAssets * 100 else 0.0
                                else -> totalAnnualDiv
                            }
                            Text(
                                text = if (chartMode == "yield") String.format(Locale.JAPAN, "%.2f%%", centerVal)
                                    else stringResource(R.string.add_holding_1_s , NumberFormat.getNumberInstance(Locale.JAPAN).format(centerVal.toLong())),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnSurface,
                                    fontFamily = AppNumericFontFamily,
                                    fontSize = AppTextSize.screenTitle,
                                    shadow = Shadow(
                                        color = Color.White.copy(alpha = 0.9f),
                                        blurRadius = 7f
                                    )
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(activeSlice?.color ?: PrimaryEmerald)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeSlice != null) {
                                        if (chartMode == "yield") "構成比 ${activeSlice.percentage}" else activeSlice.percentage
                                    } else "${mappedHoldings.size}銘柄分散中",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = activeSlice?.color ?: PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.dense
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2-Column Fully Expanded Grid List (Custom non-scrolling grid layout)
                    val chunkedSlices = donutSlices.chunked(2)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunkedSlices.forEach { rowSlices ->
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSlices.forEach { slice ->
                                    val isActive = slice.code == activeStockCode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainerHigh)
                                            .border(
                                                width = if (isActive) 1.5.dp else 1.dp,
                                                color = if (isActive) slice.color else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                activeStockCode = if (activeStockCode == slice.code) null else slice.code
                                            }
                                            .padding(10.dp)
                                    ) {
                                        AdaptiveRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(slice.color)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    slice.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = OnSurface,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                slice.percentage,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = OnSurfaceVariant,
                                                    fontFamily = AppNumericFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                                // Fill missing space for uneven grids
                                if (rowSlices.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. Stacked Sector Percentage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.annual_dividend_text_12),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            stringResource(R.string.annual_dividend_text_13 , formattedSectors.size),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stacked bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh)
                ) {
                    formattedSectors.forEach { sec ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(sec.rawPercentage.coerceAtLeast(0.01f))
                                .background(sec.color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sector Legends Chips (All sectors displayed cleanly without any slice truncation)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    formattedSectors.forEach { sec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(sec.color))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.add_holding_1_s_2_s , sec.name, sec.percentageVal),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamic Category classification helper calculations
                val classifiedHoldings = holdings.filter { climateForHolding(it) != null }
                val totalValue = if (chartMode == "dividend") {
                    classifiedHoldings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) }
                } else {
                    classifiedHoldings.sumOf { it.totalValuation }
                }

                fun valueForClimate(climate: IndustryClimate): Double = classifiedHoldings
                    .filter { climateForHolding(it) == climate }
                    .sumOf {
                        if (chartMode == "dividend") it.getEffectiveAnnualDividend(taxRateMode)
                        else it.totalValuation
                    }

                val cyclicalSum = valueForClimate(IndustryClimate.CYCLICAL)
                val mixedSum = valueForClimate(IndustryClimate.MIXED)
                val defensiveSum = valueForClimate(IndustryClimate.DEFENSIVE)

                val cyclicalPct = if (totalValue > 0.0) (cyclicalSum / totalValue) * 100.0 else 0.0
                val mixedPct = if (totalValue > 0.0) (mixedSum / totalValue) * 100.0 else 0.0
                val defensivePct = if (totalValue > 0.0) (defensiveSum / totalValue) * 100.0 else 0.0

                // Sector Quick Grid Metrics (Cyclical, Mixed, Defensive dynamic cards)
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Box 1: Cyclical
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                stringResource(R.string.annual_dividend_text_14),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                             )
                             Spacer(modifier = Modifier.height(2.dp))
                             Text(
                                 text = String.format(java.util.Locale.JAPAN, "%.1f%%", cyclicalPct),
                                 style = MaterialTheme.typography.bodyMedium.copy(
                                     color = ErrorRed,
                                     fontWeight = FontWeight.Black,
                                     fontFamily = AppNumericFontFamily
                                 )
                             )
                             Text(
                                 stringResource(R.string.annual_dividend_text_15),
                                 style = MaterialTheme.typography.labelSmall.copy(
                                     color = OnSurfaceVariant,
                                     fontSize = AppTextSize.dense
                                 ),
                                 maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                             )
                        }
                    }

                    // Box 2: Mixed
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                stringResource(R.string.annual_dividend_text_16),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(java.util.Locale.JAPAN, "%.1f%%", mixedPct),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SkyBlue,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = AppNumericFontFamily
                                )
                            )
                            Text(
                                stringResource(R.string.annual_dividend_text_17),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                ),
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                            )
                        }
                    }

                    // Box 3: Defensive
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        border = BorderStroke(1.dp, PrimaryEmeraldContainer.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                stringResource(R.string.annual_dividend_text_18),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(java.util.Locale.JAPAN, "%.1f%%", defensivePct),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaryEmeraldContainer,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = AppNumericFontFamily
                                )
                            )
                            Text(
                                stringResource(R.string.annual_dividend_text_19),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                ),
                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                            )
                        }
                    }
                }
            }
        }

        // C. Accordion Sector Details List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.annual_dividend_text_20),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = OnSurface),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh).padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("valuation" to stringResource(R.string.annual_dividend_label), "dividend" to stringResource(R.string.annual_dividend_label_2)).forEach { (mode, title) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sectorValueMode == mode) PrimaryEmerald else Color.Transparent)
                                .clickable { sectorValueMode = mode }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = if (sectorValueMode == mode) OnPrimary else OnSurfaceVariant)
                        }
                    }
                }
            }

            sectorCompositionRows.sortedByDescending {
                if (sectorValueMode == "valuation") it.valuationTotal else it.dividendTotal
            }.forEach { sec ->
                val isOpen = expandedSectors[sec.name] ?: false
                val sectorAmount = if (sectorValueMode == "valuation") sec.valuationTotal else sec.dividendTotal
                val sectorShare = if (sectorCompositionTotal > 0.0) sectorAmount / sectorCompositionTotal * 100.0 else 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AdaptiveRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSectors[sec.name] = !isOpen }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sec.color))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.annual_dividend_text_21 , sec.name, sec.holdings.size),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = OnSurface,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                    modifier = Modifier.weight(1f, fill = false),
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(sec.color.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        String.format(Locale.JAPAN, "%.1f%%", sectorShare),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = sec.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.dense
                                        )
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                    stringResource(R.string.annual_dividend_1_s_2_s , if (sectorValueMode == "dividend") com.kurogane.dividend.model.formatFinancialAmountDisplay(sectorAmount) else NumberFormat.getNumberInstance(Locale.JAPAN).format(sectorAmount.toLong()), if (sectorValueMode == "dividend") " / 年" else ""),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = sec.color,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppNumericFontFamily,
                                        fontSize = AppTextSize.dense
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (isOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceContainerHigh)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sec.holdings.forEach { h ->
                                    val divContribution = h.getEffectiveAnnualDividend(taxRateMode)
                                    val holdingAmount = if (sectorValueMode == "valuation") h.totalValuation else divContribution
                                    val amountShare = if (sectorCompositionTotal > 0.0) holdingAmount / sectorCompositionTotal * 100.0 else 0.0
                                    val currentYield = if (h.price > 0) (h.dps / h.price) * 100 else 0.0

                                    AdaptiveRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfaceContainer)
                                            .border(0.5.dp, BorderBlue, RoundedCornerShape(8.dp))
                                            .clickable { onNavigateToDetail(h.code) }
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(SurfaceContainerHigh),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    h.code,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OnSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    h.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = OnSurface,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                                )
                                                Text(
                                                    stringResource(
                                                        R.string.annual_dividend_text_22,
                                                        h.shares,
                                                        com.kurogane.dividend.model.formatDividendYieldDisplay(
                                                            currentYield,
                                                            h.isNoDividendConfirmed
                                                        )
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OnSurfaceVariant,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                stringResource(R.string.annual_dividend_1_s_2_s , if (sectorValueMode == "dividend") com.kurogane.dividend.model.formatFinancialAmountDisplay(holdingAmount) else NumberFormat.getNumberInstance(Locale.JAPAN).format(holdingAmount.toLong()), if (sectorValueMode == "dividend") " / 年" else ""),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = OnSurface,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = AppNumericFontFamily
                                                )
                                            )
                                            Text(
                                                stringResource(R.string.annual_dividend_text_23) + String.format(Locale.JAPAN, "%.1f%%", amountShare),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = sec.color,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // D. AI Portfolio Health Diagnosis Card
        Spacer(modifier = Modifier.height(16.dp))

        val portfolioDiagnosis = remember(holdings) { PortfolioDiagnosisGenerator.generate(holdings) }
        val score = portfolioDiagnosis.score
        val grade = portfolioDiagnosis.grade
        val power = portfolioDiagnosis.dividendGrowthLabel
        val risk = portfolioDiagnosis.dividendRiskLabel
        val cardColor = when (grade) {
            "S" -> StatusSuccess
            "A" -> PrimaryEmerald
            "B" -> SkyBlue
            "C" -> AccentAmber
            "D" -> ErrorRed
            else -> OnSurfaceVariant
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
            border = BorderStroke(1.dp, BorderBlue)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.annual_dividend_text_24),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = OnSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.body
                                    )
                                )
                                PortfolioDiagnosisInfo()
                            }
                            Text(
                                stringResource(R.string.annual_dividend_text_25),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (score == null) "健全度: 判定不可" else "健全度: $grade ($score/100)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = cardColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = portfolioDiagnosis.narrative,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        lineHeight = 16.sp
                    )
                )

                if (portfolioDiagnosis.missingLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.annual_dividend_text_26 , portfolioDiagnosis.missingLabels.joinToString("、")),
                        color = AccentAmber,
                        fontSize = AppTextSize.dense
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            stringResource(R.string.annual_dividend_text_27),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            power,
                            color = cardColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BackgroundDark)
                            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            stringResource(R.string.annual_dividend_text_28),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            risk,
                            color = cardColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioDiagnosisInfo() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.annual_dividend_portfolio_info_content_description),
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = CardDark,
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
                            Icon(Icons.Default.Info, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(17.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.annual_dividend_portfolio_info_title),
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderBlue, thickness = 0.75.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.annual_dividend_portfolio_info_description),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.caption,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun CompoundingSimulationSection(
    viewModel: DividendViewModel,
    holdings: List<com.kurogane.dividend.model.Holding>,
    taxRateMode: String,
    totalAnnualDiv: Double,
    totalCost: Double,
    isPrivacyActive: Boolean,
    chartViewportBounds: Rect?,
    isTabSelected: Boolean
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    LaunchedEffect(holdings.map { it.code }) { viewModel.refreshAnalysisFinancialDataIfDue() }
    val currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
    val comparisonYears = remember(holdings, financialData, taxRateMode, currentYear) {
        com.kurogane.dividend.model.DividendHistory.calculate(holdings, financialData, (currentYear - 5)..currentYear, taxRateMode)
    }
    val years = remember(comparisonYears, totalAnnualDiv, currentYear) {
        comparisonYears.drop(1).map { year ->
            if (year.year == currentYear) year.copy(amount = totalAnnualDiv) else year
        }
    }
    var annualChartVisible by remember { mutableStateOf(false) }
    val annualBarsProgress = remember { Animatable(1f) }
    LaunchedEffect(isTabSelected) {
        if (!isTabSelected) {
            annualBarsProgress.snapTo(1f)
            return@LaunchedEffect
        }
        annualBarsProgress.snapTo(0f)
        withFrameNanos { }
        if (annualChartVisible) {
            annualBarsProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)
            )
        } else {
            annualBarsProgress.snapTo(1f)
        }
    }
    val format = NumberFormat.getNumberInstance(Locale.JAPAN)
    fun money(value: Double?): String = if (value == null) "未取得" else if (isPrivacyActive) "¥••••" else "¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(value)}"
    fun compactMoney(value: Double): String {
        val absoluteValue = kotlin.math.abs(value)
        return when {
            absoluteValue >= 1_000_000_000_000.0 -> String.format(Locale.JAPAN, "%.1f兆", value / 1_000_000_000_000.0)
            absoluteValue >= 100_000_000.0 -> String.format(Locale.JAPAN, "%.1f億", value / 100_000_000.0)
            absoluteValue >= 10_000.0 -> String.format(Locale.JAPAN, "%.1f万", value / 10_000.0)
            else -> "¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(value)}"
        }
    }
    var selectedYearIndex by remember { mutableIntStateOf(4) }
    val knownYears = years.filter { it.amount != null }
    val firstPoint = knownYears.firstOrNull()
    val latestPoint = knownYears.lastOrNull()
    val spanYears = if (firstPoint != null && latestPoint != null) latestPoint.year - firstPoint.year else 0
    val increaseAmount = if (firstPoint != null && latestPoint != null) latestPoint.amount!! - firstPoint.amount!! else null
    val increaseRate = if (firstPoint != null && latestPoint != null && firstPoint.amount!! > 0.0) {
        (latestPoint.amount!! / firstPoint.amount!! - 1.0) * 100.0
    } else null
    val cagr = if (firstPoint != null && latestPoint != null && firstPoint.amount!! > 0.0 && spanYears > 0) {
        (Math.pow(latestPoint.amount!! / firstPoint.amount!!, 1.0 / spanYears) - 1.0) * 100.0
    } else null
    val averageAnnualIncrease = if (increaseAmount != null && spanYears > 0) increaseAmount / spanYears else null
    val maxValue = years.mapNotNull { it.amount }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val selectedIndex = selectedYearIndex.coerceIn(years.indices)
    val selectedYear = years[selectedIndex]
    fun signedMoney(value: Double?): String = when {
        value == null -> "未取得"
        isPrivacyActive -> "••••"
        value > 0 -> "+¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(value)}"
        value < 0 -> "−¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(kotlin.math.abs(value))}"
        else -> "¥0"
    }
    fun compactSignedMoney(value: Double?): String = when {
        value == null -> "未取得"
        isPrivacyActive -> "••••"
        value > 0 -> "+${compactMoney(value)}"
        value < 0 -> "−${compactMoney(kotlin.math.abs(value))}"
        else -> "¥0"
    }
    fun signedPercent(value: Double?): String = value?.let { String.format(Locale.JAPAN, "%+.2f%%", it) } ?: "未取得"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.annual_dividend_text_29), color = OnSurface, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        AnnualDividendHistoryInfo(stringResource(R.string.annual_dividend_text_32))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = PrimaryEmerald.copy(alpha = .15f)) {
                        Text(stringResource(R.string.annual_dividend_text_31), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(PrimaryEmerald))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.annual_dividend_text_40), color = OnSurface, fontSize = AppTextSize.dense)
                    }
                }
                val chartColor = PrimaryEmerald
                val chartGridColor = BorderColor
                val barAreaBottomFraction = .94f
                val barAreaHeightFraction = .76f
                val targetGridStep = maxValue / 3.0
                val gridMagnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(targetGridStep)))
                val normalizedGridStep = targetGridStep / gridMagnitude
                val gridStepMultiplier = when {
                    normalizedGridStep >= 5.0 -> 5.0
                    normalizedGridStep >= 2.0 -> 2.0
                    else -> 1.0
                }
                val amountGuideStep = gridMagnitude * gridStepMultiplier
                val amountGuideCount = kotlin.math.floor(maxValue / amountGuideStep).toInt()
                Column(Modifier.fillMaxWidth()) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(136.dp)
                        .reportViewportVisibility(chartViewportBounds) { annualChartVisible = it }
                ) {
                val chartHeight = 136.dp
                val labelTopOffset = 24.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale
                val groupWidth = maxWidth / years.size
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .pointerInput(years.size) {
                            detectTapGestures { tap ->
                                if (size.width > 0) {
                                    selectedYearIndex = (tap.x / size.width * years.size)
                                        .toInt()
                                        .coerceIn(years.indices)
                                }
                            }
                        }
                ) {
                    val plotBottom = size.height * barAreaBottomFraction
                    val plotHeight = size.height * barAreaHeightFraction
                    (0..amountGuideCount).forEach { guideIndex ->
                        val guideAmount = guideIndex * amountGuideStep
                        val y = plotBottom - plotHeight * (guideAmount / maxValue).toFloat()
                        drawLine(chartGridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val count = years.size
                    val canvasGroupWidth = size.width / count
                    years.forEachIndexed { index, point ->
                        val centerX = canvasGroupWidth * (index + .5f)
                        val stagger = index * 0.08f
                        val barProgress = ((annualBarsProgress.value - stagger) / (1f - stagger)).coerceAtLeast(0f)
                        val normalizedHeight = point.amount?.let { (it / maxValue).toFloat().coerceIn(0f, 1f) } ?: 0f
                        val barHeight = normalizedHeight * plotHeight * barProgress
                        if (index == selectedIndex) {
                            drawRoundRect(chartColor.copy(alpha = .08f), Offset(centerX - canvasGroupWidth * .45f, 0f), Size(canvasGroupWidth * .9f, size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                            drawRoundRect(chartColor.copy(alpha = .35f), Offset(centerX - canvasGroupWidth * .45f, 0f), Size(canvasGroupWidth * .9f, size.height), cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                        if (barHeight > 0f) {
                            drawRoundRect(Brush.verticalGradient(listOf(chartColor, chartColor.copy(alpha = .45f)), startY = plotBottom - barHeight, endY = plotBottom), Offset(centerX - 16.dp.toPx(), plotBottom - barHeight), Size(32.dp.toPx(), barHeight), cornerRadius = CornerRadius(4.dp.toPx()))
                        }
                    }
                }
                if (!isPrivacyActive) {
                    years.forEachIndexed { index, point ->
                        point.amount?.let { amount ->
                            val stagger = index * 0.08f
                            val barProgress = ((annualBarsProgress.value - stagger) / (1f - stagger)).coerceAtLeast(0f)
                            val normalized = (amount / maxValue).toFloat().coerceIn(0f, 1f) * barProgress
                            Text(
                                text = compactMoney(amount),
                                modifier = Modifier
                                    .offset(
                                        x = groupWidth * (index + 0.5f) - 28.dp,
                                        y = chartHeight * (barAreaBottomFraction - normalized * barAreaHeightFraction) - labelTopOffset
                                    )
                                    .width(56.dp),
                                color = if (index == selectedIndex) PrimaryEmerald else OnSurfaceVariant,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                }
                AdaptiveRow(Modifier.fillMaxWidth()) {
                    years.forEachIndexed { index, point ->
                        Text(point.year.toString(), color = if (index == selectedIndex) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.annual_dividend_text_42), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        years.forEachIndexed { index, point ->
                            val previousAmount = comparisonYears.getOrNull(index)?.amount
                            val yearChange = if (point.amount != null && previousAmount != null) point.amount - previousAmount else null
                            val isSelected = index == selectedIndex
                            Card(Modifier.weight(1f).clickable { selectedYearIndex = index }, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) SurfaceContainerHigh else BackgroundDark), border = BorderStroke(0.5.dp, BorderBlue)) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(stringResource(R.string.annual_dividend_1_s , point.year), color = if (isSelected) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Text(
                                        compactSignedMoney(yearChange),
                                        color = if ((yearChange ?: 0.0) >= 0.0) StatusSuccess else ErrorRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundDark,
                    border = BorderStroke(0.5.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (selectedYear.year == currentYear) {
                                    stringResource(R.string.annual_dividend_text_87, selectedYear.year)
                                } else {
                                    stringResource(R.string.annual_dividend_text_46, selectedYear.year)
                                },
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            AnimatedContent(targetState = money(selectedYear.amount),
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                                label = "Selected year dividend") { amount ->
                                Text(stringResource(R.string.annual_dividend_text_47, amount), color = OnSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (selectedYear.year == currentYear) {
                                    stringResource(R.string.annual_dividend_text_86)
                                } else {
                                    stringResource(R.string.annual_dividend_text_48, selectedYear.covered, selectedYear.expected)
                                },
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            Text(stringResource(R.string.annual_dividend_text_49 , money(selectedYear.amount?.div(12))), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                AdaptiveRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh), border = BorderStroke(0.5.dp, BorderBlue)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.annual_dividend_text_35), color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 2)
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = PrimaryEmerald.copy(alpha = .15f)) {
                                    Text(signedPercent(increaseRate), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            Text(signedMoney(increaseAmount), color = if ((increaseAmount ?: 0.0) >= 0.0) StatusSuccess else ErrorRed, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = AppNumericFontFamily)
                            Text(stringResource(R.string.annual_dividend_text_36, firstPoint?.year?.let { resources.getString(R.string.stock_detail_label_5, it.toString()) } ?: resources.getString(R.string.annual_dividend_label_20), latestPoint?.year?.let { resources.getString(R.string.stock_detail_label_5, it.toString()) } ?: resources.getString(R.string.annual_dividend_label_21)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                    }
                    Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh), border = BorderStroke(0.5.dp, BorderBlue)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.annual_dividend_text_37), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }
                            Text(cagr?.let { String.format(Locale.JAPAN, "%.2f%%", it) } ?: resources.getString(R.string.not_available), color = PrimaryEmerald, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = AppNumericFontFamily)
                            Text(stringResource(R.string.annual_dividend_text_39, signedMoney(averageAnnualIncrease)), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetTrackingSection(
    holdings: List<Holding>,
    totalAssets: Double,
    totalCost: Double,
    totalAnnualDiv: Double,
    currencyFormat: NumberFormat,
    isPrivacyActive: Boolean,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    loadMonthlyPriceHistory: suspend (List<String>, List<String>) -> List<CloudflareMonthlyPriceSeries>
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
    // 1. Dynamic states for user interactivity
    var selectedYearIndex by remember { mutableIntStateOf(4) }
    // Adapt to actual portfolio data or fallback to image defaults if empty
    val realCost = totalCost.coerceAtLeast(0.0)
    val realAssets = totalAssets.coerceAtLeast(0.0)

    val yearLabels = (currentYear - 4..currentYear).toList()
    val yearMonths = yearLabels.map { if (it == currentYear) YearMonth.now(ZoneId.of("Asia/Tokyo")).toString() else "$it-12" }
    val domesticHoldings = holdings.filter { it.category == Category.JAPAN && it.shares > 0 }
    fun normalizedCode(code: String): String = code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let {
        if (it.length == 5 && it.endsWith('0')) it.dropLast(1) else it
    }
    val codes = domesticHoldings.map { normalizedCode(it.code) }.distinct()
    val requestedYearMonths = (yearMonths + yearMonths.map { YearMonth.parse(it).minusMonths(1).toString() }).distinct()
    var annualPrices by remember(codes, yearMonths) { mutableStateOf<List<CloudflareMonthlyPriceSeries>>(emptyList()) }
    var annualHistoryFailed by remember(codes, yearMonths) { mutableStateOf(false) }
    LaunchedEffect(codes, requestedYearMonths, selectedPeriod) {
        if (selectedPeriod == "5y" && codes.isNotEmpty()) {
            com.kurogane.dividend.data.runCatchingCancellable { loadMonthlyPriceHistory(codes, requestedYearMonths) }
                .onSuccess { annualPrices = it; annualHistoryFailed = false }
                .onFailure { annualHistoryFailed = true }
        }
    }
    val priceMap = annualPrices.associate { it.code to it.prices.associateBy { price -> price.month } }
    val domesticCost = domesticHoldings.sumOf { it.totalCost }
    val costs = List(5) { domesticCost }
    fun priceForMonth(code: String, month: String): CloudflareMonthlyPrice? =
        priceMap[normalizedCode(code)]?.let { prices ->
            prices[month] ?: prices[YearMonth.parse(month).minusMonths(1).toString()]
        }
    val assets = yearMonths.map { month ->
        if (domesticHoldings.isEmpty() || domesticHoldings.any { priceForMonth(it.code, month) == null }) Double.NaN
        else domesticHoldings.sumOf { it.shares * priceForMonth(it.code, month)!!.close }
    }
    val usedPreviousMonth = yearMonths.any { month ->
        domesticHoldings.any { holding ->
            val prices = priceMap[normalizedCode(holding.code)]
            prices?.get(month) == null && prices?.get(YearMonth.parse(month).minusMonths(1).toString()) != null
        }
    }
    val gains = assets.map { it - domesticCost }
    val gainsPct = gains.map { if (domesticCost > 0) it / domesticCost * 100 else Double.NaN }
    fun historicalMoney(amount: Double): String = if (amount.isFinite()) currencyFormat.format(amount.toLong()) else "未取得"
    fun historicalPercent(amount: Double): String = if (amount.isFinite()) String.format(Locale.JAPAN, "%.1f%%", amount) else "未取得"
    fun historicalWan(amount: Double): String = if (amount.isFinite()) String.format(Locale.JAPAN, "%.0f万", amount / 10_000) else "未取得"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (annualHistoryFailed) Text(stringResource(R.string.annual_dividend_text_53), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        if (selectedPeriod == "5y" && usedPreviousMonth) Text("一部銘柄は前月末の株価を使用しています。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        // A. Header Title row with switch pills
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (selectedPeriod == "5y") R.string.annual_dividend_text_62
                            else R.string.annual_dividend_text_54
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                }
                Text(
                    if (selectedPeriod == "5y") resources.getString(R.string.annual_dividend_label_22, (currentYear - 4).toString(), (currentYear).toString()) else resources.getString(R.string.annual_dividend_label_23),
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                )
            }

            // Interval selector pills
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerHigh)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedPeriod == "5y") PrimaryEmerald else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedPeriod == "5y") PrimaryEmerald else Color.Transparent),
                    modifier = Modifier.clickable { onPeriodSelected("5y") }
                ) {
                    Text(
                        stringResource(R.string.annual_dividend_text_55),
                        color = if (selectedPeriod == "5y") OnPrimary else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedPeriod == "12m") PrimaryEmerald else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedPeriod == "12m") PrimaryEmerald else Color.Transparent),
                    modifier = Modifier.clickable { onPeriodSelected("12m") }
                ) {
                    Text(
                        stringResource(R.string.annual_dividend_text_56),
                        color = if (selectedPeriod == "12m") OnPrimary else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = if (selectedPeriod == "12m") FontWeight.ExtraBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (selectedPeriod == "5y") {
            Text(
                stringResource(R.string.annual_dividend_text_52),
                color = OnSurfaceVariant,
                fontSize = AppTextSize.caption
            )
        }

        // B. Dual Asset KPI Header Rows
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card Left: Asset Evaluation
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.annual_dividend_text_57), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PrimaryEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                historicalPercent(gainsPct[4]),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyActive) "¥••••••••" else "¥${currencyFormat.format(realAssets.toLong())}",
                        color = OnSurface,
                        fontSize = AppTextSize.section,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppNumericFontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_24) else resources.getString(R.string.annual_dividend_label_25, (currencyFormat.format(realCost.toLong())).toString()),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )
                }
            }

            // Card Right: Total Unrealized Gain
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.annual_dividend_text_58), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SkyBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                stringResource(R.string.annual_dividend_text_59),
                                color = SkyBlue,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyActive) "+¥••••••" else "+¥${historicalMoney(gains[4])}",
                        color = PrimaryEmerald,
                        fontSize = AppTextSize.section,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppNumericFontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.annual_dividend_yield_on_cost) + String.format(java.util.Locale.JAPAN, "%.2f%%", (totalAnnualDiv / realCost) * 100),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )
                }
            }
        }

        if (selectedPeriod == "5y") {
        // C. Double Bar Grouped Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.annual_dividend_text_60), color = OnSurface, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ChartNeutral))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.annual_dividend_text_61), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryEmerald.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_26) else resources.getString(R.string.annual_dividend_label_27, (historicalMoney(gains[4])).toString(), (historicalPercent(gainsPct[4])).toString()),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // custom grouped double bars rendering
                val maxVal = (costs + assets).filter { it.isFinite() }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val assetChartGridColor = BorderColor
                val assetChartAccent = PrimaryEmerald
                val assetChartBarColor = PrimaryEmeraldContainer
                val assetChartNeutralColor = ChartNeutral
                var assetBarsVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { assetBarsVisible = true }
                val assetBarProgress by animateFloatAsState(
                    targetValue = if (assetBarsVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 600),
                    label = "Asset history bars"
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val gridY1 = size.height * 0.25f
                    val gridY2 = size.height * 0.55f
                    val gridY3 = size.height * 0.85f
                    drawLine(color = assetChartGridColor, start = Offset(0f, gridY1), end = Offset(size.width, gridY1))
                    drawLine(color = assetChartGridColor, start = Offset(0f, gridY2), end = Offset(size.width, gridY2))
                    drawLine(color = assetChartGridColor, start = Offset(0f, gridY3), end = Offset(size.width, gridY3))

                    val numYears = 5
                    val groupWidth = 36.dp.toPx()
                    val barWidth = 10.dp.toPx()
                    val spacing = (size.width - (groupWidth * numYears)) / (numYears + 1)

                    for (i in 0 until numYears) {
                        val costVal = costs[i]
                        val assetVal = assets[i]

                        val costRatio = (costVal / maxVal).toFloat()
                        val assetRatio = (assetVal / maxVal).takeIf { it.isFinite() }?.toFloat() ?: 0f

                        val costBarHeight = size.height * costRatio * 0.8f * assetBarProgress
                        val assetBarHeight = size.height * assetRatio * 0.8f * assetBarProgress

                        val groupLeft = spacing + i * (groupWidth + spacing)

                        // If year is selected, draw highlight background
                        if (selectedYearIndex == i) {
                            drawRoundRect(
                                color = assetChartAccent.copy(alpha = 0.08f),
                                topLeft = Offset(groupLeft - 6.dp.toPx(), 0f),
                                size = Size(groupWidth + 12.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            drawRoundRect(
                                color = assetChartAccent.copy(alpha = 0.3f),
                                topLeft = Offset(groupLeft - 6.dp.toPx(), 0f),
                                size = Size(groupWidth + 12.dp.toPx(), size.height),
                                style = Stroke(width = 1.dp.toPx()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }

                        // Cost Bar (Left side of group - Gray)
                        val costBarLeft = groupLeft + 4.dp.toPx()
                        val costBarTop = size.height - costBarHeight
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(assetChartNeutralColor, assetChartNeutralColor.copy(alpha = 0.82f))),
                            topLeft = Offset(costBarLeft, costBarTop),
                            size = Size(barWidth, costBarHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )

                        // Asset Bar (Right side of group - Emerald)
                        val assetBarLeft = costBarLeft + barWidth + 3.dp.toPx()
                        val assetBarTop = size.height - assetBarHeight
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(assetChartBarColor, assetChartBarColor.copy(alpha = 0.3f))),
                            topLeft = Offset(assetBarLeft, assetBarTop),
                            size = Size(barWidth, assetBarHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }

                // X-Axis
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    yearLabels.map { it.toString() }.forEachIndexed { idx, year ->
                        Text(
                            text = year,
                            color = if (selectedYearIndex == idx) PrimaryEmerald else OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = if (selectedYearIndex == idx) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    yearLabels.map { it.toString() }.forEachIndexed { idx, yr ->
                        val isSelected = selectedYearIndex == idx
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedYearIndex = idx },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SurfaceContainerHigh else BackgroundDark
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(yr, color = if (isSelected) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.annual_dividend_text_63) + String.format(java.util.Locale.JAPAN, resources.getString(R.string.annual_dividend_label_28), costs[idx] / 10000.0),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                                Text(
                                    stringResource(R.string.annual_dividend_text_64) + historicalWan(assets[idx]),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = historicalWan(gains[idx]),
                                    color = if (gains[idx] >= 0) StatusSuccess else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // E. Selected Year Summary Card details block
                val selYearStr = "${currentYear - 4 + selectedYearIndex}年末株価比較"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BackgroundDark,
                    border = BorderStroke(1.dp, BorderBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.annual_dividend_text_65 , selYearStr), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_29) else resources.getString(R.string.annual_dividend_label_30, (historicalMoney(assets[selectedYearIndex])).toString()),
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppNumericFontFamily
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_24) else resources.getString(R.string.annual_dividend_label_25, (historicalMoney(costs[selectedYearIndex])).toString()),
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            Text(
                                text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_31) else resources.getString(R.string.annual_dividend_label_32, (historicalMoney(gains[selectedYearIndex])).toString(), (historicalPercent(gainsPct[selectedYearIndex])).toString()),
                                color = if (gains[selectedYearIndex] >= 0) StatusSuccess else ErrorRed,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppNumericFontFamily
                            )
                        }
                    }
                }
            }
        }

        // G. Historical Timeline detailed list (Timeline vertical list at the bottom of Image 2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.annual_dividend_text_77),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                }
                Text(stringResource(R.string.annual_dividend_1_s_2_s_2 , currentYear - 4, currentYear), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                yearLabels.reversed().map { "${it}年 株価比較" }.forEachIndexed { idx, title ->
                    val index = 4 - idx
                    val isGain = gains[index] >= 0
                    val prefix = if (isGain) "+" else ""

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BackgroundDark,
                        border = BorderStroke(1.dp, BorderBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Year circle badge
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.annual_dividend_text_78) + title.take(4).drop(2),
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(title, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (index == 4) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = PrimaryEmeraldContainer.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.annual_dividend_text_79),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = PrimaryEmeraldContainer,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (index > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = OnSurfaceVariant.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.annual_dividend_text_80),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = OnSurfaceVariant,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_41) else resources.getString(R.string.annual_dividend_label_42, (historicalMoney(costs[index])).toString(), (historicalMoney(assets[index])).toString()),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isPrivacyActive) "••••" else String.format(java.util.Locale.JAPAN, "%s¥%s", prefix, historicalMoney(gains[index])),
                                    color = if (isGain) StatusSuccess else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppNumericFontFamily
                                )
                                Text(
                                    text = if (index == 0) resources.getString(R.string.annual_dividend_label_43) else historicalPercent(gainsPct[index]),
                                    color = if (index == 0) OnSurfaceVariant else if (isGain) StatusSuccess else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        } else {
            AssetTrackingTwelveMonthSection(
                holdings = holdings,
                isPrivacyActive = isPrivacyActive,
                currencyFormat = currencyFormat,
                loadMonthlyPriceHistory = loadMonthlyPriceHistory
            )
        }
    }
}

@Composable
private fun AssetTrackingTwelveMonthSection(
    holdings: List<Holding>,
    isPrivacyActive: Boolean,
    currencyFormat: NumberFormat,
    loadMonthlyPriceHistory: suspend (List<String>, List<String>) -> List<CloudflareMonthlyPriceSeries>
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val months = remember {
        val currentMonth = YearMonth.now(ZoneId.of("Asia/Tokyo"))
        (11 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    }
    val targetHoldings = remember(holdings) {
        holdings.filter { holding ->
            holding.category == Category.JAPAN && holding.shares > 0 &&
                holding.code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let {
                    it.matches(Regex("[A-Z0-9]{4}0?"))
                }
        }
    }
    val targetCodes = remember(targetHoldings) {
        targetHoldings.map { it.code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let { code -> if (code.length == 5 && code.endsWith('0')) code.dropLast(1) else code } }.distinct()
    }
    val monthKeys = remember(months) { listOf(months.first().minusMonths(1).toString()) + months.map { it.toString() } }
    var monthlyPriceHistory by remember(targetCodes, monthKeys) { mutableStateOf<Map<String, Map<String, CloudflareMonthlyPrice>>>(emptyMap()) }
    var isHistoryLoading by remember(targetCodes, monthKeys) { mutableStateOf(false) }
    var historyLoadFailed by remember(targetCodes, monthKeys) { mutableStateOf(false) }
    LaunchedEffect(targetCodes, monthKeys) {
        if (targetCodes.isEmpty()) return@LaunchedEffect
        isHistoryLoading = true
        historyLoadFailed = false
        com.kurogane.dividend.data.runCatchingCancellable { loadMonthlyPriceHistory(targetCodes, monthKeys) }
            .onSuccess { series ->
                monthlyPriceHistory = series.associate { row -> row.code to row.prices.associateBy { it.month } }
            }
            .onFailure {
                monthlyPriceHistory = emptyMap()
                historyLoadFailed = true
            }
        isHistoryLoading = false
    }
    val currentCost = targetHoldings.sumOf { it.totalCost }
    var usedPreviousMonth = false
    val monthlySnapshots = remember(targetHoldings, months, monthlyPriceHistory) {
        months.map { month ->
            val prices = targetHoldings.mapNotNull { holding ->
                val code = holding.code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let {
                    if (it.length == 5 && it.endsWith('0')) it.dropLast(1) else it
                }
                val series = monthlyPriceHistory[code]
                val price = series?.get(month.toString()) ?: series?.get(month.minusMonths(1).toString())
                price?.let { holding to it.close }
            }
            val value = if (targetHoldings.isNotEmpty() && prices.size == targetHoldings.size) {
                prices.sumOf { (holding, close) -> holding.shares * close }
            } else null
            MonthAssetSnapshot(
                month = month.monthValue,
                yearMonth = month,
                cost = currentCost,
                value = value,
                gain = value?.minus(currentCost)
            )
        }
    }
    usedPreviousMonth = months.any { month ->
        targetHoldings.any { holding ->
            val code = holding.code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let {
                if (it.length == 5 && it.endsWith('0')) it.dropLast(1) else it
            }
            monthlyPriceHistory[code]?.get(month.toString()) == null &&
                monthlyPriceHistory[code]?.get(month.minusMonths(1).toString()) != null
        }
    }
    val latest = monthlySnapshots.last()
    val validValues = monthlySnapshots.mapNotNull { it.value }
    val axisMin = (listOf(currentCost) + validValues).minOrNull()?.times(0.85) ?: 0.0
    val axisMax = (listOf(currentCost) + validValues).maxOrNull()?.times(1.03)?.coerceAtLeast(axisMin + 1.0) ?: 1.0
    val axisRange = (axisMax - axisMin).coerceAtLeast(1.0)
    val unsupportedCount = holdings.size - targetHoldings.size
    val chartGridColor = BorderColor
    val chartHighlightColor = PrimaryEmerald
    val chartAssetColor = PrimaryEmeraldContainer
    val chartNeutralColor = ChartNeutral
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.annual_dividend_text_81), color = OnSurface, fontSize = AppTextSize.dense)
                    Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(ChartNeutral))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.annual_dividend_text_82), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = when {
                            isPrivacyActive -> resources.getString(R.string.annual_dividend_label_44)
                            latest.gain == null -> if (isHistoryLoading) resources.getString(R.string.annual_dividend_label_45) else resources.getString(R.string.annual_dividend_label_46)
                            else -> resources.getString(R.string.annual_dividend_label_47, (if (latest.gain >= 0) "+" else "−").toString(), (currencyFormat.format(kotlin.math.abs(latest.gain).toLong())).toString())
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = if ((latest.gain ?: 0.0) >= 0) StatusSuccess else ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .padding(horizontal = 3.dp)
            ) {
                val numMonths = monthlySnapshots.size
                val groupWidth = 14.dp.toPx()
                val barWidth = 5.5.dp.toPx()
                val innerGap = 2.dp.toPx()
                val spacing = ((size.width - groupWidth * numMonths) / (numMonths + 1)).coerceAtLeast(0f)
                val chartBottom = size.height - 5.dp.toPx()
                val chartHeight = size.height * 0.9f
                val gridY1 = chartBottom - chartHeight * 0.33f
                val gridY2 = chartBottom - chartHeight * 0.66f
                drawLine(chartGridColor, Offset(0f, gridY1), Offset(size.width, gridY1), strokeWidth = 1.dp.toPx())
                drawLine(chartGridColor, Offset(0f, gridY2), Offset(size.width, gridY2), strokeWidth = 1.dp.toPx())

                monthlySnapshots.forEachIndexed { index, item ->
                    val groupLeft = spacing + index * (groupWidth + spacing)
                    val groupTop = chartBottom - chartHeight
                    if (index == monthlySnapshots.lastIndex) {
                        drawRoundRect(
                            color = chartHighlightColor.copy(alpha = 0.08f),
                            topLeft = Offset(groupLeft - 2.dp.toPx(), groupTop),
                            size = Size(groupWidth + 4.dp.toPx(), chartHeight),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }

                    val costHeight = (chartHeight * ((item.cost - axisMin) / axisRange).toFloat()).coerceAtLeast(1f)
                    val barsWidth = barWidth * 2 + innerGap
                    val barsLeft = groupLeft + (groupWidth - barsWidth) / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(chartNeutralColor, chartNeutralColor.copy(alpha = 0.82f))),
                        topLeft = Offset(barsLeft, chartBottom - costHeight),
                        size = Size(barWidth, costHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    item.value?.let { value ->
                        val valueHeight = (chartHeight * ((value - axisMin) / axisRange).toFloat()).coerceAtLeast(1f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(chartAssetColor, chartAssetColor.copy(alpha = 0.55f))),
                            topLeft = Offset(barsLeft + barWidth + innerGap, chartBottom - valueHeight),
                            size = Size(barWidth, valueHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }
                }
            }

            AdaptiveRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                monthlySnapshots.forEach { item ->
                    Text(
                        text = item.month.toString(),
                        color = if (item == latest) PrimaryEmerald else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = if (item == latest) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.width(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.annual_dividend_text_83), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                Text(if (isHistoryLoading) stringResource(R.string.annual_dividend_label_3) else stringResource(R.string.annual_dividend_label_4), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                monthlySnapshots.filterIndexed { index, _ -> (index + 1) % 3 == 0 }.forEach { item ->
                    val isLatest = item == latest
                    val gainColor = if ((item.gain ?: 0.0) >= 0) StatusSuccess else ErrorRed
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLatest) PrimaryEmerald.copy(alpha = 0.12f) else BackgroundDark)
                            .border(
                                1.dp,
                                if (isLatest) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isLatest) resources.getString(R.string.annual_dividend_label_48, (item.month).toString()) else resources.getString(R.string.ex_dividend_matrix_dialog_text_4, (item.month).toString()),
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isLatest) PrimaryEmerald else OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                            textAlign = TextAlign.Center,
                            softWrap = false
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = if (isPrivacyActive) resources.getString(R.string.annual_dividend_label_50) else resources.getString(R.string.annual_dividend_label_51, (formatAssetWan(item.cost)).toString()),
                            modifier = Modifier.fillMaxWidth(),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                            textAlign = TextAlign.Center,
                            softWrap = false
                        )
                        Text(
                            text = when {
                                isPrivacyActive -> resources.getString(R.string.annual_dividend_label_52)
                                item.value == null -> resources.getString(R.string.annual_dividend_label_53)
                                else -> resources.getString(R.string.annual_dividend_label_54, (formatAssetWan(item.value)).toString())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isLatest) PrimaryEmerald else OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                            textAlign = TextAlign.Center,
                            softWrap = false
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                isPrivacyActive -> "••••"
                                item.gain == null -> resources.getString(R.string.annual_dividend_label_55)
                                else -> "${if (item.gain >= 0) "+" else "−"}${formatAssetWan(kotlin.math.abs(item.gain))}"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = gainColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                            textAlign = TextAlign.Center,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append(resources.getString(R.string.annual_dividend_label_56))
                    if (unsupportedCount > 0) append(resources.getString(R.string.annual_dividend_label_57, (unsupportedCount).toString()))
                    if (usedPreviousMonth) append(" 一部銘柄は前月末の株価を使用しています。")
                    if (historyLoadFailed) append(resources.getString(R.string.annual_dividend_label_58))
                    else if (targetHoldings.isEmpty()) append(resources.getString(R.string.annual_dividend_label_59))
                    else if (!isHistoryLoading && monthlySnapshots.none { it.value != null }) append(resources.getString(R.string.annual_dividend_label_60))
                },
                color = OnSurfaceVariant,
                fontSize = AppTextSize.dense
            )
        }
    }
}

private fun formatAssetWan(amount: Double): String {
    val amountInWan = amount / 10_000.0
    val formatted = String.format(Locale.JAPAN, "%.1f", amountInWan).removeSuffix(".0")
    return "${formatted}万"
}

private data class MonthAssetSnapshot(
    val month: Int,
    val yearMonth: YearMonth,
    val cost: Double,
    val value: Double?,
    val gain: Double?
)
