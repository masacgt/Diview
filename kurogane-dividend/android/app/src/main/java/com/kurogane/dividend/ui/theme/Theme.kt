package com.kurogane.dividend.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

internal const val APP_FONT_FEATURE_SETTINGS = "\"kern\" 1, \"palt\" 1"
internal const val APP_NUMERIC_FONT_FEATURE_SETTINGS = "\"tnum\" 1, \"kern\" 1, \"palt\" 1"

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
    val accentSet = remember(selectedAccent) { accentSetFor(selectedAccent) }
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            inversePrimary = accentSet.lightPrimary,
            secondary = palette.secondary,
            onSecondary = contentColorFor(palette.secondary),
            secondaryContainer = palette.secondary.copy(alpha = 0.16f).compositeOver(palette.surface),
            onSecondaryContainer = palette.secondary,
            tertiary = palette.info,
            onTertiary = contentColorFor(palette.info),
            tertiaryContainer = palette.info.copy(alpha = 0.16f).compositeOver(palette.surface),
            onTertiaryContainer = palette.info,
            background = palette.background,
            onBackground = palette.onSurface,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            surfaceTint = palette.primary,
            inverseSurface = LightAppPalette.surface,
            inverseOnSurface = LightAppPalette.onSurface,
            error = palette.error,
            onError = contentColorFor(palette.error),
            errorContainer = palette.error.copy(alpha = 0.16f).compositeOver(palette.surface),
            onErrorContainer = palette.error,
            outline = palette.controlOutline,
            outlineVariant = palette.border,
            scrim = Color.Black,
            surfaceBright = palette.surfaceHighest,
            surfaceDim = palette.surfaceLowest,
            surfaceContainerLowest = palette.surfaceLowest,
            surfaceContainerLow = palette.surfaceLow,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceHigh,
            surfaceContainerHighest = palette.surfaceHighest
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            inversePrimary = accentSet.darkPrimary,
            secondary = palette.secondary,
            onSecondary = contentColorFor(palette.secondary),
            secondaryContainer = palette.secondary.copy(alpha = 0.12f).compositeOver(palette.surface),
            onSecondaryContainer = palette.secondary,
            tertiary = palette.info,
            onTertiary = contentColorFor(palette.info),
            tertiaryContainer = palette.info.copy(alpha = 0.12f).compositeOver(palette.surface),
            onTertiaryContainer = palette.info,
            background = palette.background,
            onBackground = palette.onSurface,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceHigh,
            onSurfaceVariant = palette.onSurfaceVariant,
            surfaceTint = palette.primary,
            inverseSurface = DarkAppPalette.surface,
            inverseOnSurface = DarkAppPalette.onSurface,
            error = palette.error,
            onError = contentColorFor(palette.error),
            errorContainer = palette.error.copy(alpha = 0.12f).compositeOver(palette.surface),
            onErrorContainer = palette.error,
            outline = palette.controlOutline,
            outlineVariant = palette.border,
            scrim = Color.Black,
            surfaceBright = palette.surfaceLowest,
            surfaceDim = palette.surfaceHighest,
            surfaceContainerLowest = palette.surfaceLowest,
            surfaceContainerLow = palette.surfaceLow,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceHigh,
            surfaceContainerHighest = palette.surfaceHighest
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
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = APP_FONT_FEATURE_SETTINGS
                ),
                content = content
            )
        }
    }
}

internal fun accentColorForMode(accent: String, dark: Boolean): Color =
    accentSetFor(accent).let { if (dark) it.darkPrimary else it.lightPrimary }

private fun accentSetFor(accent: String): AccentSet = when (accent) {
    "cyan" -> AccentSet(Color(0xFF67E8F9), Color(0xFF0891B2), Color(0xFF0B6577))
    "blue" -> AccentSet(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1D4ED8))
    "amber" -> AccentSet(Color(0xFFFCD34D), Color(0xFFD97706), Color(0xFF92400E))
    "purple" -> AccentSet(Color(0xFFC4B5FD), Color(0xFF7C3AED), Color(0xFF6D28D9))
    else -> AccentSet(Color(0xFF4EDEA3), Color(0xFF10B981), Color(0xFF065F46))
}

private fun createPalette(dark: Boolean, oled: Boolean, accent: String): AppColorPalette {
    val accentSet = accentSetFor(accent)
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
