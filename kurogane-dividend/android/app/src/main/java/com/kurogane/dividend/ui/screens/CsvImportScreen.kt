package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.AdaptiveTopAppBar
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
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
import kotlinx.coroutines.Job
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit = {},
    onImportCsv: suspend (List<CsvHoldingRow>, Set<String>) -> Unit = { _, _ -> },
    onImportDvm: suspend (List<DvmBackupPreview>) -> Unit = {}
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var broker by remember { mutableStateOf<CsvBroker?>(null) }
    var brokerWasAutoDetected by remember { mutableStateOf(false) }
    var csvResult by remember { mutableStateOf<CsvImportResult?>(null) }
    var selectedFile by remember { mutableStateOf("") }
    var dvmFiles by remember { mutableStateOf<List<DvmBackupPreview>>(emptyList()) }
    var dvmFileNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var complete by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("csv") }
    val selectedFields = remember { mutableStateListOf("shares", "averageCost", "account") }
    var selectedRowIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var fileReadJob by remember { mutableStateOf<Job?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileReadJob?.cancel()
            csvResult = null
            selectedRowIndexes = emptySet()
            complete = false
            isReading = true
            error = null; selectedFile = context.getString(R.string.csv_import_message)
            fileReadJob = scope.launch {
                try { runCatchingCancellable {
                    selectedFile = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } ?: uri.lastPathSegment ?: "CSV"
                    withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(10 * 1024 * 1024) } ?: error(resources.getString(R.string.csv_import_label_6))
                    val catalog = LocalSecurityCatalog.names(context)
                    val funds = LocalSecurityCatalog.fundNames(context)
                    val detected = BrokerCsvImporter.detectBroker(bytes, selectedFile)
                    broker = detected
                    brokerWasAutoDetected = detected != null
                    BrokerCsvImporter.parse(bytes, detected ?: CsvBroker.SBI, catalog, funds)
                    }
                }.onSuccess { parsed -> csvResult = parsed; selectedRowIndexes = parsed.rows.indices.toSet() }.onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_8) }
                } finally { isReading = false }
            }
        }
    }
    val dvmFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            fileReadJob?.cancel()
            isReading = true
            isImporting = false
            complete = false
            error = null
            dvmFiles = emptyList()
            fileReadJob = scope.launch {
                try { runCatchingCancellable {
                    withContext(Dispatchers.IO) { scanDvmBackupFolder(context, uri) }
                }.onSuccess { result ->
                    val (previews, names, skipped) = result
                    if (previews.isEmpty()) {
                        error = resources.getString(R.string.csv_dvm_folder_empty)
                    } else {
                        dvmFiles = previews
                        dvmFileNames = names
                        isImporting = true
                        runCatchingCancellable { onImportDvm(previews) }
                            .onSuccess { complete = true }
                            .onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_17) }
                        if (skipped > 0 && complete) error = resources.getString(R.string.csv_dvm_folder_skipped, skipped)
                    }
                }.onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_15) }
                } finally { isReading = false }
                isImporting = false
            }
        }
    }

    Scaffold(topBar = {
        AdaptiveTopAppBar(title = { Text(if (mode == "csv") stringResource(R.string.csv_import_label) else stringResource(R.string.csv_import_label_2), color = OnSurface, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = OnSurface) } },
            containerColor = BackgroundDark)
    }, containerColor = BackgroundDark) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                AdaptiveRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainer).padding(4.dp)) {
                    listOf("csv" to stringResource(R.string.csv_import_label_3), "dvm" to stringResource(R.string.csv_import_label_4)).forEach { (id, label) ->
                        Text(label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (mode == id) PrimaryEmerald else Color.Transparent).clickable(enabled = !isReading && !isImporting) { mode = id; error = null }.padding(11.dp), color = if (mode == id) OnPrimary else OnSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (mode == "csv") {
                item {
                    Text(
                        text = when {
                            brokerWasAutoDetected -> resources.getString(R.string.csv_broker_auto_detected, broker?.label.orEmpty())
                            broker == null -> stringResource(R.string.csv_broker_auto_unknown)
                            else -> resources.getString(R.string.csv_broker_manual_selection, broker?.label.orEmpty())
                        },
                        color = if (brokerWasAutoDetected) PrimaryEmerald else OnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.csv_import_text), color = OnSurface, fontWeight = FontWeight.Bold)
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CsvBroker.entries.forEach { item ->
                            Text(item.label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (broker == item) SurfaceContainerHighest else SurfaceContainer).clickable(enabled = !isReading && !isImporting) { broker = item; brokerWasAutoDetected = false; csvResult = csvResult?.copy(broker = item); complete = false }.padding(vertical = 11.dp, horizontal = 4.dp), color = if (broker == item) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    ImportCard(onClick = { if (!isReading && !isImporting) csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, title = if (selectedFile.isBlank()) resources.getString(R.string.csv_select_file) else selectedFile, subtitle = resources.getString(R.string.csv_import_label_16), icon = Icons.Default.UploadFile)
                }
                    csvResult?.let { result ->
                    item {
                        Text(stringResource(R.string.csv_import_text_2 , result.encoding.name, result.rows.size), color = PrimaryEmerald, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.csv_import_text_3), color = OnSurface, fontWeight = FontWeight.Bold)
                        listOf("shares" to stringResource(R.string.csv_import_label_5), "averageCost" to stringResource(R.string.add_holding_text_15), "account" to stringResource(R.string.csv_import_label_7), "currentPrice" to stringResource(R.string.asset_management_text_23)).forEach { (key, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = key in selectedFields, onCheckedChange = { checked -> if (checked) selectedFields.add(key) else selectedFields.remove(key) })
                                Text(label, color = OnSurface)
                            }
                        }
                        if (result.errors.isNotEmpty()) Text(result.errors.joinToString(), color = ErrorRed)
                    }
                    itemsIndexed(result.rows) { index, row ->
                        val checked = index in selectedRowIndexes
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
                            AdaptiveRow(Modifier.fillMaxWidth().clickable { selectedRowIndexes = if (checked) selectedRowIndexes - index else selectedRowIndexes + index }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { value -> selectedRowIndexes = if (value) selectedRowIndexes + index else selectedRowIndexes - index })
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.csv_import_1_s_2_s , if (row.isFund) resources.getString(R.string.csv_import_label_13) else row.code, row.name), color = OnSurface, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.csv_import_text_4 , row.shares ?: 0, row.averageCost ?: 0.0) + (row.account?.let { resources.getString(R.string.csv_import_label_14, (it).toString()) } ?: ""), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                                }
                                if (row.matchedName != null) Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald)
                            }
                        }
                    }
                    if (result.unmatchedFunds.isNotEmpty()) item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.csv_import_text_5 , result.unmatchedFunds.size), color = ErrorRed, fontWeight = FontWeight.Bold)
                                result.unmatchedFunds.forEach { Text(stringResource(R.string.csv_import_text_6 , it.sourceName), color = OnSurfaceVariant, fontSize = AppTextSize.caption) }
                            }
                        }
                    }
                    item {
                        Button(onClick = {
                            val chosen = result.rows.filterIndexed { index, _ -> index in selectedRowIndexes }
                            if (chosen.isEmpty()) error = context.getString(R.string.csv_import_message_2) else {
                                val fields = selectedFields.toSet()
                                isImporting = true
                                scope.launch {
                                    try { runCatchingCancellable { onImportCsv(chosen, fields) }.onSuccess { complete = true }
                                        .onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_17) }
                                    } finally { isImporting = false }
                                }
                            }
                        }, enabled = !isReading && !isImporting && !complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text(if (complete) stringResource(R.string.csv_import_label_9) else stringResource(R.string.csv_import_label_10), color = OnPrimary, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                item { Text(stringResource(R.string.csv_import_text_7), color = OnSurfaceVariant) }
                item { ImportCard(onClick = { if (!isReading && !isImporting) dvmFolderPicker.launch(null) }, title = resources.getString(R.string.csv_dvm_folder_title), subtitle = resources.getString(R.string.csv_dvm_folder_subtitle), icon = Icons.Default.FolderOpen) }
                items(dvmFiles, key = { it.kind.name }) { backup ->
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.csv_import_text_8 , backup.kind.label, backup.itemCount), color = OnSurface, fontWeight = FontWeight.Bold)
                            Text(dvmFileNames[backup.kind.name].orEmpty(), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            if (backup.kind == DvmBackupKind.STOCKS) backup.stocks.take(30).forEach { stock ->
                                Text(stringResource(R.string.csv_import_text_9 , stock.code, stock.name, stock.shares.toInt()), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                            }
                        }
                    }
                }
                if (complete) item { Text(stringResource(R.string.csv_import_label_11), color = PrimaryEmerald, fontWeight = FontWeight.Bold) }
            }
            error?.let { item { Text(it, color = ErrorRed, fontSize = AppTextSize.caption) } }
            item { Text(stringResource(R.string.csv_import_text_10), color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
        }
    }
}

