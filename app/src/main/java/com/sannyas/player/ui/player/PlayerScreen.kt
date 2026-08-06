package com.sannyas.player.ui.player

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sannyas.player.R
import com.sannyas.player.core.common.DurationFormat
import com.sannyas.player.core.common.spokenDuration
import com.sannyas.player.core.designsystem.component.Artwork
import com.sannyas.player.core.designsystem.component.AuroraBackground
import com.sannyas.player.core.designsystem.component.OshoIconButton
import com.sannyas.player.core.designsystem.component.PlayPauseButton
import com.sannyas.player.core.designsystem.component.glow
import com.sannyas.player.core.designsystem.component.softShadow
import com.sannyas.player.core.designsystem.theme.OshoColors
import com.sannyas.player.core.designsystem.theme.OshoShapeTokens
import com.sannyas.player.playback.PlaybackRecovery
import com.sannyas.player.domain.model.DownloadState
import com.sannyas.player.domain.model.PlayerState
import com.sannyas.player.domain.model.RepeatMode
import com.sannyas.player.domain.model.SleepTimer

/**
 * The full player.
 *
 * Layout adapts to orientation: portrait stacks artwork over controls, landscape
 * places artwork beside them so the transport row never gets squeezed. Both
 * share the same control composables, so behaviour cannot drift between them.
 *
 * The background is built from the current artwork — blurred, darkened and
 * overlaid with an aurora tinted by the series accent — which is what makes the
 * screen feel bespoke to whatever is playing.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val isFavourite by viewModel.isCurrentFavourite.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.currentDownloadStatus.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val downloadsPaused by viewModel.downloadsPaused.collectAsStateWithLifecycle()
    val dimWhenIdle by viewModel.dimPlayerWhenIdle.collectAsStateWithLifecycle()
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistsWithTrack by viewModel.playlistIdsWithCurrent.collectAsStateWithLifecycle()

    val track = state.currentTrack
    val context = LocalContext.current
    val shareChooser = stringResource(R.string.player_share_chooser)

    var showQueue by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var confirmRemoveDownload by remember { mutableStateOf(false) }
    var showDownloadControls by remember { mutableStateOf(false) }

    val accent = remember(track?.discourseId) {
        OshoColors.accentFor(track?.discourseId ?: "osho")
    }

    val isLandscape = LocalConfiguration.current.screenWidthDp >
        LocalConfiguration.current.screenHeightDp

    NightDim(
        enabled = dimWhenIdle,
        isPlaying = state.isPlaying,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                // Swipe down anywhere on the background to dismiss, matching the
                // gesture that opened it.
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > DISMISS_DRAG_THRESHOLD) onCollapse()
                    }
                },
        ) {
            PlayerBackground(artworkUrl = track?.artworkUrl, accent = accent)

            if (track == null) {
                // Only reachable if the session is torn down while expanded.
                Text(
                    text = stringResource(R.string.player_nothing),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                PlayerTopBar(
                    stationName = state.stationName,
                    seriesTitle = track.discourseTitle,
                    isReconnecting = recovery.isReconnecting,
                    sourceUnreachable = recovery.outage ==
                        PlaybackRecovery.Outage.SOURCE_UNREACHABLE,
                    sleepTimer = sleepTimer,
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        PlayerArtwork(
                            artworkUrl = track.artworkUrl,
                            fallbackKey = track.discourseId,
                            fallbackLabel = track.discourseTitle,
                            isPlaying = state.isPlaying,
                            accent = accent.first,
                            modifier = Modifier
                                .fillMaxHeight(0.82f)
                                .aspectRatio(1f),
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            TrackHeadline(
                                title = track.title,
                                speaker = track.speaker,
                                seriesTitle = track.discourseTitle,
                                centered = false,
                            )
                            SeekBar(state = state, accent = accent.first, onSeek = viewModel::seekTo)
                            TransportRow(state = state, viewModel = viewModel, accent = accent.first)
                            SecondaryActions(
                                isFavourite = isFavourite,
                                downloadState = downloadStatus?.state,
                                speed = state.speed,
                                sleepTimer = sleepTimer,
                                onFavourite = viewModel::toggleFavourite,
                                onDownload = {
                                    when (downloadStatus?.state) {
                                        // Deleting an offline copy is destructive; ask first.
                                        DownloadState.DOWNLOADED -> confirmRemoveDownload = true
                                        // Mid-download the useful actions are pause and stop,
                                        // not "download again".
                                        DownloadState.DOWNLOADING,
                                        DownloadState.QUEUED,
                                        -> showDownloadControls = true
                                        else -> viewModel.toggleDownload()
                                    }
                                },
                                onSpeed = { showSpeed = true },
                                onTimer = { showTimer = true },
                                onShare = { context.shareTrack(viewModel.shareText(context), shareChooser) },
                                onAddToPlaylist = { showPlaylists = true },
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    ) {
                        PlayerArtwork(
                            artworkUrl = track.artworkUrl,
                            fallbackKey = track.discourseId,
                            fallbackLabel = track.discourseTitle,
                            isPlaying = state.isPlaying,
                            accent = accent.first,
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .aspectRatio(1f),
                        )

                        Spacer(Modifier.height(4.dp))

                        TrackHeadline(
                            title = track.title,
                            speaker = track.speaker,
                            seriesTitle = track.discourseTitle,
                            centered = true,
                        )

                        SeekBar(state = state, accent = accent.first, onSeek = viewModel::seekTo)
                        TransportRow(state = state, viewModel = viewModel, accent = accent.first)
                        SecondaryActions(
                            isFavourite = isFavourite,
                            downloadState = downloadStatus?.state,
                            speed = state.speed,
                            sleepTimer = sleepTimer,
                            onFavourite = viewModel::toggleFavourite,
                            onDownload = {
                                when (downloadStatus?.state) {
                                    // Deleting an offline copy is destructive; ask first.
                                    DownloadState.DOWNLOADED -> confirmRemoveDownload = true
                                    // Mid-download the useful actions are pause and stop,
                                    // not "download again".
                                    DownloadState.DOWNLOADING,
                                    DownloadState.QUEUED,
                                    -> showDownloadControls = true
                                    else -> viewModel.toggleDownload()
                                }
                            },
                            onSpeed = { showSpeed = true },
                            onTimer = { showTimer = true },
                            onShare = { context.shareTrack(viewModel.shareText(context), shareChooser) },
                            onAddToPlaylist = { showPlaylists = true },
                        )
                    }
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            state = state,
            onDismiss = { showQueue = false },
            onPlayIndex = viewModel::playQueueItem,
            onRemove = viewModel::removeQueueItem,
        )
    }

    if (showSpeed) {
        PlaybackSpeedSheet(
            current = state.speed,
            onSelect = viewModel::setSpeed,
            onDismiss = { showSpeed = false },
        )
    }

    if (showTimer) {
        SleepTimerSheet(
            current = sleepTimer,
            onSelectMinutes = viewModel::setSleepTimerMinutes,
            onSelectEndOfTrack = viewModel::setSleepTimerEndOfTrack,
            onCancel = viewModel::cancelSleepTimer,
            onDismiss = { showTimer = false },
        )
    }

    if (showDownloadControls) {
        val percent = ((downloadStatus?.progress ?: 0f) * 100).toInt()
        AlertDialog(
            onDismissRequest = { showDownloadControls = false },
            title = { Text(stringResource(R.string.player_downloading), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = stringResource(
                        if (downloadsPaused) {
                            R.string.player_download_paused
                        } else {
                            R.string.player_download_progress
                        },
                        percent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (downloadsPaused) viewModel.resumeDownloads() else viewModel.pauseDownloads()
                    showDownloadControls = false
                }) {
                    Text(stringResource(if (downloadsPaused) R.string.action_resume else R.string.action_pause))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelCurrentDownload()
                    showDownloadControls = false
                }) {
                    Text(stringResource(R.string.detail_stop_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }

    if (confirmRemoveDownload) {
        AlertDialog(
            onDismissRequest = { confirmRemoveDownload = false },
            title = { Text(stringResource(R.string.player_remove_download_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = stringResource(
                        R.string.player_remove_download_message,
                        track?.title.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleDownload()
                    confirmRemoveDownload = false
                }) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveDownload = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        )
    }

    if (showPlaylists) {
        AddToPlaylistSheet(
            playlists = playlists,
            containing = playlistsWithTrack,
            onToggle = viewModel::togglePlaylistMembership,
            onCreate = viewModel::createPlaylistWithCurrent,
            onDismiss = { showPlaylists = false },
        )
    }
}

/**
 * Blurred artwork wash behind the player.
 *
 * `Modifier.blur` is a no-op below API 31, so the artwork is also heavily
 * scaled and scrimmed — on older devices that alone still reads as a soft
 * colour field rather than a recognisable image.
 */
