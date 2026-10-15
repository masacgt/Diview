package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.model.*
import com.kurogane.dividend.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Summary of dividend changes shown on the dividend-change subtab. */
@Composable
internal fun DividendChangeAnalysisCards(
    holdings: List<Holding>,
    financialData: Map<String, FinancialData>,
    isPrivacyActive: Boolean,
    taxRateMode: String,
    onNavigateToDetail: (String) -> Unit
) {
    val analysis = remember(holdings, financialData, taxRateMode) {
        DividendChangeAnalysis.calculate(holdings, financialData, taxRateMode)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnnualImpactCard(analysis, isPrivacyActive, taxRateMode)
        MonitorKpis(analysis, isPrivacyActive)
    }
}

/** Payout-health and held-stock risk panels shown on the dividend-analysis subtab. */
@Composable
internal fun DividendPayoutHealthAndRiskCards(
    holdings: List<Holding>,
    financialData: Map<String, FinancialData>,
    taxRateMode: String,
    onNavigateToDetail: (String) -> Unit
) {
    val analysis = remember(holdings, financialData, taxRateMode) {
        DividendChangeAnalysis.calculate(holdings, financialData, taxRateMode)
    }
    var riskSort by rememberSaveable { mutableStateOf("payout") }
    var sortAnimationRevision by remember { mutableStateOf(0) }
    fun sortedRiskRows(sort: String) = when (sort) {
        "years" -> analysis.securities.sortedByDescending { it.profile?.noCutYears?.takeIf { years -> years >= 0 } ?: -1 }
        "growth" -> analysis.securities.sortedByDescending { it.profile?.consecutiveYears?.takeIf { years -> years >= 0 } ?: -1 }
        else -> analysis.securities.sortedByDescending {
            it.payoutRatio?.let { ratio -> if (ratio < 0) Double.POSITIVE_INFINITY else ratio }
                ?: Double.NEGATIVE_INFINITY
        }
    }
    val riskRowsState = remember(analysis) { mutableStateOf(sortedRiskRows(riskSort)) }
    val riskRows = riskRowsState.value
    val cardHeights = remember { mutableStateMapOf<String, Int>() }
    val reorderOffsets = remember { mutableStateMapOf<String, Float>() }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
    fun selectRiskSort(sort: String) {
        val nextRows = sortedRiskRows(sort)
        val oldPositions = mutableMapOf<String, Float>()
        var position = 0f
        riskRowsState.value.forEach { row ->
            oldPositions[row.code] = position
            position += (cardHeights[row.code]?.toFloat() ?: 0f) + spacingPx
        }
        val newPositions = mutableMapOf<String, Float>()
        position = 0f
        nextRows.forEach { row ->
            newPositions[row.code] = position
            position += (cardHeights[row.code]?.toFloat() ?: 0f) + spacingPx
        }
        reorderOffsets.clear()
        nextRows.forEach { row ->
            val offset = (oldPositions[row.code] ?: 0f) - (newPositions[row.code] ?: 0f)
            if (offset != 0f) {
                reorderOffsets[row.code] = offset
            }
        }
        riskRowsState.value = nextRows
        riskSort = sort
        sortAnimationRevision++
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PayoutHealthCard(analysis)
        MonitorHeading("保有銘柄の減配リスク診断", Icons.Default.Shield)
        MonitorSelectors("並び替え", listOf("payout" to stringResource(R.string.dividend_change_analysis_cards_label), "years" to stringResource(R.string.dividend_change_analysis_cards_label_2), "growth" to stringResource(R.string.dividend_change_analysis_cards_label_24)), riskSort, ::selectRiskSort)
        if (riskRows.isEmpty()) {
            MonitorEmptyCard("保有する国内株を追加すると、配当性向と非減配年数を表示します。")
        }
        riskRows.forEach { row ->
            key(row.code) {
                val verticalOffset = remember(row.code) { Animatable(0f) }
                val startOffset = reorderOffsets[row.code] ?: 0f
                LaunchedEffect(riskSort, sortAnimationRevision) {
                    if (startOffset != 0f) {
                        verticalOffset.snapTo(startOffset)
                        verticalOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (cardHeights[row.code] != size.height) cardHeights[row.code] = size.height
                        }
                        .graphicsLayer { translationY = verticalOffset.value }
                ) {
                    DividendRiskCard(row, onClick = { onNavigateToDetail(row.code) })
                }
            }
        }
    }
}

