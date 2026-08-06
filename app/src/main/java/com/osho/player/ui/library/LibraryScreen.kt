package com.osho.player.ui.library

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osho.player.R
import com.osho.player.core.designsystem.component.AuroraBackground
import com.osho.player.core.designsystem.component.verticalScrollbar
import com.osho.player.core.designsystem.component.ContinueListeningCard
import com.osho.player.core.designsystem.component.DiscourseCard
import com.osho.player.core.designsystem.component.SectionHeader
import com.osho.player.core.designsystem.component.pressScale
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.core.common.seriesSubtitle
import com.osho.player.ui.navigation.CollectionKind

/**
 * Library.
 *
 * A set of shelves rather than a single list: the two collections with visual
 * content — Continue Listening and Favourites — get real carousels, while the
 * rest are compact navigation rows carrying their own counts.
 */
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onDiscourseClick: (String) -> Unit,
    onCollectionClick: (CollectionKind) -> Unit,
    onDownloadsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.25f)

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                        .semantics { heading() },
                )
            }

            if (state.continueListening.isNotEmpty()) {
                item(key = "continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.home_continue),
                            onSeeAll = { onCollectionClick(CollectionKind.CONTINUE_LISTENING) },
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) {
                            items(state.continueListening, key = { it.track.id }) { item ->
                                ContinueListeningCard(
                                    title = item.track.title,
                                    seriesTitle = item.discourse.title,
                                    imageUrl = item.discourse.image,
                                    positionMs = item.positionMs,
                                    durationMs = item.durationMs,
                                    onClick = { viewModel.resume(item) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "shelves") {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // One shelf for both kinds of favourite: singling out a talk
                    // and saving a whole series are the same gesture to a
                    // listener, and two near-identical rows read as a mistake.
                    ShelfRow(
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.library_favourites),
                        subtitle = stringResource(R.string.library_favourites_subtitle),
                        onClick = { onCollectionClick(CollectionKind.FAVOURITES) },
                    )
                    ShelfRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = stringResource(R.string.playlists_title),
                        subtitle = stringResource(R.string.playlists_subtitle),
                        onClick = onPlaylistsClick,
                    )
                    ShelfRow(
                        icon = Icons.Rounded.DownloadDone,
                        title = stringResource(R.string.library_downloads),
                        subtitle = countLabel(state.downloadedCount, R.plurals.talks_offline_count),
                        onClick = onDownloadsClick,
                    )
                    ShelfRow(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.library_history),
                        subtitle = countLabel(state.historyCount, R.plurals.talks_played_count),
                        onClick = { onCollectionClick(CollectionKind.HISTORY) },
                    )
                    ShelfRow(
                        icon = Icons.Rounded.PlayCircle,
                        title = stringResource(R.string.library_all_series),
                        subtitle = stringResource(R.string.library_all_series_subtitle),
                        onClick = { onCollectionClick(CollectionKind.ALL_SERIES) },
                    )
                }
            }

            if (state.favourites.isNotEmpty()) {
                item(key = "favourites") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.library_favourites),
                            subtitle = stringResource(R.string.library_favourites_subtitle),
                            onSeeAll = { onCollectionClick(CollectionKind.FAVOURITES) },
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) {
                            items(state.favourites, key = { it.id }) { discourse ->
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
            }
        }
    }
}

@Composable
private fun ShelfRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val description = stringResource(R.string.cd_item_detail, title, subtitle)
    val openLabel = stringResource(R.string.cd_open, title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 64.dp)
            .pressScale(interaction, pressedScale = 0.99f)
            .clip(OshoShapeTokens.Card)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = openLabel,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(OshoShapeTokens.Artwork)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** "12 talks saved offline", or an inviting line when the shelf is still empty. */
@Composable
private fun countLabel(count: Int, @PluralsRes plural: Int): String = if (count == 0) {
    stringResource(R.string.library_empty)
} else {
    pluralStringResource(plural, count, count)
}