@Composable
fun DvmBackupFolderImportCard(onImportDvm: suspend (List<DvmBackupPreview>) -> Unit) {
    val context = LocalContext.current
    val resources = context.resources
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            isWorking = true
            status = null
            error = null
            scope.launch {
                try {
                    val (backups, _, skipped) = withContext(Dispatchers.IO) { scanDvmBackupFolder(context, uri) }
                    if (backups.isEmpty()) error = resources.getString(R.string.csv_dvm_folder_empty)
                    else {
                        runCatchingCancellable { onImportDvm(backups) }
                            .onSuccess {
                                status = resources.getString(R.string.csv_dvm_folder_imported, backups.size)
                                if (skipped > 0) error = resources.getString(R.string.csv_dvm_folder_skipped, skipped)
                            }
                            .onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_17) }
                    }
                } catch (failure: Throwable) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    error = failure.message ?: resources.getString(R.string.csv_import_label_15)
                } finally { isWorking = false }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportCard(
            onClick = { if (!isWorking) picker.launch(null) },
            title = resources.getString(R.string.csv_dvm_folder_title),
            subtitle = resources.getString(R.string.csv_dvm_folder_subtitle),
            icon = Icons.Default.FolderOpen
        )
        if (isWorking) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryEmerald)
        status?.let { Text(it, color = PrimaryEmerald, fontWeight = FontWeight.Bold) }
        error?.let { Text(it, color = ErrorRed, fontSize = AppTextSize.caption) }
    }
}

