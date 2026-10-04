package com.kurogane.dividend.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.data.backup.LegacyBackupPayload
import com.kurogane.dividend.data.backup.LegacyBackupStore
import com.kurogane.dividend.data.backup.LegacyDvmBackupImporter
import com.kurogane.dividend.data.backup.toHoldings
import com.kurogane.dividend.ui.theme.BackgroundDark
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.SurfaceContainer
import com.kurogane.dividend.viewmodel.DividendViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyBackupImportScreen(
    viewModel: DividendViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<List<LegacyBackupPayload>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            error = null
            successMessage = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val importer = LegacyDvmBackupImporter()
                        uris.map { uri ->
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: throw IllegalArgumentException("ファイルを開けませんでした。")
                            importer.read(context.displayName(uri), bytes)
                        }.also { payloads ->
                            require(payloads.map { it.kind }.distinct().size == payloads.size) {
                                "同じ種類のバックアップが複数選択されています。"
                            }
                        }
                    }
                }.onSuccess { selected = it }
                    .onFailure { error = it.message ?: "バックアップを読み取れませんでした。" }
                busy = false
            }
        }
    }

    BackHandler(onBack = onBack)
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("バックアップを取り込みますか？") },
            text = {
                Text("選択した${selected.size}種類のデータを、この端末内に保存します。銘柄データを含む場合は、現在の銘柄一覧を置き換えます。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    busy = true
                    error = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { LegacyBackupStore(context).save(selected) }
                            selected.firstOrNull { it.kind.name == "STOCKS" }?.let { viewModel.replaceHoldings(it.toHoldings()) }
                        }.onSuccess {
                            successMessage = "バックアップを端末内に保存しました。"
                            selected = emptyList()
                        }.onFailure { error = it.message ?: "保存できませんでした。" }
                        busy = false
                    }
                }) { Text("取り込む", color = PrimaryEmerald) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("キャンセル") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("バックアップ取込", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, null, tint = PrimaryEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text("端末内で復号・解析", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "以前のAPKで作成したDVMバックアップを読み込みます。ファイルの復号と銘柄照合はこの端末内で行い、外部へ送信しません。",
                            color = OnSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("バックアップファイルを選択", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryEmerald) }
            error?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            successMessage?.let { message ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text(message, color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (selected.isNotEmpty()) {
                item { Text("解析結果", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                items(selected) { payload ->
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(payload.kind.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(payload.sourceName, color = OnSurfaceVariant, fontSize = 11.sp)
                            }
                            Text("${payload.records.size()} 件", color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Button(
                        onClick = { showConfirm = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("この端末に取り込む", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun Context.displayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "backup.dvm"
