package com.sannyas.player.ui.radio

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sannyas.player.R
import com.sannyas.player.core.common.talkCount
import com.sannyas.player.core.designsystem.component.AuroraBackground
import com.sannyas.player.core.designsystem.component.verticalScrollbar
import com.sannyas.player.core.designsystem.component.CarouselSkeleton
import com.sannyas.player.core.designsystem.component.EmptyState
import com.sannyas.player.core.designsystem.component.NowPlayingIndicator
import com.sannyas.player.core.designsystem.component.SectionHeader
import com.sannyas.player.core.designsystem.component.StationCard
import com.sannyas.player.core.designsystem.component.accentBrush
import com.sannyas.player.core.designsystem.component.pressScale
import com.sannyas.player.core.designsystem.component.rememberInteraction
import com.sannyas.player.core.designsystem.component.softShadow
import com.sannyas.player.core.designsystem.theme.OshoColors
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens
import com.sannyas.player.domain.model.Station

/**
 * Radio.
 *
 * The whole-library station is the point of the screen — it is the one a
 * listener with no particular plan should be able to hit without reading
 * anything — so it gets a full-width hero, and the per-tradition stations sit
 * below it as a plain two-column grid.
 */
@Composable
fun RadioScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allLibraryName = stringResource(R.string.radio_all_station)
    val scrollState = rememberLazyGridState()

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.32f)

        when {
            state.isLoading -> RadioSkeleton(contentPadding)

            state.isEmpty -> EmptyState(
                icon = Icons.Rounded.Radio,
                title = stringResource(R.string.radio_empty_title),
                message = stringResource(R.string.radio_empty_message),
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LazyVerticalGrid(
                state = scrollState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 32.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(top = 24.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.radio_title),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.radio_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.allLibrary?.let { station ->
                    item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                        AllLibraryCard(
                            name = allLibraryName,
                            trackCount = station.trackCount,
                            onAir = state.playingStationId == station.id,
                            onClick = { viewModel.playStation(station, allLibraryName) },
                        )
                    }
                }

                if (state.categoryStations.isNotEmpty()) {
                    item(key = "stations-header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = stringResource(R.string.radio_stations),
                            subtitle = stringResource(R.string.radio_stations_subtitle),
                            // The grid already supplies the horizontal inset.
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalPadding = 0.dp,
                        )
                    }

                    items(state.categoryStations, key = { it.id }) { station ->
                        StationTile(
                            station = station,
                            onAir = state.playingStationId == station.id,
                            onClick = { viewModel.playStation(station, station.name) },
                        )
                    }
                }
            }
        }
    }
}

/** The full-width "just press play" card. */
@Composable
private fun AllLibraryCard(
    name: String,
    trackCount: Int,
    onAir: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val accent = OshoColors.accentFor(ALL_LIBRARY_ACCENT_KEY)
    val detail = stringResource(R.string.radio_all_station_detail)
    val description = stringResource(R.string.cd_station, name, detail)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
            .pressScale(interaction)
            .softShadow(OshoShapeTokens.HeroCard, elevation = 20.dp)
            .clip(OshoShapeTokens.HeroCard)
            .background(accentBrush(accent))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.cd_play_item, name),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Rounded.Podcasts,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .size(26.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onAir) {
                OnAirBadge()
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = talkCount(trackCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun StationTile(station: Station, onAir: Boolean, onClick: () -> Unit) {
    Box {
        StationCard(
            name = station.name,
            detail = talkCount(station.trackCount),
            onClick = onClick,
            width = null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onAir) {
            Box(Modifier.padding(start = 16.dp, top = 16.dp)) { OnAirBadge() }
        }
    }
}

/** Marks the station that is currently playing, so a second tap is never a guess. */
@Composable
private fun OnAirBadge() {
    Row(
        modifier = Modifier
            .clip(OshoShapeTokens.Pill)
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingIndicator(isPlaying = true, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.radio_on_air),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun RadioSkeleton(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 24.dp, bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        CarouselSkeleton()
        CarouselSkeleton()
    }
}

/** Fixed so the hero keeps one identity rather than borrowing a category's accent. */
private const val ALL_LIBRARY_ACCENT_KEY = "osho-radio"
