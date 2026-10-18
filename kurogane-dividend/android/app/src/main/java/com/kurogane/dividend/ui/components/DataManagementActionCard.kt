package com.kurogane.dividend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurogane.dividend.ui.theme.OnSurface
import com.kurogane.dividend.ui.theme.OnSurfaceVariant
import com.kurogane.dividend.ui.theme.PrimaryEmerald
import com.kurogane.dividend.ui.theme.SurfaceContainerLow

@Composable
fun DataManagementActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isWorking: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val cardColors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
    val shape = RoundedCornerShape(12.dp)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                }
                if (isWorking) CircularProgressIndicator(color = PrimaryEmerald, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            content()
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), colors = cardColors, shape = shape, content = body)
    } else {
        Card(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), colors = cardColors, shape = shape, content = body)
    }
}
