package com.kurogane.dividend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurogane.dividend.ui.theme.AppTextSize
import com.kurogane.dividend.ui.theme.OnSurfaceVariant

/** Inline status used by search and detail. State and retry work belong to callers. */
@Composable
fun StatusMessage(message: String, modifier: Modifier = Modifier, color: Color = OnSurfaceVariant,
                  actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = color, fontSize = AppTextSize.caption)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
