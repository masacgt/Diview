package com.kurogane.dividend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.R
import com.kurogane.dividend.notifications.NotificationHistoryEntry
import com.kurogane.dividend.notifications.NotificationKind
import com.kurogane.dividend.notifications.NotificationStore
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.theme.BorderColor
import com.kurogane.dividend.ui.theme.OnPrimary
import com.kurogane.dividend.ui.theme.OnSurface
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.PrimaryEmeraldContainer
import com.kurogane.dividend.ui.theme.SurfaceContainer
import com.kurogane.dividend.ui.theme.SurfaceContainerHigh
import com.kurogane.dividend.ui.theme.SurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
fun NotificationHistorySheet(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onUnreadCountChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { NotificationStore(context) }
    var history by remember { mutableStateOf(store.notificationHistory()) }
    var selectedKind by remember { mutableStateOf<NotificationKind?>(null) }
    val unreadCount = history.count { !it.isRead }
    val filteredHistory = history.filter { selectedKind == null || it.kind == selectedKind }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer,
        contentColor = OnSurface,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp)
                    .clip(CircleShape).background(SurfaceContainerHigh)
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Notifications, null, tint = PrimaryEmerald, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.notification_history_title),
                        color = OnSurface,
                        fontSize = AppTextSize.headline,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.notification_history_subtitle),
                        color = OnSurfaceVariant,
                        fontSize = AppTextSize.dense
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = OnSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    enabled = unreadCount > 0,
                    onClick = {
                        store.markAllNotificationsRead()
                        history = store.notificationHistory()
                        onUnreadCountChanged(history.count { !it.isRead })
                    }
                ) {
                    Text(stringResource(R.string.notification_history_mark_all_read), color = if (unreadCount > 0) PrimaryEmerald else OnSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryFilterChip(
                    label = stringResource(R.string.notification_history_filter_all, history.size),
                    selected = selectedKind == null,
                    onClick = { selectedKind = null }
                )
                NotificationKind.entries.forEach { kind ->
                    HistoryFilterChip(
                        label = stringResource(kind.historyLabel()),
                        selected = selectedKind == kind,
                        onClick = { selectedKind = kind }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.weight(1f, fill = true)) {
                if (filteredHistory.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.notification_history_empty_title),
                            color = OnSurface,
                            fontSize = AppTextSize.body,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.notification_history_empty_message),
                            color = OnSurfaceVariant,
                            fontSize = AppTextSize.dense
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredHistory, key = { it.id }) { entry ->
                            NotificationHistoryCard(entry = entry, onClick = {
                                if (!entry.isRead) {
                                    store.markNotificationRead(entry.id)
                                    history = store.notificationHistory()
                                    onUnreadCountChanged(history.count { !it.isRead })
                                }
                            })
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.notification_history_open_settings), color = OnSurfaceVariant)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = OnSurface)
                ) {
                    Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = AppTextSize.dense, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryEmeraldContainer,
            selectedLabelColor = OnPrimary,
            containerColor = SurfaceContainerHigh,
            labelColor = OnSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = BorderColor,
            selectedBorderColor = PrimaryEmeraldContainer
        )
    )
}

@Composable
private fun NotificationHistoryCard(entry: NotificationHistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, if (entry.isRead) BorderColor else OnSurfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape)
                .background(if (entry.isRead) OnSurfaceVariant.copy(alpha = 0.35f) else PrimaryEmerald)
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(entry.kind.historyLabel()),
                    modifier = Modifier.weight(1f),
                    color = if (entry.isRead) OnSurfaceVariant else PrimaryEmerald,
                    fontSize = AppTextSize.dense,
                    fontWeight = FontWeight.Bold
                )
                Text(relativeNotificationTime(entry.createdAtMillis), color = OnSurfaceVariant, fontSize = AppTextSize.caption)
            }
            Text(entry.title, color = OnSurface, fontSize = AppTextSize.body, fontWeight = FontWeight.Bold)
            if (entry.message.isNotBlank()) {
                Text(entry.message, color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 19.sp)
            }
        }
    }
}

private fun NotificationKind.historyLabel(): Int = when (this) {
    NotificationKind.PRICE -> R.string.notification_history_kind_price
    NotificationKind.DIVIDEND -> R.string.notification_history_kind_dividend_month
    NotificationKind.DIVIDEND_CHANGE -> R.string.notification_history_kind_dividend_change
}

private fun relativeNotificationTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed in 0..59_999 -> "たった今"
        elapsed in 60_000..3_599_999 -> "${elapsed / 60_000}分前"
        elapsed in 3_600_000..86_399_999 -> "${elapsed / 3_600_000}時間前"
        elapsed in 86_400_000..172_799_999 -> "昨日 ${SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(timestamp))}"
        else -> SimpleDateFormat("M月d日 HH:mm", Locale.JAPAN).format(Date(timestamp))
    }
}
