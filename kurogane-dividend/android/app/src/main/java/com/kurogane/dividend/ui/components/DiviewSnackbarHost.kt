package com.kurogane.dividend.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.theme.OnPrimary
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald

enum class DiviewSnackbarTone {
    Success,
    Error,
    Info
}

private data class DiviewSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val tone: DiviewSnackbarTone
) : SnackbarVisuals

suspend fun SnackbarHostState.showDiviewSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short,
    tone: DiviewSnackbarTone = DiviewSnackbarTone.Info
): SnackbarResult = showSnackbar(
    DiviewSnackbarVisuals(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = withDismissAction,
        duration = duration,
        tone = tone
    )
)

@Composable
fun DiviewSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState, modifier = Modifier.navigationBarsPadding()) { data ->
        DiviewSnackbar(data)
    }
}

@Composable
private fun DiviewSnackbar(data: SnackbarData) {
    val tone = (data.visuals as? DiviewSnackbarVisuals)?.tone ?: DiviewSnackbarTone.Info
    val (icon, iconTint) = when (tone) {
        DiviewSnackbarTone.Success -> Icons.Default.CheckCircle to PrimaryEmerald
        DiviewSnackbarTone.Error -> Icons.Default.Error to androidx.compose.material3.MaterialTheme.colorScheme.error
        DiviewSnackbarTone.Info -> Icons.Default.Info to OnSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = PrimaryEmerald,
        contentColor = OnPrimary,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 10.dp).size(20.dp),
                tint = iconTint
            )
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f),
                color = OnPrimary,
                fontSize = AppTextSize.body
            )
            data.visuals.actionLabel?.let { actionLabel ->
                TextButton(onClick = data::performAction) {
                    Text(actionLabel, color = OnPrimary)
                }
            }
            if (data.visuals.withDismissAction) {
                IconButton(onClick = data::dismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "通知を閉じる",
                        modifier = Modifier.size(18.dp),
                        tint = OnPrimary
                    )
                }
            }
        }
    }
}
