package com.kurogane.dividend.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Keeps the ordinary row, and stacks its existing children when text is enlarged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    stackAtFontScale: Float = 1.8f,
    content: @Composable RowScope.() -> Unit
) {
    if (LocalDensity.current.fontScale >= stackAtFontScale) {
        FlowRow(modifier, horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 1) { content() }
    } else Row(modifier, horizontalArrangement, verticalAlignment, content)
}
