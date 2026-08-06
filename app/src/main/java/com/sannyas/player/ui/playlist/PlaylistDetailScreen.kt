package com.sannyas.player.ui.playlist

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import com.sannyas.player.core.designsystem.component.EmptyState
import com.sannyas.player.core.designsystem.component.ListSkeleton
import com.sannyas.player.core.designsystem.component.OshoIconButton
import com.sannyas.player.core.designsystem.component.PrimaryPillButton
import com.sannyas.player.core.designsystem.component.SecondaryPillButton
import com.sannyas.player.core.designsystem.component.SwipeToRemoveBackground
import com.sannyas.player.ui.components.TrackRow
import com.sannyas.player.ui.player.PlaylistNameDialog

/**
 * One playlist, in its own order.
 *
 * Playing from here hands the whole playlist to the player as the queue, which
 * is the difference between a playlist and a bookmark folder.
 */
@Composable
fun PlaylistDetailScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    val name = state.playlist?.name.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize(), alpha = 0.22f)

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
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
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = talkCount(state.tracks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    OshoIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_playlist_options, name),
                        onClick = { menuOpen = true },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_rename)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null)
                            },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }

            when {
                state.isLoading -> ListSkeleton(Modifier.padding(top = 24.dp))

                state.isEmpty -> EmptyState(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.playlist_empty_title),
                    message = stringResource(R.string.playlist_empty_message),
                )

                else -> LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize().verticalScrollbar(scrollState),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PrimaryPillButton(
                                text = stringResource(R.string.action_play_all),
                                icon = Icons.Rounded.PlayArrow,
                                onClick = { viewModel.play() },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryPillButton(
                                text = stringResource(R.string.action_shuffle),
                                icon = Icons.Rounded.Shuffle,
                                onClick = viewModel::shuffle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    itemsIndexed(state.tracks, key = { _, item -> item.track.id }) { index, item ->
                        val removeLabel =
                            stringResource(R.string.cd_remove_from_playlist, item.track.title)
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                val dismissed = value != SwipeToDismissBoxValue.Settled
                                if (dismissed) viewModel.remove(item)
                                dismissed
                            },
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.animateItem(),
                            backgroundContent = {
                                if (dismissState.dismissDirection !=
                                    SwipeToDismissBoxValue.Settled
                                ) {
                                    SwipeToRemoveBackground()
                                }
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TrackRow(
                                    track = item.track,
                                    isCurrent = state.currentTrackId == item.track.id,
                                    isPlaying = state.isPlaying &&
                                        state.currentTrackId == item.track.id,
                                    onClick = { viewModel.play(index) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction(removeLabel) {
                                                    viewModel.remove(item)
                                                    true
                                                },
                                            )
                                        },
                                )
                                ReorderControls(
                                    title = item.track.title,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < state.tracks.lastIndex,
                                    onUp = { viewModel.move(item, -1) },
                                    onDown = { viewModel.move(item, 1) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            initial = name,
            confirmLabel = stringResource(R.string.playlist_rename),
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    text = stringResource(R.string.playlist_delete_title, name),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.playlist_delete_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    confirmDelete = false
                    onBack()
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }
}

/**
 * Side by side rather than stacked.
 *
 * Two 48dp targets in a column set the row's height to 96dp, which left every
 * talk floating in a field of empty space. Laid out horizontally the row keeps
 * a list rhythm and both targets stay full size.
 */
@Composable
private fun ReorderControls(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row {
        OshoIconButton(
            icon = Icons.Rounded.KeyboardArrowUp,
            contentDescription = stringResource(R.string.cd_move_up, title),
            onClick = onUp,
            enabled = canMoveUp,
            iconSize = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OshoIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_move_down, title),
            onClick = onDown,
            enabled = canMoveDown,
            iconSize = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
