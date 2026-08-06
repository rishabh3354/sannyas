package com.sannyas.player.core.designsystem.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** User-selectable theme preference, persisted in DataStore. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkScheme = darkColorScheme(
    primary = OshoColors.Amethyst,
    onPrimary = Color.White,
    primaryContainer = OshoColors.AmethystDeep,
    onPrimaryContainer = OshoColors.AmethystSoft,
    inversePrimary = OshoColors.AmethystDeep,

    secondary = OshoColors.Ember,
    onSecondary = Color(0xFF2B0F05),
    secondaryContainer = Color(0xFF54210F),
    onSecondaryContainer = OshoColors.EmberSoft,

    tertiary = OshoColors.Teal,
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF124D54),
    onTertiaryContainer = OshoColors.TealSoft,

    background = OshoColors.DarkSurface,
    onBackground = OshoColors.DarkOnSurface,
    surface = OshoColors.DarkSurface,
    onSurface = OshoColors.DarkOnSurface,
    surfaceVariant = OshoColors.DarkSurfaceContainerHigh,
    onSurfaceVariant = OshoColors.DarkOnSurfaceVariant,
    surfaceBright = OshoColors.DarkSurfaceBright,
    surfaceDim = OshoColors.DarkSurfaceContainerLowest,
    surfaceContainerLowest = OshoColors.DarkSurfaceContainerLowest,
    surfaceContainerLow = OshoColors.DarkSurfaceContainerLow,
    surfaceContainer = OshoColors.DarkSurfaceContainer,
    surfaceContainerHigh = OshoColors.DarkSurfaceContainerHigh,
    surfaceContainerHighest = OshoColors.DarkSurfaceContainerHighest,

    outline = OshoColors.DarkOutline,
    outlineVariant = OshoColors.DarkOutlineVariant,
    error = OshoColors.Error,
    onError = Color.White,
    errorContainer = OshoColors.ErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD5),

    inverseSurface = OshoColors.DarkOnSurface,
    inverseOnSurface = OshoColors.DarkSurface,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = OshoColors.AmethystDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1D0060),
    inversePrimary = OshoColors.AmethystSoft,

    secondary = Color(0xFF9C4322),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF3A0B00),

    tertiary = Color(0xFF00696F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9EF0F7),
    onTertiaryContainer = Color(0xFF002022),

    background = OshoColors.LightSurface,
    onBackground = OshoColors.LightOnSurface,
    surface = OshoColors.LightSurface,
    onSurface = OshoColors.LightOnSurface,
    surfaceVariant = OshoColors.LightSurfaceContainerHigh,
    onSurfaceVariant = OshoColors.LightOnSurfaceVariant,
    surfaceBright = OshoColors.LightSurfaceBright,
    surfaceDim = Color(0xFFDDD8E6),
    surfaceContainerLowest = OshoColors.LightSurfaceContainerLowest,
    surfaceContainerLow = OshoColors.LightSurfaceContainerLow,
    surfaceContainer = OshoColors.LightSurfaceContainer,
    surfaceContainerHigh = OshoColors.LightSurfaceContainerHigh,
    surfaceContainerHighest = OshoColors.LightSurfaceContainerHighest,

    outline = OshoColors.LightOutline,
    outlineVariant = OshoColors.LightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = OshoColors.ErrorContainerLight,
    onErrorContainer = Color(0xFF410002),

    inverseSurface = Color(0xFF322F3A),
    inverseOnSurface = Color(0xFFF5EEFF),
    scrim = Color.Black,
)

/**
 * Extra colours that Material 3 has no slot for but the app needs everywhere:
 * the aurora gradient stops and the glass tint used by frosted surfaces.
 */
data class OshoAmbience(
    val aurora: List<Color>,
    val glassTint: Color,
    val glassBorder: Color,
    val scrimTop: Color,
    val scrimBottom: Color,
)

val LocalOshoAmbience = staticCompositionLocalOf {
    OshoAmbience(
        aurora = OshoColors.AuroraDark,
        glassTint = Color.White.copy(alpha = 0.06f),
        glassBorder = Color.White.copy(alpha = 0.10f),
        scrimTop = Color.Black.copy(alpha = 0.0f),
        scrimBottom = Color.Black.copy(alpha = 0.85f),
    )
}

