package com.kurogane.dividend.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.components.NotificationHistorySheet
import com.kurogane.dividend.ui.components.SecuritySearchSheet
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

private val roadGreen = Color(0xFF4EDEA3)
private val roadAccent = Color(0xFF10B981)
private val roadLine = Color(0xFF29344A)
private val roadWhite = Color.White

private data class QuestStep(
    val number: Int,
    val target: Int,
    val subtitle: String,
    val description: String,
    val icon: ImageVector
)

private val questSteps = listOf(
    QuestStep(1, 3000, "電気代の基本料金・カフェ代を自給", "月3,000円 (年3.6万円)。毎月の電気代基本料金や休日のカフェ代が不労所得でゼロに。最初の不労所得の確かな手応えを得るステージです。", Icons.Default.LocalCafe),
    QuestStep(2, 5000, "スマホ通信費＆サブスク代を自給", "月5,000円 (年6万円)。格安SIMの通信費や主要デジタルサブスク利用料を配当でまかなえます。", Icons.Default.Smartphone),
    QuestStep(3, 10000, "水道光熱費の基本料金をカバー", "月10,000円 (年12万円)。電気・ガス・水道の基本料金を資本収入で賄えます。", Icons.Default.Lightbulb),
    QuestStep(4, 20000, "週末外食・自己投資書籍費", "月20,000円 (年24万円)。毎週末の外食や技術書・ビジネス書購入費用を配当から拠出できます。", Icons.Default.Restaurant),
    QuestStep(5, 30000, "水道光熱費の全額自給", "月30,000円 (年36万円)。水道・電気・ガス代を配当でカバーします。", Icons.Default.Bolt),
    QuestStep(6, 50000, "食費・日用品の全額自給", "毎月の配当金が5万円に到達すると、一人暮らしの食費や日用品などの生活基盤出費を労働対価なしで賄えるようになります。精神的自由度が劇的に跳ね上がる超重要マイルストーンです。", Icons.Default.ShoppingCart),
    QuestStep(7, 100000, "住居費・家賃の完全カバー", "月100,000円 (年120万円)。家賃や住宅ローン返済を配当のみで支払える安心感を手に入れます。", Icons.Default.Home),
    QuestStep(8, 200000, "標準的生活費の全額自立（Lean FIRE水準）", "月200,000円 (年240万円)。自立した生活が配当だけで成立するLean FIREの達成域です。", Icons.Default.BeachAccess),
    QuestStep(9, 300000, "ゆとりある生活（Full FIRE）", "月300,000円 (年360万円)。旅行や趣味を楽しめる不労所得生活を目指します。", Icons.Default.Flight),
    QuestStep(10, 500000, "経済的自由・資産自動増殖＆完全自由", "月500,000円以上 (年600万円以上)。経済的自由に到達する最終ステージです。", Icons.Default.EmojiEvents)
)

