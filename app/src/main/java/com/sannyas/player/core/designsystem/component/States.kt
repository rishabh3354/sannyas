package com.sannyas.player.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sannyas.player.core.designsystem.theme.OshoColors
import com.sannyas.player.R
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens

/**
 * Empty state.
 *
 * The icon sits inside a soft coloured halo and scales in on first composition,
 * so an empty Library or a search with no results still looks like a designed
 * screen rather than a failure.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    // Animates only because the target flips after the first composition; a
    // constant target would settle immediately and never move.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "empty-scale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .glow(accent, radius = 28.dp, alpha = 0.30f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(40.dp),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        action?.let {
            Spacer(Modifier.height(8.dp))
            it()
        }
    }
}

/** Error state. Same visual language as [EmptyState] plus a retry action. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.CloudOff,
) {
    EmptyState(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier,
        accent = MaterialTheme.colorScheme.error,
        action = {
            SecondaryPillButton(
                text = stringResource(R.string.action_try_again),
                icon = Icons.Rounded.Refresh,
                onClick = onRetry,
            )
        },
    )
}

// ---------------------------------------------------------------- Shimmer ----

/**
 * Loading placeholders that mirror the real layout.
 *
 * Matching the eventual geometry — same card widths, same rhythm — means the
 * content lands without a visible reflow when the catalog resolves.
 */
@Composable
fun CarouselSkeleton(
    modifier: Modifier = Modifier,
    cardWidth: Dp = 168.dp,
    itemCount: Int = 4,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = modifier) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .width(160.dp)
                .height(22.dp)
                .shimmer(OshoShapeTokens.Pill, base, highlight),
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            userScrollEnabled = false,
        ) {
            items(itemCount) {
                Column(Modifier.width(cardWidth)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shimmer(OshoShapeTokens.Artwork, base, highlight),
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .shimmer(OshoShapeTokens.Pill, base, highlight),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp)
                            .shimmer(OshoShapeTokens.Pill, base, highlight),
                    )
                }
            }
        }
    }
}

/** Placeholder for a vertical list of track rows. */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 6,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).shimmer(OshoShapeTokens.Artwork, base, highlight))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .shimmer(OshoShapeTokens.Pill, base, highlight),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(11.dp)
                            .shimmer(OshoShapeTokens.Pill, base, highlight),
                    )
                }
            }
        }
    }
}

/**
 * A subtle animated bar shown while the current track is playing, used in track
 * lists to mark the active row.
 */
@Composable
fun NowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "eq")

    Row(
        modifier = modifier
            .height(16.dp)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Three bars on deliberately mismatched periods, so the pattern never
        // visibly loops the way an evenly-timed equaliser does.
        listOf(520, 700, 610).forEachIndexed { index, period ->
            val animated by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(period),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 130),
                ),
                label = "bar-$index",
            )
            val height = if (isPlaying) animated else RESTING_BAR_HEIGHT

            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(height)
                    .background(
                        Brush.verticalGradient(listOf(color, color.copy(alpha = 0.6f))),
                        OshoShapeTokens.Pill,
                    ),
            )
        }
    }
}

private const val RESTING_BAR_HEIGHT = 0.35f

/**
 * The wash revealed behind a row being swiped away.
 *
 * Drawn from both ends so the gesture works whichever way the thumb goes.
 * Callers must only place it while a swipe is actually in progress: the cards
 * in front of it are translucent, so anything painted at rest shows through.
 */
@Composable
fun SwipeToRemoveBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(OshoShapeTokens.Card)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
    ) {
        listOf(Alignment.CenterStart, Alignment.CenterEnd).forEach { alignment ->
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(alignment).size(22.dp),
            )
        }
    }
}
