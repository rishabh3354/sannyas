package com.osho.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osho.player.R
import com.osho.player.core.designsystem.component.Artwork
import com.osho.player.core.designsystem.component.OshoIconButton
import com.osho.player.core.designsystem.component.PlayPauseButton
import com.osho.player.core.designsystem.component.glassSurface
import com.osho.player.core.designsystem.component.pressScale
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.component.softShadow
import com.osho.player.core.designsystem.theme.LocalIsDarkTheme
import com.osho.player.core.designsystem.theme.OshoColors
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.domain.model.PlayerState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The bar pinned above the bottom navigation.
 *
 * Its accent is derived from the current series, so the strip of colour under
 * the artwork changes as the queue moves — a small thing that makes the app
 * feel alive without any extra chrome. Tapping anywhere expands the full player.
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
) {
    val track = state.currentTrack ?: return
    val interaction = rememberInteraction()
    val expandLabel = stringResource(R.string.cd_expand_player)
    val description = stringResource(R.string.cd_mini_player, track.title, track.speaker)

    val accent = OshoColors.accentFor(track.discourseId.ifBlank { track.title })
    val isDark = LocalIsDarkTheme.current
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 420),
        label = "mini-progress",
    )
    val buffered by animateFloatAsState(
        targetValue = state.bufferedProgress,
        animationSpec = tween(durationMillis = 420),
        label = "mini-buffered",
    )

    // Horizontal drag offset, so the bar tracks the finger before it is flung
    // away. Reset with a spring if the gesture does not clear the threshold.
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { DISMISS_THRESHOLD.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            // Fades out as it slides, so the dismissal reads as intentional.
            .graphicsLayer {
                alpha = 1f - (abs(offsetX.value) / (dismissThresholdPx * 2.5f)).coerceIn(0f, 0.85f)
            }
            .pointerInput(track.id, dismissEnabled) {
                if (!dismissEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > dismissThresholdPx) {
                                offsetX.animateTo(
                                    targetValue = offsetX.value.sign * size.width * 1.4f,
                                    animationSpec = tween(180),
                                )
                                onDismiss()
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                    onHorizontalDrag = { _, delta ->
                        scope.launch { offsetX.snapTo(offsetX.value + delta) }
                    },
                )
            }
            .softShadow(OshoShapeTokens.MiniPlayer, elevation = 16.dp)
            .clip(OshoShapeTokens.MiniPlayer)
            // Deliberately opaque rather than frosted: at the previous
            // translucency the artwork scrolling behind it made the title hard
            // to read, which matters more here than seeing through the bar.
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(
                Brush.horizontalGradient(
                    // The accent's deep tone reads as mud over a light surface,
                    // so light theme gets a much thinner wash of it.
                    listOf(
                        accent.second.copy(alpha = if (isDark) 0.55f else 0.14f),
                        Color.Transparent,
                    ),
                ),
            )
            .pressScale(interaction, pressedScale = 0.99f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = expandLabel,
                onClick = onExpand,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                imageUrl = track.artworkUrl,
                contentDescription = null,
                fallbackKey = track.discourseId,
                fallbackLabel = track.discourseTitle,
                shape = OshoShapeTokens.Artwork,
                modifier = Modifier.size(46.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Text(
                    text = track.discourseTitle.ifBlank { track.speaker },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }

            Spacer(Modifier.width(8.dp))

            PlayPauseButton(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onClick = onPlayPause,
                size = 40.dp,
                showGlow = false,
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            )

            OshoIconButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                onClick = onNext,
                enabled = state.hasNext,
                iconSize = 24.dp,
            )
        }

        // A hairline progress track rather than a full-height bar: it reads as
        // part of the surface, not as a control.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .padding(horizontal = 10.dp)
                .clip(OshoShapeTokens.Pill)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                .drawBehind {
                    // Matches the full player's seek bar, so the two never
                    // disagree about how much has loaded.
                    drawRect(
                        color = accent.first.copy(alpha = 0.55f),
                        size = size.copy(width = size.width * buffered),
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(accent.first, accent.first.copy(alpha = 0.65f)),
                        ),
                        size = size.copy(width = size.width * progress),
                    )
                },
        )

        Spacer(Modifier.height(8.dp))
    }
}

/** How far the bar must travel before a drag counts as a dismissal. */
private val DISMISS_THRESHOLD = 96.dp
