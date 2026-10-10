package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurogane.dividend.data.financialText
import com.kurogane.dividend.data.financialYears
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.model.StockAnalysisNarrative
import com.kurogane.dividend.model.StockAnalysisNarrativeGenerator
import com.kurogane.dividend.viewmodel.DividendViewModel
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.data.Tse33IndustryMaster
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {},
    onOpenAddHolding: (String) -> Unit = {}
) {
    val resources = LocalContext.current.resources
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val shareholderBenefits by viewModel.shareholderBenefits.collectAsStateWithLifecycle()
    val stockClimateOverrides by viewModel.stockClimateOverrides.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val financialData by viewModel.financialData.collectAsStateWithLifecycle()
    val exchangeRate by viewModel.exchangeRate.collectAsStateWithLifecycle()
    val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    
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

    var showDeleteHoldingConfirmation by remember(currentHolding.id) { mutableStateOf(false) }
    val isOwnedHolding = holdings.any { it.id == currentHolding.id }
    val isBookmarked = watchlist.any { it.code.equals(currentHolding.code, true) }
    val stockBenefits = shareholderBenefits[currentHolding.code].orEmpty()
    val defaultClimate = remember(currentHolding.sector) { Tse33IndustryMaster.defaultClimate(currentHolding.sector) }
    val selectedClimate = stockClimateOverrides[currentHolding.code] ?: defaultClimate
    var climateMenuExpanded by remember(currentHolding.code) { mutableStateOf(false) }
    var climateSelectorWidthPx by remember(currentHolding.code) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var editingBenefitIndex by rememberSaveable(currentHolding.code) { mutableIntStateOf(-1) }
    var newBenefitName by rememberSaveable(currentHolding.code) { mutableStateOf("") }
    var newBenefitValue by rememberSaveable(currentHolding.code) { mutableStateOf("") }

    // Selected status tab: default to "risk" to highlight the user requested detailed mock
    var selectedStatusTab by rememberSaveable(stockCode) { mutableStateOf("risk") }
    var simAddedSharesInput by rememberSaveable(stockCode) { mutableStateOf("100") }
    var showBuyUpSimulator by rememberSaveable(stockCode) { mutableStateOf(false) }
    val simAddedShares = simAddedSharesInput.toIntOrNull() ?: 0
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationAccess = com.kurogane.dividend.ui.components.rememberNotificationAccess {
        viewModel.setNotificationEnabled(com.kurogane.dividend.notifications.NotificationKind.DIVIDEND, true)
    }
    val isAlertOn = notificationSettings.dividendEnabled

    val baseShares = currentHolding.shares
    val currentPrice = currentHolding.price
    val dps = currentHolding.dps
    val financial = financialData[currentHolding.code]
    val fiscalMonth = financial?.period?.substringAfterLast('/')?.toIntOrNull()?.takeIf { it in 1..12 }
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
    val valuationValues = remember(backendAnalysisValues, financial) {
        ((backendAnalysisValues ?: emptyMap()) + (financial?.values ?: emptyMap())).filterKeys { it == "per" || it == "pbr" }
    }
    val valuationNarrative = remember(valuationValues) { StockAnalysisNarrativeGenerator.generate(valuationValues) }
    val profile = baseProfile.copy(
        healthGrade = healthNarrative.grade ?: "未判定", healthScore = healthNarrative.score ?: -1,
        healthEvaluation = healthNarrative.title, healthDiagnosisComment = healthNarrative.findings.joinToString(" ") { it.text }.ifBlank { "財務指標未取得" },
        valuationScore = valuationNarrative.score ?: -1,
        buySignalTitle = valuationNarrative.title,
        buySignalComment = valuationNarrative.findings.joinToString(" ") { it.text }.ifBlank { "株価指標未取得" }
    )
    val buySignalDiagnosisItems = buildList {
        if (profile.yieldRangeMin.isFinite() && profile.yieldRangeAvg.isFinite() && profile.yieldRangeMax.isFinite()) {
            val yieldPosition = when {
                currentHolding.yieldRate < profile.yieldRangeMin -> "過去の最低水準を下回っています"
                currentHolding.yieldRate > profile.yieldRangeMax -> "過去の最高水準を上回っています"
                currentHolding.yieldRate >= profile.yieldRangeAvg -> "過去平均以上です"
                else -> "過去平均を下回っています"
            }
            add("配当利回り：現在${currentHolding.yieldRate.financialText(2, "%")}、過去実績は最低${profile.yieldRangeMin.financialText(2, "%")}・平均${profile.yieldRangeAvg.financialText(2, "%")}・最高${profile.yieldRangeMax.financialText(2, "%")}です。$yieldPosition。")
        } else {
            add("配当利回り：過去の利回り実績が不足しているため、過去平均との差は判定できません。現在値は${currentHolding.yieldRate.financialText(2, "%")}です。")
        }
        valuationNarrative.findings.forEach { add(it.text) }
        if (valuationNarrative.missingLabels.isNotEmpty()) {
            add("未取得の株価指標：${valuationNarrative.missingLabels.joinToString("・")}。取得できた指標のみで評価しています。")
        }
    }
    val growthDurabilityDiagnosisItems = listOf(
        when {
            profile.consecutiveYears > 0 -> "連続増配実績：${profile.consecutiveYears}期連続で増配しています。長期の継続性を見る材料ですが、今後の利益・キャッシュの裏付けも確認が必要です。"
            profile.consecutiveYears == 0 -> "連続増配実績：直近の連続増配は確認できません。増配年数だけでなく、減配の有無や配当水準の推移も合わせて見ます。"
            else -> "連続増配実績：データ未取得のため、連続増配年数は判定できません。"
        },
        when {
            profile.noCutYears > 0 -> "減配しなかった期間：${profile.noCutYears}年です。記録期間内に減配がないことを示しますが、将来の配当を保証するものではありません。"
            profile.noCutYears == 0 -> "減配しなかった期間：直近の連続記録はありません。過去の配当推移を確認してください。"
            else -> "減配しなかった期間：比較できる配当履歴が不足しています。"
        },
        if (profile.growthRate.isFinite()) {
            val growthDirection = when {
                profile.growthRate > 0.0 -> "増配傾向"
                profile.growthRate < 0.0 -> "減配傾向"
                else -> "横ばい"
            }
            "配当成長率：取得できた直近最大5期の年平均成長率（CAGR）は${profile.growthRate.financialText(1, "%")}で、${growthDirection}です。"
        } else {
            "配当成長率：連続した複数期の比較データが不足しているため算出できません。"
        },
        when {
            !profile.payoutRatio.isFinite() -> "配当性向：未取得のため、利益に対する配当負担は判定できません。"
            profile.payoutRatio > 100.0 -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}で利益を上回っています。利益以外の資金に依存していないか注意が必要です。"
            profile.payoutRatio > 70.0 -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}と高めです。利益が落ちると配当維持の余裕が縮む可能性があります。"
            profile.payoutRatio >= 0.0 -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}です。利益のうち配当に回していない部分もあり、利益変動への余地を確認する材料になります。"
            else -> "配当性向：約${profile.payoutRatio.financialText(1, "%")}です。赤字や特殊要因の影響を含む可能性があるため、決算内容の確認が必要です。"
        },
        "財務面の支え：自己資本比率${profile.equityRatio.financialText(1, "%")}、営業利益率${profile.operatingMargin.financialText(1, "%")}、${profile.fcfStatus}です。未取得の指標は評価に含めていません。"
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
    val cutImpactRatio = if (totalPortfolioDiv > 0) cutImpact.toDouble() / totalPortfolioDiv * 100.0 else 0.0
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)
    val riskDiagnosisItems = listOf(
        if (totalPortfolioDiv > 0) {
            val stockRisk = when {
                divDependency > 25.0 -> "目安の10%を大きく上回っています"
                divDependency > 10.0 -> "目安の10%を上回っています"
                else -> "目安の10%以内です"
            }
            "銘柄配当依存度：年間配当の約${divDependencyStr}%を${currentHolding.name}が占めています。$stockRisk。"
        } else {
            "銘柄配当依存度：年間配当データがないため、構成比を算出できません。"
        },
        if (totalPortfolioVal > 0) {
            "セクター集中度：${cleanSectorName}の評価額比率は約${sectorDependencyStr}%で、${sectorRiskText}の状態です。"
        } else {
            "セクター集中度：評価額データがないため、構成比を算出できません。"
        },
        if (totalPortfolioDiv > 0) {
            "減配時の影響：${currentHolding.name}の配当が50%減ると、年間配当は約¥${currencyFormat.format(cutImpact)}（全体の約${String.format(Locale.JAPAN, "%.1f", cutImpactRatio)}%）減る試算です。"
        } else {
            "減配時の影響：年間配当データがないため、試算できません。"
        },
        when {
            divDependency > 25.0 || sectorDependency > 30.0 -> "見直しの目安：追加投資を別銘柄・別セクターへ分散し、集中度を少しずつ下げる方法があります。"
            divDependency > 10.0 || sectorDependency > 20.0 -> "見直しの目安：今後の追加投資先を分散すると、特定銘柄やセクターへの依存を抑えられます。"
            else -> "現在は銘柄・セクターの集中が比較的小さい状態です。複数の投資先への分散を続けると、偏りを抑えやすくなります。"
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
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
                                        if (!isOwnedHolding) {
                                            IconButton(
                                                onClick = { onOpenAddHolding(currentHolding.code) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "保有銘柄に登録",
                                                    tint = PrimaryEmerald,
                                                    modifier = Modifier.size(20.dp)
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
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(if (isBookmarked) "Watchlistから解除しました" else "Watchlistに登録しました")
                                    }
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
                                    Text(stringResource(R.string.stock_detail_text_7, currentHolding.currency, currentHolding.acqPrice.toLong()), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
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
                            }
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text(if (profile.consecutiveYears > 0) stringResource(R.string.stock_detail_label_3) else stringResource(R.string.stock_detail_label_4), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4 Bars Container
                        val chartFontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                        val chartHeight = (120f + 80f * (chartFontScale - 1f).coerceAtLeast(0f)).dp
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
                                val history = financial?.dividends.orEmpty().takeLast(8)
                                val maxDps = history.maxOfOrNull { it.dps }?.takeIf { it > 0 } ?: 1.0
                                val prefix = currentHolding.currency
                                val bars = history.map { point ->
                                    val roundedDps = kotlin.math.round(point.dps * 10.0) / 10.0
                                    val dpsText = if (roundedDps % 1.0 == 0.0) {
                                        String.format(Locale.JAPAN, "%.0f", roundedDps)
                                    } else {
                                        String.format(Locale.JAPAN, "%.1f", roundedDps)
                                    }
                                    Triple(
                                        stringResource(R.string.stock_detail_label_5, point.year.toString()),
                                        stringResource(R.string.add_holding_1_s_2_s_2, prefix, dpsText),
                                        (point.dps / maxDps * 0.95).toFloat()
                                    )
                                }
                                var barsVisible by remember(stockCode) { mutableStateOf(false) }
                                LaunchedEffect(stockCode, bars.isNotEmpty()) { if (bars.isNotEmpty()) barsVisible = true }
                                if (bars.isEmpty()) Text(stringResource(R.string.stock_detail_text_9), color = OnSurfaceVariant)
                                bars.forEachIndexed { index, (term, dpsVal, ratio) ->
                                    val animatedRatio by animateFloatAsState(
                                        targetValue = if (barsVisible) ratio.coerceIn(0f, 0.95f) else 0f,
                                        animationSpec = tween(durationMillis = 560, delayMillis = index * 30),
                                        label = "Dividend bar height"
                                    )
                                    val isProjected = term == bars.lastOrNull()?.first
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Text(dpsVal, color = if (isProjected) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Int.MAX_VALUE else 1)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        BoxWithConstraints(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(26.dp)
                                                    .height(maxHeight * animatedRatio)
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
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.stock_detail_health_grade, profile.healthGrade), modifier = Modifier.weight(0.8f), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = AppTextSize.dense, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.stock_detail_health_payout, profile.payoutRatio.financialText(1, "%")), modifier = Modifier.weight(1.2f), color = OnSurfaceVariant, fontSize = AppTextSize.dense, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    val healthBadgeColor = when (profile.healthGrade) {
                                        "S", "A", "B" -> PrimaryEmerald
                                        "C", "D" -> AccentAmber
                                        else -> OnSurfaceVariant
                                    }
                                    Surface(shape = RoundedCornerShape(50), color = healthBadgeColor.copy(alpha = 0.14f), border = BorderStroke(1.dp, healthBadgeColor.copy(alpha = 0.35f))) {
                                        Text(profile.healthEvaluation, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = healthBadgeColor, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
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
                                        color = AccentAmber.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = AccentAmber,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("診断コメント", color = AccentAmber, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                            }
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

            item {
                val displayedBenefits = stockBenefits.filter { it.name.isNotBlank() || it.marketValue.isNotBlank() }
                val benefitAnnualValue = displayedBenefits.sumOf { it.marketValue.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
                val positionValue = currentHolding.totalValuation
                val benefitYield = if (positionValue > 0.0) benefitAnnualValue / positionValue * 100.0 else null
                val combinedAnnualValue = currentHolding.getEffectiveAnnualDividend(taxRateMode) + benefitAnnualValue
                val combinedYield = if (positionValue > 0.0) combinedAnnualValue / positionValue * 100.0 else null
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
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryEmerald.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryEmerald)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("銘柄属性・特典設定", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                    modifier = Modifier.widthIn(max = 130.dp).padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(PrimaryEmerald))
                                    Text(
                                        "${selectedClimate?.label ?: "景気特性未設定"} / ${if (hasSavedBenefit) "優待あり" else "優待未登録"}",
                                        color = PrimaryEmerald,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Bold
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
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("景気特性", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            if (stockClimateOverrides.containsKey(currentHolding.code)) "手動設定" else "${cleanSectorName}の標準分類",
                                            color = OnSurfaceVariant,
                                            fontSize = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .onGloballyPositioned { climateSelectorWidthPx = it.size.width }
                                    ) {
                                        OutlinedButton(
                                            onClick = { climateMenuExpanded = true },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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
                                                        viewModel.setStockClimateOverride(currentHolding.code, climate)
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
                                    Text(climateDescription, color = OnSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
                                    if (stockClimateOverrides.containsKey(currentHolding.code)) {
                                        TextButton(
                                            onClick = { viewModel.setStockClimateOverride(currentHolding.code, null) },
                                            modifier = Modifier.align(Alignment.End).heightIn(min = 34.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) { Text("業種の初期分類に戻す", color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
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
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("株主優待", color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "優待利回り ${benefitYield?.let { "+${String.format(Locale.JAPAN, "%.2f", it)}%" } ?: "—"}",
                                        color = PrimaryEmerald,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("優待の名前と相場金額を記録できます。", color = OnSurfaceVariant, fontSize = 14.sp)

                                displayedBenefits.forEachIndexed { index, benefit ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                            .background(SurfaceContainerLowest)
                                            .border(1.dp, PrimaryEmerald.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                            .clickable { editingBenefitIndex = if (editingBenefitIndex == index) -1 else index }
                                            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                                .background(PrimaryEmerald.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(benefit.name.ifBlank { "優待名未設定" }, color = OnSurface, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text("年間相当額", color = OnSurfaceVariant, fontSize = 12.sp)
                                        }
                                        Text(
                                            "¥${benefit.marketValue.toLongOrNull()?.let { yenFormat.format(it) } ?: "0"}",
                                            color = PrimaryEmerald,
                                            fontSize = 16.sp,
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
                                                placeholder = { Text("優待名") },
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
                                                placeholder = { Text("相場金額（円）") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                shape = RoundedCornerShape(12.dp),
                                                trailingIcon = { Text("円", color = OnSurfaceVariant) }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = newBenefitName,
                                    onValueChange = { newBenefitName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("優待名") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                OutlinedTextField(
                                    value = newBenefitValue,
                                    onValueChange = { newBenefitValue = it.filter(Char::isDigit) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("相場金額（円）") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    trailingIcon = { Text("円", color = OnSurfaceVariant) }
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
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    enabled = newBenefitName.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = PrimaryEmerald)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("優待を追加", fontWeight = FontWeight.Bold)
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Percent, contentDescription = null, tint = PrimaryEmerald)
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("配当＋優待 合計実質利回り ", color = OnSurface, fontSize = 13.sp)
                                                Text(
                                                    combinedYield?.let { "${String.format(Locale.JAPAN, "%.2f", it)}%" } ?: "—",
                                                    color = PrimaryEmerald,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                "年間 ¥${yenFormat.format(combinedAnnualValue.coerceAtLeast(0.0).toLong())} 相当",
                                                color = OnSurfaceVariant,
                                                fontSize = 12.sp
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
                        shape = RoundedCornerShape(12.dp)
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
                        shape = RoundedCornerShape(12.dp)
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
                                            fontFamily = FontFamily.Monospace
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
