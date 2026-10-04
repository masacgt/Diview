package com.kurogane.dividend.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

private val supportFaq = listOf(
    "証券口座の自動連携はできますか？" to "証券会社のIDやパスワードを入力する自動連携はありません。保有銘柄は手動で登録できます。",
    "CSVインポートで対応している証券会社は？" to "設定のデータ管理画面に対応形式を表示しています。取り込み前にCSVの形式をご確認ください。",
    "新NISAの非課税計算はどうなっていますか？" to "口座区分に応じて配当額を試算します。実際の税額は証券会社の明細をご確認ください。"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactFeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var openFaq by remember { mutableIntStateOf(-1) }
    var category by remember { mutableStateOf("機能の改善要望") }
    var senderName by remember { mutableStateOf("") }
    var senderEmail by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showMessageError by remember { mutableStateOf(false) }
    var attachDiagnostics by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact And Feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "設定に戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Official Support  •", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Kurogane サポート", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("配当管理や操作についてご不明な点、改善のご要望をお気軽にお寄せください。通常24時間以内に返信いたします。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("よくあるご質問 (FAQ)", fontWeight = FontWeight.Bold)
                }
            }
            items(supportFaq.size) { index ->
                val (question, answer) = supportFaq[index]
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { openFaq = if (openFaq == index) -1 else index }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(question, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Icon(Icons.Default.ExpandMore, contentDescription = if (openFaq == index) "閉じる" else "開く")
                        }
                        if (openFaq == index) {
                            HorizontalDivider()
                            Text(answer, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text("お問い合わせ・ご意見", fontWeight = FontWeight.Bold)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("カテゴリ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        listOf("機能の改善要望", "不具合の報告", "銘柄データの誤り", "その他").chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { option ->
                                    FilterChip(
                                        selected = category == option,
                                        onClick = { category = option },
                                        label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = { senderName = it },
                            label = { Text("お名前 / ニックネーム（任意）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = senderEmail,
                            onValueChange = { senderEmail = it },
                            label = { Text("返信用メールアドレス（任意）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { if (it.length <= 1000) { message = it; showMessageError = false } },
                            label = { Text("お問い合わせ内容・ご要望 *") },
                            minLines = 4,
                            maxLines = 7,
                            isError = showMessageError,
                            supportingText = { Text(if (showMessageError) "お問い合わせ内容をご入力ください" else "${message.length} / 1,000") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("▣", color = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text("端末診断情報を添付　推奨", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("v1.2.0 (Build 248) • iOS/Android OS情報", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = attachDiagnostics, onCheckedChange = { attachDiagnostics = it })
                            }
                        }
                        Text("✓ 入力内容はSSL暗号化され、お問い合わせ対応のみに厳重管理されます", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                if (message.isBlank()) {
                                    showMessageError = true
                                } else {
                                    val body = buildString {
                                        appendLine("カテゴリ: $category")
                                        if (senderName.isNotBlank()) appendLine("お名前: $senderName")
                                        if (senderEmail.isNotBlank()) appendLine("返信用メールアドレス: $senderEmail")
                                        appendLine()
                                        append(message.trim())
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Kurogane Dividend お問い合わせ: $category")
                                        putExtra(Intent.EXTRA_TEXT, body)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "お問い合わせ内容を共有"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("送信する")
                        }
                        Text("共有先のアプリを選んで送信してください。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♣", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("公式コミュニティ・応援", fontWeight = FontWeight.Bold)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("#　開発チーム公式 X (旧Twitter)　✓", fontWeight = FontWeight.Bold)
                        Text("最新の配当データ更新・アップデート速報を発信中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("☆　アプリストアで応援レビューを書く", fontWeight = FontWeight.Bold)
                        Text("★5レビューが個人開発の大きな励みになります", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("♙　Kurogane プライバシー・セキュリティ保証", fontWeight = FontWeight.Bold)
                        Text("送信されたお問い合わせ内容およびメールアドレスは、返信および機能改善の統計分析目的でのみ厳重に取り扱われます。第三者への提供、マーケティング用途への転用、資産状況の追跡などは一切行いません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