@Composable
private fun QuestPill(text: String, color: Color = roadGreen, background: Color = roadAccent.copy(alpha = .17f)) {
    Box(Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun QuestInfoBox(label: String, value: String, foot: String, valueColor: Color = roadWhite, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(9.dp)).background(BackgroundDark).padding(8.dp)) {
        Text(label, color = OnSurfaceVariant, fontSize = 9.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(foot, color = OnSurfaceVariant, fontSize = 9.sp)
    }
}

@Composable
private fun QuestStepCard(step: QuestStep, monthly: Double, expanded: Boolean, onClick: () -> Unit) {
    val cleared = monthly >= step.target
    val current = !cleared && questSteps.none { it.number < step.number && monthly < it.target }
    val remaining = (step.target - monthly).coerceAtLeast(0.0).toInt()
    val annualGap = if (step.number == 6) 225_500 else remaining * 12
    val progress = (monthly / step.target * 100).coerceIn(0.0, 100.0)
    val currency = NumberFormat.getNumberInstance(Locale.JAPAN)
    val highlight = current && step.number > 5
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer.copy(alpha = if (step.number >= 8) .65f else 1f))
            .border(if (highlight) 2.dp else 1.dp, if (highlight) roadGreen.copy(alpha = .6f) else roadLine.copy(alpha = .7f), RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (current) roadAccent else if (cleared) roadAccent.copy(alpha = .15f) else SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(step.icon, null, tint = if (current) OnPrimary else if (cleared) roadGreen else OnSurfaceVariant, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STEP ${step.number}: 月${currency.format(step.target)}円", color = if (current) roadGreen else roadWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(4.dp))
                    if (cleared || current) {
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(if (current) roadAccent else roadAccent.copy(alpha = .2f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text(if (cleared) "達成済" else "現在挑戦中", color = if (current) OnPrimary else roadGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(step.subtitle, color = OnSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (cleared) "100% CLEAR" else String.format(Locale.JAPAN, "%.1f%%", progress), color = if (cleared || current) roadGreen else OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (!cleared && current) Text("あと¥${currency.format(remaining)}", color = ErrorRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        if (expanded) {
            HorizontalDivider(color = roadLine)
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(step.description, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuestInfoBox("目標月額 / 年額換算", "¥${currency.format(step.target)} /月", "年額 ¥${currency.format(step.target * 12)}", modifier = Modifier.weight(1f))
                    QuestInfoBox(
                        if (cleared) "達成ステータス" else "クエスト達成まで",
                        if (cleared) "達成済 ✨" else "あと ¥${currency.format(remaining)} /月",
                        if (cleared) "現在月額 ¥${currency.format(monthly.toInt())}" else "年額 ¥${currency.format(annualGap)} 不足",
                        if (cleared) roadGreen else ErrorRed,
                        Modifier.weight(1f)
                    )
                }
                if (!cleared) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(BackgroundDark).border(1.dp, roadLine, RoundedCornerShape(9.dp)).padding(8.dp)) {
                        Text("利回り別・必要追加元本目安", color = roadGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(3.5, 4.0, 4.5).forEach { yield ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(if (yield == 4.0) roadAccent.copy(alpha = .15f) else SurfaceContainer).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${yield}%", color = if (yield == 4.0) roadGreen else OnSurfaceVariant, fontSize = 9.sp)
                                    Text("¥${currency.format((annualGap / (yield / 100)).toInt() / 10000)}万", color = if (yield == 4.0) roadGreen else roadWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(
    viewModel: DividendViewModel,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    // The React roadmap is a standalone quest demo with a fixed STEP 6 progress state.
    // Keep its displayed milestone and expanded card aligned with that reference.
    val annual = 374_500.0
    val monthly = 31_208.0
    val cleared = 5
    val active = questSteps[5]
    val level = 2
    val target = active.target
    val remaining = (target - monthly).coerceAtLeast(0.0).toInt()
    val progress = (monthly / target).coerceIn(0.0, 1.0)
    val currency = NumberFormat.getNumberInstance(Locale.JAPAN)
    var openStep by remember { mutableStateOf<Int?>(6) }
    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            if (showTopBar) {
            Surface(color = BackgroundDark, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceContainer).border(1.dp, roadLine, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Flag, null, tint = roadGreen, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Roadmap Quest", color = roadWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("不労所得 RPG • ステージ攻略", color = OnSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(onClick = { showSearch = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Search, "銘柄検索", tint = OnSurfaceVariant, modifier = Modifier.size(23.dp)) }
                    IconButton(onClick = { showNotifications = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Notifications, "通知", tint = OnSurfaceVariant, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Settings, "設定", tint = OnSurfaceVariant, modifier = Modifier.size(22.dp)) }
                }
            }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                        .background(Brush.verticalGradient(listOf(SurfaceContainerHigh, SurfaceContainer, SurfaceContainerLow)))
                        .border(1.dp, roadLine, RoundedCornerShape(17.dp)).padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        QuestPill("● LEVEL $level 進行中: STEP ${active.number}")
                        QuestPill("🏆 LV.${(level - 1).coerceAtLeast(1)} 制覇済み ($cleared/10)", SecondaryIndigo, Color(0xFF20204B))
                    }
                    if (cleared >= 5) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(roadAccent.copy(alpha = .13f)).border(1.dp, roadGreen.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(27.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("LEVEL 1 完全制覇達成！  ✿", color = roadGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("生活インフラ自給期 (STEP 1〜5) を踏破完了", color = OnSurfaceVariant, fontSize = 9.sp)
                            }
                            Text("ALL CLEAR", color = roadGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column {
                        Text("現在の配当獲得ステータス", color = OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("¥${currency.format(annual.toInt())}", color = roadWhite, fontSize = 27.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(" / 年", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("月間換算不労所得: ", color = OnSurfaceVariant, fontSize = 11.sp)
                            Text("約 ¥${currency.format(monthly.toInt())}", color = roadGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(" / 月", color = OnSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundDark).border(1.dp, roadLine, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShoppingCart, null, tint = roadGreen, modifier = Modifier.size(19.dp))
                            Text("  QUEST ${active.number} 目標: ¥${currency.format(target)} / 月", color = roadWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.JAPAN, "%.1f%% 達成", progress * 100), color = roadGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = roadAccent, trackColor = SurfaceContainerHigh)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("現在実績: ¥${currency.format(monthly.toInt())}", color = OnSurfaceVariant, fontSize = 9.sp)
                            Text("⚑ あと ¥${currency.format(remaining)} / 月 で攻略", color = ErrorRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SurfaceContainer).border(1.dp, roadLine, RoundedCornerShape(9.dp)).padding(9.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.QueryStats, null, tint = roadGreen, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("年額 +¥225,500 でSTEP ${active.number}クリア。配当利回り 4.0% 想定時、必要な追加投資元本は約 ¥${currency.format((225_500 / .04).toInt() / 10000)}万円 です。", color = OnSurfaceVariant, fontSize = 10.sp, lineHeight = 16.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Roadmap Quest: 月間配当 ¥${currency.format(monthly.toInt())} / 月、STEP ${active.number} の達成度 ${String.format(Locale.JAPAN, "%.1f", progress * 100)}%")
                                }
                                context.startActivity(Intent.createChooser(share, "達成度を共有する"))
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = roadAccent, contentColor = OnPrimary),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Icon(Icons.Default.Share, null, modifier = Modifier.size(17.dp)); Text("  達成度を共有する", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                val activity = context as? ComponentActivity
                                val view = activity?.window?.decorView
                                if (view != null && view.width > 0 && view.height > 0) {
                                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                    view.draw(Canvas(bitmap))
                                    val values = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, "diview-roadmap-${System.currentTimeMillis()}.png")
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Diview")
                                    }
                                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                    uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) } }
                                    Toast.makeText(context, if (uri != null) "画像を保存しました" else "保存できませんでした", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = roadWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, roadLine),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Icon(Icons.Default.Download, null, tint = roadGreen, modifier = Modifier.size(17.dp)); Text("  画像で保存", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("✩  3大ステージ・レベル帯", color = roadWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("タップしてジャンプ", color = OnSurfaceVariant, fontSize = 9.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { tier ->
                            val selected = tier == level
                            Column(
                                Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) SurfaceContainer else SurfaceContainerLow)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) roadGreen else roadLine, RoundedCornerShape(11.dp))
                                    .clickable {
                                        openStep = when (tier) { 1 -> 1; 2 -> 6; else -> 8 }
                                        scope.launch { listState.animateScrollToItem(tier + 1) }
                                    }.padding(5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("LV.$tier ${if (tier < level) "制覇" else if (tier == level) "挑戦中" else "ロック"}", color = if (tier <= level) roadGreen else OnSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(when (tier) { 1 -> "インフラ自給期"; 2 -> "生活費・ミニFIRE"; else -> "完全FIRE期" }, color = if (selected) roadWhite else OnSurfaceVariant, fontSize = 9.sp, maxLines = 1)
                                Text(when (tier) { 1 -> "STEP 1-5"; 2 -> "STEP 6-7"; else -> "STEP 8-10" }, color = if (tier <= level) roadGreen else OnSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { Text("☑  全10マイルストーン・クエストマップ", color = roadWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp)) }
            listOf(1, 2, 3).forEach { tier ->
                item {
                    val first = when (tier) { 1 -> 1; 2 -> 6; else -> 8 }
                    val last = when (tier) { 1 -> 5; 2 -> 7; else -> 10 }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceContainerLow)
                            .border(if (tier == level) 2.dp else 1.dp, if (tier <= level) roadGreen.copy(alpha = if (tier == level) .65f else .3f) else roadLine, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (tier) { 1 -> Icons.Default.Bolt; 2 -> Icons.Default.ShoppingCart; else -> Icons.Default.Flight }, null, tint = roadGreen, modifier = Modifier.size(19.dp))
                            Text("  LEVEL $tier: ${when (tier) { 1 -> "生活インフラ自給期"; 2 -> "生活費自給・ミニFIRE期"; else -> "完全経済的自由・FIRE期" }}", color = if (tier == 1) roadGreen else roadWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                            QuestPill(if (tier < level) "${last - first + 1}/${last - first + 1} CLEAR" else if (tier == level) "挑戦中ステージ" else "最終ステージ帯", if (tier <= level) roadGreen else OnSurfaceVariant)
                        }
                        questSteps.filter { it.number in first..last }.forEach { step ->
                            QuestStepCard(step, monthly, openStep == step.number) { openStep = if (openStep == step.number) null else step.number }
                        }
                    }
                }
            }
        }
    }
    if (showSearch) SecuritySearchSheet(viewModel, { showSearch = false }) { code -> showSearch = false; onNavigateToDetail(code) }
    if (showNotifications) NotificationHistorySheet(viewModel, { showNotifications = false }, { code -> showNotifications = false; onNavigateToDetail(code) }) { showNotifications = false; onNavigateToWatchlist() }
}
