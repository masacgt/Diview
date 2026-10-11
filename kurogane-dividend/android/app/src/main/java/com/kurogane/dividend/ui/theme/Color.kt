package com.kurogane.dividend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColorPalette(
    val background: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val textMuted: Color,
    val secondary: Color,
    val skyBlue: Color,
    val accentAmber: Color,
    val error: Color,
    val border: Color
)

internal val DarkAppPalette = AppColorPalette(
    background = Color(0xFF0B1326),
    surfaceLowest = Color(0xFF060E20),
    surfaceLow = Color(0xFF131B2E),
    surface = Color(0xFF171F33),
    surfaceHigh = Color(0xFF222A3D),
    surfaceHighest = Color(0xFF2D3449),
    primary = Color(0xFF4EDEA3),
    primaryContainer = Color(0xFF10B981),
    onPrimary = Color(0xFF003824),
    onPrimaryContainer = Color(0xFF003824),
    onSurface = Color(0xFFDAE2FD),
    onSurfaceVariant = Color(0xFFBBCABF),
    textMuted = Color(0xFF94A299),
    secondary = Color(0xFFC0C1FF),
    skyBlue = Color(0xFF38BDF8),
    accentAmber = Color(0xFFF59E0B),
    error = Color(0xFFFFB4AB),
    border = Color(0xFF1E293B)
)

internal val LightAppPalette = AppColorPalette(
    background = Color(0xFFF1F5F9),
    surfaceLowest = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFFFFFFF),
    surface = Color(0xFFF8FAFC),
    surfaceHigh = Color(0xFFEFF3F8),
    surfaceHighest = Color(0xFFE2E8F0),
    primary = Color(0xFF047857),
    primaryContainer = Color(0xFF059669),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172033),
    onSurfaceVariant = Color(0xFF475569),
    textMuted = Color(0xFF58677D),
    secondary = Color(0xFF5B5FC7),
    skyBlue = Color(0xFF0369A1),
    accentAmber = Color(0xFFB45309),
    error = Color(0xFFB42318),
    border = Color(0xFFCBD5E1)
)

internal val LocalAppPalette = staticCompositionLocalOf { DarkAppPalette }

val BackgroundDark: Color @Composable get() = LocalAppPalette.current.background
val SurfaceContainerLowest: Color @Composable get() = LocalAppPalette.current.surfaceLowest
val SurfaceContainerLow: Color @Composable get() = LocalAppPalette.current.surfaceLow
val SurfaceContainer: Color @Composable get() = LocalAppPalette.current.surface
val SurfaceContainerHigh: Color @Composable get() = LocalAppPalette.current.surfaceHigh
val SurfaceContainerHighest: Color @Composable get() = LocalAppPalette.current.surfaceHighest
val PrimaryEmerald: Color @Composable get() = LocalAppPalette.current.primary
val PrimaryEmeraldContainer: Color @Composable get() = LocalAppPalette.current.primaryContainer
val OnPrimary: Color @Composable get() = LocalAppPalette.current.onPrimary
val OnPrimaryContainer: Color @Composable get() = LocalAppPalette.current.onPrimaryContainer
val OnSurface: Color @Composable get() = LocalAppPalette.current.onSurface
val OnSurfaceVariant: Color @Composable get() = LocalAppPalette.current.onSurfaceVariant
val TextMuted: Color @Composable get() = LocalAppPalette.current.textMuted
val SecondaryIndigo: Color @Composable get() = LocalAppPalette.current.secondary
val SkyBlue: Color @Composable get() = LocalAppPalette.current.skyBlue
val AccentAmber: Color @Composable get() = LocalAppPalette.current.accentAmber
val ErrorRed: Color @Composable get() = LocalAppPalette.current.error
val BorderColor: Color @Composable get() = LocalAppPalette.current.border
