package com.kurogane.dividend.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Shared font-size values for the app's semantic text roles. */
object AppTextSize {
    val chartLabel: TextUnit = 9.sp
    val supporting: TextUnit = 12.sp
    val body: TextUnit = 14.sp
    val heading: TextUnit = 18.sp
    val metric: TextUnit = 24.sp

    // Compatibility aliases share the semantic role values above.
    val micro: TextUnit = supporting
    val tiny: TextUnit = supporting
    val compact: TextUnit = supporting
    val dense: TextUnit = supporting
    val caption: TextUnit = supporting
    val compactBody: TextUnit = supporting
    val emphasizedBody: TextUnit = body
    val section: TextUnit = heading
    val emphasizedSection: TextUnit = heading
    val headline: TextUnit = heading
    val screenTitle: TextUnit = heading
    val largeMetric: TextUnit = metric
    val hero: TextUnit = metric
}

/** Existing explicit line heights, named without changing their rendered values. */
object AppTextLineHeight {
    val compact: TextUnit = 11.sp
    val dense: TextUnit = 13.sp
    val denseRelaxed: TextUnit = 14.sp
    val caption: TextUnit = 15.sp
    val bodyCompact: TextUnit = 16.sp
    val body: TextUnit = 18.sp
    val bodyRelaxed: TextUnit = 19.sp
    val section: TextUnit = 20.sp
}

/** Existing explicit letter spacing, named without changing its rendered value. */
object AppLetterSpacing {
    val tight: TextUnit = (-0.5).sp
    val navigation: TextUnit = 0.5.sp
    val shareTitle: TextUnit = 1.sp
}

/** Role-based weights shared by Japanese copy, headings, and supporting labels. */
object AppTextWeight {
    val body: FontWeight = FontWeight.Normal
    val supporting: FontWeight = FontWeight.Medium
    val heading: FontWeight = FontWeight.SemiBold
    val strongHeading: FontWeight = FontWeight.Bold
    val numeric: FontWeight = FontWeight.Bold
}

/** Tabular numeric style for amounts and financial metrics. */
val AppNumericTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = AppTextWeight.numeric,
    fontFeatureSettings = APP_NUMERIC_FONT_FEATURE_SETTINGS
)

val AppNumericFontFamily: FontFamily = AppNumericTextStyle.fontFamily!!

/** Add tabular figures and the app's Japanese spacing features to an existing style. */
fun TextStyle.withAppNumericFigures(): TextStyle = copy(
    fontFamily = AppNumericFontFamily,
    fontFeatureSettings = APP_NUMERIC_FONT_FEATURE_SETTINGS
)

private fun TextStyle.withAppTextRhythm(lineHeight: TextUnit): TextStyle = copy(
    lineHeight = lineHeight,
    letterSpacing = 0.sp
)

private val JapaneseTextLocales = LocaleList(Locale("ja-JP"))

private fun TextStyle.withJapaneseGlyphs(): TextStyle = copy(
    localeList = JapaneseTextLocales,
    fontFeatureSettings = APP_FONT_FEATURE_SETTINGS
)

private fun Typography.withJapaneseGlyphs(): Typography = copy(
    displayLarge = displayLarge.withJapaneseGlyphs(),
    displayMedium = displayMedium.withJapaneseGlyphs(),
    displaySmall = displaySmall.withJapaneseGlyphs(),
    headlineLarge = headlineLarge.withJapaneseGlyphs(),
    headlineMedium = headlineMedium.withJapaneseGlyphs(),
    headlineSmall = headlineSmall.withJapaneseGlyphs(),
    titleLarge = titleLarge.withJapaneseGlyphs(),
    titleMedium = titleMedium.withJapaneseGlyphs(),
    titleSmall = titleSmall.withJapaneseGlyphs(),
    bodyLarge = bodyLarge.withJapaneseGlyphs(),
    bodyMedium = bodyMedium.withJapaneseGlyphs(),
    bodySmall = bodySmall.withJapaneseGlyphs(),
    labelLarge = labelLarge.withJapaneseGlyphs(),
    labelMedium = labelMedium.withJapaneseGlyphs(),
    labelSmall = labelSmall.withJapaneseGlyphs()
)

private val materialTypography = Typography().withJapaneseGlyphs()

