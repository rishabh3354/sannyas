package com.sannyas.player.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import com.sannyas.player.core.designsystem.component.accentBrush
import com.sannyas.player.core.designsystem.component.softShadow
import com.sannyas.player.core.designsystem.theme.OshoColors
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sannyas.player.R
import com.sannyas.player.core.common.DurationFormat
import com.sannyas.player.core.common.seriesSubtitle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.sannyas.player.core.designsystem.component.verticalScrollbar
import com.sannyas.player.core.designsystem.component.Artwork
import com.sannyas.player.core.designsystem.component.CategoryCard
import com.sannyas.player.core.designsystem.component.AuroraBackground
import com.sannyas.player.core.designsystem.component.ContinueListeningCard
import com.sannyas.player.core.designsystem.component.DiscourseCard
import com.sannyas.player.core.designsystem.component.EmptyState
import com.sannyas.player.core.designsystem.component.ListSkeleton
import com.sannyas.player.core.designsystem.component.OshoIconButton
import com.sannyas.player.core.designsystem.component.SwipeToRemoveBackground
import com.sannyas.player.core.designsystem.component.pressScale
import com.sannyas.player.core.designsystem.component.rememberInteraction
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens
import com.sannyas.player.ui.components.TrackRow
import com.sannyas.player.ui.navigation.CollectionKind

/**
 * The list screen behind every category and "See all".
 *
 * Renders a two-column grid for series and a single-column list for talks or
 * bookmarks, chosen from whichever collection the ViewModel populated.
 */
