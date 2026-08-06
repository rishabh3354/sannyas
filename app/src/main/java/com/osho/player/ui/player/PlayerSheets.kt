package com.osho.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osho.player.R
import com.osho.player.core.common.DurationFormat
import com.osho.player.core.common.compactDuration
import com.osho.player.core.common.talkCount
import com.osho.player.core.common.spokenDuration
import com.osho.player.core.designsystem.component.Artwork
import com.osho.player.core.designsystem.component.NowPlayingIndicator
import com.osho.player.core.designsystem.component.OshoIconButton
import com.osho.player.core.designsystem.component.pressScale
import com.osho.player.core.designsystem.component.rememberInteraction
import com.osho.player.core.designsystem.theme.OshoShapeTokens
import com.osho.player.domain.model.Playlist
import com.osho.player.domain.model.PlayerState
import com.osho.player.domain.model.SleepTimer

/** Shared chrome so every player sheet has the same header and background. */
@Composable
private fun PlayerSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = OshoShapeTokens.Sheet,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(OshoShapeTokens.Pill)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OshoIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    iconSize = 22.dp,
                )
            }

            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * The up-next queue.
 *
 * Capped in height so the sheet never covers the whole screen, and scrolled to
 * nothing special on open — the current item is highlighted in place rather
 * than auto-scrolled, which keeps the user's sense of position in the series.
 */
@Composable
fun QueueSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    PlayerSheet(
        title = stringResource(R.string.player_up_next),
        subtitle = stringResource(R.string.player_queue_subtitle, state.queue.size),
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        ) {
            itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
                val isCurrent = index == state.queueIndex
                val interaction = rememberInteraction()
                val playLabel = stringResource(R.string.cd_play_item, track.title)
                val removeLabel = stringResource(R.string.cd_remove_from_queue, track.title)
                val nowPlaying = stringResource(R.string.cd_now_playing)
                val spoken = spokenDuration(track.durationSeconds)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(interaction, pressedScale = 0.99f)
                        .clip(OshoShapeTokens.Card)
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClickLabel = playLabel,
                            onClick = { onPlayIndex(index) },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                if (isCurrent) append(nowPlaying).append(' ')
                                append(track.title)
                                append(", ")
                                append(spoken)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (isCurrent) {
                            NowPlayingIndicator(isPlaying = state.isPlaying)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Artwork(
                        imageUrl = track.artworkUrl,
                        contentDescription = null,
                        fallbackKey = track.discourseId,
                        fallbackLabel = track.discourseTitle,
                        modifier = Modifier.size(44.dp),
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = compactDuration(track.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Removing the playing item would restart playback, so it is
                    // only offered for the rest of the queue.
                    if (!isCurrent) {
                        OshoIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = removeLabel,
                            onClick = { onRemove(index) },
                            iconSize = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Speeds tuned for spoken word, where small steps matter more than for music. */
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlaybackSpeedSheet(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSheet(
        title = stringResource(R.string.player_speed),
        subtitle = stringResource(R.string.player_speed_subtitle),
        onDismiss = onDismiss,
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SPEED_OPTIONS.forEach { speed ->
                SelectableChip(
                    label = formatSpeed(speed),
                    selected = kotlin.math.abs(speed - current) < 0.01f,
                    onClick = {
                        onSelect(speed)
                        onDismiss()
                    },
                )
            }
        }
    }
}

private val TIMER_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)

@Composable
fun SleepTimerSheet(
    current: SleepTimer,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitle = when (current) {
        is SleepTimer.Duration -> if (current.isFading) {
            stringResource(R.string.player_sleep_fading, DurationFormat.clock(current.remainingMs))
        } else {
            stringResource(R.string.player_sleep_remaining, DurationFormat.clock(current.remainingMs))
        }

        SleepTimer.EndOfTrack -> stringResource(R.string.player_sleep_stopping)
        SleepTimer.Off -> stringResource(R.string.player_sleep_subtitle)
    }

    PlayerSheet(
        title = stringResource(R.string.player_sleep_timer),
        subtitle = subtitle,
        onDismiss = onDismiss,
    ) {
        // Promoted out of the chip row. "Finish this talk" is the choice most
        // people actually want at night — a fixed number of minutes is a guess
        // about where the talk ends — and as one pill among eight it read as
        // just another duration.
        SleepOptionRow(
            title = stringResource(R.string.player_sleep_end_of_talk_title),
            detail = stringResource(R.string.player_sleep_end_of_talk_detail),
            selected = current is SleepTimer.EndOfTrack,
            onClick = {
                onSelectEndOfTrack()
                onDismiss()
            },
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.player_sleep_after),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TIMER_OPTIONS.forEach { minutes ->
                SelectableChip(
                    label = stringResource(R.string.duration_minutes, minutes),
                    selected = current is SleepTimer.Duration &&
                        current.totalMs == minutes * 60_000L,
                    onClick = {
                        onSelectMinutes(minutes)
                        onDismiss()
                    },
                )
            }

            if (current != SleepTimer.Off) {
                SelectableChip(
                    label = stringResource(R.string.player_sleep_off),
                    selected = false,
                    onClick = {
                        onCancel()
                        onDismiss()
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // States the fade plainly rather than leaving it to be discovered at
        // the one moment nobody is paying attention.
        Text(
            text = stringResource(R.string.player_sleep_fade_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()

    Box(
        modifier = Modifier
            .sizeIn(minHeight = 44.dp)
            .pressScale(interaction)
            .clip(OshoShapeTokens.Pill)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 11.dp)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * A full-width, first-class option in a sheet otherwise made of chips.
 *
 * Used for "finish this talk", which is a different kind of answer from a
 * number of minutes and should not look like one.
 */
@Composable
private fun SleepOptionRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .sizeIn(minHeight = 64.dp)
            .pressScale(interaction, pressedScale = 0.99f)
            .clip(OshoShapeTokens.Card)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * "Add to playlist".
 *
 * Membership is a toggle rather than an add-only action, so the sheet doubles
 * as the place to take a talk back out — which is where anyone will look for it
 * having just put it in by accident.
 */
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    containing: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }

    PlayerSheet(title = stringResource(R.string.playlist_add_to), onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            playlists.forEach { playlist ->
                val checked = playlist.id in containing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp)
                        .clickable(role = Role.Checkbox) { onToggle(playlist.id) }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .semantics { this.selected = checked },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (checked) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = talkCount(playlist.trackCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp)
                    .clickable(role = Role.Button) { naming = true }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.playlist_new),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (naming) {
        PlaylistNameDialog(
            initial = "",
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = {
                onCreate(it)
                naming = false
                onDismiss()
            },
            onDismiss = { naming = false },
        )
    }
}

/** Shared by "new playlist" and "rename". */
@Composable
fun PlaylistNameDialog(
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.playlist_name_hint),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    )
}