/** Payout-risk list for Japanese stocks registered in the Watchlist. */
@Composable
internal fun WatchlistDividendPayoutRiskContent(
    watchlist: List<WatchlistStock>,
    financialData: Map<String, FinancialData>,
    onNavigateToDetail: (String) -> Unit
) {
    val rows = remember(watchlist, financialData) {
        watchlist.filter { it.category == "jp" }.map { stock ->
            val normalizedCode = PortfolioMath.normalizeCode(stock.code).removeSuffix(".T")
            val data = financialData[stock.code] ?: financialData[normalizedCode]
            val consecutiveYears = data?.dividends.orEmpty()
                .filter { it.dps.isFinite() && it.dps >= 0 }
                .distinctBy { it.year }
                .sortedBy { it.year }
                .takeLast(2)
                .takeIf { it.size == 2 && it[1].year == it[0].year + 1 }
            val previous = consecutiveYears?.first()
            val current = consecutiveYears?.last()
            DividendChangeSecurity(
                code = stock.code,
                name = stock.name,
                sector = stock.sector,
                shares = stock.defaultShares.toLong(),
                annualDividend = stock.dps * stock.defaultShares,
                previousYear = previous?.year,
                currentYear = current?.year,
                previousDps = previous?.dps,
                currentDps = current?.dps,
                previousAnnualDividend = previous?.dps?.times(stock.defaultShares),
                annualImpact = if (previous != null && current != null) {
                    (current.dps - previous.dps) * stock.defaultShares
                } else null,
                profile = data?.profile,
                dividendHistoryBasis = data?.dividendHistoryBasis ?: "unknown"
            )
        }
    }
    var sort by rememberSaveable { mutableStateOf("payout") }
    var sortAnimationRevision by remember { mutableStateOf(0) }
    fun sortedRiskRows(selectedSort: String) = when (selectedSort) {
        "years" -> rows.sortedByDescending { it.profile?.noCutYears?.takeIf { years -> years >= 0 } ?: -1 }
        "growth" -> rows.sortedByDescending { it.profile?.consecutiveYears?.takeIf { years -> years >= 0 } ?: -1 }
        else -> rows.sortedByDescending {
            it.payoutRatio?.let { ratio -> if (ratio < 0) Double.POSITIVE_INFINITY else ratio }
                ?: Double.NEGATIVE_INFINITY
        }
    }
    val riskRowsState = remember(rows) { mutableStateOf(sortedRiskRows(sort)) }
    val riskRows = riskRowsState.value
    val cardHeights = remember { mutableStateMapOf<String, Int>() }
    val reorderOffsets = remember { mutableStateMapOf<String, Float>() }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
    fun selectRiskSort(selectedSort: String) {
        val nextRows = sortedRiskRows(selectedSort)
        val oldPositions = mutableMapOf<String, Float>()
        var position = 0f
        riskRowsState.value.forEach { row ->
            oldPositions[row.code] = position
            position += (cardHeights[row.code]?.toFloat() ?: 0f) + spacingPx
        }
        val newPositions = mutableMapOf<String, Float>()
        position = 0f
        nextRows.forEach { row ->
            newPositions[row.code] = position
            position += (cardHeights[row.code]?.toFloat() ?: 0f) + spacingPx
        }
        reorderOffsets.clear()
        nextRows.forEach { row ->
            val offset = (oldPositions[row.code] ?: 0f) - (newPositions[row.code] ?: 0f)
            if (offset != 0f) {
                reorderOffsets[row.code] = offset
            }
        }
        riskRowsState.value = nextRows
        sort = selectedSort
        sortAnimationRevision++
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MonitorSelectors(
            "並び替え",
            listOf(
                "payout" to stringResource(R.string.dividend_change_analysis_cards_label),
                "years" to stringResource(R.string.dividend_change_analysis_cards_label_2),
                "growth" to stringResource(R.string.dividend_change_analysis_cards_label_24)
            ),
            sort,
            ::selectRiskSort
        )
        if (riskRows.isEmpty()) {
            MonitorEmptyCard("Watchlistに日本株を登録すると、配当性向と非減配年数を表示します。")
        }
        riskRows.forEach { row ->
            key(row.code) {
                val verticalOffset = remember(row.code) { Animatable(0f) }
                val startOffset = reorderOffsets[row.code] ?: 0f
                LaunchedEffect(sort, sortAnimationRevision) {
                    if (startOffset != 0f) {
                        verticalOffset.snapTo(startOffset)
                        verticalOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (cardHeights[row.code] != size.height) cardHeights[row.code] = size.height
                        }
                        .graphicsLayer { translationY = verticalOffset.value }
                ) {
                    DividendRiskCard(row, onClick = { onNavigateToDetail(row.code) })
                }
            }
        }
    }
}

