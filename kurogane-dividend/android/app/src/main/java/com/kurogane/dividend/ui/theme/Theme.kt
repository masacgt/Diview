package com.kurogane.dividend.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun KuroganeDividendTheme(
    themeMode: String = "system",
    isOledBlack: Boolean = false,
    selectedAccent: String = "emerald",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light" -> false
        "system" -> systemDark
        else -> true
    }
    val palette = remember(darkTheme, isOledBlack, selectedAccent) {
        createPalette(darkTheme, isOledBlack, selectedAccent)
    }
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            background = palette.background,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            secondary = palette.secondary,
            error = palette.error
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            background = palette.background,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            secondary = palette.secondary,
            error = palette.error
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
    }
}

private fun createPalette(dark: Boolean, oled: Boolean, accent: String): AppColorPalette {
    val accentSet = when (accent) {
        "cyan" -> AccentSet(Color(0xFF67E8F9), Color(0xFF0891B2), Color(0xFF0E7490))
        "blue" -> AccentSet(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1D4ED8))
        "amber" -> AccentSet(Color(0xFFFCD34D), Color(0xFFD97706), Color(0xFFB45309))
        "purple" -> AccentSet(Color(0xFFC4B5FD), Color(0xFF7C3AED), Color(0xFF6D28D9))
        else -> AccentSet(Color(0xFF4EDEA3), Color(0xFF10B981), Color(0xFF047857))
    }
    val base = if (dark) DarkAppPalette else LightAppPalette
    return base.copy(
        background = if (dark && oled) Color.Black else base.background,
        surfaceLowest = if (dark && oled) Color.Black else base.surfaceLowest,
        surfaceLow = if (dark && oled) Color(0xFF05080F) else base.surfaceLow,
        surface = if (dark && oled) Color(0xFF090D16) else base.surface,
        surfaceHigh = if (dark && oled) Color(0xFF101722) else base.surfaceHigh,
        surfaceHighest = if (dark && oled) Color(0xFF1A2431) else base.surfaceHighest,
        primary = if (dark) accentSet.darkPrimary else accentSet.lightPrimary,
        primaryContainer = if (dark) accentSet.darkContainer else accentSet.lightPrimary,
        onPrimary = contentColorFor(primary = if (dark) accentSet.darkPrimary else accentSet.lightPrimary),
        onPrimaryContainer = contentColorFor(primary = if (dark) accentSet.darkContainer else accentSet.lightPrimary)
    )
}

private fun contentColorFor(primary: Color): Color {
    val luminance = primary.luminance()
    val blackContrast = (luminance + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (luminance + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private data class AccentSet(
    val darkPrimary: Color,
    val darkContainer: Color,
    val lightPrimary: Color
)