private fun scanDvmBackupFolder(
    context: android.content.Context,
    treeUri: android.net.Uri
): Triple<List<DvmBackupPreview>, Map<String, String>, Int> {
    runCatching {
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val resolver = context.contentResolver
    val previews = linkedMapOf<DvmBackupKind, DvmBackupPreview>()
    val names = linkedMapOf<String, String>()
    var skipped = 0
    var visited = 0

    fun visit(documentId: String, depth: Int) {
        if (depth > 12 || visited >= 3000) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val children = mutableListOf<Triple<String, String, String>>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext() && visited < 3000) {
                visited++
                children += Triple(cursor.getString(idIndex), cursor.getString(nameIndex).orEmpty(), cursor.getString(mimeIndex).orEmpty())
            }
        }
        children.forEach { (id, name, mime) ->
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                visit(id, depth + 1)
            } else if (name.endsWith(".dvm", ignoreCase = true)) {
                val kind = DvmBackupKind.fromName(name)
                if (kind == null || previews.containsKey(kind)) {
                    skipped++
                } else {
                    runCatching {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        val bytes = resolver.openInputStream(fileUri)?.use { it.readBoundedBytes(50 * 1024 * 1024) }
                            ?: error("DVMファイルを読み込めません")
                        DvmBackupImporter.parse(name, bytes)
                    }.onSuccess { preview ->
                        previews[kind] = preview
                        names[kind.name] = name
                    }.onFailure { skipped++ }
                }
            }
        }
    }

    visit(DocumentsContract.getTreeDocumentId(treeUri), 0)
    return Triple(previews.values.toList(), names, skipped)
}

