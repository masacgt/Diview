package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale
import com.kurogane.dividend.ui.components.rememberNotificationAccess
import com.kurogane.dividend.notifications.NotificationKind

private fun sortWatchlist(
    stocks: List<WatchlistStock>,
    criteria: String,
    direction: String
): List<WatchlistStock> = stocks.sortedWith { a, b ->
    val yieldA = com.kurogane.dividend.model.PortfolioMath.yieldPercent(a.dps, a.price)
    val yieldB = com.kurogane.dividend.model.PortfolioMath.yieldPercent(b.dps, b.price)
    val rateA = yieldA / a.targetYield
    val rateB = yieldB / b.targetYield

    val comparison = when (criteria) {
        "ach" -> rateA.compareTo(rateB)
        "yield" -> yieldA.compareTo(yieldB)
        "price" -> a.price.compareTo(b.price)
        else -> a.code.compareTo(b.code)
    }

    if (direction == "desc") -comparison else comparison
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val notificationAccess = rememberNotificationAccess { action ->
        val code = action.substringAfter(':', "")
        if (viewModel.watchlist.value.any { it.code == code && !it.alertEnabled }) viewModel.toggleWatchlistStock(code)
        viewModel.setNotificationEnabled(NotificationKind.PRICE, true)
    }
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.watchlistScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.watchlistScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.watchlistScrollIndex = index
                viewModel.watchlistScrollOffset = offset
            }
    }

    // Dynamic Watchlist Calculations
    val averageYield = if (watchlist.isNotEmpty()) {
        watchlist.map { com.kurogane.dividend.model.PortfolioMath.yieldPercent(it.dps, it.price) }.average()
    } else 0.0

    val currentUnitInvestment = watchlist.sumOf { stock ->
        val unitShares = if (stock.category == "us") 1 else 100
        stock.price * unitShares * stock.currencyFactor
    }

    val potentialDiv = watchlist.sumOf { stock ->
        val shares = stock.defaultShares
        val rate = stock.currencyFactor
        stock.dps * shares * rate
    }

    val heldCount = watchlist.filter { stock -> holdings.any { it.code == stock.code } }.size

    val totalTargetInvestment = watchlist.sumOf { stock ->
        val shares = stock.defaultShares
        val rate = stock.currencyFactor
        val targetPrice = if (stock.targetYield > 0.0) stock.dps / (stock.targetYield / 100.0) else stock.price
        targetPrice * shares * rate
    }

    val targetAverageYield = if (totalTargetInvestment > 0.0) {
        (potentialDiv / totalTargetInvestment) * 100.0
    } else 0.0

    var filterState by remember { mutableStateOf("all") } // "all", "jp", "us", "ready"
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var sortCriteria by remember { mutableStateOf("ach") } // "ach", "yield", "price", "code"
    var sortDirection by remember { mutableStateOf("desc") } // "asc", "desc"
    var draftSortCriteria by remember { mutableStateOf("ach") }
    var draftSortDirection by remember { mutableStateOf("desc") }
    var appliedOrderCodes by remember { mutableStateOf<List<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Bottom Sheet States for ex-dividend matrix and payout calendar
    var showMatrixBottomSheet by remember { mutableStateOf(false) }
    val matrixSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showGraphBottomSheet by remember { mutableStateOf(false) }
    val graphSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Process filters
    val filteredList = watchlist.filter { stock ->
        val currentYield = com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
        val isReady = currentYield >= stock.targetYield
        val queryMatch = searchQuery.isEmpty() ||
                stock.code.contains(searchQuery, ignoreCase = true) ||
                stock.name.contains(searchQuery, ignoreCase = true) ||
                stock.sector.contains(searchQuery, ignoreCase = true)

        val catMatch = when (filterState) {
            "jp" -> stock.category == "jp"
            "us" -> stock.category == "us"
            "ready" -> isReady
            else -> true
        }
        catMatch && queryMatch
    }

    // Capture the default order once, then preserve it while stock values change.
    LaunchedEffect(watchlist.map { it.code }) {
        val existingOrder = appliedOrderCodes
        appliedOrderCodes = if (existingOrder == null) {
            if (watchlist.isEmpty()) null
            else sortWatchlist(watchlist, sortCriteria, sortDirection).map { it.code }
        } else {
            val currentCodes = watchlist.mapTo(mutableSetOf()) { it.code }
            existingOrder.filter { it in currentCodes } +
                watchlist.map { it.code }.filterNot { it in existingOrder }
        }
    }

    val appliedCodes = appliedOrderCodes
    val visibleByCode = filteredList.associateBy { it.code }
    val sortedList = if (appliedCodes == null) {
        sortWatchlist(filteredList, sortCriteria, sortDirection)
    } else {
        buildList {
            appliedCodes.forEach { code -> visibleByCode[code]?.let { add(it) } }
            val placedCodes = appliedCodes.toHashSet()
            filteredList.forEach { stock -> if (stock.code !in placedCodes) add(stock) }
        }
    }

    val readyCount = watchlist.filter { com.kurogane.dividend.model.PortfolioMath.yieldPercent(it.dps, it.price) >= it.targetYield }.size

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("買場到達 ${readyCount}銘柄", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                    Text(" / ${watchlist.size}銘柄監視中", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BackgroundDark)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("買場アラート", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = true,
                                    onCheckedChange = {},
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(checkedThumbColor = OnSurface, checkedTrackColor = PrimaryEmeraldContainer)
                                )
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
                                    Text("初期投資概算（各1単元）", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("¥", fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text(currencyFormat.format(currentUnitInvestment.toLong()), fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("平均現在利回り", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(String.format("%.2f", averageYield), fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontFamily = FontFamily.Monospace)
                                        Text("%", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                    }
                                    Text("東証平均 +1.8%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
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
                                    Text("配当ポテンシャル", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("¥", fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text(currencyFormat.format(potentialDiv.toLong()), fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = FontFamily.Monospace)
                                        Text("/年", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                    }
                                    Text("各1単元保有試算 (${heldCount}銘柄保有済)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                        }
                    }
                }
            }

            // Batch Simulation of Watchlist Stocks Card (Image 1)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "監視銘柄の一括シミュレーション",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "各1単元（日本株100株 / 米国株1株）を現在の目標買場で揃えた場合、初期投資概算 ¥${currencyFormat.format(totalTargetInvestment.toLong())} で年間 +¥${currencyFormat.format(potentialDiv.toLong())}（平均利回り ${String.format("%.2f", targetAverageYield)}%）の不労所得基盤が完成します。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 16.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showMatrixBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("権利落ち月マトリクス", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showGraphBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.BarChart, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("受取月別グラフ試算", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Filters & Sorting Panel (Matching Web layout exactly)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            Pair("all", "すべて (${watchlist.size})"),
                            Pair("jp", "日本株 (${watchlist.filter { it.category == "jp" }.size})"),
                            Pair("us", "米国株 (${watchlist.filter { it.category == "us" }.size})"),
                            Pair("ready", "買場到達 (${readyCount})")
                        )
                        filters.forEach { (id, label) ->
                            val isSelected = filterState == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) PrimaryEmerald else SurfaceContainerLow)
                                    .clickable { filterState = id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) OnPrimary else SoftGray,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Sorting Button and Add Holding Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentLabel = when (sortCriteria) {
                            "ach" -> "達成率"
                            "yield" -> "利回り"
                            "price" -> "株価"
                            else -> "コード"
                        }
                        val currentDirLabel = if (sortDirection == "desc") "降順" else "昇順"

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerLow)
                                .clickable {
                                    draftSortCriteria = sortCriteria
                                    draftSortDirection = sortDirection
                                    showSortBottomSheet = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$currentLabel ($currentDirLabel)",
                                color = SoftGray,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = SoftGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Add stock button (matches layout header)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SurfaceContainerLow)
                                .border(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .clickable { viewModel.addHolding("7203", 100, 2450.0) } // triggers quick default item or similar action
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "銘柄追加",
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Search Field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("監視銘柄コード・銘柄名・セクターで検索", color = SoftGray, fontSize = AppTextSize.dense) },
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
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryEmerald.copy(alpha = 0.5f),
                        unfocusedBorderColor = BorderBlue,
                        focusedContainerColor = SurfaceContainerLow,
                        unfocusedContainerColor = SurfaceContainerLow,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )
            }

            // Stock Cards
            items(items = sortedList, key = { it.code }) { stock ->
                val currentYield = com.kurogane.dividend.model.PortfolioMath.yieldPercent(stock.dps, stock.price)
                val isReached = currentYield >= stock.targetYield
                val isClose = !isReached && (currentYield >= (stock.targetYield - 0.25))
                val yieldAchievement = (currentYield / stock.targetYield * 100.0).coerceIn(0.0, 100.0)
                val targetPriceGap = kotlin.math.abs(stock.price - stock.targetPrice).toInt()
                val targetPriceGapText = when {
                    stock.price > stock.targetPrice -> "あと ${stock.currency}${targetPriceGap} 下落"
                    stock.price < stock.targetPrice -> "目安価格を ${stock.currency}${targetPriceGap} 下回り"
                    else -> "買場目安に到達"
                }
                
                val dynamicBadge = if (isReached) {
                    "絶好の買場到達 (利回り " + String.format("%.2f", currentYield) + "%)"
                } else if (isClose) {
                    "買場目安近接"
                } else if (currentYield >= 4.0) {
                    "超高配当 (利回り " + String.format("%.2f", currentYield) + "%)"
                } else {
                    "優良ディフェンシブ"
                }

                val badgeColor = if (isReached) {
                    PrimaryEmerald
                } else if (isClose) {
                    AccentAmber // Amber
                } else if (currentYield >= 4.0) {
                    Color(0xFF8B5CF6) // Purple
                } else {
                    OnSurfaceVariant // Slate
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isReached) Icons.Default.Verified else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    dynamicBadge,
                                    color = badgeColor,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isReached) PrimaryEmerald else SurfaceContainerHighest
                                ) {
                                    Text(
                                        stock.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isReached) OnPrimary else OnSurface,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stock.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${stock.currency}${stock.price.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    fontSize = AppTextSize.section,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stock.dailyChange,
                                    fontWeight = FontWeight.Bold,
                                    color = if (stock.isPositive) PrimaryEmerald else ErrorRed,
                                    fontSize = AppTextSize.dense,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("現在利回り", color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1)
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            "${String.format(Locale.JAPAN, "%.2f", stock.currentYield)}%",
                                            fontWeight = FontWeight.Bold,
                                            color = if (stock.isReady) PrimaryEmerald else OnSurface,
                                            fontSize = AppTextSize.body,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "目標利回り",
                                            tint = OnSurfaceVariant,
                                            modifier = Modifier.padding(start = 5.dp).size(15.dp)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("目標:", color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, -0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "-", tint = OnSurface, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            "${String.format(Locale.JAPAN, "%.2f", stock.targetYield)}%",
                                            color = PrimaryEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.caption,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, 0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "+", tint = OnSurface, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${String.format(Locale.JAPAN, "%.1f", yieldAchievement)}% 達成",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isReached) "目標達成" else "目標まで あと ${String.format(Locale.JAPAN, "%.2f", stock.targetYield - currentYield)}%",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                LinearProgressIndicator(
                                    progress = { (yieldAchievement / 100.0).toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = if (stock.isReady) PrimaryEmerald else PrimaryEmeraldContainer,
                                    trackColor = SurfaceContainerHighest
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Paid, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "買場目安: ${stock.currency}${stock.targetPrice.toInt()} ($targetPriceGapText)",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            "1株配当 ${stock.currency}${stock.dps}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onOpenAddHolding(stock.code) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = OnPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("保有株に登録", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnPrimary)
                            }

                            IconButton(
                                onClick = {
                                    if (stock.alertEnabled) viewModel.toggleWatchlistStock(stock.code)
                                    else notificationAccess.request("price:${stock.code}")
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainer)
                                    .border(
                                        width = 0.5.dp,
                                        color = if (stock.alertEnabled) PrimaryEmerald.copy(alpha = 0.4f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (stock.alertEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = "Alert Toggle",
                                    tint = if (stock.alertEnabled) PrimaryEmerald else SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Button(
                                onClick = { onNavigateToDetail(stock.code) },
                                modifier = Modifier.width(72.dp).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer, contentColor = OnSurface),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("詳細", fontSize = AppTextSize.dense, color = OnSurface)
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = OnSurface)
                            }
                        }
                    }
                }
            }

            // Big Green Button: Add New Stock to Watchlist
            item {
                Button(
                    onClick = {
                        // Quick add Toyota default template as demonstration or trigger modal
                        viewModel.addHolding("7203", 100, 2600.0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "新しい銘柄をウォッチリストに追加",
                        color = OnPrimary,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            containerColor = CardDark,
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "監視銘柄の並び替え条件",
                            color = OnSurface,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showSortBottomSheet = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    "目標値を変更した後、最新の達成率順にするには「適用する」を押してください。",
                    color = SoftGray,
                    fontSize = AppTextSize.dense
                )

                // Direction Switcher
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "順序",
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundDark)
                            .padding(4.dp)
                    ) {
                        val directions = listOf(
                            Pair("desc", "降順 (大きい順)"),
                            Pair("asc", "昇順 (小さい順)")
                        )
                        directions.forEach { (dir, label) ->
                            val isSelected = draftSortDirection == dir
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryEmerald else Color.Transparent)
                                    .clickable { draftSortDirection = dir }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (dir == "desc") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isSelected) OnPrimary else SoftGray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        color = if (isSelected) OnPrimary else SoftGray,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Criteria Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "ソート項目",
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    val criteriaOptions = listOf(
                        Triple("ach", "利回り達成率 (目安到達に近い順)", Icons.Default.Bolt),
                        Triple("yield", "現在利回り (高配当順)", Icons.Default.TrendingUp),
                        Triple("price", "株価 (購入必要額基準)", Icons.Default.AttachMoney),
                        Triple("code", "銘柄コード / ティッカー順", Icons.Default.Bookmark)
                    )
                    
                    criteriaOptions.forEach { (crit, label, icon) ->
                        val isSelected = draftSortCriteria == crit
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SurfaceContainerHigh else BackgroundDark)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { draftSortCriteria = crit }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSelected) PrimaryEmerald else SoftGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    label,
                                    color = if (isSelected) OnSurface else SoftGray,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Action apply buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showSortBottomSheet = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            "キャンセル",
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.caption
                        )
                    }
                    Button(
                        onClick = {
                            val newlySortedCodes = sortWatchlist(
                                filteredList,
                                draftSortCriteria,
                                draftSortDirection
                            ).map { it.code }
                            val visibleCodes = newlySortedCodes.toHashSet()
                            val previousOrder = appliedOrderCodes ?: watchlist.map { it.code }
                            appliedOrderCodes = newlySortedCodes + previousOrder.filterNot { it in visibleCodes }
                            sortCriteria = draftSortCriteria
                            sortDirection = draftSortDirection
                            showSortBottomSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            "適用する",
                            color = OnPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = AppTextSize.caption
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet 1: 権利落ち月マトリクス (Image 2)
    if (showMatrixBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMatrixBottomSheet = false },
            sheetState = matrixSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("権利落ち月マトリクス", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "ヒートマップ分析",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "監視4銘柄の想定権利落ちスケジュールと配当シミュレーション",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showMatrixBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderColor)

                // KPI Sub-Grid Row (3 Cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("想定配当回数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("10", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("回/年", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Text("日6回 + 米4回 (想定)", color = PrimaryEmerald, fontSize = AppTextSize.dense)
                    }

                    // Card 2
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, PrimaryEmerald.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("次回権利落ち", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("3月27日", color = PrimaryEmerald, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                        Text("あと42日 (買付検討期)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    // Card 3
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("潜在配当規模 (各1単元)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("¥42,800", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("想定利回り 3.84%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }

                // Table Section Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "12ヶ月配当密度ヒートマップマトリクス",
                        color = OnSurface,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )

                    // Small Legend indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("少", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Box(modifier = Modifier.size(6.dp).background(BorderColor))
                        Box(modifier = Modifier.size(6.dp).background(Color(0x3310B981)))
                        Box(modifier = Modifier.size(6.dp).background(PrimaryEmeraldContainer))
                        Text("多", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }

                // Heatmap Scrollable Row Table
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column {
                        // Header Row
                        Row(
                            modifier = Modifier.background(SurfaceContainerHigh).padding(vertical = 6.dp)
                        ) {
                            Text("銘柄", modifier = Modifier.width(80.dp).padding(start = 8.dp), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            (1..12).forEach { m ->
                                Text(
                                    text = "${m}月" + if (m == 3 || m == 9) "\n(権利落)" else if (m == 6 || m == 12) "\n(入金)" else "",
                                    modifier = Modifier.width(52.dp),
                                    color = if (m == 3 || m == 9 || m == 6 || m == 12) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Row 1: 三井住友
                        HeatmapRow(
                            code = "8316",
                            name = "三井住友",
                            cells = mapOf(
                                3 to Pair("権利落\n¥6,500", Color(0x3310B981)),
                                6 to Pair("入金\n¥6,500", PrimaryEmeraldContainer),
                                9 to Pair("権利落\n¥6,500", Color(0x3310B981)),
                                12 to Pair("入金\n¥6,500", PrimaryEmeraldContainer)
                            )
                        )

                        // Row 2: トヨタ
                        HeatmapRow(
                            code = "7203",
                            name = "トヨタ",
                            cells = mapOf(
                                3 to Pair("権利落\n¥3,640", Color(0x2210B981)),
                                6 to Pair("入金\n¥3,640", Color(0xCC10B981)),
                                9 to Pair("権利落\n¥3,640", Color(0x2210B981)),
                                12 to Pair("入金\n¥3,640", Color(0xCC10B981))
                            )
                        )

                        // Row 3: NTT
                        HeatmapRow(
                            code = "9432",
                            name = "NTT",
                            cells = mapOf(
                                3 to Pair("権利落\n¥2,600", Color(0x1F10B981)),
                                6 to Pair("入金\n¥2,600", Color(0xAA10B981)),
                                9 to Pair("権利落\n¥2,600", Color(0x1F10B981)),
                                12 to Pair("入金\n¥2,600", Color(0xAA10B981))
                            )
                        )

                        // Row 4: Apple
                        HeatmapRow(
                            code = "AAPL",
                            name = "アップル",
                            cells = mapOf(
                                2 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f)),
                                5 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f)),
                                8 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f)),
                                11 to Pair("権/入\n$2.5", SecondaryIndigo.copy(alpha = 0.18f))
                            )
                        )

                        // Row 5: 想定受取計 (買付時)
                        Row(
                            modifier = Modifier.background(SurfaceContainerHigh.copy(alpha = 0.5f)).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("想定受取計\n(買付時)", modifier = Modifier.width(80.dp).padding(start = 8.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            (1..12).forEach { m ->
                                val sumText = when (m) {
                                    2, 5, 8, 11 -> "¥385"
                                    6, 12 -> "¥12,740"
                                    else -> "-"
                                }
                                Text(
                                    text = sumText,
                                    modifier = Modifier.width(52.dp),
                                    color = if (sumText != "-") OnSurface else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Analytics advice card below the table matching Image 2
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Scale, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "権利落ち・受取平準化アナリティクス",
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "偏重度: 82%",
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "監視中の日本株3銘柄は 3月・9月末に権利落ちが集中しており、実際に購入した場合の配当受取は 6月・12月に偏ります。米国株（AAPL）をポートフォリオに組み入れることで 2/5/8/11月のインカム空白月を補完できます。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("● 日株 6/12月想定計: ¥25,480", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text("● 米株 想定年間計: ¥1,540", color = SecondaryIndigo, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dismiss Matrix Button
                Button(
                    onClick = { showMatrixBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("マトリクスを閉じる", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal Bottom Sheet 2: 受取月別グラフ試算 (Image 3)
    if (showGraphBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGraphBottomSheet = false },
            sheetState = graphSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("受取月別グラフ試算", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SkyBlue.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "平準化シミュレーション",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = SkyBlue,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "監視4銘柄の入金タイミング・キャッシュフロー偏重分析",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showGraphBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderColor)

                // KPI Row (3 cards side-by-side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Card 1
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("年間受取予定", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("¥42,800/年", color = PrimaryEmerald, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    // Card 2
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("月平均手取り", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("¥3,567/月", color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    // Card 3
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("⚠️ インカム空白", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("4 ヶ月 (無収入)", color = ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }

                // Card 1: Quarter comparison matching Image 3
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PieChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("四半期キャッシュフロー対比", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "Q2・Q4偏重 85.0%",
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Multi-color segmented row representing quarters proportion
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(BorderColor)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().weight(0.075f).background(SecondaryIndigo)) // Q1
                            Box(modifier = Modifier.fillMaxHeight().weight(0.425f).background(PrimaryEmerald)) // Q2
                            Box(modifier = Modifier.fillMaxHeight().weight(0.075f).background(SecondaryIndigo)) // Q3
                            Box(modifier = Modifier.fillMaxHeight().weight(0.425f).background(PrimaryEmerald)) // Q4
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Q1 Box
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q1 (1-3月)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("¥3,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("7.5%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }

                            // Q2 Box - Highlighted Emerald
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .border(1.dp, PrimaryEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q2 (4-6月) 🟢", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("¥18,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("42.5%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            // Q3 Box
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q3 (7-9月)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("¥3,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("7.5%", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }

                            // Q4 Box - Highlighted Emerald
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BackgroundDark)
                                    .border(1.dp, PrimaryEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Q4 (10-12月) 🟢", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("¥18,200", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                Text("42.5%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Card 2: 12-month calendar bar graph Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridOn, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("12ヶ月 受取配当カレンダー棒グラフ", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmeraldContainer))
                                Text("日株集中", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SecondaryIndigo))
                                Text("米株", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF475569)))
                                Text("空白", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Canvas Graph Custom drawing exactly as shown in Image 3
                        val calendarEmptyMonthColor = BorderColor
                        val calendarDividendColor = PrimaryEmeraldContainer
                        val calendarUsColor = SecondaryIndigo
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            val maxBarVal = 17.4f
                            val numMonths = 12
                            val barWidth = 14.dp.toPx()
                            val spacing = (size.width - (barWidth * numMonths)) / (numMonths + 1)

                            for (m in 1..12) {
                                val monthLeft = spacing + (m - 1) * (barWidth + spacing)
                                val (barVal, color) = when (m) {
                                    2, 5, 8, 11 -> Pair(0.8f, calendarUsColor)
                                    3, 9 -> Pair(2.4f, calendarUsColor)
                                    6, 12 -> Pair(17.4f, calendarDividendColor)
                                    else -> Pair(0f, Color.Transparent)
                                }

                                if (barVal > 0f) {
                                    val barHeight = size.height * (barVal / maxBarVal) * 0.85f
                                    val barTop = size.height - barHeight
                                    drawRoundRect(
                                        color = color,
                                        topLeft = Offset(monthLeft, barTop),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    )
                                } else {
                                    // Empty month box
                                    drawRoundRect(
                                        color = calendarEmptyMonthColor,
                                        topLeft = Offset(monthLeft, size.height - 12.dp.toPx()),
                                        size = Size(barWidth, 12.dp.toPx()),
                                        style = Stroke(width = 1.dp.toPx()),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )
                                }
                            }
                        }

                        // Labels row under calendar
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            (1..12).forEach { m ->
                                val label = when (m) {
                                    2, 5, 8, 11 -> "米"
                                    6, 12 -> "日3"
                                    else -> "-"
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$m", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Text(label, color = if (label.startsWith("日")) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("日本株(3/9月期)は 6月・12月 入金", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            Text("⚠️ 空白月: 1, 4, 7, 10月", color = ErrorRed, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Card 3: Income advice card below the graph
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "インカム空白月解消アドバイス",
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x1FF59E0B)
                            ) {
                                Text(
                                    "平準化おすすめ",
                                    color = AccentAmber,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "1月・4月・7月・10月決算の高配当銘柄（積水ハウス等）やREITを追加すると、日本株特有の入金タイムラグを補完し、毎月途切れない配当キャッシュフローが完成します。",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                lineHeight = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Actionable Sekisui House addition row
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SurfaceContainerHighest
                                    ) {
                                        Text(
                                            "1928",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            color = OnSurface,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("積水ハウス", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        Text("1月・7月決算（4月・10月受取）", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("4.12%", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                    Button(
                                        onClick = {
                                            viewModel.addHolding("1928", 100, 2200.0)
                                            showGraphBottomSheet = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("空白月解消に追加", color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 4: Stocks contribution card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("銘柄別 受取月寄与度", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("監視4銘柄", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceContainerHighest
                                ) {
                                    Text("8316", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("三井住友FG", color = OnSurface, fontSize = AppTextSize.dense)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("¥13,000 / 年", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontFamily = FontFamily.Monospace)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text("6月 50%", color = PrimaryEmerald, fontSize = AppTextSize.dense, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.15f)
                                ) {
                                    Text("12月 50%", color = PrimaryEmerald, fontSize = AppTextSize.dense, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom cell row for Ex-dividend heatmap matrix (Image 2)
@Composable
private fun HeatmapRow(
    code: String,
    name: String,
    cells: Map<Int, Pair<String, Color>>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderColor)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(80.dp).padding(start = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SurfaceContainerHighest,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(name, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
        }

        (1..12).forEach { m ->
            val cellData = cells[m]
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(38.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cellData?.second ?: Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (cellData != null) {
                    Text(
                        text = cellData.first,
                        color = OnSurface,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this
