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
    val border: Color,
    val controlOutline: Color,
    val success: Color,
    val info: Color,
    val chartBlue: Color,
    val chartPurple: Color,
    val chartCyan: Color,
    val chartNeutral: Color
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
    accentAmber = Color(0xFFFCD34D),
    error = Color(0xFFFFB4AB),
    border = Color(0xFF334155),
    controlOutline = Color(0xFF8491A6),
    success = Color(0xFF4ADE80),
    info = Color(0xFF7DD3FC),
    chartBlue = Color(0xFF60A5FA),
    chartPurple = Color(0xFFC4B5FD),
    chartCyan = Color(0xFF67E8F9),
    chartNeutral = Color(0xFF94A3B8)
)

internal val LightAppPalette = AppColorPalette(
    background = Color(0xFFF1F5F9),
    surfaceLowest = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFFFFFFF),
    surface = Color(0xFFF8FAFC),
    surfaceHigh = Color(0xFFEFF3F8),
    surfaceHighest = Color(0xFFE2E8F0),
    primary = Color(0xFF065F46),
    primaryContainer = Color(0xFF065F46),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172033),
    onSurfaceVariant = Color(0xFF475569),
    textMuted = Color(0xFF58677D),
    secondary = Color(0xFF4F46B8),
    skyBlue = Color(0xFF0369A1),
    accentAmber = Color(0xFF92400E),
    error = Color(0xFFB42318),
    border = Color(0xFFCBD5E1),
    controlOutline = Color(0xFF64748B),
    success = Color(0xFF166534),
    info = Color(0xFF075985),
    chartBlue = Color(0xFF1D4ED8),
    chartPurple = Color(0xFF6D28D9),
    chartCyan = Color(0xFF0B6577),
    chartNeutral = Color(0xFF475569)
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
val ControlOutline: Color @Composable get() = LocalAppPalette.current.controlOutline
val FocusRingColor: Color @Composable get() = LocalAppPalette.current.primary
val StatusSuccess: Color @Composable get() = LocalAppPalette.current.success
val StatusWarning: Color @Composable get() = LocalAppPalette.current.accentAmber
val StatusInfo: Color @Composable get() = LocalAppPalette.current.info
val ChartBlue: Color @Composable get() = LocalAppPalette.current.chartBlue
val ChartPurple: Color @Composable get() = LocalAppPalette.current.chartPurple
val ChartCyan: Color @Composable get() = LocalAppPalette.current.chartCyan
val ChartNeutral: Color @Composable get() = LocalAppPalette.current.chartNeutral
