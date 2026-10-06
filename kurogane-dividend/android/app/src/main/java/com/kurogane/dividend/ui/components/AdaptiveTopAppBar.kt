package com.kurogane.dividend.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Uses the existing app bar at normal size and allows its title to wrap at large sizes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    if (LocalDensity.current.fontScale < 1.4f) {
        TopAppBar(title, modifier, navigationIcon,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor))
    } else {
        Row(modifier.fillMaxWidth().background(containerColor)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            navigationIcon()
            Box(Modifier.weight(1f).padding(end = 12.dp)) { title() }
        }
    }
}
