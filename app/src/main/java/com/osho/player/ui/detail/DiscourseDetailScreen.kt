package com.osho.player.ui.detail

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osho.player.R
import com.osho.player.core.common.formatBytes
import com.osho.player.core.common.seriesSubtitle
import com.osho.player.core.common.talkCount
import com.osho.player.core.designsystem.component.Artwork
import com.osho.player.core.designsystem.component.FavouriteButton
import com.osho.player.core.designsystem.component.ListSkeleton
import com.osho.player.core.designsystem.component.OshoIconButton
import com.osho.player.core.designsystem.component.PrimaryPillButton
import com.osho.player.core.designsystem.component.SecondaryPillButton
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.component.softShadow
import com.osho.player.core.designsystem.theme.OshoColors
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.ui.components.TrackRow

/**
 * Series detail.
 *
 * A parallax hero: the blurred artwork wash sits behind the whole header while
 * the sharp cover scales and fades as the list scrolls, and a solid top bar
 * fades in exactly when the title would otherwise collide with it.
 */
@Composable
fun DiscourseDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscourseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val shareChooser = stringResource(R.string.share_series_chooser)

    var headerHeightPx by remember { mutableStateOf(1) }
    var confirmDownload by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }

    // 0f at rest, 1f once the header has scrolled fully away.
    val collapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    val discourse = state.discourse
    val accent = remember(discourse?.id) { OshoColors.accentFor(discourse?.id ?: "osho") }

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient wash keyed to the artwork, held behind everything.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .alpha(1f - collapseFraction * 0.6f),
        ) {
            Artwork(
                imageUrl = discourse?.image,
                contentDescription = null,
                fallbackKey = discourse?.id.orEmpty(),
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(52.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        // Must land on the same colour the root paints, not on
                        // `surface`: under a dynamic scheme the two differ just
                        // enough to draw a hard horizontal seam where this
                        // 420dp box ends, halfway down the screen.
                        Brush.verticalGradient(
                            listOf(
                                accent.second.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        when {
            state.isLoading -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Spacer(Modifier.height(80.dp))
                ListSkeleton()
            }

            discourse == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.detail_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 32.dp,
                ),
            ) {
                item(key = "header") {
                    DetailHeader(
                        state = state,
                        collapseFraction = collapseFraction,
                        onPlayAll = viewModel::playAll,
                        onShuffle = viewModel::shuffle,
                        onToggleFavourite = viewModel::toggleFavourite,
                        onDownload = { confirmDownload = true },
                        onRemoveDownloads = { confirmRemoveDownloads = true },
                        onPauseDownloads = viewModel::pauseDownloads,
                        onResumeDownloads = viewModel::resumeDownloads,
                        onShare = { context.share(viewModel.shareText(context), shareChooser) },
                        onHeightMeasured = { headerHeightPx = it.coerceAtLeast(1) },
                    )
                }

                item(key = "tracks-header") {
                    Text(
                        text = stringResource(R.string.detail_talks),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
                            .semantics { heading() },
                    )
                }

                items(discourse.tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = state.currentTrackId == track.id,
                        isPlaying = state.isPlaying && state.currentTrackId == track.id,
                        onClick = { viewModel.playTrack(track) },
                        resumeFraction = state.progressByTrack[track.id]
                            ?.takeIf { it.isMeaningful }?.fraction ?: 0f,
                        downloadStatus = state.downloadByTrack[track.id],
                        onDownloadClick = { viewModel.toggleTrackDownload(track) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }

        CollapsingTopBar(
            title = discourse?.title.orEmpty(),
            collapseFraction = collapseFraction,
            onBack = onBack,
        )
    }

    if (confirmDownload && discourse != null) {
        DownloadConfirmDialog(
            title = stringResource(R.string.detail_download_title, discourse.title),
            message = stringResource(
                R.string.detail_download_message,
                talkCount(discourse.trackCount),
                viewModel.estimatedSeriesBytes().formatBytes(),
            ),
            confirmLabel = stringResource(R.string.detail_download_confirm),
            onConfirm = {
                viewModel.downloadSeries()
                confirmDownload = false
            },
            onDismiss = { confirmDownload = false },
        )
    }

    if (confirmRemoveDownloads && discourse != null) {
        DownloadConfirmDialog(
            title = stringResource(
                if (state.isFullyDownloaded) R.string.detail_remove_title else R.string.detail_stop_title,
            ),
            message = if (state.isFullyDownloaded) {
                stringResource(R.string.detail_remove_message, discourse.title)
            } else {
                stringResource(R.string.detail_stop_message)
            },
            confirmLabel = stringResource(
                if (state.isFullyDownloaded) R.string.action_remove else R.string.detail_stop_confirm,
            ),
            destructive = true,
            onConfirm = {
                viewModel.cancelSeriesDownload()
                confirmRemoveDownloads = false
            },
            onDismiss = { confirmRemoveDownloads = false },
        )
    }
}

/** Confirmation used before any bulk download or deletion on this screen. */
@Composable
private fun DownloadConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun DetailHeader(
    state: DiscourseDetailUiState,
    collapseFraction: Float,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavourite: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownloads: () -> Unit,
    onPauseDownloads: () -> Unit,
    onResumeDownloads: () -> Unit,
    onShare: () -> Unit,
    onHeightMeasured: (Int) -> Unit,
) {
    val discourse = state.discourse ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .onGloballyPositioned { onHeightMeasured(it.size.height) }
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cover shrinks and fades as the list scrolls under it.
        Artwork(
            imageUrl = discourse.image,
            contentDescription = stringResource(R.string.cd_artwork_for, discourse.title),
            fallbackKey = discourse.id,
            fallbackLabel = discourse.title,
            shape = OshoShapeTokens.ArtworkLarge,
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .scale(1f - collapseFraction * 0.18f)
                .alpha(1f - collapseFraction)
                .softShadow(OshoShapeTokens.ArtworkLarge, elevation = 28.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = discourse.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .semantics { heading() },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.dot_separated, discourse.speaker, discourse.category),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = seriesSubtitle(discourse.trackCount, discourse.durationSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (discourse.description.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            ExpandableDescription(text = discourse.description)
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryPillButton(
                text = stringResource(R.string.action_play_all),
                icon = Icons.Rounded.PlayArrow,
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            SecondaryPillButton(
                text = stringResource(R.string.action_shuffle),
                icon = Icons.Rounded.Shuffle,
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FavouriteButton(
                isFavourite = state.isFavourite,
                onToggle = onToggleFavourite,
            )

            OshoIconButton(
                icon = when {
                    state.isFullyDownloaded -> Icons.Rounded.DownloadDone
                    state.downloadingCount > 0 -> Icons.Rounded.Downloading
                    else -> Icons.Rounded.Download
                },
                contentDescription = when {
                    state.isFullyDownloaded -> stringResource(R.string.cd_downloaded_remove_series)
                    state.downloadingCount > 0 ->
                        stringResource(R.string.cd_downloading_series, state.downloadingCount)

                    else -> stringResource(R.string.cd_download_series)
                },
                onClick = if (state.isFullyDownloaded || state.downloadingCount > 0) {
                    onRemoveDownloads
                } else {
                    onDownload
                },
                active = state.isFullyDownloaded,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Pause/resume only exists while there is work to pause.
            if (state.downloadingCount > 0) {
                OshoIconButton(
                    icon = if (state.downloadsPaused) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.Pause
                    },
                    contentDescription = stringResource(
                        if (state.downloadsPaused) {
                            R.string.cd_resume_downloads
                        } else {
                            R.string.cd_pause_downloads
                        },
                    ),
                    onClick = if (state.downloadsPaused) onResumeDownloads else onPauseDownloads,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OshoIconButton(
                icon = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.cd_share_series),
                onClick = onShare,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Description that clamps to three lines until tapped. */
@Composable
private fun ExpandableDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = rememberInteraction()
    val toggleLabel = stringResource(
        if (expanded) R.string.cd_collapse_description else R.string.cd_expand_description,
    )

    // No clip here: a 24dp corner radius over a full-width text block curves in
    // far enough to shave the first glyph of the top line and of "Show more".
    // The tap target needs no shaping, so the clip simply comes off.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = toggleLabel,
                onClick = { expanded = !expanded },
            )
            .padding(horizontal = 28.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                if (expanded) R.string.action_show_less else R.string.action_show_more,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Back button, plus a title that fades in once the hero has scrolled away. */
@Composable
private fun CollapsingTopBar(
    title: String,
    collapseFraction: Float,
    onBack: () -> Unit,
) {
    val barAlpha by animateFloatAsState(
        targetValue = if (collapseFraction > 0.6f) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "topbar-alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fully opaque once collapsed. At 94% the saturated "Play all" pill
            // stayed visible through the bar as the header scrolled under it.
            .background(MaterialTheme.colorScheme.surface.copy(alpha = barAlpha))
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OshoIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(barAlpha)
                .padding(end = 16.dp),
        )
    }
}

/** Opens the system share sheet; nothing leaves the device without the user choosing a target. */
private fun Context.share(text: String?, chooserTitle: String) {
    if (text.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}
