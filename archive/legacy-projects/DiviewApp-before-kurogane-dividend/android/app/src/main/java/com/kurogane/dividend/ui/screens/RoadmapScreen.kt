package com.kurogane.dividend.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

private val TextLavender = Color(0xFFC0C1FF)
private val BorderBlue = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(viewModel: DividendViewModel) {
    var openStep by remember { mutableStateOf<Int?>(null) }
    
    // 1. Collect live native holdings state
    val holdings by viewModel.holdings.collectAsState()
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    // Calculate real-time annual expected dividend and monthly income
    val totalAnnualExpectedDividend = holdings.sumOf { 
        val rate = if (it.currency == "$") 150.0 else 1.0
        (it.shares * it.dps * rate).toLong()
    }
    val monthlyIncome = totalAnnualExpectedDividend / 12

    // Define 10 static milestone thresholds
    val milestoneSteps = listOf(
        MilestoneData(1, "STEP 1: 月3,000円", "電気代基本料金・カフェ代", 3000, "coffee", "月3,000円 (年3.6万円)。毎月の電気代基本料金や休日のカフェ代が不労所得でゼロに。最初の不労所得の確かな手応えを得るステージです。"),
        MilestoneData(2, "STEP 2: 月5,000円", "スマホ通信費＆サブスク自給", 5000, "smartphone", "月5,000円 (年6万円)。格安SIMの通信費やNetflix、Spotify、iCloudなどの主要デジタルサブスク利用料が自動でまかなえます。"),
        MilestoneData(3, "STEP 3: 月10,000円", "水道光熱費の基本料金カバー", 10000, "lightbulb", "月10,000円 (年12万円)。電気・ガス・水道の全基本料金が資本収入で賄われ、暮らしているだけで発生する固定費の土台が自動化されます。"),
        MilestoneData(4, "STEP 4: 月20,000円", "週末外食・書籍費自立", 20000, "restaurant", "月20,000円 (年24万円)。毎週末の気兼ねない外食ランチや、スキルアップのための技術書・ビジネス書購入費用が不労所得から拠出可能になります。"),
        MilestoneData(5, "STEP 5: 月30,000円", "水道光熱費の全額自給", 30000, "bolt", "月30,000円 (年36万円)。一人暮らし・少人数世帯の水道・電気・ガス代全額を完全カバー。生活インフラを資本収入のみで賄う大記録を達成！"),
        MilestoneData(6, "STEP 6: 月50,000円", "食費・日用品の全額自給", 50000, "shopping_cart", "月50,000円 (年60万円)。毎月の基本的な食生活および生活日用品が完全に自給可能になり、自給レベルが一気に引きあがる重要なステージです。"),
        MilestoneData(7, "STEP 7: 月100,000円", "住居費・家賃の完全カバー", 100000, "home", "月10万円 (年120万円)。家賃や住宅ローンの大半を配当収入のみで支払うことができる、実質的なハーフセミリタイア達成段階です。"),
        MilestoneData(8, "STEP 8: 月200,000円", "標準生活費自立 (Lean FIRE)", 200000, "flight", "月20万円 (年240万円)。基礎的な生活に必要とされるミニマルなコストが配当で満たされます。労働の選択が完全に自由になる「自由への門」です。"),
        MilestoneData(9, "STEP 9: 月300,000円", "ゆとりある生活 (Full FIRE)", 300000, "star", "月30万円 (年360万円)。一般的な世帯平均支出を大幅に上回り、趣味、教育、旅行、自己投資を楽しみながら優雅に暮らすFIRE本番期です。"),
        MilestoneData(10, "STEP 10: 月500,000円+", "経済的自由・資産自動増殖", 500000, "autorenew", "月50万円 (年600万円)。配当の全自動再投資と元本自体のキャピタルゲインが雪だるま式に自己増殖し始める、富豪ステージです。")
    )

    // Calculate dynamic stats across the 10 milestones
    val dynamicMilestones = milestoneSteps.map { step ->
        val isCompleted = monthlyIncome >= step.targetMonthly
        val prevStep = milestoneSteps.find { s -> s.step == step.step - 1 }
        val isPrevCompleted = prevStep == null || monthlyIncome >= prevStep.targetMonthly

        val status = if (isCompleted) "completed" else if (isPrevCompleted) "active" else "locked"
        val statusLabel = if (isCompleted) "達成済" else if (isPrevCompleted) "挑戦中" else "ロック中"
        val currentRate = if (isCompleted) 100f else (monthlyIncome.toFloat() / step.targetMonthly.toFloat()).coerceIn(0f, 0.99f)
        
        DynamicMilestone(
            step = step,
            status = status,
            statusLabel = statusLabel,
            currentRate = currentRate
        )
    }

    val highestCompletedStep = dynamicMilestones.findLast { it.status == "completed" }?.step?.step ?: 0
    val activeQuest = dynamicMilestones.find { it.status == "active" } ?: dynamicMilestones.find { it.status == "locked" } ?: dynamicMilestones.last()
    val activeStep = activeQuest.step
    val currentLevel = if (highestCompletedStep >= 10) 3 else if (highestCompletedStep >= 7) 3 else if (highestCompletedStep >= 5) 2 else 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Roadmap Quest",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            "不労所得 RPG • ステージ攻略",
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quest Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    if (highestCompletedStep >= 10) "全ステージ完全制覇！" else "LEVEL $currentLevel 進行中: STEP ${activeStep.step}",
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                if (highestCompletedStep >= 10) "ALL CLEAR (10/10)"
                                else if (highestCompletedStep >= 7) "LV.3 クリア度 (${highestCompletedStep - 7}/3)"
                                else if (highestCompletedStep >= 5) "LV.2 クリア度 (${highestCompletedStep - 5}/2)"
                                else "LV.1 クリア度 ($highestCompletedStep/5)",
                                color = SecondaryIndigo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Trophy Alert Banner
                        if (highestCompletedStep >= 10) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentAmber.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("LEVEL 3 完全制覇＆FIRE達成！ 👑", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("完全経済的自由期 (STEP 8〜10) を完全クリア！", color = OnSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = AccentAmber.copy(alpha = 0.2f)) {
                                        Text("FIRE達成", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = AccentAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (highestCompletedStep >= 7) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFC084FC).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("LEVEL 2 完全制覇達成！ 🚀", color = Color(0xFFC084FC), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("生活費自給・ミニFIRE期 (STEP 6〜7) を攻略完了", color = OnSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFC084FC).copy(alpha = 0.2f)) {
                                        Text("LV.2 CLEAR", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color(0xFFC084FC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (highestCompletedStep >= 5) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("LEVEL 1 完全制覇達成！ 🎉", color = PrimaryEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("生活インフラ自給期 (STEP 1〜5) をすべて攻略完了", color = OnSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = PrimaryEmerald.copy(alpha = 0.2f)) {
                                        Text("LV.1 CLEAR", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = SecondaryIndigo, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("LEVEL 1 攻略進行中...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("目標: 基礎生活固定費の全額自給 (月3万円)", color = OnSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(4.dp), color = SecondaryIndigo.copy(alpha = 0.15f)) {
                                        Text("${highestCompletedStep * 20}% CLEAR", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = SecondaryIndigo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("現在の配当獲得ステータス", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            "¥${currencyFormat.format(totalAnnualExpectedDividend)} / 年",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif
                        )
                        
                        Text(
                            "月間換算不労所得: 約 ¥${currencyFormat.format(monthlyIncome)} / 月",
                            color = PrimaryEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Target Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundDark.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "QUEST ${activeStep.step} 目標: ¥${currencyFormat.format(activeStep.targetMonthly)} / 月",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${(activeQuest.currentRate * 100).toInt()}% 達成",
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                var isProgressActive by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { isProgressActive = true }
                                val animatedProgress by animateFloatAsState(
                                    targetValue = if (isProgressActive) activeQuest.currentRate else 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "questProgress"
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = PrimaryEmerald,
                                    trackColor = SurfaceContainerHighest
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "現在実績: ¥${currencyFormat.format(monthlyIncome)}",
                                        color = OnSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                    
                                    val shortage = activeStep.targetMonthly - monthlyIncome
                                    if (shortage > 0) {
                                        Text(
                                            "あと ¥${currencyFormat.format(shortage)} / 月 で攻略",
                                            color = ErrorRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            "マイルストーン達成済！",
                                            color = PrimaryEmerald,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Milestone Steps list title
            item {
                Text(
                    "全10マイルストーン・クエストマップ",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            // Dynamic Milestone rows
            items(dynamicMilestones.size) { idx ->
                val item = dynamicMilestones[idx]
                val stepData = item.step
                val isCleared = item.status == "completed"
                val isCurrent = item.status == "active"
                val isOpen = openStep == stepData.step

                val rowBadgeColor = if (isCleared) {
                    PrimaryEmerald
                } else if (isCurrent) {
                    Color(0xFFF59E0B) // Amber
                } else {
                    OnSurfaceVariant // Gray/Slate
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) SurfaceContainerHigh else SurfaceContainerLow
                    ),
                    border = BorderStroke(0.5.dp, if (isCurrent) PrimaryEmerald.copy(alpha = 0.5f) else Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { openStep = if (isOpen) null else stepData.step }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCleared) PrimaryEmerald.copy(alpha = 0.15f) else if (isCurrent) PrimaryEmerald else SurfaceContainerHighest,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${stepData.step}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = if (isCurrent) Color.Black else if (isCleared) PrimaryEmerald else OnSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stepData.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(rowBadgeColor.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                item.statusLabel,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = rowBadgeColor
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        stepData.title,
                                        color = OnSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isCleared) "100% CLEAR" else "${(item.currentRate * 100).toInt()}%",
                                    color = if (isCleared || isCurrent) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
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
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = SurfaceContainerHighest, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                stepData.description,
                                color = TextLavender,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            val shortage = stepData.targetMonthly - monthlyIncome
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0C1322))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("目標額 (月額 / 年額)", fontSize = 9.sp, color = OnSurfaceVariant)
                                        Text("¥${currencyFormat.format(stepData.targetMonthly)} /月", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("年額 ¥${currencyFormat.format(stepData.targetMonthly * 12)}", fontSize = 9.sp, color = OnSurfaceVariant)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0C1322))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("攻略状況", fontSize = 9.sp, color = OnSurfaceVariant)
                                        if (isCleared) {
                                            Text("達成済 ✨", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                            Text("現在月額 ¥${currencyFormat.format(monthlyIncome)}", fontSize = 9.sp, color = PrimaryEmerald)
                                        } else {
                                            Text("あと ¥${currencyFormat.format(shortage)} /月", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                                            Text("年額 ¥${currencyFormat.format(shortage * 12)} 不足", fontSize = 9.sp, color = OnSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            if (!isCleared) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val shortageAnnual = shortage * 12
                                
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0C1322))
                                        .border(0.5.dp, BorderBlue, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        "利回り別・必要追加元本シミュレーション",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3.5 to "3.5%", 4.0 to "4.0%", 4.5 to "4.5%").forEach { (yield, label) ->
                                            val capital = Math.round(shortageAnnual / (yield / 100.0) / 10000.0)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (yield == 4.0) Color(0x1F10B981) else Color(0xFF171F33))
                                                    .border(0.5.dp, if (yield == 4.0) PrimaryEmerald.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(label, fontSize = 8.sp, color = if (yield == 4.0) PrimaryEmerald else OnSurfaceVariant)
                                                    Text("¥${capital}万", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (yield == 4.0) PrimaryEmerald else Color.White)
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
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

// Data structures for localized clean calculations
private data class MilestoneData(
    val step: Int,
    val name: String,
    val title: String,
    val targetMonthly: Int,
    val icon: String,
    val description: String
)

private data class DynamicMilestone(
    val step: MilestoneData,
    val status: String,
    val statusLabel: String,
    val currentRate: Float
)
