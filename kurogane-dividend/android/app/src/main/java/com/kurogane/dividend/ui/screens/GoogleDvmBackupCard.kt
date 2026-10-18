package com.kurogane.dividend.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.data.DividendHistoryOverride
import com.kurogane.dividend.ui.theme.OnSurface
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.SurfaceContainerLow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GoogleDvmBackupAction { EXPORT, IMPORT }

@Composable
fun GoogleDvmBackupCard(
    action: GoogleDvmBackupAction,
    holdings: List<Holding> = emptyList(),
    shareholderBenefits: Map<String, List<ShareholderBenefit>> = emptyMap(),
    stockClimateOverrides: Map<String, StockClimate> = emptyMap(),
    dividendHistoryOverrides: Map<String, List<DividendHistoryOverride>> = emptyMap(),
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

    val authorizedAction: suspend (String) -> String = { accessToken ->
        when (action) {
            GoogleDvmBackupAction.EXPORT -> {
                val encrypted = withContext(Dispatchers.IO) { DvmBackupExporter.export(holdings) }
                val encryptedExtras = withContext(Dispatchers.IO) {
                    DvmBackupExporter.exportExtras(shareholderBenefits, stockClimateOverrides, dividendHistoryOverrides)
                }
                withContext(Dispatchers.IO) {
                    GoogleDriveBackupClient.upload(accessToken, DvmBackupKind.STOCKS.fileName, encrypted)
                    GoogleDriveBackupClient.upload(accessToken, DvmBackupKind.DIVIEW_EXTRAS.fileName, encryptedExtras)
                    LocalImportStore(context).saveBackup(DvmBackupKind.STOCKS, DvmBackupImporter.decrypt(encrypted))
                    LocalImportStore(context).saveBackup(DvmBackupKind.DIVIEW_EXTRAS, DvmBackupImporter.decrypt(encryptedExtras))
                }
                resources.getString(R.string.dvm_drive_backup_success)
            }
            GoogleDvmBackupAction.IMPORT -> {
                val backups = withContext(Dispatchers.IO) {
                    listOfNotNull(
                        GoogleDriveBackupClient.download(accessToken, DvmBackupKind.STOCKS.fileName)
                            ?.let { DvmBackupImporter.parse(DvmBackupKind.STOCKS.fileName, it) },
                        GoogleDriveBackupClient.download(accessToken, DvmBackupKind.DIVIEW_EXTRAS.fileName)
                            ?.let { DvmBackupImporter.parse(DvmBackupKind.DIVIEW_EXTRAS.fileName, it) }
                    ).also { if (it.isEmpty()) throw IllegalStateException(resources.getString(R.string.dvm_drive_backup_missing)) }
                }
                onImportDvm(backups)
                resources.getString(R.string.dvm_drive_restore_success, backups.firstOrNull { it.kind == DvmBackupKind.STOCKS }?.itemCount ?: 0)
            }
        }
    }
    val latestAction by rememberUpdatedState(authorizedAction)

    fun executeWithToken(accessToken: String) {
        if (accessToken.isBlank()) {
            isWorking = false
            error = resources.getString(R.string.dvm_drive_auth_failed)
            return
        }
        scope.launch {
            try {
                status = latestAction(accessToken)
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = failure.message ?: resources.getString(R.string.dvm_drive_action_failed)
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
                    error = it.message ?: resources.getString(R.string.dvm_drive_auth_failed)
                }
        } else {
            isWorking = false
            error = resources.getString(R.string.dvm_drive_auth_cancelled)
        }
    }

    fun authorize() {
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
                            error = it.message ?: resources.getString(R.string.dvm_drive_auth_failed)
                        }
                    }
                } else {
                    latestExecute(result.accessToken.orEmpty())
                }
            }
            .addOnFailureListener { failure ->
                isWorking = false
                error = failure.message ?: resources.getString(R.string.dvm_drive_auth_failed)
            }
    }

    LaunchedEffect(action) {
        status = null
        error = null
    }

    val title = stringResource(if (action == GoogleDvmBackupAction.EXPORT) R.string.settings_label_13 else R.string.dvm_drive_restore_title)
    val subtitle = stringResource(if (action == GoogleDvmBackupAction.EXPORT) R.string.dvm_drive_backup_subtitle else R.string.dvm_drive_restore_subtitle)
    Card(
        onClick = ::authorize,
        enabled = !isWorking,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (action == GoogleDvmBackupAction.EXPORT) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                    contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(25.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                }
                if (isWorking) CircularProgressIndicator(color = PrimaryEmerald, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            status?.let { Text(it, color = PrimaryEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            error?.let { Text(it, color = OnSurfaceVariant, fontSize = 11.sp) }
        }
    }
}
