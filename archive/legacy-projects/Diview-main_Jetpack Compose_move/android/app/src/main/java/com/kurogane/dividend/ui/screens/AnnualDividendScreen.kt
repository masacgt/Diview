package com.kurogane.dividend.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import com.kurogane.dividend.model.YearlyAssetSnapshot
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualDividendScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    showTopBar: Boolean = true
) {
    val holdings by viewModel.holdings.collectAsState()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsState()
    val diagnosisBands by viewModel.diagnosisBands.collectAsState()
    val sharedSecurities by viewModel.sharedSecurities.collectAsState()
    val portfolioDefinitions by viewModel.portfolioAnalysisDefinitions.collectAsState()

    // The reference screen opens on dividend growth; analysis and asset tracking remain selectable.
    var selectedTab by remember { mutableIntStateOf(1) } // 0: 分析, 1: 配当推移, 2: 資産推移
    var evalBasis by remember { mutableStateOf("eval") } // "eval" or "cost"

    // The React analytics tabs use a fixed 2025 illustration dataset.
    val totalAssets = 3_500_000.0
    val totalCost = 3_050_000.0
    val totalAnnualDiv = if (evalBasis == "eval") 124_500.0 else 118_200.0
    val yieldOnCost = 4.18
    val yieldOnValuation = 3.56
    val demoAssetHistory = remember {
        listOf(
            YearlyAssetSnapshot("2021", 800_000, 850_000),
            YearlyAssetSnapshot("2022", 1_450_000, 1_580_000),
            YearlyAssetSnapshot("2023", 2_100_000, 2_420_000),
            YearlyAssetSnapshot("2024", 2_750_000, 3_180_000),
            YearlyAssetSnapshot("2025", 3_050_000, 3_500_000)
        )
    }

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        topBar = {
            if (showTopBar) {
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
            }
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
            if (holdings.any { it.category == com.kurogane.dividend.model.Category.US && it.fxRateToJpy <= 0.0 }) {
                item {
                    Text("為替レート未取得の米国資産は、円換算の配当・資産集計に含めていません。", color = OnSurfaceVariant, fontSize = 10.sp)
                }
            }
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
                                "分析",
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
                                "配当推移",
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
                    CompositionAnalysisSection(
                        holdings = holdings,
                        diagnosisBands = diagnosisBands,
                        sharedSecurities = sharedSecurities,
                        portfolioDefinitions = portfolioDefinitions
                    )
                }
            }

            // Tab 1: Dividend Compounding Simulation
            if (selectedTab == 1) {
                item {
                    DividendHistorySection()
                }
            }

            // Tab 2: Asset Tracking
            if (selectedTab == 2) {
                item {
                    AssetTrackingSection(
                        totalAssets = totalAssets,
                        totalCost = totalCost,
                        currencyFormat = currencyFormat,
                        isPrivacyActive = isPrivacyActive,
                        history = demoAssetHistory
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
private fun CompositionAnalysisSection(
    holdings: List<com.kurogane.dividend.model.Holding>,
    diagnosisBands: List<com.kurogane.dividend.model.PortfolioDiagnosisBand>,
    sharedSecurities: Map<String, com.kurogane.dividend.data.SharedSecurityData>,
    portfolioDefinitions: com.kurogane.dividend.data.PortfolioAnalysisDefinitions?
) {
    var isVisible by remember { mutableStateOf(false) }
    var expandedSector by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "donutAnimation"
    )

    val sectorColors = listOf(
        Color(0xFF10B981),
        Color(0xFF06B6D4),
        Color(0xFF8B5CF6),
        Color(0xFFF59E0B),
        Color(0xFF3B82F6),
        Color(0xFFEC4899)
    )
    val totalDividend = 124_500.0
    val sectorBreakdown = listOf(
        "トヨタ自動車" to 47_310.0,
        "Apple Inc." to 34_860.0,
        "eMAXIS Slim 全世界" to 22_410.0,
        "NTT (9432)" to 19_920.0
    )
    val leadingSector = sectorBreakdown.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "セクター・資産別配当比率",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Donut Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val strokeWidth = 24.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2
                    val centerOffset = Offset(size.width / 2, size.height / 2)

                    var startAngle = -90f
                    sectorBreakdown.forEachIndexed { index, (_, amount) ->
                        val sweep = if (totalDividend > 0.0) (amount / totalDividend * 360.0).toFloat() else 0f
                        drawArc(
                            color = sectorColors[index % sectorColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep * animatedProgress,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "年間配当合計",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp)
                    )
                    Text(
                        "¥124,500",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        "4銘柄分散中",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = PrimaryEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Breakdown List
            sectorBreakdown.forEachIndexed { index, (sector, amount) ->
                val ratio = if (totalDividend > 0.0) amount / totalDividend * 100.0 else 0.0
                SectorRow(
                    name = sector,
                    amount = "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(amount.toLong())}",
                    ratio = String.format(Locale.JAPAN, "%.1f%%", ratio),
                    color = sectorColors[index % sectorColors.size]
                )
                if (index < sectorBreakdown.lastIndex) Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("セクター別比率", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("3業種分散", color = PrimaryEmerald, fontSize = 10.sp)
                    }
                    Row(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape)) {
                        Box(Modifier.weight(.452f).fillMaxHeight().background(PrimaryEmerald))
                        Box(Modifier.weight(.328f).fillMaxHeight().background(Color(0xFF38BDF8)))
                        Box(Modifier.weight(.220f).fillMaxHeight().background(Color(0xFF818CF8)))
                    }
                    Text("● 輸送用機器 45.2%     ● 情報・通信 32.8%     ● 全世界・米国 22.0%", color = OnSurfaceVariant, fontSize = 9.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("輸送用機器" to "¥56,274" to "45.2%", "情報・通信" to "¥40,836" to "32.8%", "全世界・米国" to "¥27,390" to "22.0%").forEach { entry ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(BackgroundDark).padding(7.dp)) {
                                Text(entry.first.first, color = OnSurfaceVariant, fontSize = 8.sp, maxLines = 1)
                                Text(entry.first.second, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(entry.second, color = PrimaryEmerald, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }

            Text("セクター・銘柄構成詳細", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp))
            listOf(
                Triple("輸送用機器", "45.2%", "¥56,274 / 年") to "7203  トヨタ自動車 • 保有100株 • 利回り2.8%",
                Triple("情報・通信 / テック", "32.8%", "¥40,836 / 年") to "AAPL  Apple Inc. • 米国株・連続増配",
                Triple("投資信託 / 全世界株式", "22.0%", "再投資型") to "eMAXIS Slim 全世界株式 • 無分配で複利運用"
            ).forEach { (sector, detail) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainer).clickable { expandedSector = if (expandedSector == sector.first) "" else sector.first }.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("${sector.first}  ${sector.second}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(sector.third, color = PrimaryEmerald, fontSize = 10.sp) }
                        Icon(if (expandedSector == sector.first) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnSurfaceVariant)
                    }
                    if (expandedSector == sector.first) { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF273249)); Text(detail, color = OnSurfaceVariant, fontSize = 10.sp) }
                }
            }

            if (holdings.isEmpty()) {
                Text("保有資産を登録すると、ポートフォリオ診断を表示します。", color = OnSurfaceVariant, fontSize = 11.sp)
            } else if (diagnosisBands.isEmpty()) {
                Text("診断定義を取得できません。通信が回復すると表示されます。", color = OnSurfaceVariant, fontSize = 11.sp)
            } else {
                val uniqueHoldings = holdings.distinctBy { it.code }
                val dividendBySecurity = holdings.groupBy { it.code }.mapValues { (_, lots) -> lots.sumOf { it.annualDividend } }
                val dividendConcentration = if (totalDividend > 0.0) {
                    dividendBySecurity.values.maxOrNull()?.div(totalDividend)?.times(100.0)
                } else null
                val knownIncreaseYears = holdings.mapNotNull { sharedSecurities[it.code]?.consecutiveDividendIncreaseYears }
                val averageIncreaseYears = knownIncreaseYears.takeIf { it.isNotEmpty() }?.average()
                val sectorCount = uniqueHoldings.map { it.sector.trim() }.filter { it.isNotBlank() && it != "未分類" }.distinct().size
                val knownPayoutRatios = uniqueHoldings.mapNotNull { sharedSecurities[it.code]?.payoutRatio }
                val healthyPayoutRatioShare = knownPayoutRatios.takeIf { it.isNotEmpty() }?.let { values ->
                    val maxHealthy = portfolioDefinitions?.healthyPayoutRatioMax ?: 70.0
                    values.count { it in 0.0..maxHealthy }.toDouble() / values.size * 100.0
                }
                val knownFcfMargins = uniqueHoldings.mapNotNull { sharedSecurities[it.code]?.fcfMargin }
                val positiveFcfShare = knownFcfMargins.takeIf { it.isNotEmpty() }?.let { values ->
                    values.count { it >= 0.0 }.toDouble() / values.size * 100.0
                }
                val defs = portfolioDefinitions
                fun scoreFor(value: Double?, bands: List<com.kurogane.dividend.data.ScoreBand>, missing: Int): Int {
                    if (value == null) return missing
                    return bands.firstOrNull { band ->
                        (band.min == null || value >= band.min) && (band.max == null || value <= band.max)
                    }?.score ?: missing
                }
                val concentrationScore = scoreFor(dividendConcentration, defs?.concentrationBands.orEmpty(), defs?.missingConcentrationScore ?: 40)
                val sectorScore = scoreFor(sectorCount.toDouble(), defs?.sectorBands.orEmpty(), 20)
                val holdingsScore = scoreFor(uniqueHoldings.size.toDouble(), defs?.holdingBands.orEmpty(), 20)
                val dividendGrowthScore = scoreFor(averageIncreaseYears, defs?.dividendGrowthBands.orEmpty(), defs?.missingDividendGrowthScore ?: 50)
                val payoutScore = scoreFor(healthyPayoutRatioShare, defs?.healthyShareBands.orEmpty(), defs?.missingHealthyShareScore ?: 50)
                val fcfScore = scoreFor(positiveFcfShare, defs?.healthyShareBands.orEmpty(), defs?.missingHealthyShareScore ?: 50)
                val weights = defs?.weights.orEmpty()
                val weightTotal = weights.values.sum().takeIf { it > 0 } ?: 100
                val score = (
                    concentrationScore * (weights["concentration"] ?: 25) +
                    sectorScore * (weights["sector"] ?: 20) +
                    holdingsScore * (weights["holdings"] ?: 10) +
                    dividendGrowthScore * (weights["dividend_growth"] ?: 15) +
                    payoutScore * (weights["payout"] ?: 15) +
                    fcfScore * (weights["fcf"] ?: 15)
                ) / weightTotal
                val diagnosis = diagnosisBands.firstOrNull { score in it.minScore..it.maxScore } ?: diagnosisBands.last()
                val stabilityWeights = defs?.stabilityWeights.orEmpty()
                val stabilityTotal = stabilityWeights.values.sum().takeIf { it > 0 } ?: 100
                val stabilityScore = (
                    sectorScore * (stabilityWeights["sector"] ?: 35) +
                    concentrationScore * (stabilityWeights["concentration"] ?: 35) +
                    fcfScore * (stabilityWeights["fcf"] ?: 30)
                ) / stabilityTotal
                val riskWeights = defs?.dividendRiskWeights.orEmpty()
                val riskTotal = riskWeights.values.sum().takeIf { it > 0 } ?: 100
                val dividendRiskScore = 100 - (
                    payoutScore * (riskWeights["payout"] ?: 40) +
                    fcfScore * (riskWeights["fcf"] ?: 35) +
                    dividendGrowthScore * (riskWeights["dividend_growth"] ?: 25)
                ) / riskTotal
                val stabilityLabel = defs?.stabilityLabels?.firstOrNull { band ->
                    (band.min == null || stabilityScore >= band.min) && (band.max == null || stabilityScore <= band.max)
                }?.label ?: when {
                    stabilityScore >= 85 -> "非常に高い"
                    stabilityScore >= 70 -> "高い"
                    stabilityScore >= 55 -> "標準"
                    stabilityScore >= 40 -> "低め"
                    else -> "低い"
                }
                val dividendRiskLabel = defs?.dividendRiskLabels?.firstOrNull { band ->
                    (band.min == null || dividendRiskScore >= band.min) && (band.max == null || dividendRiskScore <= band.max)
                }?.label ?: when {
                    dividendRiskScore <= 15 -> "低い"
                    dividendRiskScore <= 30 -> "やや低い"
                    dividendRiskScore <= 45 -> "標準"
                    dividendRiskScore <= 60 -> "やや高い"
                    else -> "高い"
                }

                Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundDark)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, tint = PrimaryEmerald)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("AIポートフォリオ診断", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("分散スコア＆リスク評価", color = OnSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
                        Surface(shape = RoundedCornerShape(12.dp), color = PrimaryEmerald.copy(alpha = .18f)) {
                            Text(
                                "健全度: " + diagnosis.grade + " (" + score + "/100)",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = PrimaryEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(diagnosis.comment, color = Color.White, fontSize = 11.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SurfaceContainerHigh) {
                            Column(Modifier.padding(10.dp)) {
                                Text("安定度", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("$stabilityLabel ($stabilityScore/100)", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SurfaceContainerHigh) {
                            Column(Modifier.padding(10.dp)) {
                                Text("減配リスク", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("$dividendRiskLabel ($dividendRiskScore/100)", color = if (dividendRiskScore >= 50) ErrorRed else PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SurfaceContainerHigh) {
                            Column(Modifier.padding(10.dp)) {
                                Text("連続増配年数（取得分）", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(averageIncreaseYears?.let { String.format(Locale.JAPAN, "平均 %.1f年", it) } ?: "データ未取得", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SurfaceContainerHigh) {
                            Column(Modifier.padding(10.dp)) {
                                Text("最大銘柄の配当集中度", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(dividendConcentration?.let { String.format(Locale.JAPAN, "%.1f%%", it) } ?: "配当なし", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SurfaceContainerHigh) {
                            Column(Modifier.padding(10.dp)) {
                                Text("配当性向70%以下", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(healthyPayoutRatioShare?.let { String.format(Locale.JAPAN, "%.0f%%", it) } ?: "データ未取得", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = SurfaceContainerHigh) {
                            Column(Modifier.padding(10.dp)) {
                                Text("FCF黒字銘柄", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(positiveFcfShare?.let { String.format(Locale.JAPAN, "%.0f%%", it) } ?: "データ未取得", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorRow(name: String, amount: String, ratio: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                ratio,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OnSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun DividendHistorySection() {
    val years = listOf(2021, 2022, 2023, 2024, 2025)
    val amounts = listOf(48_000, 66_000, 87_000, 110_800, 124_500)
    val growth = listOf("基準年", "+37.5%", "+31.8%", "+27.4%", "+12.4%")
    var selectedYear by remember { mutableIntStateOf(2025) }
    val selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0)
    val number = NumberFormat.getNumberInstance(Locale.JAPAN)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryEmerald))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("配当受取額の複利成長推移", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("2021年〜2025年 (予想)", color = OnSurfaceVariant, fontSize = 11.sp)
                    }
                    Text("5期連続増配達成", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text("増配と再投資による累積カーブ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5年平均増配率" to "+21.4%", "累計受取総額" to "¥401,500", "簿価利回り (YOC)" to "4.08%").forEach { (label, value) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(BackgroundDark).padding(9.dp)) {
                            Text(label, color = OnSurfaceVariant, fontSize = 8.sp, maxLines = 1)
                            Text(value, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(PrimaryEmerald))
                        Spacer(Modifier.width(8.dp))
                        Text("年次配当額（受取）", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("＋12.4% 増配 (2025予想)", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    years.forEachIndexed { index, year ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if (index == selectedIndex) PrimaryEmerald.copy(alpha = .12f) else BackgroundDark).border(if (index == selectedIndex) 1.dp else .5.dp, if (index == selectedIndex) PrimaryEmerald.copy(alpha = .6f) else SurfaceContainerHigh, RoundedCornerShape(7.dp)).clickable { selectedYear = year }.padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (year == 2025) "2025予想" else "${year}年", color = OnSurfaceVariant, fontSize = 7.sp, maxLines = 1)
                            Text("¥${number.format(amounts[index])}", color = if (index == selectedIndex) PrimaryEmerald else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(growth[index], color = PrimaryEmerald, fontSize = 7.sp)
                        }
                    }
                }
                val maxAmount = amounts.maxOrNull()!!.toFloat()
                Box(Modifier.fillMaxWidth().height(154.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val spacing = size.width / years.size
                        val points = amounts.mapIndexed { index, value ->
                            Offset(spacing * (index + .5f), size.height * .83f - size.height * .67f * value / maxAmount)
                        }
                        for (line in 1..3) {
                            val y = size.height * line / 4f
                            drawLine(Color.White.copy(alpha = .07f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                        amounts.forEachIndexed { index, value ->
                            val x = spacing * (index + .5f)
                            val barHeight = size.height * .67f * value / maxAmount
                            drawRoundRect(PrimaryEmerald.copy(alpha = if (index == selectedIndex) 1f else .78f), Offset(x - spacing * .18f, size.height * .83f - barHeight), Size(spacing * .36f, barHeight), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                        }
                        for (i in 0 until points.lastIndex) drawLine(Color(0xFF38BDF8), points[i], points[i + 1], 2.dp.toPx(), cap = StrokeCap.Round)
                        points.forEachIndexed { index, point -> drawCircle(if (index == selectedIndex) Color.White else Color(0xFF38BDF8), if (index == selectedIndex) 4.dp.toPx() else 3.dp.toPx(), point, style = Stroke(2.dp.toPx())) }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        years.forEachIndexed { index, year -> Text(if (year == 2025) "2025予" else "${year}", color = if (index == selectedIndex) PrimaryEmerald else OnSurfaceVariant, fontSize = 8.sp, modifier = Modifier.clickable { selectedYear = year }) }
                    }
                }
                Surface(color = BackgroundDark, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("● ${selectedYear}年 ${if (selectedYear == 2025) "予想見込" else "実績"}", color = OnSurfaceVariant, fontSize = 9.sp)
                            Text("¥${number.format(amounts[selectedIndex])} / 年", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("月換算 約¥${number.format(amounts[selectedIndex] / 12)} / 月", color = OnSurfaceVariant, fontSize = 9.sp)
                            Text("${if (selectedIndex > 0) "+¥${number.format(amounts[selectedIndex] - amounts[selectedIndex - 1])}" else "基準年"}   ${growth[selectedIndex]}", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("▦  年別インカム詳細・月割シミュレーション", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("5年間増配記録", color = OnSurfaceVariant, fontSize = 9.sp)
        }
        years.indices.reversed().forEach { index ->
            val year = years[index]
            Surface(Modifier.fillMaxWidth().clickable { selectedYear = year }, shape = RoundedCornerShape(14.dp), color = SurfaceContainer, border = if (year == selectedYear) BorderStroke(1.dp, PrimaryEmerald.copy(alpha = .5f)) else null) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (year == 2025) PrimaryEmerald.copy(alpha = .15f) else SurfaceContainerHigh), contentAlignment = Alignment.Center) { Text("’${year.toString().takeLast(2)}", color = if (year == 2025) PrimaryEmerald else OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${year}年 ${if (year == 2025) "予想" else "実績"}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (year == 2025) "過去最高配当" else "確定", color = PrimaryEmerald, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("月換算 約¥${number.format(amounts[index] / 12)} / 月 • ${if (year == 2025) "YOC 4.08%" else "配当受取"}", color = OnSurfaceVariant, fontSize = 9.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("¥${number.format(amounts[index])}", color = if (year == 2025) PrimaryEmerald else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (index > 0) "${growth[index]} 前年比" else "基準年", color = PrimaryEmerald, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetTrackingSection(
    totalAssets: Double,
    totalCost: Double,
    currencyFormat: NumberFormat,
    isPrivacyActive: Boolean,
    history: List<com.kurogane.dividend.model.YearlyAssetSnapshot>
) {
    val totalGain = totalAssets - totalCost
    val gainPercent = if (totalCost > 0) (totalGain / totalCost) * 100 else 0.0
    var rangeMode by remember { mutableStateOf("5y") }
    var selectedYear by remember(history) { mutableStateOf(history.lastOrNull()?.year) }
    val visibleHistory = if (rangeMode == "5y") history.takeLast(5) else history.takeLast(12)
    val selected = visibleHistory.firstOrNull { it.year == selectedYear } ?: visibleHistory.lastOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "資産・配当金推移",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SurfaceContainerHigh)
                    .padding(2.dp)
            ) {
                Surface(
                    modifier = Modifier.clickable {
                        rangeMode = "5y"
                        selectedYear = history.takeLast(5).lastOrNull()?.year
                    },
                    shape = RoundedCornerShape(7.dp),
                    color = if (rangeMode == "5y") PrimaryEmerald else Color.Transparent
                ) {
                    Text(
                        "5年",
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        color = if (rangeMode == "5y") Color.Black else OnSurfaceVariant,
                        fontWeight = if (rangeMode == "5y") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                }
                Surface(
                    modifier = Modifier.clickable {
                        rangeMode = "12m"
                        selectedYear = history.takeLast(12).lastOrNull()?.year
                    },
                    shape = RoundedCornerShape(7.dp),
                    color = if (rangeMode == "12m") PrimaryEmerald else Color.Transparent
                ) {
                    Text(
                        "12ヶ月",
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        color = if (rangeMode == "12m") Color.Black else OnSurfaceVariant,
                        fontWeight = if (rangeMode == "12m") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                }
            }

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

            Spacer(modifier = Modifier.height(18.dp))

            if (visibleHistory.isNotEmpty()) {
                val maxValuation = visibleHistory.maxOf { it.valuation }.coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    visibleHistory.forEach { point ->
                        val principalHeight = (120f * point.principal / maxValuation).dp
                        val valuationHeight = (120f * point.valuation / maxValuation).dp
                        Column(
                            modifier = Modifier.clickable { selectedYear = point.year },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.width(38.dp).height(130.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier.width(34.dp).height(principalHeight)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(Color(0xFF374151))
                                )
                                Box(
                                    Modifier.width(18.dp).height(valuationHeight)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmerald.copy(alpha = .45f))))
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                point.year,
                                color = if (point.year == selected?.year) PrimaryEmerald else OnSurfaceVariant,
                                fontSize = 9.sp,
                                fontWeight = if (point.year == selected?.year) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("年次推移データグリッド（元本 vs 時価対比）", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleHistory.forEach { point ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable { selectedYear = point.year },
                            shape = RoundedCornerShape(8.dp),
                            color = if (point.year == selected?.year) PrimaryEmerald.copy(alpha = .12f) else SurfaceContainerHigh,
                            border = if (point.year == selected?.year) BorderStroke(1.dp, PrimaryEmerald.copy(alpha = .55f)) else null
                        ) {
                            Column(Modifier.padding(7.dp)) {
                                Text(point.year, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                Text("元: " + point.principal / 10000 + "万", color = OnSurfaceVariant, fontSize = 8.sp)
                                Text("時: " + point.valuation / 10000 + "万", color = OnSurfaceVariant, fontSize = 8.sp)
                                Text("+" + point.gain / 10000 + "万", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                selected?.let { selectedPoint ->
                Surface(shape = RoundedCornerShape(12.dp), color = BackgroundDark, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(selectedPoint.year + " 実績", color = OnSurfaceVariant, fontSize = 10.sp)
                            Text("評価額: ¥" + currencyFormat.format(selectedPoint.valuation), color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("投資元本 ¥" + currencyFormat.format(selectedPoint.principal), color = OnSurfaceVariant, fontSize = 10.sp)
                            Text(
                                "含み益 +¥" + currencyFormat.format(selectedPoint.gain) + "  +" + String.format("%.1f", selectedPoint.gainPercent) + "%",
                                color = PrimaryEmerald,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                }

                Spacer(Modifier.height(18.dp))
                Text("年次推移・元本 vs 評価額", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                visibleHistory.asReversed().forEach { point ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedYear = point.year },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHigh,
                        border = if (point.year == selected?.year) BorderStroke(1.dp, PrimaryEmerald.copy(alpha = .5f)) else null
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(point.year, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    "元本 ¥" + currencyFormat.format(point.principal) + "・評価額 ¥" + currencyFormat.format(point.valuation),
                                    color = OnSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+¥" + currencyFormat.format(point.gain), color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                                Text("+" + String.format("%.1f", point.gainPercent) + "% 利益", color = PrimaryEmerald, fontSize = 9.sp)
                            }
                        }
                    }
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
                if (principalRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(principalRatio)
                            .background(Color(0xFF6B7280))
                    )
                }
                if (principalRatio < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f - principalRatio)
                            .background(PrimaryEmerald)
                    )
                }
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
