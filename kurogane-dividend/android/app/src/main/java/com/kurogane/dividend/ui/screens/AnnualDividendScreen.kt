package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToLong
import kotlin.math.sqrt
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualDividendScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(2) } // 0: 配当推移, 1: 資産推移, 2: 分析
    var assetTrackingPeriod by rememberSaveable { mutableStateOf("5y") }
    var evalBasis by remember { mutableStateOf("eval") } // "eval" or "cost"

    val totalAssets = remember(holdings) { holdings.sumOf { it.totalValuation } }
    val totalCost = remember(holdings) { holdings.sumOf { it.totalCost } }
    val grossAnnualDividend = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("gross") } }
    val netAnnualDividend = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("net") } }
    val totalAnnualDiv = remember(holdings, taxRateMode) { holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) } }
    val yieldOnCost = remember(grossAnnualDividend, totalCost) { if (totalCost > 0) (grossAnnualDividend / totalCost) * 100 else 0.0 }
    val yieldOnValuation = remember(grossAnnualDividend, totalAssets) { if (totalAssets > 0) (grossAnnualDividend / totalAssets) * 100 else 0.0 }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.annualScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.annualScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.annualScrollIndex = index
                viewModel.annualScrollOffset = offset
            }
    }

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card: Annual Expected Dividend
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "税引前配当額",
                                fontSize = AppTextSize.caption,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald.copy(alpha = 0.12f))
                                        .border(0.5.dp, PrimaryEmerald.copy(alpha = 0.24f), CircleShape)
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isPrivacyActive) "月換算 ¥•••••" else "月換算 ¥${currencyFormat.format((grossAnnualDividend / 12).roundToLong())}",
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isPrivacyActive) "税引後 ¥•••••" else "税引後 ¥${currencyFormat.format((netAnnualDividend / 12).roundToLong())}",
                                    fontSize = AppTextSize.dense,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (isPrivacyActive) "¥••••••••" else "¥${currencyFormat.format(grossAnnualDividend.toLong())}",
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnSurface,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = AppTextSize.hero,
                                    letterSpacing = (-0.5).sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("/ 年", color = OnSurfaceVariant, fontSize = AppTextSize.caption, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyActive) "税引き後配当額: ¥ •••••••" else "税引き後配当額: ¥${currencyFormat.format(netAnnualDividend.toLong())}",
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
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
                                    .padding(12.dp)
                            ) {
                                Text("取得額配当利回り", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format(Locale.JAPAN, "%.2f", yieldOnCost),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryEmerald,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("%", fontSize = AppTextSize.dense, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPrivacyActive) "取得額 ¥••••" else "取得額 ¥${currencyFormat.format(totalCost.toLong())}",
                                    fontSize = AppTextSize.dense,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
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
                                    .padding(12.dp)
                            ) {
                                Text("評価額配当利回り", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, maxLines = 1, softWrap = false)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format(Locale.JAPAN, "%.2f", yieldOnValuation),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryEmerald,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("%", fontSize = AppTextSize.dense, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isPrivacyActive) "評価額 ¥••••" else "評価額 ¥${currencyFormat.format(totalAssets.toLong())}",
                                    fontSize = AppTextSize.dense,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
            // 3-way pill tab selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceContainerHigh)
                        .padding(4.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnnualDividendTab(
                        label = "配当推移",
                        icon = Icons.Default.TrendingUp,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    AnnualDividendTab(
                        label = "資産推移",
                        icon = Icons.Default.AccountBalance,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    AnnualDividendTab(
                        label = "分析",
                        icon = Icons.Default.DataUsage,
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Tab 2: Sector & Composition Analysis
            if (selectedTab == 2) {
                item {
                    CompositionAnalysisSection(holdings = holdings, taxRateMode = taxRateMode, totalAssets = totalAssets, totalAnnualDiv = totalAnnualDiv)
                }
            }

            // Tab 0: Dividend Compounding Simulation
            if (selectedTab == 0) {
                item {
                    CompoundingSimulationSection(
                        viewModel = viewModel,
                        holdings = holdings,
                        taxRateMode = taxRateMode,
                        totalAnnualDiv = totalAnnualDiv,
                        totalCost = totalCost,
                        isPrivacyActive = isPrivacyActive
                    )
                }
            }

            // Tab 1: Asset Tracking
            if (selectedTab == 1) {
                item {
                    AssetTrackingSection(
                        holdings = holdings,
                        totalAssets = totalAssets,
                        totalCost = totalCost,
                        totalAnnualDiv = totalAnnualDiv,
                        currencyFormat = currencyFormat,
                        isPrivacyActive = isPrivacyActive,
                        selectedPeriod = assetTrackingPeriod,
                        onPeriodSelected = { assetTrackingPeriod = it },
                        loadMonthlyPriceHistory = { codes, months -> viewModel.fetchMonthlyPriceHistory(codes, months) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AnnualDividendTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabShape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(tabShape)
            .background(if (selected) PrimaryEmerald else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) OnPrimary else OnSurface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) OnPrimary else OnSurface,
            fontSize = AppTextSize.dense
        )
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

private data class SectorCompositionRow(
    val name: String,
    val holdings: List<com.kurogane.dividend.model.Holding>,
    val dividendTotal: Double,
    val valuationTotal: Double,
    val color: Color
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun CompositionAnalysisSection(holdings: List<com.kurogane.dividend.model.Holding>, taxRateMode: String, totalAssets: Double, totalAnnualDiv: Double) {
    var isVisible by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf("dividend") } // "holding" or "dividend"
    var sectorValueMode by remember { mutableStateOf("valuation") } // "valuation" or "dividend"
    val context = LocalContext.current
    val industriesByCode = remember(context) { Tse33IndustryMaster.industries(context) }

    fun climateForHolding(holding: com.kurogane.dividend.model.Holding): IndustryClimate? {
        if (holding.category != com.kurogane.dividend.model.Category.JAPAN) return null
        val code = Tse33IndustryMaster.normalizeCode(holding.code) ?: return null
        return classifyIndustry(industriesByCode[code])
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
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
            .filter { if (chartMode == "dividend") it.second > 0.0 else it.third > 0.0 }
            .sortedByDescending { if (chartMode == "dividend") it.second else it.third }
    }

    val totalChartValue = remember(mappedHoldings, chartMode) { mappedHoldings.sumOf { if (chartMode == "dividend") it.second else it.third } }
    val totalPortfolioDividend = remember(mappedHoldings) { mappedHoldings.sumOf { it.second } }

    // Color definitions
    val sliceColors = listOf(
        PrimaryEmeraldContainer, // Emerald
        SkyBlue, // Sky Blue
        SecondaryIndigo, // Indigo
        ErrorRed, // Rose
        AccentAmber, // Amber
        Color(0xFFA78BFA), // Purple
        Color(0xFF22D3EE), // Cyan
        ErrorRed, // Pink
        OnSurfaceVariant  // Gray
    )

    // Build Donut Slice Objects
    val donutSlices = mappedHoldings.mapIndexed { index, (holding, div, valuation) ->
        val itemVal = if (chartMode == "dividend") div else valuation
        val ratio = if (totalChartValue > 0.0) itemVal / totalChartValue else 0.0
        val sweepAngle = (ratio * 360f).toFloat()
        val percentageStr = String.format("%.1f%%", ratio * 100.0)
        val color = sliceColors[index % sliceColors.size]
        
        DonutSliceData(
            code = holding.code,
            name = holding.name,
            expectedDiv = itemVal,
            sweepAngle = sweepAngle,
            percentage = percentageStr,
            color = color
        )
    }

    var activeStockCode by remember { mutableStateOf<String?>(null) }

    // Reset active stock selection on load/mode switch to display totals by default
    LaunchedEffect(holdings, chartMode) {
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
        val percentageVal = String.format("%.1f%%", ratio * 100.0)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "構成銘柄・配当比率",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )

                    // Pill Toggle Switch: 構成比率 vs 配当比率
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
                                .clickable { chartMode = "holding" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "構成比率",
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
                                .clickable { chartMode = "dividend" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "配当比率",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (chartMode == "dividend") OnPrimary else OnSurfaceVariant,
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
                            "配当支払い銘柄がありません。",
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
                                activeSlice?.name ?: if (chartMode == "holding") "資産総額" else "年間配当合計",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val centerVal = activeSlice?.expectedDiv ?: if (chartMode == "holding") totalAssets else totalAnnualDiv
                            Text(
                                text = "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(centerVal.toLong())}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = AppTextSize.screenTitle
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
                                    text = if (activeSlice != null) activeSlice.percentage else "${donutSlices.size}銘柄分散中",
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
                            Row(
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
                                        Row(
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
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                slice.percentage,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = OnSurfaceVariant,
                                                    fontFamily = FontFamily.Monospace,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "セクター別比率",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrimaryEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${formattedSectors.size}業種分散",
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
                                "${sec.name} ${sec.percentageVal}",
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
                Row(
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
                                "景気敏感",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                             )
                             Spacer(modifier = Modifier.height(2.dp))
                             Text(
                                 text = String.format("%.1f%%", cyclicalPct),
                                 style = MaterialTheme.typography.bodyMedium.copy(
                                     color = ErrorRed,
                                     fontWeight = FontWeight.Black,
                                     fontFamily = FontFamily.Monospace
                                 )
                             )
                             Text(
                                 "卸売・輸送用機器等",
                                 style = MaterialTheme.typography.labelSmall.copy(
                                     color = OnSurfaceVariant,
                                     fontSize = AppTextSize.dense
                                 ),
                                 maxLines = 1
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
                                "景気影響が混在",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%.1f%%", mixedPct),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SkyBlue,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                "水産・化学・保険業等",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                ),
                                maxLines = 1
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
                                "ディフェンシブ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%.1f%%", defensivePct),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaryEmeraldContainer,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                "食料・医薬品等",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                ),
                                maxLines = 1
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "セクター・銘柄構成詳細",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = OnSurface),
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh).padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("valuation" to "評価額", "dividend" to "配当額").forEach { (mode, title) ->
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
                        Row(
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
                                    "${sec.name}：${sec.holdings.size}銘柄",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = OnSurface,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
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
                                    "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(sectorAmount.toLong())}${if (sectorValueMode == "dividend") " / 年" else ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = sec.color,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
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

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfaceContainer)
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
                                                    maxLines = 1
                                                )
                                                Text(
                                                    "保有 ${h.shares}株 • 利回り ${String.format("%.2f%%", currentYield)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OnSurfaceVariant,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(holdingAmount.toLong())}${if (sectorValueMode == "dividend") " / 年" else ""}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = OnSurface,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                            Text(
                                                "構成比 " + String.format(Locale.JAPAN, "%.1f%%", amountShare),
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
            "S" -> PrimaryEmeraldContainer
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
                Row(
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
                            Text(
                                "AIポートフォリオ診断",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = OnSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.body
                                )
                            )
                            Text(
                                "分散スコア & リスク評価",
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (portfolioDiagnosis.possibleCombinations == PortfolioDiagnosis.TOTAL_COMBINATIONS) {
                        "文章パターン: ${NumberFormat.getNumberInstance(Locale.JAPAN).format(portfolioDiagnosis.possibleCombinations)}通り"
                    } else {
                        "評価対象の文章パターン: ${NumberFormat.getNumberInstance(Locale.JAPAN).format(portfolioDiagnosis.possibleCombinations)}通り（最大 ${NumberFormat.getNumberInstance(Locale.JAPAN).format(PortfolioDiagnosis.TOTAL_COMBINATIONS)}通り）"
                    },
                    color = cardColor,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.SemiBold
                )
                if (portfolioDiagnosis.missingLabels.isNotEmpty()) {
                    Text(
                        text = "未評価: ${portfolioDiagnosis.missingLabels.joinToString("、")}",
                        color = AccentAmber,
                        fontSize = AppTextSize.dense
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
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
                            "増配・非減配の実績",
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
                            "減配リスク",
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
private fun CompoundingSimulationSection(
    viewModel: DividendViewModel,
    holdings: List<com.kurogane.dividend.model.Holding>,
    taxRateMode: String,
    totalAnnualDiv: Double,
    totalCost: Double,
    isPrivacyActive: Boolean
) {
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    var updateFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load(force: Boolean = false) {
        loading = true
        try { updateFailed = viewModel.fetchPortfolioFinancialData(force = force).any { it.isFailure } }
        finally { loading = false }
    }
    LaunchedEffect(holdings.map { it.code }) { load() }
    val currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
    val years = remember(holdings, financialData, taxRateMode, currentYear) {
        com.kurogane.dividend.model.DividendHistory.calculate(holdings, financialData, (currentYear - 4)..currentYear, taxRateMode)
    }
    val format = NumberFormat.getNumberInstance(Locale.JAPAN)
    fun money(value: Double?): String = if (value == null) "未取得" else if (isPrivacyActive) "¥••••" else "¥${format.format(value.toLong())}"
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
        value > 0 -> "+¥${format.format(value.toLong())}"
        value < 0 -> "−¥${format.format(kotlin.math.abs(value).toLong())}"
        else -> "¥0"
    }
    fun signedPercent(value: Double?): String = value?.let { String.format(Locale.JAPAN, "%+.2f%%", it) } ?: "未取得"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("年間配当推移", color = OnSurface, fontWeight = FontWeight.Bold)
                }
                Text("${years.first().year}年〜${years.last().year}年・現在株数で換算した参考推移", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = PrimaryEmerald.copy(alpha = .15f)) {
                Text("5期", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Text("国内株の現在保有株数 × 分割調整済みの各期実績配当。実際の受取履歴ではありません。米国株・投資信託は対象外です。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        Text("出典：金融庁EDINET / JPX TDnet（IRBANK加工）", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (updateFailed) Text("一部の履歴を更新できませんでした。取得済みの値を表示しています。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("年間配当の増加額", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = PrimaryEmerald.copy(alpha = .15f)) {
                            Text(signedPercent(increaseRate), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(signedMoney(increaseAmount), color = if ((increaseAmount ?: 0.0) >= 0.0) PrimaryEmerald else ErrorRed, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("${firstPoint?.year?.let { "${it}期" } ?: "開始期未取得"} → ${latestPoint?.year?.let { "${it}期" } ?: "最新期未取得"}・年額差", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
            }
            Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShowChart, null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("平均成長", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                    Text(cagr?.let { String.format(Locale.JAPAN, "%.2f%%", it) } ?: "未取得", color = PrimaryEmerald, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("年平均増配率（CAGR）", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Text("年平均増配額 ${signedMoney(averageAnnualIncrease)} /年", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(PrimaryEmerald))
                        Spacer(Modifier.width(5.dp))
                        Text("年間配当額（現在株数換算・取得済み分）", color = OnSurface, fontSize = AppTextSize.dense)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = PrimaryEmerald.copy(alpha = .12f), border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = .3f))) {
                        Text("開始期比 ${signedPercent(increaseRate)}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                val chartColor = PrimaryEmerald
                val chartGridColor = BorderColor
                Canvas(Modifier.fillMaxWidth().height(136.dp)) {
                    val gridTop = size.height * .2f
                    val gridMid = size.height * .55f
                    val gridBottom = size.height * .9f
                    listOf(gridTop, gridMid, gridBottom).forEach { y -> drawLine(chartGridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
                    val count = years.size
                    val groupWidth = 32.dp.toPx()
                    val spacing = (size.width - count * groupWidth) / (count + 1)
                    years.forEachIndexed { index, point ->
                        val left = spacing + index * (groupWidth + spacing)
                        val barHeight = point.amount?.let { (it / maxValue).toFloat().coerceIn(0f, 1f) * size.height * .78f } ?: 0f
                        if (index == selectedIndex) {
                            drawRoundRect(chartColor.copy(alpha = .08f), Offset(left - 5.dp.toPx(), 0f), Size(groupWidth + 10.dp.toPx(), size.height), cornerRadius = CornerRadius(6.dp.toPx()))
                            drawRoundRect(chartColor.copy(alpha = .35f), Offset(left - 5.dp.toPx(), 0f), Size(groupWidth + 10.dp.toPx(), size.height), cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                        }
                        if (barHeight > 0f) {
                            drawRoundRect(Brush.verticalGradient(listOf(chartColor, chartColor.copy(alpha = .45f)), startY = size.height - barHeight, endY = size.height), Offset(left + (groupWidth - 16.dp.toPx()) / 2f, size.height - barHeight), Size(16.dp.toPx(), barHeight), cornerRadius = CornerRadius(4.dp.toPx()))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    years.forEachIndexed { index, point ->
                        Text(point.year.toString(), color = if (index == selectedIndex) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Text("年次推移データグリッド（年間配当額と前年差）", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    years.forEachIndexed { index, point ->
                        val previousAmount = years.getOrNull(index - 1)?.amount
                        val yearChange = if (point.amount != null && previousAmount != null) point.amount - previousAmount else null
                        val isSelected = index == selectedIndex
                        Card(Modifier.weight(1f).clickable { selectedYearIndex = index }, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) SurfaceContainerHigh else BackgroundDark), border = BorderStroke(1.dp, if (isSelected) PrimaryEmerald else Color.Transparent)) {
                            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${point.year}", color = if (isSelected) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("${point.covered}/${point.expected}銘柄", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                                Text("年額", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                                Text(if (point.amount == null) "未取得" else if (isPrivacyActive) "••••" else "¥${format.format(point.amount.toLong())}", color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                                Text("前年差", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                                Text(signedMoney(yearChange), color = if ((yearChange ?: 0.0) >= 0.0) PrimaryEmerald else ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(10.dp), color = BackgroundDark, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${selectedYear.year}期・現在株数で換算", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            Text("年間配当額 ${money(selectedYear.amount)}", color = OnSurface, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("取得済み ${selectedYear.covered}/${selectedYear.expected}銘柄", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            Text("月換算 ${money(selectedYear.amount?.div(12))}", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("表示額は取得できた銘柄分の小計です。履歴がない銘柄は除外し、実績0円の記録は0円として含めます。税引後では現在の口座区分を適用します。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                TextButton(onClick = { if (!loading) scope.launch { load() } }, enabled = !loading) { Text("履歴を更新") }
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
    val currentYear = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year
    // 1. Dynamic states for user interactivity
    var selectedYearIndex by remember { mutableIntStateOf(4) }
    var monthlySavingsOption by remember { mutableIntStateOf(1) } // 0: ¥10万, 1: ¥30万, 2: ¥50万
    var yieldOption by remember { mutableIntStateOf(1) }          // 0: 3%, 1: 4%, 2: 5%
    var isReinvestSelected by remember { mutableStateOf(true) }

    // Derive inputs
    val monthlySavings = when (monthlySavingsOption) {
        0 -> 10000.0
        1 -> 30000.0
        else -> 50000.0
    }
    val yieldRate = when (yieldOption) {
        0 -> 0.03
        1 -> 0.04
        else -> 0.05
    }

    // Adapt to actual portfolio data or fallback to image defaults if empty
    val realCost = totalCost.coerceAtLeast(0.0)
    val realAssets = totalAssets.coerceAtLeast(0.0)

    val yearLabels = (currentYear - 4..currentYear).toList()
    val yearMonths = yearLabels.map { if (it == currentYear) YearMonth.now(ZoneId.of("Asia/Tokyo")).toString() else "$it-12" }
    val domesticHoldings = holdings.filter { it.category == Category.JAPAN && it.shares > 0 }
    val codes = domesticHoldings.map { it.code }.distinct()
    var annualPrices by remember(codes, yearMonths) { mutableStateOf<List<CloudflareMonthlyPriceSeries>>(emptyList()) }
    var annualHistoryFailed by remember(codes, yearMonths) { mutableStateOf(false) }
    LaunchedEffect(codes, yearMonths, selectedPeriod) {
        if (selectedPeriod == "5y" && codes.isNotEmpty()) {
            com.kurogane.dividend.data.runCatchingCancellable { loadMonthlyPriceHistory(codes, yearMonths) }
                .onSuccess { annualPrices = it; annualHistoryFailed = false }
                .onFailure { annualHistoryFailed = true }
        }
    }
    val priceMap = annualPrices.associate { it.code to it.prices.associateBy { price -> price.month } }
    val domesticCost = domesticHoldings.sumOf { it.totalCost }
    val costs = List(5) { domesticCost }
    val assets = yearMonths.map { month ->
        if (domesticHoldings.isEmpty() || domesticHoldings.any { priceMap[it.code]?.get(month) == null }) Double.NaN
        else domesticHoldings.sumOf { it.shares * priceMap.getValue(it.code).getValue(month).close }
    }
    val gains = assets.map { it - domesticCost }
    val gainsPct = gains.map { if (domesticCost > 0) it / domesticCost * 100 else Double.NaN }
    fun historicalMoney(amount: Double): String = if (amount.isFinite()) currencyFormat.format(amount.toLong()) else "未取得"
    fun historicalPercent(amount: Double): String = if (amount.isFinite()) String.format(Locale.JAPAN, "%.1f%%", amount) else "未取得"
    fun historicalWan(amount: Double): String = if (amount.isFinite()) String.format(Locale.JAPAN, "%.0f万", amount / 10_000) else "未取得"

    // Simulated values calculation using mathematically precise compound formulas
    val yearsSim = listOf(10, 20, 30)
    val simResults = yearsSim.map { years ->
        var fv = realAssets
        val monthlyRate = yieldRate / 12.0
        val totalMonths = years * 12
        for (m in 1..totalMonths) {
            if (isReinvestSelected) {
                fv = fv * (1.0 + monthlyRate) + monthlySavings
            } else {
                fv = fv + monthlySavings
            }
        }
        if (!isReinvestSelected) {
            fv += realAssets * yieldRate * years
        }
        val costAtYear = realCost + (monthlySavings * 12.0 * years)
        Pair(fv, costAtYear)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("国内株の現在株数 × 各年末の実際の終値（今年は直近月）。取得額は現在の取得額を比較基準とします。過去の保有資産額ではありません。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        if (annualHistoryFailed) Text("株価履歴を取得できませんでした。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
        // A. Header Title row with switch pills
        Row(
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
                        "資産・配当金推移",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                }
                Text(
                    if (selectedPeriod == "5y") "${currentYear - 4}年〜${currentYear}年・年末株価による参考評価" else "過去12か月・月末株価による参考評価",
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
                        "5年",
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
                        "12ヶ月",
                        color = if (selectedPeriod == "12m") OnPrimary else OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = if (selectedPeriod == "12m") FontWeight.ExtraBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // B. Dual Asset KPI Header Rows
        Row(
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("資産評価額", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
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
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (isPrivacyActive) "投資元本: ¥••••" else "投資元本: ¥${currencyFormat.format(realCost.toLong())}",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("通算含み益", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SkyBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "簿価利回り",
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
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Yield on Cost: " + String.format("%.2f%%", (totalAnnualDiv / realCost) * 100),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("評価額 (時価)", color = OnSurface, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF64748B)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("累積投資元本 (簿価)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryEmerald.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isPrivacyActive) "含み益: ¥••••" else "含み益: +¥${historicalMoney(gains[4])} (+${historicalPercent(gainsPct[4])})",
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

                        val costBarHeight = size.height * costRatio * 0.8f
                        val assetBarHeight = size.height * assetRatio * 0.8f

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
                            brush = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF475569))),
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
                Row(
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

                // D. Interactive data grid matching the selector in image
                Text(
                    "年次推移データグリッド (元本 vs 時価対比)",
                    color = OnSurface,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
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
                                    "元: " + String.format("%.0f万", costs[idx] / 10000.0),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                                Text(
                                    "時: " + historicalWan(assets[idx]),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = historicalWan(gains[idx]),
                                    color = if (gains[idx] >= 0) PrimaryEmeraldContainer else ErrorRed,
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
                            Text("$selYearStr 詳細", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyActive) "評価額: ¥••••••••" else "評価額: ¥${historicalMoney(assets[selectedYearIndex])}",
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isPrivacyActive) "投資元本: ¥••••" else "投資元本: ¥${historicalMoney(costs[selectedYearIndex])}",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                            Text(
                                text = if (isPrivacyActive) "含み損益: ••••" else "損益比較 ¥${historicalMoney(gains[selectedYearIndex])} (${historicalPercent(gainsPct[selectedYearIndex])})",
                                color = if (gains[selectedYearIndex] >= 0) PrimaryEmeraldContainer else ErrorRed,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // F. 未来30年・複利成長シミュレーター Card (Image 1 & 2 premium simulation engine!)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "未来30年・複利成長シミュレーター",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.caption
                                )
                            )
                            Text(
                                "毎月の積立額と配当再投資による複利試算",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "複利シミュレーション",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 1: Monthly Savings Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "毎月の新規積立額:",
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "¥" + currencyFormat.format(monthlySavings.toLong()),
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("¥10万", "¥30万", "¥50万").forEachIndexed { index, label ->
                        val isSel = monthlySavingsOption == index
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { monthlySavingsOption = index },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) PrimaryEmerald else BackgroundDark
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) OnPrimary else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 2: Yield Ratio Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "想定配当利回り:",
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        String.format(java.util.Locale.JAPAN, "%.2f%%", yieldRate * 100),
                        color = OnSurface,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("3.00%", "4.00%", "5.00%").forEachIndexed { index, label ->
                        val isSel = yieldOption == index
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { yieldOption = index },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) PrimaryEmerald else BackgroundDark
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) OnPrimary else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector 3: Reinvestment Switch Row
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
                        Column {
                            Text("配当金を自動で再投資する (複利効果)", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text("税引後(20.315%控除)の配当金を元本に自動組み入れ", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        Switch(
                            checked = isReinvestSelected,
                            onCheckedChange = { isReinvestSelected = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnSurface,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Simulation Result Header
                Text(
                    "複利成長シミュレーション結果",
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 3 columns of simulation results
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 20, 30).forEachIndexed { index, yr ->
                        val (valAmt, costAmt) = simResults[index]
                        val is30Year = yr == 30
                        val monthDiv = (valAmt * yieldRate) / 12.0

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                            border = BorderStroke(
                                1.dp,
                                if (is30Year) PrimaryEmerald else BorderColor
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${yr}年後 (" + (currentYear + yr) + ")",
                                    color = if (is30Year) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPrivacyActive) "••••" else String.format("¥%.0f万", valAmt / 10000.0),
                                    color = OnSurface,
                                    fontSize = AppTextSize.body,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isPrivacyActive) "元本: ••••" else String.format("元本: ¥%.0f万", costAmt / 10000.0),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Divider(color = BorderColor)
                                Spacer(modifier = Modifier.height(6.dp))

                                Text("月配当(目安)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text(
                                    text = if (isPrivacyActive) "¥••••/月" else "¥${currencyFormat.format(monthDiv.toLong())}/月",
                                    color = if (is30Year) PrimaryEmeraldContainer else SkyBlue,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // F. Simulation Explanation box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryEmerald.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(
                                "現在の資産評価額 ¥%,.0f万から、毎月 ¥%s を積立、利回り %.2f%% で運用し、さらに配当金を再投資%sした場合のシミュレーションです。複利効果により、30年間の累計不労所得・資産価値は加速度的に成長します。",
                                realAssets / 10000.0,
                                currencyFormat.format(monthlySavings.toLong()),
                                yieldRate * 100,
                                if (isReinvestSelected) "し続けた" else "せず貯蓄"
                            ),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // G. Historical Timeline detailed list (Timeline vertical list at the bottom of Image 2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
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
                        "年次推移・元本 VS 評価額",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    )
                }
                Text("${currentYear - 4}〜$currentYear", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
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
                                        "'" + title.take(4).drop(2),
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
                                                    "含み益最大",
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
                                                    "確定",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = OnSurfaceVariant,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isPrivacyActive) "元本 ¥•••• • 評価額 ¥••••" else "元本 ¥${historicalMoney(costs[index])} • 評価額 ¥${historicalMoney(assets[index])}",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isPrivacyActive) "••••" else String.format("%s¥%s", prefix, historicalMoney(gains[index])),
                                    color = if (isGain) PrimaryEmeraldContainer else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (index == 0) "基準年" else historicalPercent(gainsPct[index]),
                                    color = if (index == 0) OnSurfaceVariant else if (isGain) PrimaryEmeraldContainer else ErrorRed,
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
    val monthKeys = remember(months) { months.map { it.toString() } }
    var monthlyPriceHistory by remember(targetCodes, monthKeys) { mutableStateOf<Map<String, Map<String, CloudflareMonthlyPrice>>>(emptyMap()) }
    var isHistoryLoading by remember(targetCodes, monthKeys) { mutableStateOf(false) }
    var historyLoadFailed by remember(targetCodes, monthKeys) { mutableStateOf(false) }
    LaunchedEffect(targetCodes, monthKeys) {
        if (targetCodes.isEmpty()) return@LaunchedEffect
        isHistoryLoading = true
        historyLoadFailed = false
        runCatching { loadMonthlyPriceHistory(targetCodes, monthKeys) }
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
    val monthlySnapshots = remember(targetHoldings, months, monthlyPriceHistory) {
        months.map { month ->
            val prices = targetHoldings.mapNotNull { holding ->
                val code = holding.code.trim().uppercase(Locale.ROOT).removeSuffix(".T").let {
                    if (it.length == 5 && it.endsWith('0')) it.dropLast(1) else it
                }
                monthlyPriceHistory[code]?.get(month.toString())?.let { holding to it.close }
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
    val latest = monthlySnapshots.last()
    val validValues = monthlySnapshots.mapNotNull { it.value }
    val axisMin = (listOf(currentCost) + validValues).minOrNull()?.times(0.85) ?: 0.0
    val axisMax = (listOf(currentCost) + validValues).maxOrNull()?.times(1.03)?.coerceAtLeast(axisMin + 1.0) ?: 1.0
    val axisRange = (axisMax - axisMin).coerceAtLeast(1.0)
    val unsupportedCount = holdings.size - targetHoldings.size
    val chartGridColor = BorderColor
    val chartHighlightColor = PrimaryEmerald
    val chartAssetColor = PrimaryEmeraldContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                    Spacer(Modifier.width(5.dp))
                    Text("国内株評価額 (時価)", color = OnSurface, fontSize = AppTextSize.dense)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF64748B)))
                    Spacer(Modifier.width(5.dp))
                    Text("取得元本 (現在保有分)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = when {
                            isPrivacyActive -> "含み益 ¥••••"
                            latest.gain == null -> if (isHistoryLoading) "履歴取得中" else "履歴なし"
                            else -> "含み益 ${if (latest.gain >= 0) "+" else "−"}¥${currencyFormat.format(kotlin.math.abs(latest.gain).toLong())}"
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = if ((latest.gain ?: 0.0) >= 0) PrimaryEmerald else ErrorRed,
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
                        brush = Brush.verticalGradient(listOf(Color(0xFF64748B), Color(0xFF475569))),
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

            Row(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("月次推移データ (現在の保有数で再評価)", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                Text(if (isHistoryLoading) "株価履歴取得中" else "月末株価", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                monthlySnapshots.filterIndexed { index, _ -> (index + 1) % 3 == 0 }.forEach { item ->
                    val isLatest = item == latest
                    val gainColor = if ((item.gain ?: 0.0) >= 0) PrimaryEmerald else ErrorRed
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
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                        text = if (isLatest) "${item.month}月最新" else "${item.month}月",
                            color = if (isLatest) PrimaryEmerald else OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = if (isPrivacyActive) "元: ••••" else "元: ${formatAssetWan(item.cost)}",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = when {
                                isPrivacyActive -> "時: ••••"
                                item.value == null -> "時: 履歴なし"
                                else -> "時: ${formatAssetWan(item.value)}"
                            },
                            color = if (isLatest) PrimaryEmerald else OnSurface,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                isPrivacyActive -> "••••"
                                item.gain == null -> "損益: —"
                                else -> "${if (item.gain >= 0) "+" else "−"}${formatAssetWan(kotlin.math.abs(item.gain))}"
                            },
                            color = gainColor,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("現在の保有株数に各月の株価を適用した参考値です。取得株数の過去履歴は含みません。")
                    if (unsupportedCount > 0) append(" 国内株以外 ${unsupportedCount}銘柄は対象外です。")
                    if (historyLoadFailed) append(" 株価履歴を取得できませんでした。")
                    else if (targetHoldings.isEmpty()) append(" 対象となる国内株がありません。")
                    else if (!isHistoryLoading && monthlySnapshots.none { it.value != null }) append(" 月末株価がないため表示できる履歴がありません。")
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
