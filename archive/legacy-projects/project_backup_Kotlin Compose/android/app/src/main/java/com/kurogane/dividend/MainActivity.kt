package com.kurogane.dividend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kurogane.dividend.ui.screens.AnnualDividendScreen
import com.kurogane.dividend.ui.screens.AssetManagementScreen
import com.kurogane.dividend.ui.screens.CsvImportScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KuroganeDividendTheme {
                KuroganeNativeApp()
            }
        }
    }
}

@Composable
private fun KuroganeNativeApp() {
    val navController = rememberNavController()
    val viewModel: DividendViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "asset"
    val isMainTab = currentRoute in setOf("asset", "annual", "roadmap", "watchlist")

    Scaffold(
        containerColor = BackgroundDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isMainTab) {
                NativeBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        }
                    }
                )
            }
        }
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = "asset",
            modifier = Modifier.padding(insets)
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
            composable("roadmap") { RoadmapScreen() }
            composable("watchlist") {
                WatchlistScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { code -> navController.navigate("detail/$code") }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("csv-import") {
                CsvImportScreen(onBack = { navController.popBackStack() })
            }
            composable("detail/{code}") { entry ->
                StockDetailScreen(
                    stockCode = entry.arguments?.getString("code") ?: "7203",
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun NativeBottomBar(currentRoute: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("asset", "資産管理", Icons.Default.AccountBalanceWallet),
        Triple("annual", "年間配当", Icons.Default.PieChart),
        Triple("roadmap", "ロードマップ", Icons.Default.Flag),
        Triple("watchlist", "Watchlist", Icons.Default.Bookmark)
    )
    Surface(color = BackgroundDark, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (route, label, icon) ->
                val selected = currentRoute == route
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onSelect(route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(width = 26.dp, height = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = PrimaryEmerald,
                            shape = RoundedCornerShape(3.dp)
                        ) {}
                    }
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) PrimaryEmerald else OnSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        label,
                        color = if (selected) PrimaryEmerald else OnSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