@Composable
fun BrokerCsvImportCard(onImportCsv: suspend (List<CsvHoldingRow>, Set<String>) -> Unit) {
    val context = LocalContext.current
    val resources = context.resources
    val scope = rememberCoroutineScope()
    var broker by remember { mutableStateOf<CsvBroker?>(null) }
    var brokerWasAutoDetected by remember { mutableStateOf(false) }
    var csvResult by remember { mutableStateOf<CsvImportResult?>(null) }
    var selectedFile by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    var selectedRowIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val selectedFields = remember { mutableStateListOf("shares", "averageCost", "account") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isReading = true
            csvResult = null
            selectedRowIndexes = emptySet()
            complete = false
            error = null
            scope.launch {
                try {
                    runCatchingCancellable {
                        selectedFile = withContext(Dispatchers.IO) {
                            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        } ?: uri.lastPathSegment ?: "CSV"
                        withContext(Dispatchers.IO) {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(10 * 1024 * 1024) }
                                ?: error(resources.getString(R.string.csv_import_label_6))
                            val detected = BrokerCsvImporter.detectBroker(bytes, selectedFile)
                            broker = detected
                            brokerWasAutoDetected = detected != null
                            BrokerCsvImporter.parse(bytes, detected ?: CsvBroker.SBI, LocalSecurityCatalog.names(context), LocalSecurityCatalog.fundNames(context))
                        }
                    }.onSuccess { result ->
                        csvResult = result
                        selectedRowIndexes = result.rows.indices.toSet()
                    }.onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_8) }
                } finally { isReading = false }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = when {
                brokerWasAutoDetected -> resources.getString(R.string.csv_broker_auto_detected, broker?.label.orEmpty())
                broker == null -> stringResource(R.string.csv_broker_auto_unknown)
                else -> resources.getString(R.string.csv_broker_manual_selection, broker?.label.orEmpty())
            },
            color = if (brokerWasAutoDetected) PrimaryEmerald else OnSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Text(stringResource(R.string.csv_import_text), color = OnSurface, fontWeight = FontWeight.Bold)
        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CsvBroker.entries.forEach { item ->
                Text(item.label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (broker == item) SurfaceContainerHighest else SurfaceContainer)
                    .clickable(enabled = !isReading && !isImporting) { broker = item; brokerWasAutoDetected = false; csvResult = csvResult?.copy(broker = item); complete = false; error = null }
                    .padding(vertical = 11.dp, horizontal = 4.dp),
                    color = if (broker == item) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
            }
        }
        ImportCard(
            onClick = { if (!isReading && !isImporting) picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
            title = selectedFile.ifBlank { resources.getString(R.string.csv_select_file) },
            subtitle = resources.getString(R.string.csv_import_label_16), icon = Icons.Default.UploadFile
        )
        if (isReading || isImporting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryEmerald)
        csvResult?.let { result ->
            Text(stringResource(R.string.csv_import_text_2, result.encoding.name, result.rows.size), color = PrimaryEmerald, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.csv_import_text_3), color = OnSurface, fontWeight = FontWeight.Bold)
            listOf("shares" to stringResource(R.string.csv_import_label_5), "averageCost" to stringResource(R.string.add_holding_text_15),
                "account" to stringResource(R.string.csv_import_label_7), "currentPrice" to stringResource(R.string.asset_management_text_23)).forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = key in selectedFields, onCheckedChange = { checked -> if (checked) selectedFields.add(key) else selectedFields.remove(key) })
                    Text(label, color = OnSurface)
                }
            }
            result.errors.take(5).forEach { Text(it, color = ErrorRed, fontSize = AppTextSize.caption) }
            result.rows.forEachIndexed { index, row ->
                val checked = index in selectedRowIndexes
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), shape = RoundedCornerShape(12.dp)) {
                    AdaptiveRow(Modifier.fillMaxWidth().clickable { selectedRowIndexes = if (checked) selectedRowIndexes - index else selectedRowIndexes + index }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { value -> selectedRowIndexes = if (value) selectedRowIndexes + index else selectedRowIndexes - index })
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.csv_import_1_s_2_s, if (row.isFund) resources.getString(R.string.csv_import_label_13) else row.code, row.name), color = OnSurface, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.csv_import_text_4, row.shares ?: 0, row.averageCost ?: 0.0), color = OnSurfaceVariant, fontSize = AppTextSize.dense)
                        }
                        if (row.matchedName != null) Icon(Icons.Default.CheckCircle, null, tint = PrimaryEmerald)
                    }
                }
            }
            if (result.unmatchedFunds.isNotEmpty()) Text(stringResource(R.string.csv_import_text_5, result.unmatchedFunds.size), color = ErrorRed, fontWeight = FontWeight.Bold)
            Button(onClick = {
                val chosen = result.rows.filterIndexed { index, _ -> index in selectedRowIndexes }
                if (chosen.isEmpty()) error = context.getString(R.string.csv_import_message_2) else {
                    isImporting = true
                    scope.launch {
                        try { runCatchingCancellable { onImportCsv(chosen, selectedFields.toSet()) }
                            .onSuccess { complete = true }.onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_17) }
                        } finally { isImporting = false }
                    }
                }
            }, enabled = broker != null && !isReading && !isImporting && !complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) {
                Text(if (complete) stringResource(R.string.csv_import_label_9) else stringResource(R.string.csv_import_label_10), color = OnPrimary, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(it, color = ErrorRed, fontSize = AppTextSize.caption) }
    }
}

@Composable
private fun ImportCard(onClick: () -> Unit, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = PrimaryEmerald, modifier = Modifier.size(32.dp))
            Column { Text(title, color = OnSurface, fontWeight = FontWeight.Bold); Text(subtitle, color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
        }
    }
}
