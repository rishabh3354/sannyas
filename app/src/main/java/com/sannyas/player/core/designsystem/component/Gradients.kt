package com.sannyas.player.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sannyas.player.core.designsystem.theme.LocalOshoAmbience
import kotlin.math.cos
import kotlin.math.sin

/**
 * A slow, continuously drifting multi-colour glow.
 *
 * Three large radial gradients orbit at different rates and radii; because they
 * are drawn with additive-looking soft alpha over a dark surface, the result
 * reads as one shifting field rather than three circles. Drawn in [drawBehind]
 * so it never allocates a layout node or triggers recomposition — only the
 * animated floats change, and they are read inside the draw lambda.
 *
 * @param colors gradient stops; defaults to the theme's aurora palette.
 * @param alpha overall strength. Lower it behind dense content.
 * @param speedMillis time for one full orbit. Long by design — a fast gradient
 *   reads as cheap.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = LocalOshoAmbience.current.aurora,
    alpha: Float = 0.55f,
    speedMillis: Int = 28_000,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(speedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora-phase",
    )

    val palette = remember(colors) { colors.ifEmpty { listOf(Color.Transparent) } }

    Box(
        modifier = modifier.drawBehind {
            drawAurora(palette, phase, alpha)
        },
        content = content,
    )
}

private fun DrawScope.drawAurora(colors: List<Color>, phase: Float, alpha: Float) {
    val radius = size.maxDimension * 0.75f

    colors.forEachIndexed { index, color ->
        // Each blob gets its own orbit speed and phase offset so they never
        // line up into an obvious rotating pattern.
        val speed = 1f + index * 0.35f
        val offset = index * (2f * Math.PI.toFloat() / colors.size)
        val angle = phase * speed + offset

        val centre = Offset(
            x = size.width * (0.5f + 0.42f * cos(angle)),
            y = size.height * (0.45f + 0.38f * sin(angle * 0.8f)),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

/**
 * Vertical scrim used under artwork so overlaid text stays legible.
 *
 * Uses an eased three-stop ramp rather than a linear one; a linear black
 * gradient over a photo produces a visible band where it starts.
 */
fun Modifier.verticalScrim(
    color: Color = Color.Black,
    startAlpha: Float = 0f,
    endAlpha: Float = 0.88f,
    startFraction: Float = 0.35f,
): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = startAlpha),
                startFraction to color.copy(alpha = endAlpha * 0.18f),
                (startFraction + 1f) / 2f to color.copy(alpha = endAlpha * 0.62f),
                1f to color.copy(alpha = endAlpha),
            ),
            startY = 0f,
            endY = size.height,
        ),
    )
}

/** Scrim that darkens the top of a hero image so a back button stays visible. */
fun Modifier.topScrim(color: Color = Color.Black, alpha: Float = 0.45f): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.45f,
        ),
    )
}

/**
 * A soft coloured halo behind an element — the glow under the play button and
 * beneath album art on the player screen.
 */
fun Modifier.glow(
    color: Color,
    radius: Dp = 48.dp,
    alpha: Float = 0.45f,
): Modifier = drawBehind {
    val glowRadius = size.minDimension / 2f + radius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = glowRadius,
        ),
        radius = glowRadius,
    )
}

/**
 * Two-colour diagonal wash used as a fallback behind artwork that has not
 * loaded, so a card is never a flat grey rectangle.
 */
fun accentBrush(accent: Pair<Color, Color>, size: Size = Size.Unspecified): Brush =
    Brush.linearGradient(
        colors = listOf(accent.first.copy(alpha = 0.85f), accent.second),
        start = Offset.Zero,
        end = if (size.isSpecified()) Offset(size.width, size.height) else Offset.Infinite,
    )

private fun Size.isSpecified(): Boolean = this != Size.Unspecified && width > 0f && height > 0f