/** Per-stock increase/decrease list shown at the bottom of the dividend-change tab. */
@Composable
internal fun DividendChangeSecuritiesList(
    holdings: List<Holding>,
    financialData: Map<String, FinancialData>,
    isPrivacyActive: Boolean,
    taxRateMode: String,
    onNavigateToDetail: (String) -> Unit
) {
    val analysis = remember(holdings, financialData, taxRateMode) {
        DividendChangeAnalysis.calculate(holdings, financialData, taxRateMode)
    }
    var filter by rememberSaveable { mutableStateOf("all") }
    var changeSort by rememberSaveable { mutableStateOf("impact") }
    var sortAnimationRevision by remember { mutableStateOf(0) }
    fun sortedChangeRows(selectedFilter: String, selectedSort: String) = analysis.securities
        .filter {
            when (selectedFilter) {
                "increase" -> it.change == DividendChangeStatus.INCREASE
                "monitor" -> it.requiresMonitoring
                "maintain" -> it.change == DividendChangeStatus.MAINTAIN
                else -> true
            }
        }
        .let { filtered ->
            when (selectedSort) {
                "growth" -> filtered.sortedByDescending { it.growthRate ?: Double.NEGATIVE_INFINITY }
                "years" -> filtered.sortedByDescending { it.profile?.consecutiveYears ?: -1 }
                "code" -> filtered.sortedBy { it.code }
                else -> filtered.sortedByDescending { it.annualImpact ?: Double.NEGATIVE_INFINITY }
            }
        }
    val rowsState = remember(analysis, filter) { mutableStateOf(sortedChangeRows(filter, changeSort)) }
    val rows = rowsState.value
    val cardHeights = remember { mutableStateMapOf<String, Int>() }
    val reorderOffsets = remember { mutableStateMapOf<String, Float>() }
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
    fun selectChangeSort(selectedSort: String) {
        val nextRows = sortedChangeRows(filter, selectedSort)
        val oldPositions = mutableMapOf<String, Float>()
        var position = 0f
        rowsState.value.forEach { row ->
            oldPositions[row.code] = position
            position += (cardHeights[row.code]?.toFloat() ?: 0f) + spacingPx
        }
        val newPositions = mutableMapOf<String, Float>()
        position = 0f
        nextRows.forEach { row ->
            newPositions[row.code] = position
            position += (cardHeights[row.code]?.toFloat() ?: 0f) + spacingPx
        }
        reorderOffsets.clear()
        nextRows.forEach { row ->
            val offset = (oldPositions[row.code] ?: 0f) - (newPositions[row.code] ?: 0f)
            if (offset != 0f) reorderOffsets[row.code] = offset
        }
        rowsState.value = nextRows
        changeSort = selectedSort
        sortAnimationRevision++
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MonitorHeading("銘柄別の増配・減配", Icons.Default.QueryStats)
        val counts = analysis.securities
        MonitorSelectors(
            label = null,
            options = listOf(
                "all" to stringResource(R.string.dividend_change_analysis_cards_label_5, counts.size),
                "increase" to stringResource(R.string.dividend_change_analysis_cards_label_6, counts.count { it.change == DividendChangeStatus.INCREASE }),
                "monitor" to stringResource(R.string.dividend_change_analysis_cards_label_7, analysis.monitoring.size),
                "maintain" to stringResource(R.string.dividend_change_analysis_cards_label_8, counts.count { it.change == DividendChangeStatus.MAINTAIN })
            ),
            selected = filter,
            onSelect = { filter = it }
        )
        MonitorSelectors(
            "並び替え",
            listOf("impact" to stringResource(R.string.dividend_change_analysis_cards_label_9), "growth" to stringResource(R.string.dividend_change_analysis_cards_label_10), "years" to stringResource(R.string.dividend_change_analysis_cards_label_11), "code" to stringResource(R.string.dividend_change_analysis_cards_label_12)),
            changeSort
        ) { selectChangeSort(it) }
        if (rows.isEmpty()) MonitorEmptyCard("該当する銘柄はありません。")
        rows.forEach { row ->
            key(row.code) {
                val verticalOffset = remember(row.code) { Animatable(0f) }
                val startOffset = reorderOffsets[row.code] ?: 0f
                LaunchedEffect(changeSort, sortAnimationRevision) {
                    if (startOffset != 0f) {
                        verticalOffset.snapTo(startOffset)
                        verticalOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (cardHeights[row.code] != size.height) cardHeights[row.code] = size.height
                        }
                        .graphicsLayer { translationY = verticalOffset.value }
                ) {
                    DividendChangeCard(row, isPrivacyActive, onClick = { onNavigateToDetail(row.code) })
                }
            }
        }
    }
}

