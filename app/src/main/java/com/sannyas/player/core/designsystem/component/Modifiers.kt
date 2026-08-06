package com.sannyas.player.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sannyas.player.core.designsystem.theme.LocalOshoAmbience

/**
 * Frosted-glass surface: a translucent tint plus a one-pixel light border.
 *
 * Compose has no live backdrop blur below API 31, so rather than fake one
 * inconsistently this leans on translucency and a bright hairline edge, which
 * is what actually sells the effect over the app's animated backgrounds.
 */
fun Modifier.glassSurface(
    shape: Shape,
    tintAlphaBoost: Float = 0f,
    borderWidth: Dp = 1.dp,
): Modifier = composed {
    val ambience = LocalOshoAmbience.current
    val tint = ambience.glassTint.let { it.copy(alpha = (it.alpha + tintAlphaBoost).coerceIn(0f, 1f)) }

    clip(shape)
        .background(tint)
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                listOf(ambience.glassBorder, ambience.glassBorder.copy(alpha = 0.02f)),
            ),
            shape = shape,
        )
}

/**
 * Soft elevation shadow tuned for the dark theme, where Material's default
 * shadow is almost invisible.
 */
fun Modifier.softShadow(
    shape: Shape,
    elevation: Dp = 18.dp,
    ambientAlpha: Float = 0.28f,
    spotAlpha: Float = 0.42f,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = ambientAlpha),
    spotColor = Color.Black.copy(alpha = spotAlpha),
)

/**
 * Springy press feedback.
 *
 * Pass the same [interactionSource] to the clickable so the scale tracks the
 * real press state, including presses cancelled by a scroll.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
    scale(scale)
}

/**
 * The sweeping highlight used by loading placeholders.
 *
 * Drawn with [drawWithCache] so the brush is rebuilt only when the size or
 * colours change, not on every animation frame.
 */
fun Modifier.shimmer(
    shape: Shape,
    baseColor: Color,
    highlightColor: Color,
    durationMillis: Int = 1_400,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloatAsStateInfinite(durationMillis)

    clip(shape).drawWithCache {
        val width = size.width
        val sweep = width * 0.6f
        val start = -sweep + (width + sweep * 2f) * progress

        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(start, 0f),
            end = Offset(start + sweep, size.height),
        )

        onDrawBehind { drawRect(brush) }
    }
}

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatAsStateInfinite(
    durationMillis: Int,
) = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = "shimmer-progress",
)

/** Remembers an interaction source without the boilerplate at every call site. */
@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
