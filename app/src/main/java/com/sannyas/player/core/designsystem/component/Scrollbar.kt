package com.sannyas.player.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A scroll position indicator for the long lists.
 *
 * The catalogue runs to hundreds of series and thousands of talks, and a list
 * that long with no indicator gives no sense of how far in you are or how much
 * is left. Deliberately an indicator rather than a control: it fades in while
 * the finger is moving and fades out afterwards, so it never competes with the
 * artwork at rest and never becomes a second thing to aim at.
 *
 * Position is derived from item indices rather than pixels. Items here vary in
 * height (cards, rows, headers), so an exact pixel mapping would need every
 * item measured; index fractions are a close enough approximation for a hint
 * and cost nothing.
 */
private fun Modifier.scrollbar(
    totalItems: Int,
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    isScrolling: Boolean,
    color: Color,
    width: Dp,
    minThumbHeight: Dp,
): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 120 else 600),
        label = "scrollbar",
    )

    drawWithContent {
        drawContent()

        // Nothing to say when it all fits, or when the finger has been still
        // long enough for the bar to have faded out.
        if (alpha <= 0.01f || totalItems == 0 || visibleItemCount >= totalItems) {
            return@drawWithContent
        }

        val trackHeight = size.height
        val thumbHeight = maxOf(
            minThumbHeight.toPx(),
            trackHeight * (visibleItemCount.toFloat() / totalItems),
        )
        val scrollable = (totalItems - visibleItemCount).coerceAtLeast(1)
        val progress = (firstVisibleIndex.toFloat() / scrollable).coerceIn(0f, 1f)

        val barWidth = width.toPx()
        drawRoundRect(
            color = color.copy(alpha = color.alpha * alpha),
            topLeft = Offset(
                x = size.width - barWidth,
                y = progress * (trackHeight - thumbHeight),
            ),
            size = Size(barWidth, thumbHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
}

@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color = defaultScrollbarColor(),
    width: Dp = 3.dp,
    minThumbHeight: Dp = 28.dp,
): Modifier = scrollbar(
    totalItems = state.layoutInfo.totalItemsCount,
    firstVisibleIndex = state.firstVisibleItemIndex,
    visibleItemCount = state.layoutInfo.visibleItemsInfo.size,
    isScrolling = state.isScrollInProgress,
    color = color,
    width = width,
    minThumbHeight = minThumbHeight,
)

@Composable
fun Modifier.verticalScrollbar(
    state: LazyGridState,
    color: Color = defaultScrollbarColor(),
    width: Dp = 3.dp,
    minThumbHeight: Dp = 28.dp,
): Modifier = scrollbar(
    totalItems = state.layoutInfo.totalItemsCount,
    firstVisibleIndex = state.firstVisibleItemIndex,
    visibleItemCount = state.layoutInfo.visibleItemsInfo.size,
    isScrolling = state.isScrollInProgress,
    color = color,
    width = width,
    minThumbHeight = minThumbHeight,
)

@Composable
private fun defaultScrollbarColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
