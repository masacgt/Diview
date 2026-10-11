package com.kurogane.dividend

import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import android.content.Intent
import com.kurogane.dividend.notifications.DiviewNotifications
import com.kurogane.dividend.notifications.NotificationStore
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.kurogane.dividend.ui.components.NotificationHistorySheet
import com.kurogane.dividend.viewmodel.DividendViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val viewModel: DividendViewModel by viewModels()
    private val notificationDestination = MutableStateFlow<String?>(null)
    private val appLocked = MutableStateFlow(true)
    private lateinit var authentication: com.kurogane.dividend.security.AppAuthentication
    private val authError = MutableStateFlow<String?>(null)
    private var autoPromptRequested = false
    private var navigationController: androidx.navigation.NavHostController? = null
    private var savedNavigation: Bundle? = null

    override fun onStop() {
        if (viewModel.biometricEnabled.value && !isChangingConfigurations) {
            if (!appLocked.value) savedNavigation = navigationController?.saveState()
            if (!authentication.inProgress) autoPromptRequested = false
            appLocked.value = true
        }
        super.onStop()
    }

    private fun completeUnlock() {
        savedNavigation?.let { navigationController?.restoreState(it) }
        savedNavigation = null
        appLocked.value = false
        authError.value = null
    }

    fun authenticatePreference(enabled: Boolean) {
        autoPromptRequested = true
        authentication.authenticate(onSuccess = {
            if (!viewModel.setBiometricEnabled(enabled)) android.widget.Toast.makeText(this, "認証設定を保存できませんでした", android.widget.Toast.LENGTH_LONG).show()
            completeUnlock()
        }, onError = {
            authError.value = it
            android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show()
        })
    }

    private fun unlock() {
        autoPromptRequested = true
        authentication.authenticate(
        onSuccess = { completeUnlock() },
        onError = { authError.value = it }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = DiviewNotifications.destination(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authentication = com.kurogane.dividend.security.AppAuthentication(this)
        appLocked.value = viewModel.biometricEnabled.value
        DiviewNotifications(this).createChannels()
        if (savedInstanceState == null) notificationDestination.value = DiviewNotifications.destination(intent)
        setContent {
            val resources = androidx.compose.ui.platform.LocalContext.current.resources
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isOledBlack by viewModel.isOledBlack.collectAsStateWithLifecycle()
            val selectedAccent by viewModel.selectedAccent.collectAsStateWithLifecycle()
            KuroganeDividendTheme(
                themeMode = themeMode,
                isOledBlack = isOledBlack,
                selectedAccent = selectedAccent
            ) {
                val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
                val locked by appLocked.collectAsStateWithLifecycle()
                val error by authError.collectAsStateWithLifecycle()
                SideEffect {
                    if (biometricEnabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                val navController = rememberNavController()
                SideEffect { navigationController = navController }
                if (biometricEnabled && locked) {
                    LaunchedEffect(Unit) { if (!autoPromptRequested) unlock() }
                    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(24.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, null, tint = PrimaryEmerald)
                        Text(stringResource(R.string.main_activity_text), color = OnSurface)
                        error?.let { Text(it, color = OnSurfaceVariant) }
                        Button(onClick = { unlock() }) { Text(stringResource(R.string.main_activity_text_2)) }
                        TextButton(onClick = { startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }) { Text(stringResource(R.string.main_activity_text_3)) }
                    }
                    return@KuroganeDividendTheme
                }

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
                val appContext = LocalContext.current
                var unreadNotificationCount by remember(appContext) {
                    mutableIntStateOf(NotificationStore(appContext).unreadNotificationCount())
                }
                var showAddHoldingSheet by remember { mutableStateOf(false) }
                var prefilledAddHoldingCode by remember { mutableStateOf("") }
                val scrollToTopRequests = remember { mutableStateMapOf<String, Int>() }
                val saveError by viewModel.saveError.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(saveError) {
                    saveError?.let { snackbarHostState.showSnackbar(it); viewModel.clearSaveError() }
                }

                val dynamicBg = BackgroundDark

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = dynamicBg,
                    topBar = {
                        val isMainTab = currentRoute in listOf("asset", "annual", "roadmap", "watchlist")
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .background(dynamicBg)
                                .border(BorderStroke(0.5.dp, BorderColor))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMainTab) {
                                // Left side main tab title layout
                                val (title, subtitle, icon) = when (currentRoute) {
                                    "asset" -> Triple(stringResource(R.string.asset_management), stringResource(R.string.main_activity_asset_management), Icons.Default.AccountBalanceWallet)
                                    "annual" -> Triple(stringResource(R.string.analysis), stringResource(R.string.main_activity_annual_dividend), Icons.Default.PieChart)
                                    "roadmap" -> Triple(stringResource(R.string.roadmap_roadmap_quest), stringResource(R.string.main_activity_label), Icons.Default.Flag)
                                    "watchlist" -> Triple(stringResource(R.string.main_activity_watchlist), stringResource(R.string.main_activity_watchlist_2), Icons.Default.Bookmark)
                                    else -> Triple(stringResource(R.string.asset_management), stringResource(R.string.main_activity_asset_management), Icons.Default.AccountBalanceWallet)
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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnSurface, modifier = Modifier.size(18.dp))
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
                                        Text(stringResource(R.string.settings_title), fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold, color = OnSurface)
                                        Text(stringResource(R.string.main_activity_settings), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurfaceVariant, letterSpacing = 0.5.sp)
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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = OnSurface)
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
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.main_activity_a11y), tint = OnSurfaceVariant)
                                }

                                // Notification Button with pulse badge
                                Box(contentAlignment = Alignment.TopEnd) {
                                    IconButton(onClick = {
                                        unreadNotificationCount = NotificationStore(appContext).unreadNotificationCount()
                                        showNotificationsDialog = true
                                    }) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = if (unreadNotificationCount > 0) {
                                                stringResource(R.string.notification_unread_count, unreadNotificationCount)
                                            } else stringResource(R.string.main_activity_a11y_2),
                                            tint = OnSurfaceVariant
                                        )
                                    }
                                    if (unreadNotificationCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp, end = 8.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(PrimaryEmerald)
                                        )
                                    }
                                }

                                // Settings Button
                                IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.main_activity_a11y_3),
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
                                    .background(BackgroundDark)
                                    .navigationBarsPadding()
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
                                            .offset { androidx.compose.ui.unit.IntOffset(indicatorOffset.roundToPx(), 0) }
                                            .width(32.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(PrimaryEmerald)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabs = listOf(
                                        Triple("asset", stringResource(R.string.asset_management), Icons.Default.AccountBalanceWallet),
                                        Triple("annual", stringResource(R.string.analysis), Icons.Default.PieChart),
                                        Triple("roadmap", stringResource(R.string.main_activity_label_2), Icons.Default.Flag),
                                        Triple("watchlist", stringResource(R.string.main_activity_watchlist), Icons.Default.Bookmark)
                                    )
                                    tabs.forEach { (route, label, icon) ->
                                        val selected = currentRoute == route
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .testTag("main_tab_$route")
                                                .selectable(selected, role = Role.Tab, onClick = {
                                                    if (selected && route in listOf("annual", "roadmap", "watchlist")) {
                                                        scrollToTopRequests[route] = (scrollToTopRequests[route] ?: 0) + 1
                                                    } else if (!selected) {
                                                        // Save the tab itself, without its settings/detail screens.
                                                        while (tabs.none { it.first == navController.currentDestination?.route }) {
                                                            if (!navController.popBackStack()) break
                                                        }
                                                        navController.navigate(route) {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                        // Older saved stacks may still include a secondary screen.
                                                        navController.popBackStack(route, inclusive = false)
                                                    }
                                                })
                                                .padding(vertical = 8.dp),
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
                                    },
                                    onOpenHoldingRegistration = { code ->
                                        prefilledAddHoldingCode = code
                                        showAddHoldingSheet = true
                                    }
                                )
                            }
                            composable("annual") {
                                AnnualDividendScreen(
                                    viewModel = viewModel,
                                    scrollToTopRequest = scrollToTopRequests["annual"] ?: 0,
                                    onNavigateToDetail = { code -> navController.navigate("detail/${android.net.Uri.encode(code)}") { launchSingleTop = true } }
                                )
                            }
                            composable("roadmap") {
                                RoadmapScreen(viewModel = viewModel, scrollToTopRequest = scrollToTopRequests["roadmap"] ?: 0)
                            }
                            composable("watchlist") {
                                WatchlistScreen(
                                    viewModel = viewModel,
                                    scrollToTopRequest = scrollToTopRequests["watchlist"] ?: 0,
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
                                    onBack = { navController.popBackStack() },
                                    onOpenAddHolding = { selectedCode ->
                                        prefilledAddHoldingCode = selectedCode
                                        showAddHoldingSheet = true
                                    }
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
                        val exchangeRate by viewModel.exchangeRate.collectAsStateWithLifecycle()
                        val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
                        AddHoldingSheet(
                            usdJpyRate = exchangeRate?.usdJpy,
                            usWithholdingEnabled = usTaxDeduction,
                            existingHoldings = viewModel.holdings.value,
                            visible = showAddHoldingSheet,
                            prefilledCode = prefilledAddHoldingCode,
                            onDismiss = { showAddHoldingSheet = false },
                            onSearchSecurities = { query -> viewModel.searchCloudflareSecurities(query) },
                            onResolveSecurity = { code -> viewModel.resolveCloudflareSecurity(code) },
                            onRegisterCollective = { code, allocations, replaceExisting ->
                                val registered = viewModel.registerCloudflareAllocations(code, allocations, replaceExisting)
                                if (registered) showAddHoldingSheet = false
                                registered
                            }
                        )

                        // Notification history modal
                        if (showNotificationsDialog) {
                            NotificationHistorySheet(
                                onDismiss = { showNotificationsDialog = false },
                                onOpenSettings = {
                                    showNotificationsDialog = false
                                    navController.navigate("settings") { launchSingleTop = true }
                                },
                                onUnreadCountChanged = { unreadNotificationCount = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
