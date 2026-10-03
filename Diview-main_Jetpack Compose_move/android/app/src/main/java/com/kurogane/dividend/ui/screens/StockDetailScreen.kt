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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {}
) {
    var simAddedShares by remember { mutableStateOf(100f) }
    var simText by remember { mutableStateOf("100") }
    val baseShares = 100
    val price = 2600
    val dps = 72.8
    val per = 10.4
    val pbr = 1.12

    val totalSimShares = baseShares + simAddedShares.toInt()
    val simAnnualDiv = (totalSimShares * dps).toInt()
    val simMonthlyDiv = simAnnualDiv / 12
    val addedCapital = simAddedShares.toInt() * price
    val addedAnnualDiv = (simAddedShares.toInt() * dps).toInt()
    val scenario = viewModel.valuationScenarios.firstOrNull { per <= it.perMax && pbr <= it.pbrMax }
        ?: viewModel.valuationScenarios.last()

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
                                    Text("トヨタ自動車", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = SurfaceContainerHighest) {
                                        Text("7203", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = OnSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Text("東証PRM • 新NISA 成長投資枠", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(onClick = {}) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text("現在株価 リアルタイム", color = OnSurfaceVariant, fontSize = 10.sp)
                                Text("¥2,600/株", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Text("+¥35 (+1.36%)", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
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
                                    Text("¥7,280/年", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text("月 ¥606/月", color = OnSurfaceVariant, fontSize = 10.sp)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("シミュレーション (${totalSimShares}株)", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("¥${simAnnualDiv}/年", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text("月 約¥${simMonthlyDiv}/月", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                    Text("単元 (100株単位)", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = simText,
                                        onValueChange = { raw ->
                                            val digits = raw.filter { it.isDigit() }.take(4)
                                            simText = digits
                                            val value = digits.toIntOrNull()?.coerceIn(0, 500) ?: 0
                                            simAddedShares = value.toFloat()
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        suffix = { Text("株") }
                                    )
                                    listOf(-10, 10, 100).forEach { delta ->
                                        OutlinedButton(onClick = {
                                            val next = (simAddedShares.toInt() + delta).coerceIn(0, 500)
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
                            value = simAddedShares,
                            onValueChange = {
                                simAddedShares = it
                                simText = it.toInt().toString()
                            },
                            valueRange = 0f..500f,
                            steps = 49,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("0株", color = OnSurfaceVariant, fontSize = 10.sp)
                            Text("+250株", color = OnSurfaceVariant, fontSize = 10.sp)
                            Text("+500株 (計600株)", color = OnSurfaceVariant, fontSize = 10.sp)
                        }

                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("追加必要資金", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text("¥" + String.format("%,d", addedCapital), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("シミュレーション年間受取", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text("¥" + String.format("%,d", simAnnualDiv), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("年間配当増額分", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text("+¥" + String.format("%,d", addedAnnualDiv) + " / 年", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("新月間不労所得", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Text("約¥" + String.format("%,d", simMonthlyDiv) + " / 月", fontWeight = FontWeight.Bold, color = Color.White)
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
                        Surface(shape = RoundedCornerShape(9.dp), color = SurfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "【スコア 88】 " + scenario.id + ". " + scenario.title + " (PER " + per + "倍 / PBR " + pbr + "倍)",
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                        Text("PER: " + per + "倍    PBR: " + pbr + "倍", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
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
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