@Composable
private fun AnnualImpactCard(analysis: DividendChangeAnalysis, privacy: Boolean, taxRateMode: String) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val compared = analysis.compared
    val total = analysis.securities.size
    val increased = compared.count { it.change == DividendChangeStatus.INCREASE }
    val maintained = compared.count { it.change == DividendChangeStatus.MAINTAIN }
    val decreased = compared.count { it.change == DividendChangeStatus.DECREASE }
    val impactColor = if ((analysis.impact ?: 0.0) < 0) ErrorRed else StatusSuccess
    val impactCellHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        maxOf(18.dp, 15.sp.toDp()) + 19.sp.toDp() + 16.dp
    }
    MonitorCard(compact = true, tight = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = impactColor, modifier = Modifier.size(18.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(stringResource(R.string.dividend_change_analysis_cards_text), color = OnSurface, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp)
                AnnualImpactInfo(
                    stringResource(
                        R.string.dividend_change_analysis_cards_text_2,
                        if (taxRateMode == "net") resources.getString(R.string.dividend_change_analysis_cards_label_17)
                        else resources.getString(R.string.stock_detail_text_5)
                    ),
                    excludedHistoryCount = total - compared.size
                )
            }
            MonitorBadge(percent(analysis.growthRate, signed = true), impactColor)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(money(analysis.impact, privacy, signed = true), fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, color = impactColor, modifier = Modifier.weight(1f, fill = false))
            Text(stringResource(R.string.annual_dividend_text_2), color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        if (analysis.compared.any { it.dividendHistoryBasis == "calendar_year_observed_distributions" }) {
            Text("yfinanceの修復済み配当イベントを暦年で合計しています。分割比率の独自換算は加えず、決算期別DPSとは期間・株数基準が異なる場合があります。",
                color = OnSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
        }
        AdaptiveRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.dividend_change_analysis_cards_text_3 , total), color = OnSurfaceVariant, fontSize = 11.sp)
            Text(stringResource(R.string.dividend_change_analysis_cards_text_4 , compared.size, total), color = OnSurfaceVariant, fontSize = 11.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(SurfaceContainerHigh)) {
            listOf(increased to PrimaryEmerald, maintained to TextMuted, decreased to ErrorRed).forEach { (count, color) ->
                if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
            }
            val unknown = total - compared.size
            if (unknown > 0) Box(Modifier.weight(unknown.toFloat()).fillMaxHeight().background(SurfaceContainerHighest))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            DistributionCell("増配", increased, compared.size, PrimaryEmerald, Modifier.weight(1f).fillMaxHeight(), compact = true)
            DistributionCell("維持", maintained, compared.size, OnSurfaceVariant, Modifier.weight(1f).fillMaxHeight(), compact = true)
            DistributionCell("減配", decreased, compared.size, ErrorRed, Modifier.weight(1f).fillMaxHeight(), compact = true)
        }
        HorizontalDivider(color = BorderColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(impactCellHeight)) {
            ImpactCell(
                stringResource(R.string.dividend_change_analysis_cards_text_5),
                money(analysis.positiveImpact.takeIf { compared.isNotEmpty() }, privacy, signed = true),
                PrimaryEmerald,
                Modifier.weight(1f).fillMaxHeight()
            ) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
            }
            ImpactCell(
                stringResource(R.string.dividend_change_analysis_cards_text_6),
                money(analysis.negativeImpact.takeIf { compared.isNotEmpty() }, privacy, signed = true),
                ErrorRed,
                Modifier.weight(1f).fillMaxHeight()
            ) {
                DecreasingBanknoteIcon(tint = ErrorRed, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DecreasingBanknoteIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val downwardArrow = Path().apply {
            moveTo(size.width * 0.84f, size.height * 0.12f)
            lineTo(size.width * 0.84f, size.height * 0.78f)
            moveTo(size.width * 0.66f, size.height * 0.60f)
            lineTo(size.width * 0.84f, size.height * 0.80f)
            lineTo(size.width * 0.99f, size.height * 0.61f)
        }
        val outline = Stroke(width = size.width * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.03f, size.height * 0.21f),
            size = Size(size.width * 0.68f, size.height * 0.58f),
            cornerRadius = CornerRadius(size.width * 0.06f),
            style = outline
        )
        drawCircle(
            color = tint,
            radius = size.width * 0.11f,
            center = Offset(size.width * 0.37f, size.height * 0.50f),
            style = outline
        )
        drawPath(downwardArrow, tint, style = outline)
    }
}

