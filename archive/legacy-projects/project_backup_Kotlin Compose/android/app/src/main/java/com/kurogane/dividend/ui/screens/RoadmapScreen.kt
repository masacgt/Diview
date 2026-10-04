package com.kurogane.dividend.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen() {
    var openStep by remember { mutableStateOf<Int?>(6) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Roadmap Quest", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        Text("不労所得 RPG • ステージ攻略", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceVariant, fontSize = 10.sp))
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
            // Quest Hero Card
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = CircleShape, color = PrimaryEmerald.copy(alpha = 0.15f)) {
                                Text(
                                    "LEVEL 2 進行中: STEP 6",
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text("LV.1 制覇済み (5/10)", color = SecondaryIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("現在の配当獲得ステータス", color = OnSurfaceVariant, fontSize = 10.sp)
                        Text(
                            "¥374,500 / 年",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("月間換算不労所得: 約 ¥31,208 / 月", color = PrimaryEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Box
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BackgroundDark.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("QUEST 6 目標: ¥50,000 / 月", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("62.4% 達成", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                var isProgressActive by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { isProgressActive = true }
                                val animatedProgress by animateFloatAsState(
                                    targetValue = if (isProgressActive) 0.624f else 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "questProgress"
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = PrimaryEmerald,
                                    trackColor = SurfaceContainerHighest
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("現在実績: ¥31,208", color = OnSurfaceVariant, fontSize = 10.sp)
                                    Text("あと ¥18,792 / 月 で攻略", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Milestone Steps list
            item {
                Text("全10マイルストーン・クエストマップ", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }

            val steps = listOf(
                Triple(1, "STEP 1: 月3,000円", "電気代基本料金・カフェ代"),
                Triple(2, "STEP 2: 月5,000円", "スマホ通信費＆サブスク自給"),
                Triple(3, "STEP 3: 月10,000円", "水道光熱費の基本料金カバー"),
                Triple(4, "STEP 4: 月20,000円", "週末外食・書籍費自立"),
                Triple(5, "STEP 5: 月30,000円", "水道光熱費の全額自給"),
                Triple(6, "STEP 6: 月50,000円", "食費・日用品の全額自給 (挑戦中)"),
                Triple(7, "STEP 7: 月100,000円", "住居費・家賃の完全カバー"),
                Triple(8, "STEP 8: 月200,000円", "標準生活費自立 (Lean FIRE)"),
                Triple(9, "STEP 9: 月300,000円", "ゆとりある生活 (Full FIRE)"),
                Triple(10, "STEP 10: 月500,000円+", "経済的自由・資産自動増殖")
            )

            items(steps.size) { idx ->
                val (num, title, sub) = steps[idx]
                val isCleared = num <= 5
                val isCurrent = num == 6
                val isOpen = openStep == num

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) SurfaceContainerHigh else SurfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.clickable { openStep = if (isOpen) null else num }.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCleared) PrimaryEmerald.copy(alpha = 0.15f) else if (isCurrent) PrimaryEmerald else SurfaceContainerHighest,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "$num",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isCurrent) OnPrimary else if (isCleared) PrimaryEmerald else OnSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                    Text(sub, color = OnSurfaceVariant, fontSize = 10.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isCleared) "100% CLEAR" else if (isCurrent) "62.4%" else "未達成",
                                    color = if (isCleared || isCurrent) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isOpen) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = SurfaceContainerHighest)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "配当利回り4.0%想定時の必要追加元本目安などをシミュレーション可能。",
                                color = OnSurfaceVariant,
                                fontSize = 11.sp
                            )
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
