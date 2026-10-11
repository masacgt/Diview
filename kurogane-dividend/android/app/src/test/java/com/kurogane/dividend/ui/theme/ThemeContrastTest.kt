package com.kurogane.dividend.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun smallTextRemainsReadableAcrossBothSurfacePalettes() {
        for (palette in listOf(DarkAppPalette, LightAppPalette)) {
            val backgrounds = listOf(palette.background, palette.surfaceLowest, palette.surfaceLow,
                palette.surface, palette.surfaceHigh, palette.surfaceHighest)
            for (text in listOf(palette.onSurface, palette.onSurfaceVariant, palette.textMuted)) {
                for (background in backgrounds) {
                    assertTrue("Text $text on $background must have contrast >= 4.5", contrast(text, background) >= 4.5f)
                }
            }
        }
    }
    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance(); val b = second.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
}