@Composable
private fun AnnualImpactInfo(comparisonDescription: String, excludedHistoryCount: Int) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.dividend_change_analysis_cards_impact_info_content_description),
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 300.dp),
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
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.dividend_change_analysis_cards_impact_info_title),
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderBlue, thickness = 0.75.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    comparisonDescription,
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.caption,
                    lineHeight = 20.sp
                )
                if (excludedHistoryCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dividend_change_analysis_cards_text_7, excludedHistoryCount),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.caption,
                        lineHeight = 20.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dividend_change_analysis_cards_text_9),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.caption,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun DistributionCell(label: String, count: Int, total: Int, color: Color, modifier: Modifier, compact: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceContainer)
        .border(0.5.dp, BorderBlue, RoundedCornerShape(12.dp))
        .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = if (compact) 4.dp else 10.dp),
        verticalArrangement = if (compact) Arrangement.SpaceEvenly else Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(5.dp).background(color, CircleShape))
                Text(label, fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = if (compact) 15.sp else TextUnit.Unspecified, color = OnSurfaceVariant)
            }
            Text(percent(if (total > 0) count.toDouble() / total * 100 else null),
                fontSize = if (compact) 9.sp else 10.sp,
                lineHeight = if (compact) 13.sp else TextUnit.Unspecified,
                color = color)
        }
        Text(stringResource(R.string.dividend_change_analysis_cards_text_8 , count),
            fontSize = if (compact) 14.sp else 17.sp,
            lineHeight = if (compact) 19.sp else TextUnit.Unspecified,
            fontWeight = FontWeight.Bold, color = OnSurface,
            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
            textAlign = if (compact) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
private fun ImpactCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .border(0.5.dp, BorderBlue, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(Modifier.width(4.dp))
            Text(label, color = OnSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 1)
        }
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun MonitorKpis(analysis: DividendChangeAnalysis, privacy: Boolean) {
    val known = analysis.securities.filter { it.annualImpact != null || it.payoutRatio != null }
    val riskRatio = known.takeIf { it.isNotEmpty() }?.let { analysis.monitoring.size.toDouble() / it.size * 100 }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
        KpiCell("加重平均増配率", percent(analysis.growthRate, signed = true),
            money(analysis.impact, privacy, signed = true), if ((analysis.growthRate ?: 0.0) < 0) ErrorRed else StatusSuccess,
            Modifier.weight(1f).fillMaxHeight())
        KpiCell("減配リスク比率", percent(riskRatio), if (known.isEmpty()) "指標未取得" else "${analysis.monitoring.size}銘柄 要監視", ErrorRed,
            Modifier.weight(1f).fillMaxHeight())
        KpiCell("性向80%超・赤字", if (analysis.payoutCovered.isEmpty()) "未取得" else "${analysis.highPayout.size}銘柄", "配当比 ${percent(analysis.highPayoutDividendShare)}", AccentAmber,
            Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun KpiCell(title: String, value: String, detail: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = OnSurfaceVariant, fontSize = 9.sp, lineHeight = 11.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp)
            Text(detail, color = color, fontSize = 9.sp, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun PayoutHealthCard(analysis: DividendChangeAnalysis) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val covered = analysis.payoutCovered
    val zones = listOf(
        Triple(stringResource(R.string.dividend_change_analysis_cards_label_13), covered.count { it.payoutRatio!! in 0.0..40.0 }, PrimaryEmerald),
        Triple(stringResource(R.string.dividend_change_analysis_cards_label_14), covered.count { it.payoutRatio!! > 40 && it.payoutRatio!! <= 70 }, SkyBlue),
        Triple(stringResource(R.string.dividend_change_analysis_cards_label_15), covered.count { it.payoutRatio!! > 70 && it.payoutRatio!! <= 80 }, AccentAmber),
        Triple(stringResource(R.string.dividend_change_analysis_cards_label_16), analysis.highPayout.size, ErrorRed)
    )
    MonitorCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonitorHeading("配当性向健全度レーダー", Icons.Default.Shield)
            PayoutHealthInfo()
        }
        Text(stringResource(R.string.dividend_change_analysis_cards_text_10 , analysis.headroomScore?.let { "$it/100" } ?: resources.getString(R.string.not_available)), color = PrimaryEmerald,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
        zones.forEach { (label, count, color) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Text(label, color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.dividend_change_analysis_cards_text_8 , count), color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                MonitorProgress(if (covered.isEmpty()) 0f else count.toFloat() / covered.size, color)
            }
        }
        Text(stringResource(R.string.dividend_change_analysis_cards_text_12 , covered.size, analysis.securities.size), color = TextMuted, fontSize = 10.sp)
        analysis.highPayout.maxByOrNull { it.payoutRatio?.let { ratio -> if (ratio < 0) Double.POSITIVE_INFINITY else ratio } ?: 0.0 }?.let { row ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ErrorRed.copy(alpha = 0.08f))
                .border(0.5.dp, BorderBlue, RoundedCornerShape(10.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.dividend_change_analysis_cards_1_s_2_s , row.name, row.code), color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(stringResource(R.string.dividend_change_analysis_cards_text_13 , percent(row.payoutRatio), if (row.payoutRatio!! < 0) resources.getString(R.string.dividend_change_analysis_cards_label_19) else resources.getString(R.string.dividend_change_analysis_cards_label_20)),
                    color = ErrorRed, fontSize = 11.sp)
                Text(stringResource(R.string.dividend_change_analysis_cards_text_14 , row.profile?.fcfStatus?.takeUnless { it == "未取得" } ?: resources.getString(R.string.dividend_change_analysis_cards_label_21)),
                    color = OnSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PayoutHealthInfo() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.dividend_change_analysis_cards_payout_info_content_description),
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 300.dp),
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
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.dividend_change_analysis_cards_payout_info_title),
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = AppTextSize.body
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderBlue, thickness = 0.75.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.dividend_change_analysis_cards_text_11),
                    color = OnSurfaceVariant,
                    fontSize = AppTextSize.caption,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DividendRiskCard(row: DividendChangeSecurity, onClick: () -> Unit) {
    val ratio = row.payoutRatio
    val color = when {
        ratio == null -> TextMuted
        ratio > 80 || ratio < 0 -> ErrorRed
        ratio > 70 -> AccentAmber
        ratio > 40 -> SkyBlue
        else -> PrimaryEmerald
    }
    MonitorCard(onClick = onClick, compact = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.code, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
            Text(row.name, modifier = Modifier.weight(1f), color = OnSurface,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
            MonitorBadge("配当性向 ${percent(ratio)}", color, compact = true)
        }
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.dividend_change_analysis_cards_text_15 , row.profile?.noCutYears?.takeIf { it >= 0 }?.let { "${it}年" } ?: "未取得")
                + " / 連続増配${row.profile?.consecutiveYears?.takeIf { it >= 0 }?.let { "${it}年" } ?: "未取得"}",
                color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
            Text(when {
                ratio == null -> "指標未取得"
                ratio < 0 -> "赤字決算・要監視"
                ratio > 80 -> "高配当性向・要監視"
                ratio > 70 -> "配当性向を確認"
                else -> "配当性向は標準範囲"
            } + " ›", color = color, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        MonitorProgress(when { ratio == null -> 0f; ratio < 0 -> 1f; else -> (ratio / 100).toFloat() }, color)
    }
}

@Composable
private fun DividendChangeCard(row: DividendChangeSecurity, privacy: Boolean, onClick: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val color = when (row.change) {
        DividendChangeStatus.INCREASE -> PrimaryEmerald
        DividendChangeStatus.DECREASE -> ErrorRed
        DividendChangeStatus.MAINTAIN -> OnSurfaceVariant
        DividendChangeStatus.UNKNOWN -> TextMuted
    }
    MonitorCard(onClick = onClick, compact = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            MonitorBadge(row.code, PrimaryEmerald, compact = true)
            Text(row.name, modifier = Modifier.weight(1f), color = OnSurface,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
            val label = when (row.change) {
                DividendChangeStatus.INCREASE -> "増配"
                DividendChangeStatus.DECREASE -> "減配"
                DividendChangeStatus.MAINTAIN -> "維持"
                DividendChangeStatus.UNKNOWN -> "履歴未取得"
            }
            MonitorBadge(label, color, compact = true)
            Icon(Icons.Default.ChevronRight, "銘柄詳細を開く", tint = TextMuted, modifier = Modifier.size(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val streak = row.profile?.consecutiveYears?.takeIf { it > 0 }?.let { "・${it}年連続増配" }.orEmpty()
            Text(stringResource(R.string.dividend_change_analysis_cards_text_16 , row.sector, if (privacy) "•••" else number(row.shares.toDouble()), streak),
                modifier = Modifier.weight(1f), color = OnSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
            val muted = OnSurfaceVariant
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = muted, fontSize = 10.sp)) { append(resources.getString(R.string.dividend_change_analysis_cards_label_22)) }
                withStyle(SpanStyle(color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)) {
                    append(money(row.annualImpact, privacy, signed = true))
                }
            }, lineHeight = 21.sp)
        }
        AdaptiveRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceContainerLowest)
            .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InlineDividendValue(row.previousYear, row.previousDps, privacy, "前期実績", OnSurface, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = color, modifier = Modifier.size(13.dp))
            InlineDividendValue(row.currentYear, row.currentDps, privacy, "最新実績", color, Modifier.weight(1f))
            MonitorBadge(percent(row.growthRate, signed = true), color, compact = true)
        }
    }
}

