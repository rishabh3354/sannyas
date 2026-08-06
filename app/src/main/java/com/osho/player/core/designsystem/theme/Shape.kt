package com.osho.player.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Larger than stock Material 3 throughout — soft, pill-like
 * geometry is a big part of why the app reads as premium rather than utilitarian.
 */
val OshoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Named shapes for recurring surfaces, so radii stay consistent across screens. */
object OshoShapeTokens {
    val Artwork = RoundedCornerShape(20.dp)
    val ArtworkLarge = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(24.dp)
    val HeroCard = RoundedCornerShape(28.dp)
    val Chip = RoundedCornerShape(percent = 50)
    val MiniPlayer = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Pill = RoundedCornerShape(percent = 50)
}
