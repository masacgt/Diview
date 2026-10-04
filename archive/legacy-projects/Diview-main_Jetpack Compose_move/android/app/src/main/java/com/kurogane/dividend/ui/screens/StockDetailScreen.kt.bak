package com.kurogane.dividend.ui.screens

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String = "7203",
    viewModel: DividendViewModel,
    onBack: () -> Unit = {}
) {
    var simAddedShares by remember { mutableStateOf(100f) }
    val baseShares = 100
    val price = 2600
    val dps = 72.8

    val totalSimShares = baseShares + simAddedShares.toInt()
    val simAnnualDiv = (totalSimShares * dps).toInt()
    val simMonthlyDiv = simAnnualDiv / 12

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
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
                                Text("東証PRM • 新NISA 成長投資枠", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(onClick = {}) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = PrimaryEmerald)
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
                            Surface(shape = RoundedCornerShape(8.dp), color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text("+¥35 (+1.36%)", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Calculate, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("買い増しシミュレーター", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            }
                            Text("+${simAddedShares.toInt()}株 (計${totalSimShares}株)", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Simulator comparison box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark,
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
                                    Text("シミュレーション (${totalSimShares}株)", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("¥${simAnnualDiv}/年", fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                                    Text("月 約¥${simMonthlyDiv}/月", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Slider(
                            value = simAddedShares,
                            onValueChange = { simAddedShares = it },
                            valueRange = 0f..500f,
                            steps = 4,
                            colors = SliderDefaults.colors(thumbColor = PrimaryEmerald, activeTrackColor = PrimaryEmerald)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
