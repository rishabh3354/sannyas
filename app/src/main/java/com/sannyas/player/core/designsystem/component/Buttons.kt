package com.sannyas.player.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sannyas.player.R
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens

/** Minimum touch target, per the Material accessibility guidance. */
private val MinTouchTarget = 48.dp

/**
 * The primary transport button.
 *
 * The icon swap is animated with a scale+fade rather than an instant change,
 * which is the single detail that most makes a player feel responsive. The
 * whole button also breathes slightly while playing.
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    showGlow: Boolean = true,
) {
    val interaction = rememberInteraction()
    val clickLabel = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.97f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "play-scale",
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .size(size)
            .scale(scale)
            .pressScale(interaction, pressedScale = 0.92f)
            .then(if (showGlow) Modifier.glow(containerColor, radius = size / 3) else Modifier)
            .clip(OshoShapeTokens.Pill)
            .background(
                Brush.linearGradient(
                    listOf(containerColor, containerColor.copy(alpha = 0.82f)),
                ),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = clickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isBuffering to isPlaying,
            transitionSpec = {
                (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.7f) + fadeOut())
            },
            label = "play-icon",
        ) { (buffering, playing) ->
            when {
                buffering -> CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.4f),
                    color = contentColor,
                    strokeWidth = 2.5.dp,
                )

                else -> Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(size * 0.52f),
                )
            }
        }
    }
}

/**
 * Transport / utility icon button.
 *
 * Always occupies at least a 48 dp touch target even when the icon is smaller,
 * so the dense control rows on the player remain usable.
 */
@Composable
fun OshoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val interaction = rememberInteraction()
    val activeTint = if (active) MaterialTheme.colorScheme.primary else tint
    val resolvedTint = if (enabled) activeTint else tint.copy(alpha = 0.35f)
    // Aliased because `contentDescription` inside the semantics lambda resolves
    // to the property being set, not to this parameter.
    val description = contentDescription

    Box(
        modifier = modifier
            .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .clip(OshoShapeTokens.Pill)
            .pressScale(interaction, pressedScale = 0.88f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            // The label belongs on the node that is actually clickable. With it
            // only on the Icon, accessibility tooling sees an unlabelled button
            // with a described child, and a Play review flagged exactly that
            // shape on the back arrow as an unresponsive control.
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            // Described by the parent; repeating it here makes TalkBack
            // announce the same words twice.
            contentDescription = null,
            tint = resolvedTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Favourite toggle with a small overshoot when it turns on. */
@Composable
fun FavouriteButton(
    isFavourite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavourite) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "favourite-scale",
    )

    OshoIconButton(
        icon = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = stringResource(
            if (isFavourite) R.string.cd_unfavourite else R.string.cd_favourite,
        ),
        onClick = onToggle,
        modifier = modifier.scale(scale),
        iconSize = iconSize,
        tint = if (isFavourite) MaterialTheme.colorScheme.secondary else tint,
    )
}

/** Filled pill action — "Play all". */
@Composable
fun PrimaryPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interaction = rememberInteraction()

    Row(
        modifier = modifier
            .sizeIn(minHeight = MinTouchTarget)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Pill)
            .background(containerColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/** Outlined pill action — "Shuffle". */
@Composable
fun SecondaryPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = rememberInteraction()

    Row(
        modifier = modifier
            .sizeIn(minHeight = MinTouchTarget)
            .pressScale(interaction)
            .glassSurface(OshoShapeTokens.Pill, tintAlphaBoost = 0.04f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