@Composable
private fun PlayerBackground(artworkUrl: String?, accent: Pair<Color, Color>) {
    Box(Modifier.fillMaxSize()) {
        Artwork(
            imageUrl = artworkUrl,
            contentDescription = null,
            fallbackKey = artworkUrl.orEmpty(),
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.6f)
                .blur(64.dp),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )

        AuroraBackground(
            modifier = Modifier.fillMaxSize(),
            colors = listOf(accent.first, accent.second, accent.first.copy(alpha = 0.6f)),
            alpha = 0.30f,
            speedMillis = 34_000,
        )
    }
}

/**
 * Artwork with a subtle zoom while playing.
 *
 * The scale delta is small on purpose — enough to register as "alive" in
 * peripheral vision, not enough to be distracting over a 90-minute talk.
 */
@Composable
private fun PlayerArtwork(
    artworkUrl: String?,
    fallbackKey: String,
    fallbackLabel: String,
    isPlaying: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artwork-zoom",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.45f else 0.18f,
        animationSpec = tween(durationMillis = 600),
        label = "artwork-glow",
    )

    Artwork(
        imageUrl = artworkUrl,
        contentDescription = stringResource(R.string.cd_artwork_for, fallbackLabel),
        fallbackKey = fallbackKey,
        fallbackLabel = fallbackLabel,
        shape = OshoShapeTokens.ArtworkLarge,
        modifier = modifier
            .scale(scale)
            .glow(accent, radius = 36.dp, alpha = glowAlpha)
            .softShadow(OshoShapeTokens.ArtworkLarge, elevation = 32.dp),
    )
}

