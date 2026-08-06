package com.sannyas.player.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The Osho palette.
 *
 * The app is dark-first: a near-black, very slightly violet surface lets large
 * artwork and the animated aurora backdrop carry the colour, the way premium
 * music apps do. Light theme exists and is fully supported, but it is tuned as
 * a warm paper tone rather than pure white so the accent colours stay legible.
 */
object OshoColors {

    // ---- Brand ------------------------------------------------------------
    /** Primary brand violet. Used for the play button, active states, progress. */
    val Amethyst = Color(0xFF7C5CFF)
    val AmethystSoft = Color(0xFFB39DFF)
    val AmethystDeep = Color(0xFF4B32C3)

    /** Warm secondary. Evokes lamplight; used for favourites and highlights. */
    val Ember = Color(0xFFFF8A65)
    val EmberSoft = Color(0xFFFFB59B)

    /** Cool tertiary. Used for meditation/stillness categories and downloads. */
    val Teal = Color(0xFF4DD0E1)
    val TealSoft = Color(0xFF9BE8F2)

    val Gold = Color(0xFFF5C26B)

    // ---- Dark scheme ------------------------------------------------------
    val DarkSurface = Color(0xFF0B0A0F)
    val DarkSurfaceBright = Color(0xFF16141D)
    val DarkSurfaceContainerLowest = Color(0xFF070609)
    val DarkSurfaceContainerLow = Color(0xFF121017)
    val DarkSurfaceContainer = Color(0xFF17151F)
    val DarkSurfaceContainerHigh = Color(0xFF1F1C29)
    val DarkSurfaceContainerHighest = Color(0xFF272334)
    val DarkOnSurface = Color(0xFFF3F0FA)
    val DarkOnSurfaceVariant = Color(0xFFA8A2BC)
    val DarkOutline = Color(0xFF474155)
    val DarkOutlineVariant = Color(0xFF2A2635)

    // ---- Light scheme -----------------------------------------------------
    val LightSurface = Color(0xFFFBF8FF)
    val LightSurfaceBright = Color(0xFFFFFFFF)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFF5F1FC)
    val LightSurfaceContainer = Color(0xFFEFEAF8)
    val LightSurfaceContainerHigh = Color(0xFFE9E3F4)
    val LightSurfaceContainerHighest = Color(0xFFE2DBEF)
    val LightOnSurface = Color(0xFF161320)
    val LightOnSurfaceVariant = Color(0xFF585167)
    val LightOutline = Color(0xFF8B8399)
    val LightOutlineVariant = Color(0xFFDBD4E8)

    val Error = Color(0xFFFF5449)
    val ErrorContainerDark = Color(0xFF5C1512)
    val ErrorContainerLight = Color(0xFFFFDAD5)

    /**
     * Gradient stops used by the animated aurora that sits behind the home
     * header and the full player. Ordered so consecutive pairs blend pleasantly.
     */
    val AuroraDark = listOf(
        Color(0xFF3B1F7A),
        Color(0xFF1B3A6B),
        Color(0xFF6B2247),
        Color(0xFF17403F),
    )

    val AuroraLight = listOf(
        Color(0xFFD9CCFF),
        Color(0xFFCCE2FF),
        Color(0xFFFFD6E4),
        Color(0xFFCFF2EC),
    )

    /**
     * Deterministic accent pairs assigned to categories and to artwork that has
     * not been colour-extracted yet, so every card still feels intentional.
     */
    val CategoryAccents = listOf(
        Color(0xFF7C5CFF) to Color(0xFF3B1F7A),
        Color(0xFF4DD0E1) to Color(0xFF10454C),
        Color(0xFFFF8A65) to Color(0xFF6B2B18),
        Color(0xFFF5C26B) to Color(0xFF5C4415),
        Color(0xFF80D8A0) to Color(0xFF17452C),
        Color(0xFFFF7EB6) to Color(0xFF611437),
        Color(0xFF8AB4FF) to Color(0xFF1B3A6B),
        Color(0xFFC792EA) to Color(0xFF48226B),
    )

    /** Stable accent for a given key, so a series keeps its colour across screens. */
    fun accentFor(key: String): Pair<Color, Color> {
        val index = (key.hashCode().toLong() and 0xFFFFFFFFL) % CategoryAccents.size
        return CategoryAccents[index.toInt()]
    }
}
