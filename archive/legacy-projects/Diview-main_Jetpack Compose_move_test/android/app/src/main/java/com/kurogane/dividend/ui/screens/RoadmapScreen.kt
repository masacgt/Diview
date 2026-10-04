package com.kurogane.dividend.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kurogane.dividend.ui.theme.*

private val QuestInk = Color(0xFF0B1326)
private val QuestCard = Color(0xFF171F33)
private val QuestGroup = Color(0xFF131B2E)
private val QuestMuted = Color(0xFFBBCABF)
private val QuestMint = Color(0xFF4EDEA3)
private val QuestGreen = Color(0xFF10B981)
private val QuestRose = Color(0xFFFFB4AB)

private data class QuestStep(
    val number: Int,
    val level: Int,
    val name: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val monthly: Int,
    val annual: Int,
    val status: String,
    val rate: String,
    val shortageMonthly: Int = 0,
    val shortageAnnual: Int = 0,
    val capital35: String = "",
    val capital40: String = "",
    val capital45: String = ""
)

private val questSteps = listOf(
    QuestStep(1, 1, "STEP 1: 月3,000円", "電気代の基本料金・カフェ代を自給", "月3,000円 (年3.6万円)。毎月の電気代基本料金や休日のカフェ代が不労所得でゼロに。最初の不労所得の確かな手応えを得るステージです。", Icons.Default.Coffee, 3_000, 36_000, "達成済", "100%"),
    QuestStep(2, 1, "STEP 2: 月5,000円", "スマホ通信費＆サブスク代を自給", "月5,000円 (年6万円)。格安SIMの通信費やNetflix、Spotify、iCloudなどの主要デジタルサブスク利用料が自動でまかなえます。", Icons.Default.Smartphone, 5_000, 60_000, "達成済", "100%"),
    QuestStep(3, 1, "STEP 3: 月10,000円", "水道光熱費の基本料金をカバー", "月10,000円 (年12万円)。電気・ガス・水道の全基本料金が資本収入で賄われ、暮らしているだけで発生する固定費の土台が自動化されます。", Icons.Default.Lightbulb, 10_000, 120_000, "達成済", "100%"),
    QuestStep(4, 1, "STEP 4: 月20,000円", "週末外食・自己投資書籍費", "月20,000円 (年24万円)。毎週末の気兼ねない外食ランチや、スキルアップのための技術書・ビジネス書購入費用が不労所得から拠出可能になります。", Icons.Default.Restaurant, 20_000, 240_000, "達成済", "100%"),
    QuestStep(5, 1, "STEP 5: 月30,000円", "水道光熱費の全額自給", "月30,000円 (年36万円)。一人暮らし・少人数世帯の水道・電気・ガス代全額を完全カバー。生活インフラを資本収入のみで賄う大記録を達成！", Icons.Default.Bolt, 30_000, 360_000, "達成済", "100%"),
    QuestStep(6, 2, "STEP 6: 月50,000円", "食費・日用品の全額自給", "毎月の配当金が5万円に到達すると、一人暮らしの食費や日用品などの生活基盤出費が労働対価なしで賄えるようになります。精神的自由度が劇的に跳ね上がる超重要マイルストーンです。", Icons.Default.ShoppingCart, 50_000, 600_000, "現在挑戦中", "62.4%", 18_792, 225_500, "¥644万", "¥563万", "¥501万"),
    QuestStep(7, 2, "STEP 7: 月100,000円", "住居費・家賃の完全カバー", "月100,000円 (年120万円)。人生最大の固定出費である家賃や住宅ローン返済を配当のみで支払える圧倒的安心感を手に入れます。", Icons.Default.Home, 100_000, 1_200_000, "未達成", "31.2%", 68_792, 825_500, "¥2,358万", "¥2,063万", "¥1,834万"),
    QuestStep(8, 3, "STEP 8: 月200,000円", "標準的生活費の全額自立（Lean FIRE水準）", "月200,000円 (年240万円)。必要最低限の生活費を超え、質素ながら自立した生活が配当だけで成立するLean FIREの達成域です。", Icons.Default.BeachAccess, 200_000, 2_400_000, "未達成", "15.6%", 168_792, 2_025_500, "¥5,787万", "¥5,063万", "¥4,501万"),
    QuestStep(9, 3, "STEP 9: 月300,000円", "ゆとりある生活（Full FIRE）", "月300,000円 (年360万円)。旅行や趣味、家族との豊かな時間を過ごしながらも元本が減らない理想の不労所得ライフスタイルを確立。", Icons.Default.Flight, 300_000, 3_600_000, "未達成", "10.4%", 268_792, 3_225_500, "¥9,215万", "¥8,063万", "¥7,167万"),
    QuestStep(10, 3, "STEP 10: 月500,000円+", "経済的自由・資産自動増殖", "月500,000円 (年600万円)。労働に依存せず、資産収入で自由な選択を続けられる経済的自由の到達点です。", Icons.Default.Star, 500_000, 6_000_000, "未達成", "6.2%", 468_792, 5_625_500, "¥1億6,073万", "¥1億4,063万", "¥1億2,501万")
)

