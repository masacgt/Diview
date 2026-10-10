package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Local High-Fidelity Color Theme matching React Web App CSS
val SpaceNavy: Color @Composable get() = BackgroundDark
val CardDark: Color @Composable get() = SurfaceContainerLow
val CardDarkLighter: Color @Composable get() = SurfaceContainer
val NeonGreen: Color @Composable get() = PrimaryEmeraldContainer
val NeonGreenLight: Color @Composable get() = PrimaryEmerald
val SoftGray: Color @Composable get() = OnSurfaceVariant
val BorderBlue: Color @Composable get() = BorderColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {},
    onOpenHoldingRegistration: (String) -> Unit = onOpenAddHolding
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()

    // Sorting state
    var sortCriteria by rememberSaveable { mutableStateOf("val") } // "val", "gain", "div", "div_amt", "code"
    var sortDirection by rememberSaveable { mutableStateOf("desc") } // "desc", "asc"
    var showSortModal by remember { mutableStateOf(false) }

    // Dialog / Modal State
    var showAddAssetModal by remember { mutableStateOf(false) }
    var stockCode by remember { mutableStateOf("") }
    var shareCount by remember { mutableStateOf("") }
    var acqPrice by remember { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var quickEditHolding by remember { mutableStateOf<Holding?>(null) }
    var revealedHoldingId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    val deleteActionWidth = 80.dp
    val deleteActionWidthPx = with(LocalDensity.current) { deleteActionWidth.toPx() }

    fun deleteHolding(holding: Holding) {
        revealedHoldingId = null
        if (!viewModel.removeHolding(holding.id)) return
        screenScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${holding.name}を保有銘柄から削除しました",
                actionLabel = "元に戻す",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreHolding(holding)
        }
    }

    val javaCurrencyFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val categoryCounts = remember(holdings) { holdings.groupingBy { it.category }.eachCount() }
    val hasMissingExchangeRate = remember(holdings) {
        holdings.any { it.currency == "$" && it.usdJpyRate == null }
    }

    // Dynamic calculations from holdings optimized using remember to prevent recomposition overhead
    val totalAssetsVal = remember(holdings) { holdings.sumOf { it.totalValuation } }
    val totalCostVal = remember(holdings) { holdings.sumOf { it.totalCost } }
    val totalGainVal = remember(totalAssetsVal, totalCostVal) { totalAssetsVal - totalCostVal }
    val gainPctVal = remember(totalGainVal, totalCostVal) { if (totalCostVal > 0.0) (totalGainVal / totalCostVal) * 100.0 else 0.0 }
    val totalAnnualDivVal = remember(holdings, taxRateMode) { holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) } }
    val netAnnualDivVal = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("net") } }
    val calculationBasis by viewModel.calculationBasis.collectAsStateWithLifecycle()
    val avgYield = com.kurogane.dividend.model.PortfolioMath.portfolioYield(totalAnnualDivVal, totalAssetsVal, totalCostVal, calculationBasis)

    // Dynamic Annual Dividend Target based on currently active roadmap step optimized with remember
    val totalAnnualExpectedDividendForRoadmap = remember(holdings, taxRateMode) {
        holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode).toLong() }
    }
    val monthlyIncomeForRoadmap = remember(totalAnnualExpectedDividendForRoadmap) { totalAnnualExpectedDividendForRoadmap / 12 }

    val roadmapMonthlyTargets = remember {
        listOf(
            1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 8_000, 10_000, 15_000, 20_000,
            25_000, 30_000, 35_000, 40_000, 45_000, 50_000, 60_000, 70_000, 80_000, 100_000,
            120_000, 150_000, 180_000, 200_000, 250_000, 300_000, 350_000, 400_000, 500_000, 600_000
        )
    }
    val currentMonthlyTarget = remember(monthlyIncomeForRoadmap, roadmapMonthlyTargets) {
        roadmapMonthlyTargets.firstOrNull { monthlyIncomeForRoadmap < it } ?: roadmapMonthlyTargets.last()
    }
    val currentAnnualTarget = currentMonthlyTarget * 12

    // Category and month filtering optimized with remember and search filtering
    val filteredHoldings = remember(holdings, selectedMonth, selectedCategory, searchQuery) {
        holdings.filter { h ->
            val monthMatch = selectedMonth == null || h.dividendMonths.contains(selectedMonth)
            val catMatch = selectedCategory == null || h.category == selectedCategory
            val queryMatch = searchQuery.isEmpty() ||
                    h.code.contains(searchQuery, ignoreCase = true) ||
                    h.name.contains(searchQuery, ignoreCase = true) ||
                    h.sector.contains(searchQuery, ignoreCase = true)
            monthMatch && catMatch && queryMatch
        }
    }

    // Sorted holdings optimized with remember
    val displayedHoldings = remember(filteredHoldings, sortCriteria, sortDirection, taxRateMode, calculationBasis) {
        filteredHoldings.sortedWith { a, b ->
            val comp = when (sortCriteria) {
                "val" -> a.totalValuation.compareTo(b.totalValuation)
                "gain" -> a.gainLossPercent.compareTo(b.gainLossPercent)
                "div" -> (if (calculationBasis == "cost") a.liveYoc else a.liveYield).compareTo(if (calculationBasis == "cost") b.liveYoc else b.liveYield)
                "div_amt" -> a.getEffectiveAnnualDividend(taxRateMode).compareTo(b.getEffectiveAnnualDividend(taxRateMode))
                "code" -> a.code.compareTo(b.code)
                else -> a.totalValuation.compareTo(b.totalValuation)
            }
            if (sortDirection == "desc") -comp else comp
        }
    }

    val badgeText = when {
        avgYield >= 4.0 -> resources.getString(R.string.asset_management_label_5)
        avgYield >= 3.0 -> resources.getString(R.string.asset_management_label_20)
        avgYield >= 2.0 -> resources.getString(R.string.asset_management_label_21)
        avgYield >= 0.5 -> resources.getString(R.string.asset_management_label_22)
        else -> resources.getString(R.string.asset_management_label_23)
    }

    val badgeIcon = when {
        avgYield >= 4.0 -> Icons.Default.Bolt
        avgYield >= 3.0 -> Icons.Default.CheckCircle
        avgYield >= 2.0 -> Icons.Default.Star
        else -> Icons.Default.Info
    }

    val badgeColor = when {
        avgYield >= 4.0 -> SecondaryIndigo // Purple
        avgYield >= 3.0 -> NeonGreen // Emerald
        avgYield >= 2.0 -> if (BackgroundDark == LightAppPalette.background) Color(0xFF1D4ED8) else Color(0xFF60A5FA) // Blue
        avgYield >= 0.5 -> AccentAmber // Amber
        else -> SoftGray // Slate
    }

    fun getSortLabel(): String {
        val dir = if (sortDirection == "desc") resources.getString(R.string.asset_management_label_24) else resources.getString(R.string.asset_management_label_25)
        return when (sortCriteria) {
            "val" -> resources.getString(R.string.asset_management_label_26, (dir).toString())
            "gain" -> resources.getString(R.string.asset_management_label_27, (dir).toString())
            "div" -> resources.getString(R.string.asset_management_label_28, (dir).toString())
            "div_amt" -> resources.getString(R.string.asset_management_label_29, (dir).toString())
            "code" -> resources.getString(R.string.asset_management_label_30, (dir).toString())
            else -> resources.getString(R.string.asset_management_label_26, (dir).toString())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(56.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonGreen, ambientColor = NeonGreen)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonGreenLight, NeonGreen)))
                    .clickable { onOpenAddHolding("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.asset_management_add_asset), tint = OnPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        },
        containerColor = SpaceNavy
    ) { innerPadding ->
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = viewModel.assetScrollIndex,
            initialFirstVisibleItemScrollOffset = viewModel.assetScrollOffset
        )
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewModel.assetScrollIndex = index
                    viewModel.assetScrollOffset = offset
                }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("asset_management_list"),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (hasMissingExchangeRate) {
                item(key = "exchange_rate_warning", contentType = "warning") {
                    Text(stringResource(R.string.asset_management_text), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                }
            }
            // ================= TOTAL ASSET HERO CARD =================
            item(key = "asset_summary", contentType = "summary") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.asset_management_text_2), fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(R.string.asset_management_privacy),
                                    tint = SoftGray,
                                    modifier = Modifier.size(16.dp).clickable { viewModel.togglePrivacy() }
                                )
                            }
                            // Profit Pill
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x1F10B981))
                                    .border(0.5.dp, Color(0x3310B981), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    val gainText = if (isPrivacyActive) "+¥••••••" else {
                                        val sign = if (totalGainVal >= 0) "+" else ""
                                        "$sign¥${javaCurrencyFormat.format(totalGainVal.toLong())} ($sign${String.format(java.util.Locale.JAPAN, "%.1f", gainPctVal)}%)"
                                    }
                                    Text(gainText, fontSize = AppTextSize.dense, fontWeight = FontWeight.ExtraBold, color = if (totalGainVal >= 0) NeonGreen else ErrorRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isPrivacyActive) "¥ •,•••,•••,•••" else "¥${javaCurrencyFormat.format(totalAssetsVal.toLong())}",
                            fontSize = AppTextSize.hero,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnSurface,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_3) else resources.getString(R.string.annual_dividend_label_25, (javaCurrencyFormat.format(totalCostVal.toLong())).toString()),
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.Medium,
                            color = SoftGray
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderBlue, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Sub-Grid 2 Columns
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Expected Dividend Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceContainer)
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(stringResource(R.string.asset_management_text_3), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_4) else resources.getString(R.string.add_holding_text_22, (javaCurrencyFormat.format(totalAnnualDivVal.toLong())).toString()),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NeonGreen
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_6) else resources.getString(R.string.asset_management_label_7, (javaCurrencyFormat.format(netAnnualDivVal.toLong())).toString()),
                                        fontSize = AppTextSize.dense,
                                        color = SoftGray
                                    )
                                }
                            }

                            // Dividend Yield Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceContainer)
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(stringResource(R.string.asset_management_text_4), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = if (isPrivacyActive) "•••" else String.format(java.util.Locale.JAPAN, "%.2f", avgYield),
                                            fontSize = AppTextSize.headline,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OnSurface
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(stringResource(R.string.annual_dividend_text_4), fontSize = AppTextSize.dense, color = SoftGray, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(badgeText, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black, color = badgeColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= GOAL PROGRESS CARD =================
            item(key = "dividend_goal", contentType = "goal") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val progressPct = ((totalAnnualDivVal / currentAnnualTarget.toDouble()) * 100).coerceAtLeast(0.0)
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.asset_management_text_5 , javaCurrencyFormat.format(currentAnnualTarget)), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                            }
                            Text(String.format(java.util.Locale.JAPAN, "%.1f%%", progressPct), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = NeonGreen)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (progressPct / 100f).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = NeonGreen,
                            trackColor = BorderColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_8) else resources.getString(R.string.asset_management_label_9, (javaCurrencyFormat.format(totalAnnualDivVal.toLong())).toString()),
                                fontSize = AppTextSize.dense,
                                color = SoftGray
                            )
                            val shortage = currentAnnualTarget.toDouble() - totalAnnualDivVal
                            Text(
                                text = if (shortage > 0) {
                                    if (isPrivacyActive) {
                                        val formattedTarget = if (currentMonthlyTarget >= 10000) resources.getString(R.string.asset_management_label_10, (currentMonthlyTarget / 10000).toString()) else resources.getString(R.string.asset_management_label_11, (javaCurrencyFormat.format(currentMonthlyTarget)).toString())
                                        resources.getString(R.string.asset_management_label_12, (formattedTarget).toString())
                                    } else {
                                        resources.getString(R.string.asset_management_label_13, (javaCurrencyFormat.format(shortage.toLong())).toString())
                                    }
                                } else resources.getString(R.string.asset_management_label_14),
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                color = if (shortage > 0) ErrorRed else NeonGreen
                            )
                        }
                    }
                }

            }

            // ================= HOLDINGS LIST HEADER & SORT =================
            item(key = "holdings_header", contentType = "header") {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedMonth != null) resources.getString(R.string.asset_management_label_15, (selectedMonth).toString()) else resources.getString(R.string.asset_management_label_16),
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = CircleShape, color = SurfaceContainerHigh) {
                            Text(
                                stringResource(R.string.asset_management_text_6 , displayedHoldings.size),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = NeonGreenLight,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selectedMonth != null) {
                            Text(
                                stringResource(R.string.asset_management_text_7),
                                color = NeonGreenLight,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.selectMonth(selectedMonth!!) }
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CardDarkLighter,
                            border = BorderStroke(0.5.dp, BorderBlue),
                            modifier = Modifier.clickable { showSortModal = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getSortLabel(), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

            }
            // ================= SEARCH FIELD =================
            item(key = "holdings_search", contentType = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.asset_management_text_8), color = SoftGray, fontSize = AppTextSize.dense) },
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
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen.copy(alpha = 0.5f),
                        unfocusedBorderColor = BorderBlue,
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )

            }
            // ================= CATEGORY FILTER CHIPS =================
            item(key = "holdings_categories", contentType = "categories") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chips = listOf(
                        Pair(null, "すべて (${holdings.size})"),
                        Pair(Category.JAPAN, "日本株 (${categoryCounts[Category.JAPAN] ?: 0})"),
                        Pair(Category.US, "米国株 (${categoryCounts[Category.US] ?: 0})"),
                        Pair(Category.FUND, "投信 (${categoryCounts[Category.FUND] ?: 0})")
                    )
                    chips.forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) NeonGreen else CardDarkLighter,
                            border = BorderStroke(0.5.dp, if (isSelected) NeonGreen else BorderBlue),
                            modifier = Modifier.clickable { viewModel.selectCategory(cat) }
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = if (isSelected) OnPrimaryContainer else SoftGray,
                                fontSize = AppTextSize.dense,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        }
                    }
                }

            }
            // ================= HOLDING STOCK CARDS LIST =================
            if (displayedHoldings.isEmpty()) {
                item(key = "empty_holdings", contentType = "empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderBlue)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = SoftGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.asset_management_text_9),
                                modifier = Modifier.fillMaxWidth(),
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                if (selectedMonth != null) resources.getString(R.string.asset_management_label_17, (selectedMonth).toString()) else resources.getString(R.string.asset_management_label_18),
                                color = SoftGray,
                                fontSize = AppTextSize.dense,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(displayedHoldings, key = { "holding:${it.id}" }, contentType = { "holding" }) { holding ->
                    val isUSD = holding.currency == "$"
                    val curVal = holding.totalValuation
                    val curCost = holding.totalCost
                    val diff = holding.gainLoss
                    val diffPct = holding.gainLossPercent
                    val annualDiv = holding.getEffectiveAnnualDividend(taxRateMode)
                    val dividends = financialData[holding.code]?.dividends
                    val latestDividendPeriod = remember(dividends) {
                        dividends?.zipWithNext()
                            ?.lastOrNull { (previous, current) -> current.year == previous.year + 1 }
                    }
                    val latestDividendChange = latestDividendPeriod?.let { (previous, current) -> current.dps - previous.dps }
                    val latestDividendGrowth = latestDividendPeriod?.let { (previous, current) ->
                        if (previous.dps > 0.0) (current.dps - previous.dps) / previous.dps * 100.0 else null
                    }
                    val annualDividendChange = latestDividendChange?.let {
                        holding.shares * it / holding.priceUnit * holding.currencyFactor
                    }
                    val payoutMonthSummary = remember(holding.dividendMonths) {
                        holding.dividendMonths.sorted()
                            .joinToString("・") { "${it}月" }
                            .ifEmpty { "配当月未取得" }
                    }
                    val swipeOffset = remember(holding.id) { Animatable(0f) }
                    LaunchedEffect(revealedHoldingId, holding.id) {
                        if (revealedHoldingId != holding.id) swipeOffset.animateTo(0f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.matchParentSize(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                onClick = { deleteHolding(holding) },
                                modifier = Modifier.width(deleteActionWidth).fillMaxHeight(),
                                shape = RoundedCornerShape(0.dp),
                                color = ErrorRed
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                    Text("削除", color = Color.White, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                                .pointerInput(holding.id, deleteActionWidthPx) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            if (revealedHoldingId != holding.id) {
                                                revealedHoldingId = null
                                                screenScope.launch { swipeOffset.snapTo(0f) }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            screenScope.launch {
                                                swipeOffset.snapTo(
                                                    (swipeOffset.value + dragAmount).coerceIn(-deleteActionWidthPx, 0f)
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            val shouldReveal = swipeOffset.value <= -deleteActionWidthPx / 2f
                                            screenScope.launch {
                                                swipeOffset.animateTo(if (shouldReveal) -deleteActionWidthPx else 0f)
                                                revealedHoldingId = if (shouldReveal) holding.id else null
                                            }
                                        },
                                        onDragCancel = {
                                            screenScope.launch { swipeOffset.animateTo(0f) }
                                            if (revealedHoldingId == holding.id) revealedHoldingId = null
                                        }
                                    )
                                }
                                .clickable { onNavigateToDetail(holding.code) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            border = BorderStroke(
                                1.dp,
                                if (selectedMonth != null) NeonGreenLight.copy(alpha = 0.5f) else BorderBlue
                            )
                        ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Code, Name, Sector, Valuation & Gain
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            holding.name,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface,
                                            fontSize = AppTextSize.body,
                                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (holding.category) {
                                                    Category.US -> SecondaryIndigo.copy(alpha = 0.14f)
                                                    Category.FUND -> ErrorRed.copy(alpha = 0.14f)
                                                    else -> BorderColor
                                                }
                                            ) {
                                                Text(
                                                    holding.code,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    color = when (holding.category) {
                                                        Category.US -> SecondaryIndigo
                                                        Category.FUND -> ErrorRed
                                                        else -> OnSurface
                                                    },
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                                )
                                            }
                                            if (holding.accountType.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(shape = RoundedCornerShape(4.dp), color = BorderColor) {
                                                    Text(
                                                        compactAccountLabel(holding.accountType),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                        color = SecondaryIndigo,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                holding.sector,
                                                modifier = Modifier.weight(1f),
                                                color = SoftGray,
                                                fontSize = AppTextSize.dense,
                                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        stringResource(R.string.add_holding_1_s , javaCurrencyFormat.format(curVal.toLong())),
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val sign = if (diff >= 0) "+" else ""
                                    Text(
                                        stringResource(R.string.asset_management_1_s_2_s_3_s_4_s , sign, javaCurrencyFormat.format(diff.toLong()), sign, String.format(java.util.Locale.JAPAN, "%.1f", diffPct)),
                                        fontWeight = FontWeight.Bold,
                                        color = if (diff >= 0) NeonGreenLight else ErrorRed,
                                        fontSize = AppTextSize.dense,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = BorderBlue, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Four compact metric tiles matching the portfolio card design.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDarkLighter)
                                    .padding(horizontal = 4.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val latestChangeColor = when {
                                    latestDividendChange == null -> SoftGray
                                    latestDividendChange >= 0.0 -> NeonGreenLight
                                    else -> ErrorRed
                                }
                                HoldingMetricTile(
                                    label = resources.getString(R.string.asset_management_label_31),
                                    value = "${String.format(java.util.Locale.JAPAN, "%.2f", if (calculationBasis == "cost") holding.liveYoc else holding.liveYield)}%",
                                    detail = if (calculationBasis == "cost") resources.getString(R.string.yield_basis_cost_detail) else "現在株価比",
                                    modifier = Modifier.weight(1f),
                                    valueColor = NeonGreenLight
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = resources.getString(R.string.asset_management_label_32),
                                    value = if (annualDiv > 0) "¥${javaCurrencyFormat.format(annualDiv.toLong())}/年" else "再投資型",
                                    detail = payoutMonthSummary,
                                    modifier = Modifier.weight(1f)
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = resources.getString(R.string.asset_management_label_33),
                                    value = latestDividendGrowth?.let {
                                        String.format(java.util.Locale.JAPAN, "%+.1f%%", it)
                                    } ?: "未取得",
                                    detail = annualDividendChange?.let {
                                        val sign = if (it > 0) "+" else if (it < 0) "−" else ""
                                        "年額 $sign¥${javaCurrencyFormat.format(kotlin.math.abs(it).toLong())}"
                                    } ?: "履歴なし",
                                    modifier = Modifier.weight(1f),
                                    valueColor = latestChangeColor,
                                    detailColor = latestChangeColor
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = resources.getString(R.string.csv_import_label_5),
                                    value = "${holding.shares}株",
                                    detail = "@${holding.currency}${javaCurrencyFormat.format(holding.price.toLong())}",
                                    modifier = Modifier.weight(1f),
                                    showEditHint = true,
                                    onClick = {
                                        onOpenHoldingRegistration(holding.code)
                                    }
                                )
                            }

                            // Month Highlight Banner if selected
                            if (selectedMonth != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardDarkLighter,
                                    border = BorderStroke(0.5.dp, NeonGreenLight.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.asset_management_text_10, selectedMonth.toString()), color = SoftGray, fontSize = AppTextSize.dense)
                                        }
                                        Text(holding.nextPayoutAmount, color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                        }
                        }
                    }
                }
            }

            // ================= NEXT DIVIDEND PAYOUT STRIP (100% DYNAMIC) =================
            item(key = "next_dividend", contentType = "payout") {
                val currentMonthNum = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
                val validHoldings = holdings.filter { it.dividendMonths.isNotEmpty() && it.shares > 0 }
                var nextStock = validHoldings.firstOrNull()
                var nextMonth = 12
                var minDiff = 999
                validHoldings.forEach { h ->
                    h.dividendMonths.forEach { m ->
                        var diff = m - currentMonthNum
                        if (diff < 0) diff += 12
                        if (diff < minDiff) {
                            minDiff = diff
                            nextStock = h
                            nextMonth = m
                        }
                    }
                }
                val nextDivAmt = if (nextStock != null) {
                    val rate = nextStock.currencyFactor
                    val monthsCount = if (nextStock.dividendMonths.isNotEmpty()) nextStock.dividendMonths.size else 2
                    ((nextStock.shares * nextStock.dps * rate) / monthsCount).toLong()
                } else 0L

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(stringResource(R.string.asset_management_text_11), color = SoftGray, fontSize = AppTextSize.dense)
                                Text(if (nextStock != null) stringResource(R.string.asset_management_label, (nextMonth).toString(), (nextStock!!.name).toString()) else stringResource(R.string.asset_management_label_2), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.caption)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.add_holding_1_s , javaCurrencyFormat.format(nextDivAmt)), fontWeight = FontWeight.Bold, color = NeonGreenLight, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                            Text(stringResource(R.string.asset_management_text_12 , nextStock?.dividendType ?: resources.getString(R.string.asset_management_label_19), nextStock?.shares ?: 0), color = SoftGray, fontSize = AppTextSize.dense)
                        }
                    }
                }

            }
            item(key = "bottom_spacing", contentType = "spacing") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ================= SORT MODAL BOTTOM SHEET =================
    if (showSortModal) {
        AlertDialog(
            onDismissRequest = { showSortModal = false },
            confirmButton = {
                Button(
                    onClick = { showSortModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.apply), color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortModal = false }) {
                    Text(stringResource(R.string.close), color = SoftGray, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Text(stringResource(R.string.asset_management_text_13), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val criteriaOptions = listOf(
                        Pair("val", resources.getString(R.string.annual_dividend_label)),
                        Pair("gain", resources.getString(R.string.asset_management_label_34)),
                        Pair("div", resources.getString(R.string.asset_management_label_31)),
                        Pair("div_amt", resources.getString(R.string.asset_management_label_35)),
                        Pair("code", resources.getString(R.string.dividend_change_analysis_cards_label_12))
                    )
                    criteriaOptions.forEach { (key, label) ->
                        AdaptiveRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sortCriteria == key) CardDarkLighter else Color.Transparent)
                                .clickable { sortCriteria = key }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = if (sortCriteria == key) NeonGreenLight else OnSurface, fontSize = AppTextSize.body, fontWeight = if (sortCriteria == key) FontWeight.Bold else FontWeight.Normal)
                            if (sortCriteria == key) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = BorderBlue, thickness = 0.5.dp)
                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { sortDirection = "desc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "desc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.asset_management_text_14), color = if (sortDirection == "desc") OnPrimaryContainer else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { sortDirection = "asc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "asc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.asset_management_text_15), color = if (sortDirection == "asc") OnPrimaryContainer else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, BorderBlue, RoundedCornerShape(16.dp))
        )
    }

    // ================= ADD ASSET MODAL DIALOG =================
    if (showAddAssetModal) {
        AlertDialog(
            onDismissRequest = { showAddAssetModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (stockCode.isNotEmpty() && shareCount.isNotEmpty() && acqPrice.isNotEmpty()) {
                            val shares = shareCount.toIntOrNull() ?: 100
                            val price = acqPrice.toDoubleOrNull() ?: 2500.0
                            viewModel.updateHolding(stockCode, shares, price)
                            showAddAssetModal = false
                            stockCode = ""
                            shareCount = ""
                            acqPrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.asset_management_text_16), color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAssetModal = false }) {
                    Text(stringResource(R.string.cancel), color = SoftGray, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.asset_management_text_17), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.asset_management_text_18), color = SoftGray, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                    OutlinedTextField(
                        value = stockCode,
                        onValueChange = { stockCode = it },
                        label = { Text(stringResource(R.string.asset_management_text_19), fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (stockCode.isNotEmpty()) {
                                IconButton(onClick = { stockCode = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = shareCount,
                        onValueChange = { shareCount = it },
                        label = { Text(stringResource(R.string.asset_management_text_20), fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (shareCount.isNotEmpty()) {
                                IconButton(onClick = { shareCount = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = acqPrice,
                        onValueChange = { acqPrice = it },
                        label = { Text(stringResource(R.string.asset_management_text_21), fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (acqPrice.isNotEmpty()) {
                                IconButton(onClick = { acqPrice = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, BorderBlue, RoundedCornerShape(20.dp))
        )
    }

    // ================= HOLDING QUICK EDIT BOTTOM SHEET =================
    quickEditHolding?.let { holding ->
        HoldingQuickEditModal(holding, viewModel) { quickEditHolding = null }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HoldingQuickEditModal(
    holding: Holding,
    viewModel: DividendViewModel,
    onDismiss: () -> Unit
) {
    var quickEditShares by remember(holding.id) { mutableStateOf(holding.shares.toString()) }
    var quickEditAcqPrice by remember(holding.id) { mutableStateOf(holding.acqPrice.toString().removeSuffix(".0")) }
    var quickEditMarketPrice by remember(holding.id) { mutableDoubleStateOf(holding.price) }
    val javaCurrencyFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shares = quickEditShares.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val editValid = quickEditShares.toIntOrNull()?.let { it >= 0 } == true && quickEditAcqPrice.toDoubleOrNull()?.let(com.kurogane.dividend.model.PortfolioMath::validMoney) == true
    val averagePrice = quickEditAcqPrice.toDoubleOrNull()?.takeIf(com.kurogane.dividend.model.PortfolioMath::validMoney) ?: 0.0
    val currencyFactor = holding.currencyFactor
    val cost = shares * averagePrice / holding.priceUnit * currencyFactor
    val currentPrice = quickEditMarketPrice.takeIf { it > 0.0 } ?: holding.price
    val valuation = shares * currentPrice / holding.priceUnit * currencyFactor
    val gain = valuation - cost
    val gainPct = if (cost > 0.0) gain / cost * 100.0 else 0.0
    val projectedDividend = shares * holding.dps / holding.priceUnit * currencyFactor
    val modalCurrency = if (holding.currency == "$") "$" else "¥"
    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = CardDark,
        contentColor = OnSurface,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp)
                    .clip(CircleShape).background(BorderBlue)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(CardDarkLighter).border(1.dp, BorderBlue, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, null, tint = NeonGreenLight, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.asset_management_text_22), color = OnSurface, fontSize = AppTextSize.headline, fontWeight = FontWeight.ExtraBold, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                    Text(stringResource(R.string.asset_management_1_s_2_s_3_s , holding.code, holding.name, holding.market), color = SoftGray, fontSize = AppTextSize.caption, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                }
                IconButton(onClick = { onDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = SoftGray)
                }
            }

            AdaptiveRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                    .border(1.dp, BorderBlue, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.asset_management_text_23), color = SoftGray, fontSize = AppTextSize.dense)
                    Text(stringResource(R.string.add_holding_1_s_2_s_2 , modalCurrency, javaCurrencyFormat.format(currentPrice)), color = OnSurface, fontSize = AppTextSize.screenTitle, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                }
                Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                    Text(
                        stringResource(R.string.asset_management_text_24 , holding.dailyChange),
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = if (holding.isPositive) NeonGreenLight else ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            AdaptiveRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(stringResource(R.string.asset_management_text_25), color = SoftGray, fontSize = AppTextSize.dense, modifier = Modifier.weight(1f))
                listOf(-50.0, -10.0, 10.0, 50.0).forEach { delta ->
                    val label = if (delta > 0) "+${delta.toInt()}" else delta.toInt().toString()
                    Surface(
                        modifier = Modifier.clickable { quickEditMarketPrice = (currentPrice + delta).coerceAtLeast(1.0) },
                        shape = RoundedCornerShape(7.dp),
                        color = CardDarkLighter
                    ) {
                        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 6.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { quickEditMarketPrice = holding.price }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                    Text(stringResource(R.string.asset_management_text_26), color = NeonGreenLight, fontSize = AppTextSize.dense)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.asset_management_text_27), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.asset_management_text_28), color = SoftGray, fontSize = AppTextSize.caption)
                }
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QuickEditStepButton("−", enabled = shares > 1) { quickEditShares = (shares - 10).coerceAtLeast(1).toString() }
                    OutlinedTextField(
                        value = quickEditShares,
                        onValueChange = { quickEditShares = it.filter(Char::isDigit).ifEmpty { "1" } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text(stringResource(R.string.asset_management_text_29), color = SoftGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreenLight, unfocusedBorderColor = BorderBlue, focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                    )
                    QuickEditStepButton("＋") { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 10).toString() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("1単元 (100株)" to { quickEditShares = "100" }, "+10" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 10).toString() }, "+100" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 100).toString() }, "+500" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 500).toString() }).forEach { (label, action) ->
                        Surface(Modifier.clickable(onClick = action), shape = RoundedCornerShape(10.dp), color = CardDarkLighter, border = BorderStroke(1.dp, BorderBlue)) {
                            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 9.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sell, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.asset_management_text_30 , modalCurrency), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { quickEditAcqPrice = currentPrice.toString().removeSuffix(".0") }) {
                        Text(stringResource(R.string.add_holding_text_9), color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QuickEditStepButton("−", enabled = averagePrice > 1) { quickEditAcqPrice = (averagePrice - 10).coerceAtLeast(1.0).toString().removeSuffix(".0") }
                    OutlinedTextField(
                        value = quickEditAcqPrice,
                        onValueChange = { quickEditAcqPrice = it.filter { ch -> ch.isDigit() || ch == '.' }.ifEmpty { "1" } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix = { Text(modalCurrency, color = SoftGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreenLight, unfocusedBorderColor = BorderBlue, focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                    )
                    QuickEditStepButton("＋") { quickEditAcqPrice = (averagePrice + 10).toString().removeSuffix(".0") }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                    .border(1.dp, NeonGreenLight.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.asset_management_text_31), color = NeonGreenLight, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                        Text(stringResource(R.string.asset_management_text_32), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = BorderBlue)
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickEditSummary("投資元本", "$modalCurrency${javaCurrencyFormat.format(cost.toLong())}", OnSurface, Modifier.weight(1f))
                    QuickEditSummary("評価損益", "${if (gain >= 0) "+" else "−"}$modalCurrency${javaCurrencyFormat.format(kotlin.math.abs(gain).toLong())}\n(${String.format(Locale.JAPAN, "%+.1f%%", gainPct)})", if (gain >= 0) NeonGreenLight else ErrorRed, Modifier.weight(1f), Alignment.CenterHorizontally)
                    QuickEditSummary("年間予想配当金", "$modalCurrency${javaCurrencyFormat.format(projectedDividend.toLong())}/年", NeonGreenLight, Modifier.weight(1f), Alignment.End)
                }
            }

            AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.weight(0.8f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDarkLighter, contentColor = OnSurface),
                    border = BorderStroke(1.dp, BorderBlue)
                ) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        if (viewModel.updateHolding(holding.code, shares, averagePrice, currentPrice)) onDismiss()
                    },
                    enabled = editValid,
                    modifier = Modifier.weight(1.5f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.Check, null, tint = OnPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.asset_management_text_33), color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun QuickEditStepButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(50.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CardDarkLighter,
        border = BorderStroke(1.dp, BorderBlue)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) OnSurface else SoftGray, fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickEditSummary(label: String, value: String, valueColor: Color, modifier: Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SoftGray, fontSize = AppTextSize.dense, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
        Text(value, color = valueColor, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 2)
    }
}

@Composable
private fun HoldingMetricTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color = OnSurface,
    detailColor: Color = SoftGray,
    showEditHint: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    val clickModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
    } else Modifier
    Column(
        modifier = modifier
            .then(clickModifier)
            .padding(horizontal = if (showEditHint) 3.dp else 2.dp, vertical = if (showEditHint) 4.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = SoftGray, fontSize = 9.sp, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
            if (showEditHint) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = NeonGreenLight.copy(alpha = 0.72f),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
        Text(detail, color = detailColor, fontSize = 8.sp, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
    }
}

@Composable
private fun HoldingMetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(32.dp)
            .background(BorderBlue)
    )
}

private fun compactAccountLabel(accountType: String): String = when {
    accountType.startsWith("特定口座") -> accountType
        .replace("特定口座", "特定")
        .replace(" (四半期)", "・四半期")
        .replace("(四半期)", "・四半期")
    accountType.startsWith("新NISA成長枠") -> accountType.replace("新NISA成長枠", "新NISA")
    accountType.startsWith("新NISAつみたて投資枠") -> accountType.replace("新NISAつみたて投資枠", "新NISA積立")
    else -> accountType
}
