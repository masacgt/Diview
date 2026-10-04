package com.kurogane.dividend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import com.kurogane.dividend.ui.screens.LegacyBackupImportScreen
import com.kurogane.dividend.ui.screens.StockDetailScreen
import com.kurogane.dividend.ui.screens.WatchlistScreen
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.viewmodel.DividendViewModel
import com.kurogane.dividend.data.backup.LegacyBackupKind
import com.kurogane.dividend.data.backup.LegacyBackupStore
import com.kurogane.dividend.data.backup.toHoldings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: DividendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { LegacyBackupStore(this@MainActivity).loadSaved() }
            }.getOrNull()?.firstOrNull { it.kind == LegacyBackupKind.STOCKS }?.let { payload ->
                viewModel.replaceHoldings(payload.toHoldings())
            }
        }
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
                                    label = { Text("年間配当") },
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
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("annual") {
                            AnnualDividendScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("roadmap") {
                            RoadmapScreen(viewModel = viewModel)
                        }
                        composable("watchlist") {
                            WatchlistScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenBackupImport = { navController.navigate("backup-import") }
                            )
                        }
                        composable("backup-import") {
                            LegacyBackupImportScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
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
}