@Composable
fun RoadmapScreen(onOpenSettings: () -> Unit = {}) {
    var openStep by remember { mutableIntStateOf(1) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var shareDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            RoadmapHeader(
                onSettings = onOpenSettings,
                onSearch = { scope.launch { snackbar.showSnackbar("検索") } },
                onNotifications = { scope.launch { snackbar.showSnackbar("お知らせ") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = QuestInk
    ) { insets ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { QuestSummaryCard(onShare = { shareDialog = true }, onSave = { scope.launch { snackbar.showSnackbar("達成カード画像を保存しました") } }) }
            item { LevelNavigator(onSelect = { level ->
                openStep = when (level) { 1 -> 1; 2 -> 6; else -> 8 }
                scope.launch { listState.animateScrollToItem(if (level == 1) 3 else if (level == 2) 4 else 5) }
            }) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 5.dp)) {
                    Icon(Icons.Default.AssignmentTurnedIn, null, tint = QuestMint, modifier = Modifier.size(21.dp))
                    Text("全10マイルストーン・クエストマップ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            item { QuestLevelGroup(level = 1, expandedStep = openStep, onToggle = { openStep = if (openStep == it) 0 else it }) }
            item { QuestLevelGroup(level = 2, expandedStep = openStep, onToggle = { openStep = if (openStep == it) 0 else it }) }
            item { QuestLevelGroup(level = 3, expandedStep = openStep, onToggle = { openStep = if (openStep == it) 0 else it }) }
        }
    }

    if (shareDialog) {
        AlertDialog(
            onDismissRequest = { shareDialog = false },
            containerColor = QuestCard,
            title = { Text("達成状況をシェア", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("LEVEL 1を完全制覇し、現在 STEP 6「食費・日用品の全額自給 (月5万円)」を攻略中です。", color = QuestMuted) },
            confirmButton = { TextButton(onClick = { shareDialog = false }) { Text("閉じる", color = QuestMint) } }
        )
    }
}

@Composable
private fun RoadmapHeader(onSettings: () -> Unit, onSearch: () -> Unit, onNotifications: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).background(QuestInk).border(width = 0.5.dp, color = Color(0x66313B50)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(QuestCard).border(1.dp, Color(0x66313B50), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Flag, null, tint = QuestMint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Roadmap Quest", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text("不労所得 RPG • ステージ攻略", color = QuestMuted, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, maxLines = 1)
        }
        Surface(shape = CircleShape, color = QuestCard, modifier = Modifier.border(1.dp, Color(0x664EDEA3), CircleShape)) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Android, null, tint = QuestMint, modifier = Modifier.size(14.dp))
                Text("Compose", color = QuestMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Search, "検索", tint = QuestMuted, modifier = Modifier.size(21.dp)) }
        Box {
            IconButton(onClick = onNotifications, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Notifications, "通知", tint = QuestMuted, modifier = Modifier.size(21.dp)) }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).size(7.dp).clip(CircleShape).background(QuestMint))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Settings, "設定", tint = QuestMuted, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
private fun QuestSummaryCard(onShare: () -> Unit, onSave: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222A3D), Color(0xFF171F33), Color(0xFF131B2E))))
            .border(1.dp, Color(0x66313B50), RoundedCornerShape(20.dp)).padding(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = QuestGreen.copy(alpha = .15f), modifier = Modifier.border(1.dp, QuestMint.copy(alpha = .28f), CircleShape)) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(QuestMint))
                    Text("LEVEL 2 進行中: STEP 6", color = QuestMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Surface(shape = CircleShape, color = Color(0xFF171A42), modifier = Modifier.border(1.dp, Color(0x663C3680), CircleShape)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFB800), modifier = Modifier.size(18.dp))
                    Text("LV.1 制覇済み (5/10)", color = Color(0xFFC0C1FF), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(QuestGreen.copy(alpha = .20f), QuestGreen.copy(alpha = .07f), Color.Transparent))).border(1.dp, QuestMint.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFB800)).border(1.dp, Color(0x66FFB800), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFB800), modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("LEVEL 1 完全制覇達成！", color = QuestMint, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(3.dp)); Icon(Icons.Default.Verified, null, tint = QuestMint, modifier = Modifier.size(16.dp)) }
                Text("生活インフラ自給期 (STEP 1〜5) を踏破完了", color = QuestMuted, fontSize = 8.sp, maxLines = 1)
            }
            Text("ALL CLEAR", Modifier.clip(RoundedCornerShape(5.dp)).background(QuestGreen.copy(alpha = .2f)).padding(horizontal = 7.dp, vertical = 4.dp), color = QuestMint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(11.dp))
        Text("現在の配当獲得ステータス", color = QuestMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .4.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("¥374,500", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(5.dp)); Text("/ 年", color = QuestMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("月間換算不労所得:", color = QuestMuted, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp)); Text("約 ¥31,208", color = QuestMint, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(3.dp)); Text("/ 月", color = QuestMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(QuestInk.copy(alpha = .85f)).border(1.dp, Color(0x66313B50), RoundedCornerShape(13.dp)).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.ShoppingCart, null, tint = QuestMint, modifier = Modifier.size(19.dp))
                    Text("QUEST 6 目標: ¥50,000 / 月", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("62.4% 達成", color = QuestMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { .624f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = QuestMint, trackColor = Color(0xFF263249))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("現在実績: ¥31,208", color = QuestMuted, fontSize = 8.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Flag, null, tint = QuestRose, modifier = Modifier.size(15.dp)); Text("あと ¥18,792 / 月 で攻略", color = QuestRose, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xCC171F33)).border(1.dp, Color(0x55313B50), RoundedCornerShape(9.dp)).padding(9.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Default.QueryStats, null, tint = QuestMint, modifier = Modifier.size(19.dp))
            Text("年額 +¥225,500 でSTEP 6クリア。配当利回り 4.0% 想定時、必要な追加投資元本は約 ¥563万円 です。", color = QuestMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = onShare, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = QuestGreen, contentColor = Color(0xFF003824)), contentPadding = PaddingValues(4.dp)) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("達成度を共有する", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66313B50)), contentPadding = PaddingValues(4.dp)) {
                Icon(Icons.Default.Download, null, tint = QuestMint, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("画像で保存", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LevelNavigator(onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Grade, null, tint = QuestMint, modifier = Modifier.size(19.dp)); Text("3大ステージ・レベル帯", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Text("タップしてジャンプ", color = QuestMuted, fontSize = 9.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LevelPill(1, "LV.1 制覇", "インフラ自給期", "STEP 1-5", Modifier.weight(1f), onSelect)
            LevelPill(2, "LV.2 挑戦中", "生活費・ミニFIRE", "STEP 6-7", Modifier.weight(1f), onSelect, selected = true)
            LevelPill(3, "LV.3 ロック", "完全FIRE期", "STEP 8-10", Modifier.weight(1f), onSelect, locked = true)
        }
    }
}

@Composable
private fun LevelPill(level: Int, heading: String, subtitle: String, steps: String, modifier: Modifier, onSelect: (Int) -> Unit, selected: Boolean = false, locked: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(if (selected) QuestCard else QuestGroup).border(if (selected) 1.5.dp else 1.dp, if (selected) QuestMint else if (level == 1) QuestMint.copy(alpha = .4f) else Color(0x55313B50), RoundedCornerShape(13.dp)).clickable { onSelect(level) }.padding(horizontal = 4.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(heading, color = if (locked) QuestMuted else QuestMint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip)
            Icon(if (level == 1) Icons.Default.CheckCircle else if (selected) Icons.Default.PlayCircle else Icons.Default.Lock, null, tint = if (locked) QuestMuted else QuestMint, modifier = Modifier.size(13.dp))
        }
        Text(subtitle, color = if (selected) Color.White else QuestMuted, fontSize = 8.sp, maxLines = 1)
        Text(steps, color = if (locked) QuestMuted else QuestMint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuestLevelGroup(level: Int, expandedStep: Int, onToggle: (Int) -> Unit) {
    val items = questSteps.filter { it.level == level }
    val active = level == 2
    val heading = when (level) { 1 -> "LEVEL 1: 生活インフラ自給期"; 2 -> "LEVEL 2: 生活費自給・ミニFIRE期"; else -> "LEVEL 3: 完全経済的自由・FIRE期" }
    val icon = when (level) { 1 -> Icons.Default.Bolt; 2 -> Icons.Default.ShoppingCart; else -> Icons.Default.Flight }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(QuestGroup).border(if (active) 1.5.dp else 1.dp, if (active) QuestMint.copy(alpha = .55f) else if (level == 1) QuestMint.copy(alpha = .32f) else Color(0x55313B50), RoundedCornerShape(19.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(icon, null, tint = if (level == 3) QuestMuted else QuestMint, modifier = Modifier.size(17.dp)); Text(heading, color = if (level == 2) Color.White else if (level == 3) QuestMuted else QuestMint, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            when (level) {
                1 -> Row(Modifier.clip(CircleShape).background(QuestGreen.copy(alpha = .2f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DoneAll, null, tint = QuestMint, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(3.dp)); Text("5/5 CLEAR", color = QuestMint, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                2 -> Text("挑戦中ステージ", Modifier.clip(CircleShape).background(QuestGreen).padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF003824), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                else -> Text("最終ステージ帯", Modifier.clip(CircleShape).background(Color(0xFF263249)).padding(horizontal = 8.dp, vertical = 4.dp), color = QuestMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        items.forEach { step ->
            QuestStepCard(step, expandedStep == step.number, onClick = { onToggle(step.number) })
        }
    }
}

@Composable
private fun QuestStepCard(step: QuestStep, expanded: Boolean, onClick: () -> Unit) {
    val cleared = step.level == 1
    val current = step.number == 6
    val border = if (current) QuestMint.copy(alpha = .55f) else Color(0x55313B50)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (current) QuestCard else if (step.level == 3) QuestCard.copy(alpha = .75f) else QuestCard).border(if (current) 1.5.dp else 1.dp, border, RoundedCornerShape(13.dp))) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (current) QuestGreen else if (cleared) QuestGreen.copy(alpha = .14f) else Color(0xFF202A3D)), contentAlignment = Alignment.Center) {
                Icon(step.icon, null, tint = if (current) Color(0xFF003824) else if (cleared) QuestMint else QuestMuted, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(step.name, color = if (current) QuestMint else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cleared || current) Text(if (cleared) "達成済" else "現在挑戦中", Modifier.clip(RoundedCornerShape(4.dp)).background(if (current) QuestGreen else QuestGreen.copy(alpha = .2f)).padding(horizontal = 4.dp, vertical = 2.dp), color = if (current) Color(0xFF003824) else QuestMint, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (step.level == 3) Icon(Icons.Default.Lock, null, tint = QuestMuted, modifier = Modifier.size(12.dp))
                }
                Text(step.title, color = QuestMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(5.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(if (cleared) "100% CLEAR" else step.rate, color = if (cleared || current) QuestMint else QuestMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                if (current || (step.level == 2 && step.number == 7)) Text(if (current) "あと¥18,792" else "あと¥68,792", color = if (current) QuestRose else QuestMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = QuestMuted, modifier = Modifier.padding(start = 4.dp).size(17.dp))
        }
        if (expanded) {
            HorizontalDivider(color = Color(0x55313B50))
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(step.description, color = QuestMuted, fontSize = 9.sp, lineHeight = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoTile(Modifier.weight(1f), "目標月額 / 年額換算", "¥${step.monthly.toGroupedString()} /月", "年額 ¥${step.annual.toGroupedString()}", Color.White)
                    if (cleared) InfoTile(Modifier.weight(1f), "達成ステータス", "達成済 ✨", "現在月額 ¥31,208 で超過中", QuestMint)
                    else InfoTile(Modifier.weight(1f), if (current) "クエスト達成まで" else "攻略不足額", "あと ¥${step.shortageMonthly.toGroupedString()} /月", "年額 ¥${step.shortageAnnual.toGroupedString()} 不足", QuestRose)
                }
                if (step.capital40.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(QuestInk).border(1.dp, Color(0x55313B50), RoundedCornerShape(9.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Savings, null, tint = QuestMint, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("利回り別・必要追加元本目安", color = QuestMint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CapitalTile(Modifier.weight(1f), "3.5%", step.capital35)
                            CapitalTile(Modifier.weight(1f), "4.0%", step.capital40, highlight = true)
                            CapitalTile(Modifier.weight(1f), "4.5%", step.capital45)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTile(modifier: Modifier, title: String, value: String, caption: String, valueColor: Color) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(QuestInk).padding(7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, color = QuestMuted, fontSize = 7.sp, maxLines = 1)
        Text(value, color = valueColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(caption, color = if (valueColor == QuestMint) QuestMint else QuestMuted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CapitalTile(modifier: Modifier, yield: String, amount: String, highlight: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(if (highlight) QuestGreen.copy(alpha = .15f) else QuestCard).border(if (highlight) 1.dp else 0.dp, if (highlight) QuestMint.copy(alpha = .3f) else Color.Transparent, RoundedCornerShape(6.dp)).padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(yield, color = if (highlight) QuestMint else QuestMuted, fontSize = 8.sp)
        Text(amount, color = if (highlight) QuestMint else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun Int.toGroupedString(): String = String.format("%,d", this)
