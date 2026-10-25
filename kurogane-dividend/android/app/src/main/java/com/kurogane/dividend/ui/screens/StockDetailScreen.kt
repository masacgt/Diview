package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.DiviewSnackbarHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurogane.dividend.data.financialText
import com.kurogane.dividend.data.financialSignedText
import com.kurogane.dividend.data.financialYears
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import com.kurogane.dividend.R
import com.kurogane.dividend.ui.components.StatusMessage
import com.kurogane.dividend.ui.components.DiviewSnackbarHost
import com.kurogane.dividend.ui.components.DiviewSnackbarTone
import com.kurogane.dividend.ui.components.showDiviewSnackbar
import com.kurogane.dividend.ui.networkErrorMessage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.StockAnalysisNarrative
import com.kurogane.dividend.model.StockAnalysisNarrativeGenerator
import com.kurogane.dividend.viewmodel.DividendViewModel
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.data.DividendHistoryOverride
import com.kurogane.dividend.data.Tse33IndustryMaster
import java.text.NumberFormat
import java.util.Locale
import java.time.ZoneId
import kotlinx.coroutines.launch

private data class StockDetailDividendPeriod(
    val year: Int,
    val stockPrice: Double?,
    val annualDividend: Double?,
    val isForecast: Boolean = false
)

private data class DividendHistoryEditValue(
    val year: Int,
    val stockPrice: Double?,
    val annualDividend: Double?
)

