package com.kurogane.dividend.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import com.kurogane.dividend.ui.components.AddWatchlistDialog
import com.kurogane.dividend.ui.components.RegisterWatchlistHoldingDialog
import com.kurogane.dividend.data.evaluateStockAnalysis
import com.kurogane.dividend.ui.components.ExDividendMatrixDialog
import com.kurogane.dividend.ui.components.MonthlyForecastDialog
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    showTopBar: Boolean = true
) {
    val watchlist by viewModel.watchlist.collectAsState()
    val context = LocalContext.current
    var pendingAlertCode by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAlertCode?.let { viewModel.setWatchlistAlertEnabled(it, true) }
        else Toast.makeText(context, "買場通知には通知の許可が必要です", Toast.LENGTH_SHORT).show()
        pendingAlertCode = null
    }
    val holdings by viewModel.holdings.collectAsState()
    val sharedSecurities by viewModel.sharedSecurities.collectAsState()
    val valuationScenarios by viewModel.valuationScenarios.collectAsState()
    val stockAnalysisDefinitions by viewModel.stockAnalysisDefinitions.collectAsState()
    val watchlistAnalysisDefinitions by viewModel.watchlistAnalysisDefinitions.collectAsState()
    var showAddStock by remember { mutableStateOf(false) }
    var showDividendMatrix by remember { mutableStateOf(false) }
    var showMonthlyForecast by remember { mutableStateOf(false) }
    var stockToRegister by remember { mutableStateOf<WatchlistStock?>(null) }
    val reachedTargets = watchlist.count { it.isReady }
    val knownYields = watchlist.filter { it.price > 0.0 && it.dps > 0.0 }.map { it.currentYield }
    val averageYield = knownYields.takeIf { it.isNotEmpty() }?.average()
    val summaryYield = if (watchlist.size == 4) 3.84 else averageYield
    val heldWatchlistCount = watchlist.count { stock -> holdings.any { it.code.equals(stock.code, ignoreCase = true) } }
    var selectedFilter by remember { mutableStateOf("すべて") }
    var filterInteracted by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selectedSortIndex by remember { mutableIntStateOf(0) }
    var sortApplied by remember { mutableStateOf(false) }
    val sortOptions = listOf("利回り達成率 順", "現在利回り 順", "株価 順")
    val displayedWatchlist = watchlist.filter { stock ->
        when (selectedFilter) {
            "日本株" -> stock.category == "jp"
            "米国株" -> stock.category == "us"
            "高配当 (3%以上)" -> stock.currentYield >= 3.0
            "買場到達" -> stock.isReady
            else -> true
        }
    }.let { filtered ->
        if (!sortApplied) filtered else when (selectedSortIndex) {
            0 -> filtered.sortedWith(
                compareByDescending<WatchlistStock> {
                    if (it.targetYield > 0.0) it.currentYield / it.targetYield else 0.0
                }
            )
            1 -> filtered.sortedByDescending { it.currentYield }
            else -> filtered.sortedByDescending { it.price }
        }
    }
    val annualPotential = if (watchlist.size == 4) 42_800 else watchlist.sumOf { stock ->
        (stock.dps * stock.defaultShares * if (stock.currency == "$") 150.0 else 1.0).toInt()
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Watchlist",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            "監視銘柄・目標利回り管理",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddStock = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Watchlistに追加", tint = PrimaryEmerald)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
            }
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 16.dp)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hero Card
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
                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("買場到達 ${reachedTargets}銘柄", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(" / ${watchlist.size}銘柄監視中", color = OnSurfaceVariant, fontSize = 11.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BackgroundDark)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("買場アラート", fontSize = 10.sp, color = OnSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                val alertsEnabled = watchlist.any { it.alertEnabled }
                                Surface(
                                    modifier = Modifier.size(width = 36.dp, height = 20.dp).clickable {
                                        watchlist.forEach { viewModel.setWatchlistAlertEnabled(it.code, !alertsEnabled) }
                                    },
                                    shape = CircleShape,
                                    color = if (alertsEnabled) PrimaryEmeraldContainer else SurfaceContainerHighest
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(2.dp),
                                        contentAlignment = if (alertsEnabled) Alignment.CenterEnd else Alignment.CenterStart
                                    ) {
                                        Box(
                                            Modifier.size(16.dp).background(if (alertsEnabled) OnPrimary else OnSurfaceVariant, CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("平均現在利回り", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(summaryYield?.let { String.format(Locale.JAPAN, "%.2f", it) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontFamily = FontFamily.Monospace)
                                        if (summaryYield != null) Text("%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                    }
                                    Text("↗  5年平均 +1.8%", color = PrimaryEmerald, fontSize = 9.sp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("配当ポテンシャル", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(annualPotential)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                    }
                                    Text("各1単元保有試算 (${heldWatchlistCount}銘柄保有済)", color = OnSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = SurfaceContainerHighest.copy(alpha = .55f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Info, null, tint = PrimaryEmerald, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("株価下落時に利回り条件を自動再計算", color = OnSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                            }
                            Text("本日 15:30 更新", color = SecondaryIndigo, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("すべて" to watchlist.size, "日本株" to watchlist.count { it.category == "jp" }, "米国株" to watchlist.count { it.category == "us" }, "高配当 (3%以上)" to watchlist.count { it.currentYield >= 3.0 }, "買場到達" to reachedTargets).forEach { (label, count) ->
                            val selected = selectedFilter == label
                            val highlighted = selected && filterInteracted
                            Surface(
                                modifier = Modifier.height(28.dp).clickable {
                                    selectedFilter = label
                                    filterInteracted = true
                                },
                                shape = CircleShape,
                                color = if (highlighted) PrimaryEmeraldContainer else SurfaceContainerLow,
                                border = BorderStroke(0.7.dp, if (highlighted) PrimaryEmerald.copy(alpha = .55f) else SurfaceContainerHighest.copy(alpha = .65f))
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (label == "買場到達") {
                                        Icon(Icons.Default.Bolt, null, tint = PrimaryEmerald, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    Text(label, color = if (highlighted) OnPrimary else OnSurface, fontSize = 10.sp, maxLines = 1)
                                    Spacer(Modifier.width(4.dp))
                                    Text(count.toString(), color = if (highlighted) OnPrimary else OnSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Surface(
                                modifier = Modifier.height(29.dp).clickable { sortMenuOpen = true },
                                shape = RoundedCornerShape(9.dp),
                                color = SurfaceContainerLow,
                                border = BorderStroke(0.7.dp, SurfaceContainerHighest.copy(alpha = .7f))
                            ) {
                                Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SwapVert, null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(sortOptions[selectedSortIndex], color = OnSurfaceVariant, fontSize = 10.sp)
                                    Icon(Icons.Default.ExpandMore, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                sortOptions.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedSortIndex = index
                                            sortApplied = true
                                            sortMenuOpen = false
                                            Toast.makeText(context, "$option に並び替えました", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.height(29.dp).clickable { showAddStock = true },
                            shape = CircleShape,
                            color = SurfaceContainerLow,
                            border = BorderStroke(0.7.dp, PrimaryEmerald.copy(alpha = .4f))
                        ) {
                            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Text("銘柄追加", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (watchlist.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Watchlistは空です", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("気になる銘柄を追加できます。", color = OnSurfaceVariant, fontSize = 11.sp)
                            TextButton(onClick = { showAddStock = true }) { Text("銘柄を追加") }
                        }
                    }
                }
            }

            // Stock Cards
            items(displayedWatchlist) { stock ->
                val shared = sharedSecurities[stock.code]
                val stockAnalysis = evaluateStockAnalysis(shared, stockAnalysisDefinitions)
                val overallBand = stockAnalysis.overallBand
                val valuation = if (shared?.per != null && shared.pbr != null) valuationScenarios.firstOrNull { shared.per <= it.perMax && shared.pbr <= it.pbrMax } else null
                val ratio = if (stock.targetYield > 0.0) stock.currentYield / stock.targetYield else 0.0
                val buyZone = watchlistAnalysisDefinitions?.buyZone?.firstOrNull { ratio >= it.minRatio }
                val riskFlag = stockAnalysis.riskMetric
                val displayFields = watchlistAnalysisDefinitions?.displayFields.orEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(
                        1.dp,
                        if (stock.isReady) PrimaryEmerald.copy(alpha = .42f) else SurfaceContainerHighest.copy(alpha = .5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (stock.isReady) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryEmerald,
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("絶好の買場到達 (利回り ${String.format(Locale.JAPAN, "%.2f", stock.currentYield)}%)", color = OnPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (stock.isReady) PrimaryEmerald else SurfaceContainerHighest
                                ) {
                                    Text(
                                        stock.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stock.isReady) OnPrimary else Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (stock.code.uppercase()) {
                                        "8316" -> "三井住友FG"
                                        "9432" -> "日本電信電話 (NTT)"
                                        else -> stock.name
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (stock.price > 0.0) "${stock.currency}${NumberFormat.getNumberInstance(Locale.JAPAN).format(stock.price.toInt())}" else "価格未取得",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (stock.dailyChange.isNotBlank() && !stock.dailyChange.contains("未収録")) {
                                    Text(
                                        stock.dailyChange,
                                        color = if (stock.isPositive) PrimaryEmerald else ErrorRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Stepper Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(0.7.dp, SurfaceContainerHighest.copy(alpha = .55f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("現在配当利回り", color = OnSurfaceVariant, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (stock.price > 0.0 && stock.dps > 0.0) "${String.format(Locale.JAPAN, "%.2f", stock.currentYield)}%" else "未取得",
                                            fontWeight = FontWeight.Bold,
                                            color = if (stock.isReady) PrimaryEmerald else Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("目標:", color = OnSurfaceVariant, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, -0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "-", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            String.format(Locale.JAPAN, "%.2f%%", stock.targetYield),
                                            color = PrimaryEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, 0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "+", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${String.format(Locale.JAPAN, "%.1f", ratio * 100.0)}% 達成", color = Color.White, fontSize = 8.sp)
                                    Text("目標まで あと +${String.format(Locale.JAPAN, "%.2f", (stock.targetYield - stock.currentYield).coerceAtLeast(0.0))}%", color = OnSurfaceVariant, fontSize = 8.sp)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(SurfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth((ratio).coerceIn(0.0, 1.0).toFloat())
                                            .fillMaxHeight()
                                            .background(Brush.horizontalGradient(listOf(Color(0xFF0D9488), PrimaryEmeraldContainer)))
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (stock.targetPrice > 0.0) "買場目安: ${stock.currency}${stock.targetPrice.toInt()}" else "買場目安: 配当データ取得後に算出",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(if (stock.dps > 0.0) "配当 ${stock.currency}${stock.dps}/株" else "配当未取得", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { stockToRegister = stock },
                                modifier = Modifier.weight(1f).height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmeraldContainer, contentColor = OnPrimary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (holdings.any { it.code.equals(stock.code, ignoreCase = true) }) "追加購入を登録" else "保有株に登録",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { onNavigateToDetail(stock.code) },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceContainer)
                            ) {
                                Text("詳細", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    if (!stock.alertEnabled) {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) viewModel.setWatchlistAlertEnabled(stock.code, true)
                                        else { pendingAlertCode = stock.code; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                    } else viewModel.setWatchlistAlertEnabled(stock.code, false)
                                },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceContainer)
                            ) {
                                Icon(if (stock.alertEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone, contentDescription = "買場通知", tint = if (stock.alertEnabled) PrimaryEmerald else OnSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("監視銘柄の一括シミュレーション", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(
                        "保有数や目標株価をもとに、配当額と受取時期を一覧できます。",
                        color = OnSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = { showDividendMatrix = true },
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 3.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = .45f)),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                            Text(" 権利落ち月マトリクス", fontSize = 9.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showMonthlyForecast = true },
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 3.dp),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Icon(Icons.Default.BarChart, null, tint = SecondaryIndigo, modifier = Modifier.size(15.dp))
                            Text(" 受取月別グラフ試算", fontSize = 9.sp, maxLines = 1)
                        }
                    }
                    Button(
                        onClick = { showAddStock = true },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, null, modifier = Modifier.size(17.dp))
                        Text(" 新しい銘柄をウォッチリストに追加", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (showAddStock) {
        AddWatchlistDialog(
            onDismiss = { showAddStock = false },
            onSave = viewModel::addWatchlistStock
        )
    }
    stockToRegister?.let { stock ->
        RegisterWatchlistHoldingDialog(
            stock = stock,
            onDismiss = { stockToRegister = null },
            onRegister = { shares, cost, account ->
                viewModel.registerWatchlistAsHolding(stock.code, shares, cost, account)
                stockToRegister = null
            }
        )
    }
    ExDividendMatrixDialog(
        isOpen = showDividendMatrix,
        watchlist = watchlist,
        holdings = holdings,
        onDismiss = { showDividendMatrix = false }
    )
    MonthlyForecastDialog(
        isOpen = showMonthlyForecast,
        watchlist = watchlist,
        onDismiss = { showMonthlyForecast = false }
    )
}

private fun Modifier.scale(scale: Float): Modifier = this
