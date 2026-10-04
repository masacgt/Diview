package com.kurogane.dividend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            if (currentRoute == "roadmap") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .background(BackgroundDark)
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabs = listOf(
                                        Triple("asset", "資産管理", Icons.Default.AccountBalanceWallet),
                                        Triple("annual", "年間配当", Icons.Default.PieChart),
                                        Triple("roadmap", "ロードマップ", Icons.Default.Flag),
                                        Triple("watchlist", "Watchlist", Icons.Default.Bookmark)
                                    )
                                    tabs.forEach { (route, label, icon) ->
                                        val selected = currentRoute == route
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable { navController.navigate(route) },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 3.dp)
                                                    .width(if (selected) 24.dp else 0.dp)
                                                    .height(if (selected) 4.dp else 0.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(PrimaryEmerald)
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            Icon(icon, null, tint = if (selected) PrimaryEmerald else OnSurfaceVariant, modifier = Modifier.size(22.dp))
                                            Text(label, color = if (selected) PrimaryEmerald else OnSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                }
                            } else NavigationBar(
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
                                onNavigateToDetail = { code ->
                                    navController.navigate("detail/$code")
                                }
                            )
                        }
                        composable("annual") {
                            AnnualDividendScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code ->
                                    navController.navigate("detail/$code")
                                }
                            )
                        }
                        composable("watchlist") {
                            WatchlistScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { code ->
                                    navController.navigate("detail/$code")
                                }
                            )
                        }
                        composable("roadmap") {
                            RoadmapScreen(onOpenSettings = { navController.navigate("settings") })
                        }
                        composable("settings") {
                            SettingsScreen(
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
