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

@OptIn(ExperimentalMaterial3Api::class)
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
                    CompoundingSimulationSection()
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

@Composable
private fun CompositionAnalysisSection(holdings: List<com.kurogane.dividend.model.Holding>) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "donutAnimation"
    )

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

                    // Sector arcs animated with progress
                    drawArc(
                        color = Color(0xFF10B981), // Emerald - 輸送機器
                        startAngle = -90f,
                        sweepAngle = 140f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF06B6D4), // Cyan - テクノロジー
                        startAngle = 55f,
                        sweepAngle = 100f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFF8B5CF6), // Purple - 銀行
                        startAngle = 160f,
                        sweepAngle = 70f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color(0xFFF59E0B), // Amber - その他
                        startAngle = 235f,
                        sweepAngle = 30f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "筆頭セクター",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp)
                    )
                    Text(
                        "輸送用機器",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        "38.9%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = PrimaryEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Breakdown List
            SectorRow(name = "輸送用機器 (トヨタなど)", amount = "¥145,600", ratio = "38.9%", color = Color(0xFF10B981))
            Spacer(modifier = Modifier.height(8.dp))
            SectorRow(name = "テクノロジー (米国株)", amount = "¥112,000", ratio = "29.9%", color = Color(0xFF06B6D4))
            Spacer(modifier = Modifier.height(8.dp))
            SectorRow(name = "銀行業 (三井住友など)", amount = "¥78,000", ratio = "20.8%", color = Color(0xFF8B5CF6))
            Spacer(modifier = Modifier.height(8.dp))
            SectorRow(name = "通信・情報", amount = "¥38,900", ratio = "10.4%", color = Color(0xFFF59E0B))
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
private fun CompoundingSimulationSection() {
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

            // 5-Year Bar comparison
            val years = listOf("2024", "2025", "2026", "2027", "2028")
            val amounts = listOf(37, 46, 58, 73, 91) // 万円

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

