package com.osho.player.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * The scale is deliberately tighter and heavier than stock Material 3: display
 * and headline styles use negative tracking and semi-bold weights, which is what
 * gives streaming apps their editorial feel. Body styles keep generous line
 * height for long discourse descriptions.
 *
 * ### Using Manrope or Inter
 * The app ships with the platform font family so it builds with no binary
 * assets and no Google Play Services dependency. To switch to Manrope:
 *
 * 1. Drop `manrope_regular.ttf`, `manrope_medium.ttf`, `manrope_semibold.ttf`
 *    and `manrope_bold.ttf` into `app/src/main/res/font/`.
 * 2. Replace [OshoFontFamily] with the commented-out definition below.
 *
 * Everything else picks it up automatically because every style below is
 * defined in terms of [OshoFontFamily].
 *
 * ```
 * val OshoFontFamily = FontFamily(
 *     Font(R.font.manrope_regular, FontWeight.Normal),
 *     Font(R.font.manrope_medium, FontWeight.Medium),
 *     Font(R.font.manrope_semibold, FontWeight.SemiBold),
 *     Font(R.font.manrope_bold, FontWeight.Bold),
 * )
 * ```
 */
val OshoFontFamily: FontFamily = FontFamily.Default

/** Trims the extra leading Android adds, which otherwise breaks vertical rhythm. */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun osho(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double,
) = TextStyle(
    fontFamily = OshoFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TightLineHeight,
)

private fun typography(track: (Double) -> Double) = Typography(
    displayLarge = osho(56, 62, FontWeight.Bold, track(-1.4)),
    displayMedium = osho(44, 50, FontWeight.Bold, track(-1.0)),
    displaySmall = osho(34, 40, FontWeight.SemiBold, track(-0.7)),

    headlineLarge = osho(30, 36, FontWeight.SemiBold, track(-0.6)),
    headlineMedium = osho(26, 32, FontWeight.SemiBold, track(-0.5)),
    headlineSmall = osho(22, 28, FontWeight.SemiBold, track(-0.3)),

    titleLarge = osho(20, 26, FontWeight.SemiBold, track(-0.2)),
    titleMedium = osho(16, 22, FontWeight.SemiBold, track(0.0)),
    titleSmall = osho(14, 20, FontWeight.Medium, track(0.1)),

    bodyLarge = osho(16, 25, FontWeight.Normal, track(0.1)),
    bodyMedium = osho(14, 22, FontWeight.Normal, track(0.15)),
    bodySmall = osho(12, 18, FontWeight.Normal, track(0.2)),

    labelLarge = osho(14, 18, FontWeight.SemiBold, track(0.3)),
    labelMedium = osho(12, 16, FontWeight.Medium, track(0.4)),
    labelSmall = osho(11, 14, FontWeight.Medium, track(0.5)),
)

/** The Latin scale, with the tight tracking the design calls for. */
val OshoTypography: Typography = typography { it }

/**
 * The same scale with the negative tracking neutralised.
 *
 * Tightening is a Latin-display nicety. On Devanagari it is wrong twice over:
 * matras and conjuncts need their sidebearings, and — more concretely — it
 * desynchronises Compose's line breaker from the shaped width, so a six-letter
 * title like "रेडियो" splits across two lines inside a box with three times the
 * room it needs. Only the negative values are clamped; the positive tracking on
 * label styles widens text and is safe, so it stays. Sizes, weights and line
 * heights are untouched, and the two scales remain visually of a piece.
 */
val OshoTypographyUntracked: Typography = typography { it.coerceAtLeast(0.0) }
