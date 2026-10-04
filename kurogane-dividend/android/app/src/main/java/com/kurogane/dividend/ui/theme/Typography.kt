package com.kurogane.dividend.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Shared font-size steps for screen text and dense financial data. */
object AppTextSize {
    val dense: TextUnit = 11.sp
    val caption: TextUnit = 12.sp
    val body: TextUnit = 14.sp
    val section: TextUnit = 16.sp
    val headline: TextUnit = 18.sp
    val screenTitle: TextUnit = 20.sp
    val metric: TextUnit = 24.sp
    val largeMetric: TextUnit = 28.sp
    val hero: TextUnit = 32.sp
}

private val materialTypography = Typography()

val AppTypography = materialTypography.copy(
    displayLarge = materialTypography.displayLarge.copy(fontSize = AppTextSize.hero, fontWeight = FontWeight.Bold),
    displayMedium = materialTypography.displayMedium.copy(fontSize = AppTextSize.largeMetric, fontWeight = FontWeight.Bold),
    displaySmall = materialTypography.displaySmall.copy(fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold),
    headlineLarge = materialTypography.headlineLarge.copy(fontSize = AppTextSize.metric, fontWeight = FontWeight.Bold),
    headlineMedium = materialTypography.headlineMedium.copy(fontSize = AppTextSize.screenTitle, fontWeight = FontWeight.Bold),
    headlineSmall = materialTypography.headlineSmall.copy(fontSize = AppTextSize.headline, fontWeight = FontWeight.SemiBold),
    titleLarge = materialTypography.titleLarge.copy(fontSize = AppTextSize.screenTitle, fontWeight = FontWeight.Bold),
    titleMedium = materialTypography.titleMedium.copy(fontSize = AppTextSize.headline, fontWeight = FontWeight.SemiBold),
    titleSmall = materialTypography.titleSmall.copy(fontSize = AppTextSize.section, fontWeight = FontWeight.SemiBold),
    bodyLarge = materialTypography.bodyLarge.copy(fontSize = AppTextSize.body),
    bodyMedium = materialTypography.bodyMedium.copy(fontSize = AppTextSize.caption),
    bodySmall = materialTypography.bodySmall.copy(fontSize = AppTextSize.dense),
    labelLarge = materialTypography.labelLarge.copy(fontSize = AppTextSize.body, fontWeight = FontWeight.Medium),
    labelMedium = materialTypography.labelMedium.copy(fontSize = AppTextSize.caption, fontWeight = FontWeight.Medium),
    labelSmall = materialTypography.labelSmall.copy(fontSize = AppTextSize.dense, fontWeight = FontWeight.Medium)
)
