package com.kurogane.dividend.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.data.*
import com.kurogane.dividend.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit = {},
    onImportCsv: (List<CsvHoldingRow>, Set<String>) -> Unit = { _, _ -> },
    onImportDvm: (List<DvmBackupPreview>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var broker by remember { mutableStateOf(CsvBroker.SBI) }
    var csvResult by remember { mutableStateOf<CsvImportResult?>(null) }
    var selectedFile by remember { mutableStateOf("") }
    var dvmFiles by remember { mutableStateOf<List<DvmBackupPreview>>(emptyList()) }
    var dvmFileNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var complete by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("csv") }
    val selectedFields = remember { mutableStateListOf("shares", "averageCost", "account") }
    val selectedRowIndexes = remember { mutableStateListOf<Int>() }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            error = null; selectedFile = "CSVを解析中…"
            scope.launch {
                runCatching {
                    selectedFile = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } ?: uri.lastPathSegment ?: "CSV"
                    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("ファイルを読み込めません") }
                    require(bytes.size <= 10 * 1024 * 1024) { "CSVは10MB以下のファイルを選択してください" }
                    val catalog = LocalSecurityCatalog.names(context)
                    val funds = LocalSecurityCatalog.fundNames(context)
                    BrokerCsvImporter.parse(bytes, broker, catalog, funds)
                }.onSuccess { parsed -> csvResult = parsed; selectedRowIndexes.clear(); selectedRowIndexes.addAll(parsed.rows.indices); complete = false }.onFailure { error = it.message ?: "CSVを解析できませんでした" }
            }
        }
    }
    val dvmPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val name = withContext(Dispatchers.IO) { context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } } ?: "backup.dvm"
                    val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("ファイルを読み込めません") }
                    require(bytes.size <= 50 * 1024 * 1024) { "DVMバックアップが大きすぎます" }
                    DvmBackupImporter.parse(name, bytes) to name
                }.onSuccess { (preview, name) ->
                    dvmFiles = (dvmFiles.filterNot { it.kind == preview.kind } + preview)
                    dvmFileNames = dvmFileNames + (preview.kind.name to name)
                    error = null; complete = false
                }.onFailure { error = it.message ?: "バックアップを解析できませんでした" }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (mode == "csv") "証券CSVインポート" else "DVMバックアップ復元", color = OnSurface, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = OnSurface) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark))
    }, containerColor = BackgroundDark) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainer).padding(4.dp)) {
                    listOf("csv" to "証券CSV", "dvm" to "DVMバックアップ").forEach { (id, label) ->
                        Text(label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (mode == id) PrimaryEmerald else Color.Transparent).clickable { mode = id; error = null }.padding(11.dp), color = if (mode == id) Color.Black else OnSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (mode == "csv") {
                item {
                    Text("証券会社", color = OnSurface, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CsvBroker.entries.forEach { item ->
                            Text(item.label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (broker == item) SurfaceContainerHighest else SurfaceContainer).clickable { broker = item; csvResult = null }.padding(vertical = 11.dp, horizontal = 4.dp), color = if (broker == item) PrimaryEmerald else OnSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    ImportCard(onClick = { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, title = if (selectedFile.isBlank()) "CSVファイルを選択" else selectedFile, subtitle = "UTF-8 / Shift-JIS / EUC-JPを端末内で判定", icon = Icons.Default.UploadFile)
                }
                    csvResult?.let { result ->
                    item {
                        Text("解析結果：${result.encoding.name} ・ ${result.rows.size}銘柄", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        Text("取り込む項目", color = OnSurface, fontWeight = FontWeight.Bold)
                        listOf("shares" to "保有数量", "averageCost" to "取得単価", "account" to "口座区分", "currentPrice" to "現在値").forEach { (key, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = key in selectedFields, onCheckedChange = { checked -> if (checked) selectedFields.add(key) else selectedFields.remove(key) })
                                Text(label, color = OnSurface)
                            }
                        }
                        if (result.errors.isNotEmpty()) Text(result.errors.joinToString(), color = Color(0xFFFF8080))
                    }
                    itemsIndexed(result.rows) { index, row ->
                        val checked = index in selectedRowIndexes
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { if (checked) selectedRowIndexes.remove(index) else selectedRowIndexes.add(index) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { value -> if (value && index !in selectedRowIndexes) selectedRowIndexes.add(index) else if (!value) selectedRowIndexes.remove(index) })
                                Column(Modifier.weight(1f)) {
                                    Text("${if (row.isFund) "投資信託" else row.code}  ${row.name}", color = OnSurface, fontWeight = FontWeight.Bold)
                                    Text("数量 ${row.shares ?: 0} ・ 取得単価 ¥${row.averageCost ?: 0.0}" + (row.account?.let { " ・ $it" } ?: ""), color = OnSurfaceVariant, fontSize = 11.sp)
                                }
                                if (row.matchedName != null) Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald)
                            }
                        }
                    }
                    if (result.unmatchedFunds.isNotEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("照合できなかった投資信託 (${result.unmatchedFunds.size})", color = Color(0xFFFFB4AB), fontWeight = FontWeight.Bold)
                                result.unmatchedFunds.forEach { Text("・${it.sourceName}", color = OnSurfaceVariant, fontSize = 12.sp) }
                            }
                        }
                    }
                    item {
                        Button(onClick = {
                            val chosen = result.rows.filterIndexed { index, _ -> index in selectedRowIndexes }
                            if (chosen.isEmpty()) error = "取り込む銘柄を選択してください" else { onImportCsv(chosen, selectedFields.toSet()); complete = true }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text(if (complete) "取込済み" else "選択した銘柄を取り込む", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                item { Text("4種類のDVMファイルを個別に選択できます。復号と確認は端末内で行います。", color = OnSurfaceVariant) }
                item { ImportCard(onClick = { dvmPicker.launch(arrayOf("application/octet-stream", "application/*")) }, title = "DVMファイルを選択", subtitle = "銘柄・推移・カスタム銘柄・アラート", icon = Icons.Default.FolderOpen) }
                items(dvmFiles) { backup ->
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${backup.kind.label}　${backup.itemCount}件", color = OnSurface, fontWeight = FontWeight.Bold)
                            Text(dvmFileNames[backup.kind.name].orEmpty(), color = OnSurfaceVariant, fontSize = 11.sp)
                            if (backup.kind == DvmBackupKind.STOCKS) backup.stocks.take(30).forEach { stock ->
                                Text("${stock.code}  ${stock.name}  ${stock.shares.toInt()}株", color = OnSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
                if (dvmFiles.isNotEmpty()) item {
                    Button(onClick = { onImportDvm(dvmFiles); complete = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text(if (complete) "端末内に保存しました" else "確認したバックアップを保存", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
            error?.let { item { Text(it, color = Color(0xFFFF8080), fontSize = 12.sp) } }
            item { Text("ファイルの解析・銘柄照合・保存は端末内で行われ、Cloudflareへ送信されません。", color = OnSurfaceVariant, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun ImportCard(onClick: () -> Unit, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = PrimaryEmerald, modifier = Modifier.size(32.dp))
            Column { Text(title, color = OnSurface, fontWeight = FontWeight.Bold); Text(subtitle, color = OnSurfaceVariant, fontSize = 10.sp) }
        }
    }
}
