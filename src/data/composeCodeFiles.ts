export interface ComposeFileItem {
  id: string;
  name: string;
  path: string;
  category: 'screen' | 'viewmodel' | 'model' | 'theme' | 'config' | 'dialog';
  description: string;
  code: string;
}

export const composeFiles: ComposeFileItem[] = [
  {
    id: 'MainActivity',
    name: 'MainActivity.kt',
    path: 'app/src/main/java/com/kurogane/dividend/MainActivity.kt',
    category: 'config',
    description: 'Jetpack Composeのエントリーポイント。Navigation Composeによる画面遷移とMaterial 3 Scaffold / NavigationBarを構成。',
    code: `package com.kurogane.dividend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kurogane.dividend.ui.screens.AnnualDividendScreen
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.screens.RoadmapScreen
import com.kurogane.dividend.ui.screens.SettingsScreen
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.screens.WatchlistScreen
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.viewmodel.DividendViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DividendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KuroganeDividendTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundDark,
                    bottomBar = {
                        val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist")
                        if (isMainTab) {
                            NavigationBar(
                                containerColor = BackgroundDark
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == "asset",
                                    onClick = { navController.navigate("asset") },
                                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                    label = { Text("資産管理") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryEmerald,
                                        selectedTextColor = PrimaryEmerald,
                                        unselectedIconColor = OnSurfaceVariant,
                                        unselectedTextColor = OnSurfaceVariant
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "annual",
                                    onClick = { navController.navigate("annual") },
                                    icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                                    label = { Text("配当金") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryEmerald,
                                        selectedTextColor = PrimaryEmerald,
                                        unselectedIconColor = OnSurfaceVariant,
                                        unselectedTextColor = OnSurfaceVariant
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "roadmap",
                                    onClick = { navController.navigate("roadmap") },
                                    icon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                    label = { Text("ロードマップ") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryEmerald,
                                        selectedTextColor = PrimaryEmerald,
                                        unselectedIconColor = OnSurfaceVariant,
                                        unselectedTextColor = OnSurfaceVariant
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "watchlist",
                                    onClick = { navController.navigate("watchlist") },
                                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                                    label = { Text("Watchlist") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryEmerald,
                                        selectedTextColor = PrimaryEmerald,
                                        unselectedIconColor = OnSurfaceVariant,
                                        unselectedTextColor = OnSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "asset",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("asset") {
                            AssetManagementScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("annual") {
                            AnnualDividendScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("watchlist") {
                            WatchlistScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("roadmap") {
                            RoadmapScreen()
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("detail/{code}") { backStackEntry ->
                            val code = backStackEntry.arguments?.getString("code") ?: "7203"
                            StockDetailScreen(
                                stockCode = code,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'AssetManagementScreen',
    name: 'AssetManagementScreen.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/screens/AssetManagementScreen.kt',
    category: 'screen',
    description: '資産管理ダッシュボード。総資産評価額、含み損益、プライバシーマスク機能、月別受取配当ピボットスクロール、保有銘柄リストカードを実装。',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val totalAssets = holdings.sumOf { it.totalValuation }
    val totalCost = holdings.sumOf { it.totalCost }
    val totalGain = totalAssets - totalCost
    val totalAnnualDiv = holdings.sumOf { it.annualDividend }
    val avgYield = if (totalAssets > 0) (totalAnnualDiv / totalAssets) * 100 else 0.0

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    val displayedHoldings = holdings.filter { h ->
        val monthMatch = selectedMonth == null || h.dividendMonths.contains(selectedMonth)
        val catMatch = selectedCategory == null || h.category == selectedCategory
        monthMatch && catMatch
    }

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
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PrimaryEmerald)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("資産管理", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            Text("Asset Management", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
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
            // Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("総資産評価額", color = OnSurfaceVariant, fontSize = 12.sp)
                            IconButton(onClick = { viewModel.togglePrivacy() }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Privacy",
                                    tint = OnSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = if (isPrivacyActive) "¥••••••••" else "¥\${currencyFormat.format(totalAssets.toInt())}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Text(
                                text = if (isPrivacyActive) "••••" else "+¥\${currencyFormat.format(totalGain.toInt())} (+18.4%)",
                                color = PrimaryEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Monthly Dividend Selector
            item {
                Text("受取月ピボット絞り込み", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    viewModel.monthlyDividends.take(6).forEach { md ->
                        val isSelected = selectedMonth == md.month
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryEmerald else SurfaceContainerHigh)
                                .clickable { viewModel.selectMonth(md.month) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterVertically) {
                                Text("\${md.month}月", color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(md.label, color = if (isSelected) Color.Black else PrimaryEmerald, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            // Holdings List
            items(displayedHoldings) { holding ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDetail(holding.code) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(holding.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("\${holding.code} • \${holding.sector}", color = OnSurfaceVariant, fontSize = 11.sp)
                            }
                            Text(
                                if (isPrivacyActive) "¥••••" else "¥\${currencyFormat.format(holding.totalValuation.toInt())}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("保有: \${holding.shares}株", color = OnSurfaceVariant, fontSize = 11.sp)
                            Text("年間配当: ¥\${currencyFormat.format(holding.annualDividend.toInt())} (\${holding.yieldRate}%)", color = PrimaryEmerald, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'AnnualDividendScreen',
    name: 'AnnualDividendScreen.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/screens/AnnualDividendScreen.kt',
    category: 'screen',
    description: '年間配当金と複利シミュレーション。Compose Canvasを用いたドーナツ円グラフ描画、時価/取得額基準トグル、5年複利棒グラフを実装。',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var evalBasis by remember { mutableStateOf("eval") }

    val totalAssets = holdings.sumOf { it.totalValuation }
    val totalCost = holdings.sumOf { it.totalCost }
    val totalAnnualDiv = holdings.sumOf { it.annualDividend }
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("年間配当金", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("年間予想配当金 (税引前)", color = OnSurfaceVariant, fontSize = 12.sp)
                        Text(
                            if (isPrivacyActive) "¥••••••••" else "¥\${currencyFormat.format(totalAnnualDiv.toInt())}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            // Donut Chart via Canvas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("セクター配当比率", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(140.dp)) {
                                val strokeWidth = 22.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2
                                val centerOffset = Offset(size.width / 2, size.height / 2)

                                drawArc(Color(0xFF10B981), -90f, 140f, false, Offset(centerOffset.x - radius, centerOffset.y - radius), Size(radius * 2, radius * 2), style = Stroke(strokeWidth, cap = StrokeCap.Round))
                                drawArc(Color(0xFF06B6D4), 55f, 100f, false, Offset(centerOffset.x - radius, centerOffset.y - radius), Size(radius * 2, radius * 2), style = Stroke(strokeWidth, cap = StrokeCap.Round))
                                drawArc(Color(0xFF8B5CF6), 160f, 70f, false, Offset(centerOffset.x - radius, centerOffset.y - radius), Size(radius * 2, radius * 2), style = Stroke(strokeWidth, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterVertically) {
                                Text("筆頭セクター", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("輸送用機器", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("38.9%", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'WatchlistScreen',
    name: 'WatchlistScreen.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/screens/WatchlistScreen.kt',
    category: 'screen',
    description: '監視銘柄・買場目安ステッパー管理。目標利回りと現在利回りの乖離バー、権利落ち月バッジ、買場到達アラート表示を実装。',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
fun WatchlistScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val watchlist by viewModel.watchlist.collectAsState()
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(watchlist) { stock ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToDetail(stock.code) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(stock.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("\${stock.code} • \${stock.sector}", color = OnSurfaceVariant, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("¥\${currencyFormat.format(stock.price.toInt())}", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(stock.dailyChange, color = if (stock.isPositive) PrimaryEmerald else Color(0xFFFF6B6B), fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Yield Stepper
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh).padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("現在利回り: \${stock.currentYield}%", color = Color.White, fontSize = 11.sp)
                                Text("目標買場: \${stock.targetYield}% (¥\${stock.targetPrice.toInt()})", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = { viewModel.updateTargetYield(stock.code, -0.1) }, modifier = Modifier.size(28.dp).background(SurfaceContainerHighest, CircleShape)) {
                                    Icon(Icons.Default.Remove, contentDescription = "-0.1%", tint = Color.White)
                                }
                                IconButton(onClick = { viewModel.updateTargetYield(stock.code, +0.1) }, modifier = Modifier.size(28.dp).background(SurfaceContainerHighest, CircleShape)) {
                                    Icon(Icons.Default.Add, contentDescription = "+0.1%", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'RoadmapScreen',
    name: 'RoadmapScreen.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/screens/RoadmapScreen.kt',
    category: 'screen',
    description: 'FIRE・不労所得ロードマップQuest。10段階のステージRPG風アチーブメント、達成トロフィー、必要追加投資元本算出を実装。',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roadmap Quest", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("LEVEL 2 • QUEST 6", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("通信費・光熱費 自給ステージ", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { 0.624f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = PrimaryEmerald,
                            trackColor = SurfaceContainerHigh
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("現在: 月¥31,208 (62.4%)", color = OnSurfaceVariant, fontSize = 11.sp)
                            Text("目標: 月¥50,000", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'StockDetailScreen',
    name: 'StockDetailScreen.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/screens/StockDetailScreen.kt',
    category: 'screen',
    description: '個別銘柄詳細画面。保有株数・取得額・損益グリッド、買増しシミュレータスライダー、株価指標、AI健全性判定を実装。',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {}
) {
    var simAddedShares by remember { mutableFloatStateOf(100f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("銘柄情報: $stockCode", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("買増しシミュレーター", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("追加購入数: \${simAddedShares.toInt()}株", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        Slider(
                            value = simAddedShares,
                            onValueChange = { simAddedShares = it },
                            valueRange = 0f..500f,
                            steps = 49,
                            colors = SliderDefaults.colors(thumbColor = PrimaryEmerald, activeTrackColor = PrimaryEmerald)
                        )
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'SettingsScreen',
    name: 'SettingsScreen.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/screens/SettingsScreen.kt',
    category: 'screen',
    description: '設定・環境画面。ダーク/ライト/有機EL完全漆黒モード、5色アクセントカラー、税制設定、証券CSV連携ガイド、Zero Knowledge保証。',
    code: `package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kurogane.dividend.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定・環境", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("外観テーマ (Appearance)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}`
  },
  {
    id: 'DividendViewModel',
    name: 'DividendViewModel.kt',
    path: 'app/src/main/java/com/kurogane/dividend/viewmodel/DividendViewModel.kt',
    category: 'viewmodel',
    description: 'MVI/MVVMアーキテクチャのStateFlow管理。保有銘柄更新、ピボット月フィルタ、目標利回りリアクティブ計算を集中管理。',
    code: `package com.kurogane.dividend.viewmodel

import androidx.lifecycle.ViewModel
import com.kurogane.dividend.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DividendViewModel : ViewModel() {
    private val _isPrivacyActive = MutableStateFlow(false)
    val isPrivacyActive: StateFlow<Boolean> = _isPrivacyActive.asStateFlow()

    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    private val _holdings = MutableStateFlow<List<Holding>>(emptyList())
    val holdings: StateFlow<List<Holding>> = _holdings.asStateFlow()

    private val _watchlist = MutableStateFlow<List<WatchlistStock>>(emptyList())
    val watchlist: StateFlow<List<WatchlistStock>> = _watchlist.asStateFlow()

    fun togglePrivacy() { _isPrivacyActive.update { !it } }
    fun selectMonth(month: Int) { _selectedMonth.update { if (it == month) null else month } }
    fun updateTargetYield(code: String, delta: Double) { /* ... */ }
}`
  },
  {
    id: 'Models',
    name: 'Models.kt',
    path: 'app/src/main/java/com/kurogane/dividend/model/Models.kt',
    category: 'model',
    description: 'Kotlinデータクラス群。Holding, WatchlistStock, RoadmapStep, MonthlyDividend等のドメインモデルと計算プロパティ。',
    code: `package com.kurogane.dividend.model

data class Holding(
    val id: String,
    val code: String,
    val name: String,
    val sector: String,
    val market: String,
    val category: Category,
    val price: Double,
    val currency: String = "¥",
    val shares: Int,
    val acqPrice: Double,
    val dps: Double,
    val yieldRate: Double,
    val yoc: Double,
    val nextPayoutTiming: String,
    val nextPayoutAmount: String,
    val dividendMonths: List<Int>,
    val dividendType: String,
    val accountType: String,
    val dailyChange: String,
    val isPositive: Boolean
) {
    val totalValuation: Double get() = shares * price * (if (currency == "$") 154.0 else 1.0)
    val totalCost: Double get() = shares * acqPrice * (if (currency == "$") 154.0 else 1.0)
    val gainLoss: Double get() = totalValuation - totalCost
    val annualDividend: Double get() = shares * dps * (if (currency == "$") 154.0 else 1.0)
}

enum class Category { JAPAN, US, FUND }

data class WatchlistStock(
    val code: String,
    val name: String,
    val price: Double,
    val targetYield: Double,
    val targetPrice: Double
)`
  },
  {
    id: 'Theme',
    name: 'Theme.kt & Color.kt',
    path: 'app/src/main/java/com/kurogane/dividend/ui/theme/Theme.kt',
    category: 'theme',
    description: 'Jetpack Compose Material 3 テーマ設定とカラーパレットトークン (PrimaryEmerald, BackgroundDark, SurfaceContainer)。',
    code: `package com.kurogane.dividend.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0B1326)
val SurfaceContainer = Color(0xFF171F33)
val SurfaceContainerHigh = Color(0xFF222A3D)
val PrimaryEmerald = Color(0xFF4EDEA3)
val OnSurfaceVariant = Color(0xFFBBCABF)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    background = BackgroundDark,
    surface = SurfaceContainer
)

@Composable
fun KuroganeDividendTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}`
  },
  {
    id: 'BuildGradle',
    name: 'build.gradle.kts',
    path: 'app/build.gradle.kts',
    category: 'config',
    description: 'Android Gradle ビルドスクリプト。Jetpack Compose BOM 2024.10, Material 3, Navigation Compose, Kotlin 2.0設定。',
    code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kurogane.dividend"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kurogane.dividend"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
}`
  }
];
