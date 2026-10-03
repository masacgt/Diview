package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: DividendViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val watchlist by viewModel.watchlist.collectAsState()
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Watchlist",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            "監視銘柄・目標利回り管理",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OnSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
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
            // Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("買場到達 1銘柄", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(" / 4銘柄監視中", color = OnSurfaceVariant, fontSize = 11.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BackgroundDark)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("買場アラート", fontSize = 10.sp, color = OnSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = true,
                                    onCheckedChange = {},
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryEmeraldContainer)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("平均現在利回り", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("3.84", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryEmerald, fontFamily = FontFamily.Monospace)
                                        Text("%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                    }
                                    Text("東証平均 +1.8%", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BackgroundDark.copy(alpha = 0.7f))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("配当ポテンシャル", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("¥", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("42,800", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                        Text("/年", fontSize = 9.sp, color = OnSurfaceVariant)
                                    }
                                    Text("各1単元保有試算", color = OnSurfaceVariant, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Stock Cards
            items(watchlist) { stock ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (stock.badge != null) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryEmerald,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stock.badge, color = OnPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (stock.badge != null) PrimaryEmerald else SurfaceContainerHighest
                                ) {
                                    Text(
                                        stock.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stock.badge != null) OnPrimary else Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stock.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            }
                            Text(
                                "${stock.currency}${stock.price.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("現在利回り", color = OnSurfaceVariant, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "${stock.currentYield}%",
                                            fontWeight = FontWeight.Bold,
                                            color = if (stock.isReady) PrimaryEmerald else Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SurfaceContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("目標:", color = OnSurfaceVariant, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, -0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "-", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                        Text(
                                            "${stock.targetYield}%",
                                            color = PrimaryEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = { viewModel.updateTargetYield(stock.code, 0.05) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "+", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { stock.achievementRate / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = if (stock.isReady) PrimaryEmerald else PrimaryEmeraldContainer,
                                    trackColor = SurfaceContainerHighest
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "買場目安: ${stock.currency}${stock.targetPrice.toInt()}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("配当 ${stock.currency}${stock.dps}/株", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f).height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmeraldContainer, contentColor = OnPrimary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("保有株に登録", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalIconButton(
                                onClick = { onNavigateToDetail(stock.code) },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceContainer)
                            ) {
                                Text("詳細", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
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

private fun Modifier.scale(scale: Float): Modifier = this