private fun Typography.withAppRoleWeights(): Typography = copy(
    displayLarge = displayLarge.copy(fontWeight = AppTextWeight.strongHeading),
    displayMedium = displayMedium.copy(fontWeight = AppTextWeight.strongHeading),
    displaySmall = displaySmall.copy(fontWeight = AppTextWeight.strongHeading),
    headlineLarge = headlineLarge.copy(fontWeight = AppTextWeight.strongHeading),
    headlineMedium = headlineMedium.copy(fontWeight = AppTextWeight.strongHeading),
    headlineSmall = headlineSmall.copy(fontWeight = AppTextWeight.heading),
    titleLarge = titleLarge.copy(fontWeight = AppTextWeight.strongHeading),
    titleMedium = titleMedium.copy(fontWeight = AppTextWeight.heading),
    titleSmall = titleSmall.copy(fontWeight = AppTextWeight.heading),
    bodyLarge = bodyLarge.copy(fontWeight = AppTextWeight.body),
    bodyMedium = bodyMedium.copy(fontWeight = AppTextWeight.body),
    bodySmall = bodySmall.copy(fontWeight = AppTextWeight.supporting),
    labelLarge = labelLarge.copy(fontWeight = AppTextWeight.supporting),
    labelMedium = labelMedium.copy(fontWeight = AppTextWeight.supporting),
    labelSmall = labelSmall.copy(fontWeight = AppTextWeight.supporting)
)

val AppTypography = materialTypography.withAppRoleWeights().copy(
    displayLarge = materialTypography.displayLarge.copy(fontSize = AppTextSize.metric, fontWeight = AppTextWeight.strongHeading).withAppTextRhythm(32.sp),
    displayMedium = materialTypography.displayMedium.copy(fontSize = AppTextSize.metric, fontWeight = AppTextWeight.strongHeading).withAppTextRhythm(32.sp),
    displaySmall = materialTypography.displaySmall.copy(fontSize = AppTextSize.metric, fontWeight = AppTextWeight.strongHeading).withAppTextRhythm(32.sp),
    headlineLarge = materialTypography.headlineLarge.copy(fontSize = AppTextSize.metric, fontWeight = AppTextWeight.strongHeading).withAppTextRhythm(32.sp),
    headlineMedium = materialTypography.headlineMedium.copy(fontSize = AppTextSize.heading, fontWeight = AppTextWeight.strongHeading).withAppTextRhythm(24.sp),
    headlineSmall = materialTypography.headlineSmall.copy(fontSize = AppTextSize.heading, fontWeight = AppTextWeight.heading).withAppTextRhythm(24.sp),
    titleLarge = materialTypography.titleLarge.copy(fontSize = AppTextSize.heading, fontWeight = AppTextWeight.strongHeading).withAppTextRhythm(24.sp),
    titleMedium = materialTypography.titleMedium.copy(fontSize = AppTextSize.heading, fontWeight = AppTextWeight.heading).withAppTextRhythm(24.sp),
    titleSmall = materialTypography.titleSmall.copy(fontSize = AppTextSize.heading, fontWeight = AppTextWeight.heading).withAppTextRhythm(22.sp),
    bodyLarge = materialTypography.bodyLarge.copy(fontSize = AppTextSize.body, fontWeight = AppTextWeight.body).withAppTextRhythm(AppTextLineHeight.body),
    bodyMedium = materialTypography.bodyMedium.copy(fontSize = AppTextSize.supporting, fontWeight = AppTextWeight.body).withAppTextRhythm(AppTextLineHeight.caption),
    bodySmall = materialTypography.bodySmall.copy(fontSize = AppTextSize.supporting, fontWeight = AppTextWeight.supporting).withAppTextRhythm(AppTextLineHeight.denseRelaxed),
    labelLarge = materialTypography.labelLarge.copy(fontSize = AppTextSize.body, fontWeight = AppTextWeight.supporting).withAppTextRhythm(AppTextLineHeight.body),
    labelMedium = materialTypography.labelMedium.copy(fontSize = AppTextSize.supporting, fontWeight = AppTextWeight.supporting).withAppTextRhythm(AppTextLineHeight.caption),
    labelSmall = materialTypography.labelSmall.copy(fontSize = AppTextSize.supporting, fontWeight = AppTextWeight.supporting).withAppTextRhythm(AppTextLineHeight.denseRelaxed)
)
