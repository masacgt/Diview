package com.kurogane.dividend.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(viewModel: DividendViewModel) {
    var openStep by remember { mutableStateOf<Int?>(null) }
    var selectedStageFilter by remember { mutableIntStateOf(1) } // 0: All, 1: LV.1, 2: LV.2, 3: LV.3

    // Bottom Sheet State for multi-account allocation modal
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Bottom Sheet State for Share Achievement modal (Added for sharing progress)
    var showShareBottomSheet by remember { mutableStateOf(false) }
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.roadmapScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.roadmapScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.roadmapScrollIndex = index
                viewModel.roadmapScrollOffset = offset
            }
    }

    // 1. Collect live native holdings state
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val currencyFormat = NumberFormat.getNumberInstance(Locale.JAPAN)

    // Calculate real-time annual expected dividend and monthly income
    val totalAnnualExpectedDividend = holdings.sumOf { 
        it.getEffectiveAnnualDividend(taxRateMode).toLong()
    }
    val monthlyIncome = totalAnnualExpectedDividend / 12

    // Define the 30 static milestone thresholds
    val milestoneSteps = listOf(
        MilestoneData(1, "STEP 1: 月1,000円", "コンビニコーヒーやお菓子代を自給", 1000, "coffee", "労働以外の方法で収入を得る感覚をつかむ最初のステップです。金額は小さくても、資産収入を生活に役立てる経験になります。"),
        MilestoneData(2, "STEP 2: 月2,000円", "音楽サブスクと少額のアプリ課金をカバー", 2000, "music_note", "音楽やデジタルコンテンツの費用を資産収入で賄えます。毎月の支出が一つ減る、小さな達成感を得られます。"),
        MilestoneData(3, "STEP 3: 月3,000円", "電気代の基本料金、またはカフェ代を自給", 3000, "lightbulb", "毎月かかる固定費の一部を賄えます。カフェ代に充てれば、日常の楽しみを資産収入で支えられます。"),
        MilestoneData(4, "STEP 4: 月4,000円", "格安SIM1回線分の通信費をカバー", 4000, "smartphone", "スマートフォンの通信費を資産収入で賄えるようになります。毎月の支出を減らし、家計に余裕をつくる一歩です。"),
        MilestoneData(5, "STEP 5: 月5,000円", "動画・音楽サブスクを複数カバー", 5000, "movie", "Netflix、YouTube Premium、Amazon Primeなど、日常的に利用する娯楽サービスの費用をまとめて賄えます。"),
        MilestoneData(6, "STEP 6: 月6,000円", "自宅のインターネット回線・Wi-Fi代を自給", 6000, "wifi", "スマートフォンに続き、自宅のインターネット費用も資産収入で賄えるようになります。通信にかかる支出をさらに減らせます。"),
        MilestoneData(7, "STEP 7: 月8,000円", "水道・光熱費の基本料金をカバー", 8000, "water_drop", "電気・ガス・水道の基本料金を賄えます。生活に必要な固定費の一部を、労働以外の収入で支えられる段階です。"),
        MilestoneData(8, "STEP 8: 月10,000円", "書籍・アプリ・勉強代などの自己投資を自給", 10000, "menu_book", "月1万円に到達するステップです。知識やスキルを身につけるための費用に充てれば、将来の収入や資産形成にもつながります。"),
        MilestoneData(9, "STEP 9: 月15,000円", "電気・ガスの使用料をほぼ全額カバー", 15000, "bolt", "基本料金に加え、実際に使った分の電気・ガス代もほぼ賄えます。季節によって変わる光熱費への不安を減らせます。"),
        MilestoneData(10, "STEP 10: 月20,000円", "週末の外食や趣味にかかる費用を自給", 20000, "restaurant", "固定費だけでなく、余暇や趣味の費用も資産収入で賄えるようになります。生活を楽しむための支出を支えられる段階です。"),
        MilestoneData(11, "STEP 11: 月25,000円", "通信費と水道・光熱費を自給", 25000, "wifi", "通信費と水道・光熱費を資産収入で賄えます。ここから、食費や住居費など生活費の大きな項目に取り組みます。"),
        MilestoneData(12, "STEP 12: 月30,000円", "インフラ費と基本的な娯楽費を自給", 30000, "bolt", "通信費や水道・光熱費に加えて、身近な娯楽の費用も賄えます。給与から支払う毎月の支出が減り、手元に残るお金が増えます。"),
        MilestoneData(13, "STEP 13: 月35,000円", "日用品・雑費をカバー", 35000, "shopping_cart", "トイレットペーパーや洗剤、スキンケア用品、家具の買い替えなどにかかる費用を賄えます。生活を維持するための細かな支出が減ります。"),
        MilestoneData(14, "STEP 14: 月40,000円", "自炊を中心とした基本的な食費をカバー", 40000, "restaurant", "日々の食費の基本部分を資産収入で賄えます。仕事を失った場合にも、食費を支える収入があるという安心につながります。"),
        MilestoneData(15, "STEP 15: 月45,000円", "食費と日用品の基本分を自給", 45000, "shopping_cart", "食費と日用品の費用をまとめて賄えます。生活に必要な支出の多くを資産収入で支えられる段階です。"),
        MilestoneData(16, "STEP 16: 月50,000円", "外食を含む食費と日用品を自給", 50000, "shopping_cart", "毎月の資産収入が5万円に達すると、一人暮らしの食費や日用品など、生活を支える支出を労働収入に頼らず賄えるようになります。家計と働き方の選択肢が広がる、大きな節目です。（現在、この目標に向けて挑戦中）"),
        MilestoneData(17, "STEP 17: 月60,000円", "地方都市の家賃をカバー", 60000, "home", "生活費の中でも大きな割合を占める家賃を賄う段階です。地域や物件によっては、地方都市のワンルームの家賃をカバーできます。"),
        MilestoneData(18, "STEP 18: 月70,000円", "都市部のワンルームの家賃をカバー", 70000, "home", "都市部のワンルームの家賃も、資産収入で賄える水準です。家計に占める大きな固定費が減り、資産形成に回せるお金が増えます。"),
        MilestoneData(19, "STEP 19: 月80,000円", "実家暮らしの場合、基本的な生活費を自給", 80000, "home", "実家暮らしなら、生活に必要な費用を資産収入で賄える段階です。働く収入を自己実現や貯蓄に充てる選択肢が生まれます。"),
        MilestoneData(20, "STEP 20: 月100,000円", "更新料などを含む住居費をカバー", 100000, "home", "家賃に加え、更新料や火災保険、引っ越し費用も含めて住居費を賄うことを目指します。住まいにかかる費用を資産収入で支えられる段階です。"),
        MilestoneData(21, "STEP 21: 月120,000円", "家賃・食費・インフラ費など基本生活費をカバー", 120000, "home", "家賃、食費、インフラ費を資産収入で賄える水準です。支出を抑えた暮らしなら、働いて得る収入がなくても生活できるという安心につながります。"),
        MilestoneData(22, "STEP 22: 月150,000円", "サイドFIREを目指す水準", 150000, "flight", "週2〜3日働きながら、生活にゆとりを持てる水準を目指します。働く日数や仕事を選びやすくなり、通勤や残業の負担も減らせます。"),
        MilestoneData(23, "STEP 23: 月180,000円", "都市部での独身生活費をカバー", 180000, "location_city", "東京などの都市部で暮らす場合も、独身の生活費を資産収入で賄える水準です。自分の時間を自分の判断で使いやすくなります。"),
        MilestoneData(24, "STEP 24: 月200,000円", "標準的な生活費を賄う水準・Lean FIRE", 200000, "flight", "独身で生活する場合の、完全リタイアの目安となる水準です。仕事を続けるかどうかを、自分の希望に合わせて選びやすくなります。"),
        MilestoneData(25, "STEP 25: 月250,000円", "都市部でゆとりのある独身生活を送る", 250000, "star", "基本的な生活費に加え、趣味や旅行、少しぜいたくな食事にも資産収入を充てられます。お金を理由に諦めることが減り、選択肢が広がります。"),
        MilestoneData(26, "STEP 26: 月300,000円", "趣味や旅行を楽しめる生活・Full FIRE", 300000, "flight", "Full FIREを目指す水準です。資産収入が一般的な現役世代の平均手取り額を上回り、旅行や好きな活動に時間とお金を使いやすくなります。"),
        MilestoneData(27, "STEP 27: 月350,000円", "二人暮らし・カップル世帯の基本生活費をカバー", 350000, "people", "一人分の生活費だけでなく、パートナーとの暮らしにかかる基本的な費用も資産収入で賄える水準です。二人で過ごす時間や、家計の選択肢を広げられます。"),
        MilestoneData(28, "STEP 28: 月400,000円", "子どものいる世帯の生活費をカバー", 400000, "family_restroom", "子どものいる世帯の基本的な生活費を賄うことを目指します。家族で過ごす時間や、教育など将来に向けた選択肢を確保しやすくなります。"),
        MilestoneData(29, "STEP 29: 月500,000円", "生活費を上回る収入を得て、資産の増加を目指す", 500000, "trending_up", "毎月の資産収入が一般的な生活費を大きく上回る水準です。使い切らなかった分を再投資すれば、資産の増加を目指せます。"),
        MilestoneData(30, "STEP 30: 月600,000円", "経済的な選択肢が大きく広がる水準", 600000, "autorenew", "このロードマップの最終目標です。FIREを達成した後も、資産収入によって暮らしの選択肢をさらに広げられる水準を目指します。お金のために働く時間を減らし、自分の望む生活に時間を使えるようになります。")
    )

    // Calculate dynamic stats across the 30 milestones
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
    val currentLevel = when {
        highestCompletedStep >= 20 -> 3
        highestCompletedStep >= 10 -> 2
        else -> 1
    }
    val levelOneLabel = if (highestCompletedStep >= 10) "LV.1 制覇" else "LV.1 挑戦中"
    val levelTwoLabel = when {
        highestCompletedStep >= 20 -> "LV.2 制覇"
        currentLevel == 2 -> "LV.2 挑戦中"
        else -> "LV.2 ロック"
    }
    val levelThreeLabel = when {
        highestCompletedStep >= 30 -> "LV.3 制覇"
        currentLevel == 3 -> "LV.3 挑戦中"
        else -> "LV.3 ロック"
    }

    Scaffold(
        containerColor = BackgroundDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = PrimaryEmerald,
                contentColor = OnPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "複数口座一括配分登録",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quest Hero Card Dashboard
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, PrimaryEmerald.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "● LEVEL $currentLevel 進行中: STEP ${activeStep.step}",
                                    color = PrimaryEmerald,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = AppTextSize.dense,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, SecondaryIndigo.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "🏆 クリア済み ($highestCompletedStep/30)",
                                    color = SecondaryIndigo,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Trophy banner appears after all ten Level 1 steps are cleared.
                        if (highestCompletedStep >= 10) Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryEmerald.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentAmber.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = AccentAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "LEVEL 1 完全制覇達成！ 👑",
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.dense
                                        )
                                        Text(
                                            "生活インフラ自給期 (STEP 1〜10) を踏破完了",
                                            color = OnSurfaceVariant,
                                            fontSize = AppTextSize.dense
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PrimaryEmerald.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "ALL CLEAR",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("現在の配当獲得ステータス", color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            "¥${currencyFormat.format(totalAnnualExpectedDividend)} / 年",
                            fontSize = AppTextSize.hero,
                            fontWeight = FontWeight.Black,
                            color = OnSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        
                        Text(
                            text = "月間換算不労所得: 約 ¥${currencyFormat.format(monthlyIncome)} / 月",
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target Box with dynamic flag shortage calculations matching image
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundDark.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "QUEST ${activeStep.step} 目標: ¥${currencyFormat.format(activeStep.targetMonthly)} / 月",
                                            color = OnSurface,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "${(activeQuest.currentRate * 100).toInt()}% 達成",
                                        color = PrimaryEmerald,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = AppTextSize.caption
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
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PrimaryEmerald,
                                    trackColor = SurfaceContainerHighest
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "現在実績: ¥${currencyFormat.format(monthlyIncome)}",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                    
                                    val shortage = activeStep.targetMonthly - monthlyIncome
                                    if (shortage > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Flag, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                "あと ¥${currencyFormat.format(shortage)} / 月 で攻略",
                                                color = ErrorRed,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Text(
                                            "マイルストーン達成済！",
                                            color = PrimaryEmerald,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic simulation tip card
                        val shortage = maxOf(0L, activeStep.targetMonthly - monthlyIncome)
                        val shortageAnnual = shortage * 12
                        val neededPrincipalAt4 = Math.round(shortageAnnual / 0.04 / 10000.0)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "年額 +¥${currencyFormat.format(shortageAnnual)} でSTEP ${activeStep.step}クリア。配当利回り 4.0% 想定時、必要な追加投資元本は約 ¥${neededPrincipalAt4}万円 です。",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Share & Save Buttons matching Image 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showShareBottomSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("達成度を共有する", color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = OnSurface, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("画像で保存", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 2: "3大ステージ・レベル帯" Grid selection
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("3大ステージ・レベル帯", fontWeight = FontWeight.ExtraBold, color = OnSurface, fontSize = AppTextSize.body)
                        }
                        Text("タップしてジャンプ", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Level 1 Filter Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStageFilter = 1 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStageFilter == 1) SurfaceContainerHigh else SurfaceContainerLow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedStageFilter == 1) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(levelOneLabel, color = if (selectedStageFilter == 1) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        if (highestCompletedStep >= 10) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = if (highestCompletedStep >= 10) PrimaryEmerald else AccentAmber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text("インフラ自給期", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text("STEP 1-10", color = if (selectedStageFilter == 1) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Level 2 Filter Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStageFilter = 2 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStageFilter == 2) SurfaceContainerHigh else SurfaceContainerLow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedStageFilter == 2) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(levelTwoLabel, color = if (selectedStageFilter == 2) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        when {
                                            highestCompletedStep >= 20 -> Icons.Default.CheckCircle
                                            currentLevel == 2 -> Icons.Default.PlayCircle
                                            else -> Icons.Default.Lock
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            highestCompletedStep >= 20 -> PrimaryEmerald
                                            currentLevel == 2 -> AccentAmber
                                            else -> OnSurfaceVariant
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text("生活費・ミニFIRE", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("STEP 11-20", color = if (selectedStageFilter == 2) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Level 3 Filter Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStageFilter = 3 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStageFilter == 3) SurfaceContainerHigh else SurfaceContainerLow
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selectedStageFilter == 3) PrimaryEmerald else Color.Transparent
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(levelThreeLabel, color = if (selectedStageFilter == 3) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        when {
                                            highestCompletedStep >= 30 -> Icons.Default.CheckCircle
                                            currentLevel == 3 -> Icons.Default.PlayCircle
                                            else -> Icons.Default.Lock
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            highestCompletedStep >= 30 -> PrimaryEmerald
                                            currentLevel == 3 -> AccentAmber
                                            else -> OnSurfaceVariant
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text("完全FIRE期", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text("STEP 21-30", color = if (selectedStageFilter == 3) OnSurfaceVariant else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Milestone Steps list title
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckBox, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "全30マイルストーン・クエストマップ",
                        fontWeight = FontWeight.ExtraBold,
                        color = OnSurface,
                        fontSize = AppTextSize.body
                    )
                }
            }

            // Dynamic Milestone list rows filtered by level stage filter
            val filteredMilestones = dynamicMilestones.filter { dm ->
                val s = dm.step.step
                when (selectedStageFilter) {
                    1 -> s in 1..10
                    2 -> s in 11..20
                    3 -> s in 21..30
                    else -> true
                }
            }

            items(filteredMilestones.size) { idx ->
                val item = filteredMilestones[idx]
                val stepData = item.step
                val isCleared = item.status == "completed"
                val isCurrent = item.status == "active"
                val isOpen = openStep == stepData.step

                val rowBadgeColor = if (isCleared) {
                    PrimaryEmerald
                } else if (isCurrent) {
                    AccentAmber // Amber
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
                                            fontSize = AppTextSize.caption,
                                            color = if (isCurrent) OnPrimary else if (isCleared) PrimaryEmerald else OnSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stepData.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OnSurface,
                                            fontSize = AppTextSize.caption
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
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold,
                                                color = rowBadgeColor
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        stepData.title,
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isCleared) "100% CLEAR" else "${(item.currentRate * 100).toInt()}%",
                                    color = if (isCleared || isCurrent) PrimaryEmerald else OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
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
                                color = SecondaryIndigo,
                                fontSize = AppTextSize.dense,
                                lineHeight = 15.sp
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            val shortageAmt = stepData.targetMonthly - monthlyIncome
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainer)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("目標額 (月額 / 年額)", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        Text("¥${currencyFormat.format(stepData.targetMonthly)} /月", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text("年額 ¥${currencyFormat.format(stepData.targetMonthly * 12)}", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceContainer)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text("攻略状況", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        if (isCleared) {
                                            Text("達成済 ✨", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                            Text("現在月額 ¥${currencyFormat.format(monthlyIncome)}", fontSize = AppTextSize.dense, color = PrimaryEmerald)
                                        } else {
                                            Text("あと ¥${currencyFormat.format(shortageAmt)} /月", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = ErrorRed)
                                            Text("年額 ¥${currencyFormat.format(shortageAmt * 12)} 不足", fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            if (!isCleared) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val shortageAnnualAmt = shortageAmt * 12
                                
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceContainer)
                                        .border(0.5.dp, BorderBlue, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        "利回り別・必要追加元本シミュレーション",
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3.5 to "3.5%", 4.0 to "4.0%", 4.5 to "4.5%").forEach { (yield, label) ->
                                            val capital = Math.round(shortageAnnualAmt / (yield / 100.0) / 10000.0)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (yield == 4.0) Color(0x1F10B981) else SurfaceContainer)
                                                    .border(0.5.dp, if (yield == 4.0) PrimaryEmerald.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                    .padding(6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(label, fontSize = AppTextSize.dense, color = if (yield == 4.0) PrimaryEmerald else OnSurfaceVariant)
                                                    Text("¥${capital}万", fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = if (yield == 4.0) PrimaryEmerald else OnSurface)
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
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal Bottom Sheet: 複数口座一括配分登録 (Image 2)
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "複数口座一括配分登録",
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "口座間の株数・取得単価を1画面でまとめて設定",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                Divider(color = BorderColor)

                // Active stock selector state
                var selectedTickerInput by remember { mutableStateOf("AAPL") }
                val activeTicker = selectedTickerInput.uppercase(Locale.ROOT).trim()

                // Static DB for demonstration matching Image 2
                val activeStockName = when (activeTicker) {
                    "AAPL" -> "Apple Inc."
                    "7203" -> "トヨタ自動車 (TOYOTA)"
                    "8316" -> "三井住友フィナンシャルG"
                    "9432" -> "日本電信電話 (NTT)"
                    else -> "$activeTicker Corp."
                }
                val activeStockMarket = when (activeTicker) {
                    "AAPL" -> "NASDAQ ・ US Tech ・ 米国株"
                    else -> "東証PRM ・ 日本株 ・ 円建て"
                }
                val activeStockCurrency = if (activeTicker == "AAPL") "$" else "¥"
                val activeStockPrice = when (activeTicker) {
                    "AAPL" -> 220.0
                    "7203" -> 2600.0
                    "8316" -> 9800.0
                    "9432" -> 170.0
                    else -> 1000.0
                }
                val activeStockYield = when (activeTicker) {
                    "AAPL" -> 0.45
                    "7203" -> 2.80
                    "8316" -> 3.50
                    "9432" -> 3.20
                    else -> 3.00
                }
                val activeStockDPS = activeStockPrice * (activeStockYield / 100.0)

                // Input States for the 4 Accounts
                var sharesNisaGrowth by remember { mutableStateOf("5") }
                var priceNisaGrowth by remember { mutableStateOf("220.00") }

                var sharesNisaTsumitate by remember { mutableStateOf("0") }
                var priceNisaTsumitate by remember { mutableStateOf("2600") }

                var sharesTokutei by remember { mutableStateOf("5") }
                var priceTokutei by remember { mutableStateOf("220.00") }

                var sharesIppan by remember { mutableStateOf("0") }
                var priceIppan by remember { mutableStateOf("220.00") }

                // Search Bar Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "銘柄コード・ティッカー番号入力",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = selectedTickerInput,
                            onValueChange = { selectedTickerInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            placeholder = { Text("例: 7203, AAPL など", fontSize = AppTextSize.dense, color = OnSurfaceVariant) },
                            singleLine = true
                        )
                        Button(
                            onClick = { /* Apply */ },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("適用", color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Candidate Chips matching Image 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("AAPL", "7203", "8316", "9432").forEach { code ->
                            val label = when (code) {
                                "AAPL" -> "AAPL Apple"
                                "7203" -> "7203 トヨタ"
                                "8316" -> "8316 三井住友"
                                "9432" -> "9432 NTT"
                                else -> code
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activeTicker == code) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceContainerHigh,
                                border = BorderStroke(1.dp, if (activeTicker == code) PrimaryEmerald else Color.Transparent),
                                modifier = Modifier
                                    .clickable {
                                        selectedTickerInput = code
                                        // Auto fill price template values based on stock selection
                                        val templatePrice = when (code) {
                                            "AAPL" -> "220.00"
                                            "7203" -> "2600"
                                            "8316" -> "9800"
                                            "9432" -> "170"
                                            else -> "100"
                                        }
                                        priceNisaGrowth = templatePrice
                                        priceNisaTsumitate = templatePrice
                                        priceTokutei = templatePrice
                                        priceIppan = templatePrice
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = if (activeTicker == code) PrimaryEmerald else OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Active Stock Preview Card matching Image 2
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryEmerald.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        activeTicker.take(4),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        activeStockName,
                                        color = OnSurface,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        activeStockMarket,
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x1F10B981)
                                ) {
                                    Text(
                                        "買場目安近接",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "1株配当 $activeStockCurrency" + String.format("%.2f", activeStockDPS),
                                    color = OnSurface,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "現在値: $activeStockCurrency" + currencyFormat.format(activeStockPrice.toInt()),
                                color = OnSurface,
                                fontSize = AppTextSize.caption,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "現在配当利回り: " + String.format("%.2f%%", activeStockYield),
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Header sub-label Matrix Allocation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "口座別 ポートフォリオ配分マトリックス",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "クリア",
                        color = ErrorRed,
                        fontSize = AppTextSize.dense,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            sharesNisaGrowth = "0"
                            sharesNisaTsumitate = "0"
                            sharesTokutei = "0"
                            sharesIppan = "0"
                        }
                    )
                }

                // Account Allocation Cards Grid inside BottomSheet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Account Row 1: 新NISA 成長投資枠
                    AccountMatrixCard(
                        title = "新NISA 成長投資枠",
                        isTaxFree = true,
                        shares = sharesNisaGrowth,
                        price = priceNisaGrowth,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesNisaGrowth = it },
                        onPriceChange = { priceNisaGrowth = it },
                        dps = activeStockDPS
                    )

                    // Account Row 2: 新NISA つみたて枠
                    AccountMatrixCard(
                        title = "新NISA つみたて枠",
                        isTaxFree = true,
                        shares = sharesNisaTsumitate,
                        price = priceNisaTsumitate,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesNisaTsumitate = it },
                        onPriceChange = { priceNisaTsumitate = it },
                        dps = activeStockDPS
                    )

                    // Account Row 3: 特定口座 (源泉徴収あり)
                    AccountMatrixCard(
                        title = "特定口座 (源泉徴収あり)",
                        isTaxFree = false,
                        shares = sharesTokutei,
                        price = priceTokutei,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesTokutei = it },
                        onPriceChange = { priceTokutei = it },
                        dps = activeStockDPS
                    )

                    // Account Row 4: 一般口座 (確定申告)
                    AccountMatrixCard(
                        title = "一般口座 (確定申告)",
                        isTaxFree = false,
                        shares = sharesIppan,
                        price = priceIppan,
                        currency = activeStockCurrency,
                        onSharesChange = { sharesIppan = it },
                        onPriceChange = { priceIppan = it },
                        dps = activeStockDPS
                    )
                }

                // Save Action Button inside BottomSheet
                Button(
                    onClick = {
                        val entries = listOf(
                            Triple(sharesNisaGrowth, priceNisaGrowth, "新NISA成長枠"),
                            Triple(sharesNisaTsumitate, priceNisaTsumitate, "新NISAつみたて投資枠"),
                            Triple(sharesTokutei, priceTokutei, "特定口座"),
                            Triple(sharesIppan, priceIppan, "一般口座")
                        )
                        val allocations = entries.mapNotNull { (quantity, price, account) ->
                            val shares = quantity.toIntOrNull() ?: return@mapNotNull null
                            val cost = price.toDoubleOrNull() ?: return@mapNotNull null
                            com.kurogane.dividend.model.HoldingAllocation(shares, cost, account)
                        }
                        if (allocations.size != entries.size || !viewModel.addHoldingAllocations(activeTicker, allocations)) return@Button
                        showBottomSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "ポートフォリオに一括追加登録",
                        color = OnPrimary,
                        fontSize = AppTextSize.body,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet: 達成状況をシェア (Image 1 & 2 share certificate)
    if (showShareBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareBottomSheet = false },
            sheetState = shareSheetState,
            containerColor = BackgroundDark,
            tonalElevation = 8.dp,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Share Modal Header matching image perfectly
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "達成状況をシェア",
                                color = OnSurface,
                                fontSize = AppTextSize.body,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "SNSで進捗を共有できます",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh)
                            .clickable { showShareBottomSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }

                // Interactive Tab Selector: 進捗カード vs 踏破認定証
                var selectedShareTab by remember { mutableIntStateOf(1) } // Default active: 踏破認定証
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedShareTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedShareTab == 0) SurfaceContainerHigh else Color.Transparent
                        ),
                        border = if (selectedShareTab == 0) BorderStroke(1.dp, BorderColor) else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = OnSurface, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QUEST 6 進捗カード", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { selectedShareTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedShareTab == 1) SurfaceContainerHigh else Color.Transparent
                        ),
                        border = if (selectedShareTab == 1) BorderStroke(1.dp, BorderColor) else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LV.1 踏破認定証", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    }
                }

                // Masking Switch Options Row
                var isAmountMasked by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHigh,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("金額を伏字にする", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = isAmountMasked,
                            onCheckedChange = { isAmountMasked = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnSurface,
                                checkedTrackColor = PrimaryEmerald
                            )
                        )
                    }
                }

                // Dynamic Certificate Card Box View matching Image perfectly
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceContainerHigh)
                        .border(1.dp, if (selectedShareTab == 1) PrimaryEmerald.copy(alpha = 0.4f) else BorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedShareTab == 1) {
                            // Medal design matching image
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryEmerald.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = PrimaryEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "LEVEL 1 COMPLETE",
                                color = PrimaryEmerald,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "生活インフラ自給期 踏破認定証",
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "STEP 1〜10 (月2万円・年24万円) を完全達成",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Inner sub-card with highlight title
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "獲得した生活権益",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "水道光熱費・通信費の全額自給権 ⚡ 📱",
                                        color = PrimaryEmeraldContainer,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "到達実績: " + if (isAmountMasked) "¥••,••• / 月" else "¥${currencyFormat.format(monthlyIncome)} / 月",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "認定ランク: LEVEL 1 MASTER",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Roadmap Quest",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            // Quest Card View (Tab 0 Selected)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SkyBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SkyBlue.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = SkyBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "CURRENT ACTIVE QUEST",
                                color = SkyBlue,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "QUEST ${activeStep.step}: " + activeStep.name.substringAfter(": "),
                                color = OnSurface,
                                fontSize = AppTextSize.section,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "目標額: 月 ¥${currencyFormat.format(activeStep.targetMonthly)} 自給に向けて挑戦中",
                                color = OnSurfaceVariant,
                                fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Inner progress sub-card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "現在のクエスト進捗状況",
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "目標達成率: ${(activeQuest.currentRate * 100).toInt()}% 🚀",
                                        color = PrimaryEmeraldContainer,
                                        fontSize = AppTextSize.body,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "現在実績: " + if (isAmountMasked) "¥••,••• / 月" else "¥${currencyFormat.format(monthlyIncome)} / 月",
                                        color = OnSurface,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "現在のステータス: LEVEL 2 CHALLENGER",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Roadmap Quest",
                                    color = OnSurfaceVariant,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Premium Gradient Share Action Button matching image perfectly
                Button(
                    onClick = { showShareBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OnSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        "𝕏 𝕏 (Twitter) でクエスト進捗をシェア",
                        color = BackgroundDark,
                        fontSize = AppTextSize.caption,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// Custom Account matrix allocation form Card component
@Composable
private fun AccountMatrixCard(
    title: String,
    isTaxFree: Boolean,
    shares: String,
    price: String,
    currency: String,
    onSharesChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    dps: Double
) {
    val numShares = shares.toIntOrNull() ?: 0
    val unitPrice = price.toDoubleOrNull() ?: 0.0
    val totalCost = numShares * unitPrice
    val rawDividend = numShares * dps
    val finalDividend = if (isTaxFree) rawDividend else rawDividend * (1.0 - 0.20315)
    val taxAmt = rawDividend * 0.20315

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isTaxFree) PrimaryEmerald else SecondaryIndigo)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title, color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isTaxFree) PrimaryEmerald.copy(alpha = 0.15f) else Color(0x1F94A3B8)
                    ) {
                        Text(
                            text = if (isTaxFree) "非課税 0%" else "課税 20.315%",
                            color = if (isTaxFree) PrimaryEmerald else OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = "手取り配当: $currency" + String.format("%.2f/年", finalDividend),
                    color = if (numShares > 0) PrimaryEmerald else OnSurfaceVariant,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Inputs Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shares Input Column
                Column(modifier = Modifier.weight(1f)) {
                    Text("保有株数", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = shares,
                            onValueChange = onSharesChange,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        )
                        // Quick add buttons
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { onSharesChange(((shares.toIntOrNull() ?: 0) + 10).toString()) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("+10", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { onSharesChange(((shares.toIntOrNull() ?: 0) + 100).toString()) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("+100", color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Acquisition Price Input Column
                Column(modifier = Modifier.weight(1f)) {
                    Text("取得単価 (平均)", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextField(
                            value = price,
                            onValueChange = onPriceChange,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceContainerHigh,
                                unfocusedContainerColor = SurfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        )
                        Text("/株", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }
                }
            }

            if (numShares > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "投資元本: $currency" + String.format("%.2f", totalCost),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )

                    if (isTaxFree) {
                        Text(
                            "NISA節税効果: +$currency" + String.format("%.2f", taxAmt),
                            color = PrimaryEmerald,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "源泉控除税額: -$currency" + String.format("%.2f", taxAmt),
                            color = ErrorRed,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Data structures for clean dynamic rendering
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