@Composable
private fun PlayerTopBar(
    stationName: String?,
    seriesTitle: String,
    isReconnecting: Boolean,
    sourceUnreachable: Boolean,
    sleepTimer: SleepTimer,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
) {
    val sleepRemaining = if (sleepTimer is SleepTimer.Duration) {
        stringResource(
            R.string.cd_sleep_timer_remaining,
            spokenDuration(sleepTimer.remainingMs / 1000),
        )
    } else {
        ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OshoIconButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_collapse_player),
            onClick = onCollapse,
            iconSize = 28.dp,
            tint = Color.White,
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    // Naming the host matters: someone with full bars being told
                    // to check their network concludes the app is broken.
                    isReconnecting && sourceUnreachable ->
                        stringResource(R.string.player_source_down)

                    isReconnecting -> stringResource(R.string.player_reconnecting)
                    stationName != null -> stringResource(R.string.player_radio)
                    else -> stringResource(R.string.player_playing_from)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (stationName != null || isReconnecting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.6f)
                },
            )
            Text(
                text = stationName ?: seriesTitle,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The remaining sleep time replaces the queue icon while a timer runs,
        // so an active timer is never invisible.
        if (sleepTimer is SleepTimer.Duration) {
            Text(
                text = DurationFormat.clock(sleepTimer.remainingMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .semantics { contentDescription = sleepRemaining },
            )
        }

        OshoIconButton(
            icon = Icons.Rounded.QueueMusic,
            contentDescription = stringResource(R.string.cd_queue),
            onClick = onShowQueue,
            iconSize = 24.dp,
            tint = Color.White,
        )
    }
}

