package com.kurogane.dividend.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import com.kurogane.dividend.ui.components.AddHoldingDialog
import com.kurogane.dividend.ui.components.SecuritySearchSheet
import com.kurogane.dividend.ui.components.NotificationHistorySheet
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    showTopBar: Boolean = true
) {
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    var showAddHolding by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var simulatorState by remember { mutableStateOf("normal") }

    // Match the React screen's normal-state demonstration dashboard; the holdings list remains user-data driven.
    val totalAssets = 3_500_000.0
    val totalCost = 3_050_000.0
    val totalGain = 450_000.0
    val totalAnnualDiv = 124_500.0
    val japaneseAfterTaxEstimate = 99_195.0
    val avgYield = 3.56
    val monthlyDividends = listOf(2100, 3800, 12400, 1500, 4200, 38500, 2100, 3200, 9800, 1600, 4800, 42000)
        .mapIndexed { index, amount -> com.kurogane.dividend.model.MonthlyDividend(index + 1, amount, when { amount >= 10000 -> "${amount / 10000.0}万"; else -> "${amount / 1000.0}千" }) }
    val annualDividendTotal = totalAnnualDiv.toInt()
    val monthlyChartMax = monthlyDividends.maxOfOrNull { it.amount }?.coerceAtLeast(1) ?: 1
    val annualDividendGoal = 300000.0
    val dividendGoalProgress = (annualDividendTotal / annualDividendGoal).coerceIn(0.0, 1.0)
    val remainingToGoal = (annualDividendGoal - annualDividendTotal).coerceAtLeast(0.0)

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val displayedHoldings = holdings.filter { h ->
        val monthMatch = selectedMonth == null || h.dividendMonths.contains(selectedMonth)
        val catMatch = selectedCategory == null || h.category == selectedCategory
        monthMatch && catMatch
    }

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
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = PrimaryEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "資産管理",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                "Asset Management",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddHolding = true }) {
                        Icon(Icons.Default.Add, contentDescription = "保有資産を登録", tint = PrimaryEmerald)
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, contentDescription = "銘柄検索を開く", tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = { showNotifications = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "通知履歴を開く", tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定を開く", tint = OnSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark.copy(alpha = 0.95f)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6171F33)).border(1.dp, Color(0xFF26334A), RoundedCornerShape(14.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("● STATE SIMULATOR", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("画面状態の動的切替", color = OnSurfaceVariant, fontSize = 9.sp)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("normal" to "① 通常・一覧", "offline" to "② オフライン", "loading" to "③ 読込中", "zero" to "④ 検索ゼロ", "empty" to "⑤ 初期未登録").forEach { (id, label) ->
                            Surface(
                                modifier = Modifier.clip(CircleShape).clickable { simulatorState = id },
                                color = if (simulatorState == id) PrimaryEmeraldContainer else Color(0xFF0B1326),
                                shape = CircleShape
                            ) {
                                Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (simulatorState == id) Color(0xFF003824) else OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (simulatorState == "offline") item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF332819)), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, null, tint = Color(0xFFFFC36B))
                        Column(Modifier.weight(1f)) {
                            Text("通信エラー / オフライン表示中", color = Color(0xFFFFC36B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("保存済みの保有情報を端末内から表示しています。", color = OnSurfaceVariant, fontSize = 10.sp)
                        }
                    }
                }
            }
            // 1. Total Asset Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("総資産評価額", color = OnSurfaceVariant, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.togglePrivacy() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Privacy",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    if (isPrivacyActive) "+¥ ••••••" else {
                                        val gainPercent = if (totalCost > 0.0) totalGain / totalCost * 100.0 else 0.0
                                        String.format("%s¥%s (%.1f%%)", if (totalGain >= 0) "+" else "", currencyFormat.format(totalGain.toLong()), gainPercent)
                                    },
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = if (isPrivacyActive) "¥ ••••••••" else "¥${currencyFormat.format(totalAssets.toLong())}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "投資元本: " + if (isPrivacyActive) "¥ ••••••••" else "¥${currencyFormat.format(totalCost.toLong())}",
                            color = OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                        if (holdings.any { it.category == Category.US && it.fxRateToJpy <= 0.0 }) {
                            Text("為替レート未取得の米国資産は、円換算の合計に含めていません。", color = OnSurfaceVariant, fontSize = 9.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dividend Sub-tiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceContainer.copy(alpha = 0.8f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("年間予想受取配当", color = OnSurfaceVariant, fontSize = 11.sp)
                                    Text(
                                        if (isPrivacyActive) "¥ ••••••" else "¥${currencyFormat.format(totalAnnualDiv.toLong())}",
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text("税引後概算（日本株）: " + if (isPrivacyActive) "••••••" else "¥${currencyFormat.format(japaneseAfterTaxEstimate.toLong())}", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Text("課税口座20.315%控除・NISA非課税の試算。外国税等は含みません。", color = OnSurfaceVariant, fontSize = 8.sp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceContainer.copy(alpha = 0.8f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("平均配当利回り", color = OnSurfaceVariant, fontSize = 11.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (isPrivacyActive) "•••" else if (totalAssets > 0.0) String.format("%.2f", avgYield) else "—",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text("%", color = OnSurfaceVariant, fontSize = 11.sp)
                                    }
                                    Text("好配当水準", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Monthly Dividend Chart Card (Interactive Pivot)
            item {
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
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("月別推移グラフ", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("2024", color = OnSurfaceVariant, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                text = if (selectedMonth != null) "${selectedMonth}月予定: ¥${currencyFormat.format(monthlyDividends[selectedMonth!! - 1].amount)}" else "年間合計: ¥${currencyFormat.format(annualDividendTotal)}",
                                color = PrimaryEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Interactive 1-12 Bars
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            monthlyDividends.forEach { col ->
                                val isSelected = selectedMonth == col.month
                                val heightFraction = if (annualDividendTotal > 0) (col.amount.toFloat() / monthlyChartMax.toFloat()).coerceIn(0.04f, 1f) else 0.04f

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { viewModel.selectMonth(col.month) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        col.label,
                                        fontSize = 8.sp,
                                        color = if (isSelected) PrimaryEmerald else SkyBlue,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .fillMaxHeight(heightFraction)
                                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                            .background(
                                                if (col.amount > 0)
                                                    Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmeraldContainer))
                                                else
                                                    Brush.verticalGradient(listOf(SkyBlue, SkyBlue.copy(alpha = 0.7f)))
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${col.month}",
                                        fontSize = 9.sp,
                                        color = if (isSelected) PrimaryEmerald else OnSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Goal Progress Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("年間配当目標: ¥300,000", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text("${(dividendGoalProgress * 100).toInt()}%", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { dividendGoalProgress.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = PrimaryEmeraldContainer,
                            trackColor = SurfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("現在: ¥${currencyFormat.format(annualDividendTotal)}", color = OnSurfaceVariant, fontSize = 11.sp)
                            Text("あと ¥${currencyFormat.format(remainingToGoal)} で月2.5万円達成", color = OnSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }

            // 4. Category Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        null to "すべて (${holdings.size})",
                        Category.JAPAN to "日本株 (${holdings.count { it.category == Category.JAPAN }})",
                        Category.US to "米国株 (${holdings.count { it.category == Category.US }})",
                        Category.FUND to "投信 (${holdings.count { it.category == Category.FUND }})"
                    )
                    categories.forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) PrimaryEmeraldContainer else SurfaceContainerHigh,
                            modifier = Modifier.clickable { viewModel.selectCategory(cat) }
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) OnPrimary else OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Holdings List
            if (displayedHoldings.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("保有資産はまだ登録されていません", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("保有中の株式・投資信託を登録すると、資産額と配当をここに表示します。", color = OnSurfaceVariant, fontSize = 11.sp)
                            TextButton(onClick = { showAddHolding = true }) { Text("保有資産を登録") }
                        }
                    }
                }
            }
            items(if (simulatorState == "empty" || simulatorState == "zero") emptyList() else displayedHoldings) { holding ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(holding.code) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceContainerHighest
                                ) {
                                    Text(
                                        holding.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(holding.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text(holding.sector, color = OnSurfaceVariant, fontSize = 11.sp)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (holding.fxRateToJpy > 0.0) "¥${currencyFormat.format(holding.totalValuation.toLong())}" else "${holding.currency}${currencyFormat.format((holding.shares * holding.price).toLong())}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    holding.dailyChange,
                                    color = when {
                                        holding.dailyChange.contains("+") -> PrimaryEmerald
                                        holding.dailyChange.contains("-") -> ErrorRed
                                        else -> OnSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                TextButton(
                                    onClick = { viewModel.removeHolding(holding.id) },
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                ) { Text("削除", color = ErrorRed, fontSize = 10.sp) }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceContainerHighest)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("保有数量", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("${holding.shares}株 (@${holding.currency}${holding.price.toInt()})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("配当利回り", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("${holding.yieldRate}%", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("予想配当金", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(
                                    if (holding.fxRateToJpy > 0.0) "¥${currencyFormat.format(holding.annualDividend.toLong())}/年" else "${holding.currency}${currencyFormat.format((holding.shares * holding.dps).toLong())}/年",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                holding.accountType,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = SecondaryIndigo
                            )
                            Text(
                                holding.nextPayoutTiming,
                                color = OnSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }

            if (simulatorState == "zero" || simulatorState == "empty") item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (simulatorState == "zero") Icons.Default.SearchOff else Icons.Default.AccountBalanceWallet, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                        Text(if (simulatorState == "zero") "該当する銘柄が見つかりません" else "保有資産はまだ登録されていません", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (simulatorState == "zero") "検索条件に一致する保有銘柄はありません。" else "保有株式・投資信託を登録すると、資産額と配当を表示します。", color = OnSurfaceVariant, fontSize = 11.sp)
                        TextButton(onClick = { simulatorState = "normal" }) { Text("表示をリセット", color = PrimaryEmerald) }
                    }
                }
            }
        }
    }

    if (showAddHolding) {
        AddHoldingDialog(
            onDismiss = { showAddHolding = false },
            onSave = { holding ->
                viewModel.addHolding(holding)
                showAddHolding = false
            }
        )
    }
    if (showSearch) {
        SecuritySearchSheet(
            viewModel = viewModel,
            onDismiss = { showSearch = false },
            onSelect = { code ->
                showSearch = false
                onNavigateToDetail(code)
            }
        )
    }
    if (showNotifications) {
        NotificationHistorySheet(
            viewModel = viewModel,
            onDismiss = { showNotifications = false },
            onSelect = { code ->
                showNotifications = false
                onNavigateToDetail(code)
            },
            onOpenWatchlist = {
                showNotifications = false
                onNavigateToWatchlist()
            }
        )
    }
}