/** True when the resolved scheme is dark, regardless of how it was resolved. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

@Composable
fun OshoTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseScheme = when {
        dynamicColor && supportsDynamic ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkScheme
        else -> LightScheme
    }

    // Dynamic dark schemes come back lighter than the app's near-black, which
    // washes out artwork. Earlier this replaced the surfaces outright — but that
    // threw away the wallpaper tint entirely, which is the whole point of
    // Material You and why it looked like it "wasn't working". Darkening towards
    // black instead keeps the hue while restoring the depth.
    val scheme = if (dynamicColor && supportsDynamic && dark) {
        baseScheme.copy(
            background = baseScheme.background.deepen(0.86f),
            surface = baseScheme.surface.deepen(0.86f),
            surfaceContainerLowest = baseScheme.surfaceContainerLowest.deepen(0.88f),
            surfaceContainerLow = baseScheme.surfaceContainerLow.deepen(0.82f),
            surfaceContainer = baseScheme.surfaceContainer.deepen(0.78f),
            surfaceContainerHigh = baseScheme.surfaceContainerHigh.deepen(0.70f),
            surfaceContainerHighest = baseScheme.surfaceContainerHighest.deepen(0.62f),
        )
    } else {
        baseScheme
    }

    val animated = scheme.animated()

    val ambience = OshoAmbience(
        aurora = if (dark) OshoColors.AuroraDark else OshoColors.AuroraLight,
        // Glass works by lifting a surface off a dark backdrop. On a light one
        // a white tint and white border are invisible — the "Shuffle" pill was
        // white-on-white — so light theme tints and outlines with black instead.
        glassTint = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
        glassBorder = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.14f),
        scrimTop = Color.Transparent,
        scrimBottom = if (dark) Color.Black.copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.55f),
    )

    // Devanagari does not want the Latin scale's tightening — see
    // [OshoTypographyUntracked].
    val locale = LocalConfiguration.current.locales[0]
    val typography = remember(locale.language) {
        if (locale.language == "hi") OshoTypographyUntracked else OshoTypography
    }

    CompositionLocalProvider(
        LocalOshoAmbience provides ambience,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = animated,
            typography = typography,
            shapes = OshoShapes,
            content = content,
        )
    }
}

/**
 * Mixes a colour towards black, keeping its hue.
 *
 * [amount] is how far to travel: 0f leaves it untouched, 1f is black.
 */
private fun Color.deepen(amount: Float): Color {
    val keep = 1f - amount.coerceIn(0f, 1f)
    return Color(red = red * keep, green = green * keep, blue = blue * keep, alpha = alpha)
}

/**
 * Cross-fades every colour role so switching theme or wallpaper never snaps.
 * Costs one animation per role but they are all idle in the steady state.
 */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = 420)

    @Composable
    fun Color.anim(label: String) = animateColorAsState(this, spec, label = label).value

    return copy(
        primary = primary.anim("primary"),
        onPrimary = onPrimary.anim("onPrimary"),
        primaryContainer = primaryContainer.anim("primaryContainer"),
        onPrimaryContainer = onPrimaryContainer.anim("onPrimaryContainer"),
        secondary = secondary.anim("secondary"),
        onSecondary = onSecondary.anim("onSecondary"),
        secondaryContainer = secondaryContainer.anim("secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.anim("onSecondaryContainer"),
        tertiary = tertiary.anim("tertiary"),
        onTertiary = onTertiary.anim("onTertiary"),
        tertiaryContainer = tertiaryContainer.anim("tertiaryContainer"),
        onTertiaryContainer = onTertiaryContainer.anim("onTertiaryContainer"),
        background = background.anim("background"),
        onBackground = onBackground.anim("onBackground"),
        surface = surface.anim("surface"),
        onSurface = onSurface.anim("onSurface"),
        surfaceVariant = surfaceVariant.anim("surfaceVariant"),
        onSurfaceVariant = onSurfaceVariant.anim("onSurfaceVariant"),
        surfaceContainerLowest = surfaceContainerLowest.anim("surfaceContainerLowest"),
        surfaceContainerLow = surfaceContainerLow.anim("surfaceContainerLow"),
        surfaceContainer = surfaceContainer.anim("surfaceContainer"),
        surfaceContainerHigh = surfaceContainerHigh.anim("surfaceContainerHigh"),
        surfaceContainerHighest = surfaceContainerHighest.anim("surfaceContainerHighest"),
        outline = outline.anim("outline"),
        outlineVariant = outlineVariant.anim("outlineVariant"),
    )
}