@Composable
private fun TrackHeadline(
    title: String,
    speaker: String,
    seriesTitle: String,
    centered: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = if (seriesTitle.isBlank()) {
                speaker
            } else {
                stringResource(R.string.dot_separated, speaker, seriesTitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

/**
 * Seek bar.
 *
 * Drawn rather than a Material `Slider`: that component insets its track by half
 * the thumb width and leaves a gap either side of the thumb, so the bar never
 * reached the edges and the thumb — a tall bar — sat on top of the opening
 * minutes it was meant to be pointing at. Here the track runs the full width and
 * the thumb is a small disc riding on it.
 *
 * Three layers, back to front: what is left, what has downloaded ahead of the
 * playhead, and what has played. The buffered layer is the one people actually
 * want on a streamed 90-minute talk — it is the difference between "the app has
 * stalled" and "the next ten minutes are already safe".
 *
 * While the user drags, the displayed position comes from local state rather
 * than the player, so the thumb tracks the finger instead of fighting the
 * 500 ms position poll. The real seek is issued once on release.
 */
@Composable
private fun SeekBar(
    state: PlayerState,
    accent: Color,
    onSeek: (Long) -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    // Re-sync whenever playback moves on its own.
    LaunchedEffect(state.positionMs, state.durationMs) {
        if (!scrubbing) scrubValue = state.progress
    }

    val fraction = if (scrubbing) scrubValue else state.progress
    val displayedMs = if (scrubbing) (scrubValue * state.durationMs).toLong() else state.positionMs
    val seekDescription = stringResource(
        R.string.cd_seek,
        spokenDuration(displayedMs / 1000),
        spokenDuration(state.durationMs / 1000),
    )

    // Both grow under the finger, which is the only feedback a bar this thin
    // can give that it has taken the gesture.
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 7.dp else 5.dp,
        label = "seek-track",
    )
    // Kept small on purpose. The loaded-ahead sliver sits immediately in front
    // of the playhead, and a fat thumb simply parks on top of the one thing the
    // buffered layer exists to show.
    val thumbRadius by animateDpAsState(
        targetValue = if (scrubbing) 9.dp else 4.dp,
        label = "seek-thumb",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEEK_TOUCH_HEIGHT)
                .pointerInput(state.durationMs) {
                    detectTapGestures { offset ->
                        val target = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((target * state.durationMs).toLong())
                    }
                }
                .pointerInput(state.durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubbing = true
                            scrubValue = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((scrubValue * state.durationMs).toLong())
                            scrubbing = false
                        },
                        onDragCancel = { scrubbing = false },
                        onHorizontalDrag = { change, _ ->
                            scrubValue = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
                .semantics {
                    contentDescription = seekDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        onSeek((target.coerceIn(0f, 1f) * state.durationMs).toLong())
                        true
                    }
                },
        ) {
            val centreY = size.height / 2f
            val height = trackHeight.toPx()
            val radius = CornerRadius(height / 2f)
            val top = Offset(0f, centreY - height / 2f)

            fun band(width: Float, color: Color) {
                if (width <= 0f) return
                drawRoundRect(
                    color = color,
                    topLeft = top,
                    size = Size(width.coerceAtMost(size.width), height),
                    cornerRadius = radius,
                )
            }

            // The gap between these two alphas is doing real work: on a
            // two-hour talk ExoPlayer holds only a couple of minutes ahead, so
            // the loaded band is a ~2% sliver and needs the contrast to read at
            // all.
            band(size.width, Color.White.copy(alpha = 0.16f))
            band(size.width * state.bufferedProgress, Color.White.copy(alpha = 0.50f))
            band(size.width * fraction, accent)

            // Kept a thumb's width inside the ends so it never hangs half off
            // the screen at either extreme.
            val thumb = thumbRadius.toPx()
            drawCircle(
                color = Color.White,
                radius = thumb,
                center = Offset(
                    x = (size.width * fraction).coerceIn(thumb, size.width - thumb),
                    y = centreY,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DurationFormat.clock(displayedMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = DurationFormat.clock(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun TransportRow(
    state: PlayerState,
    viewModel: PlayerViewModel,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OshoIconButton(
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(
                if (state.shuffleEnabled) R.string.cd_shuffle_on else R.string.cd_shuffle_off,
            ),
            onClick = viewModel::toggleShuffle,
            tint = Color.White.copy(alpha = 0.75f),
            active = state.shuffleEnabled,
        )

        OshoIconButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.cd_previous),
            onClick = viewModel::previous,
            iconSize = 34.dp,
            tint = Color.White,
        )

        PlayPauseButton(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            onClick = viewModel::togglePlayPause,
            size = 74.dp,
            containerColor = Color.White,
            contentColor = Color.Black,
        )

        OshoIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next),
            onClick = viewModel::next,
            iconSize = 34.dp,
            tint = Color.White,
            enabled = state.hasNext,
        )

        OshoIconButton(
            icon = when (state.repeatMode) {
                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            },
            contentDescription = stringResource(
                when (state.repeatMode) {
                    RepeatMode.OFF -> R.string.cd_repeat_off
                    RepeatMode.ALL -> R.string.cd_repeat_all
                    RepeatMode.ONE -> R.string.cd_repeat_one
                },
            ),
            onClick = viewModel::cycleRepeat,
            tint = Color.White.copy(alpha = 0.75f),
            active = state.repeatMode != RepeatMode.OFF,
        )
    }
}

@Composable
private fun SecondaryActions(
    isFavourite: Boolean,
    downloadState: DownloadState?,
    speed: Float,
    sleepTimer: SleepTimer,
    onFavourite: () -> Unit,
    onDownload: () -> Unit,
    onSpeed: () -> Unit,
    onTimer: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedButton(speed = speed, onClick = onSpeed)

            OshoIconButton(
                icon = Icons.Rounded.Timer,
                contentDescription = stringResource(R.string.player_sleep_timer),
                onClick = onTimer,
                iconSize = 22.dp,
                tint = Color.White.copy(alpha = 0.75f),
                active = sleepTimer != SleepTimer.Off,
            )

            com.sannyas.player.core.designsystem.component.FavouriteButton(
                isFavourite = isFavourite,
                onToggle = onFavourite,
                iconSize = 22.dp,
                tint = Color.White.copy(alpha = 0.75f),
            )

            OshoIconButton(
                icon = when (downloadState) {
                    DownloadState.DOWNLOADED -> Icons.Rounded.DownloadDone
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> Icons.Rounded.Downloading
                    else -> Icons.Rounded.Download
                },
                contentDescription = stringResource(
                    when (downloadState) {
                        DownloadState.DOWNLOADED -> R.string.cd_downloaded_remove
                        DownloadState.DOWNLOADING, DownloadState.QUEUED -> R.string.cd_downloading
                        else -> R.string.cd_download_offline
                    },
                ),
                onClick = onDownload,
                iconSize = 22.dp,
                tint = Color.White.copy(alpha = 0.75f),
                active = downloadState == DownloadState.DOWNLOADED,
            )


            OshoIconButton(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = stringResource(R.string.cd_add_to_playlist),
                onClick = onAddToPlaylist,
                iconSize = 22.dp,
                tint = Color.White.copy(alpha = 0.75f),
            )

            OshoIconButton(
                icon = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.cd_share),
                onClick = onShare,
                iconSize = 22.dp,
                tint = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

/** Speed shown as a label rather than an icon, because the value is the point. */
@Composable
private fun SpeedButton(speed: Float, onClick: () -> Unit) {
    val label = remember(speed) { formatSpeed(speed) }
    val description = stringResource(R.string.cd_playback_speed_current, label)

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        OshoIconButton(
            icon = Icons.Rounded.Speed,
            contentDescription = description,
            onClick = onClick,
            iconSize = 22.dp,
            tint = Color.White.copy(alpha = 0.75f),
            active = speed != 1f,
        )
    }
}

internal fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x"

/** Opens the system share sheet. Never sends anything on its own. */
private fun android.content.Context.shareTrack(text: String?, chooserTitle: String) {
    if (text.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

private const val DISMISS_DRAG_THRESHOLD = 28f

/** Comfortable to hit in the dark; the drawn bar is a fraction of it. */
private val SEEK_TOUCH_HEIGHT = 40.dp
