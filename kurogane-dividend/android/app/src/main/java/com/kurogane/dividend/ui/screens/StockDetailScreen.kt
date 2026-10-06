package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurogane.dividend.data.financialText
import com.kurogane.dividend.data.financialYears
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import com.kurogane.dividend.ui.components.StatusMessage
import com.kurogane.dividend.ui.networkErrorMessage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.model.StockAnalysisNarrative
import com.kurogane.dividend.model.StockAnalysisNarrativeGenerator
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {}
) {
    val resources = LocalContext.current.resources
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    val exchangeRate by viewModel.exchangeRate.collectAsStateWithLifecycle()
    val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
    
    var resolvedSecurity by remember(stockCode) { mutableStateOf<com.kurogane.dividend.data.CloudflareSecurity?>(null) }
    var lookupFinished by remember(stockCode) { mutableStateOf(false) }
    var lookupError by remember(stockCode) { mutableStateOf<String?>(null) }
    var lookupRetry by remember(stockCode) { mutableIntStateOf(0) }
    LaunchedEffect(stockCode, lookupRetry) {
        lookupFinished = false
        lookupError = null
        val result = com.kurogane.dividend.data.runCatchingCancellable { viewModel.resolveCloudflareSecurity(stockCode) }
        resolvedSecurity = result.getOrNull()
        lookupError = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }
        lookupFinished = true
    }
    val currentHolding = com.kurogane.dividend.model.StockDetailResolver.resolve(stockCode, holdings, watchlist, resolvedSecurity)?.let { holding ->
        val dps = financialData[holding.code]?.dividends?.lastOrNull()?.dps ?: holding.dps
        holding.copy(dps = dps, yieldRate = com.kurogane.dividend.model.PortfolioMath.yieldPercent(dps, holding.price),
            yoc = com.kurogane.dividend.model.PortfolioMath.yieldPercent(dps, holding.acqPrice),
            usdJpyRate = exchangeRate?.usdJpy, usWithholdingEnabled = usTaxDeduction)
    }
    if (currentHolding == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!lookupFinished) CircularProgressIndicator() else {
                Text(stringResource(R.string.stock_lookup_failed), color = OnSurface)
                if (lookupError != null) StatusMessage(lookupError!!,
                    actionLabel = resources.getString(R.string.retry), onAction = { lookupRetry++ })
                else TextButton(onClick = { lookupRetry++ }) { Text(stringResource(R.string.retry)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        return
    }

    var showHoldingQuickEdit by remember(currentHolding.id) { mutableStateOf(false) }
    var showDeleteHoldingConfirmation by remember(currentHolding.id) { mutableStateOf(false) }
    val isOwnedHolding = holdings.any { it.id == currentHolding.id }
    val isBookmarked = watchlist.any { it.code.equals(currentHolding.code, true) }

    // Selected status tab: default to "risk" to highlight the user requested detailed mock
    var selectedStatusTab by rememberSaveable(stockCode) { mutableStateOf("risk") }
    var simAddedShares by rememberSaveable(stockCode) { mutableIntStateOf(100) }
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationAccess = com.kurogane.dividend.ui.components.rememberNotificationAccess {
        viewModel.setNotificationEnabled(com.kurogane.dividend.notifications.NotificationKind.DIVIDEND, true)
    }
    val isAlertOn = notificationSettings.dividendEnabled

    val baseShares = currentHolding.shares
    val currentPrice = currentHolding.price
    val dps = currentHolding.dps
    val financial = financialData[currentHolding.code]
    val baseProfile = financial?.profile ?: com.kurogane.dividend.model.StockFinancialProfile()
    var financialError by remember(currentHolding.code) { mutableStateOf(false) }
    var financialLoading by remember(currentHolding.code) { mutableStateOf(false) }
    var financialFailure by remember(currentHolding.code) { mutableStateOf<String?>(null) }
    var financialRetry by remember(currentHolding.code) { mutableIntStateOf(0) }
    LaunchedEffect(currentHolding.code, financialRetry) {
        if (currentHolding.category != com.kurogane.dividend.model.Category.JAPAN) return@LaunchedEffect
        financialLoading = true
        financialError = false
        financialFailure = null
        try {
            val result = com.kurogane.dividend.data.runCatchingCancellable { viewModel.fetchFinancialData(currentHolding.code, force = true) }
            financialError = result.isFailure
            financialFailure = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }
        } finally { financialLoading = false }
    }
    var backendAnalysisValues by remember(currentHolding.code) { mutableStateOf<Map<String, Double?>?>(null) }
    LaunchedEffect(currentHolding.code) {
        backendAnalysisValues = viewModel.fetchStockAnalysis(currentHolding.code)?.values
    }
    val analysisNarrative = remember(currentHolding, backendAnalysisValues, financial) { StockAnalysisNarrativeGenerator.generate(
        (backendAnalysisValues ?: emptyMap()) + (financial?.values ?: emptyMap())
    ) }
    val healthNarrative = remember(financial) { StockAnalysisNarrativeGenerator.generate(financial?.values.orEmpty()) }
    val valuationNarrative = remember(backendAnalysisValues) { StockAnalysisNarrativeGenerator.generate(backendAnalysisValues.orEmpty().filterKeys { it == "per" || it == "pbr" }) }
    val profile = baseProfile.copy(
        healthGrade = healthNarrative.grade ?: "未判定", healthScore = healthNarrative.score ?: -1,
        healthEvaluation = healthNarrative.title, healthDiagnosisComment = healthNarrative.findings.joinToString(" ") { it.text }.ifBlank { "財務指標未取得" },
        valuationScore = valuationNarrative.score ?: -1,
        buySignalTitle = valuationNarrative.title,
        buySignalComment = valuationNarrative.findings.joinToString(" ") { it.text }.ifBlank { "株価指標未取得" }
    )
    val liveYield = if (currentPrice > 0) (dps / currentPrice) * 100.0 else 0.0
    val liveYoc = if (currentHolding.acqPrice > 0) (dps / currentHolding.acqPrice) * 100.0 else liveYield

    val totalSimShares = baseShares.toLong() + simAddedShares
    val simPurchaseAmount = simAddedShares * currentHolding.price / currentHolding.priceUnit
    val newTotalAnnualDiv = currentHolding.getEffectiveDividendForShares(totalSimShares, taxRateMode).toLong()
    val newMonthlyDiv = newTotalAnnualDiv / 12
    val multiplier = if (baseShares > 0) String.format(java.util.Locale.JAPAN, "%.1f", totalSimShares.toFloat() / baseShares) else "1.0"

    val cutImpact = (currentHolding.getEffectiveAnnualDividend(taxRateMode) / 2).toLong()

    val totalPortfolioDiv = holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode).toLong() }
    val currentStockAnnualDiv = currentHolding.getEffectiveAnnualDividend(taxRateMode).toLong()
    val divDependency = if (totalPortfolioDiv > 0) (currentStockAnnualDiv.toDouble() / totalPortfolioDiv.toDouble()) * 100.0 else 0.0
    val divDependencyStr = String.format(java.util.Locale.JAPAN, "%.1f", divDependency)

    val totalPortfolioVal = holdings.sumOf { it.totalValuation.toLong() }
    val sectorVal = holdings.filter { it.sector == currentHolding.sector }.sumOf { it.totalValuation.toLong() }
    val sectorDependency = if (totalPortfolioVal > 0) (sectorVal.toDouble() / totalPortfolioVal.toDouble()) * 100.0 else 0.0
    val sectorDependencyStr = String.format(java.util.Locale.JAPAN, "%.1f", sectorDependency)
    val cleanSectorName = currentHolding.sector.replace(Regex("^.*[•·]\\s*"), "")
    val sectorRiskText = if (sectorDependency > 30.0) "高集中" else if (sectorDependency > 20.0) "やや集中" else "適正分散"

    val isHighRisk = divDependency > 10.0
    val riskBadgeText = if (divDependency > 25.0) "高リスク・要分散" else if (divDependency > 10.0) "やや高め" else "適正範囲"

    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(stringResource(R.string.stock_detail_text), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
            }

            item {
                Text(stringResource(R.string.stock_detail_text_2 , financial?.period?.ifBlank { resources.getString(R.string.not_available) } ?: resources.getString(R.string.not_available), if (financialError) resources.getString(R.string.stock_detail_label_14) else ""), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                if (currentHolding.category == com.kurogane.dividend.model.Category.JAPAN) {
                    when {
                        financialLoading -> StatusMessage(resources.getString(R.string.data_loading))
                        financialError -> StatusMessage(financialFailure ?: resources.getString(R.string.financial_update_failed),
                            color = ErrorRed, actionLabel = resources.getString(R.string.retry), onAction = { financialRetry++ })
                        financial == null -> StatusMessage(resources.getString(R.string.financial_empty),
                            actionLabel = resources.getString(R.string.retry), onAction = { financialRetry++ })
                    }
                }
            }
            // 1. Hero Stock Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentHolding.name,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SurfaceContainerHigh
                                    ) {
                                        Text(
                                            currentHolding.code,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BackgroundDark,
                                        border = BorderStroke(1.dp, BorderColor)
                                    ) {
                                        Text(
                                            currentHolding.market,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryEmerald.copy(alpha = 0.15f),
                                        border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            currentHolding.accountType,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.addHoldingToWatchlist(currentHolding) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBookmarked) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceContainer)
                                    .border(
                                        1.dp,
                                        if (isBookmarked) PrimaryEmerald else BorderColor,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isBookmarked) "ウォッチリスト登録済み" else "ウォッチリストに登録",
                                    tint = if (isBookmarked) PrimaryEmerald else OnSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Price & Daily Change
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.stock_detail_text_3),
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.add_holding_1_s_2_s_2 , currentHolding.currency, currencyFormat.format(currentHolding.price)),
                                    fontSize = AppTextSize.largeMetric,
                                    fontWeight = FontWeight.Black,
                                    color = OnSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentHolding.isPositive) PrimaryEmerald.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, if (currentHolding.isPositive) PrimaryEmerald.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    currentHolding.dailyChange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (currentHolding.isPositive) PrimaryEmerald else ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Sub KPI Grid (3 columns)
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(if (currentHolding.category == com.kurogane.dividend.model.Category.FUND) stringResource(R.string.stock_detail_label) else stringResource(R.string.stock_detail_label_2), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(stringResource(R.string.add_holding_1_s_2_s_2 , currentHolding.currency, currentHolding.dps), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.caption, fontFamily = FontFamily.Monospace)
                                    Text(stringResource(R.string.annual_dividend_text_44), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.stock_detail_text_4), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(String.format(Locale.JAPAN, "%.2f%%", currentHolding.liveYield), fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = AppTextSize.caption, fontFamily = FontFamily.Monospace)
                                    Text(stringResource(R.string.stock_detail_text_5), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.stock_detail_text_6), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(String.format(Locale.JAPAN, "%.2f%% YOC", currentHolding.liveYoc), fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = AppTextSize.caption, fontFamily = FontFamily.Monospace)
                                    Text(stringResource(R.string.stock_detail_text_7 , currentHolding.currency, currentHolding.acqPrice), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                        }
                    }
                }
            }

            // 2. 1株配当金の推移 (4期) Bar Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BarChart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.stock_detail_text_8), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text(if (profile.consecutiveYears > 0) stringResource(R.string.stock_detail_label_3) else stringResource(R.string.stock_detail_label_4), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4 Bars Container
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val history = financial?.dividends.orEmpty().takeLast(4)
                                val maxDps = history.maxOfOrNull { it.dps }?.takeIf { it > 0 } ?: 1.0
                                val prefix = currentHolding.currency
                                val bars = history.map { point -> Triple(stringResource(R.string.stock_detail_label_5, (point.year).toString()), stringResource(R.string.add_holding_1_s_2_s_2, (prefix).toString(), (point.dps.financialText()).toString()), (point.dps / maxDps * 0.95).toFloat()) }
                                if (bars.isEmpty()) Text(stringResource(R.string.stock_detail_text_9), color = OnSurfaceVariant)
                                bars.forEach { (term, dpsVal, ratio) ->
                                    val isProjected = term == bars.lastOrNull()?.first
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Text(dpsVal, color = if (isProjected) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(26.dp)
                                                    .height((60 * ratio).dp)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(
                                                        if (isProjected) Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmeraldContainer))
                                                        else Brush.verticalGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5)))
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(term, color = if (isProjected) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val recent = financial?.dividends.orEmpty().takeLast(4)
                            val growth = if (recent.size == 4 && recent.first().dps > 0 && recent.last().year - recent.first().year == 3)
                                ((recent.last().dps / recent.first().dps) - 1) * 100 else Double.NaN
                            Text(stringResource(R.string.stock_detail_text_10 , growth.financialText(1, "%")), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.stock_detail_text_11 , profile.payoutRatio.financialText(1, "%")), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. 銘柄総合分析ステータス (2x2 Grid + Detailed Panels matching image.png)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.stock_detail_text_22), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Text(stringResource(R.string.stock_detail_text_23), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2x2 Grid of Status Cards
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Health
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "health") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "health") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "health") PrimaryEmerald else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "health" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.stock_detail_text_24), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(stringResource(R.string.add_holding_1_s_2_s , profile.healthGrade, profile.payoutRatio.financialText(1, "%")), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text(profile.healthEvaluation, color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                }
                            }

                            // 2. Buy Signal
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "value") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "value") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "value") PrimaryEmerald else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "value" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.stock_detail_text_25), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.TrackChanges, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(stringResource(R.string.stock_detail_text_26 , String.format(java.util.Locale.JAPAN, "%.2f", liveYield)), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text(profile.getValuationStatus(currentHolding.price, currentHolding.dps).split(resources.getString(R.string.stock_detail_label_20)).first(), color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 3. Growth
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "growth") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "growth") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "growth") SecondaryIndigo else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "growth" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.stock_detail_text_27), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = SecondaryIndigo, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(if (profile.consecutiveYears > 0) stringResource(R.string.stock_detail_label_10, (if (profile.consecutiveYears >= 0) resources.getString(R.string.stock_detail_label_5, (profile.consecutiveYears).toString()) else resources.getString(R.string.not_available)).toString(), (profile.growthRate.financialText(1, "%")).toString()) else stringResource(R.string.stock_detail_label_11, (profile.growthRate.financialText(1, "%")).toString()), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text(stringResource(R.string.dividend_change_analysis_cards_text_15 , profile.noCutYears.financialYears()), color = PrimaryEmerald, fontSize = AppTextSize.dense)
                                }
                            }

                            // 4. Concentration Risk (Matching image (6).png)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedStatusTab == "risk") SurfaceContainer else BackgroundDark,
                                border = BorderStroke(
                                    if (selectedStatusTab == "risk") 1.5.dp else 1.dp,
                                    if (selectedStatusTab == "risk") AccentAmber else BorderColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedStatusTab = "risk" }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.stock_detail_text_28), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(stringResource(R.string.stock_detail_1_s_2_s , if (isHighRisk) resources.getString(R.string.stock_detail_label_21) else resources.getString(R.string.stock_detail_label_22), divDependencyStr), color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                                    Text(stringResource(R.string.stock_detail_text_29), color = AccentAmber, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        // DETAIL ACCORDION CONTENT MATCHING IMAGE (6).PNG
                        if (selectedStatusTab == "risk") {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Header follows the buy-signal detail card format.
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = AccentAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.stock_detail_text_30),
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.section
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = AccentAmber.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(AccentAmber))
                                        Text(riskBadgeText, color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Main panel uses the same dark outer card and inset metric cards as buy-signal analysis.
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceContainer,
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            // Portfolio dependency card, parallel to the yield-range card.
                                            AdaptiveRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.stock_detail_text_31),
                                                color = OnSurface,
                                                fontSize = AppTextSize.caption,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isHighRisk) AccentAmber.copy(alpha = 0.15f) else PrimaryEmerald.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    if (isHighRisk) resources.getString(R.string.stock_detail_label_23) else resources.getString(R.string.stock_detail_label_24),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    color = if (isHighRisk) AccentAmber else PrimaryEmerald,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            stringResource(R.string.settings_1_s , divDependencyStr),
                                            color = if (isHighRisk) AccentAmber else PrimaryEmerald,
                                            fontSize = AppTextSize.screenTitle,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                            Spacer(modifier = Modifier.height(8.dp))

                                    // Gradient Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(BorderColor)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((divDependency / 50.0).coerceIn(0.08, 1.0).toFloat())
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(PrimaryEmeraldContainer, AccentAmber, AccentAmber)
                                                    )
                                                )
                                        )
                                    }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            AdaptiveRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                        Text(stringResource(R.string.stock_detail_text_32), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text(stringResource(R.string.stock_detail_text_33), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text(stringResource(R.string.stock_detail_text_34 , currentHolding.name, divDependencyStr), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    }

                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Secondary risk metrics use the same inset-card treatment as the other analyses.
                                    AdaptiveRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(stringResource(R.string.stock_detail_text_35), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(stringResource(R.string.settings_1_s , sectorDependencyStr), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(stringResource(R.string.stock_detail_text_36 , cleanSectorName, sectorRiskText), color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(stringResource(R.string.stock_detail_text_37), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(stringResource(R.string.stock_detail_1_s , currencyFormat.format(cutImpact)), color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(stringResource(R.string.stock_detail_text_38 , if (cutImpact > 8000) resources.getString(R.string.stock_detail_label_25) else if (cutImpact > 3000) resources.getString(R.string.stock_detail_label_26) else resources.getString(R.string.stock_detail_label_27)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Rebalance Recommendation Row
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceContainer,
                                        border = BorderStroke(1.dp, BorderColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Tune, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.stock_detail_text_39), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentAmber.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    stringResource(R.string.stock_detail_text_40),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = AccentAmber,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Diagnosis comment card follows the shared analysis-card styling.
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0x1A10B981),
                                        border = BorderStroke(1.dp, Color(0x4010B981)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = PrimaryEmerald,
                                                modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (isHighRisk)
                                                    resources.getString(R.string.stock_detail_label_28, (divDependencyStr).toString())
                                                else
                                                    resources.getString(R.string.stock_detail_label_29, (divDependencyStr).toString()),
                                                color = OnSurface.copy(alpha = 0.9f),
                                                fontSize = AppTextSize.dense,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (selectedStatusTab == "value") {
                            // 買場シグナル詳細分析 (Exact match to image.png)
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header row
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrackChanges,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            stringResource(R.string.stock_detail_text_41),
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.section
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryEmerald)
                                            )
                                            Text(
                                                profile.buySignalZone,
                                                color = PrimaryEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }

                                // Container Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 1. 配当利回りレンジ判定
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                AdaptiveRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(stringResource(R.string.stock_detail_text_42), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                        ) {
                                                            Text(
                                                                profile.yieldRangeBadge,
                                                                color = PrimaryEmerald,
                                                                fontSize = AppTextSize.dense,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        stringResource(R.string.settings_1_s , String.format(Locale.US, "%.2f", currentHolding.yieldRate)),
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = AppTextSize.metric,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                val yld = currentHolding.yieldRate
                                                val rangeProgress = ((yld - profile.yieldRangeMin) / (profile.yieldRangeMax - profile.yieldRangeMin)).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(BorderColor)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(rangeProgress)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(Color(0xFF818CF8), SkyBlue, PrimaryEmerald)
                                                                )
                                                            )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                AdaptiveRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(stringResource(R.string.stock_detail_text_43 , profile.yieldRangeMin.financialText(2)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Text(stringResource(R.string.stock_detail_text_44 , profile.yieldRangeAvg.financialText(2)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Text(stringResource(R.string.stock_detail_text_45 , profile.yieldRangeMax.financialText(2)), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 2. 2x1 Grid for PER & PBR
                                        AdaptiveRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // PER
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    AdaptiveRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(stringResource(R.string.stock_detail_text_46), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                        Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(13.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.getPer(currentHolding.price), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.perEvaluation, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }

                                            // PBR
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    AdaptiveRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(stringResource(R.string.stock_detail_text_47), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                        Icon(Icons.Default.Tune, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(13.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.getPbr(currentHolding.price), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.pbrEvaluation, color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 3. 買場シグナル総合スコア
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                                    Text(stringResource(R.string.stock_detail_text_48), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                                ) {
                                                    Text(
                                                        stringResource(R.string.stock_detail_text_49 , if (profile.valuationScore >= 0) "${profile.valuationScore} / 100" else resources.getString(R.string.stock_detail_label_30)),
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 4. 総合自動診断 Box
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                    Text(profile.buySignalTitle, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.14f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        profile.buySignalBadge,
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    profile.buySignalComment,
                                                    color = OnSurface.copy(alpha = 0.95f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedStatusTab == "health") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header row
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = PrimaryEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            stringResource(R.string.stock_detail_text_50),
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.section
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryEmerald)
                                            )
                                            Text(
                                                stringResource(R.string.stock_detail_text_51 , profile.healthGrade),
                                                color = PrimaryEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AppTextSize.dense
                                            )
                                            Text(
                                                stringResource(R.string.stock_detail_1_s_2 , profile.healthEvaluation),
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }

                                // Container Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 1. Payout ratio card
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                AdaptiveRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(stringResource(R.string.stock_detail_text_52), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.caption)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                        ) {
                                                            Text(
                                                                profile.payoutStatusBadge,
                                                                color = PrimaryEmerald,
                                                                fontSize = AppTextSize.dense,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        stringResource(R.string.stock_detail_text_53 , profile.payoutRatio.financialText(1, "%")),
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = AppTextSize.metric,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                val progress = (profile.payoutRatio / 100.0).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat() ?: 0f
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(BorderColor)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(progress)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(PrimaryEmerald, PrimaryEmerald)
                                                                )
                                                            )
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                AdaptiveRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(stringResource(R.string.stock_detail_text_54), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Text(stringResource(R.string.stock_detail_text_55), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                    Text(stringResource(R.string.stock_detail_text_56), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 2. 2x2 Grid of Financial Metrics
                                        AdaptiveRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 自己資本比率
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_57), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(stringResource(R.string.annual_dividend_1_s , profile.equityRatio.financialText(1, "%")), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.equityRatioSub, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }

                                            // 株主還元方針
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_58), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.doePolicy, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.doePolicySub, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        AdaptiveRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // ネットD/Eレシオ
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_59), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.netDeRatio, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(stringResource(R.string.stock_detail_text_60), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }

                                            // 営業利益率
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_61), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(stringResource(R.string.annual_dividend_1_s , profile.operatingMargin.financialText(1, "%")), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.headline, fontFamily = FontFamily.Monospace)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(stringResource(R.string.stock_detail_text_62), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        // 3. FCF Row
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                                    Text(stringResource(R.string.stock_detail_text_63), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(12.dp))
                                                        Text(profile.fcfStatus, color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        // 4. 総合自動診断 Box
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                AdaptiveRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                        Text(profile.modelType, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = SurfaceContainer,
                                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                    ) {
                                                        Text(
                                                            stringResource(R.string.stock_detail_text_64 , if (profile.healthScore >= 0) "${profile.healthScore}/100" else resources.getString(R.string.stock_detail_label_30)),
                                                            color = PrimaryEmerald,
                                                            fontSize = AppTextSize.dense,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    stringResource(R.string.stock_detail_text_65 , profile.healthDiagnosisComment),
                                                    color = OnSurface.copy(alpha = 0.95f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (selectedStatusTab == "growth") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.MilitaryTech, null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                        Text(stringResource(R.string.stock_detail_text_66), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.section)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = PrimaryEmerald.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(PrimaryEmerald))
                                            Text(
                                                if (profile.consecutiveYears > 0) resources.getString(R.string.stock_detail_label_31, (if (profile.consecutiveYears >= 0) "${profile.consecutiveYears}期" else "未取得").toString()) else if (profile.consecutiveYears == 0) resources.getString(R.string.stock_detail_label_32) else resources.getString(R.string.stock_detail_label_33),
                                                color = PrimaryEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = BackgroundDark,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AdaptiveRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_67), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(stringResource(R.string.annual_dividend_1_s , if (profile.consecutiveYears >= 0) resources.getString(R.string.stock_detail_label_5, (profile.consecutiveYears).toString()) else resources.getString(R.string.not_available)), color = PrimaryEmerald, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.metric, fontFamily = FontFamily.Monospace)
                                                    Text(stringResource(R.string.stock_detail_text_68 , profile.growthRate.financialText(1, "%")), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_69), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(stringResource(R.string.annual_dividend_1_s , profile.noCutYears.financialYears()), color = OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.metric, fontFamily = FontFamily.Monospace)
                                                    Text(stringResource(R.string.stock_detail_text_70), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                                    Text(stringResource(R.string.stock_detail_text_71), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = PrimaryEmerald.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                                                ) {
                                                    Text(
                                                        if (profile.consecutiveYears > 0) resources.getString(R.string.stock_detail_label_34, (if (profile.consecutiveYears >= 0) "${profile.consecutiveYears}期" else "未取得").toString()) else if (profile.consecutiveYears == 0) resources.getString(R.string.stock_detail_label_32) else resources.getString(R.string.stock_detail_label_33),
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryEmerald.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        if (profile.consecutiveYears > 0) resources.getString(R.string.stock_detail_label_35, (if (profile.consecutiveYears >= 0) "${profile.consecutiveYears}期" else "未取得").toString()) else resources.getString(R.string.stock_detail_label_36),
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = AppTextSize.dense
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    stringResource(R.string.stock_detail_text_72 , profile.noCutYears.financialYears(), profile.growthRate.financialText(1, "%")),
                                                    color = OnSurface.copy(alpha = 0.95f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 買い増しシミュレーター Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.5.dp, PrimaryEmerald.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.stock_detail_text_12), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Surface(
                                shape = CircleShape,
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    stringResource(R.string.stock_detail_text_13 , simAddedShares, totalSimShares),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Comparison Impact Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceContainer,
                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.stock_detail_text_14), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Surface(shape = CircleShape, color = PrimaryEmerald) {
                                        Text(stringResource(R.string.stock_detail_text_15 , multiplier), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Black)
                                    }
                                }
                                Text(stringResource(R.string.add_holding_1_s_2_s_2 , if (taxRateMode == "net") resources.getString(R.string.stock_detail_label_15) else resources.getString(R.string.stock_detail_label_16), if (currentHolding.currency == "$") if (exchangeRate == null) resources.getString(R.string.stock_detail_label_17) else resources.getString(R.string.stock_detail_label_18, (exchangeRate!!.date).toString()) else ""), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                                Spacer(modifier = Modifier.height(8.dp))
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = BackgroundDark, modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(stringResource(R.string.stock_detail_text_16 , baseShares), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                            Text(stringResource(R.string.add_holding_text_11 , currencyFormat.format(currentStockAnnualDiv)), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                                            Text(stringResource(R.string.stock_detail_text_17 , currencyFormat.format(currentStockAnnualDiv / 12)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(10.dp), color = PrimaryEmerald.copy(alpha = 0.1f), border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(stringResource(R.string.stock_detail_text_18 , totalSimShares), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            Text(stringResource(R.string.add_holding_text_11 , currencyFormat.format(newTotalAnnualDiv)), fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                                            Text(stringResource(R.string.stock_detail_text_19 , currencyFormat.format(newMonthlyDiv)), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AdaptiveRow(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.stock_detail_text_20), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Column(horizontalAlignment = Alignment.End) {
                                    val nativeAmount = if (currentHolding.currency == "$") {
                                        String.format(Locale.US, "$%,.2f", simPurchaseAmount)
                                    } else {
                                        "¥${currencyFormat.format(simPurchaseAmount.toLong())}"
                                    }
                                    Text(nativeAmount, color = PrimaryEmerald, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    if (currentHolding.currency == "$") {
                                        val yenAmount = exchangeRate?.usdJpy?.takeIf { it.isFinite() && it > 0.0 }
                                            ?.let { "約¥${currencyFormat.format((simPurchaseAmount * it).toLong())}" }
                                        Text(yenAmount ?: resources.getString(R.string.stock_detail_label_19), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper row
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.annual_dividend_1_s , simAddedShares), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body, fontFamily = FontFamily.Monospace)
                                    Text(stringResource(R.string.asset_management_text_29), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { simAddedShares = (simAddedShares - 10).coerceAtLeast(0) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.stock_detail_text_21), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { simAddedShares = com.kurogane.dividend.model.PortfolioMath.addShares(simAddedShares, 10) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.add_holding_text_13), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { simAddedShares = com.kurogane.dividend.model.PortfolioMath.addShares(simAddedShares, 100) },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text(stringResource(R.string.add_holding_text_14), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { simAddedShares = 100 },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceContainer)
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = stringResource(R.string.stock_detail_a11y), tint = OnSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Presets
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0 to stringResource(R.string.stock_detail_label_6),
                                100 to stringResource(R.string.stock_detail_label_7),
                                250 to stringResource(R.string.stock_detail_label_8),
                                500 to stringResource(R.string.stock_detail_label_9)
                            ).forEach { (count, label) ->
                                val isSelected = simAddedShares == count
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryEmerald else SurfaceContainer,
                                    border = BorderStroke(0.5.dp, if (isSelected) PrimaryEmerald else BorderColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { simAddedShares = count }
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = if (isSelected) OnPrimary else OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                StockAnalysisNarrativeCard(analysisNarrative)
            }

            // 5. 次回権利落ち・直近イベント
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.stock_detail_text_73), fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body)
                            }
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text(if (isAlertOn && notificationAccess.dividendAllowed) stringResource(R.string.stock_detail_label_12) else stringResource(R.string.stock_detail_label_13), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(stringResource(R.string.stock_detail_text_74), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(stringResource(R.string.stock_detail_text_75), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(currentHolding.dividendMonths.filter { it in 1..12 }.distinct().sorted().joinToString(resources.getString(R.string.stock_detail_label_20)) { resources.getString(R.string.ex_dividend_matrix_dialog_text_4, (it).toString()) }.ifEmpty { resources.getString(R.string.not_available) }, color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AlarmOn, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.stock_detail_text_76), color = OnSurface, fontSize = AppTextSize.dense)
                            }
                            Switch(
                                checked = isAlertOn,
                                onCheckedChange = {
                                    if (it) notificationAccess.request("dividend")
                                    else viewModel.setNotificationEnabled(com.kurogane.dividend.notifications.NotificationKind.DIVIDEND, false)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = OnSurface, checkedTrackColor = PrimaryEmerald)
                            )
                        }
                    }
                }
            }

            // 6. Action buttons
            item {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showHoldingQuickEdit = true },
                        enabled = isOwnedHolding,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurface)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.stock_detail_text_77), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showDeleteHoldingConfirmation = true },
                        enabled = isOwnedHolding,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.stock_detail_text_78), color = ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    if (showHoldingQuickEdit && isOwnedHolding) {
        HoldingQuickEditModal(currentHolding, viewModel) { showHoldingQuickEdit = false }
    }

    if (showDeleteHoldingConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteHoldingConfirmation = false },
            title = {
                Text("保有銘柄を削除しますか？", color = OnSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("${currentHolding.name}（${currentHolding.code}）を保有銘柄から削除します。", color = OnSurfaceVariant)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteHoldingConfirmation = false
                        if (viewModel.removeHolding(currentHolding.id)) onBack()
                    }
                ) {
                    Text("削除", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHoldingConfirmation = false }) {
                    Text(stringResource(R.string.cancel), color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceContainer,
            titleContentColor = OnSurface,
            textContentColor = OnSurfaceVariant
        )
    }
}

@Composable
private fun StockAnalysisNarrativeCard(analysisNarrative: StockAnalysisNarrative) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BackgroundDark,
        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdaptiveRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stock_detail_text_79), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.stock_detail_text_80), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                if (analysisNarrative.score != null) {
                    Text(
                        stringResource(R.string.stock_detail_text_81, analysisNarrative.grade.toString(), analysisNarrative.score),
                        color = PrimaryEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
            }
            Text(
                stringResource(R.string.stock_detail_text_82 , analysisNarrative.title, analysisNarrative.findings.size),
                color = OnSurface,
                fontSize = AppTextSize.dense,
                lineHeight = 16.sp
            )
            Text(
                stringResource(R.string.stock_detail_text_83 , String.format(Locale.JAPAN, "%,d", StockAnalysisNarrative.TOTAL_COMBINATIONS), String.format(Locale.JAPAN, "%,d", analysisNarrative.possibleCombinations)),
                color = PrimaryEmerald,
                fontSize = AppTextSize.dense,
                lineHeight = 15.sp
            )
            Text(
                stringResource(R.string.stock_detail_text_84),
                color = OnSurfaceVariant,
                fontSize = AppTextSize.dense,
                lineHeight = 14.sp
            )
            if (analysisNarrative.complete && analysisNarrative.combinationIndex != null) {
                Text(
                    stringResource(R.string.stock_detail_text_85 , analysisNarrative.combinationIndex + 1, StockAnalysisNarrative.TOTAL_COMBINATIONS),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    stringResource(R.string.stock_detail_text_86 , analysisNarrative.missingLabels.joinToString("、")),
                    color = AccentAmber,
                    fontSize = AppTextSize.dense,
                    lineHeight = 15.sp
                )
            }
            HorizontalDivider(color = BorderColor)
            analysisNarrative.findings.forEach { finding ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.stock_detail_text_87 , finding.label, finding.formattedValue, finding.bandLabel, finding.position.label),
                        color = if (finding.score >= 65) PrimaryEmerald else AccentAmber,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(finding.text, color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 15.sp)
                }
            }
            Text(
                stringResource(R.string.stock_detail_text_88),
                color = OnSurfaceVariant,
                fontSize = AppTextSize.dense,
                lineHeight = 14.sp
            )
        }
    }
}
