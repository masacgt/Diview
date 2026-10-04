package com.kurogane.dividend

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kurogane.dividend.ui.screens.AnnualDividendScreen
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.screens.ContactFeedbackScreen
import com.kurogane.dividend.ui.screens.RoadmapScreen
import com.kurogane.dividend.ui.screens.SettingsScreen
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.screens.TermsPrivacyScreen
import com.kurogane.dividend.ui.screens.WatchlistScreen
import com.kurogane.dividend.ui.screens.CsvImportScreen
import com.kurogane.dividend.ui.components.AddHoldingDialog
import com.kurogane.dividend.ui.components.SecuritySearchSheet
import com.kurogane.dividend.ui.components.NotificationHistorySheet
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.viewmodel.DividendViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_WATCHLIST = "open_watchlist"
    }

    private val viewModel: DividendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val startRoute = if (intent.getBooleanExtra(EXTRA_OPEN_WATCHLIST, false)) "watchlist" else "asset"
        setContent {
            val uiSettings by viewModel.uiSettings.collectAsState()
            KuroganeDividendTheme(settings = uiSettings) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                var showGlobalSearch by remember { mutableStateOf(false) }
                var showGlobalNotifications by remember { mutableStateOf(false) }
                var showGlobalAdd by remember { mutableStateOf(false) }
                val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        if (isMainTab) {
                            MainTabHeader(
                                route = currentRoute.orEmpty(),
                                onSearch = { showGlobalSearch = true },
                                onNotifications = { showGlobalNotifications = true },
                                onSettings = { navController.navigate("settings") }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (isMainTab) {
                            FloatingActionButton(
                                onClick = { showGlobalAdd = true },
                                containerColor = Color(0xFF10B981),
                                contentColor = Color(0xFF003824),
                                shape = CircleShape
                            ) { Icon(Icons.Default.Add, "保有銘柄を追加") }
                        }
                    },
                    bottomBar = {
                        if (isMainTab || currentRoute == "settings") {
                            Surface(color = BackgroundDark, shadowElevation = 4.dp) {
                                Row(Modifier.fillMaxWidth().height(65.dp), verticalAlignment = Alignment.CenterVertically) {
                                    listOf(
                                        Triple("asset", "資産管理", Icons.Default.AccountBalanceWallet),
                                        Triple("annual", if (currentRoute == "asset") "分析" else "年間配当", Icons.Default.PieChart),
                                        Triple("roadmap", "ロードマップ", Icons.Default.Flag),
                                        Triple("watchlist", if (currentRoute == "annual" || currentRoute == "settings") "月別グラフ" else "Watchlist", if (currentRoute == "annual" || currentRoute == "settings") Icons.Default.CalendarMonth else Icons.Default.Bookmark)
                                    ).forEach { (route, label, icon) ->
                                        val selected = if (currentRoute == "settings") route == "asset" else currentRoute == route
                                        Column(
                                            modifier = Modifier.weight(1f).clickable {
                                                if (!selected) navController.navigate(route) {
                                                    launchSingleTop = true
                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                    restoreState = true
                                                }
                                            },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                Modifier.size(width = 26.dp, height = 3.dp)
                                                    .background(if (selected) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(3.dp))
                                            )
                                            Icon(icon, contentDescription = label, tint = if (selected) PrimaryEmerald else OnSurfaceVariant, modifier = Modifier.size(22.dp))
                                            Text(label, color = if (selected) PrimaryEmerald else OnSurfaceVariant, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startRoute,
                        modifier = Modifier.padding(innerPadding),
                        enterTransition = {
                            slideInHorizontally(initialOffsetX = { 400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(targetOffsetX = { -400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(initialOffsetX = { -400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(targetOffsetX = { 400 }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        composable("asset") {
                            AssetManagementScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code ->
                                    navController.navigate("detail/$code")
                                },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToWatchlist = { navController.navigate("watchlist") },
                                showTopBar = false
                            )
                        }
                        composable("annual") {
                            AnnualDividendScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code ->
                                    navController.navigate("detail/$code")
                                },
                                showTopBar = false
                            )
                        }
                        composable("watchlist") {
                            WatchlistScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code ->
                                    navController.navigate("detail/$code")
                                },
                                showTopBar = false
                            )
                        }
                        composable("roadmap") {
                            RoadmapScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") },
                                onNavigateToWatchlist = { navController.navigate("watchlist") },
                                showTopBar = false
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onContact = { navController.navigate("contact-feedback") },
                                onTerms = { navController.navigate("terms-privacy") },
                                onCsvImport = { navController.navigate("csv-import") }
                            )
                        }
                        composable("contact-feedback") {
                            ContactFeedbackScreen(onBack = { navController.popBackStack() })
                        }
                        composable("terms-privacy") {
                            TermsPrivacyScreen(onBack = { navController.popBackStack() })
                        }
                        composable("csv-import") {
                            CsvImportScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable("detail/{code}") { backStackEntry ->
                            val code = backStackEntry.arguments?.getString("code").orEmpty()
                            StockDetailScreen(
                                stockCode = code,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
                if (showGlobalSearch) {
                    SecuritySearchSheet(
                        viewModel = viewModel,
                        onDismiss = { showGlobalSearch = false },
                        onSelect = { code ->
                            showGlobalSearch = false
                            navController.navigate("detail/$code")
                        }
                    )
                }
                if (showGlobalNotifications) {
                    NotificationHistorySheet(
                        viewModel = viewModel,
                        onDismiss = { showGlobalNotifications = false },
                        onSelect = { code ->
                            showGlobalNotifications = false
                            navController.navigate("detail/$code")
                        },
                        onOpenWatchlist = {
                            showGlobalNotifications = false
                            navController.navigate("watchlist")
                        }
                    )
                }
                if (showGlobalAdd) {
                    AddHoldingDialog(
                        onDismiss = { showGlobalAdd = false },
                        onSave = { holding ->
                            viewModel.addHolding(holding)
                            showGlobalAdd = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabHeader(
    route: String,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit
) {
    val (title, subtitle, icon) = when (route) {
        "annual" -> Triple("Annual Dividend", "配当管理・分析", Icons.Default.TrendingUp)
        "roadmap" -> Triple("Roadmap Quest", "不労所得 RPG • ステージ攻略", Icons.Default.Flag)
        "watchlist" -> Triple("Watchlist", "監視銘柄・目標利回り管理", Icons.Default.Bookmark)
        else -> Triple("資産管理", "ASSET MANAGEMENT", Icons.Default.TrendingUp)
    }
    Surface(color = BackgroundDark.copy(alpha = .96f), shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().height(if (route == "watchlist") 56.dp else 64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (route != "watchlist") {
                Box(
                    Modifier.size(36.dp).background(Color(0xFF171F33), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = PrimaryEmerald, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.size(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (route == "watchlist") 20.sp else 15.sp, maxLines = 1)
                Text(subtitle, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = if (route == "watchlist") 10.sp else 9.sp, maxLines = 1)
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Search, "銘柄検索", tint = OnSurfaceVariant, modifier = Modifier.size(21.dp))
            }
            Box {
                IconButton(onClick = onNotifications, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Notifications, "通知", tint = OnSurfaceVariant, modifier = Modifier.size(21.dp))
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(7.dp).background(PrimaryEmerald, CircleShape))
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Settings, "設定", tint = OnSurfaceVariant, modifier = Modifier.size(21.dp))
            }
        }
    }
}
