package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Local High-Fidelity Color Theme matching React Web App CSS
val SpaceNavy: Color @Composable get() = BackgroundDark
val CardDark: Color @Composable get() = SurfaceContainerLow
val CardDarkLighter: Color @Composable get() = SurfaceContainer
val NeonGreen: Color @Composable get() = PrimaryEmeraldContainer
val NeonGreenLight: Color @Composable get() = PrimaryEmerald
val SoftGray: Color @Composable get() = OnSurfaceVariant
val BorderBlue: Color @Composable get() = BorderColor

private data class OdometerGlyph(val character: Char, val layout: TextLayoutResult)

private const val ODOMETER_STEP_COUNT = 8
private const val ODOMETER_STEP_INTERVAL_MS = 38f
private const val ODOMETER_TRANSITION_DURATION_MS = 49f
private const val ODOMETER_ANIMATION_DURATION_MS =
    ODOMETER_STEP_COUNT * ODOMETER_STEP_INTERVAL_MS + ODOMETER_TRANSITION_DURATION_MS

@Composable
private fun OdometerAmount(
    targetValue: Long,
    animate: Boolean = true,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val targetNumber = numberFormat.format(targetValue)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = AppTextSize.hero,
        fontWeight = FontWeight.ExtraBold,
        color = OnSurface,
        fontFamily = FontFamily.SansSerif,
        fontFeatureSettings = "\"tnum\" 1, \"kern\" 1, \"palt\" 1",
        letterSpacing = (-0.5).sp
    )
    val glyphs = remember(targetNumber, textStyle, textMeasurer) {
        targetNumber.map { character ->
            OdometerGlyph(
                character = character,
                layout = textMeasurer.measure(
                    text = AnnotatedString(character.toString()),
                    style = textStyle,
                    softWrap = false,
                    maxLines = 1
                )
            )
        }
    }
    val digitLayouts = remember(textStyle, textMeasurer) {
        (0..9).map { digit ->
            textMeasurer.measure(
                text = AnnotatedString(digit.toString()),
                style = textStyle,
                softWrap = false,
                maxLines = 1
            )
        }
    }
    val numberWidthPx = glyphs.sumOf { it.layout.size.width }
    val numberHeightPx = glyphs.maxOfOrNull { it.layout.size.height } ?: 0
    val density = LocalDensity.current
    val numberWidth = with(density) { numberWidthPx.toDp() }
    val numberHeight = with(density) { numberHeightPx.toDp() }
    val elapsedMillis = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animate, targetValue) {
        if (!animate) {
            elapsedMillis.floatValue = ODOMETER_ANIMATION_DURATION_MS
            return@LaunchedEffect
        }

        elapsedMillis.floatValue = 0f
        val animationStartNanos = withFrameNanos { it }
        do {
            val frameNanos = withFrameNanos { it }
            val elapsed = (frameNanos - animationStartNanos) / 1_000_000f
            elapsedMillis.floatValue = elapsed.coerceAtMost(ODOMETER_ANIMATION_DURATION_MS)
        } while (elapsed < ODOMETER_ANIMATION_DURATION_MS)
    }

    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "¥${numberFormat.format(targetValue)}"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "¥", style = textStyle)
        Canvas(modifier = Modifier.size(width = numberWidth, height = numberHeight)) {
            val elapsed = elapsedMillis.floatValue
            var x = 0f

            glyphs.forEach { glyph ->
                if (!animate || elapsed >= ODOMETER_ANIMATION_DURATION_MS || !glyph.character.isDigit()) {
                    drawText(glyph.layout, topLeft = Offset(x, 0f))
                } else {
                    val finalDigit = glyph.character.digitToInt()
                    for (step in 0..ODOMETER_STEP_COUNT) {
                        val enterStart = step * ODOMETER_STEP_INTERVAL_MS
                        val exitStart = (step + 1) * ODOMETER_STEP_INTERVAL_MS

                        if (step > 0 && elapsed < enterStart) continue
                        if (step < ODOMETER_STEP_COUNT && elapsed >= exitStart + ODOMETER_TRANSITION_DURATION_MS) continue

                        val enterProgress = if (step == 0) {
                            1f
                        } else {
                            ((elapsed - enterStart) / ODOMETER_TRANSITION_DURATION_MS).coerceIn(0f, 1f)
                        }
                        val exitProgress = if (step == ODOMETER_STEP_COUNT) {
                            0f
                        } else {
                            ((elapsed - exitStart) / ODOMETER_TRANSITION_DURATION_MS).coerceIn(0f, 1f)
                        }
                        val enterAlpha = if (step == 0) 1f else FastOutSlowInEasing.transform(enterProgress)
                        val exitAlpha = if (step == ODOMETER_STEP_COUNT) 1f else 1f - FastOutSlowInEasing.transform(exitProgress)
                        val enterOffset = if (step == 0) 0f else size.height * (1f - LinearOutSlowInEasing.transform(enterProgress))
                        val exitOffset = if (step == ODOMETER_STEP_COUNT) 0f else -size.height * LinearOutSlowInEasing.transform(exitProgress)
                        val digit = (finalDigit + 2 + step) % 10
                        drawText(
                            textLayoutResult = digitLayouts[digit],
                            topLeft = Offset(x, enterOffset + exitOffset),
                            alpha = enterAlpha * exitAlpha
                        )
                    }
                }

                x += glyph.layout.size.width
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssetManagementScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {},
    onOpenHoldingRegistration: (String) -> Unit = onOpenAddHolding,
    pendingRestoreHolding: Holding? = null,
    onPendingRestoreHoldingConsumed: () -> Unit = {},
    sharedSnackbarHostState: SnackbarHostState? = null
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    var holdingsRestored by remember(viewModel) { mutableStateOf(false) }
    var hasAnimatedTotalAssets by remember(viewModel) { mutableStateOf(false) }
    val animateTotalAssets = holdingsRestored && !hasAnimatedTotalAssets
    LaunchedEffect(viewModel) {
        viewModel.awaitRestoration()
        holdingsRestored = true
    }
    LaunchedEffect(holdingsRestored) {
        if (holdingsRestored) {
            kotlinx.coroutines.delay(400)
            hasAnimatedTotalAssets = true
        }
    }
    val isPrivacyActive by viewModel.isPrivacyActive.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    // Sorting state
    var sortCriteria by rememberSaveable { mutableStateOf("val") } // "val", "gain", "div", "div_amt", "code"
    var sortDirection by rememberSaveable { mutableStateOf("desc") } // "desc", "asc"
    var showSortModal by remember { mutableStateOf(false) }
    var draftSortCriteria by remember { mutableStateOf("val") }
    var draftSortDirection by remember { mutableStateOf("desc") }

    // Dialog / Modal State
    var showAddAssetModal by remember { mutableStateOf(false) }
    var stockCode by remember { mutableStateOf("") }
    var shareCount by remember { mutableStateOf("") }
    var acqPrice by remember { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var quickEditHolding by remember { mutableStateOf<Holding?>(null) }
    var revealedHoldingId by remember { mutableStateOf<String?>(null) }
    var exitingHoldings by remember { mutableStateOf<List<Holding>>(emptyList()) }
    var exitingNextHoldingIds by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var restoringHoldingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingRestoreScrollHoldingId by remember { mutableStateOf<String?>(null) }
    val localSnackbarHostState = remember { SnackbarHostState() }
    val snackbarHostState = sharedSnackbarHostState ?: localSnackbarHostState
    val screenScope = rememberCoroutineScope()
    val deleteActionWidth = 112.dp
    val deleteActionVisibleWidth = 80.dp
    val deleteActionVisibleWidthPx = with(LocalDensity.current) { deleteActionVisibleWidth.toPx() }

    val javaCurrencyFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val listItemsFlow = remember(viewModel, sortCriteria) {
        viewModel.assetManagementListItems(sortCriteria)
    }
    val listItems by listItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val categoryCounts = remember(listItems) { listItems.groupingBy { it.category }.eachCount() }
    val hasMissingExchangeRate = remember(listItems) { listItems.any { it.missingExchangeRate } }

    val filteredHoldings = remember(listItems, selectedMonth, selectedCategory, searchQuery) {
        listItems.filter { holding ->
            val monthMatch = selectedMonth == null || holding.dividendMonths.contains(selectedMonth)
            val catMatch = selectedCategory == null || holding.category == selectedCategory
            val queryMatch = searchQuery.isEmpty() ||
                    holding.code.contains(searchQuery, ignoreCase = true) ||
                    holding.displayName.contains(searchQuery, ignoreCase = true) ||
                    holding.sector.contains(searchQuery, ignoreCase = true)
            monthMatch && catMatch && queryMatch
        }
    }

    val displayedHoldings = remember(filteredHoldings, sortCriteria, sortDirection) {
        filteredHoldings.sortedWith { a, b ->
            val comp = if (sortCriteria == "code") a.code.compareTo(b.code) else a.sortValue.compareTo(b.sortValue)
            if (sortDirection == "desc") -comp else comp
        }
    }
    val activeHoldings = remember(displayedHoldings, exitingHoldings) {
        displayedHoldings.filterNot { candidate -> exitingHoldings.any { it.id == candidate.id } }
    }
    val animatedHoldingIds = remember(activeHoldings, exitingHoldings, exitingNextHoldingIds) {
        buildList {
            val insertions = exitingHoldings.associate { exiting ->
                exiting.id to exitingNextHoldingIds[exiting.id]
            }
            var insertedNextIds = emptySet<String>()
            activeHoldings.forEach { candidate ->
                val exiting = exitingHoldings.firstOrNull { insertions[it.id] == candidate.id }
                if (exiting != null) {
                    add(exiting.id)
                    add(candidate.id)
                    insertedNextIds = insertedNextIds + candidate.id
                } else if (candidate.id !in insertedNextIds) {
                    add(candidate.id)
                }
            }
            addAll(exitingHoldings.filter { exiting -> activeHoldings.none { candidate -> insertions[exiting.id] == candidate.id } }.map { it.id })
        }
    }

    fun restoreHoldingWithAnimation(holding: Holding): Boolean {
        restoringHoldingIds = restoringHoldingIds + holding.id
        if (viewModel.restoreHolding(holding)) {
            pendingRestoreScrollHoldingId = holding.id
            screenScope.launch {
                kotlinx.coroutines.delay(600)
                restoringHoldingIds = restoringHoldingIds - holding.id
            }
            return true
        }
        restoringHoldingIds = restoringHoldingIds - holding.id
        return false
    }

    suspend fun showDeletionSnackbar(holding: Holding) {
        val result = snackbarHostState.showSnackbar(
            message = "${viewModel.japaneseDisplayName(holding.code, holding.name)}を保有銘柄から削除しました",
            actionLabel = "元に戻す",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) restoreHoldingWithAnimation(holding)
    }

    fun deleteHolding(holding: Holding) {
        revealedHoldingId = null
        val indexBeforeDeletion = displayedHoldings.indexOfFirst { it.id == holding.id }
        val nextHoldingId = displayedHoldings.getOrNull(indexBeforeDeletion + 1)?.id
        if (!viewModel.removeHolding(holding.id)) return
        exitingNextHoldingIds = exitingNextHoldingIds + (holding.id to nextHoldingId)
        exitingHoldings = exitingHoldings + holding
        screenScope.launch {
            kotlinx.coroutines.delay(420)
            exitingHoldings = exitingHoldings.filterNot { it.id == holding.id }
            exitingNextHoldingIds = exitingNextHoldingIds - holding.id
        }
        screenScope.launch {
            kotlinx.coroutines.delay(900)
            showDeletionSnackbar(holding)
        }
    }

    LaunchedEffect(pendingRestoreHolding) {
        val holding = pendingRestoreHolding ?: return@LaunchedEffect
        restoreHoldingWithAnimation(holding)
        onPendingRestoreHoldingConsumed()
    }

    fun getSortLabel(): String {
        val dir = if (sortDirection == "desc") resources.getString(R.string.asset_management_label_24) else resources.getString(R.string.asset_management_label_25)
        return when (sortCriteria) {
            "val" -> resources.getString(R.string.asset_management_label_26, (dir).toString())
            "gain" -> resources.getString(R.string.asset_management_label_27, (dir).toString())
            "div" -> resources.getString(R.string.asset_management_label_28, (dir).toString())
            "div_amt" -> resources.getString(R.string.asset_management_label_29, (dir).toString())
            "code" -> resources.getString(R.string.asset_management_label_30, (dir).toString())
            else -> resources.getString(R.string.asset_management_label_26, (dir).toString())
        }
    }

    Scaffold(
        snackbarHost = {
            if (sharedSnackbarHostState == null) SnackbarHost(snackbarHostState) { data ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceContainerHigh,
                    contentColor = OnSurface,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = data.visuals.message,
                            modifier = Modifier.weight(1f),
                            color = OnSurface,
                            fontSize = 14.sp
                        )
                        data.visuals.actionLabel?.let { actionLabel ->
                            TextButton(onClick = data::performAction) {
                                Text(actionLabel, color = PrimaryEmerald)
                            }
                        }
                        if (data.visuals.withDismissAction) {
                            IconButton(onClick = data::dismiss, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "通知を閉じる",
                                    modifier = Modifier.size(18.dp),
                                    tint = OnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp, end = 4.dp)
                    .size(56.dp)
                    .shadow(16.dp, CircleShape, spotColor = NeonGreen, ambientColor = NeonGreen)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(NeonGreenLight, NeonGreen)))
                    .clickable { onOpenAddHolding("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.asset_management_add_asset), tint = OnPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        },
        containerColor = SpaceNavy
    ) { innerPadding ->
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = viewModel.assetScrollIndex,
            initialFirstVisibleItemScrollOffset = viewModel.assetScrollOffset
        )
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    viewModel.assetScrollIndex = index
                    viewModel.assetScrollOffset = offset
                }
        }
        LaunchedEffect(pendingRestoreScrollHoldingId, animatedHoldingIds, hasMissingExchangeRate) {
            val holdingId = pendingRestoreScrollHoldingId ?: return@LaunchedEffect
            val restoredCardIndex = animatedHoldingIds.indexOf(holdingId)
            if (restoredCardIndex < 0) return@LaunchedEffect

            val fixedItemCountBeforeHoldings = 5 + if (hasMissingExchangeRate) 1 else 0
            listState.animateScrollToItem(fixedItemCountBeforeHoldings + restoredCardIndex)
            pendingRestoreScrollHoldingId = null
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("asset_management_list"),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (hasMissingExchangeRate) {
                item(key = "exchange_rate_warning", contentType = "warning") {
                    Text(stringResource(R.string.asset_management_text), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                }
            }
            // ================= TOTAL ASSET HERO CARD =================
            item(key = "asset_summary", contentType = "summary") {
                val summary by viewModel.assetManagementSummary.collectAsStateWithLifecycle()
                val totalAssetsVal = summary.totalAssets
                val totalCostVal = summary.totalCost
                val totalGainVal = summary.totalGain
                val gainPctVal = summary.gainPercent
                val totalAnnualDivVal = summary.annualDividend
                val netAnnualDivVal = summary.netAnnualDividend
                val avgYield = summary.averageYield
                val badgeText = when {
                    avgYield >= 4.0 -> resources.getString(R.string.asset_management_label_5)
                    avgYield >= 3.0 -> resources.getString(R.string.asset_management_label_20)
                    avgYield >= 2.0 -> resources.getString(R.string.asset_management_label_21)
                    avgYield >= 0.5 -> resources.getString(R.string.asset_management_label_22)
                    else -> resources.getString(R.string.asset_management_label_23)
                }
                val badgeIcon = when {
                    avgYield >= 4.0 -> Icons.Default.Bolt
                    avgYield >= 3.0 -> Icons.Default.CheckCircle
                    avgYield >= 2.0 -> Icons.Default.Star
                    else -> Icons.Default.Info
                }
                val badgeColor = when {
                    avgYield >= 4.0 -> SecondaryIndigo
                    avgYield >= 3.0 -> NeonGreen
                    avgYield >= 2.0 -> if (BackgroundDark == LightAppPalette.background) Color(0xFF1D4ED8) else Color(0xFF60A5FA)
                    avgYield >= 0.5 -> AccentAmber
                    else -> SoftGray
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.asset_management_text_2), fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = SoftGray)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    if (isPrivacyActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(R.string.asset_management_privacy),
                                    tint = SoftGray,
                                    modifier = Modifier.size(16.dp).clickable { viewModel.togglePrivacy() }
                                )
                            }
                            // Profit Pill
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0x1F10B981))
                                    .border(0.5.dp, Color(0x3310B981), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    val gainText = if (isPrivacyActive) "+¥••••••" else {
                                        val sign = if (totalGainVal >= 0) "+" else ""
                                        "$sign¥${javaCurrencyFormat.format(totalGainVal.toLong())} ($sign${String.format(java.util.Locale.JAPAN, "%.1f", gainPctVal)}%)"
                                    }
                                    Text(gainText, fontSize = AppTextSize.dense, fontWeight = FontWeight.ExtraBold, color = if (totalGainVal >= 0) NeonGreen else ErrorRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        if (isPrivacyActive) {
                            Text(
                                text = "¥ •,•••,•••,•••",
                                fontSize = AppTextSize.hero,
                                fontWeight = FontWeight.ExtraBold,
                                color = OnSurface,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-0.5).sp
                            )
                        } else {
                            OdometerAmount(
                                targetValue = totalAssetsVal.toLong(),
                                animate = animateTotalAssets,
                                numberFormat = javaCurrencyFormat
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_3) else resources.getString(R.string.annual_dividend_label_25, (javaCurrencyFormat.format(totalCostVal.toLong())).toString()),
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.Medium,
                            color = SoftGray
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = BorderBlue, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sub-Grid 2 Columns
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Expected Dividend Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceContainer)
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(stringResource(R.string.asset_management_text_3), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_4) else resources.getString(R.string.add_holding_text_22, (javaCurrencyFormat.format(totalAnnualDivVal.toLong())).toString()),
                                        fontSize = AppTextSize.headline,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NeonGreen
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_6) else resources.getString(R.string.asset_management_label_7, (javaCurrencyFormat.format(netAnnualDivVal.toLong())).toString()),
                                        fontSize = AppTextSize.dense,
                                        color = SoftGray
                                    )
                                }
                            }

                            // Dividend Yield Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceContainer)
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Text(stringResource(R.string.asset_management_text_4), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = SoftGray)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = if (isPrivacyActive) "•••" else String.format(java.util.Locale.JAPAN, "%.2f", avgYield),
                                            fontSize = AppTextSize.headline,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OnSurface
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(stringResource(R.string.annual_dividend_text_4), fontSize = AppTextSize.dense, color = SoftGray, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor.copy(alpha = 0.15f)).padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(badgeText, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black, color = badgeColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= GOAL PROGRESS CARD =================
            item(key = "dividend_goal", contentType = "goal") {
                val summary by viewModel.assetManagementSummary.collectAsStateWithLifecycle()
                val totalAnnualDivVal = summary.annualDividend
                val monthlyIncomeForRoadmap = summary.roadmapDividend / 12
                val roadmapMonthlyTargets = listOf(
                    1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 8_000, 10_000, 15_000, 20_000,
                    25_000, 30_000, 35_000, 40_000, 45_000, 50_000, 60_000, 70_000, 80_000, 100_000,
                    120_000, 150_000, 180_000, 200_000, 250_000, 300_000, 350_000, 400_000, 500_000, 600_000
                )
                val currentMonthlyTarget = roadmapMonthlyTargets.firstOrNull { monthlyIncomeForRoadmap < it }
                    ?: roadmapMonthlyTargets.last()
                val currentAnnualTarget = currentMonthlyTarget * 12
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                        val progressPct = ((totalAnnualDivVal / currentAnnualTarget.toDouble()) * 100).coerceAtLeast(0.0)
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.asset_management_text_5 , javaCurrencyFormat.format(currentAnnualTarget)), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                            }
                            Text(String.format(java.util.Locale.JAPAN, "%.1f%%", progressPct), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = NeonGreen)
                        }

                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { (progressPct / 100f).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = NeonGreen,
                            trackColor = BorderColor
                        )

                        Spacer(modifier = Modifier.height(5.dp))
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPrivacyActive) resources.getString(R.string.asset_management_label_8) else resources.getString(R.string.asset_management_label_9, (javaCurrencyFormat.format(totalAnnualDivVal.toLong())).toString()),
                                fontSize = AppTextSize.dense,
                                color = SoftGray
                            )
                            val shortage = currentAnnualTarget.toDouble() - totalAnnualDivVal
                            Text(
                                text = if (shortage > 0) {
                                    if (isPrivacyActive) {
                                        val formattedTarget = if (currentMonthlyTarget >= 10000) resources.getString(R.string.asset_management_label_10, (currentMonthlyTarget / 10000).toString()) else resources.getString(R.string.asset_management_label_11, (javaCurrencyFormat.format(currentMonthlyTarget)).toString())
                                        resources.getString(R.string.asset_management_label_12, (formattedTarget).toString())
                                    } else {
                                        resources.getString(R.string.asset_management_label_13, (javaCurrencyFormat.format(shortage.toLong())).toString())
                                    }
                                } else resources.getString(R.string.asset_management_label_14),
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                color = if (shortage > 0) ErrorRed else NeonGreen
                            )
                        }
                    }
                }

            }

            // ================= HOLDINGS LIST HEADER & SORT =================
            item(key = "holdings_header", contentType = "header") {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedMonth != null) resources.getString(R.string.asset_management_label_15, (selectedMonth).toString()) else resources.getString(R.string.asset_management_label_16),
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = CircleShape, color = SurfaceContainerHigh) {
                            Text(
                                stringResource(R.string.asset_management_text_6 , displayedHoldings.size),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = NeonGreenLight,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selectedMonth != null) {
                            Text(
                                stringResource(R.string.asset_management_text_7),
                                color = NeonGreenLight,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.selectMonth(selectedMonth!!) }
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CardDarkLighter,
                            border = BorderStroke(0.5.dp, BorderBlue),
                            modifier = Modifier.clickable {
                                draftSortCriteria = sortCriteria
                                draftSortDirection = sortDirection
                                showSortModal = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getSortLabel(), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

            }
            // ================= SEARCH FIELD =================
            item(key = "holdings_search", contentType = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.asset_management_text_8), color = SoftGray, fontSize = AppTextSize.dense) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen.copy(alpha = 0.5f),
                        unfocusedBorderColor = BorderBlue,
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )

            }
            // ================= CATEGORY FILTER CHIPS =================
            item(key = "holdings_categories", contentType = "categories") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chips = listOf(
                        Pair(null, "すべて (${listItems.size})"),
                        Pair(Category.JAPAN, "日本株 (${categoryCounts[Category.JAPAN] ?: 0})"),
                        Pair(Category.US, "米国株 (${categoryCounts[Category.US] ?: 0})"),
                        Pair(Category.FUND, "投信 (${categoryCounts[Category.FUND] ?: 0})")
                    )
                    chips.forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) NeonGreen else CardDarkLighter,
                            border = BorderStroke(0.5.dp, if (isSelected) NeonGreen else BorderBlue),
                            modifier = Modifier.clickable { viewModel.selectCategory(cat) }
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = if (isSelected) OnPrimaryContainer else SoftGray,
                                fontSize = AppTextSize.dense,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        }
                    }
                }

            }
            // ================= HOLDING STOCK CARDS LIST =================
            if (activeHoldings.isEmpty() && exitingHoldings.isEmpty()) {
                item(key = "empty_holdings", contentType = "empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = BorderStroke(1.dp, BorderBlue)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = SoftGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.asset_management_text_9),
                                modifier = Modifier.fillMaxWidth(),
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.body,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                if (selectedMonth != null) resources.getString(R.string.asset_management_label_17, (selectedMonth).toString()) else resources.getString(R.string.asset_management_label_18),
                                color = SoftGray,
                                fontSize = AppTextSize.dense,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(animatedHoldingIds, key = { "holding:$it" }, contentType = { "holding" }) { holdingId ->
                    val exitingHolding = exitingHoldings.firstOrNull { it.id == holdingId }
                    val holdingFlow = remember(viewModel, holdingId) { viewModel.holdingForId(holdingId) }
                    val holdingState by holdingFlow.collectAsStateWithLifecycle(
                        initialValue = viewModel.holdingSnapshot(holdingId) ?: exitingHolding
                    )
                    val holding = holdingState ?: exitingHolding ?: return@items
                    val isExiting = exitingHolding != null
                    val isRestoring = holding.id in restoringHoldingIds
                    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
                    val calculationBasis by viewModel.calculationBasis.collectAsStateWithLifecycle()
                    val exitOffset by animateIntOffsetAsState(
                        targetValue = if (isExiting) IntOffset(-listState.layoutInfo.viewportSize.width, 0) else IntOffset.Zero,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                        label = "holding-exit-${holding.id}"
                    )
                    val exitAlpha by animateFloatAsState(
                        targetValue = if (isExiting) 0f else 1f,
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                        label = "holding-exit-alpha-${holding.id}"
                    )
                    val restoreOffset by animateIntOffsetAsState(
                        targetValue = if (isRestoring) IntOffset(-listState.layoutInfo.viewportSize.width, 0) else IntOffset.Zero,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                        label = "holding-restore-${holding.id}"
                    )
                    val restoreAlpha by animateFloatAsState(
                        targetValue = if (isRestoring) 0f else 1f,
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                        label = "holding-restore-alpha-${holding.id}"
                    )
                    val isUSD = holding.currency == "$"
                    val curVal = holding.totalValuation
                    val curCost = holding.totalCost
                    val diff = holding.gainLoss
                    val diffPct = holding.gainLossPercent
                    val annualDiv = holding.getEffectiveAnnualDividend(taxRateMode)
                    val financialDataFlow = remember(viewModel, holding.code) { viewModel.financialDataFor(holding.code) }
                    val financialData by financialDataFlow.collectAsStateWithLifecycle(
                        initialValue = viewModel.financialDataSnapshot(holding.code)
                    )
                    val dividends = financialData?.dividends
                    val latestDividendPeriod = remember(dividends) {
                        dividends?.zipWithNext()
                            ?.lastOrNull { (previous, current) -> current.year == previous.year + 1 }
                    }
                    val latestDividendChange = latestDividendPeriod?.let { (previous, current) -> current.dps - previous.dps }
                    val latestDividendGrowth = latestDividendPeriod?.let { (previous, current) ->
                        if (previous.dps > 0.0) (current.dps - previous.dps) / previous.dps * 100.0 else null
                    }
                    val annualDividendChange = latestDividendChange?.let {
                        holding.shares * it / holding.priceUnit * holding.currencyFactor
                    }
                    val payoutMonthSummary = remember(holding.dividendMonths) {
                        holding.dividendMonths.sorted()
                            .joinToString("・") { "${it}月" }
                            .ifEmpty { "配当月未取得" }
                    }
                    val swipeOffset = remember(holding.id) { Animatable(0f) }
                    LaunchedEffect(revealedHoldingId, holding.id) {
                        if (revealedHoldingId != holding.id) swipeOffset.animateTo(0f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement(animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { if (isExiting) exitOffset else restoreOffset }
                                .graphicsLayer { alpha = if (isExiting) exitAlpha else restoreAlpha }
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                        Row(
                            modifier = Modifier.matchParentSize(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier.width(deleteActionWidth).fillMaxHeight(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Surface(
                                    onClick = { deleteHolding(holding) },
                                    modifier = Modifier.width(deleteActionWidth).fillMaxHeight(),
                                    shape = RoundedCornerShape(0.dp),
                                    color = ErrorRed
                                ) {
                                    Column(
                                        modifier = Modifier.offset(x = (deleteActionWidth - deleteActionVisibleWidth) / 2),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                        Text("削除", color = Color.White, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                                .pointerInput(holding.id, deleteActionVisibleWidthPx) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            if (revealedHoldingId != holding.id) {
                                                revealedHoldingId = null
                                                screenScope.launch { swipeOffset.snapTo(0f) }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            screenScope.launch {
                                                swipeOffset.snapTo(
                                                    (swipeOffset.value + dragAmount).coerceIn(-deleteActionVisibleWidthPx, 0f)
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            val shouldReveal = swipeOffset.value <= -deleteActionVisibleWidthPx / 2f
                                            screenScope.launch {
                                                swipeOffset.animateTo(if (shouldReveal) -deleteActionVisibleWidthPx else 0f)
                                                revealedHoldingId = if (shouldReveal) holding.id else null
                                            }
                                        },
                                        onDragCancel = {
                                            screenScope.launch { swipeOffset.animateTo(0f) }
                                            if (revealedHoldingId == holding.id) revealedHoldingId = null
                                        }
                                    )
                                }
                                .clickable { onNavigateToDetail(holding.code) },
                            shape = if (swipeOffset.value < 0f) {
                                RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp)
                            } else {
                                RoundedCornerShape(16.dp)
                            },
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            border = BorderStroke(
                                1.dp,
                                if (selectedMonth != null) NeonGreenLight.copy(alpha = 0.5f) else BorderBlue
                            )
                        ) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 8.dp)
                        ) {
                            // Top Row: Code, Name, Sector, Valuation & Gain
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            viewModel.japaneseDisplayName(holding.code, holding.name),
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface,
                                            fontSize = AppTextSize.body,
                                            maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (holding.category) {
                                                    Category.US -> SecondaryIndigo.copy(alpha = 0.14f)
                                                    Category.FUND -> ErrorRed.copy(alpha = 0.14f)
                                                    else -> BorderColor
                                                }
                                            ) {
                                                Text(
                                                    holding.code,
                                                    modifier = Modifier.padding(start = 5.dp, top = 0.dp, end = 5.dp, bottom = 1.6.dp),
                                                    color = when (holding.category) {
                                                        Category.US -> SecondaryIndigo
                                                        Category.FUND -> ErrorRed
                                                        else -> OnSurface
                                                    },
                                                    fontSize = AppTextSize.dense * 0.8f,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                                )
                                            }
                                            if (holding.accountType.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(shape = RoundedCornerShape(4.dp), color = BorderColor) {
                                                    Text(
                                                        compactAccountLabel(holding.accountType),
                                                        modifier = Modifier.padding(start = 5.dp, top = 0.dp, end = 5.dp, bottom = 1.6.dp),
                                                        color = SecondaryIndigo,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                holding.sector,
                                                modifier = Modifier.weight(1f),
                                                color = SoftGray,
                                                fontSize = AppTextSize.dense,
                                                maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        stringResource(R.string.add_holding_1_s , javaCurrencyFormat.format(curVal.toLong())),
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val sign = if (diff >= 0) "+" else ""
                                    Text(
                                        stringResource(R.string.asset_management_1_s_2_s_3_s_4_s , sign, javaCurrencyFormat.format(diff.toLong()), sign, String.format(java.util.Locale.JAPAN, "%.1f", diffPct)),
                                        fontWeight = FontWeight.Bold,
                                        color = if (diff >= 0) NeonGreenLight else ErrorRed,
                                        fontSize = AppTextSize.dense,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = BorderBlue, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            // Four compact metric tiles matching the portfolio card design.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDarkLighter)
                                    .border(0.5.dp, BorderBlue, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 4.dp, vertical = 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val latestChangeColor = when {
                                    latestDividendChange == null -> SoftGray
                                    latestDividendChange >= 0.0 -> NeonGreenLight
                                    else -> ErrorRed
                                }
                                HoldingMetricTile(
                                    label = resources.getString(R.string.asset_management_label_31),
                                    value = "${String.format(java.util.Locale.JAPAN, "%.2f", if (calculationBasis == "cost") holding.liveYoc else holding.liveYield)}%",
                                    detail = if (calculationBasis == "cost") resources.getString(R.string.yield_basis_cost_detail) else "現在株価比",
                                    modifier = Modifier.weight(1f),
                                    valueColor = NeonGreenLight
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = resources.getString(R.string.asset_management_label_32),
                                    value = if (annualDiv > 0) "¥${javaCurrencyFormat.format(annualDiv.toLong())}/年" else "再投資型",
                                    detail = payoutMonthSummary,
                                    modifier = Modifier.weight(1f)
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = resources.getString(R.string.asset_management_label_33),
                                    value = latestDividendGrowth?.let {
                                        String.format(java.util.Locale.JAPAN, "%+.1f%%", it)
                                    } ?: "未取得",
                                    detail = annualDividendChange?.let {
                                        val sign = if (it > 0) "+" else if (it < 0) "−" else ""
                                        "年額 $sign¥${javaCurrencyFormat.format(kotlin.math.abs(it).toLong())}"
                                    } ?: "履歴なし",
                                    modifier = Modifier.weight(1f),
                                    valueColor = latestChangeColor,
                                    detailColor = latestChangeColor
                                )
                                HoldingMetricDivider()
                                HoldingMetricTile(
                                    label = resources.getString(R.string.csv_import_label_5),
                                    value = "${holding.shares}株",
                                    detail = "@${holding.currency}${javaCurrencyFormat.format(holding.price.toLong())}",
                                    modifier = Modifier.weight(1f),
                                    showEditHint = true,
                                    onClick = {
                                        onOpenHoldingRegistration(holding.code)
                                    }
                                )
                            }

                            // Month Highlight Banner if selected
                            if (selectedMonth != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CardDarkLighter,
                                    border = BorderStroke(0.5.dp, NeonGreenLight.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.asset_management_text_10, selectedMonth.toString()), color = SoftGray, fontSize = AppTextSize.dense)
                                        }
                                        Text(holding.nextPayoutAmount, color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                        }
                        }
                        }
                    }
                }
            }

            // ================= NEXT DIVIDEND PAYOUT STRIP (100% DYNAMIC) =================
            item(key = "next_dividend", contentType = "payout") {
                val upcomingDividend by viewModel.assetManagementUpcomingDividend.collectAsStateWithLifecycle()
                val nextStockName = upcomingDividend.name

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(stringResource(R.string.asset_management_text_11), color = SoftGray, fontSize = AppTextSize.dense)
                                Text(if (nextStockName != null) stringResource(R.string.asset_management_label, (upcomingDividend.month).toString(), nextStockName) else stringResource(R.string.asset_management_label_2), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.caption)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.add_holding_1_s , javaCurrencyFormat.format(upcomingDividend.amount)), fontWeight = FontWeight.Bold, color = NeonGreenLight, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                            Text(stringResource(R.string.asset_management_text_12 , upcomingDividend.dividendType ?: resources.getString(R.string.asset_management_label_19), upcomingDividend.shares), color = SoftGray, fontSize = AppTextSize.dense)
                        }
                    }
                }

            }
            item(key = "bottom_spacing", contentType = "spacing") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ================= SORT MODAL BOTTOM SHEET =================
    if (showSortModal) {
        ModalBottomSheet(
            onDismissRequest = { showSortModal = false },
            containerColor = CardDark,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdaptiveRow(
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
                            stringResource(R.string.asset_management_text_13),
                            color = OnSurface,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showSortModal = false },
                        modifier = Modifier.minimumInteractiveComponentSize().size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.watchlist_text_23),
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
                        listOf(
                            "desc" to R.string.asset_management_text_14,
                            "asc" to R.string.asset_management_text_15
                        ).forEach { (direction, labelRes) ->
                            val isSelected = draftSortDirection == direction
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryEmerald else Color.Transparent)
                                    .clickable { draftSortDirection = direction }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (direction == "desc") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (isSelected) OnPrimary else SoftGray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(labelRes),
                                        color = if (isSelected) OnPrimary else SoftGray,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.watchlist_text_24),
                        color = SoftGray,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    val criteriaOptions = listOf(
                        Triple("val", resources.getString(R.string.annual_dividend_label), Icons.Default.AccountBalance),
                        Triple("gain", resources.getString(R.string.asset_management_label_34), Icons.AutoMirrored.Filled.TrendingUp),
                        Triple("div", resources.getString(R.string.asset_management_label_31), Icons.Default.Percent),
                        Triple("div_amt", resources.getString(R.string.asset_management_label_35), Icons.Default.Payments),
                        Triple("code", resources.getString(R.string.dividend_change_analysis_cards_label_12), Icons.Default.Bookmark)
                    )
                    criteriaOptions.forEach { (key, label, icon) ->
                        val isSelected = draftSortCriteria == key
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
                                .clickable { draftSortCriteria = key }
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
                    }
                }

                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showSortModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppTextSize.caption
                        )
                    }
                    Button(
                        onClick = {
                            sortCriteria = draftSortCriteria
                            sortDirection = draftSortDirection
                            showSortModal = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(
                            stringResource(R.string.apply),
                            color = OnPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = AppTextSize.caption
                        )
                    }
                }
            }
        }
    }

    // ================= ADD ASSET MODAL DIALOG =================
    if (showAddAssetModal) {
        AlertDialog(
            onDismissRequest = { showAddAssetModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (stockCode.isNotEmpty() && shareCount.isNotEmpty() && acqPrice.isNotEmpty()) {
                            val shares = shareCount.toIntOrNull() ?: 100
                            val price = acqPrice.toDoubleOrNull() ?: 2500.0
                            viewModel.updateHolding(stockCode, shares, price)
                            showAddAssetModal = false
                            stockCode = ""
                            shareCount = ""
                            acqPrice = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.asset_management_text_16), color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.caption)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAssetModal = false }) {
                    Text(stringResource(R.string.cancel), color = SoftGray, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.asset_management_text_17), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.asset_management_text_18), color = SoftGray, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                    OutlinedTextField(
                        value = stockCode,
                        onValueChange = { stockCode = it },
                        label = { Text(stringResource(R.string.asset_management_text_19), fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (stockCode.isNotEmpty()) {
                                IconButton(onClick = { stockCode = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = shareCount,
                        onValueChange = { shareCount = it },
                        label = { Text(stringResource(R.string.asset_management_text_20), fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (shareCount.isNotEmpty()) {
                                IconButton(onClick = { shareCount = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                    OutlinedTextField(
                        value = acqPrice,
                        onValueChange = { acqPrice = it },
                        label = { Text(stringResource(R.string.asset_management_text_21), fontSize = AppTextSize.dense) },
                        singleLine = true,
                        trailingIcon = {
                            if (acqPrice.isNotEmpty()) {
                                IconButton(onClick = { acqPrice = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_a11y), tint = SoftGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = NeonGreen,
                            unfocusedLabelColor = SoftGray,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                }
            },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, BorderBlue, RoundedCornerShape(20.dp))
        )
    }

    // ================= HOLDING QUICK EDIT BOTTOM SHEET =================
    quickEditHolding?.let { holding ->
        HoldingQuickEditModal(holding, viewModel) { quickEditHolding = null }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HoldingQuickEditModal(
    holding: Holding,
    viewModel: DividendViewModel,
    onDismiss: () -> Unit
) {
    var quickEditShares by remember(holding.id) { mutableStateOf(holding.shares.toString()) }
    var quickEditAcqPrice by remember(holding.id) { mutableStateOf(holding.acqPrice.toString().removeSuffix(".0")) }
    var quickEditMarketPrice by remember(holding.id) { mutableDoubleStateOf(holding.price) }
    val javaCurrencyFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shares = quickEditShares.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val editValid = quickEditShares.toIntOrNull()?.let { it >= 0 } == true && quickEditAcqPrice.toDoubleOrNull()?.let(com.kurogane.dividend.model.PortfolioMath::validMoney) == true
    val averagePrice = quickEditAcqPrice.toDoubleOrNull()?.takeIf(com.kurogane.dividend.model.PortfolioMath::validMoney) ?: 0.0
    val currencyFactor = holding.currencyFactor
    val cost = shares * averagePrice / holding.priceUnit * currencyFactor
    val currentPrice = quickEditMarketPrice.takeIf { it > 0.0 } ?: holding.price
    val valuation = shares * currentPrice / holding.priceUnit * currencyFactor
    val gain = valuation - cost
    val gainPct = if (cost > 0.0) gain / cost * 100.0 else 0.0
    val projectedDividend = shares * holding.dps / holding.priceUnit * currencyFactor
    val modalCurrency = if (holding.currency == "$") "$" else "¥"
    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = CardDark,
        contentColor = OnSurface,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp)
                    .clip(CircleShape).background(BorderBlue)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(CardDarkLighter).border(1.dp, BorderBlue, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, null, tint = NeonGreenLight, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.asset_management_text_22), color = OnSurface, fontSize = AppTextSize.headline, fontWeight = FontWeight.ExtraBold, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                    Text(stringResource(R.string.asset_management_1_s_2_s_3_s , holding.code, viewModel.japaneseDisplayName(holding.code, holding.name), holding.market), color = SoftGray, fontSize = AppTextSize.caption, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                }
                IconButton(onClick = { onDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = SoftGray)
                }
            }

            AdaptiveRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                    .border(1.dp, BorderBlue, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.asset_management_text_23), color = SoftGray, fontSize = AppTextSize.dense)
                    Text(stringResource(R.string.add_holding_1_s_2_s_2 , modalCurrency, javaCurrencyFormat.format(currentPrice)), color = OnSurface, fontSize = AppTextSize.screenTitle, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                }
                Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                    Text(
                        stringResource(R.string.asset_management_text_24 , holding.dailyChange),
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = if (holding.isPositive) NeonGreenLight else ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            AdaptiveRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(stringResource(R.string.asset_management_text_25), color = SoftGray, fontSize = AppTextSize.dense, modifier = Modifier.weight(1f))
                listOf(-50.0, -10.0, 10.0, 50.0).forEach { delta ->
                    val label = if (delta > 0) "+${delta.toInt()}" else delta.toInt().toString()
                    Surface(
                        modifier = Modifier.clickable { quickEditMarketPrice = (currentPrice + delta).coerceAtLeast(1.0) },
                        shape = RoundedCornerShape(7.dp),
                        color = CardDarkLighter
                    ) {
                        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 6.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { quickEditMarketPrice = holding.price }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                    Text(stringResource(R.string.asset_management_text_26), color = NeonGreenLight, fontSize = AppTextSize.dense)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.asset_management_text_27), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.asset_management_text_28), color = SoftGray, fontSize = AppTextSize.caption)
                }
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QuickEditStepButton("−", enabled = shares > 1) { quickEditShares = (shares - 10).coerceAtLeast(1).toString() }
                    OutlinedTextField(
                        value = quickEditShares,
                        onValueChange = { quickEditShares = it.filter(Char::isDigit).ifEmpty { "1" } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text(stringResource(R.string.asset_management_text_29), color = SoftGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreenLight, unfocusedBorderColor = BorderBlue, focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                    )
                    QuickEditStepButton("＋") { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 10).toString() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("1単元 (100株)" to { quickEditShares = "100" }, "+10" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 10).toString() }, "+100" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 100).toString() }, "+500" to { quickEditShares = com.kurogane.dividend.model.PortfolioMath.addShares(shares, 500).toString() }).forEach { (label, action) ->
                        Surface(Modifier.clickable(onClick = action), shape = RoundedCornerShape(10.dp), color = CardDarkLighter, border = BorderStroke(1.dp, BorderBlue)) {
                            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 9.dp), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sell, null, tint = NeonGreenLight, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.asset_management_text_30 , modalCurrency), color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { quickEditAcqPrice = currentPrice.toString().removeSuffix(".0") }) {
                        Text(stringResource(R.string.add_holding_text_9), color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QuickEditStepButton("−", enabled = averagePrice > 1) { quickEditAcqPrice = (averagePrice - 10).coerceAtLeast(1.0).toString().removeSuffix(".0") }
                    OutlinedTextField(
                        value = quickEditAcqPrice,
                        onValueChange = { quickEditAcqPrice = it.filter { ch -> ch.isDigit() || ch == '.' }.ifEmpty { "1" } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = OnSurface, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix = { Text(modalCurrency, color = SoftGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonGreenLight, unfocusedBorderColor = BorderBlue, focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                    )
                    QuickEditStepButton("＋") { quickEditAcqPrice = (averagePrice + 10).toString().removeSuffix(".0") }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BackgroundDark)
                    .border(1.dp, NeonGreenLight.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, null, tint = NeonGreenLight, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.asset_management_text_31), color = NeonGreenLight, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = CircleShape, color = NeonGreen.copy(alpha = 0.18f)) {
                        Text(stringResource(R.string.asset_management_text_32), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = NeonGreenLight, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = BorderBlue)
                AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickEditSummary("投資元本", "$modalCurrency${javaCurrencyFormat.format(cost.toLong())}", OnSurface, Modifier.weight(1f))
                    QuickEditSummary("評価損益", "${if (gain >= 0) "+" else "−"}$modalCurrency${javaCurrencyFormat.format(kotlin.math.abs(gain).toLong())}\n(${String.format(Locale.JAPAN, "%+.1f%%", gainPct)})", if (gain >= 0) NeonGreenLight else ErrorRed, Modifier.weight(1f), Alignment.CenterHorizontally)
                    QuickEditSummary("年間予想配当金", "$modalCurrency${javaCurrencyFormat.format(projectedDividend.toLong())}/年", NeonGreenLight, Modifier.weight(1f), Alignment.End)
                }
            }

            AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.weight(0.8f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDarkLighter, contentColor = OnSurface),
                    border = BorderStroke(1.dp, BorderBlue)
                ) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        if (viewModel.updateHolding(holding.code, shares, averagePrice, currentPrice)) onDismiss()
                    },
                    enabled = editValid,
                    modifier = Modifier.weight(1.5f).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.Check, null, tint = OnPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.asset_management_text_33), color = OnPrimaryContainer, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun QuickEditStepButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(50.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CardDarkLighter,
        border = BorderStroke(1.dp, BorderBlue)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (enabled) OnSurface else SoftGray, fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickEditSummary(label: String, value: String, valueColor: Color, modifier: Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SoftGray, fontSize = AppTextSize.dense, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
        Text(value, color = valueColor, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 2)
    }
}

@Composable
private fun HoldingMetricTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color = OnSurface,
    detailColor: Color = SoftGray,
    showEditHint: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    val clickModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
    } else Modifier
    Column(
        modifier = modifier
            .then(clickModifier)
            .padding(horizontal = if (showEditHint) 3.dp else 2.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = SoftGray, fontSize = 9.sp, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
            if (showEditHint) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = NeonGreenLight.copy(alpha = 0.72f),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
        Text(detail, color = detailColor, fontSize = 8.sp, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
    }
}

@Composable
private fun HoldingMetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(BorderBlue)
    )
}

private fun compactAccountLabel(accountType: String): String = when {
    accountType.startsWith("特定口座") -> accountType
        .replace("特定口座", "特定")
        .replace(" (四半期)", "・四半期")
        .replace("(四半期)", "・四半期")
    accountType.startsWith("新NISA成長枠") -> accountType.replace("新NISA成長枠", "新NISA")
    accountType.startsWith("新NISAつみたて投資枠") -> accountType.replace("新NISAつみたて投資枠", "新NISA積立")
    else -> accountType
}
