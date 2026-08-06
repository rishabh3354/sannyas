package com.osho.player.ui.downloads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
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
import com.osho.player.core.common.formatBytes
import com.osho.player.core.common.talkCount
import com.osho.player.core.designsystem.component.Artwork
import com.osho.player.core.designsystem.component.AuroraBackground
import com.osho.player.core.designsystem.component.verticalScrollbar
import com.osho.player.core.designsystem.component.EmptyState
import com.osho.player.core.designsystem.component.OshoIconButton
import com.osho.player.core.designsystem.component.glassSurface
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.ui.components.TrackRow

/**
 * Downloads.
 *
 * Grouped by series with a storage summary at the top. Deleting is always
 * confirmed — offline audio can be expensive to fetch again on mobile data,
 * so an accidental tap should never destroy it.
 */
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var confirmDeleteAll by remember { mutableStateOf(false) }
    var groupPendingDelete by remember { mutableStateOf<DownloadGroup?>(null) }
    val scrollState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.20f)

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OshoIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.downloads_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (state.groups.isNotEmpty()) {
                    OshoIconButton(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.cd_delete_all_downloads),
                        onClick = { confirmDeleteAll = true },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.isEmpty) {
                EmptyState(
                    icon = Icons.Rounded.DownloadForOffline,
                    title = stringResource(R.string.downloads_empty_title),
                    message = stringResource(R.string.downloads_empty_message),
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                )
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "storage") {
                        StorageSummary(
                            trackCount = state.trackCount,
                            seriesCount = state.groups.size,
                            bytes = state.storageUsedBytes,
                        )
                    }

                    state.groups.forEach { group ->
                        item(key = "group-${group.discourseId}") {
                            GroupHeader(
                                group = group,
                                onDelete = { groupPendingDelete = group },
                            )
                        }

                        items(group.tracks, key = { it.track.id }) { item ->
                            TrackRow(
                                track = item.track,
                                isCurrent = false,
                                isPlaying = false,
                                onClick = { viewModel.play(group, item) },
                                downloadStatus = item.status,
                                onDownloadClick = { viewModel.removeTrack(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.downloads_delete_all_title),
            message = stringResource(R.string.downloads_delete_all_message, state.trackCount),
            confirmLabel = stringResource(R.string.downloads_delete_all_confirm),
            onConfirm = {
                viewModel.removeAll()
                confirmDeleteAll = false
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }

    groupPendingDelete?.let { group ->
        ConfirmDialog(
            title = stringResource(R.string.downloads_delete_series_title, group.discourseTitle),
            message = stringResource(R.string.downloads_delete_series_message, group.tracks.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.removeGroup(group)
                groupPendingDelete = null
            },
            onDismiss = { groupPendingDelete = null },
        )
    }
}

@Composable
private fun StorageSummary(trackCount: Int, seriesCount: Int, bytes: Long) {
    val description = stringResource(
        R.string.cd_storage_summary,
        trackCount,
        seriesCount,
        bytes.formatBytes(),
    )
    val summary = stringResource(
        R.string.downloads_storage_summary,
        talkCount(trackCount),
        pluralStringResource(R.plurals.series_count, seriesCount, seriesCount),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(OshoShapeTokens.Card)
            .padding(20.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bytes.formatBytes(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupHeader(group: DownloadGroup, onDelete: () -> Unit) {
    val ready = stringResource(R.string.downloads_group_ready, group.completedCount, group.tracks.size)
    val inProgress = stringResource(R.string.downloads_group_downloading, group.inProgressCount)
    val deleteLabel = stringResource(R.string.cd_delete_series_downloads, group.discourseTitle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            imageUrl = group.artworkUrl,
            contentDescription = null,
            fallbackKey = group.discourseId,
            fallbackLabel = group.discourseTitle,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = group.discourseTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = listOfNotNull(
                    ready,
                    inProgress.takeIf { group.inProgressCount > 0 },
                    group.totalBytes.formatBytes(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OshoIconButton(
            icon = Icons.Rounded.DeleteSweep,
            contentDescription = deleteLabel,
            onClick = onDelete,
            iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Series-level progress, so a part-downloaded series is legible at a glance.
    if (group.inProgressCount > 0) {
        LinearProgressIndicator(
            progress = { group.completedCount.toFloat() / group.tracks.size.coerceAtLeast(1) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(3.dp)
                .clip(OshoShapeTokens.Pill),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    )
}
