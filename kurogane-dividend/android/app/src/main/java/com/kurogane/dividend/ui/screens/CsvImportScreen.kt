package com.kurogane.dividend.ui.screens

import com.kurogane.dividend.ui.components.AdaptiveRow
import com.kurogane.dividend.ui.components.AdaptiveTopAppBar
import androidx.compose.ui.res.stringResource
import com.kurogane.dividend.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

private fun reportImportFailure(error: Throwable, fallback: String): String {
    val message = com.kurogane.dividend.ui.importErrorMessage(error, fallback)
    // Provider messages and parser input can contain private file names/content.
    android.util.Log.w("DiviewImport", "Import failed (${error.javaClass.simpleName}): $fallback")
    return message
}

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
    var csvBytes by remember { mutableStateOf<ByteArray?>(null) }
    var csvResult by remember { mutableStateOf<CsvImportResult?>(null) }
    var selectedFile by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var complete by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("csv") }
    val selectedFields = remember { mutableStateListOf("shares", "averageCost", "account") }
    var selectedRowIndexes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var fileReadJob by remember { mutableStateOf<Job?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var guideExpanded by remember { mutableStateOf(true) }
    val useStackedModeSelector = LocalDensity.current.fontScale >= 1.4f

    fun reparseCsvAs(selectedBroker: CsvBroker, bytes: ByteArray) {
        fileReadJob?.cancel()
        broker = selectedBroker
        brokerWasAutoDetected = false
        csvResult = null
        selectedRowIndexes = emptySet()
        complete = false
        isReading = true
        error = null
        fileReadJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BrokerCsvImporter.parse(bytes, selectedBroker, LocalSecurityCatalog.names(context), LocalSecurityCatalog.fundNames(context))
                }
                csvResult = result
                selectedRowIndexes = result.rows.indices.toSet()
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: resources.getString(R.string.csv_import_label_8)
            } finally {
                isReading = false
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val preselectedBroker = broker?.takeUnless { brokerWasAutoDetected }
            fileReadJob?.cancel()
            csvResult = null
            csvBytes = null
            broker = null
            brokerWasAutoDetected = false
            selectedRowIndexes = emptySet()
            complete = false
            isReading = true
            error = null; selectedFile = context.getString(R.string.csv_import_message)
            fileReadJob = scope.launch {
                try { runCatchingCancellable {
                    selectedFile = withContext(Dispatchers.IO) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } ?: uri.lastPathSegment ?: "CSV"
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(10 * 1024 * 1024) }
                            ?: error(resources.getString(R.string.csv_import_label_6))
                    }
                    csvBytes = bytes
                    val detected = withContext(Dispatchers.IO) { BrokerCsvImporter.detectBroker(bytes, selectedFile) }
                    val selectedBroker = detected ?: preselectedBroker
                    broker = selectedBroker
                    brokerWasAutoDetected = detected != null
                    if (selectedBroker == null) null
                    else withContext(Dispatchers.IO) {
                        BrokerCsvImporter.parse(bytes, selectedBroker, LocalSecurityCatalog.names(context), LocalSecurityCatalog.fundNames(context))
                    }
                }.onSuccess { parsed ->
                    csvResult = parsed
                    selectedRowIndexes = parsed?.rows?.indices?.toSet().orEmpty()
                }.onFailure { error = it.message ?: resources.getString(R.string.csv_import_label_8) }
                } finally { isReading = false }
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
                val options = listOf("csv" to stringResource(R.string.csv_import_label_3), "drive" to stringResource(R.string.csv_import_label_4))
                val selectorModifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainer).padding(4.dp)
                if (useStackedModeSelector) {
                    Column(selectorModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        options.forEach { (id, label) ->
                            Text(label, Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(if (mode == id) PrimaryEmerald else Color.Transparent).clickable(enabled = !isReading && !isImporting) { mode = id; error = null }.padding(11.dp), color = if (mode == id) OnPrimary else OnSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(selectorModifier) {
                        options.forEach { (id, label) ->
                            Text(label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (mode == id) PrimaryEmerald else Color.Transparent).clickable(enabled = !isReading && !isImporting) { mode = id; error = null }.padding(11.dp), color = if (mode == id) OnPrimary else OnSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
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
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CsvBroker.entries.filter { it != CsvBroker.DIVIEW }.forEach { item ->
                            Text(item.label, Modifier.clip(RoundedCornerShape(9.dp)).background(if (broker == item) SurfaceContainerHighest else SurfaceContainer).clickable(enabled = !isReading && !isImporting) {
                                broker = item
                                brokerWasAutoDetected = false
                                csvBytes?.let { reparseCsvAs(item, it) }
                            }.padding(vertical = 11.dp, horizontal = 10.dp), color = if (broker == item) PrimaryEmerald else OnSurface, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    ImportCard(onClick = { if (!isReading && !isImporting) csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, title = if (selectedFile.isBlank()) resources.getString(R.string.csv_select_file) else selectedFile, subtitle = resources.getString(R.string.csv_import_label_16), icon = Icons.Default.UploadFile)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainer), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { guideExpanded = !guideExpanded }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, null, tint = PrimaryEmerald)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.csv_guide_title), Modifier.weight(1f), color = OnSurface, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ExpandMore, null, tint = OnSurfaceVariant)
                            }
                            if (guideExpanded) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CsvBroker.entries.filter { it != CsvBroker.DIVIEW }.forEach { item ->
                                        Text(item.label, Modifier.clip(RoundedCornerShape(50)).background(if (broker == item) PrimaryEmerald else SurfaceContainerHighest).clickable(enabled = !isReading && !isImporting) {
                                            csvBytes?.let { reparseCsvAs(item, it) } ?: run { broker = item; brokerWasAutoDetected = false }
                                        }.padding(horizontal = 10.dp, vertical = 7.dp), color = if (broker == item) OnPrimary else OnSurfaceVariant, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh), shape = RoundedCornerShape(10.dp)) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Default.Lightbulb, null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.csv_guide_notice), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
                                    }
                                }
                                Text(stringResource(when (broker ?: CsvBroker.SBI) {
                                    CsvBroker.SBI -> R.string.csv_guide_sbi
                                    CsvBroker.RAKUTEN -> R.string.csv_guide_rakuten
                                    CsvBroker.MONEX -> R.string.csv_guide_monex
                                    CsvBroker.MATSUI -> R.string.csv_guide_matsui
                                    CsvBroker.MITSUBISHI_UFJ -> R.string.csv_guide_mitsubishi
                                    CsvBroker.NOMURA -> R.string.csv_guide_nomura
                                    CsvBroker.DIVIEW -> R.string.csv_guide_diview
                                }), color = OnSurface, fontSize = AppTextSize.caption, lineHeight = 18.sp)
                                Text(stringResource(R.string.csv_guide_steps), color = PrimaryEmerald, fontSize = AppTextSize.caption, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
                                        .onFailure { error = reportImportFailure(it, resources.getString(R.string.csv_import_label_17)) }
                                    } finally { isImporting = false }
                                }
                            }
                        }, enabled = !isReading && !isImporting && !complete, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryEmerald)) { Text(if (complete) stringResource(R.string.csv_import_label_9) else stringResource(R.string.csv_import_label_10), color = OnPrimary, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                item { GoogleDvmBackupCard(action = GoogleDvmBackupAction.IMPORT, onImportDvm = onImportDvm) }
            }
            error?.let { item { Text(it, color = ErrorRed, fontSize = AppTextSize.caption) } }
            if (mode == "csv") item { Text(stringResource(R.string.csv_import_text_10), color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
        }
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