@Composable
private fun InlineDividendValue(year: Int?, value: Double?, privacy: Boolean, fallback: String, color: Color, modifier: Modifier) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val muted = TextMuted
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = muted, fontSize = 10.sp)) { append(year?.let { resources.getString(R.string.dividend_change_analysis_cards_label_23, (it).toString()) } ?: "$fallback ") }
        withStyle(SpanStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)) { append(dps(value, privacy)) }
    }, modifier = modifier, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun MonitorSelectors(label: String?, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, color = TextMuted, fontSize = 10.sp)
        options.forEach { (id, text) ->
            val active = selected == id
            val color = if (active) PrimaryEmerald else OnSurfaceVariant
            Surface(shape = RoundedCornerShape(6.dp), color = if (active) PrimaryEmerald.copy(alpha = 0.13f) else SurfaceContainer,
                border = BorderStroke(1.dp, if (active) PrimaryEmerald.copy(alpha = 0.4f) else BorderColor)) {
                Box(Modifier.selectable(selected = active, onClick = { onSelect(id) }, role = Role.Tab)
                    .heightIn(min = 29.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(text, color = color, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun MonitorCard(onClick: (() -> Unit)? = null, compact: Boolean = false, tight: Boolean = false, heightScale: Float = 1f, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()
        .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        border = BorderStroke(1.dp, BorderColor)) {
        Column(
            Modifier
                .then(if (heightScale == 1f) Modifier else Modifier.layout { measurable, constraints ->
                    val naturalHeight = measurable.minIntrinsicHeight(constraints.maxWidth)
                    val scaledHeight = (naturalHeight * heightScale).roundToInt()
                        .coerceAtMost(constraints.maxHeight)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = scaledHeight, maxHeight = scaledHeight)
                    )
                    layout(placeable.width, scaledHeight) {
                        placeable.placeRelative(0, 0)
                    }
                })
                .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (tight) 2.dp else if (compact) 6.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (tight || compact) 4.dp else 10.dp),
            content = content
        )
    }
}

@Composable
private fun MonitorEmptyCard(message: String) {
    MonitorCard { Text(message, color = OnSurfaceVariant, fontSize = 12.sp) }
}

@Composable
private fun MonitorHeading(title: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
        Text(title, color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonitorBadge(text: String, color: Color, compact: Boolean = false) {
    Surface(shape = RoundedCornerShape(5.dp), color = color.copy(alpha = 0.10f), border = BorderStroke(0.5.dp, color.copy(alpha = 0.24f))) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = if (compact) 2.dp else 4.dp),
            color = color, fontSize = 10.sp, lineHeight = if (compact) 13.sp else TextUnit.Unspecified, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonitorProgress(progress: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(SurfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

private fun number(value: Double): String = com.kurogane.dividend.model.formatFinancialAmountDisplay(value)
private fun money(value: Double?, privacy: Boolean, signed: Boolean = false): String = when {
    value == null -> "未取得"
    privacy -> "¥•••••"
    else -> (if (signed && value != 0.0) if (value > 0) "+" else "−" else "") + "¥" + number(abs(value))
}
private fun dps(value: Double?, privacy: Boolean): String = when {
    value == null -> "未取得"
    privacy -> "¥•••"
    else -> "¥${number(value)}"
}
private fun percent(value: Double?, signed: Boolean = false): String = value?.takeIf { it.isFinite() }?.let {
    String.format(Locale.JAPAN, if (signed) "%+.1f%%" else "%.1f%%", it)
} ?: "未取得"
