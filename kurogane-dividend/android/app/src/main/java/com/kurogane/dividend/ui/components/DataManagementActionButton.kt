package com.kurogane.dividend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.theme.OnSurface
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.BorderColor
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.SurfaceContainerLow

@Composable
fun DataManagementActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isWorking: Boolean = false,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceContainerLow,
            contentColor = OnSurface,
            disabledContainerColor = SurfaceContainerLow,
            disabledContentColor = OnSurfaceVariant
        ),
        border = BorderStroke(1.dp, if (enabled) PrimaryEmerald.copy(alpha = 0.7f) else BorderColor),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (enabled) OnSurface else OnSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = AppTextSize.compactBody)
                    Text(subtitle, color = OnSurfaceVariant, fontSize = AppTextSize.dense, lineHeight = 14.sp)
                }
                if (isWorking) CircularProgressIndicator(color = PrimaryEmerald, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = if (enabled) PrimaryEmerald else OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            content()
        }
    }
}
