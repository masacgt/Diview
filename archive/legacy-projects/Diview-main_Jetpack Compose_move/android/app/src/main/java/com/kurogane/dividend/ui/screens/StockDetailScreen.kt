package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.data.evaluateStockAnalysis
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String,
    viewModel: DividendViewModel,
    onBack: () -> Unit = {}
) {
    val holdings by viewModel.holdings.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val sharedSecurities by viewModel.sharedSecurities.collectAsState()
    val valuationScenarios by viewModel.valuationScenarios.collectAsState()
    val stockAnalysisDefinitions by viewModel.stockAnalysisDefinitions.collectAsState()
    val matchingHoldings = holdings.filter { it.code.equals(stockCode, ignoreCase = true) }
    val holding = matchingHoldings.firstOrNull()
    val watchedStock = watchlist.firstOrNull { it.code.equals(stockCode, ignoreCase = true) }
    val shared = sharedSecurities[stockCode]
    var simAddedShares by remember { mutableStateOf(100f) }
    var simText by remember { mutableStateOf("100") }
    val baseShares = matchingHoldings.sumOf { it.shares }
    val price = shared?.price ?: holding?.price ?: watchedStock?.price ?: 0.0
    val dps = shared?.annualDps ?: holding?.dps ?: watchedStock?.dps ?: 0.0
    val per = shared?.per
    val pbr = shared?.pbr
    val stockName = shared?.name ?: holding?.name ?: watchedStock?.name ?: viewModel.securityNameForCode(stockCode) ?: stockCode
    val quoteCurrency = holding?.currency ?: watchedStock?.currency ?: "¥"
    val market = holding?.market ?: watchedStock?.market ?: ""
    val accountType = holding?.accountType ?: ""
    val currentAnnualDiv = matchingHoldings.sumOf { it.shares * (shared?.annualDps ?: it.dps) }.toInt()
    val acquisitionPrice = holding?.acqPrice ?: price
    val yieldOnCost = if (acquisitionPrice > 0.0) dps / acquisitionPrice * 100.0 else 0.0

    val totalSimShares = baseShares + simAddedShares.toInt()
    val addedAnnualDiv = (simAddedShares.toInt() * dps).toInt()
    val simAnnualDiv = currentAnnualDiv + addedAnnualDiv
    val simMonthlyDiv = simAnnualDiv / 12
    val addedCapital = (simAddedShares.toInt() * price).toInt()
    val scenario = if (per != null && pbr != null) {
        valuationScenarios.firstOrNull { per <= it.perMax && pbr <= it.pbrMax }
            ?: valuationScenarios.lastOrNull()
    } else null

    val stockAnalysis = evaluateStockAnalysis(shared, stockAnalysisDefinitions)
    val metricResults = stockAnalysis.metrics
    val overallScore = stockAnalysis.score
    val overallBand = stockAnalysis.overallBand

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("銘柄情報", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stock Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stockName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = SurfaceContainerHighest) {
                                        Text(stockCode, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = OnSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Text(listOf(market, accountType).filter { it.isNotBlank() }.joinToString(" • "), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(onClick = {
                                if (watchedStock != null) {
                                    viewModel.removeWatchlistStock(stockCode)
                                } else {
                                    val targetYield = 3.0
                                    viewModel.addWatchlistStock(WatchlistStock(
                                        code = stockCode,
                                        name = stockName,
                                        market = market,
                                        sector = holding?.sector ?: "未分類",
                                        category = when (holding?.category) {
                                            com.kurogane.dividend.model.Category.US -> "us"
                                            com.kurogane.dividend.model.Category.FUND -> "fund"
                                            else -> "jp"
                                        },
                                        price = price,
                                        currency = holding?.currency ?: "¥",
                                        dps = dps,
                                        currentYield = if (price > 0.0) dps / price * 100.0 else 0.0,
                                        targetYield = targetYield,
                                        targetPrice = if (dps > 0.0) dps / (targetYield / 100.0) else 0.0,
                                        dailyChange = holding?.dailyChange ?: "更新データなし",
                                        isPositive = holding?.isPositive ?: true,
                                        payoutMonths = holding?.dividendMonths ?: emptyList(),
                                        payoutTiming = holding?.nextPayoutTiming ?: "予定データなし",
                                        desc = market,
                                        defaultShares = holding?.shares ?: 100
                                    ))
                                }
                            }) {
                                Icon(
                                    if (watchedStock != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (watchedStock != null) "Watchlistから解除" else "Watchlistに登録",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text("現在株価", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(if (price > 0.0) quoteCurrency + String.format("%,.0f", price) + "/株" else "価格未取得", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text(holding?.dailyChange ?: "更新データなし", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Annual dividend summary shown directly beneath the quote in the reference screen.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("年間配当シミュレーション", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = .18f)) {
                                Text("年2回配当", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("年間受取予定額 (${baseShares}株保有)", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("${quoteCurrency}${String.format(Locale.US, "%,d", currentAnnualDiv)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text("月換算 約${quoteCurrency}${String.format(Locale.US, "%,d", currentAnnualDiv / 12)} / 月", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("配当利回り (現在)", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text(String.format(Locale.US, "%.2f%%", if (price > 0) dps / price * 100 else 0.0), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("取得ベース ${String.format(Locale.US, "%.2f%%", yieldOnCost)} YOC", color = OnSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = .08f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("次回配当予定", color = OnSurfaceVariant, fontSize = 10.sp); Text(holding?.nextPayoutTiming ?: watchedStock?.payoutTiming ?: "予定データなし", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(holding?.nextPayoutAmount ?: "", color = OnSurfaceVariant, fontSize = 10.sp) }
                            Column(horizontalAlignment = Alignment.End) { Text("権利確定月", color = OnSurfaceVariant, fontSize = 10.sp); Text(if (holding?.dividendMonths.orEmpty().isEmpty()) "—" else holding!!.dividendMonths.joinToString("月 / ", postfix = "月末"), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("年2回中間・期末", color = OnSurfaceVariant, fontSize = 10.sp) }
                        }
                    }
                }
            }

            // Buy-more simulator
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("買い増しシミュレーター", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            }
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)) {
                                Text(
                                    "+${simAddedShares.toInt()}株 (合計${totalSimShares}株)",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Simulator comparison box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("現在保有 (${baseShares}株)", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Text(quoteCurrency + String.format("%,d", currentAnnualDiv) + "/年", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text("月 " + quoteCurrency + String.format("%,d", currentAnnualDiv / 12) + "/月", color = OnSurfaceVariant, fontSize = 10.sp)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("シミュレーション (${totalSimShares}株)", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(quoteCurrency + String.format("%,d", simAnnualDiv) + "/年", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text("月 約" + quoteCurrency + String.format("%,d", simMonthlyDiv) + "/月", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("追加買増株数を手動指定", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                    Text("株数を指定", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = simText,
                                        onValueChange = { raw ->
                                            val digits = raw.filter { it.isDigit() }.take(6)
                                            simText = digits
                                            val value = digits.toIntOrNull()?.coerceIn(0, 100000) ?: 0
                                            simAddedShares = value.toFloat()
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        suffix = { Text("株") }
                                    )
                                    listOf(-10, 10, 100).forEach { delta ->
                                        OutlinedButton(onClick = {
                                            val next = (simAddedShares.toInt() + delta).coerceIn(0, 100000)
                                            simAddedShares = next.toFloat()
                                            simText = next.toString()
                                        }) { Text(if (delta > 0) "+$delta" else "$delta") }
                                    }
                                    IconButton(onClick = {
                                        simAddedShares = 0f
                                        simText = "0"
                                    }) { Icon(Icons.Default.Refresh, null) }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 100, 250, 500).forEach { preset ->
                                OutlinedButton(
                                    onClick = {
                                        simAddedShares = preset.toFloat()
                                        simText = preset.toString()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (preset == 0) "現状維持" else "+" + preset + "株", fontSize = 10.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            onValueChange = {
                                simAddedShares = it
                                simText = it.toInt().toString()
                            },
                            value = simAddedShares.coerceIn(0f, 10000f),
                            valueRange = 0f..10000f,
                            steps = 99,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0株", color = OnSurfaceVariant, fontSize = 10.sp)
                            Text("+5,000株", color = OnSurfaceVariant, fontSize = 10.sp)
                            Text("+10,000株 (計${baseShares + 10000}株)", color = OnSurfaceVariant, fontSize = 10.sp)
                        }

                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("追加必要資金", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text(quoteCurrency + String.format("%,d", addedCapital), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("シミュレーション年間受取", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text(quoteCurrency + String.format("%,d", simAnnualDiv), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("年間配当増額分", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text("+" + quoteCurrency + String.format("%,d", addedAnnualDiv) + " / 年", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("新月間不労所得", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text("約" + quoteCurrency + String.format("%,d", simMonthlyDiv) + " / 月", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("AI買場診断・バリュエーション (50シナリオ)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (scenario != null && per != null && pbr != null) {
                            Surface(shape = RoundedCornerShape(9.dp), color = SurfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    scenario.id.toString() + ". " + scenario.title + " (PER " + String.format("%.2f", per) + "倍 / PBR " + String.format("%.2f", pbr) + "倍)",
                                    modifier = Modifier.padding(10.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            Text("PER: " + String.format("%.2f", per) + "倍    PBR: " + String.format("%.2f", pbr) + "倍", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            LinearProgressIndicator(progress = { (per / 20.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(scenario.id.toString() + ". " + scenario.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(scenario.badge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(scenario.comment, color = Color.White, fontSize = 11.sp, lineHeight = 17.sp)
                                }
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(9.dp), color = SurfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                                Text("PER/PBRの実データがないため、買場診断は表示できません。", modifier = Modifier.padding(12.dp), color = OnSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("銘柄総合分析ステータス", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (overallScore != null && overallBand != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("総合 ${overallBand.grade}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(overallBand.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("${overallScore}/100", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                stockAnalysis.narrative.ifBlank { overallBand.comment },
                                color = OnSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )
                            HorizontalDivider()
                            metricResults.forEach { result ->
                                val rule = result.rule
                                val value = result.value
                                val band = result.band
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(rule.label, color = OnSurfaceVariant, fontSize = 10.sp)
                                    Text(
                                        when (rule.key) {
                                            "dividend_growth_years" -> "${value.toInt()}年 / ${band.label}"
                                            else -> String.format(Locale.JAPAN, "%.1f / %s", value, band.label)
                                        },
                                        color = if (band.score >= 70) MaterialTheme.colorScheme.primary else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (band.comment.isNotBlank()) {
                                    Text(
                                        band.comment,
                                        color = OnSurfaceVariant,
                                        fontSize = 9.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        } else {
                            Text("分析に必要な財務・配当データを取得できた項目から順次評価します。", color = OnSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
