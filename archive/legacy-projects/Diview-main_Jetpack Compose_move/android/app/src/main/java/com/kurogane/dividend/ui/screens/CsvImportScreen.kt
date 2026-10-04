package com.kurogane.dividend.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.ui.theme.*
import com.kurogane.dividend.viewmodel.DividendViewModel
import java.nio.charset.Charset
import java.util.UUID

private data class CsvRow(val code: String, val name: String, val shares: Int, val price: Double, val action: String)

private const val csvDemoFile = "monex_portfolio_202412.csv"
private const val csvDemoText = "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ自動車,100,2450\nAAPL,Apple Inc.,10,192.8\neMAXIS,eMAXIS Slim 全世界株式,50000,24460"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(viewModel: DividendViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val holdings by viewModel.holdings.collectAsState()
    var selectedBroker by remember { mutableStateOf("マネックス証券") }
    var guideBroker by remember { mutableStateOf("マネックス証券") }
    var guideOpen by remember { mutableStateOf(true) }
    var fileName by remember { mutableStateOf(csvDemoFile) }
    var csvText by remember { mutableStateOf(csvDemoText) }
    var error by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var parsing by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf(emptyList<CsvRow>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("ファイルを読み込めません")
            require(bytes.size <= 10 * 1024 * 1024) { "CSVファイルは10MB以下を選択してください。" }
            val utf8 = bytes.toString(Charsets.UTF_8)
            val decoded = if (utf8.contains('\uFFFD')) String(bytes, Charset.forName("Shift_JIS")) else utf8
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "portfolio.csv"
            csvText = decoded
            error = ""
            step = 1
        }.onFailure { error = it.message ?: "ファイルを読み込めませんでした。" }
    }

    val brokers = listOf("SBI証券", "楽天証券", "マネックス証券", "松井証券")
    val parseCsv: () -> Unit = {
        parsing = true
        val lines = csvText.lineSequence().filter { it.isNotBlank() }.map { line ->
            // CSV fields may be quoted and may contain commas.
            Regex("(?:^|,)(?:\"([^\"]*(?:\"\"[^\"]*)*)\"|([^,]*))").findAll(line)
                .map { (it.groups[1]?.value ?: it.groups[2]?.value.orEmpty()).replace("\"\"", "\"").trim() }.toList()
        }.toList()
        val headerAt = lines.indexOfFirst { cells -> cells.any { it.contains("銘柄コード") || it.contains("コード") || it.equals("Code", true) } }
        if (headerAt < 0 || lines.size <= headerAt + 1) {
            error = "CSVから銘柄コードの見出しを確認できませんでした。証券会社の保有残高CSVを選び直してください。"
            parsing = false
        } else {
            val headers = lines[headerAt].map { it.lowercase() }
            fun col(keys: List<String>) = headers.indexOfFirst { h -> keys.any { h.contains(it.lowercase()) } }
            val codeCol = col(listOf("銘柄コード", "コード", "code", "ticker"))
            val nameCol = col(listOf("銘柄名", "銘柄", "商品名", "name", "ファンド名"))
            val sharesCol = col(listOf("保有数量", "保有株数", "保有口数", "数量", "株数", "shares", "口数"))
            val priceCol = col(listOf("取得単価", "平均取得価額", "取得価格", "取得単価(円)", "単価", "price"))
            val seen = holdings.map { it.code.uppercase() }.toSet()
            rows = lines.drop(headerAt + 1).mapNotNull { cells ->
                val code = cells.getOrNull(codeCol).orEmpty().trim().uppercase()
                val name = cells.getOrNull(nameCol).orEmpty().trim()
                val qty = cells.getOrNull(sharesCol).orEmpty().replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.toInt() ?: 0
                val price = cells.getOrNull(priceCol).orEmpty().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                if (code.isBlank() || name.isBlank() || qty <= 0 || price <= 0) null
                else CsvRow(code, name, qty, price, if (code in seen) "既存銘柄" else "新規")
            }
            error = if (rows.isEmpty()) "銘柄名・数量・取得単価を読み取れる行がありません。CSV形式を確認してください。" else ""
            if (rows.isNotEmpty()) step = 2
            parsing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CSVインポート", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "戻る", tint = Color.White) } },
                actions = { IconButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) { Icon(Icons.Default.Search, "CSVを選択", tint = OnSurfaceVariant) }; IconButton(onClick = onBack) { Icon(Icons.Default.Notifications, "通知", tint = OnSurfaceVariant) }; IconButton(onClick = onBack) { Icon(Icons.Default.Settings, "設定", tint = OnSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }, containerColor = BackgroundDark
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Color(0xFF060E20), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("● INTERACTIVE WIZARD SANDBOX", color = PrimaryEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("10 Scenarios Live", color = OnSurfaceVariant, fontSize = 10.sp) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("S1: ファイル選択" to 1, "S1: 解析中アニメ" to 1, "S1: エラー画面" to 1, "S2: プレビュー" to 2, "S2: 差分・重複検出" to 2, "S3: 完了・反映" to 3).forEach { (label, target) ->
                            Surface(onClick = { step = target }, shape = CircleShape, color = if (step == target) PrimaryEmeraldContainer else SurfaceContainerHigh) { Text(label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = if (step == target) Color(0xFF003824) else OnSurfaceVariant, fontSize = 10.sp, maxLines = 1) }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceContainerLow).border(1.dp, Color(0xFF26334A), RoundedCornerShape(16.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("STEP $step / 3", color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(when (step) { 1 -> "CSVファイルの選択"; 2 -> "銘柄照合プレビュー"; else -> "インポート完了" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    Text(if (parsing) "解析処理中" else if (step == 2) "照合完了" else "待機中", color = PrimaryEmerald, fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { index -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (index < step) PrimaryEmerald else SurfaceContainerHighest)) } }
            }

            if (step == 1) {
                Text("対象証券会社", color = OnSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    brokers.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { broker ->
                            val selected = selectedBroker == broker
                            Surface(Modifier.weight(1f).clickable { selectedBroker = broker; guideBroker = broker }, shape = RoundedCornerShape(13.dp), color = SurfaceContainer, border = androidx.compose.foundation.BorderStroke(if (selected) 1.dp else .5.dp, if (selected) PrimaryEmerald else Color(0xFF26334A))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(broker, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(when (broker) { "SBI証券" -> "自動判定 推奨"; "楽天証券" -> "全資産CSV対応"; "マネックス証券" -> "残高明細CSV"; else -> "現物残高CSV" }, color = if (selected) PrimaryEmerald else OnSurfaceVariant, fontSize = 10.sp) }; Icon(if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) PrimaryEmerald else OnSurfaceVariant, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    } }
                    Surface(Modifier.fillMaxWidth().clickable { selectedBroker = "その他" }, shape = RoundedCornerShape(12.dp), color = SurfaceContainer, border = androidx.compose.foundation.BorderStroke(.5.dp, Color(0xFF26334A))) { Text("☷  その他（汎用CSV / 各種フォーマット）", Modifier.padding(12.dp), color = Color.White, fontSize = 11.sp) }
                }
                Surface(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, color = SurfaceContainerLow, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = .55f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Description, null, tint = PrimaryEmerald, modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f)) { Text(if (fileName.isBlank()) "CSVファイルを選択またはドロップ" else fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(if (fileName.isBlank()) "CSV形式（最大10MB）" else "サンプル • ${csvText.lineSequence().count() - 1}銘柄 • 文字コード自動判定", color = if (fileName.isBlank()) OnSurfaceVariant else PrimaryEmerald, fontSize = 10.sp) }
                        IconButton(onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) { Icon(Icons.Default.UploadFile, "別のファイルを選ぶ", tint = PrimaryEmerald) }
                    }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF9898), fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF351E2A)).padding(10.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { guideOpen = !guideOpen }, horizontalArrangement = Arrangement.SpaceBetween) { Text("各証券会社のCSV出力方法ガイド", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold); Icon(if (guideOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnSurfaceVariant) }
                    if (guideOpen) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { brokers.forEach { broker -> FilterChip(selected = guideBroker == broker, onClick = { guideBroker = broker }, label = { Text(broker.removeSuffix("証券"), fontSize = 10.sp) }) } }
                        Text("${guideBroker}の保有資産画面からCSVをダウンロードし、この端末で選択してください。個人情報と保有資産データは外部サーバーへ送信せず、端末内で解析します。", color = OnSurfaceVariant, fontSize = 10.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { fileName = ""; csvText = ""; rows = emptyList(); error = ""; step = 1 },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceVariant)
                    ) { Text("クリア", fontWeight = FontWeight.Bold) }
                    Button(onClick = parseCsv, enabled = fileName.isNotBlank() && !parsing, modifier = Modifier.weight(2f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald, disabledContainerColor = SurfaceContainerHigh), shape = RoundedCornerShape(12.dp)) { Text(if (parsing) "CSVを解析中…" else "ファイルを解析する  →", color = Color(0xFF003824), fontWeight = FontWeight.Bold) }
                }
            }
            if (step == 2) {
                Text("解析完了: ${rows.size}件の銘柄を検出", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                rows.forEach { row -> Surface(color = SurfaceContainer, shape = RoundedCornerShape(12.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("${row.code}  ${row.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("${row.shares}株 • 取得単価 ¥${row.price}", color = OnSurfaceVariant, fontSize = 10.sp) }; Text(row.action, color = if (row.action == "新規") PrimaryEmerald else OnSurfaceVariant, fontSize = 10.sp) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f)) { Text("戻る") }
                    Button(onClick = {
                        rows.filter { row -> holdings.none { it.code.equals(row.code, true) } }.forEach { row ->
                            val category = if (row.code.any(Char::isLetter)) Category.US else Category.JAPAN
                            viewModel.addHolding(Holding(UUID.randomUUID().toString(), row.code, row.name, "CSV取込", if (category == Category.US) "NASDAQ" else "CSV", category, row.price, if (category == Category.US) "$" else "¥", row.shares, row.price, 0.0, 0.0, 0.0, "未設定", "—", emptyList(), "未設定", "特定口座", "CSV取込", true, if (category == Category.US) 154.0 else 1.0))
                        }
                        step = 3
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text("取込を実行する", color = Color(0xFF003824)) }
                }
            }
            if (step == 3) {
                Surface(color = SurfaceContainerLow, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald, modifier = Modifier.size(42.dp)); Text("インポートが完了しました", color = Color.White, fontWeight = FontWeight.Bold); Text("CSV内の銘柄情報を端末に保存しました。", color = OnSurfaceVariant, fontSize = 11.sp); Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text("資産管理へ戻る", color = Color(0xFF003824)) } } }
            }
        }
    }
}