@Composable
fun CollectionScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDiscourseClick: (String) -> Unit,
    onCollectionClick: (CollectionKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }

    val title = state.titleText ?: state.titleRes?.let { stringResource(it) }.orEmpty()
    val subtitle = state.subtitleText ?: state.subtitleRes?.let { res ->
        state.subtitleCount?.let { stringResource(res, it) } ?: stringResource(res)
    }.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.22f)

        Column(Modifier.fillMaxSize()) {
            CollectionTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                action = if (state.kind == CollectionKind.CONTINUE_LISTENING &&
                    state.continueItems.isNotEmpty()
                ) {
                    {
                        OshoIconButton(
                            icon = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.cd_clear_all_continue),
                            onClick = { confirmClearAll = true },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
            )

            when {
                state.isLoading -> ListSkeleton(Modifier.padding(top = 24.dp))

                state.isEmpty -> EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.library_empty),
                    message = stringResource(emptyMessageFor(state.kind)),
                )

                state.kind == CollectionKind.FAVOURITES -> FavouritesContent(
                    state = state,
                    contentPadding = contentPadding,
                    onDiscourseClick = onDiscourseClick,
                    onTrackClick = viewModel::playTrack,
                )

                state.languageChoices.isNotEmpty() -> LanguageChoiceGrid(
                    state = state,
                    contentPadding = contentPadding,
                    onChoose = onCollectionClick,
                )

                state.discourses.isNotEmpty() -> SeriesGrid(
                    state = state,
                    contentPadding = contentPadding,
                    onDiscourseClick = onDiscourseClick,
                    onTrackClick = viewModel::playTrack,
                )

                state.continueItems.isNotEmpty() -> ContinueList(
                    state = state,
                    contentPadding = contentPadding,
                    onResume = viewModel::resume,
                    onRemove = viewModel::removeContinueItem,
                )

                else -> TrackList(
                    state = state,
                    contentPadding = contentPadding,
                    onClick = viewModel::playTrack,
                )
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = {
                Text(
                    text = stringResource(R.string.continue_clear_all_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.continue_clear_all_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllContinue()
                    confirmClearAll = false
                }) {
                    Text(
                        text = stringResource(R.string.action_clear_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }
}

/**
 * Favourites.
 *
 * Series run as one horizontal rail, exactly as they do on Home, so the shelf
 * reads as a continuation of the app rather than as a different kind of list.
 * The saved talks follow underneath — a one-hour discourse and the fifty-part
 * set it came from are not peers, and stacking them in one grid said they were.
 */
@Composable
private fun FavouritesContent(
    state: CollectionUiState,
    contentPadding: PaddingValues,
    onDiscourseClick: (String) -> Unit,
    onTrackClick: (TrackListItem) -> Unit,
) {
    val scrollState0 = rememberLazyListState()

    LazyColumn(
        state = scrollState0,
        modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState0),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.discourses.isNotEmpty()) {
            item(key = "series-rail") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(state.discourses, key = { it.id }) { discourse ->
                        DiscourseCard(
                            title = discourse.title,
                            subtitle = seriesSubtitle(
                                discourse.trackCount,
                                discourse.durationSeconds,
                            ),
                            imageUrl = discourse.image,
                            onClick = { onDiscourseClick(discourse.id) },
                        )
                    }
                }
            }
        }

        if (state.tracks.isNotEmpty()) {
            item(key = "talks-header") {
                Text(
                    text = stringResource(R.string.detail_talks),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .semantics { heading() },
                )
            }

            items(state.tracks, key = { it.track.id }) { item ->
                TrackRow(
                    track = item.track,
                    isCurrent = state.currentTrackId == item.track.id,
                    isPlaying = state.isPlaying && state.currentTrackId == item.track.id,
                    onClick = { onTrackClick(item) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/**
 * "All Series" asks which language first.
 *
 * Two tiles rather than one list with headings: nobody wants to thumb past 175
 * English sets to reach the Hindi ones, and the choice is the first thing a
 * listener has already made in their head.
 *
 * Side by side and squarish, each carrying a large letter from its own script.
 * The glyph does the recognising before any reading happens, which matters when
 * one of the two options is in a script the reader may not use.
 */
@Composable
private fun LanguageChoiceGrid(
    state: CollectionUiState,
    contentPadding: PaddingValues,
    onChoose: (CollectionKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = contentPadding.calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.languageChoices.forEach { choice ->
            LanguageTile(
                name = stringResource(choice.titleRes),
                glyph = if (choice.kind == CollectionKind.ALL_SERIES_HINDI) "अ" else "A",
                count = choice.count,
                onClick = { onChoose(choice.kind) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LanguageTile(
    name: String,
    glyph: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberInteraction()
    val accent = remember(name) { OshoColors.accentFor(name) }
    val label = pluralStringResource(R.plurals.series_count, count, count)
    val description = stringResource(R.string.cd_item_detail, name, label)

    Box(
        modifier = modifier
            .aspectRatio(0.86f)
            .pressScale(interaction)
            .softShadow(OshoShapeTokens.Card, elevation = 16.dp)
            .clip(OshoShapeTokens.Card)
            .background(accentBrush(accent))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.cd_browse, name),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        // Oversized and half-transparent: a texture the tile is built on rather
        // than a character to be read.
        Text(
            text = glyph,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
            color = Color.White.copy(alpha = 0.22f),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp)
                .clearAndSetSemantics { },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SeriesGrid(
    state: CollectionUiState,
    contentPadding: PaddingValues,
    onDiscourseClick: (String) -> Unit,
    onTrackClick: (TrackListItem) -> Unit,
) {
    val scrollState = rememberLazyGridState()

    LazyVerticalGrid(
        state = scrollState,
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(state.discourses, key = { it.id }) { discourse ->
            DiscourseCard(
                title = discourse.title,
                subtitle = seriesSubtitle(discourse.trackCount, discourse.durationSeconds),
                imageUrl = discourse.image,
                onClick = { onDiscourseClick(discourse.id) },
                // Let the grid decide the width instead of the card's default.
                width = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Favourites holds both kinds. The talks follow the series under their
        // own heading rather than being mixed in, because a one-hour discourse
        // and the fifty-part set it came from are not peers.
        if (state.tracks.isNotEmpty()) {
            item(key = "talks-header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.detail_talks),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }

            items(
                items = state.tracks,
                key = { "talk-" + it.track.id },
                span = { GridItemSpan(maxLineSpan) },
            ) { item ->
                TrackRow(
                    track = item.track,
                    isCurrent = state.currentTrackId == item.track.id,
                    isPlaying = state.isPlaying && state.currentTrackId == item.track.id,
                    onClick = { onTrackClick(item) },
                )
            }
        }
    }
}

@Composable
private fun ContinueList(
    state: CollectionUiState,
    contentPadding: PaddingValues,
    onResume: (com.sannyas.player.ui.home.ContinueItem) -> Unit,
    onRemove: (com.sannyas.player.ui.home.ContinueItem) -> Unit,
) {
    val scrollState1 = rememberLazyListState()

    LazyColumn(
        state = scrollState1,
        modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState1),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.continueItems, key = { it.track.id }) { item ->
            val removeLabel = stringResource(R.string.cd_remove_from_continue, item.track.title)
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    val dismissed = value != SwipeToDismissBoxValue.Settled
                    if (dismissed) onRemove(item)
                    dismissed
                },
            )

            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.animateItem(),
                backgroundContent = {
                    // Only while the row is actually moving: the card in front
                    // is deliberately translucent, so anything painted here at
                    // rest shows straight through it.
                    if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                        SwipeToRemoveBackground()
                    }
                },
            ) {
                ContinueListeningCard(
                    title = item.track.title,
                    seriesTitle = item.discourse.title,
                    imageUrl = item.discourse.image,
                    positionMs = item.positionMs,
                    durationMs = item.durationMs,
                    onClick = { onResume(item) },
                    width = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            // Swiping is not reachable by a screen reader, so the
                            // same action is offered explicitly.
                            customActions = listOf(
                                CustomAccessibilityAction(removeLabel) {
                                    onRemove(item)
                                    true
                                },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    state: CollectionUiState,
    contentPadding: PaddingValues,
    onClick: (TrackListItem) -> Unit,
) {
    val scrollState2 = rememberLazyListState()

    LazyColumn(
        state = scrollState2,
        modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState2),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(state.tracks, key = { it.track.id }) { item ->
            TrackRow(
                track = item.track,
                isCurrent = state.currentTrackId == item.track.id,
                isPlaying = state.isPlaying && state.currentTrackId == item.track.id,
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
private fun CollectionTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OshoIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/**
 * Empty copy that names the shelf, so the message is never generic.
 *
 * Keyed off the collection itself rather than off its heading: the heading is
 * now a translated resource, and matching on its words only ever worked in
 * English.
 */
@StringRes
private fun emptyMessageFor(kind: CollectionKind?): Int = when (kind) {
    CollectionKind.FAVOURITES -> R.string.collection_empty_favourites
    CollectionKind.HISTORY -> R.string.collection_empty_history
    CollectionKind.CONTINUE_LISTENING -> R.string.collection_empty_continue
    else -> R.string.collection_empty_generic
}
