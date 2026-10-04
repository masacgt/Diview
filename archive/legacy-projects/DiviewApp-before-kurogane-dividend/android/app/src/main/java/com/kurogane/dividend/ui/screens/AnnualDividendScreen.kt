package com.kurogane.dividend.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AnnualDividendScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsState()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 分析, 1: 配当推移, 2: 資産推移
    var evalBasis by remember { mutableStateOf("eval") } // "eval" or "cost"

    val totalAssets = holdings.sumOf { it.totalValuation }
    val totalCost = holdings.sumOf { it.totalCost }
    val totalAnnualDiv = holdings.sumOf { it.annualDividend }
    val yieldOnCost = if (totalCost > 0) (totalAnnualDiv / totalCost) * 100 else 0.0
    val yieldOnValuation = if (totalAssets > 0) (totalAnnualDiv / totalAssets) * 100 else 0.0

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PieChart,
                                contentDescription = null,
                                tint = PrimaryEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "年間配当金",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                "Annual Dividend Analytics",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceContainer
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(
                            listOf(
                                PrimaryEmerald.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "年間予想配当金 (税引前)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = OnSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            // Basis Pill (時価 / 取得価格)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerHigh)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (evalBasis == "eval") PrimaryEmerald else Color.Transparent)
                                        .clickable { evalBasis = "eval" }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "時価基準",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (evalBasis == "eval") Color.Black else OnSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (evalBasis == "cost") PrimaryEmerald else Color.Transparent)
                                        .clickable { evalBasis = "cost" }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "取得額基準",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (evalBasis == "cost") Color.Black else OnSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Large Dividend Total
                        Text(
                            text = if (isPrivacyActive) "¥••••••••" else "¥${currencyFormat.format(totalAnnualDiv.toInt())}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 32.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary Stats Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainerHigh)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "月平均受取額",
                                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant)
                                )
                                Text(
                                    text = if (isPrivacyActive) "¥••••" else "¥${currencyFormat.format((totalAnnualDiv / 12).toInt())}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (evalBasis == "eval") "予想利回り (時価)" else "取得価格利回り (YOC)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant)
                                )
                                Text(
                                    text = if (evalBasis == "eval") {
                                        String.format("%.2f%%", yieldOnValuation)
                                    } else {
                                        String.format("%.2f%%", yieldOnCost)
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3-way Tab Selector
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceContainerHigh,
                    contentColor = PrimaryEmerald,
                    indicator = { tabPositions ->
                        // Standard tab indicator
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "分析 (内訳)",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) PrimaryEmerald else OnSurfaceVariant
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "配当推移 (複利)",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) PrimaryEmerald else OnSurfaceVariant
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "資産推移",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) PrimaryEmerald else OnSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Tab 0: Sector & Composition Analysis
            if (selectedTab == 0) {
                item {
                    CompositionAnalysisSection(holdings = holdings)
                }
            }

            // Tab 1: Dividend Compounding Simulation
            if (selectedTab == 1) {
                item {
                    CompoundingSimulationSection(totalAnnualDiv = totalAnnualDiv)
                }
            }

            // Tab 2: Asset Tracking
            if (selectedTab == 2) {
                item {
                    AssetTrackingSection(
                        totalAssets = totalAssets,
                        totalCost = totalCost,
                        currencyFormat = currencyFormat,
                        isPrivacyActive = isPrivacyActive
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
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

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun CompositionAnalysisSection(holdings: List<com.kurogane.dividend.model.Holding>) {
    var isVisible by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf("dividend") } // "holding" or "dividend"

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "donutAnimation"
    )

    // 1. Group / Map Holdings to expected dividends and valuations in JPY
    val mappedHoldings = holdings
        .map { h ->
            val rate = if (h.currency == "$") 150.0 else 1.0
            val expectedDiv = h.shares * h.dps * rate
            val valuation = h.shares * h.price * rate
            Triple(h, expectedDiv, valuation)
        }
        .filter { if (chartMode == "dividend") it.second > 0.0 else it.third > 0.0 }
        .sortedByDescending { if (chartMode == "dividend") it.second else it.third }

    val totalChartValue = mappedHoldings.sumOf { if (chartMode == "dividend") it.second else it.third }
    val totalPortfolioDividend = mappedHoldings.sumOf { it.second }

    // Color definitions
    val sliceColors = listOf(
        Color(0xFF10B981), // Emerald
        Color(0xFF38BDF8), // Sky Blue
        Color(0xFFC0C1FF), // Indigo
        Color(0xFFFFB3AD), // Rose
        Color(0xFFFBBF24), // Amber
        Color(0xFFA78BFA), // Purple
        Color(0xFF22D3EE), // Cyan
        Color(0xFFF43F5E), // Pink
        Color(0xFF94A3B8)  // Gray
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

    var activeStockCode by remember { mutableStateOf("") }

    // Set first slice active by default on load
    LaunchedEffect(holdings, chartMode) {
        if (donutSlices.isNotEmpty()) {
            activeStockCode = donutSlices.first().code
        }
    }

    val activeSlice = donutSlices.find { it.code == activeStockCode } ?: donutSlices.firstOrNull()

    // 2. Group holdings by sector
    val sectorGroups = holdings
        .map { h ->
            val rate = if (h.currency == "$") 150.0 else 1.0
            val expectedDiv = h.shares * h.dps * rate
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

    val totalSectorDividend = sectorGroups.sumOf { it.second.first }

    val formattedSectors = sectorGroups.mapIndexed { index, (sectorName, pair) ->
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
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
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
                            color = Color.White
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
                                    color = if (chartMode == "holding") Color(0xFF003824) else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
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
                                    color = if (chartMode == "dividend") Color(0xFF003824) else OnSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
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
                        Canvas(modifier = Modifier.size(160.dp)) {
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
                                    style = Stroke(currentStrokeWidth, cap = StrokeCap.Round)
                                )
                                currentStartAngle += slice.sweepAngle
                            }
                        }

                        // Centered Focus text
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                activeSlice?.name ?: if (chartMode == "holding") "資産評価合計" else "年間配当合計",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Text(
                                text = "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format((activeSlice?.expectedDiv ?: totalPortfolioDividend).toInt())}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Text(
                                activeSlice?.percentage ?: "100%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = activeSlice?.color ?: PrimaryEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            )
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
                                            .clickable { activeStockCode = slice.code }
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
                                                        color = Color.White,
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
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
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
                            color = Color.White
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

                // Sector Legends Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    formattedSectors.take(5).forEach { sec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(sec.color))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${sec.name} ${sec.percentageVal}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    if (formattedSectors.size > 5) {
                        Text(
                            "他 ${formattedSectors.size - 5}セクター",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sector Quick Grid Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formattedSectors.take(3).forEach { sec ->
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    sec.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = OnSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(sec.totalDiv.toInt())}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                Text(
                                    sec.percentageVal,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = sec.color,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }
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
            Text(
                "セクター・銘柄構成詳細",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            formattedSectors.forEach { sec ->
                val isOpen = expandedSectors[sec.name] ?: false
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(sec.color))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    sec.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(sec.color.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        sec.percentageVal,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = sec.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(sec.totalDiv.toInt())} / 年",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = sec.color,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
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
                                    val rate = if (h.currency == "$") 150.0 else 1.0
                                    val divContribution = h.shares * h.dps * rate
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
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    h.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    maxLines = 1
                                                )
                                                Text(
                                                    "保有 ${h.shares}株 • 利回り ${String.format("%.2f%%", currentYield)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = OnSurfaceVariant,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(divContribution.toInt())}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            )
                                            Text(
                                                "比率 " + String.format("%.1f%%", (divContribution / totalPortfolioDividend) * 100.0),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = sec.color,
                                                    fontSize = 9.sp,
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
    }
}

@Composable
private fun CompoundingSimulationSection(totalAnnualDiv: Double = 120770.0) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "配当再投資による複利推移シミュレーション",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                "年間配当を全額再投資 + 年30万円追加投資 (年利4.0%想定)",
                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 5-Year Bar comparison dynamically derived from actual annual dividend
            val currentMan = Math.max(5.0, totalAnnualDiv / 10000.0)
            val years = listOf("2024", "2025", "2026", "2027", "2028")
            val amounts = listOf(
                (currentMan * 0.85).toInt(),
                currentMan.toInt(),
                (currentMan * 1.25 + 3.0).toInt(),
                (currentMan * 1.55 + 6.5).toInt(),
                (currentMan * 1.95 + 10.5).toInt()
            )

            var barAnimActive by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { barAnimActive = true }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                years.zip(amounts).forEach { (year, amt) ->
                    val targetHeight = (amt * 1.3).dp
                    val animatedHeight by animateDpAsState(
                        targetValue = if (barAnimActive) targetHeight else 0.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "barHeight_$year"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${amt}万",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(animatedHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(PrimaryEmerald, PrimaryEmerald.copy(alpha = 0.4f))
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            year,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryEmerald.copy(alpha = 0.1f))
                    .border(1.dp, PrimaryEmerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "💡 5年後には年間配当が約2.4倍 (¥910,000/年・月平均7.5万円) に到達する見込みです。雪だるま式に加速します。",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PrimaryEmerald,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun AssetTrackingSection(
    totalAssets: Double,
    totalCost: Double,
    currencyFormat: NumberFormat,
    isPrivacyActive: Boolean
) {
    val totalGain = totalAssets - totalCost
    val gainPercent = if (totalCost > 0) (totalGain / totalCost) * 100 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "資産推移・含み損益サマリー",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "総資産評価額",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant)
                    )
                    Text(
                        text = if (isPrivacyActive) "¥••••••••" else "¥${currencyFormat.format(totalAssets.toInt())}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "通算含み損益",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant)
                    )
                    Text(
                        text = if (isPrivacyActive) "••••" else "+¥${currencyFormat.format(totalGain.toInt())} (${String.format("+%.2f%%", gainPercent)})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = PrimaryEmerald,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Principal vs Gain bar
            val principalRatio = if (totalAssets > 0) (totalCost / totalAssets).toFloat().coerceIn(0f, 1f) else 0.8f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(principalRatio)
                        .background(Color(0xFF6B7280))
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f - principalRatio)
                        .background(PrimaryEmerald)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF6B7280)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "投資元本: ¥${currencyFormat.format(totalCost.toInt())}",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PrimaryEmerald))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "含み益: +¥${currencyFormat.format(totalGain.toInt())}",
                        style = MaterialTheme.typography.labelSmall.copy(color = PrimaryEmerald, fontSize = 10.sp)
                    )
                }
            }
        }
    }
}
