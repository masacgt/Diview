package com.kurogane.dividend.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.roundToInt

/** Return a lazy list to its beginning in a fixed 200 ms. */
internal suspend fun LazyListState.scrollToTopIn200ms() {
    val initialIndex = firstVisibleItemIndex
    val initialOffset = firstVisibleItemScrollOffset
    if (initialIndex == 0 && initialOffset == 0) return

    animate(1f, 0f, animationSpec = tween(durationMillis = 200)) { remaining, _ ->
        requestScrollToItem(
            (initialIndex * remaining).roundToInt(),
            if (initialIndex == 0) (initialOffset * remaining).roundToInt() else 0
        )
    }
    scrollToItem(0)
}
