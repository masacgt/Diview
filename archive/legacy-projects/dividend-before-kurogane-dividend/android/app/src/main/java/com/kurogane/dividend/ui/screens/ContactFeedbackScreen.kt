package com.kurogane.dividend.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFeedbackScreen(
    onBack: () -> Unit,
    viewModel: DividendViewModel
) {
    var selectedCategory by remember { mutableStateOf("feature") } // "feature", "bug", "stock", "other"
    var senderName by remember { mutableStateOf("") }
    var senderEmail by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var attachDiagnostics by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val faqs = remember {
        listOf(
            FAQItem(
                "楽天証券やSBI証券のCSVインポートはどのように行うのですか？",
                "「インポート」タブ、または資産管理画面の右上のCSVアイコンからインポート画面を開きます。証券会社（楽天・SBI・SBIネオモバイル）を選択し、証券アプリからエクスポートしたCSVファイルをドロップするだけで保有銘柄・保有株数・取得価格が自動で一括反映されます。"
            ),
            FAQItem(
                "保有株数や取得価格を変更したい場合はどうすればよいですか？",
                "資産管理画面の銘柄一覧カードをタップすると、取得価格と保有株数の編集フォームが立ち上がります。数値を変更して「更新」をタップすると、即座にポートフォリオの全値、円グラフ、および配当ロードマップが自動再計算されます。"
            ),
            FAQItem(
                "買場目安アラートとはどのような機能ですか？",
                "ウォッチリスト（監視銘柄）の各カードで右側の通知アイコンをONにすると、株価が設定した目標利回り（ターゲット）に到達した際に、ブラウザまたはスマホのプッシュ通知でお知らせします。通知をONにするとリアルタイムに買場目安価格（逆算株価）が自動計算されます。"
            ),
            FAQItem(
                "ロードマップ達成率はどのように計算されていますか？",
                "登録したすべての保有銘柄から得られる年間予想配当金を算出し、その12分の1（月換算額）を計算します。ロードマップの各STEP（電気代、水道代、食費、FIREなど）の目標月額に対する、あなたの現在の月間配当額の割合が％としてリアルタイムに反映されます。"
            )
        )
    }

    var openFaqIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContactSupport,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ご意見・不具合報告", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceNavy)
            )
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Support Desk Intro Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Official Support", color = PrimaryEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Diview サポートデスク", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "配当管理や操作についてご不明な点、改善のご要望をお気軽にお寄せください。通常24時間以内に返信いたします。",
                            color = SoftGray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // FAQ Accordion Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Text("よくあるご質問 (FAQ)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("タップして展開", color = SoftGray, fontSize = 10.sp)
                }

                faqs.forEachIndexed { idx, faq ->
                    val open = openFaqIndex == idx
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openFaqIndex = if (open) null else idx },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Q", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                    Text(faq.q, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(
                                    imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (open) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BackgroundDark.copy(alpha = 0.5f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        faq.a,
                                        color = SoftGray,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Contact Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.RateReview, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(16.dp))
                        Text("お問い合わせ・ご意見", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("カテゴリ *", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        val categories = listOf(
                            CategoryButton("feature", "機能の改善要望", Icons.Default.Lightbulb),
                            CategoryButton("bug", "不具合の報告", Icons.Default.BugReport),
                            CategoryButton("stock", "銘柄データの誤り", Icons.Default.TableChart),
                            CategoryButton("other", "その他", Icons.Default.MoreHoriz)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.take(2).forEach { cat ->
                                val active = selectedCategory == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryEmerald else BackgroundDark)
                                        .border(BorderStroke(1.dp, if (active) Color.Transparent else Color(0xFF1E293B)), RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(cat.icon, contentDescription = null, tint = if (active) Color.Black else SoftGray, modifier = Modifier.size(14.dp))
                                        Text(cat.label, color = if (active) Color.Black else SoftGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.drop(2).forEach { cat ->
                                val active = selectedCategory == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryEmerald else BackgroundDark)
                                        .border(BorderStroke(1.dp, if (active) Color.Transparent else Color(0xFF1E293B)), RoundedCornerShape(8.dp))
                                        .clickable { selectedCategory = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(cat.icon, contentDescription = null, tint = if (active) Color.Black else SoftGray, modifier = Modifier.size(14.dp))
                                        Text(cat.label, color = if (active) Color.Black else SoftGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Sender Name Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("お名前 / ニックネーム", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("任意", color = SoftGray, fontSize = 9.sp)
                        }
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = { senderName = it },
                            placeholder = { Text("配当雪だるま", color = SoftGray, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Return Email Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("返信用メールアドレス", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("返信が必要な場合", color = SoftGray, fontSize = 9.sp)
                        }
                        OutlinedTextField(
                            value = senderEmail,
                            onValueChange = { senderEmail = it },
                            placeholder = { Text("your-email@example.com", color = SoftGray, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Message Text Multi-Line Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("お問い合わせ内容 *", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("不具合内容や改善案などをご自由にご記入ください。", color = SoftGray, fontSize = 11.sp) },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundDark,
                                unfocusedContainerColor = BackgroundDark,
                                focusedBorderColor = PrimaryEmerald,
                                unfocusedBorderColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Diagnostics Attachment Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundDark)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("診断レポートを添付", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("アプリのクラッシュログ、OSバージョン情報を含める", color = SoftGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = attachDiagnostics,
                            onCheckedChange = { attachDiagnostics = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryEmerald)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Submit Button with loading simulation
                    Button(
                        onClick = {
                            if (messageText.trim().isNotEmpty()) {
                                isSubmitting = true
                                coroutineScope.launch {
                                    delay(1000)
                                    isSubmitting = false
                                    messageText = ""
                                    senderName = ""
                                    senderEmail = ""
                                    onBack() // Back out on successful transmission!
                                }
                            }
                        },
                        enabled = !isSubmitting && messageText.trim().isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmeraldContainer,
                            disabledContainerColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = OnPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(14.dp))
                                Text("送信する", color = OnPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class FAQItem(val q: String, val a: String)
data class CategoryButton(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