private data class DividendHistoryEditDraft(
    val year: Int,
    val stockPriceInput: String,
    val annualDividendInput: String,
    val defaultStockPrice: Double?,
    val defaultAnnualDividend: Double?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {},
    onHoldingRemoved: (Holding) -> Unit = { onBack() },
    sharedSnackbarHostState: SnackbarHostState? = null
) {
    val resources = LocalContext.current.resources
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val shareholderBenefits by viewModel.shareholderBenefits.collectAsStateWithLifecycle()
    val stockClimateOverrides by viewModel.stockClimateOverrides.collectAsStateWithLifecycle()
    val dividendHistoryOverrides by viewModel.dividendHistoryOverrides.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    val stockAnalysisData by viewModel.stockAnalysisData.collectAsStateWithLifecycle()
    val cloudflareSecurityData by viewModel.cloudflareSecurityData.collectAsStateWithLifecycle()
    val exchangeRate by viewModel.exchangeRate.collectAsStateWithLifecycle()
    val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
    val localSnackbarHostState = remember { SnackbarHostState() }
    val snackbarHostState = sharedSnackbarHostState ?: localSnackbarHostState
    val snackbarScope = rememberCoroutineScope()
    var watchlistSnackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun showWatchlistSnackbar(message: String) {
        watchlistSnackbarJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()
        watchlistSnackbarJob = snackbarScope.launch {
            snackbarHostState.showDiviewSnackbar(
                message = message,
                tone = DiviewSnackbarTone.Success
            )
        }
    }
    
    var resolvedSecurity by remember(stockCode) { mutableStateOf<com.kurogane.dividend.data.CloudflareSecurity?>(null) }
    var lookupFinished by remember(stockCode) { mutableStateOf(false) }
    var lookupError by remember(stockCode) { mutableStateOf<String?>(null) }
    var lookupRetry by remember(stockCode) { mutableIntStateOf(0) }
    val normalizedSecurityCode = remember(stockCode) { com.kurogane.dividend.model.PortfolioMath.normalizeCode(stockCode).removeSuffix(".T") }
    val cachedSecurity = cloudflareSecurityData[normalizedSecurityCode]
    LaunchedEffect(stockCode, lookupRetry, cachedSecurity) {
        if (cachedSecurity != null) {
            resolvedSecurity = cachedSecurity
            lookupFinished = true
            lookupError = null
            com.kurogane.dividend.data.runCatchingCancellable {
                viewModel.resolveCloudflareSecurity(stockCode)
            }.getOrNull()?.let { resolvedSecurity = it }
            return@LaunchedEffect
        }
        lookupFinished = false
        lookupError = null
        val result = com.kurogane.dividend.data.runCatchingCancellable { viewModel.resolveCloudflareSecurity(stockCode) }
        resolvedSecurity = result.getOrNull()
        lookupError = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }
        lookupFinished = true
    }
    val currentHolding = com.kurogane.dividend.model.StockDetailResolver.resolve(stockCode, holdings, watchlist, resolvedSecurity)?.let { holding ->
        val price = resolvedSecurity?.marketPrice?.takeIf { it.isFinite() && it > 0.0 } ?: holding.price
        val securityHasDividend = resolvedSecurity?.annualDividendPerShare?.let { it > 0.0 } == true
        val noDividendConfirmed = !securityHasDividend &&
            (resolvedSecurity?.isNoDividendConfirmed == true || holding.isNoDividendConfirmed)
        val currentDps = resolvedSecurity?.annualDividendPerShare
        val dps = when {
            noDividendConfirmed -> 0.0
            currentDps != null -> currentDps
            financialData[holding.code]?.dividendHistoryBasis == "calendar_year_observed_distributions" -> 0.0
            lookupFinished && lookupError == null -> 0.0
            else -> holding.dps
        }
        val calculatedYield = com.kurogane.dividend.model.PortfolioMath.yieldPercent(dps, price)
        val irbankYieldDps = resolvedSecurity?.annualDividendPerShare
        val irbankYieldRate = resolvedSecurity?.marketYieldRate?.takeIf { dps == irbankYieldDps }
        val displayYield = irbankYieldRate ?: calculatedYield
        holding.copy(price = price, dps = dps, yieldRate = displayYield,
            isNoDividendConfirmed = noDividendConfirmed,
            irbankYieldRate = irbankYieldRate,
            irbankYieldDps = irbankYieldDps,
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

    LaunchedEffect(currentHolding.category, currentHolding.code) {
        viewModel.refreshDividendMonthsForSecurity(currentHolding.category, currentHolding.code)
    }

    val currentDpsUnavailable = lookupFinished && lookupError == null &&
        resolvedSecurity?.annualDividendPerShare == null && !currentHolding.isNoDividendConfirmed
    val uriHandler = LocalUriHandler.current
    val researchSecurityCode = currentHolding.code.trim().uppercase(Locale.ROOT).removeSuffix(".T")
    val yahooFinanceCode = if (currentHolding.code.trim().endsWith(".T", ignoreCase = true)) {
        currentHolding.code.trim()
    } else {
        "$researchSecurityCode.T"
    }

    var showDeleteHoldingConfirmation by remember(currentHolding.id) { mutableStateOf(false) }
    val isOwnedHolding = holdings.any { it.id == currentHolding.id }
    val isBookmarked = watchlist.any { it.code.equals(currentHolding.code, true) }
    val stockBenefits = shareholderBenefits[currentHolding.code].orEmpty()
    val defaultClimate = remember(currentHolding.sector) { Tse33IndustryMaster.defaultClimate(currentHolding.sector) }
    val selectedClimate = stockClimateOverrides[currentHolding.code] ?: defaultClimate
    val hasClimateOverride = stockClimateOverrides[currentHolding.code]?.let { it != defaultClimate } == true
    var climateMenuExpanded by remember(currentHolding.code) { mutableStateOf(false) }
    var climateSelectorWidthPx by remember(currentHolding.code) { mutableIntStateOf(0) }
    var showClimateInfo by remember(currentHolding.code) { mutableStateOf(false) }
    var showDoeInfo by remember(currentHolding.code) { mutableStateOf(false) }
    var showNetDeRatioInfo by remember(currentHolding.code) { mutableStateOf(false) }
    val density = LocalDensity.current
    var editingBenefitIndex by rememberSaveable(currentHolding.code) { mutableIntStateOf(-1) }
    var newBenefitName by rememberSaveable(currentHolding.code) { mutableStateOf("") }
    var newBenefitValue by rememberSaveable(currentHolding.code) { mutableStateOf("") }

    // Start the diagnosis details on health; retain the user's selection while this screen is saved.
    var selectedStatusTab by rememberSaveable(stockCode) { mutableStateOf("health") }
    var simAddedSharesInput by rememberSaveable(stockCode) { mutableStateOf("100") }
    var showBuyUpSimulator by rememberSaveable(stockCode) { mutableStateOf(false) }
    val simAddedShares = simAddedSharesInput.toIntOrNull() ?: 0
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val dividendEventRevision by viewModel.dividendEventRevision.collectAsStateWithLifecycle()
    val notificationAccess = com.kurogane.dividend.ui.components.rememberNotificationAccess {
        viewModel.setNotificationEnabled(com.kurogane.dividend.notifications.NotificationKind.DIVIDEND, true)
    }
    val isAlertOn = notificationSettings.dividendEnabled

    val baseShares = currentHolding.shares
    val currentPrice = currentHolding.price
    val dps = currentHolding.dps
    val financial = financialData[currentHolding.code]
    val fiscalMonth = financial?.period?.substringAfterLast('/')?.toIntOrNull()?.takeIf { it in 1..12 }
    val currentCalendarYear = java.time.Year.now(ZoneId.of("Asia/Tokyo")).value
    val currentCalendarDate = java.time.LocalDate.now(ZoneId.of("Asia/Tokyo"))
    val chartForecastYear = financial?.takeIf {
        it.forecastAnnualDividend != null &&
            it.forecastFiscalYear?.let { year -> year >= currentCalendarYear } == true
    }?.forecastFiscalYear ?: currentCalendarYear
    val dividendHistoryYears = (chartForecastYear - 7..chartForecastYear).toList()
    val chartDividendEvents = remember(currentHolding.code, dividendEventRevision) {
        viewModel.dividendEventsFor(currentHolding.code)
    }
    var selectedDividendYear by rememberSaveable(currentHolding.code) { mutableStateOf<Int?>(currentCalendarYear) }
    LaunchedEffect(currentHolding.code, currentCalendarYear) {
        if (selectedDividendYear == null) selectedDividendYear = currentCalendarYear
    }
    LaunchedEffect(currentHolding.code, selectedDividendYear) {
        selectedDividendYear?.let { year ->
            viewModel.refreshDividendEventsForYear(currentHolding.category, currentHolding.code, year)
        }
    }
    val selectedYearMonthlyDividends = remember(selectedDividendYear, chartDividendEvents) {
        selectedDividendYear?.let { year ->
            chartDividendEvents
                .filter { it.date.year == year }
                .groupBy { it.date.monthValue }
                .toSortedMap()
                .map { (month, events) -> month to events.sumOf { it.amountPerShare } }
        }
    }
    val chartUpcomingDividend = remember(currentHolding.code, dividendEventRevision) {
        viewModel.upcomingDividendEventFor(currentHolding.code)
    }
    val currentYearObservedDps = chartDividendEvents
        .filter { it.date.year == currentCalendarYear && !it.date.isAfter(currentCalendarDate) }
        .sumOf { it.amountPerShare }
    val lastObservedDividendAmount = chartDividendEvents
        .filter { !it.date.isAfter(currentCalendarDate) }
        .maxByOrNull { it.date }
        ?.amountPerShare
    val currentYearForecastDps = when {
        chartUpcomingDividend == null -> null
        chartUpcomingDividend.exDividendDate.year > currentCalendarYear -> currentYearObservedDps
        chartUpcomingDividend.exDividendDate.year == currentCalendarYear && lastObservedDividendAmount != null ->
            currentYearObservedDps + lastObservedDividendAmount
        else -> null
    }
    val normalizedDividendCode = currentHolding.code.trim().uppercase(Locale.ROOT).removeSuffix(".T")
    val savedDividendOverrides = dividendHistoryOverrides[normalizedDividendCode].orEmpty().associateBy { it.year }
    val issuerFiscalDividendsByYear = financial?.issuerDividends.orEmpty().groupBy { it.fiscalYear }.mapNotNull { (year, periods) ->
        // A yearly chart cannot choose or sum multiple issuer periods safely.
        periods.singleOrNull()?.displayDps?.let { year to it }
    }.toMap()
    val sourceDividendsByYear = if (financial?.issuerDividends.orEmpty().isNotEmpty()) issuerFiscalDividendsByYear
        else financial?.dividends.orEmpty().associate { it.year to it.dps }
    var yearEndPrices by remember(currentHolding.code) { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    LaunchedEffect(currentHolding.code, dividendHistoryYears) {
        yearEndPrices = com.kurogane.dividend.data.runCatchingCancellable {
            viewModel.loadMonthlyPriceHistory(
                listOf(normalizedDividendCode),
                dividendHistoryYears.filter { it < chartForecastYear }.map { "$it-12" }
            ).firstOrNull()?.prices.orEmpty().associate { price ->
                price.month.take(4).toInt() to price.close
            }
        }.getOrDefault(emptyMap())
    }
    val defaultDividendPeriods = dividendHistoryYears.map { year ->
        val isForecast = year == chartForecastYear
        val annualDividend = if (isForecast) {
            financial?.takeIf { it.forecastFiscalYear == year }?.forecastAnnualDividend
                ?: currentYearForecastDps
        } else {
            sourceDividendsByYear[year]
        }
        StockDetailDividendPeriod(
            year,
            if (isForecast) null else yearEndPrices[year],
            annualDividend,
            isForecast
        )
    }
    val dividendPeriods = defaultDividendPeriods.map { period ->
        val override = savedDividendOverrides[period.year]
        period.copy(
            stockPrice = if (override?.hasStockPriceOverride == true) override.stockPrice else period.stockPrice,
            annualDividend = if (override?.hasAnnualDividendOverride == true) override.annualDividend else period.annualDividend
        )
    }
    var showDividendHistoryEditor by rememberSaveable(currentHolding.code) { mutableStateOf(false) }
    var dividendHistoryDraft by remember(currentHolding.code) { mutableStateOf(emptyList<DividendHistoryEditDraft>()) }
    var dividendHistoryEditorDefaults by remember(currentHolding.code) { mutableStateOf(emptyList<StockDetailDividendPeriod>()) }
    var shareSplitHistory by remember(currentHolding.code) {
        mutableStateOf<com.kurogane.dividend.data.CloudflareShareSplitHistory?>(null)
    }
    var shareSplitHistoryLoading by remember(currentHolding.code) { mutableStateOf(false) }
    var shareSplitHistoryError by remember(currentHolding.code) { mutableStateOf<String?>(null) }
    var shareSplitHistoryRetry by remember(currentHolding.code) { mutableIntStateOf(0) }
    var handledShareSplitHistoryRetry by remember(currentHolding.code) { mutableIntStateOf(0) }
    LaunchedEffect(showDividendHistoryEditor, currentHolding.code, shareSplitHistoryRetry) {
        if (!showDividendHistoryEditor) return@LaunchedEffect
        val explicitlyRetried = shareSplitHistoryRetry != handledShareSplitHistoryRetry
        handledShareSplitHistoryRetry = shareSplitHistoryRetry
        shareSplitHistoryLoading = true
        shareSplitHistoryError = null
        try {
            val result = com.kurogane.dividend.data.runCatchingCancellable {
                viewModel.loadShareSplitHistory(
                    normalizedDividendCode,
                    java.time.LocalDate.of(dividendHistoryYears.first(), 1, 1),
                    java.time.LocalDate.of(dividendHistoryYears.last(), 12, 31),
                    force = explicitlyRetried
                )
            }
            shareSplitHistory = result.getOrNull() ?: shareSplitHistory
            shareSplitHistoryError = result.exceptionOrNull()?.let { error ->
                android.util.Log.w("CloudflareMarket", "Split history display failed (${error.javaClass.simpleName})")
                networkErrorMessage(resources, error)
            }
        } finally { shareSplitHistoryLoading = false }
    }
    val isCalendarYearDividendHistory = financial?.dividendHistoryBasis == "calendar_year_observed_distributions"
    val dividendShareBasisUnverified = financial?.dividendShareBasisStatus == "unverified_incomplete_corporate_action_history"
        || financial?.issuerDividendShareBasisStatus == "unverified_incomplete_corporate_action_history"
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
            val result = com.kurogane.dividend.data.runCatchingCancellable {
                viewModel.fetchFinancialData(currentHolding.code, force = financialRetry > 0)
            }
            financialError = result.isFailure
            financialFailure = result.exceptionOrNull()?.let { networkErrorMessage(resources, it) }
        } finally { financialLoading = false }
    }
    val backendAnalysisValues = stockAnalysisData[currentHolding.code.uppercase(Locale.ROOT).removeSuffix(".T")]?.values
    LaunchedEffect(currentHolding.code) { viewModel.fetchStockAnalysis(currentHolding.code) }
    val analysisNarrative = remember(currentHolding, backendAnalysisValues, financial) { StockAnalysisNarrativeGenerator.generate(
        (backendAnalysisValues ?: emptyMap()) + (financial?.values ?: emptyMap())
    ) }
    val healthNarrative = remember(financial) { StockAnalysisNarrativeGenerator.generate(financial?.values.orEmpty()) }
    val valuationValues = remember(backendAnalysisValues, financial) {
        ((backendAnalysisValues ?: emptyMap()) + (financial?.values ?: emptyMap())).filterKeys { it == "per" || it == "pbr" }
    }
    val valuationNarrative = remember(valuationValues) { StockAnalysisNarrativeGenerator.generate(valuationValues) }
    val missingValuationLabels = valuationNarrative.missingLabels.filter { it == "PER" || it == "PBR" }
    val profile = baseProfile.copy(
        healthGrade = healthNarrative.grade ?: "未判定", healthScore = healthNarrative.score ?: -1,
        healthEvaluation = healthNarrative.title, healthDiagnosisComment = healthNarrative.findings.joinToString(" ") { it.text }.ifBlank { "財務指標未取得" },
        valuationScore = valuationNarrative.score ?: -1,
        buySignalTitle = valuationNarrative.title,
        buySignalComment = valuationNarrative.findings.joinToString(" ") { it.text }.ifBlank { "株価指標未取得" }
    )
    val healthDiagnosisItems = healthNarrative.findings.map { it.text }.ifEmpty { listOf("財務指標未取得") }
    val buySignalDiagnosisItems = buildList {
        if (currentDpsUnavailable) {
            add("配当利回り：1株当たり配当（DPS）が未取得のため、現在利回りと過去実績の比較はできません。画面上の0.00%表示は未取得データによるもので、無配が確認された状態を意味しません。")
        } else if (profile.yieldRangeMin.isFinite() && profile.yieldRangeAvg.isFinite() && profile.yieldRangeMax.isFinite()) {
            val yieldPosition = when {
                currentHolding.yieldRate < profile.yieldRangeMin -> "過去の最低水準を下回っています"
                currentHolding.yieldRate > profile.yieldRangeMax -> "過去の最高水準を上回っています"
                currentHolding.yieldRate >= profile.yieldRangeAvg -> "過去平均以上です"
                else -> "過去平均を下回っています"
            }
            add("配当利回り：現在${com.kurogane.dividend.model.formatDividendYieldDisplay(currentHolding.yieldRate, currentHolding.isNoDividendConfirmed)}、取得できた直近最大5期の過去実績は最低${profile.yieldRangeMin.financialText(2, "%")}・平均${profile.yieldRangeAvg.financialText(2, "%")}・最高${profile.yieldRangeMax.financialText(2, "%")}です。$yieldPosition。利回りは株価と予想配当の両方で変動し、高い利回りだけで割安や配当継続が確定するわけではありません。")
        } else {
            add("配当利回り：現在値は${com.kurogane.dividend.model.formatDividendYieldDisplay(currentHolding.yieldRate, currentHolding.isNoDividendConfirmed)}ですが、過去の比較可能な利回り実績が不足しているため、過去平均との差や水準の位置は判定できません。履歴不足は利回り0%や無配確定を意味しません。利回りは株価と予想配当の両方で変動するため、配当の継続性も合わせて確認します。")
        }
        valuationNarrative.findings.forEach { add(it.text) }
        if (missingValuationLabels.isNotEmpty()) {
            add("株価指標の取得状況：${missingValuationLabels.joinToString("・")}は未取得です。評価コメントとスコアは取得できたPER・PBRだけを対象としており、未取得値は補完していません。")
        }
        add(if (valuationNarrative.score != null) {
            "スコアの見方：表示スコアは取得できたPER・PBRの評価をまとめたものです。配当利回りや増配実績、財務の健全性は含まれないため、各指標の診断と合わせて判断してください。"
        } else {
            "スコアの見方：PER・PBRが取得できないため、株価指標のスコアは算出できません。配当利回りも履歴と照らして確認し、データがそろってから評価してください。"
        })
    }
    val growthDurabilityDiagnosisItems = listOf(
        when {
            profile.consecutiveYears > 0 -> "連続増配実績：取得データ上、${profile.consecutiveYears}期連続で1株当たり配当が増えています。過去に増配を続けた実績は還元姿勢を見る材料ですが、増配率と利益・キャッシュの伸びが釣り合っているかも重要です。この記録だけで今後の増配や配当維持が保証されるものではありません。"
            profile.consecutiveYears == 0 -> "連続増配実績：直近の連続増配は確認できません。これは増配が続いていないことを示し、無配や減配があったと直ちに断定するものではありません。配当額の推移と減配しなかった期間を分けて確認します。"
            else -> "連続増配実績：増配年数のデータが未取得のため、連続増配の有無は判定できません。未取得を増配年数0年として扱わず、配当履歴や利益・キャッシュの推移と合わせて確認してください。"
        },
        when {
            profile.noCutYears > 0 -> "減配しなかった期間：取得データでは${profile.noCutYears}年間、配当を減らしていません。横ばいの期間も含み得るため、連続増配年数とは別の指標です。過去の記録は参考になりますが、将来の配当を保証するものではありません。"
            profile.noCutYears == 0 -> "減配しなかった期間：直近の連続記録は確認できません。連続増配の有無とは別に、どの時期に配当が変化したかを履歴で確認すると、還元の安定度を把握しやすくなります。"
            else -> "減配しなかった期間：比較できる配当履歴が不足しており、期間を判定できません。未取得は減配記録が0年という意味ではありません。履歴が確認できる範囲を連続増配年数と区別して見ます。"
        },
        if (profile.growthRate.isFinite()) {
            val growthDirection = when {
                profile.growthRate > 0.0 -> "増配傾向"
                profile.growthRate < 0.0 -> "減配傾向"
                else -> "横ばい"
            }
            val basis = if (isCalendarYearDividendHistory) "暦年配当合計" else "年間配当額"
            val basisNote = if (isCalendarYearDividendHistory) "暦年中に観測された配当の合計による参考値で、決算期ごとの年間配当額とは集計期間が異なる場合があります。" else ""
            "配当成長率：${basis}を使った直近最大5年の年平均成長率（CAGR）は${profile.growthRate.financialText(1, "%")}で、期間の始点から終点まででは${growthDirection}です。CAGRは期間全体を年率換算した値で、毎年同じ率で増減したことを示すものではありません。$basisNote"
        } else {
            "配当成長率：連続する複数期の比較に必要なデータが不足しているため、算出できません。これは成長率0%を意味しません。配当額の履歴がそろうと、期間全体の増減を年率で比較できます。"
        },
        when {
            !profile.payoutRatio.isFinite() -> "配当性向：利益に対する配当負担のデータが未取得のため判定できません。未取得を0%とみなすことはできず、利益と配当の実績がそろってから評価できます。"
            profile.payoutRatio > 100.0 -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}で、当期利益を上回る水準です。利益の一時的な落ち込みや特別要因、利益以外の資金による配当の有無を決算で確認してください。1期の比率だけでは、配当原資や継続性までは判断できません。"
            profile.payoutRatio > 70.0 -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}と高めで、利益の多くを配当に充てています。利益が減少すると配当を維持する余裕が縮む可能性があるため、利益の安定性と複数期の推移を確認してください。"
            profile.payoutRatio >= 0.0 -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}です。当期利益のうち配当に回した割合を示し、残りがそのまま現金として残ることを意味するわけではありません。利益の変動、投資や負債返済も含めて配当余力を見ます。"
            else -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}です。負の値は赤字や計算元の特殊要因を含む可能性があり、通常の配当負担として単純比較できません。利益の内訳と配当原資を決算で確認してください。"
        },
        "財務面の支え：自己資本比率${profile.equityRatio.financialText(1, "%")}は資本構成、営業利益率${profile.operatingMargin.financialText(1, "%")}は本業の採算、${profile.fcfStatus}は営業活動と投資後の現金収支を見る材料です。指標が未取得の場合はその項目を評価できず、黒字や高い比率だけで配当継続が確定するわけではありません。複数期の推移と配当額を合わせて確認してください。"
    )
    val liveYield = currentHolding.liveYield
    val liveYoc = if (currentHolding.acqPrice > 0) (dps / currentHolding.acqPrice) * 100.0 else liveYield

    val totalSimShares = baseShares.toLong() + simAddedShares
    val simPurchaseAmount = simAddedShares * currentHolding.price / currentHolding.priceUnit
    val newTotalAnnualDiv = currentHolding.getEffectiveDividendForShares(totalSimShares, taxRateMode).toLong()
    val displayNewTotalAnnualDiv = currentHolding.getEffectiveDividendForShares(totalSimShares, taxRateMode)
    val newMonthlyDiv = newTotalAnnualDiv / 12
    val displayNewMonthlyDiv = displayNewTotalAnnualDiv / 12.0
    val multiplier = if (baseShares > 0) String.format(java.util.Locale.JAPAN, "%.1f", totalSimShares.toFloat() / baseShares) else "1.0"

    val cutImpact = (currentHolding.getEffectiveAnnualDividend(taxRateMode) / 2).toLong()
    val displayCutImpact = currentHolding.getEffectiveAnnualDividend(taxRateMode) / 2.0

    val totalPortfolioDiv = if (currentDpsUnavailable) 0L else
        holdings.sumOf { it.getEffectiveAnnualDividend(taxRateMode).toLong() }
    val currentStockAnnualDiv = currentHolding.getEffectiveAnnualDividend(taxRateMode).toLong()
    val divDependency = if (totalPortfolioDiv > 0) (currentStockAnnualDiv.toDouble() / totalPortfolioDiv.toDouble()) * 100.0 else 0.0
    val divDependencyStr = if (currentDpsUnavailable) "未取得" else String.format(java.util.Locale.JAPAN, "%.1f", divDependency)

    val totalPortfolioVal = holdings.sumOf { it.totalValuation.toLong() }
    val sectorVal = holdings.filter { it.sector == currentHolding.sector }.sumOf { it.totalValuation.toLong() }
    val sectorDependency = if (totalPortfolioVal > 0) (sectorVal.toDouble() / totalPortfolioVal.toDouble()) * 100.0 else 0.0
    val sectorDependencyStr = String.format(java.util.Locale.JAPAN, "%.1f", sectorDependency)
    val cleanSectorName = currentHolding.sector.replace(Regex("^.*[•·]\\s*"), "")
    val sectorRiskText = if (sectorDependency > 30.0) "高集中" else if (sectorDependency > 20.0) "やや集中" else "適正分散"

    val isHighRisk = !currentDpsUnavailable && divDependency > 10.0
    val riskBadgeText = when {
        currentDpsUnavailable -> "配当データ未取得"
        divDependency > 25.0 -> "高リスク・要分散"
        divDependency > 10.0 -> "やや高め"
        else -> "適正範囲"
    }
    val cutImpactRatio = if (totalPortfolioDiv > 0) cutImpact.toDouble() / totalPortfolioDiv * 100.0 else 0.0
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val detailListState = rememberLazyListState()
    val riskDiagnosisItems = listOf(
        if (currentDpsUnavailable) {
            "銘柄配当依存度：${currentHolding.name}のDPSが未取得のため、保有銘柄の年間配当合計に占める割合は判定できません。未取得を依存度0%とはみなさず、配当データの確認後に比較します。"
        } else if (totalPortfolioDiv > 0) {
            val stockRisk = when {
                divDependency > 25.0 -> "目安の10%を大きく上回り、1銘柄への配当依存が強い状態です"
                divDependency > 10.0 -> "目安の10%を上回っており、配当源の偏りに注意が必要です"
                else -> "目安の10%以内で、配当源の偏りは比較的小さい状態です"
            }
            "銘柄配当依存度：税引設定に応じた保有銘柄の取得済み年間配当合計のうち、約${divDependencyStr}%を${currentHolding.name}が占めています。$stockRisk。比率が高いほど、この銘柄の減配がポートフォリオ全体の受取配当に与える影響も大きくなります。"
        } else {
            "銘柄配当依存度：保有銘柄の年間配当合計に正の値がないため、構成比を算出できません。配当データ未取得とは限らず、分母がない状態を依存度0%や低リスクとは解釈できません。"
        },
        if (totalPortfolioVal > 0) {
            val concentrationGuide = when {
                sectorDependency > 30.0 -> "30%を超えており、高集中の目安に該当します"
                sectorDependency > 20.0 -> "20%を超えており、やや集中の目安に該当します"
                else -> "20%以内で、分散された範囲の目安です"
            }
            "セクター集中度：保有資産の評価額合計に占める${cleanSectorName}の割合は約${sectorDependencyStr}%で、${sectorRiskText}です。${concentrationGuide}。これは配当額ではなく評価額で測った偏りであり、株価変動に応じて比率も変わります。"
        } else {
            "セクター集中度：保有資産の評価額合計に正の値がないため、構成比を算出できません。評価額比率が未取得または算出不能な状態を、集中度0%や分散済みとは判定できません。"
        },
        if (currentDpsUnavailable) {
            "減配時の影響：${currentHolding.name}のDPSが未取得のため、減配による受取配当の変化を試算できません。未取得は無配確定ではなく、取得後に保有数量と選択中の税引設定を反映して再評価できます。"
        } else if (totalPortfolioDiv > 0) {
            "減配時の影響：${currentHolding.name}の年間配当が50%減ると仮定した場合、選択中の税引設定で年間受取配当は約¥${com.kurogane.dividend.model.formatFinancialAmountDisplay(displayCutImpact)}（現在のポートフォリオ年間配当合計の約${String.format(Locale.JAPAN, "%.1f", cutImpactRatio)}%）減る試算です。保有数量と現在の配当額を固定した単純計算であり、減配の予測ではありません。"
        } else {
            "減配時の影響：保有銘柄の年間配当合計に正の値がないため、全体に占める影響割合を算出できません。分母がない状態のため、影響0%や減配リスクなしとは解釈できません。"
        },
        when {
            currentDpsUnavailable -> if (totalPortfolioVal > 0) {
                "見直しの目安：${currentHolding.name}の配当依存度はDPS未取得のため保留です。評価額で測るセクター比率は約${sectorDependencyStr}%（20%超でやや集中、30%超で高集中の目安）なので、配当データとは分けて確認してください。"
            } else {
                "見直しの目安：DPS未取得のため銘柄配当依存度は保留で、評価額合計にも正の値がなくセクター集中度も算出できません。データがそろうまでは集中リスクを低いとは判定できません。"
            }
            totalPortfolioDiv <= 0 && totalPortfolioVal <= 0 -> "見直しの目安：年間配当と評価額のどちらも構成比を算出する分母がありません。集中度を低リスクと判断せず、保有データがそろった後に銘柄別・セクター別の比率を確認してください。"
            totalPortfolioDiv <= 0 -> "見直しの目安：年間配当合計に正の値がないため銘柄配当依存度は判定できません。評価額ベースの${cleanSectorName}比率は約${sectorDependencyStr}%です（20%超でやや集中、30%超で高集中の目安）。"
            totalPortfolioVal <= 0 -> "見直しの目安：評価額合計に正の値がないためセクター集中度は判定できません。年間配当ベースでは${currentHolding.name}が約${divDependencyStr}%を占めています（10%超で偏りに注意、25%超で強い集中の目安）。"
            divDependency > 25.0 || sectorDependency > 30.0 -> "見直しの目安：銘柄配当比率が25%超またはセクター評価額比率が30%超の集中目安に該当します。追加投資先を他銘柄・他セクターへ分けると、特定の配当源や値動きへの依存を抑えられます。"
            divDependency > 10.0 || sectorDependency > 20.0 -> "見直しの目安：銘柄配当比率が10%超またはセクター評価額比率が20%超の注意目安に該当します。今後の追加投資先を分散し、比率の変化を継続して確認してください。"
            else -> "見直しの目安：計算できた銘柄配当比率は10%以内、セクター評価額比率は20%以内で、いずれも集中目安を下回っています。比率は保有額や株価で変わるため、定期的に確認してください。"
        }
    )
    val growthStatusBadge = when {
        profile.consecutiveYears > 0 -> resources.getString(R.string.stock_detail_label_34, "${profile.consecutiveYears}期")
        profile.consecutiveYears == 0 -> resources.getString(R.string.stock_detail_label_32)
        else -> resources.getString(R.string.stock_detail_label_33)
    }
    val growthDiagnosisTitle = if (profile.consecutiveYears > 0) {
        resources.getString(R.string.stock_detail_label_35, "${profile.consecutiveYears}期")
    } else {
        resources.getString(R.string.stock_detail_label_36)
    }

    Scaffold(
        snackbarHost = { DiviewSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            state = detailListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentHolding.category == com.kurogane.dividend.model.Category.JAPAN) item {
                when {
                    financialLoading -> StatusMessage(resources.getString(R.string.data_loading))
                    financialError -> StatusMessage(financialFailure ?: resources.getString(R.string.financial_update_failed),
                        color = ErrorRed, actionLabel = resources.getString(R.string.retry), onAction = { financialRetry++ })
                    financial == null -> StatusMessage(resources.getString(R.string.financial_empty),
                        actionLabel = resources.getString(R.string.retry), onAction = { financialRetry++ })
                }
                if (financial?.issuerDividends.orEmpty().groupBy { it.fiscalYear }.any { it.value.size > 1 }) {
                    StatusMessage("同一年に複数の決算期がある配当実績は、年単位のグラフでは未取得として表示します。")
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isOwnedHolding) {
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
                                        } else {
                                            Surface(
                                                modifier = Modifier.clip(CircleShape).clickable {
                                                    onOpenAddHolding(currentHolding.code)
                                                },
                                                shape = CircleShape,
                                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                                border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    stringResource(R.string.stock_detail_add_holding_badge),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    color = PrimaryEmerald,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    fiscalMonth?.let { month ->
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(4.dp), color = BackgroundDark, border = BorderStroke(1.dp, BorderColor)) {
                                            Text("${month}月決算", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isBookmarked) viewModel.removeWatchlistStock(currentHolding.code)
                                    else viewModel.addHoldingToWatchlist(currentHolding)
                                    showWatchlistSnackbar(if (isBookmarked) "Watchlistから解除しました" else "Watchlistに登録しました")
                                },
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
                                    stringResource(R.string.add_holding_1_s_2_s_2 , currentHolding.currency, com.kurogane.dividend.model.formatStockPriceDisplay(currentHolding.price, currentHolding.code)),
                                    fontSize = AppTextSize.largeMetric,
                                    fontWeight = FontWeight.Black,
                                    color = OnSurface,
                                    fontFamily = AppNumericFontFamily
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentHolding.isPositive) StatusSuccess.copy(alpha = 0.14f) else ErrorRed.copy(alpha = 0.14f),
                                border = BorderStroke(0.5.dp, if (currentHolding.isPositive) StatusSuccess.copy(alpha = 0.45f) else ErrorRed.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    currentHolding.dailyChange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = if (currentHolding.isPositive) StatusSuccess else ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        resolvedSecurity?.dataRetrieval?.companyInfo?.let { companyInfo ->
                            val failed = companyInfo.refreshPending
                                || companyInfo.status == "failed"
                                || companyInfo.lastAttemptStatus == "failed"
                            val lastFetched = when {
                                companyInfo.fetchDateBasis == "last_successful_sync_fallback" ->
                                    companyInfo.lastFetchedSessionDate
                                        ?.let { "取得日時未記録（最終同期 $it）" }
                                        ?: "取得日時未記録"
                                companyInfo.lastFetchedSessionDate != null ->
                                    "最終取得 ${companyInfo.lastFetchedSessionDate}"
                                else -> "未取得"
                            }
                            Text(
                                if (failed) "会社情報・指標: $lastFetched（更新失敗・再取得予定）"
                                else "会社情報・指標: $lastFetched",
                                color = if (failed) ErrorRed else OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
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
                                border = BorderStroke(0.5.dp, BorderBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(if (currentHolding.category == com.kurogane.dividend.model.Category.FUND) stringResource(R.string.stock_detail_label) else stringResource(R.string.stock_detail_label_2), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(
                                        if (currentDpsUnavailable) "未取得" else stringResource(
                                            R.string.add_holding_1_s_2_s_2,
                                            currentHolding.currency,
                                            com.kurogane.dividend.model.formatFinancialAmountDisplay(currentHolding.dps)
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        fontSize = AppTextSize.caption,
                                        fontFamily = AppNumericFontFamily
                                    )
                                    Text(stringResource(R.string.annual_dividend_text_44), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                border = BorderStroke(0.5.dp, BorderBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.stock_detail_text_4), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(
                                        com.kurogane.dividend.model.formatDividendYieldDisplay(liveYield, currentHolding.isNoDividendConfirmed),
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.caption,
                                        fontFamily = AppNumericFontFamily
                                    )
                                    Text(stringResource(R.string.stock_detail_text_5), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceContainer,
                                border = BorderStroke(0.5.dp, BorderBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(stringResource(R.string.stock_detail_text_6), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(
                                        if (currentDpsUnavailable) "未取得" else com.kurogane.dividend.model.formatDividendYieldDisplay(currentHolding.liveYoc, currentHolding.isNoDividendConfirmed),
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.caption,
                                        fontFamily = AppNumericFontFamily
                                    )
                                    Text(stringResource(R.string.stock_detail_text_7, currentHolding.currency, com.kurogane.dividend.model.formatStockPriceDisplay(currentHolding.acqPrice, currentHolding.code)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                            }
                        }

                        if (currentHolding.category == com.kurogane.dividend.model.Category.JAPAN) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BorderColor)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1.5f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = PrimaryEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "外部リサーチ:",
                                        modifier = Modifier.weight(1f),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense * 0.8f,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                ExternalResearchLink(
                                    label = "Y! ファイナンス",
                                    indicatorColor = ErrorRed,
                                    modifier = Modifier.weight(2f)
                                ) {
                                    uriHandler.openUri("https://finance.yahoo.co.jp/quote/$yahooFinanceCode")
                                }
                                ExternalResearchLink(
                                    label = "IR BANK",
                                    indicatorColor = PrimaryEmerald,
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    uriHandler.openUri("https://irbank.net/$researchSecurityCode")
                                }
                            }
                        }
                    }
                }
            }

            // 2. 1株配当金の推移 (最大8期) Bar Chart Card
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
                                IconButton(
                                    onClick = {
                                        dividendHistoryEditorDefaults = defaultDividendPeriods
                                        dividendHistoryDraft = dividendPeriods.map { period ->
                                            val defaults = defaultDividendPeriods.firstOrNull { it.year == period.year }
                                            DividendHistoryEditDraft(
                                                year = period.year,
                                                stockPriceInput = period.stockPrice.toEditableStockPrice(currentHolding.code),
                                                annualDividendInput = period.annualDividend.toEditableAmount(),
                                                defaultStockPrice = defaults?.stockPrice,
                                                defaultAnnualDividend = defaults?.annualDividend
                                            )
                                        }
                                        showDividendHistoryEditor = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "配当金の推移を編集",
                                        tint = PrimaryEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text(if (profile.consecutiveYears > 0) stringResource(R.string.stock_detail_label_3) else stringResource(R.string.stock_detail_label_4), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (dividendShareBasisUnverified) {
                            Text(
                                "株式分割・併合履歴が不完全なため、過去の1株配当額の基準は未確認です。",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4 Bars Container
                        val chartFontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                        val chartHeight = (132f + 80f * (chartFontScale - 1f).coerceAtLeast(0f)).dp
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth().height(chartHeight)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val history = dividendPeriods
                                val maxDps = history.maxOfOrNull { it.annualDividend ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
                                val prefix = currentHolding.currency
                                val bars = history.map { point ->
                                    val dps = point.annualDividend ?: 0.0
                                    val dpsText = point.annualDividend
                                        ?.let { com.kurogane.dividend.model.formatFinancialAmountDisplay(it) }
                                        ?: "0"
                                    val fiscalPeriodLabel = "${point.year}期"
                                    Triple(
                                        fiscalPeriodLabel,
                                        stringResource(R.string.add_holding_1_s_2_s_2, prefix, dpsText),
                                        (dps / maxDps * 0.95).toFloat()
                                    )
                                }
                                var barsVisible by remember(stockCode) { mutableStateOf(false) }
                                LaunchedEffect(stockCode, bars.isNotEmpty()) { if (bars.isNotEmpty()) barsVisible = true }
                                bars.forEachIndexed { index, (term, dpsVal, ratio) ->
                                    val animatedRatio by animateFloatAsState(
                                        targetValue = if (barsVisible) ratio.coerceIn(0f, 0.95f) else 0f,
                                        animationSpec = tween(durationMillis = 560, delayMillis = index * 30),
                                        label = "Dividend bar height"
                                    )
                                    val isProjected = history[index].isForecast || term == bars.lastOrNull()?.first
                                    val isSelected = selectedDividendYear == history[index].year
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(onClickLabel = "${term}の配当内訳を表示") {
                                                selectedDividendYear = history[index].year
                                            }
                                    ) {
                                        BoxWithConstraints(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            val barHeight = maxHeight * animatedRatio.coerceIn(0f, 0.95f) * 0.52f
                                            if (history[index].isForecast) {
                                                Text(
                                                    "予想",
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .offset(y = -(barHeight + 30.dp))
                                                        .fillMaxWidth(),
                                                    color = PrimaryEmerald,
                                                    fontSize = AppTextSize.dense * 0.85f,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            Text(
                                                dpsVal,
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .offset(y = -(barHeight + 15.dp))
                                                    .fillMaxWidth(),
                                                color = if (isProjected) PrimaryEmerald else OnSurfaceVariant,
                                                fontSize = if (dpsVal.length >= 6) AppTextSize.dense * 0.8f else AppTextSize.dense,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = AppNumericFontFamily,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(26.dp)
                                                    .height(barHeight)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(
                                                        if (isProjected) Brush.verticalGradient(listOf(PrimaryEmerald, PrimaryEmeraldContainer))
                                                        else Brush.verticalGradient(listOf(ChartBlue, ChartBlue.copy(alpha = 0.78f)))
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            term,
                                            color = if (isSelected || isProjected) PrimaryEmerald else OnSurfaceVariant,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        selectedDividendYear?.let { year ->
                            val selectedForecastDps = dividendPeriods
                                .firstOrNull { it.year == year && it.isForecast }
                                ?.annualDividend
                            val monthlyAmounts = selectedYearMonthlyDividends.orEmpty()
                                .joinToString("　") { (month, amount) ->
                                    "${month}月${currentHolding.currency}${com.kurogane.dividend.model.formatFinancialAmountDisplay(amount)}/株"
                                }
                            val selectedYearAmount = if (selectedForecastDps != null) {
                                "予想${currentHolding.currency}${com.kurogane.dividend.model.formatFinancialAmountDisplay(selectedForecastDps)}/株"
                            } else {
                                monthlyAmounts.ifBlank { "配当履歴なし" }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${year}年の配当イベント",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    selectedYearAmount,
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1.35f),
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                                Spacer(modifier = Modifier.width(2.dp))
                                StockAnalysisDisclaimerInfo()
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
                                    val healthBadgeColor = when (profile.healthGrade) {
                                        "S", "A", "B" -> PrimaryEmerald
                                        "C", "D" -> AccentAmber
                                        else -> OnSurfaceVariant
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.stock_detail_health_grade, profile.healthGrade), modifier = Modifier.weight(1f), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Surface(shape = RoundedCornerShape(50), color = healthBadgeColor.copy(alpha = 0.14f), border = BorderStroke(1.dp, healthBadgeColor.copy(alpha = 0.35f))) {
                                            Text(profile.healthEvaluation, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = healthBadgeColor, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text("予想配当性向 ${financial?.forecastPayoutRatio?.financialText(1, "%") ?: "未取得"}", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
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
                                    Text(
                                        stringResource(
                                            R.string.stock_detail_text_26,
                                            com.kurogane.dividend.model.formatDividendYieldDisplay(liveYield, currentHolding.isNoDividendConfirmed)
                                        ),
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.body
                                    )
                                    Text(
                                        if (currentDpsUnavailable) "配当データ未取得のため判定できません" else profile.getValuationStatus(currentHolding.price, currentHolding.dps).split(resources.getString(R.string.stock_detail_label_20)).first(),
                                        color = if (currentDpsUnavailable) OnSurfaceVariant else PrimaryEmerald,
                                        fontSize = AppTextSize.dense
                                    )
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
                                    val growthSummary = when {
                                        isCalendarYearDividendHistory -> stringResource(R.string.stock_detail_calendar_growth_cagr, profile.growthRate.financialSignedText(1, "%"))
                                        profile.consecutiveYears > 0 -> stringResource(R.string.stock_detail_label_10, resources.getString(R.string.stock_detail_label_5, profile.consecutiveYears.toString()), profile.growthRate.financialText(1, "%"))
                                        else -> stringResource(R.string.stock_detail_label_11, profile.growthRate.financialText(1, "%"))
                                    }
                                    Text(growthSummary, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
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
                                    Text(
                                        if (currentDpsUnavailable) "配当依存度：未取得" else stringResource(
                                            R.string.stock_detail_1_s_2_s,
                                            if (isHighRisk) resources.getString(R.string.stock_detail_label_21) else resources.getString(R.string.stock_detail_label_22),
                                            divDependencyStr
                                        ),
                                        color = AccentAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppTextSize.body
                                    )
                                    Text(
                                        if (currentDpsUnavailable) "配当情報未取得のため判定できません" else stringResource(R.string.stock_detail_text_29),
                                        color = AccentAmber,
                                        fontSize = AppTextSize.dense
                                    )
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
                                                color = if (currentDpsUnavailable) SurfaceContainerHigh else if (isHighRisk) AccentAmber.copy(alpha = 0.15f) else PrimaryEmerald.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    if (currentDpsUnavailable) "未取得" else if (isHighRisk) resources.getString(R.string.stock_detail_label_23) else resources.getString(R.string.stock_detail_label_24),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    color = if (currentDpsUnavailable) OnSurfaceVariant else if (isHighRisk) AccentAmber else PrimaryEmerald,
                                                    fontSize = AppTextSize.dense,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            if (currentDpsUnavailable) "未取得" else stringResource(R.string.settings_1_s, divDependencyStr),
                                            color = if (currentDpsUnavailable) OnSurfaceVariant else if (isHighRisk) AccentAmber else PrimaryEmerald,
                                            fontSize = AppTextSize.metric,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = AppNumericFontFamily
                                        )
                                    }

                                            Spacer(modifier = Modifier.height(8.dp))

                                    if (currentDpsUnavailable) {
                                        Text("配当データ未取得のため算出できません", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    } else {
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
                                    }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            AdaptiveRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                        Text(stringResource(R.string.stock_detail_text_32), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text(stringResource(R.string.stock_detail_text_33), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text(
                                            if (currentDpsUnavailable) "配当依存度：未取得" else stringResource(R.string.stock_detail_text_34, currentHolding.name, divDependencyStr),
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
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
                                                Text(stringResource(R.string.settings_1_s , sectorDependencyStr), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                Text(
                                                    if (currentDpsUnavailable) "未取得" else stringResource(
                                                        R.string.stock_detail_1_s,
                                                        com.kurogane.dividend.model.formatFinancialAmountDisplay(displayCutImpact)
                                                    ),
                                                    color = if (currentDpsUnavailable) OnSurfaceVariant else ErrorRed,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = AppTextSize.metric,
                                                    fontFamily = AppNumericFontFamily
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    if (currentDpsUnavailable) "配当情報未取得のため試算できません" else stringResource(
                                                        R.string.stock_detail_text_38,
                                                        if (cutImpact > 8000) resources.getString(R.string.stock_detail_label_25) else if (cutImpact > 3000) resources.getString(R.string.stock_detail_label_26) else resources.getString(R.string.stock_detail_label_27)
                                                    ),
                                                    color = OnSurfaceVariant,
                                                    fontSize = AppTextSize.dense
                                                )
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
                                        color = AccentAmber.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        stringResource(R.string.stock_detail_text_28),
                                                        color = AccentAmber,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = AppTextSize.dense,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = AccentAmber.copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        riskBadgeText,
                                                        color = AccentAmber,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("診断コメント", color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            riskDiagnosisItems.forEachIndexed { index, item ->
                                                Text(
                                                    "${index + 1}. $item",
                                                    color = OnSurface.copy(alpha = 0.92f),
                                                    fontSize = AppTextSize.dense,
                                                    lineHeight = 18.sp
                                                )
                                            }
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
                                                        com.kurogane.dividend.model.formatDividendYieldDisplay(
                                                            currentHolding.yieldRate,
                                                            currentHolding.isNoDividendConfirmed
                                                        ),
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = AppTextSize.metric,
                                                        fontFamily = AppNumericFontFamily
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                if (currentDpsUnavailable) {
                                                    Text("配当情報未取得のため、過去の利回り範囲と比較できません", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                } else {
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
                                                                        listOf(ChartPurple, SkyBlue, PrimaryEmerald)
                                                                    )
                                                                )
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    AdaptiveRow(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(stringResource(R.string.stock_detail_text_43, profile.yieldRangeMin.financialText(2)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                        Text(stringResource(R.string.stock_detail_text_44, profile.yieldRangeAvg.financialText(2)), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                        Text(stringResource(R.string.stock_detail_text_45, profile.yieldRangeMax.financialText(2)), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                                                    }
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
                                                    Text(profile.getPer(currentHolding.price), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                    Text(profile.getPbr(currentHolding.price), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                        Text(
                                                            profile.buySignalTitle,
                                                            color = PrimaryEmerald,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = AppTextSize.dense,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }
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
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("診断コメント", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                buySignalDiagnosisItems.forEachIndexed { index, item ->
                                                    Text(
                                                        "${index + 1}. $item",
                                                        color = OnSurface.copy(alpha = 0.95f),
                                                        fontSize = AppTextSize.dense,
                                                        lineHeight = 18.sp
                                                    )
                                                }
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
                                                                if (financial?.forecastPayoutRatio != null) "予想" else "予想未取得",
                                                                color = PrimaryEmerald,
                                                                fontSize = AppTextSize.dense,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        financial?.forecastPayoutRatio?.financialText(1, "%") ?: "未取得",
                                                        color = PrimaryEmerald,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = AppTextSize.metric,
                                                        fontFamily = AppNumericFontFamily
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
                                                    Text(stringResource(R.string.annual_dividend_1_s , profile.equityRatio.financialText(1, "%")), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                    StockDetailMetricTitleWithInfo(
                                                        title = stringResource(R.string.stock_detail_text_58),
                                                        contentDescription = stringResource(R.string.stock_detail_doe_info_content_description),
                                                        description = stringResource(R.string.stock_detail_doe_info_description),
                                                        expanded = showDoeInfo,
                                                        onExpandedChange = { showDoeInfo = it }
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.calculatedDoe, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(profile.calculatedDoeSub, color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
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
                                                    StockDetailMetricTitleWithInfo(
                                                        title = stringResource(R.string.stock_detail_text_59),
                                                        contentDescription = stringResource(R.string.stock_detail_net_de_ratio_info_content_description),
                                                        description = stringResource(R.string.stock_detail_net_de_ratio_info_description),
                                                        expanded = showNetDeRatioInfo,
                                                        onExpandedChange = { showNetDeRatioInfo = it }
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(profile.netDeRatio, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                    Text(stringResource(R.string.annual_dividend_1_s , profile.operatingMargin.financialText(1, "%")), color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                        Text(
                                                            profile.modelType,
                                                            color = PrimaryEmerald,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = AppTextSize.dense,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
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
                                                Text("診断コメント", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                healthDiagnosisItems.forEachIndexed { index, item ->
                                                    Text(
                                                        "${index + 1}. $item",
                                                        color = OnSurface.copy(alpha = 0.95f),
                                                        fontSize = AppTextSize.dense,
                                                        lineHeight = 18.sp
                                                    )
                                                }
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
                                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_67), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(stringResource(R.string.annual_dividend_1_s , if (profile.consecutiveYears >= 0) resources.getString(R.string.stock_detail_label_5, (profile.consecutiveYears).toString()) else resources.getString(R.string.not_available)), color = PrimaryEmerald, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
                                                    Text(
                                                        if (isCalendarYearDividendHistory) stringResource(R.string.stock_detail_calendar_growth_cagr_detail, profile.growthRate.financialSignedText(1, "%"))
                                                        else stringResource(R.string.stock_detail_text_68, profile.growthRate.financialText(1, "%")),
                                                        color = PrimaryEmerald,
                                                        fontSize = AppTextSize.dense,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = SurfaceContainer,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text(stringResource(R.string.stock_detail_text_69), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(stringResource(R.string.annual_dividend_1_s , profile.noCutYears.financialYears()), color = OnSurface, fontWeight = FontWeight.ExtraBold, fontSize = AppTextSize.metric, fontFamily = AppNumericFontFamily)
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
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(15.dp))
                                                        Text(
                                                            growthDiagnosisTitle,
                                                            color = PrimaryEmerald,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = AppTextSize.dense,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = PrimaryEmerald.copy(alpha = 0.14f),
                                                        border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                                                    ) {
                                                        Text(
                                                            growthStatusBadge,
                                                            color = PrimaryEmerald,
                                                            fontSize = AppTextSize.dense,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text("診断コメント", color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                                growthDurabilityDiagnosisItems.forEachIndexed { index, item ->
                                                    Text(
                                                        "${index + 1}. $item",
                                                        color = OnSurface.copy(alpha = 0.95f),
                                                        fontSize = AppTextSize.dense,
                                                        lineHeight = 18.sp
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
            }



            item {
                StockAnalysisNarrativeCard(analysisNarrative)
            }

            // 5. 配当月・通知設定
            if (!currentHolding.isNoDividendConfirmed) item {
                val dividendEvents = remember(currentHolding.code, dividendEventRevision) {
                    viewModel.dividendEventsFor(currentHolding.code)
                }
                val nextDividendEvent = dividendEvents.firstOrNull { !it.date.isBefore(java.time.LocalDate.now()) }
                val upcomingDividendEvent = remember(currentHolding.code, dividendEventRevision) {
                    viewModel.upcomingDividendEventFor(currentHolding.code)
                }
                val estimatedFromHistory = upcomingDividendEvent?.estimatedFromHistory == true
                val nextExDividendDate = upcomingDividendEvent?.exDividendDate ?: nextDividendEvent?.date
                val finalRightsDate = upcomingDividendEvent?.finalRightsDate ?: nextDividendEvent?.finalRightsDate
                val recordDate = upcomingDividendEvent?.recordDate ?: nextDividendEvent?.recordDate
                val dateFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPAN)
                val nextExDividendText = nextExDividendDate?.format(dateFormat) ?: "権利落ち日未取得"
                val estimatedAmount = currentHolding.shares * currentHolding.dps / currentHolding.priceUnit * currentHolding.currencyFactor
                val finalRightsDateText = finalRightsDate?.format(dateFormat) ?: "未取得"
                val recordDateText = recordDate?.format(dateFormat) ?: "未取得"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                                        .background(PrimaryEmerald.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("次回配当金 & 権利確定スケジュール", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = AppTextSize.body, lineHeight = 20.sp)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = AccentAmber.copy(alpha = 0.12f)) {
                                Text(
                                    currentHolding.dividendType.ifBlank { "配当予定" },
                                    modifier = Modifier.widthIn(max = 76.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = AccentAmber,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark.copy(alpha = 0.8f),
                            border = BorderStroke(0.5.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (estimatedFromHistory) "次回権利落ち日（履歴推定）" else "次回権利落ち日",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                        Text(nextExDividendText, color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (currentDpsUnavailable) "予想受取額（未取得）" else "予想受取額（1株 ${currentHolding.currency}${com.kurogane.dividend.model.formatFinancialAmountDisplay(currentHolding.dps)}）",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                        Text(
                                            if (currentDpsUnavailable) "未取得" else "${currentHolding.currency}${com.kurogane.dividend.model.formatFinancialAmountDisplay(estimatedAmount)}",
                                            color = if (currentDpsUnavailable) OnSurfaceVariant else PrimaryEmerald,
                                            fontSize = AppTextSize.metric,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            when {
                                                estimatedFromHistory -> "過去の配当イベント日から推定"
                                                upcomingDividendEvent != null || nextDividendEvent != null -> "権利日程取得済み"
                                                else -> "配当月情報に基づく目安"
                                            },
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense
                                        )
                                    }
                                }
                                Text(
                                    "配当月：" + currentHolding.dividendMonths.filter { it in 1..12 }.distinct().sorted()
                                        .joinToString("・") { "${it}月" }.ifEmpty { resources.getString(R.string.not_available) },
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceContainer,
                            border = BorderStroke(0.5.dp, BorderBlue)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                listOf(
                                    (if (estimatedFromHistory) "権利付き最終日（計算）" else "権利付き最終日") to finalRightsDateText,
                                    (if (estimatedFromHistory) "権利落ち日（履歴推定）" else "権利落ち日") to nextExDividendText,
                                    (if (estimatedFromHistory) "権利確定日（計算）" else "権利確定日（推定）") to recordDateText
                                ).forEachIndexed { index, (label, value) ->
                                    if (index > 0) HorizontalDivider(color = BorderColor.copy(alpha = 0.55f))
                                    AdaptiveRow(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(label, color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                        Text(value, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))

                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AlarmOn, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("現在の配当月リマインダー", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Text("保有銘柄共通", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
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

            item {
                val displayedBenefits = stockBenefits.filter { it.name.isNotBlank() || it.marketValue.isNotBlank() }
                val benefitAnnualValue = displayedBenefits.sumOf { it.marketValue.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
                val positionValue = currentHolding.totalValuation
                val benefitYield = if (positionValue > 0.0) benefitAnnualValue / positionValue * 100.0 else null
                val combinedAnnualValue = currentHolding.getEffectiveAnnualDividend(taxRateMode) + benefitAnnualValue
                val combinedYield = if (!currentDpsUnavailable && positionValue > 0.0) combinedAnnualValue / positionValue * 100.0 else null
                val yenFormat = NumberFormat.getIntegerInstance(Locale.JAPAN)
                val hasSavedBenefit = displayedBenefits.isNotEmpty()
                val climateDescription = when (selectedClimate) {
                    StockClimate.CYCLICAL -> "景気拡大期に業績・株価が大きく伸長する特性を持ちます。インフレ・金利動向に注視したポートフォリオ管理が有効です。"
                    StockClimate.DEFENSIVE -> "景気後退局面でも需要が比較的安定し、業績や株価の変動が小さい傾向があります。"
                    StockClimate.NEUTRAL -> "景気による業績への影響は中程度です。企業固有の成長性や収益動向も確認しましょう。"
                    null -> "業種の初期分類がありません。景気特性を選択して登録できます。"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                    .background(PrimaryEmerald.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryEmerald)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("銘柄属性・特典設定", color = OnSurface, fontSize = AppTextSize.heading, fontWeight = FontWeight.Bold)
                                Text(
                                    "景気サイクル耐性・株主優待の記録と利回り換算",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = PrimaryEmerald.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(PrimaryEmerald))
                                    Text(
                                        "${selectedClimate?.label ?: "景気特性未設定"} / ${if (hasSavedBenefit) "優待あり" else "優待未登録"}",
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))

                        if (currentHolding.category == com.kurogane.dividend.model.Category.JAPAN) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.65f))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("景気特性", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (hasClimateOverride) PrimaryEmerald.copy(alpha = 0.14f) else OnSurfaceVariant.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                if (hasClimateOverride) "手動" else "自動",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                color = if (hasClimateOverride) PrimaryEmerald else OnSurfaceVariant,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { showClimateInfo = true }, modifier = Modifier.size(28.dp)) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = stringResource(R.string.stock_climate_info_content_description),
                                                    tint = OnSurfaceVariant,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showClimateInfo,
                                                onDismissRequest = { showClimateInfo = false },
                                                modifier = Modifier.widthIn(max = 300.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                containerColor = SurfaceContainerLow,
                                                tonalElevation = 0.dp,
                                                shadowElevation = 12.dp
                                            ) {
                                                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = PrimaryEmerald.copy(alpha = 0.14f),
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(17.dp))
                                                            }
                                                        }
                                                        Spacer(Modifier.width(10.dp))
                                                        Text(
                                                            "景気特性：${selectedClimate?.label ?: "未設定"}",
                                                            color = OnSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = AppTextSize.body
                                                        )
                                                    }
                                                    Spacer(Modifier.height(12.dp))
                                                    HorizontalDivider(color = BorderColor, thickness = 0.75.dp)
                                                    Spacer(Modifier.height(12.dp))
                                                    Text(climateDescription, color = OnSurfaceVariant, fontSize = AppTextSize.caption, lineHeight = 20.sp)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (hasClimateOverride) {
                                            Text(
                                                "業種の初期分類に戻す",
                                                modifier = Modifier
                                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                                        viewModel.setStockClimateOverride(currentHolding.code, null)
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.dense,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                "${cleanSectorName}の標準分類",
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.caption,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .onGloballyPositioned { climateSelectorWidthPx = it.size.width }
                                    ) {
                                        OutlinedButton(
                                            onClick = { climateMenuExpanded = true },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 52.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceContainerLowest)
                                        ) {
                                            Text(
                                                selectedClimate?.label ?: "選択してください",
                                                modifier = Modifier.weight(1f),
                                                color = PrimaryEmerald,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "景気特性を選択", tint = OnSurfaceVariant)
                                        }
                                        DropdownMenu(
                                            expanded = climateMenuExpanded,
                                            onDismissRequest = { climateMenuExpanded = false },
                                            modifier = Modifier.width(
                                                if (climateSelectorWidthPx > 0) {
                                                    with(density) { climateSelectorWidthPx.toDp() }
                                                } else {
                                                    240.dp
                                                }
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            containerColor = SurfaceContainerLow,
                                            tonalElevation = 3.dp,
                                            shadowElevation = 8.dp,
                                            border = BorderStroke(1.dp, BorderColor)
                                        ) {
                                            StockClimate.entries.forEach { climate ->
                                                val isSelected = selectedClimate == climate
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.size(8.dp).clip(CircleShape)
                                                                    .background(if (isSelected) PrimaryEmerald else BorderColor)
                                                            )
                                                            Text(
                                                                climate.label,
                                                                color = if (isSelected) PrimaryEmerald else OnSurface,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                            )
                                                            Spacer(Modifier.weight(1f))
                                                            if (isSelected) {
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = "選択中",
                                                                    tint = PrimaryEmerald,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setStockClimateOverride(
                                                            currentHolding.code,
                                                            climate.takeUnless { it == defaultClimate }
                                                        )
                                                        climateMenuExpanded = false
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) PrimaryEmerald.copy(alpha = 0.1f) else Color.Transparent),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.65f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("株主優待", color = OnSurface, fontSize = AppTextSize.section, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "優待利回り ${benefitYield?.let { "+${String.format(Locale.JAPAN, "%.2f", it)}%" } ?: "—"}",
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.caption,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("優待の名前と相場金額を記録できます。", color = OnSurfaceVariant, fontSize = AppTextSize.caption)

                                displayedBenefits.forEachIndexed { index, benefit ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceContainerLowest)
                                            .border(1.dp, PrimaryEmerald.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                            .clickable { editingBenefitIndex = if (editingBenefitIndex == index) -1 else index }
                                    .padding(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                                .background(PrimaryEmerald.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(benefit.name.ifBlank { "優待名未設定" }, color = OnSurface, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text("年間相当額", color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                                        }
                                        Text(
                                            "¥${benefit.marketValue.toLongOrNull()?.let { yenFormat.format(it) } ?: "0"}",
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.body,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.setShareholderBenefits(
                                                    currentHolding.code,
                                                    displayedBenefits.filterIndexed { itemIndex, _ -> itemIndex != index }
                                                )
                                                editingBenefitIndex = -1
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "${benefit.name.ifBlank { "株主優待" }}を削除", tint = OnSurfaceVariant)
                                        }
                                    }
                                    if (editingBenefitIndex == index) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = benefit.name,
                                                onValueChange = { value ->
                                                    val updated = displayedBenefits.toMutableList()
                                                    updated[index] = benefit.copy(name = value)
                                                    viewModel.setShareholderBenefits(currentHolding.code, updated)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = { Text("優待名", fontSize = AppTextSize.body) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            OutlinedTextField(
                                                value = benefit.marketValue,
                                                onValueChange = { value ->
                                                    val updated = displayedBenefits.toMutableList()
                                                    updated[index] = benefit.copy(marketValue = value.filter(Char::isDigit))
                                                    viewModel.setShareholderBenefits(currentHolding.code, updated)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = { Text("相場金額（円）", fontSize = AppTextSize.body) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = newBenefitName,
                                    onValueChange = { newBenefitName = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    placeholder = { Text("優待名", fontSize = AppTextSize.body) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                OutlinedTextField(
                                    value = newBenefitValue,
                                    onValueChange = { newBenefitValue = it.filter(Char::isDigit) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    placeholder = { Text("相場金額（円）", fontSize = AppTextSize.body) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                Button(
                                    onClick = {
                                        viewModel.setShareholderBenefits(
                                            currentHolding.code,
                                            displayedBenefits + ShareholderBenefit(newBenefitName.trim(), newBenefitValue)
                                        )
                                        newBenefitName = ""
                                        newBenefitValue = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = newBenefitName.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = PrimaryEmerald),
                                    border = BorderStroke(0.5.dp, BorderBlue)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("優待を追加", fontWeight = FontWeight.Bold)
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                                    border = BorderStroke(0.5.dp, BorderBlue)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Percent, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("配当＋優待 合計実質利回り ", color = OnSurface, fontSize = AppTextSize.dense)
                                                Text(
                                                    combinedYield?.let { "${String.format(Locale.JAPAN, "%.2f", it)}%" } ?: if (currentDpsUnavailable) "未取得" else "—",
                                                    color = PrimaryEmerald,
                                                    fontSize = AppTextSize.metric,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                if (currentDpsUnavailable) "配当データ未取得のため合計未取得" else "年間 ¥${yenFormat.format(combinedAnnualValue.coerceAtLeast(0.0).toLong())} 相当",
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                }
                            }
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
                        onClick = { onOpenAddHolding(currentHolding.code) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, BorderBlue)
                    ) {
                        Icon(
                            imageVector = if (isOwnedHolding) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = OnSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOwnedHolding) stringResource(R.string.stock_detail_text_77) else stringResource(R.string.stock_detail_text_89),
                            color = OnSurface,
                            fontSize = AppTextSize.caption,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { showDeleteHoldingConfirmation = true },
                        enabled = isOwnedHolding,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, BorderBlue)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.stock_detail_text_78), color = ErrorRed, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Button(
                    onClick = { showBuyUpSimulator = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, tint = OnPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stock_detail_text_12), color = OnPrimary, fontWeight = FontWeight.Bold)
                }
            }


            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    if (showBuyUpSimulator) {
        ModalBottomSheet(
            onDismissRequest = { showBuyUpSimulator = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceContainerLow,
            contentColor = OnSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
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
                                    Surface(shape = CircleShape, color = if (currentDpsUnavailable) SurfaceContainerHigh else PrimaryEmerald) {
                                        Text(
                                            if (currentDpsUnavailable) "配当 未取得" else stringResource(R.string.stock_detail_text_15, multiplier),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = if (currentDpsUnavailable) OnSurfaceVariant else OnPrimary,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Black
                                        )
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
                                            Text(
                                                if (currentDpsUnavailable) "未取得" else stringResource(
                                                    R.string.add_holding_text_11,
                                                    com.kurogane.dividend.model.formatFinancialAmountDisplay(currentHolding.getEffectiveAnnualDividend(taxRateMode))
                                                ),
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentDpsUnavailable) OnSurfaceVariant else OnSurface,
                                                fontSize = AppTextSize.body,
                                                fontFamily = AppNumericFontFamily
                                            )
                                            Text(
                                                if (currentDpsUnavailable) "配当情報未取得" else stringResource(
                                                    R.string.stock_detail_text_17,
                                                    com.kurogane.dividend.model.formatFinancialAmountDisplay(currentHolding.getEffectiveAnnualDividend(taxRateMode) / 12.0)
                                                ),
                                                color = OnSurfaceVariant,
                                                fontSize = AppTextSize.dense
                                            )
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(10.dp), color = PrimaryEmerald.copy(alpha = 0.1f), border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(stringResource(R.string.stock_detail_text_18 , totalSimShares), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            Text(
                                                if (currentDpsUnavailable) "未取得" else stringResource(
                                                    R.string.add_holding_text_11,
                                                    com.kurogane.dividend.model.formatFinancialAmountDisplay(displayNewTotalAnnualDiv)
                                                ),
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentDpsUnavailable) OnSurfaceVariant else PrimaryEmerald,
                                                fontSize = AppTextSize.body,
                                                fontFamily = AppNumericFontFamily
                                            )
                                            Text(
                                                if (currentDpsUnavailable) "配当情報未取得" else stringResource(
                                                    R.string.stock_detail_text_19,
                                                    com.kurogane.dividend.model.formatFinancialAmountDisplay(displayNewMonthlyDiv)
                                                ),
                                                color = if (currentDpsUnavailable) OnSurfaceVariant else PrimaryEmerald,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold
                                            )
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
                                    Text(nativeAmount, color = PrimaryEmerald, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold, fontFamily = AppNumericFontFamily)
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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = simAddedSharesInput,
                                        onValueChange = { input ->
                                            simAddedSharesInput = input.filter(Char::isDigit).take(9)
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.body,
                                            fontFamily = AppNumericFontFamily,
                                            fontFeatureSettings = LocalTextStyle.current.fontFeatureSettings
                                        ),
                                        cursorBrush = SolidColor(PrimaryEmerald),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    Text(stringResource(R.string.asset_management_text_29), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { simAddedSharesInput = (simAddedShares - 100).coerceAtLeast(0).toString() },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("-100", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { simAddedSharesInput = com.kurogane.dividend.model.PortfolioMath.addShares(simAddedShares, 100).toString() },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("+100", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { simAddedSharesInput = "100" },
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
                                500 to stringResource(R.string.stock_detail_label_8),
                                1000 to stringResource(R.string.stock_detail_label_9)
                            ).forEach { (count, label) ->
                                val isSelected = simAddedShares == count
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryEmerald else SurfaceContainer,
                                    border = BorderStroke(0.5.dp, if (isSelected) PrimaryEmerald else BorderColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { simAddedSharesInput = count.toString() }
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
        }
    }

    if (showDividendHistoryEditor) {
        DividendHistoryEditorSheet(
            currency = currentHolding.currency,
            draftRows = dividendHistoryDraft,
            onDraftRowsChange = { dividendHistoryDraft = it },
            onReset = {
                dividendHistoryDraft = dividendHistoryEditorDefaults.map { period ->
                    DividendHistoryEditDraft(
                        year = period.year,
                        stockPriceInput = period.stockPrice.toEditableStockPrice(currentHolding.code),
                        annualDividendInput = period.annualDividend.toEditableAmount(),
                        defaultStockPrice = period.stockPrice,
                        defaultAnnualDividend = period.annualDividend
                    )
                }
            },
            splitHistory = shareSplitHistory,
            splitHistoryLoading = shareSplitHistoryLoading,
            splitHistoryError = shareSplitHistoryError,
            onRetrySplitHistory = { shareSplitHistoryRetry++ },
            onDismiss = { showDividendHistoryEditor = false },
            onSave = { editedRows ->
                val defaultsByYear = dividendHistoryEditorDefaults.associateBy { it.year }
                val overrides = editedRows.mapNotNull { edited ->
                    val defaults = defaultsByYear[edited.year] ?: return@mapNotNull null
                    val editedPrice = edited.stockPrice
                    val editedDividend = edited.annualDividend
                    val priceChanged = !sameEditableAmount(editedPrice, defaults.stockPrice)
                    val dividendChanged = !sameEditableAmount(editedDividend, defaults.annualDividend)
                    if (!priceChanged && !dividendChanged) null else DividendHistoryOverride(
                        year = edited.year,
                        stockPrice = editedPrice.takeIf { priceChanged },
                        annualDividend = editedDividend.takeIf { dividendChanged },
                        hasStockPriceOverride = priceChanged,
                        hasAnnualDividendOverride = dividendChanged
                    )
                }
                viewModel.saveDividendHistoryOverrides(normalizedDividendCode, overrides)
                showDividendHistoryEditor = false
            }
        )
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
                        if (viewModel.removeHolding(currentHolding.id)) onHoldingRemoved(currentHolding)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DividendHistoryEditorSheet(
    currency: String,
    draftRows: List<DividendHistoryEditDraft>,
    onDraftRowsChange: (List<DividendHistoryEditDraft>) -> Unit,
    onReset: () -> Unit,
    splitHistory: com.kurogane.dividend.data.CloudflareShareSplitHistory?,
    splitHistoryLoading: Boolean,
    splitHistoryError: String?,
    onRetrySplitHistory: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<DividendHistoryEditValue>) -> Unit
) {
    val allInputsValid = draftRows.all { row ->
        (row.stockPriceInput.isBlank() || row.stockPriceInput.toNullableEditableAmount()?.let { it > 0.0 } == true) &&
            (row.annualDividendInput.isBlank() || row.annualDividendInput.toNullableEditableAmount() != null)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = SurfaceContainerLow,
        contentColor = OnSurface,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceContainerHighest)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceContainer)
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(19.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("配当金の推移を編集", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                    Text("過去8期の株価と年間配当金", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "閉じる", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)

            Text(
                "株価は各年12月の最終営業日終値です。空欄は未入力として扱います。",
                color = OnSurfaceVariant,
                fontSize = AppTextSize.dense,
                lineHeight = 15.sp
            )

            when {
                splitHistoryLoading -> Text("分割・併合情報を取得中です。", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                splitHistoryError != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (splitHistory?.events?.isNotEmpty() == true) "分割・併合情報を再取得できません。保存済み履歴を表示しています。"
                        else "分割・併合情報を取得できません。",
                        color = ErrorRed,
                        fontSize = AppTextSize.dense,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetrySplitHistory, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("再試行", color = PrimaryEmerald)
                    }
                }
                splitHistory?.events?.isNotEmpty() == true -> Text(
                    if (splitHistory?.historyStatus == "failed") "保存済みの分割・併合履歴を表示しています。最新データの取得は失敗しています。"
                    else "分割・併合イベントは該当する年の行に表示しています。",
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    lineHeight = 15.sp
                )
                splitHistory?.historyStatus == "not_fetched" || splitHistory == null -> Text(
                    "分割・併合履歴は未取得です。",
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense
                )
                splitHistory.historyStatus == "failed" -> Text(
                    "最新の取得に失敗したため、分割・併合の有無を確認できません。",
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    lineHeight = 15.sp
                )
                else -> Text("この期間の分割・併合イベントはありません。", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("年度", modifier = Modifier.width(46.dp), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                Text("株価（$currency）", modifier = Modifier.weight(1f), color = OnSurfaceVariant, fontSize = AppTextSize.caption, maxLines = 1)
                Text("年間配当金（1株）", modifier = Modifier.weight(1f), color = OnSurfaceVariant, fontSize = AppTextSize.caption, maxLines = 1)
            }

            Column(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                draftRows.forEachIndexed { index, row ->
                    val yearEvents = splitHistory?.events.orEmpty().filter { it.date.year == row.year }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${row.year}年",
                                modifier = Modifier.width(46.dp),
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = AppTextSize.dense,
                                maxLines = 1
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                DividendHistoryCompactInput(
                                    value = row.stockPriceInput,
                                    onValueChange = { value -> onDraftRowsChange(draftRows.toMutableList().also {
                                        it[index] = row.copy(stockPriceInput = value)
                                    }) },
                                    contentDescription = "${row.year}年の株価（$currency）"
                                )
                                val edited = isEditableValueModified(row.stockPriceInput, row.defaultStockPrice)
                                Text(
                                    if (edited) "ユーザー編集" else "初期値",
                                    color = if (edited) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.chartLabel,
                                    lineHeight = 10.sp,
                                    maxLines = 1
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                DividendHistoryCompactInput(
                                    value = row.annualDividendInput,
                                    onValueChange = { value -> onDraftRowsChange(draftRows.toMutableList().also {
                                        it[index] = row.copy(annualDividendInput = value)
                                    }) },
                                    contentDescription = "${row.year}年の年間配当金（1株）"
                                )
                                val edited = isEditableValueModified(row.annualDividendInput, row.defaultAnnualDividend)
                                Text(
                                    if (edited) "ユーザー編集" else "初期値",
                                    color = if (edited) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.chartLabel,
                                    lineHeight = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        yearEvents.forEach { event ->
                            Text(
                                formatShareSplitEvent(event),
                                modifier = Modifier.padding(start = 52.dp, top = 1.dp, bottom = 2.dp),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.caption,
                                lineHeight = 12.sp,
                                maxLines = 1
                            )
                        }
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.55f), thickness = 0.5.dp)
                    }
                }
            }

            if (!allInputsValid) {
                Text(
                    "株価は0より大きく、年間配当金は0以上の数値を入力してください。",
                    color = ErrorRed,
                    fontSize = AppTextSize.caption,
                    lineHeight = 13.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("初期値に戻す", color = PrimaryEmerald, fontSize = AppTextSize.dense)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("キャンセル", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                }
                Button(
                    enabled = allInputsValid,
                    onClick = {
                        onSave(draftRows.map { row ->
                            DividendHistoryEditValue(
                                year = row.year,
                                stockPrice = row.stockPriceInput.toNullableEditableAmount(),
                                annualDividend = row.annualDividendInput.toNullableEditableAmount()
                            )
                        })
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, contentColor = OnPrimary),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text("保存", fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense)
                }
            }
        }
    }
}

@Composable
private fun DividendHistoryCompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainer)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription },
            singleLine = true,
            textStyle = TextStyle(color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(PrimaryEmerald),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { innerTextField ->
                if (value.isBlank()) Text("未入力", color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1)
                innerTextField()
            }
        )
    }
}

private fun formatShareSplitEvent(event: com.kurogane.dividend.data.CloudflareShareSplitEvent): String {
    val formatter = NumberFormat.getNumberInstance(Locale.JAPAN).apply {
        maximumFractionDigits = 4
        minimumFractionDigits = 0
    }
    return if (event.ratio > 1.0) {
        "${event.date} 株式分割（比率 ${formatter.format(event.ratio)}:1）"
    } else {
        "${event.date} 株式併合（比率 1:${formatter.format(1.0 / event.ratio)}）"
    }
}

private fun isEditableValueModified(input: String, defaultValue: Double?): Boolean {
    if (input.isBlank()) return defaultValue != null
    val value = input.toNullableEditableAmount() ?: return true
    return !sameEditableAmount(value, defaultValue)
}

private fun Double?.toEditableAmount(): String = this?.takeIf { it.isFinite() }?.let {
    java.math.BigDecimal.valueOf(it)
        .setScale(1, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}.orEmpty()

private fun Double?.toEditableStockPrice(code: String): String = this?.takeIf { it.isFinite() }?.let {
    com.kurogane.dividend.model.formatStockPriceDisplay(it, code).replace(",", "")
}.orEmpty()

private fun String.toNullableEditableAmount(): Double? {
    if (isBlank()) return null
    return replace(",", "").trim().toDoubleOrNull()?.takeIf {
        it.isFinite() && it >= 0.0 && com.kurogane.dividend.model.PortfolioMath.validMoney(it)
    }
}

private fun sameEditableAmount(left: Double?, right: Double?): Boolean = when {
    left == null || right == null -> left == right
    else -> java.math.BigDecimal.valueOf(left).setScale(1, java.math.RoundingMode.HALF_UP)
        .compareTo(java.math.BigDecimal.valueOf(right).setScale(1, java.math.RoundingMode.HALF_UP)) == 0
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.stock_detail_text_79), color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(2.dp))
                        StockAnalysisDisclaimerInfo()
                    }
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

@Composable
private fun ExternalResearchLink(
    label: String,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(BackgroundDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(indicatorColor))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = OnSurface,
            fontSize = AppTextSize.dense * 0.8f,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun StockAnalysisDisclaimerInfo() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.stock_detail_analysis_info_content_description),
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = CardDark,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryEmerald.copy(alpha = 0.14f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(17.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.stock_detail_analysis_info_title),
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderBlue, thickness = 0.75.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.stock_detail_analysis_info_description),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.caption,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun StockDetailMetricTitleWithInfo(
    title: String,
    contentDescription: String,
    description: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f, fill = false),
            color = OnSurfaceVariant,
            fontSize = AppTextSize.dense,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Box {
            IconButton(onClick = { onExpandedChange(true) }, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = contentDescription,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = SurfaceContainerLow,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryEmerald.copy(alpha = 0.14f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(17.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(title, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = AppTextSize.body)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = BorderColor, thickness = 0.75.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(description, color = OnSurfaceVariant, fontSize = AppTextSize.caption, lineHeight = 20.sp)
                }
            }
        }
    }
}
