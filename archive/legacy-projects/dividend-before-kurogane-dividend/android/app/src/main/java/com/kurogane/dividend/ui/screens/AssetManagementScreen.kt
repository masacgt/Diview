package com.kurogane.dividend.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

// Local High-Fidelity Color Theme matching React Web App CSS
val SpaceNavy = Color(0xFF0B1326)
val CardDark = Color(0xFF131B2E)
val CardDarkLighter = Color(0xFF171F33)
val NeonGreen = Color(0xFF10B981)
val NeonGreenLight = Color(0xFF4EDEA3)
val SoftGray = Color(0xFFBBCABF)
val BorderBlue = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsState()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val taxRateMode by viewModel.taxRateMode.collectAsState()

    // State simulator: "normal", "offline", "loading", "zero", "empty"
    var stateSim by remember { mutableStateOf("normal") }
    var isRetrying by remember { mutableStateOf(false) }

    // Sorting state
    var sortCriteria by remember { mutableStateOf("val") } // "val", "gain", "div", "div_amt", "code"
    var sortDirection by remember { mutableStateOf("desc") } // "desc", "asc"
    var showSortModal by remember { mutableStateOf(false) }

    // Dialog / Modal State
    var showAddAssetModal by remember { mutableStateOf(false) }
    var stockCode by remember { mutableStateOf("") }
    var shareCount by remember { mutableStateOf("") }
    var acqPrice by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val javaCurrencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    // Dynamic calculations from holdings optimized using remember to prevent recomposition overhead
    val totalAssetsVal = remember(holdings) { holdings.sumOf { it.totalValuation } }
    val totalCostVal = remember(holdings) { holdings.sumOf { it.totalCost } }
    val totalGainVal = remember(totalAssetsVal, totalCostVal) { totalAssetsVal - totalCostVal }
    val gainPctVal = remember(totalGainVal, totalCostVal) { if (totalCostVal > 0.0) (totalGainVal / totalCostVal) * 100.0 else 0.0 }
    val totalAnnualDivVal = remember(holdings, taxRateMode) { holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode) } }
    val netAnnualDivVal = remember(holdings) { holdings.sumOf { it.getEffectiveAnnualDividend("net") } }
    val avgYield = remember(totalAnnualDivVal, totalAssetsVal) { if (totalAssetsVal > 0.0) (totalAnnualDivVal / totalAssetsVal) * 100.0 else 0.0 }

    // Dynamic Annual Dividend Target based on currently active roadmap step optimized with remember
    val totalAnnualExpectedDividendForRoadmap = remember(holdings, taxRateMode) {
        holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode).toLong() }
    }
    val monthlyIncomeForRoadmap = remember(totalAnnualExpectedDividendForRoadmap) { totalAnnualExpectedDividendForRoadmap / 12 }

    val milestoneSteps = remember {
        listOf(
            Pair(3000, 36000),     // STEP 1
            Pair(5000, 60000),     // STEP 2
            Pair(10000, 120000),   // STEP 3
            Pair(20000, 240000),   // STEP 4
            Pair(30000, 360000),   // STEP 5
            Pair(50000, 600000),   // STEP 6
            Pair(100000, 1200000), // STEP 7
            Pair(200000, 2400000), // STEP 8
            Pair(300000, 3600000), // STEP 9
            Pair(500000, 6000000)  // STEP 10
        )
    }

    val activeStepPair = remember(monthlyIncomeForRoadmap, milestoneSteps) {
        milestoneSteps.find { monthlyIncomeForRoadmap < it.first } ?: milestoneSteps.last()
    }
    val currentMonthlyTarget = activeStepPair.first
    val currentAnnualTarget = activeStepPair.second

    // Dynamic monthly dividend totals optimized with remember
    val dynamicMonthlyDividends = remember(holdings, taxRateMode) {
        (1..12).map { month ->
            val monthTotal = holdings.filter { it.dividendMonths.contains(month) }.sumOf {
                val countInYear = if (it.dividendMonths.isNotEmpty()) it.dividendMonths.size else 1
                it.getEffectiveAnnualDividend(taxRateMode) / countInYear
            }
            val label = if (monthTotal >= 10000) {
                String.format("%.1f万", monthTotal / 10000.0)
            } else if (monthTotal > 0) {
                String.format("%.1f千", monthTotal / 1000.0)
            } else "0"
            Triple(month, monthTotal, label)
        }
    }
    val maxMonthlyAmt = remember(dynamicMonthlyDividends) { dynamicMonthlyDividends.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0 }

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
    val displayedHoldings = remember(filteredHoldings, sortCriteria, sortDirection, taxRateMode) {
        filteredHoldings.sortedWith { a, b ->
            val comp = when (sortCriteria) {
                "val" -> a.totalValuation.compareTo(b.totalValuation)
                "gain" -> a.gainLossPercent.compareTo(b.gainLossPercent)
                "div" -> a.yieldRate.compareTo(b.yieldRate)
                "div_amt" -> a.getEffectiveAnnualDividend(taxRateMode).compareTo(b.getEffectiveAnnualDividend(taxRateMode))
                "code" -> a.code.compareTo(b.code)
                else -> a.totalValuation.compareTo(b.totalValuation)
            }
            if (sortDirection == "desc") -comp else comp
        }
    }

    val badgeText = when {
        avgYield >= 4.0 -> "超高配当水準 (4%~)"
        avgYield >= 3.0 -> "高配当水準 (3%~)"
        avgYield >= 2.0 -> "中配当水準 (2%~)"
        avgYield >= 0.5 -> "低配当・成長重視"
        else -> "インデックス・極小"
    }

    val badgeIcon = when {
        avgYield >= 4.0 -> Icons.Default.Bolt
        avgYield >= 3.0 -> Icons.Default.CheckCircle
        avgYield >= 2.0 -> Icons.Default.Star
        else -> Icons.Default.Info
    }

    val badgeColor = when {
        avgYield >= 4.0 -> Color(0xFFC084FC) // Purple
        avgYield >= 3.0 -> NeonGreen // Emerald
        avgYield >= 2.0 -> Color(0xFF60A5FA) // Blue
        avgYield >= 0.5 -> Color(0xFFFBBF24) // Amber
        else -> SoftGray // Slate
    }

    fun getSortLabel(): String {
        val dir = if (sortDirection == "desc") "降順" else "昇順"
        return when (sortCriteria) {
            "val" -> "評価額 ($dir)"
            "gain" -> "損益率 ($dir)"
            "div" -> "利回り ($dir)"
            "div_amt" -> "配当額 ($dir)"
            "code" -> "コード ($dir)"
            else -> "評価額 ($dir)"
        }
    }

    Scaffold(
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
                Icon(Icons.Default.Add, contentDescription = "Add Asset", tint = Color(0xFF031C13), modifier = Modifier.size(28.dp))
            }
        },
        containerColor = SpaceNavy
    ) { innerPadding ->
        val scrollState = rememberScrollState(initial = viewModel.assetScrollPosition)
        LaunchedEffect(scrollState.value) {
            viewModel.assetScrollPosition = scrollState.value
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ================= STATE SIMULATOR BAR =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11192C)),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonGreen))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STATE SIMULATOR", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonGreenLight)
                        }
                        Text("画面状態の動的切替", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SoftGray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val simTabs = listOf(
                        Triple("normal", "① 通常・一覧", Icons.Default.BarChart),
                        Triple("offline", "② オフライン", Icons.Default.WifiOff),
                        Triple("loading", "③ 読込中", Icons.Default.Sync),
                        Triple("zero", "④ 検索ゼロ", Icons.Default.SearchOff),
                        Triple("empty", "⑤ 初期未登録", Icons.Default.AddCircle)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        simTabs.forEach { (id, label, icon) ->
                            val isSelected = stateSim == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) NeonGreen else CardDarkLighter)
                                    .border(0.5.dp, if (isSelected) NeonGreen else BorderBlue, RoundedCornerShape(12.dp))
                                    .clickable {
                                        stateSim = id
                                        if (id == "loading") {
                                            // Automatically restore to normal after simulated load
                                            isRetrying = true
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, contentDescription = null, tint = if (isSelected) Color.Black else SoftGray, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, color = if (isSelected) Color.Black else SoftGray)
                                }
                            }
                        }
                    }
                }
            }

            // ================= STATE: OFFLINE =================
            if (stateSim == "offline") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x26F59E0B)),
                    border = BorderStroke(1.dp, Color(0x66F59E0B))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("通信エラー / オフライン表示中", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = CircleShape, color = Color(0x33F59E0B)) {
                                    Text("OFFLINE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFFDE68A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("最新株価の取得に失敗しました。前回キャッシュデータ（本日 15:00時点）を表示しています。", color = SoftGray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }

            // ================= STATE: ZERO SEARCH =================
            if (stateSim == "zero") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = SoftGray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("該当する銘柄が見つかりません", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("検索条件 \"NVDA\" に一致する保有銘柄はありません。", color = SoftGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { stateSim = "normal" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("フィルター・検索をリセット", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ================= STATE: EMPTY =================
            if (stateSim == "empty") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, contentDescription = null, tint = SoftGray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("まだ保有銘柄が登録されていません", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("証券口座連携は不要です。右下の「＋」ボタンから1分ですぐに保有株式を登録できます。", color = SoftGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { stateSim = "normal" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("デモデータを復元する", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ================= TOTAL ASSET HERO CARD =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("総資産評価額", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SoftGray)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Privacy",
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
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                val gainText = if (isPrivacyActive) "+¥••••••" else {
                                    val sign = if (totalGainVal >= 0) "+" else ""
                                    "$sign¥${javaCurrencyFormat.format(totalGainVal.toLong())} ($sign${String.format("%.1f", gainPctVal)}%)"
                                }
                                Text(gainText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (totalGainVal >= 0) NeonGreen else Color(0xFFFF4D4D))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isPrivacyActive) "¥ •,•••,•••,•••" else "¥${javaCurrencyFormat.format(totalAssetsVal.toLong())}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyActive) "投資元本: ¥ •,•••,•••,•••" else "投資元本: ¥${javaCurrencyFormat.format(totalCostVal.toLong())}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SoftGray
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = BorderBlue, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sub-Grid 2 Columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Expected Dividend Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0C1322))
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("年間予想受取配当", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPrivacyActive) "¥ ••••• /年" else "¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())} /年",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isPrivacyActive) "税引後概算: ¥ •••••" else "税引後概算: ¥${javaCurrencyFormat.format(netAnnualDivVal.toLong())}",
                                    fontSize = 10.sp,
                                    color = SoftGray
                                )
                            }
                        }

                        // Dividend Yield Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0C1322))
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("平均配当利回り", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format("%.2f", avgYield),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("%", fontSize = 10.sp, color = SoftGray, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(badgeText, fontSize = 8.sp, fontWeight = FontWeight.Black, color = badgeColor)
                                }
                            }
                        }
                    }
                }
            }

            // ================= MONTHLY CHART CARD (INTERACTIVE) =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("月別推移グラフ", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF1C253B)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("2024", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SoftGray)
                            }
                        }
                        Text(
                            text = if (isPrivacyActive) "年間合計: ¥ ••••••" else "年間合計: ¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonGreenLight
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 1-12 Interactive Monthly Bars
                    Row(
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dynamicMonthlyDividends.forEach { (month, amt, label) ->
                            val isSelected = selectedMonth == month
                            val isSpecialMonth = (month == 6 || month == 12)
                            val heightFraction = (amt / maxMonthlyAmt).toFloat().coerceIn(0.12f, 1.0f)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { viewModel.selectMonth(month) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) NeonGreenLight else if (isSpecialMonth) NeonGreenLight else Color(0xFF00B4D8),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(15.dp)
                                        .fillMaxHeight(heightFraction)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.verticalGradient(listOf(Color(0xFF4EDEA3), Color(0xFF10B981)))
                                            } else if (isSpecialMonth) {
                                                Brush.verticalGradient(listOf(NeonGreenLight, NeonGreen))
                                            } else {
                                                Brush.verticalGradient(listOf(Color(0xFF00B4D8), Color(0xFF0077B6)))
                                            }
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) else Modifier
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$month",
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) NeonGreenLight else SoftGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = BorderBlue, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Legend & Selected Month Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonGreenLight))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("日本株", fontSize = 9.sp, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("米国株", fontSize = 9.sp, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("投信", fontSize = 9.sp, color = SoftGray)
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = NeonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, NeonGreen.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = if (selectedMonth != null) "${selectedMonth}月タップ中" else "月平均: 約¥${javaCurrencyFormat.format((totalAnnualDivVal / 12).toLong())}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = NeonGreenLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ================= GOAL PROGRESS CARD =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val progressPct = ((totalAnnualDivVal / currentAnnualTarget.toDouble()) * 100).coerceAtLeast(0.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("年間配当目標: ¥${javaCurrencyFormat.format(currentAnnualTarget)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text(String.format("%.1f%%", progressPct), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (progressPct / 100f).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonGreen,
                        trackColor = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPrivacyActive) "現在: ¥ ••••••" else "現在: ¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())}",
                            fontSize = 10.sp,
                            color = SoftGray
                        )
                        val shortage = currentAnnualTarget.toDouble() - totalAnnualDivVal
                        Text(
                            text = if (shortage > 0) {
                                if (isPrivacyActive) {
                                    val formattedTarget = if (currentMonthlyTarget >= 10000) "${currentMonthlyTarget / 10000}万円" else "${javaCurrencyFormat.format(currentMonthlyTarget)}円"
                                    "あと ¥ •••••• で月${formattedTarget}達成"
                                } else {
                                    "あと ¥${javaCurrencyFormat.format(shortage.toLong())} で目標クリア"
                                }
                            } else "目標達成済！不労所得生活達成✨",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (shortage > 0) Color(0xFFFFB4AB) else NeonGreen
                        )
                    }
                }
            }

            // ================= HOLDINGS LIST HEADER & SORT =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedMonth != null) "${selectedMonth}月の配当入金ポートフォリオ" else "保有銘柄一覧",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = CircleShape, color = Color(0xFF172033)) {
                        Text(
                            "${displayedHoldings.size}件",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = NeonGreenLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedMonth != null) {
                        Text(
                            "全件表示",
                            color = NeonGreenLight,
                            fontSize = 11.sp,
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
                            Text(getSortLabel(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ================= SEARCH FIELD =================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("銘柄コード・銘柄名・セクターでリアルタイム検索", color = SoftGray, fontSize = 11.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // ================= CATEGORY FILTER CHIPS =================
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chips = listOf(
                    Pair(null, "すべて (${holdings.size})"),
                    Pair(Category.JAPAN, "日本株 (${holdings.filter { it.category == Category.JAPAN }.size})"),
                    Pair(Category.US, "米国株 (${holdings.filter { it.category == Category.US }.size})"),
                    Pair(Category.FUND, "投信 (${holdings.filter { it.category == Category.FUND }.size})")
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
                            color = if (isSelected) Color.Black else SoftGray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }
            }

            // ================= HOLDING STOCK CARDS LIST =================
            if (displayedHoldings.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = SoftGray, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("該当する保有銘柄はありません", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (selectedMonth != null) "${selectedMonth}月に入金が予定されている銘柄はありません。" else "指定の条件に一致する銘柄がありません。",
                            color = SoftGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                displayedHoldings.forEach { holding ->
                    val isUSD = holding.currency == "$"
                    val curVal = holding.totalValuation
                    val curCost = holding.totalCost
                    val diff = holding.gainLoss
                    val diffPct = holding.gainLossPercent
                    val annualDiv = holding.getEffectiveAnnualDividend(taxRateMode)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (holding.category) {
                                            Category.US -> Color(0xFF1E1B4B)
                                            Category.FUND -> Color(0xFF4C0519)
                                            else -> Color(0xFF1E293B)
                                        }
                                    ) {
                                        Text(
                                            holding.code,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = when (holding.category) {
                                                Category.US -> Color(0xFFA5B4FC)
                                                Category.FUND -> Color(0xFFFDA4AF)
                                                else -> Color.White
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(holding.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                        Text(holding.sector, color = SoftGray, fontSize = 9.sp)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "¥${javaCurrencyFormat.format(curVal.toLong())}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val sign = if (diff >= 0) "+" else ""
                                    Text(
                                        "$sign¥${javaCurrencyFormat.format(diff.toLong())} ($sign${String.format("%.1f", diffPct)}%)",
                                        fontWeight = FontWeight.Bold,
                                        color = if (diff >= 0) NeonGreenLight else Color(0xFFFFB4AB),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = BorderBlue, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // 3-Column Metrics: Shares, Yield, Annual Dividend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("保有数量", color = SoftGray, fontSize = 9.sp)
                                    Text(
                                        "${holding.shares}株 (${holding.currency}${holding.price.toInt()})",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("配当利回り", color = SoftGray, fontSize = 9.sp)
                                    Text(
                                        "${holding.yieldRate}%",
                                        color = NeonGreenLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("予想配当金", color = SoftGray, fontSize = 9.sp)
                                    Text(
                                        if (annualDiv > 0) "¥${javaCurrencyFormat.format(annualDiv.toLong())}/年" else "再投資型",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
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
                                            Text("入金予定: ${selectedMonth}月中旬予定", color = SoftGray, fontSize = 10.sp)
                                        }
                                        Text(holding.nextPayoutAmount, color = NeonGreenLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // Bottom row: Account type and Next Payout Timing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF1E293B)) {
                                    Text(holding.accountType, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFA5B4FC), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(holding.nextPayoutTiming, color = SoftGray, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ================= NEXT DIVIDEND PAYOUT STRIP (100% DYNAMIC) =================
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
                val rate = if (nextStock.currency == "$") 154.0 else 1.0
                val monthsCount = if (nextStock.dividendMonths.isNotEmpty()) nextStock.dividendMonths.size else 2
                ((nextStock.shares * nextStock.dps * rate) / monthsCount).toLong()
            } else 0L

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDarkLighter),
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
                            Text("直近の配当受取予定", color = SoftGray, fontSize = 9.sp)
                            Text(if (nextStock != null) "${nextMonth}月中旬 ${nextStock!!.name}" else "保有銘柄なし", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("¥${javaCurrencyFormat.format(nextDivAmt)}", fontWeight = FontWeight.Bold, color = NeonGreenLight, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("${nextStock?.dividendType ?: "配当金"} (${nextStock?.shares ?: 0}株)", color = SoftGray, fontSize = 9.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                    Text("適用する", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortModal = false }) {
                    Text("閉じる", color = SoftGray, fontSize = 12.sp)
                }
            },
            title = {
                Text("並べ替え条件の変更", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val criteriaOptions = listOf(
                        Pair("val", "評価額"),
                        Pair("gain", "含み損益率"),
                        Pair("div", "配当利回り"),
                        Pair("div_amt", "年間予想配当額"),
                        Pair("code", "銘柄コード")
                    )
                    criteriaOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sortCriteria == key) CardDarkLighter else Color.Transparent)
                                .clickable { sortCriteria = key }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = if (sortCriteria == key) NeonGreenLight else Color.White, fontSize = 13.sp, fontWeight = if (sortCriteria == key) FontWeight.Bold else FontWeight.Normal)
                            if (sortCriteria == key) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Divider(color = BorderBlue, thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { sortDirection = "desc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "desc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("降順 (大→小)", color = if (sortDirection == "desc") Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { sortDirection = "asc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "asc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("昇順 (小→大)", color = if (sortDirection == "asc") Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("登録する", color = Color(0xFF031C13), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAssetModal = false }) {
                    Text("キャンセル", color = SoftGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("銘柄の新規登録・修正", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("保有している配当銘柄の情報を入力してください。ダッシュボードに即時反映されます。", color = SoftGray, fontSize = 11.sp, lineHeight = 15.sp)
                    OutlinedTextField(
                        value = stockCode,
                        onValueChange = { stockCode = it },
                        label = { Text("銘柄コード (例: 7203)", fontSize = 11.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (stockCode.isNotEmpty()) {
                                IconButton(onClick = { stockCode = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = shareCount,
                        onValueChange = { shareCount = it },
                        label = { Text("保有株数 (例: 100)", fontSize = 11.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (shareCount.isNotEmpty()) {
                                IconButton(onClick = { shareCount = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = acqPrice,
                        onValueChange = { acqPrice = it },
                        label = { Text("取得単価 (例: 2450)", fontSize = 11.sp) },
                        singleLine = true,
                        trailingIcon = {
                            if (acqPrice.isNotEmpty()) {
                                IconButton(onClick = { acqPrice = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray, modifier = Modifier.size(16.dp))
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, BorderBlue, RoundedCornerShape(20.dp))
        )
    }
}
