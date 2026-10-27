package com.kurogane.dividend.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.kurogane.dividend.R
import com.kurogane.dividend.data.DvmBackupImporter
import com.kurogane.dividend.data.DvmBackupKind
import com.kurogane.dividend.data.DvmBackupPreview
import com.kurogane.dividend.data.DvmBackupExporter
import com.kurogane.dividend.data.GoogleDriveBackupClient
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.data.readBoundedBytes
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.model.WatchlistStock
import com.kurogane.dividend.data.DividendHistoryOverride
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.components.DataManagementActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GoogleDvmBackupAction { EXPORT, IMPORT }

private class DriveDvmFileUnavailableException : IllegalStateException()

@Composable
fun GoogleDvmBackupCard(
    action: GoogleDvmBackupAction,
    holdings: List<Holding> = emptyList(),
    shareholderBenefits: Map<String, List<ShareholderBenefit>> = emptyMap(),
    stockClimateOverrides: Map<String, StockClimate> = emptyMap(),
    dividendHistoryOverrides: Map<String, List<DividendHistoryOverride>> = emptyMap(),
    watchlist: List<WatchlistStock> = emptyList(),
    yieldExcludedCodes: Set<String> = emptySet(),
    onImportDvm: suspend (List<DvmBackupPreview>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = context.resources
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val latestImport by rememberUpdatedState(onImportDvm)

    val exportAction: suspend (String) -> String = { accessToken ->
        val encrypted = withContext(Dispatchers.IO) { DvmBackupExporter.export(holdings) }
        val encryptedExtras = withContext(Dispatchers.IO) {
            DvmBackupExporter.exportExtras(shareholderBenefits, stockClimateOverrides, dividendHistoryOverrides, watchlist, yieldExcludedCodes)
        }
        withContext(Dispatchers.IO) {
            GoogleDriveBackupClient.upload(accessToken, DvmBackupKind.STOCKS.fileName, encrypted)
            GoogleDriveBackupClient.upload(accessToken, DvmBackupKind.DIVIEW_EXTRAS.fileName, encryptedExtras)
            LocalImportStore(context).saveBackup(DvmBackupKind.STOCKS, DvmBackupImporter.decrypt(encrypted))
            LocalImportStore(context).saveBackup(DvmBackupKind.DIVIEW_EXTRAS, DvmBackupImporter.decrypt(encryptedExtras))
        }
        resources.getString(R.string.dvm_drive_backup_success)
    }
    val latestExportAction by rememberUpdatedState(exportAction)

    suspend fun importDvmUris(uris: List<Uri>) {
        val backups = withContext(Dispatchers.IO) {
            uris.map { uri ->
                val fileName = try {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                } catch (_: Exception) {
                    throw DriveDvmFileUnavailableException()
                }
                    ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?: throw IllegalArgumentException(resources.getString(R.string.dvm_drive_import_failed))
                require(fileName.endsWith(".dvm", ignoreCase = true)) {
                    resources.getString(R.string.dvm_drive_import_failed)
                }
                val input = try {
                    context.contentResolver.openInputStream(uri)
                } catch (_: Exception) {
                    throw DriveDvmFileUnavailableException()
                }
                    ?: throw DriveDvmFileUnavailableException()
                val bytes = try {
                    input.use { it.readBoundedBytes(50 * 1024 * 1024) }
                } catch (_: IOException) {
                    throw DriveDvmFileUnavailableException()
                }
                DvmBackupImporter.parse(fileName, bytes).also { preview ->
                    require(preview.kind == DvmBackupKind.STOCKS || preview.kind == DvmBackupKind.DIVIEW_EXTRAS) {
                        resources.getString(R.string.dvm_drive_import_failed)
                    }
                }
            }
        }
        latestImport(backups)
        val importedCount = backups.filter { it.kind == DvmBackupKind.STOCKS }.sumOf { it.itemCount } +
            backups.sumOf { it.extras.watchlist.orEmpty().size }
        status = resources.getString(R.string.dvm_drive_restore_success, importedCount)
    }

    val dvmFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !isWorking) {
            isWorking = true
            status = null
            error = null
            scope.launch {
                val store = LocalImportStore(context)
                val oldUris = store.loadDriveImportUris().mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                val persistentUris = uris.filter { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.isSuccess
                }
                try {
                    importDvmUris(uris)
                    store.saveDriveImportUris(persistentUris.map(Uri::toString))
                    oldUris.filterNot(persistentUris::contains).forEach { oldUri ->
                        runCatching { context.contentResolver.releasePersistableUriPermission(oldUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    if (persistentUris.isEmpty()) status = resources.getString(R.string.dvm_drive_import_not_remembered)
                } catch (failure: Throwable) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    persistentUris.forEach { uri ->
                        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    error = resources.getString(R.string.dvm_drive_import_failed)
                } finally {
                    isWorking = false
                }
            }
        }
    }

    fun openDvmPicker() = dvmFilePicker.launch(arrayOf("*/*"))

    fun executeWithToken(accessToken: String) {
        if (accessToken.isBlank()) {
            isWorking = false
            error = resources.getString(R.string.dvm_drive_auth_failed)
            return
        }
        scope.launch {
            try {
                status = latestExportAction(accessToken)
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = resources.getString(R.string.dvm_drive_action_failed)
            } finally {
                isWorking = false
            }
        }
    }
    val latestExecute by rememberUpdatedState<(String) -> Unit>({ token -> executeWithToken(token) })
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data!!).accessToken }
                .onSuccess { token -> latestExecute(token.orEmpty()) }
                .onFailure {
                    isWorking = false
                    error = resources.getString(R.string.dvm_drive_auth_failed)
                }
        } else {
            isWorking = false
            error = resources.getString(R.string.dvm_drive_auth_cancelled)
        }
    }

    fun authorizeExport() {
        if (isWorking) return
        isWorking = true
        status = null
        error = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file")))
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        isWorking = false
                        error = resources.getString(R.string.dvm_drive_auth_failed)
                    } else {
                        runCatching {
                            authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        }.onFailure {
                            isWorking = false
                            error = resources.getString(R.string.dvm_drive_auth_failed)
                        }
                    }
                } else {
                    latestExecute(result.accessToken.orEmpty())
                }
            }
            .addOnFailureListener { failure ->
                isWorking = false
                error = resources.getString(R.string.dvm_drive_auth_failed)
            }
    }

    LaunchedEffect(action) {
        status = null
        error = null
    }

    val title = stringResource(if (action == GoogleDvmBackupAction.EXPORT) R.string.settings_label_13 else R.string.dvm_drive_restore_title)
    val subtitle = stringResource(if (action == GoogleDvmBackupAction.EXPORT) R.string.dvm_drive_backup_subtitle else R.string.dvm_drive_restore_subtitle)
    DataManagementActionButton(
        onClick = {
            if (action == GoogleDvmBackupAction.EXPORT) authorizeExport()
            else if (isWorking) Unit else {
                isWorking = true
                status = null
                error = null
                val storedUris = LocalImportStore(context).loadDriveImportUris()
                    .mapNotNull { rawUri -> runCatching { Uri.parse(rawUri) }.getOrNull() }
                val grants = context.contentResolver.persistedUriPermissions
                    .filter { it.isReadPermission }.map { it.uri }.toSet()
                if (storedUris.isEmpty() || storedUris.any { it !in grants }) {
                    isWorking = false
                    openDvmPicker()
                } else {
                    scope.launch {
                        try {
                            importDvmUris(storedUris)
                        } catch (failure: Throwable) {
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            if (failure is DriveDvmFileUnavailableException) {
                                LocalImportStore(context).saveDriveImportUris(emptyList())
                                storedUris.forEach { uri ->
                                    runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                }
                                openDvmPicker()
                            } else {
                                error = resources.getString(R.string.dvm_drive_import_failed)
                            }
                        } finally {
                            isWorking = false
                        }
                    }
                }
            }
        },
        enabled = !isWorking,
        modifier = modifier.fillMaxWidth(),
        title = title,
        subtitle = subtitle,
        icon = if (action == GoogleDvmBackupAction.EXPORT) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
        isWorking = isWorking
    ) {
        status?.let { androidx.compose.material3.Text(it, color = PrimaryEmerald, fontSize = AppTextSize.dense, fontWeight = FontWeight.Bold) }
        error?.let { androidx.compose.material3.Text(it, color = OnSurfaceVariant, fontSize = AppTextSize.dense) }
    }
}
