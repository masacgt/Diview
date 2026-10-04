package com.kurogane.dividend.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kurogane.dividend.model.AccentColor
import com.kurogane.dividend.model.AppUiSettings
import com.kurogane.dividend.model.ThemeMode

private fun accent(color: AccentColor) = when (color) {
    AccentColor.EMERALD -> Color(0xFF4EDEA3)
    AccentColor.CYAN -> Color(0xFF22D3EE)
    AccentColor.BLUE -> Color(0xFF60A5FA)
    AccentColor.AMBER -> Color(0xFFF59E0B)
    AccentColor.PURPLE -> Color(0xFF8B5CF6)
}

private fun darkScheme(primary: Color, oled: Boolean) = darkColorScheme(
    primary = primary,
    onPrimary = OnPrimary,
    primaryContainer = primary.copy(alpha = 0.3f),
    background = if (oled) Color.Black else BackgroundDark,
    surface = if (oled) Color(0xFF080808) else SurfaceContainer,
    onSurface = OnSurface,
    surfaceVariant = if (oled) Color(0xFF141414) else SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariant,
    secondary = SecondaryIndigo
)

private fun lightScheme(primary: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color(0xFF002116),
    primaryContainer = primary.copy(alpha = 0.2f),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE9EDF5),
    onSurfaceVariant = Color(0xFF526070),
    secondary = Color(0xFF4F46E5)
)

@Composable
fun KuroganeDividendTheme(
    settings: AppUiSettings = AppUiSettings(),
    content: @Composable () -> Unit
) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val primary = accent(settings.accentColor)
    val colorScheme = if (dark) darkScheme(primary, settings.oledBlack) else lightScheme(primary)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
      )
}
