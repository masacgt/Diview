package com.kurogane.dividend

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import android.content.Intent
import com.kurogane.dividend.notifications.DiviewNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
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
import com.kurogane.dividend.ui.screens.ContactFeedbackScreen
import com.kurogane.dividend.ui.screens.TermsPrivacyScreen
import com.kurogane.dividend.ui.screens.CsvImportScreen
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.KuroganeDividendTheme
import com.kurogane.dividend.ui.theme.BorderColor
import com.kurogane.dividend.ui.theme.OnSurface
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.OnPrimary
import com.kurogane.dividend.ui.theme.PrimaryEmeraldContainer
import com.kurogane.dividend.ui.theme.SurfaceContainer
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.components.SearchSheet
import com.kurogane.dividend.ui.components.AddHoldingSheet
import com.kurogane.dividend.viewmodel.DividendViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DividendViewModel by viewModels()
    private val notificationDestination = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = DiviewNotifications.destination(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiviewNotifications(this).createChannels()
        if (savedInstanceState == null) notificationDestination.value = DiviewNotifications.destination(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isOledBlack by viewModel.isOledBlack.collectAsStateWithLifecycle()
            val selectedAccent by viewModel.selectedAccent.collectAsStateWithLifecycle()
            KuroganeDividendTheme(
                themeMode = themeMode,
                isOledBlack = isOledBlack,
                selectedAccent = selectedAccent
            ) {
                val navController = rememberNavController()
                val destination by notificationDestination.collectAsStateWithLifecycle()
                LaunchedEffect(destination) {
                    destination?.let { route ->
                        navController.navigate(route) { launchSingleTop = true }
                        notificationDestination.value = null
                    }
                }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                SideEffect {
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }

                var showSearchSheet by remember { mutableStateOf(false) }
                var showNotificationsDialog by remember { mutableStateOf(false) }
                var showAddHoldingSheet by remember { mutableStateOf(false) }
                var prefilledAddHoldingCode by remember { mutableStateOf("") }
                val saveError by viewModel.saveError.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(saveError) {
                    saveError?.let { snackbarHostState.showSnackbar(it); viewModel.clearSaveError() }
                }

                val dynamicBg = BackgroundDark

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = dynamicBg,
                    topBar = {
                        val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist")
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(dynamicBg)
                                .border(BorderStroke(0.5.dp, BorderColor))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMainTab) {
                                // Left side main tab title layout
                                val (title, subtitle, icon) = when (currentRoute) {
                                    "asset" -> Triple("資産管理", "ASSET MANAGEMENT", Icons.Default.AccountBalanceWallet)
                                    "annual" -> Triple("年間配当", "ANNUAL DIVIDEND", Icons.Default.PieChart)
                                    "roadmap" -> Triple("Roadmap Quest", "不労所得 RPG", Icons.Default.Flag)
                                    "watchlist" -> Triple("Watchlist", "WATCHLIST", Icons.Default.Bookmark)
                                    else -> Triple("資産管理", "ASSET MANAGEMENT", Icons.Default.AccountBalanceWallet)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainer)
                                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            fontSize = AppTextSize.section,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OnSurface
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            } else if (currentRoute == "settings") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る", tint = OnSurface, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceContainer)
                                            .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("設定・データ管理", fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold, color = OnSurface)
                                        Text("SETTINGS", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, letterSpacing = 0.5.sp)
                                    }
                                }
                            } else {
                                // Left side back button title layout
                                val title = when {
                                    currentRoute == "settings" -> "設定・データ管理"
                                    currentRoute?.startsWith("detail") == true -> "銘柄情報"
                                    else -> "詳細画面"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = title,
                                        fontSize = AppTextSize.section,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface
                                    )
                                }
                            }

                            // Right actions (displayed on all screens!)
                            if (currentRoute != "settings") Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Search Button
                                IconButton(onClick = { showSearchSheet = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = OnSurfaceVariant)
                                }

                                // Notification Button with pulse badge
                                Box(contentAlignment = Alignment.TopEnd) {
                                    IconButton(onClick = { showNotificationsDialog = true }) {
                                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = OnSurfaceVariant)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp, end = 8.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                }

                                // Settings Button
                                IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (currentRoute == "settings") PrimaryEmerald else OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist") || currentRoute == "settings"
                        if (isMainTab) {
                            val activeIndex = when (currentRoute) {
                                "asset" -> 0
                                "annual" -> 1
                                "roadmap" -> 2
                                "watchlist" -> 3
                                else -> 0
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(BackgroundDark)
                                    .border(BorderStroke(0.5.dp, BorderColor))
                            ) {
                                val tabWidth = maxWidth / 4
                                val indicatorOffset by animateDpAsState(
                                    targetValue = tabWidth * activeIndex + (tabWidth - 32.dp) / 2,
                                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f),
                                    label = "activeIndicator"
                                )

                                if (currentRoute != "settings") {
                                    // Sliding active indicator pill at the top of bottom bar
                                    Box(
                                        modifier = Modifier
                                            .offset(x = indicatorOffset, y = 0.dp)
                                            .width(32.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(PrimaryEmerald)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxSize(),
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
                                                .clickable {
                                                    if (!selected) navController.navigate(route)
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (selected) PrimaryEmerald else OnSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = label,
                                                fontSize = AppTextSize.dense,
                                                color = if (selected) PrimaryEmerald else OnSurfaceVariant,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = "asset",
                            modifier = Modifier.fillMaxSize(),
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
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } },
                                    onOpenAddHolding = { code ->
                                        prefilledAddHoldingCode = code
                                        showAddHoldingSheet = true
                                    }
                                )
                            }
                            composable("annual") {
                                AnnualDividendScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } }
                                )
                            }
                            composable("roadmap") {
                                RoadmapScreen(viewModel = viewModel)
                            }
                            composable("watchlist") {
                                WatchlistScreen(
                                    viewModel = viewModel,
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } },
                                    onOpenAddHolding = { code ->
                                        prefilledAddHoldingCode = code
                                        showAddHoldingSheet = true
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToFeedback = { navController.navigate("contact_feedback") { launchSingleTop = true } },
                                    onNavigateToTerms = { navController.navigate("terms_privacy") { launchSingleTop = true } },
                                    onNavigateToCsv = { navController.navigate("csv_import") { launchSingleTop = true } }
                                )
                            }
                            composable("csv_import") {
                                CsvImportScreen(
                                    onBack = { navController.popBackStack() },
                                    onImportCsv = { rows, fields ->
                                        viewModel.importCsvRows(rows, fields)
                                        navController.popBackStack()
                                    },
                                    onImportDvm = { backups ->
                                        viewModel.importDvmBackups(backups)
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("contact_feedback") {
                                ContactFeedbackScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = viewModel
                                )
                            }
                            composable("terms_privacy") {
                                TermsPrivacyScreen(
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

                        // Search Sheet Modal Overlay
                        SearchSheet(
                            visible = showSearchSheet,
                            onDismiss = { showSearchSheet = false },
                            onSearch = { query -> viewModel.searchCloudflareSecurities(query) },
                            onSelectStock = { code ->
                                showSearchSheet = false
                                navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true }
                            }
                        )

                        // Add Asset / Joint Portfolio Matrix Sheet Overlay
                        AddHoldingSheet(
                            visible = showAddHoldingSheet,
                            prefilledCode = prefilledAddHoldingCode,
                            onDismiss = { showAddHoldingSheet = false },
                            onResolveSecurity = { code -> viewModel.resolveCloudflareSecurity(code) },
                            onRegisterCollective = { code, allocations ->
                                val registered = viewModel.registerCloudflareAllocations(code, allocations)
                                if (registered) showAddHoldingSheet = false
                                registered
                            }
                        )

                        // Notification Dialog
                        if (showNotificationsDialog) {
                            val watched by viewModel.watchlist.collectAsStateWithLifecycle()
                            val owned by viewModel.holdings.collectAsStateWithLifecycle()
                            val taxMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
                            val ready = watched.filter { it.alertEnabled && it.isReady }
                            val monthlyIncome = owned.sumOf { it.getEffectiveAnnualDividend(taxMode) } / 12.0
                            AlertDialog(
                                onDismissRequest = { showNotificationsDialog = false },
                                confirmButton = {
                                    Button(
                                        onClick = { showNotificationsDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmeraldContainer)
                                    ) {
                                        Text("確認", color = OnPrimary, fontWeight = FontWeight.Bold)
                                    }
                                },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryEmerald)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("お知らせ・通知", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("🔔 利回りシグナル検知", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                        Text(if (ready.isEmpty()) "通知を有効にした銘柄で、目標利回りに到達したものはありません。" else ready.joinToString("\n") { "${it.name}：現在利回り ${String.format(java.util.Locale.JAPAN, "%.2f", it.currentYield)}% / 目標 ${String.format(java.util.Locale.JAPAN, "%.2f", it.targetYield)}%" }, color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                                        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                                        Text("🎉 ロードマップ進捗アップデート", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                        Text("現在の想定配当は月換算で ¥${java.text.NumberFormat.getNumberInstance(java.util.Locale.JAPAN).format(monthlyIncome.toLong())} です。ロードマップで目標との差額を確認できます。", color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                                    }
                                },
                                containerColor = SurfaceContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
