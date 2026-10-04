package com.kurogane.dividend.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsPrivacyScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf("privacy") }
    var openArticle1 by remember { mutableStateOf(true) }
    var openArticle2 by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms Of Service And Privacy Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kurogane Security Core", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("端末内 AES-256 暗号化　•　クラウド送信 0 Byte", style = MaterialTheme.typography.bodySmall)
                        Text("第三者提供・売買ゼロ　•　証券認証情報は非保持", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("terms" to "利用規約", "privacy" to "プライバシー").forEach { (id, label) ->
                        FilterChip(
                            selected = selectedTab == id,
                            onClick = { selectedTab = id },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (selectedTab == "terms") {
                item {
                    InfoCard("Kurogane利用規約", "本規約は、ポートフォリオ分析アプリ「Kurogane Dividend」の利用条件を定めるものです。")
                }
                item {
                    ArticleCard("第1条（総則・本サービスの目的）", openArticle1, { openArticle1 = !openArticle1 }) {
                        Text("本アプリは、ユーザー自身が入力した保有銘柄、取得価格、保有株数に基づき、年間想定配当額や受取月を試算・可視化する個人向けツールです。証券会社のIDやパスワードは預かりません。", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    ArticleCard("第2条（投資助言に関する免責事項）", openArticle2, { openArticle2 = !openArticle2 }) {
                        Text("本アプリ内の指標は参考値であり、特定の有価証券の売買を推奨するものではありません。配当金は企業の財務状況や市場動向により減配・無配となる場合があります。投資判断はご自身で行ってください。", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    InfoCard("第3条（知的財産権および禁止事項）", "本アプリ内のUIデザイン、グラフィック、アルゴリズムの著作権は権利者に帰属します。無断での複製や不正な利用は禁止します。")
                }
                item {
                    InfoCard("第4条（利用環境およびローカルデータ管理）", "証券会社のログインID・取引パスワードは取得・保管しません。保有資産データは端末内に保存されます。端末の紛失や機種変更に備え、必要に応じてCSVバックアップをご利用ください。")
                }
                item {
                    InfoCard("安心してお使いいただくために", "Kurogane Dividendは金融機関の認証情報をお預かりしません。配当受取日と利回りの可視化を、安全にお楽しみください。")
                }
                item {
                    InfoCard("利用規約について", "本アプリをダウンロード、インストール、または利用開始することで、利用規約およびプライバシーポリシーの内容に同意したものとみなします。")
                }
            } else {
                item {
                    InfoCard("ZERO KNOWLEDGE ARCHITECTURE｜金融データ端末内完結宣言", "保有銘柄・投資金額・配当キャッシュフローは、クラウドサーバーへ一切送信されず端末内のみで演算処理されます。\n\n外部送信　0 Bytes　　 保護規格　AES-256")
                }
                item {
                    InfoCard("1. 取得する情報とその範囲", "当社がアプリの安定運用および技術的改善のためにのみ取得する情報は以下の通りです。\n\n✓ 匿名化クラッシュログ：アプリクラッシュ時のスタックトレース、OSバージョン、デバイスモデル（個人を特定する情報は含まれません）\n\n✓ 匿名の機能利用統計：画面遷移頻度などの集計情報")
                }
                item {
                    InfoCard("2. 情報の利用目的", "収集した匿名ログデータは、バグの特定・解消、パフォーマンス改善、新機能の開発検証にのみ限定して使用されます。マーケティング目的での第三者名簿提供などは行いません。")
                }
                item {
                    InfoCard("3. 第三者提供および外部SDK", "アプリの正常な動作および課金決済の管理のため、信頼性の高いSDKを利用します。\n\nStoreKit / Google Billing：サブスクリプション購入領収証の検証\n\nCrashlytics：匿名クラッシュ診断・アプリ安定化")
                }
                item {
                    InfoCard("4. セキュリティ管理体制（SQLiteローカル暗号化）", "ポートフォリオデータは、端末固有のセキュアキーストア（iOS Keychain / Android Keystore）から導出されたマスターキーにより、ローカルデータベース（SQLite）レベルで暗号化されています。")
                }
                item {
                    InfoCard("5. お問い合わせ窓口", "規約内容、プライバシー情報の取り扱い、またはアプリに関するご不明点はサポート窓口までお寄せください。")
                }
                item {
                    InfoCard("安心してお使いいただくために", "Kurogane Dividendは金融機関の認証情報（ログインパスワード、取引暗証番号等）をお預かりしません。日々の配当受取日と利回り可視化を、安全にお楽しみください。")
                }
                item {
                    InfoCard("利用規約について", "本アプリのダウンロード、インストール、または利用開始をもって、利用規約およびプライバシーポリシーの内容に同意いただいたものとみなします。")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArticleCard(title: String, expanded: Boolean, onToggle: () -> Unit, body: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp)) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Default.ExpandMore, contentDescription = if (expanded) "閉じる" else "開く")
            }
            if (expanded) {
                HorizontalDivider()
                Box(Modifier.padding(16.dp)) { body() }
            }
        }
    }
}
