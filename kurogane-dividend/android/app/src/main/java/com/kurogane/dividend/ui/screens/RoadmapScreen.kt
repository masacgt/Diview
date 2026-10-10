package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.SmoothMoney
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
fun RoadmapScreen(viewModel: DividendViewModel, scrollToTopRequest: Int = 0) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    var openStep by remember { mutableStateOf<Int?>(null) }
    var selectedStageFilter by remember { mutableIntStateOf(viewModel.roadmapStageFilter) } // 0: All, 1: LV.1, 2: LV.2, 3: LV.3
    val hasSavedScrollPosition = viewModel.roadmapScrollIndex > 0 || viewModel.roadmapScrollOffset > 0

    // Bottom Sheet State for multi-account allocation modal
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGrowthSimulator by rememberSaveable { mutableStateOf(false) }

    // Bottom Sheet State for Share Achievement modal (Added for sharing progress)
    var showShareBottomSheet by rememberSaveable { mutableStateOf(false) }
    var saveRoadmapImage by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.roadmapScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.roadmapScrollOffset
    )
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.scrollToTopIn200ms()
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.roadmapScrollIndex = index
                viewModel.roadmapScrollOffset = offset
            }
    }
    LaunchedEffect(selectedStageFilter) {
        viewModel.roadmapStageFilter = selectedStageFilter
    }

    // 1. Collect live native holdings state
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val usTaxDeduction by viewModel.usTaxDeduction.collectAsStateWithLifecycle()
    val taxRateMode by viewModel.taxRateMode.collectAsStateWithLifecycle()
    val targetYield by viewModel.roadmapTargetYield.collectAsStateWithLifecycle()
    val simulatorMonthlyContribution by viewModel.growthSimulatorMonthlyContribution.collectAsStateWithLifecycle()
    val simulatorDividendYield by viewModel.growthSimulatorDividendYield.collectAsStateWithLifecycle()
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
        val currentRate = if (isCompleted) 1f else (monthlyIncome.toFloat() / step.targetMonthly.toFloat()).coerceIn(0f, 0.99f)
        
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
    val activeStage = ((activeStep.step - 1) / 10) + 1

    LaunchedEffect(activeStep.step) {
        if (!hasSavedScrollPosition) selectedStageFilter = activeStage
        openStep = activeStep.step
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AdaptiveRow(
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
                                    text = stringResource(R.string.roadmap_text , currentLevel, activeStep.step),
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
                                    text = stringResource(R.string.roadmap_text_2 , highestCompletedStep),
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
                                            stringResource(R.string.roadmap_text_3),
                                            color = OnSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppTextSize.dense
                                        )
                                        Text(
                                            stringResource(R.string.roadmap_text_4),
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
                                        stringResource(R.string.roadmap_all_clear),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = PrimaryEmerald,
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(stringResource(R.string.roadmap_text_5), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            stringResource(R.string.roadmap_text_6 , currencyFormat.format(totalAnnualExpectedDividend)),
                            fontSize = AppTextSize.hero,
                            fontWeight = FontWeight.Black,
                            color = OnSurface,
                            fontFamily = FontFamily.SansSerif
                        )
                        
                        Text(
                            text = stringResource(R.string.roadmap_text_7 , currencyFormat.format(monthlyIncome)),
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
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.roadmap_text_8 , activeStep.step, currencyFormat.format(activeStep.targetMonthly)),
                                            color = OnSurface,
                                            fontSize = AppTextSize.dense,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.roadmap_text_9 , (activeQuest.currentRate * 100).toInt()),
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
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.roadmap_text_10 , currencyFormat.format(monthlyIncome)),
                                        color = OnSurfaceVariant,
                                        fontSize = AppTextSize.dense
                                    )
                                    
                                    val shortage = activeStep.targetMonthly - monthlyIncome
                                    if (shortage > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Flag, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                stringResource(R.string.roadmap_text_11 , currencyFormat.format(shortage)),
                                                color = ErrorRed,
                                                fontSize = AppTextSize.dense,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Text(
                                            stringResource(R.string.roadmap_text_12),
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
                        val neededPrincipalAt4 = Math.round(shortageAnnual / (targetYield / 100.0) / 10000.0)
                        val summaryMetrics = listOf(
                            Triple(
                                R.string.roadmap_summary_annual_shortfall,
                                stringResource(R.string.roadmap_summary_yen, currencyFormat.format(shortageAnnual)),
                                ErrorRed
                            ),
                            Triple(
                                R.string.roadmap_summary_yield,
                                "${String.format(Locale.JAPAN, "%.2f", targetYield)}%",
                                PrimaryEmerald
                            ),
                            Triple(
                                R.string.roadmap_summary_required_principal,
                                stringResource(R.string.roadmap_summary_principal, NumberFormat.getNumberInstance(Locale.JAPAN).format(neededPrincipalAt4)),
                                OnSurface
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundDark,
                            border = BorderStroke(1.dp, BorderBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.roadmap_text_13, activeStep.step),
                                    color = PrimaryEmerald,
                                    fontSize = AppTextSize.dense,
                                    fontWeight = FontWeight.Bold
                                )
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    summaryMetrics.forEach { (labelRes, value, valueColor) ->
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            color = SurfaceContainer,
                                            border = BorderStroke(0.5.dp, BorderBlue.copy(alpha = 0.6f))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = stringResource(labelRes),
                                                    color = OnSurfaceVariant,
                                                    fontSize = AppTextSize.dense,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = value,
                                                    color = valueColor,
                                                    fontSize = AppTextSize.caption,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Share & Save Buttons matching Image 1
                        AdaptiveRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    saveRoadmapImage = false
                                    if (viewModel.isPrivacyActive.value) viewModel.setRoadmapShareMasked(true)
                                    showShareBottomSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.roadmap_text_14), color = OnPrimary, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    saveRoadmapImage = true
                                    if (viewModel.isPrivacyActive.value) viewModel.setRoadmapShareMasked(true)
                                    showShareBottomSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = OnSurface, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.roadmap_text_15), color = OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 2: "3大ステージ・レベル帯" Grid selection
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AdaptiveRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.roadmap_text_16), fontWeight = FontWeight.ExtraBold, color = OnSurface, fontSize = AppTextSize.body)
                        }
                        Text(stringResource(R.string.roadmap_text_17), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AdaptiveRow(
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
                                Text(stringResource(R.string.roadmap_text_18), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                    Text(stringResource(R.string.roadmap_step_1_10), color = if (selectedStageFilter == 1) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.roadmap_text_19), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text(stringResource(R.string.roadmap_step_11_20), color = if (selectedStageFilter == 2) PrimaryEmerald else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.roadmap_text_20), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                Text(stringResource(R.string.roadmap_step_21_30), color = if (selectedStageFilter == 3) OnSurfaceVariant else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
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
                        stringResource(R.string.roadmap_text_21),
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

            items(filteredMilestones.size, key = { filteredMilestones[it].step.step }) { idx ->
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
                        AdaptiveRow(
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
                                            stringResource(R.string.annual_dividend_1_s , stepData.step),
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
                            HorizontalDivider(color = SurfaceContainerHighest, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                stepData.description,
                                color = SecondaryIndigo,
                                fontSize = AppTextSize.dense,
                                lineHeight = 15.sp
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            val shortageAmt = stepData.targetMonthly - monthlyIncome
                            
                            AdaptiveRow(
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
                                        Text(stringResource(R.string.roadmap_text_22), fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        Text(stringResource(R.string.roadmap_text_23 , currencyFormat.format(stepData.targetMonthly)), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = OnSurface)
                                        Text(stringResource(R.string.roadmap_text_24 , currencyFormat.format(stepData.targetMonthly * 12)), fontSize = AppTextSize.dense, color = OnSurfaceVariant)
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
                                        Text(stringResource(R.string.roadmap_text_25), fontSize = AppTextSize.dense, color = OnSurfaceVariant)
                                        if (isCleared) {
                                            Text(stringResource(R.string.roadmap_text_26), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = PrimaryEmerald)
                                            Text(stringResource(R.string.roadmap_text_27 , currencyFormat.format(monthlyIncome)), fontSize = AppTextSize.dense, color = PrimaryEmerald)
                                        } else {
                                            Text(stringResource(R.string.roadmap_text_28 , currencyFormat.format(shortageAmt)), fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, color = ErrorRed)
                                            Text(stringResource(R.string.roadmap_text_29 , currencyFormat.format(shortageAmt * 12)), fontSize = AppTextSize.dense, color = OnSurfaceVariant)
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
                                        stringResource(R.string.roadmap_text_30),
                                        fontSize = AppTextSize.dense,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Text(
                                        stringResource(
                                            R.string.roadmap_text_65,
                                            currencyFormat.format(shortageAmt.coerceAtLeast(0)),
                                            currencyFormat.format(shortageAnnualAmt.coerceAtLeast(0))
                                        ),
                                        fontSize = AppTextSize.dense,
                                        color = OnSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    AdaptiveRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(3.5 to "3.50%", 4.0 to "4.00%", 4.5 to "4.50%").forEach { (yield, label) ->
                                            val capital = Math.round(shortageAnnualAmt / (yield / 100.0) / 10000.0)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(SurfaceContainerHigh)
                                                    .padding(horizontal = 3.dp, vertical = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(label, fontSize = AppTextSize.dense, color = if (yield == targetYield) PrimaryEmerald else OnSurfaceVariant)
                                                Text(stringResource(R.string.roadmap_text_31 , capital), fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold, color = if (yield == targetYield) PrimaryEmerald else OnSurface)
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
            item {
                Button(
                    onClick = { showGrowthSimulator = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)
                ) {
                    Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = OnPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("未来30年・複利成長シミュレーター", color = OnPrimary, fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    if (showBottomSheet) RoadmapAllocationSheet(viewModel) { showBottomSheet = false }

    if (showGrowthSimulator) {
        RoadmapGrowthSimulatorSheet(
            currentAssets = holdings.sumOf { it.totalValuation },
            currentCost = holdings.sumOf { it.totalCost },
            monthlyContribution = simulatorMonthlyContribution,
            dividendYield = simulatorDividendYield,
            onMonthlyContributionChange = viewModel::setGrowthSimulatorMonthlyContribution,
            onDividendYieldChange = viewModel::setGrowthSimulatorDividendYield,
            onDismiss = { showGrowthSimulator = false }
        )
    }

    if (showShareBottomSheet) {
        val completedStage = (highestCompletedStep / 10).coerceAtLeast(1)
        val stageEnd = milestoneSteps[completedStage * 10 - 1]
        RoadmapShareSheet(
            viewModel = viewModel,
            achievement = com.kurogane.dividend.model.RoadmapAchievement(
                activeStep.step, activeStep.name, activeStep.targetMonthly, monthlyIncome, highestCompletedStep,
                listOf(resources.getString(R.string.roadmap_text_18), resources.getString(R.string.roadmap_text_19), resources.getString(R.string.roadmap_text_20))[completedStage - 1],
                stageEnd.title, stageEnd.targetMonthly
            ),
            saveImage = saveRoadmapImage,
            onDismiss = { showShareBottomSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoadmapGrowthSimulatorSheet(
    currentAssets: Double,
    currentCost: Double,
    monthlyContribution: Long,
    dividendYield: Double,
    onMonthlyContributionChange: (Long) -> Unit,
    onDividendYieldChange: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var reinvest by rememberSaveable { mutableStateOf(true) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow,
        contentColor = OnSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).clip(CircleShape).background(BorderColor))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = PrimaryEmerald)
                Spacer(Modifier.width(8.dp))
                Text("未来30年・複利成長シミュレーター", modifier = Modifier.weight(1f), fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("複利シミュレーション結果", modifier = Modifier.weight(1f), color = OnSurfaceVariant, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("配当金を再投資", color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                    Switch(
                        checked = reinvest,
                        onCheckedChange = { reinvest = it },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30).forEach { years ->
                    val monthlyRate = dividendYield / 100.0 / 12.0
                    var value = currentAssets.coerceAtLeast(0.0)
                    repeat(years * 12) { value = value * (1.0 + if (reinvest) monthlyRate else 0.0) + monthlyContribution }
                    if (!reinvest) value += currentAssets * dividendYield / 100.0 * years
                    val principal = currentCost.coerceAtLeast(0.0) + monthlyContribution * 12.0 * years
                    val estimatedMonthlyDividend = value * dividendYield / 100.0 / 12.0
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SurfaceContainerHigh)
                            .border(1.dp, if (years == 30) PrimaryEmerald else BorderColor, RoundedCornerShape(12.dp)).padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "${years}年後 (${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + years})",
                            color = if (years == 30) PrimaryEmerald else OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        SmoothMoney(value, inTenThousands = true,
                            style = androidx.compose.ui.text.TextStyle(color = OnSurface, fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Black, textAlign = TextAlign.Center))
                        Text(
                            "元本: ¥${kotlin.math.round(principal / 10_000.0).toLong()}万",
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        HorizontalDivider(color = BorderColor)
                        Text("月配当(目安)", color = OnSurfaceVariant, fontSize = AppTextSize.dense, textAlign = TextAlign.Center)
                        SmoothMoney(estimatedMonthlyDividend, suffix = "/月",
                            style = androidx.compose.ui.text.TextStyle(color = PrimaryEmerald, fontSize = AppTextSize.dense,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                    }
                }
            }
            GrowthSimulatorStepper(
                label = "毎月の積立額",
                value = "¥${numberFormat.format(monthlyContribution)}",
                onDecrease = {
                    val step = if (monthlyContribution <= 20_000L) 1_000L else 5_000L
                    onMonthlyContributionChange((monthlyContribution - step).coerceAtLeast(0L))
                },
                onIncrease = {
                    val step = if (monthlyContribution < 20_000L) 1_000L else 5_000L
                    onMonthlyContributionChange((monthlyContribution + step).coerceAtMost(1_000_000L))
                }
            )
            GrowthSimulatorStepper(
                label = "想定配当利回り",
                value = String.format(Locale.JAPAN, "%.2f%%", dividendYield),
                onDecrease = { onDividendYieldChange((dividendYield - 0.10).coerceAtLeast(0.0)) },
                onIncrease = { onDividendYieldChange((dividendYield + 0.10).coerceAtMost(30.0)) }
            )
        }
    }
}

@Composable
private fun GrowthSimulatorStepper(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = BackgroundDark, border = BorderStroke(1.dp, BorderColor)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, modifier = Modifier.weight(1.15f), color = OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold, maxLines = 1)
            IconButton(onClick = onDecrease, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHighest)) {
                Icon(Icons.Default.Remove, contentDescription = "$label を下げる", tint = OnSurface)
            }
            Text(value, modifier = Modifier.weight(1f), color = OnSurface, fontSize = AppTextSize.caption, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, maxLines = 1)
            IconButton(onClick = onIncrease, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHighest)) {
                Icon(Icons.Default.Add, contentDescription = "$label を上げる", tint = OnSurface)
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
