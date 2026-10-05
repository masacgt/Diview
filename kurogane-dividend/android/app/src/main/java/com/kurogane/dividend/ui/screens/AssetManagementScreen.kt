package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
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
    onOpenAddHolding: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()

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
    var quickEditHolding by remember { mutableStateOf<Holding?>(null) }
    var quickEditShares by remember { mutableStateOf("") }
    var quickEditAcqPrice by remember { mutableStateOf("") }
    var quickEditMarketPrice by remember { mutableDoubleStateOf(0.0) }

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
        avgYield >= 4.0 -> SecondaryIndigo // Purple
        avgYield >= 3.0 -> NeonGreen // Emerald
        avgYield >= 2.0 -> Color(0xFF60A5FA) // Blue
        avgYield >= 0.5 -> AccentAmber // Amber
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
                Icon(Icons.Default.Add, contentDescription = "Add Asset", tint = OnPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        },
        containerColor = SpaceNavy
    ) { innerPadding ->
        val scrollState = rememberScrollState(initial = viewModel.assetScrollPosition)
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { viewModel.assetScrollPosition = it }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (holdings.any { it.currency == "$" && it.usdJpyRate == null }) {
                Text("為替未取得：米ドル資産は円換算合計に含まれません。設定画面から為替を更新できます。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
            }
            // ================= STATE SIMULATOR BAR =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
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
                            Text("STATE SIMULATOR", fontSize = AppTextSize.dense, fontWeight = FontWeight.Black, color = NeonGreenLight)
                        }
                        Text("画面状態の動的切替", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
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
                                    Icon(icon, contentDescription = null, tint = if (isSelected) OnPrimaryContainer else SoftGray, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = AppTextSize.dense, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, color = if (isSelected) OnPrimaryContainer else SoftGray)
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
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("通信エラー / オフライン表示中", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = CircleShape, color = Color(0x33F59E0B)) {
                                    Text("OFFLINE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("最新株価の取得に失敗しました。前回キャッシュデータ（本日 15:00時点）を表示しています。", color = SoftGray, fontSize = AppTextSize.dense, lineHeight = 15.sp)
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
                        Text("該当する銘柄が見つかりません", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("検索条件 \"NVDA\" に一致する保有銘柄はありません。", color = SoftGray, fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { stateSim = "normal" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("フィルター・検索をリセット", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
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
                        Text("まだ保有銘柄が登録されていません", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("証券口座連携は不要です。右下の「＋」ボタンから1分ですぐに保有株式を登録できます。", color = SoftGray, fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { stateSim = "normal" },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("デモデータを復元する", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
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
                            Text("総資産評価額", fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = SoftGray)
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
                        text = if (isPrivacyActive) "投資元本: ¥ •,•••,•••,•••" else "投資元本: ¥${javaCurrencyFormat.format(totalCostVal.toLong())}",
                        fontSize = AppTextSize.caption,
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
                                .background(SurfaceContainer)
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("年間配当目安", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPrivacyActive) "¥ ••••• /年" else "¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())} /年",
                                    fontSize = AppTextSize.headline,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isPrivacyActive) "税引後概算: ¥ •••••" else "税引後概算: ¥${javaCurrencyFormat.format(netAnnualDivVal.toLong())}",
                                    fontSize = AppTextSize.dense,
                                    color = SoftGray
                                )
                            }
                        }

                        // Dividend Yield Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceContainer)
                                .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("平均配当利回り", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (isPrivacyActive) "•••" else String.format("%.2f", avgYield),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("%", fontSize = AppTextSize.dense, color = SoftGray, modifier = Modifier.padding(bottom = 2.dp))
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
                            Text("月別の受取配当見込み額", fontSize = AppTextSize.body, fontWeight = FontWeight.ExtraBold, color = OnSurface)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(SurfaceContainerHigh).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).year.toString(), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                            }
                        }
                        Text(
                            text = if (isPrivacyActive) "年間合計: ¥ ••••••" else "年間合計: ¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())}",
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonGreenLight
                        )
                    }

                    Text("登録された配当月へ均等配分した目安です。配当月未取得：${holdings.count { it.dps > 0 && it.dividendMonths.isEmpty() }}銘柄。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                    Spacer(modifier = Modifier.height(18.dp))

                    // 1-12 Interactive Monthly Bars. Keep each month label inside its own fixed-width slot.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(145.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dynamicMonthlyDividends.forEach { (month, amt, label) ->
                            val isSelected = selectedMonth == month
                            val isSpecialMonth = (month == 6 || month == 12)
                            val heightFraction = (amt / maxMonthlyAmt).toFloat().coerceIn(0.0f, 1.0f)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(145.dp)
                                    .clickable { viewModel.selectMonth(month) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) NeonGreenLight else if (isSpecialMonth) NeonGreenLight else SkyBlue,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(3.dp))

                                val barMaxHeight = 85.dp
                                val barHeight = barMaxHeight * heightFraction

                                Box(
                                    modifier = Modifier.fillMaxWidth().height(barMaxHeight),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(
                                                if (isSelected) {
                                                    Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmeraldContainer))
                                                } else if (isSpecialMonth) {
                                                    Brush.verticalGradient(listOf(NeonGreenLight, NeonGreen))
                                                } else {
                                                    Brush.verticalGradient(listOf(SkyBlue, Color(0xFF0077B6)))
                                                }
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(1.5.dp, OnSurface, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) else Modifier
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "$month",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = AppTextSize.dense,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected || isSpecialMonth) NeonGreenLight else SoftGray,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
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
                                Text("日本株", fontSize = AppTextSize.dense, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("米国株", fontSize = AppTextSize.dense, color = SoftGray)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("投信", fontSize = AppTextSize.dense, color = SoftGray)
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
                                fontSize = AppTextSize.dense,
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
                            Text("年間配当目標: ¥${javaCurrencyFormat.format(currentAnnualTarget)}", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                        }
                        Text(String.format("%.1f%%", progressPct), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (progressPct / 100f).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonGreen,
                        trackColor = BorderColor
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPrivacyActive) "現在: ¥ ••••••" else "現在: ¥${javaCurrencyFormat.format(totalAnnualDivVal.toLong())}",
                            fontSize = AppTextSize.dense,
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
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            color = if (shortage > 0) ErrorRed else NeonGreen
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
                        fontSize = AppTextSize.body,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(shape = CircleShape, color = SurfaceContainerHigh) {
                        Text(
                            "${displayedHoldings.size}件",
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
                            "全件表示",
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

            // ================= SEARCH FIELD =================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("銘柄コード・銘柄名・セクターでリアルタイム検索", color = SoftGray, fontSize = AppTextSize.dense) },
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
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
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
                            color = if (isSelected) OnPrimaryContainer else SoftGray,
                            fontSize = AppTextSize.dense,
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
                        Text("該当する保有銘柄はありません", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                        Text(
                            if (selectedMonth != null) "${selectedMonth}月に入金が予定されている銘柄はありません。" else "指定の条件に一致する銘柄がありません。",
                            color = SoftGray,
                            fontSize = AppTextSize.dense,
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
                    val latestDividendPeriod = financialData[holding.code]?.dividends
                        ?.zipWithNext()
                        ?.lastOrNull { (previous, current) -> current.year == previous.year + 1 }
                    val latestDividendChange = latestDividendPeriod?.let { (previous, current) -> current.dps - previous.dps }
                    val latestDividendGrowth = latestDividendPeriod?.let { (previous, current) ->
                        if (previous.dps > 0.0) (current.dps - previous.dps) / previous.dps * 100.0 else null
                    }
                    val annualDividendChange = latestDividendChange?.let {
                        holding.shares * it / holding.priceUnit * holding.currencyFactor
                    }
                    val payoutMonthSummary = holding.dividendMonths.sorted()
                        .joinToString("・") { "${it}月" }
                        .ifEmpty { "配当月未取得" }

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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            holding.name,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface,
                                            fontSize = AppTextSize.body,
                                            maxLines = 1,
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
                                                    maxLines = 1
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
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                holding.sector,
                                                modifier = Modifier.weight(1f),
                                                color = SoftGray,
                                                fontSize = AppTextSize.dense,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "¥${javaCurrencyFormat.format(curVal.toLong())}",
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val sign = if (diff >= 0) "+" else ""
                                    Text(
                                        "$sign¥${javaCurrencyFormat.format(diff.toLong())} ($sign${String.format("%.1f", diffPct)}%)",
                                        fontWeight = FontWeight.Bold,
                                        color = if (diff >= 0) NeonGreenLight else ErrorRed,
                                        fontSize = AppTextSize.dense,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = BorderBlue, thickness = 0.5.dp)
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
                                    label = "配当利回り",
                                    value = "${String.format(java.util.Locale.JAPAN, "%.2f", holding.yieldRate)}%",
                                    detail = "現在株価比",
                                    modifier = Modifier.weight(1f),
                                    valueColor = NeonGreenLight
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = "予想配当金",
                                    value = if (annualDiv > 0) "¥${javaCurrencyFormat.format(annualDiv.toLong())}/年" else "再投資型",
                                    detail = payoutMonthSummary,
                                    modifier = Modifier.weight(1f)
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = "直近増配",
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
                                    label = "保有数量",
                                    value = "${holding.shares}株",
                                    detail = "@${holding.currency}${javaCurrencyFormat.format(holding.price.toLong())}",
                                    modifier = Modifier.weight(1f),
                                    showEditHint = true,
                                    onClick = {
                                        quickEditHolding = holding
                                        quickEditShares = holding.shares.toString()
                                        quickEditAcqPrice = holding.acqPrice.toString().removeSuffix(".0")
                                        quickEditMarketPrice = holding.price
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
                                            Text("入金予定: ${selectedMonth}月中旬予定", color = SoftGray, fontSize = AppTextSize.dense)
                                        }
                                        Text(holding.nextPayoutAmount, color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
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
                val rate = nextStock.currencyFactor
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
                            Text("直近の配当受取予定", color = SoftGray, fontSize = AppTextSize.dense)
                            Text(if (nextStock != null) "${nextMonth}月中旬 ${nextStock!!.name}" else "保有銘柄なし", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.caption)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("¥${javaCurrencyFormat.format(nextDivAmt)}", fontWeight = FontWeight.Bold, color = NeonGreenLight, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                        Text("${nextStock?.dividendType ?: "配当金"} (${nextStock?.shares ?: 0}株)", color = SoftGray, fontSize = AppTextSize.dense)
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
                    Text("適用する", color = OnPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortModal = false }) {
                    Text("閉じる", color = SoftGray, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Text("並べ替え条件の変更", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
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
                            Text(label, color = if (sortCriteria == key) NeonGreenLight else OnSurface, fontSize = AppTextSize.body, fontWeight = if (sortCriteria == key) FontWeight.Bold else FontWeight.Normal)
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
                            Text("降順 (大→小)", color = if (sortDirection == "desc") OnPrimaryContainer else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { sortDirection = "asc" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (sortDirection == "asc") NeonGreen else CardDarkLighter),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("昇順 (小→大)", color = if (sortDirection == "asc") OnPrimaryContainer else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
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
                    Text("登録する", color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAssetModal = false }) {
                    Text("キャンセル", color = SoftGray, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("銘柄の新規登録・修正", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("保有している配当銘柄の情報を入力してください。ダッシュボードに即時反映されます。", color = SoftGray, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                    OutlinedTextField(
                        value = stockCode,
                        onValueChange = { stockCode = it },
                        label = { Text("銘柄コード (例: 7203)", fontSize = AppTextSize.dense) },
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
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = shareCount,
                        onValueChange = { shareCount = it },
                        label = { Text("保有株数 (例: 100)", fontSize = AppTextSize.dense) },
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
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = acqPrice,
                        onValueChange = { acqPrice = it },
                        label = { Text("取得単価 (例: 2450)", fontSize = AppTextSize.dense) },
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
            onDismissRequest = { quickEditHolding = null },
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
                        Text("保有情報のクイック編集", color = OnSurface, fontSize = AppTextSize.headline, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text("${holding.code}  ${holding.name}  (${holding.market})", color = SoftGray, fontSize = AppTextSize.caption, maxLines = 1)
                    }
                    IconButton(onClick = { quickEditHolding = null }) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる", tint = SoftGray)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                        .border(1.dp, BorderBlue, RoundedCornerShape(16.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("現在値", color = SoftGray, fontSize = AppTextSize.dense)
                        Text("$modalCurrency${javaCurrencyFormat.format(currentPrice)}", color = OnSurface, fontSize = AppTextSize.screenTitle, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    }
                    Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                        Text(
                            "↗ 前日比 ${holding.dailyChange}",
                            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = if (holding.isPositive) NeonGreenLight else ErrorRed,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("株価試算", color = SoftGray, fontSize = AppTextSize.dense, modifier = Modifier.weight(1f))
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
                        Text("戻す", color = NeonGreenLight, fontSize = AppTextSize.dense)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("保有数量 (株)", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                        }
                        Text("単元: 100株", color = SoftGray, fontSize = AppTextSize.caption)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        QuickEditStepButton("−", enabled = shares > 1) { quickEditShares = (shares - 10).coerceAtLeast(1).toString() }
                        OutlinedTextField(
                            value = quickEditShares,
                            onValueChange = { quickEditShares = it.filter(Char::isDigit).ifEmpty { "1" } },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text("株", color = SoftGray) },
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sell, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("取得平均単価 ($modalCurrency)", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { quickEditAcqPrice = currentPrice.toString().removeSuffix(".0") }) {
                            Text("現在値を適用", color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("更新後のリアルタイム試算", color = NeonGreenLight, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                            Text("即時再計算中", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                    Divider(color = BorderBlue)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickEditSummary("投資元本", "$modalCurrency${javaCurrencyFormat.format(cost.toLong())}", OnSurface, Modifier.weight(1f))
                        QuickEditSummary("評価損益", "${if (gain >= 0) "+" else "−"}$modalCurrency${javaCurrencyFormat.format(kotlin.math.abs(gain).toLong())}\n(${String.format(Locale.JAPAN, "%+.1f%%", gainPct)})", if (gain >= 0) NeonGreenLight else ErrorRed, Modifier.weight(1f), Alignment.CenterHorizontally)
                        QuickEditSummary("年間予想配当金", "$modalCurrency${javaCurrencyFormat.format(projectedDividend.toLong())}/年", NeonGreenLight, Modifier.weight(1f), Alignment.End)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { quickEditHolding = null },
                        modifier = Modifier.weight(0.8f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDarkLighter, contentColor = OnSurface),
                        border = BorderStroke(1.dp, BorderBlue)
                    ) { Text("キャンセル", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            if (viewModel.updateHolding(holding.code, shares, averagePrice, currentPrice)) quickEditHolding = null
                        },
                        enabled = editValid,
                        modifier = Modifier.weight(1.5f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Icon(Icons.Default.Check, null, tint = OnPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("変更を保存する", color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold)
                    }
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
        Text(label, color = SoftGray, fontSize = AppTextSize.dense, maxLines = 1)
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
            .border(1.dp, NeonGreenLight.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClick = onClick)
    } else Modifier
    Column(
        modifier = modifier
            .then(clickModifier)
            .padding(horizontal = if (showEditHint) 3.dp else 2.dp, vertical = if (showEditHint) 4.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = SoftGray, fontSize = 9.sp, maxLines = 1)
            if (showEditHint) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = NeonGreenLight.copy(alpha = 0.72f),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        Text(detail, color = detailColor, fontSize = 8.sp, maxLines = 1)
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
