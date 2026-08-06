package com.sannyas.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.sannyas.player.di.ApplicationScope
import com.sannyas.player.R
import com.sannyas.player.core.review.ReviewPrompter
import com.sannyas.player.domain.model.PlayerState
import com.sannyas.player.domain.model.RepeatMode
import com.sannyas.player.domain.model.Station
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.UserDataRepository
import com.sannyas.player.data.local.datastore.SettingsDataStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single point of contact between the UI and playback.
 *
 * Holds a [MediaController] bound to [PlaybackService] and republishes its
 * callbacks as a [PlayerState] flow that Compose can collect. Every screen —
 * mini player, full player, track rows — reads from this one state, so they can
 * never disagree about what is playing.
 *
 * Position is polled rather than pushed: `Player` has no position callback, and
 * a 500 ms tick is both smooth enough for a progress bar and cheap enough to
 * leave running.
 */
@Singleton
class PlayerConnection @Inject constructor(
    private val context: Context,
    private val userDataRepository: UserDataRepository,
    private val catalogRepository: CatalogRepository,
    private val settings: SettingsDataStore,
    private val sleepTimer: SleepTimerController,
    private val recovery: PlaybackRecovery,
    private val reviewPrompter: ReviewPrompter,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * All MediaController interaction happens here. The injected
     * [scope] is a background scope and must never touch the controller.
     */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null

    /** Non-null while a station is playing; drives the top-up below. */
    private var station: Station? = null

    /**
     * The station's on-screen name, resolved by the caller.
     *
     * The whole-library station is named by a translated resource rather than
     * by catalogue data, and this layer has no `Context` to resolve one with.
     */
    private var stationDisplayName: String? = null
    private var toppingUp = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                sleepTimer.onTrackEnded()
                // An automatic advance means the previous talk actually ran out,
                // rather than being skipped.
                reviewPrompter.onTalkCompleted()
            }
            MediaItems.toTrack(mediaItem)?.let { track ->
                scope.launch { userDataRepository.recordPlayed(track) }
            }
            publish()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The last talk in a queue ends without a transition to report it.
            if (playbackState == Player.STATE_ENDED) reviewPrompter.onTalkCompleted()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A blip is being retried behind the scenes; putting "no connection"
            // in front of someone whose talk is about to resume on its own is
            // both wrong and alarming. [PlaybackRecovery] surfaces it if the
            // retries run out.
            if (recovery.handles(error)) return

            _state.value = _state.value.copy(
                errorMessageRes = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    -> R.string.player_error_network

                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    -> R.string.player_error_unavailable

                    else -> R.string.player_error_generic
                },
            )
        }
    }

    init {
        connect()
        startPositionPolling()
    }

    // ---- Connection -------------------------------------------------------

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()?.also { active ->
                    active.addListener(listener)
                    applyStoredPreferences(active)
                    if (pendingAction != null) {
                        pendingAction?.invoke(active)
                        pendingAction = null
                    } else {
                        restoreLastSession(active)
                    }
                    publish()
                }
            },
            // Must be the main executor, not directExecutor: everything in the
            // callback touches the controller, and MediaController rejects
            // calls from any thread but the one it was built on.
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Reads the stored preferences off the main thread, then applies them on it.
     */
    private fun applyStoredPreferences(controller: MediaController) {
        mainScope.launch {
            val stored = settings.settings.first()
            controller.setPlaybackSpeed(stored.defaultPlaybackSpeed)
            controller.shuffleModeEnabled = stored.shuffleEnabled
            controller.repeatMode = stored.repeatMode.toPlayerRepeatMode()
        }
    }

    /**
     * Repopulates the queue from the last listening position on a cold start.
     *
     * The service may have been killed while the app was away, in which case the
     * session comes back empty and everything the listener had open is gone.
     * This rebuilds the series queue, seeks to the saved offset and leaves it
     * *paused* — the app reopens exactly where it was left, without ambushing
     * anyone with sound.
     */
    private fun restoreLastSession(controller: MediaController) {
        if (controller.mediaItemCount > 0) return

        mainScope.launch {
            val resume = userDataRepository.observeProgress().first()
                .maxByOrNull { it.updatedAt } ?: return@launch
            val track = catalogRepository.getTrack(resume.trackId) ?: return@launch
            val queue = catalogRepository.getDiscourse(track.discourseId)?.tracks
                ?: listOf(track)

            // A later user action may have populated the queue while we were
            // reading; never stomp on it.
            if (controller.mediaItemCount > 0) return@launch

            controller.setMediaItems(
                queue.map(MediaItems::toMediaItem),
                queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                resume.positionMs,
            )
            controller.prepare()
            publish(controller)
        }
    }

    /**
     * Runs [block] against the controller on the main thread, queueing it if the
     * service is still binding. Only the most recent pending action is kept — an
     * early tap should not replay a stale queue once the connection lands.
     *
     * Dispatching through [Dispatchers.Main.immediate] means calls already on
     * the main thread (every UI event) still run synchronously, while calls
     * arriving from a background coroutine are hopped across safely.
     */
    private fun withController(block: (MediaController) -> Unit) {
        mainScope.launch {
            val active = controller
            if (active != null) block(active) else pendingAction = block
        }
    }

    private fun startPositionPolling() {
        // Polls on the main thread because reading position and publishing both
        // touch the controller.
        mainScope.launch {
            while (isActive) {
                delay(POSITION_POLL_MS)
                val active = controller ?: continue
                if (!active.isPlaying) continue
                publish(active)
            }
        }
    }

    // ---- Commands ---------------------------------------------------------

    /**
     * Replaces the queue with [queue] and starts at [startIndex].
     *
     * Resume positions are applied here rather than in the service so that
     * starting a talk from any screen picks up where the listener left off.
     */
    fun play(queue: List<Track>, startIndex: Int = 0, startPositionMs: Long? = null) {
        if (queue.isEmpty()) return
        station = null
        stationDisplayName = null
        val index = startIndex.coerceIn(0, queue.lastIndex)

        scope.launch {
            val resume = startPositionMs
                ?: userDataRepository.getProgress(queue[index].id)
                    ?.takeIf { it.isMeaningful }
                    ?.positionMs
                ?: 0L

            withController { controller ->
                controller.setMediaItems(queue.map(MediaItems::toMediaItem), index, resume)
                controller.prepare()
                controller.play()
            }
        }
    }

    /**
     * Starts a station: a shuffled batch that is extended as it plays out.
     *
     * Any ordinary `play` call clears [station], so starting a series from
     * anywhere in the app silently ends radio mode rather than continuing to
     * append talks behind the listener's back.
     */
    fun playStation(station: Station, displayName: String, firstBatch: List<Track>) {
        if (firstBatch.isEmpty()) return
        this.station = station
        this.stationDisplayName = displayName
        withController { controller ->
            controller.setMediaItems(firstBatch.map(MediaItems::toMediaItem), 0, 0L)
            controller.shuffleModeEnabled = false
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.prepare()
            controller.play()
        }
        publish()
    }

    /**
     * Appends another batch once the queue is running low.
     *
     * Keeping a rolling window rather than loading thousands of items keeps the
     * session light — the controller only ever holds a queue it can actually
     * render, and the stream still never ends.
     */
    private fun topUpStation(controller: MediaController) {
        val current = station ?: return
        if (toppingUp) return
        val remaining = controller.mediaItemCount - controller.currentMediaItemIndex - 1
        if (remaining > TOP_UP_THRESHOLD) return

        toppingUp = true
        mainScope.launch {
            val more = catalogRepository.stationTracks(current.id)
            if (more.isNotEmpty() && station?.id == current.id) {
                controller.addMediaItems(more.map(MediaItems::toMediaItem))
            }
            toppingUp = false
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        play(queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    /** Starts a series with the queue shuffled, but always from a real first talk. */
    fun shuffle(queue: List<Track>) {
        if (queue.isEmpty()) return
        withController { it.shuffleModeEnabled = true }
        scope.launch { settings.setShuffle(true) }
        play(queue.shuffled(), 0)
    }

    fun togglePlayPause() = withController { controller ->
        if (controller.isPlaying) {
            controller.pause()
        } else {
            // A finished queue should restart rather than sit inert.
            if (controller.playbackState == Player.STATE_ENDED) controller.seekTo(0, 0L)
            controller.play()
        }
    }

    fun pause() = withController { it.pause() }

    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    fun previous() = withController { controller ->
        // Mirrors the platform convention: restart the talk unless we are in the
        // first few seconds, in which case step back a track.
        if (controller.currentPosition > RESTART_THRESHOLD_MS || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun seekToFraction(fraction: Float) = withController { controller ->
        val duration = controller.duration
        if (duration != C.TIME_UNSET && duration > 0) {
            controller.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        }
    }

    fun seekBack() = withController { it.sendCustomCommandSafely(PlaybackService.COMMAND_SEEK_BACK) }

    fun seekForward() =
        withController { it.sendCustomCommandSafely(PlaybackService.COMMAND_SEEK_FORWARD) }

    fun playAt(index: Int) = withController { it.seekToDefaultPosition(index) }

    fun removeFromQueue(index: Int) = withController { it.removeMediaItem(index) }

    fun moveInQueue(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }

    fun addToQueue(track: Track) =
        withController { it.addMediaItem(MediaItems.toMediaItem(track)) }

    fun setSpeed(speed: Float) {
        withController { it.playbackParameters = PlaybackParameters(speed) }
        scope.launch { settings.setPlaybackSpeed(speed) }
    }

    fun toggleShuffle() = withController { controller ->
        val enabled = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = enabled
        scope.launch { settings.setShuffle(enabled) }
    }

    fun cycleRepeatMode() = withController { controller ->
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> RepeatMode.ALL
            Player.REPEAT_MODE_ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        controller.repeatMode = next.toPlayerRepeatMode()
        scope.launch { settings.setRepeatMode(next) }
    }

    /**
     * Stops playback and clears the queue, retiring the mini player.
     *
     * The resume position is flushed first, so a dismissed talk still shows up
     * under Continue Listening rather than being silently forgotten.
     */
    fun stop() {
        station = null
        stationDisplayName = null
        persistPosition()
        withController { controller ->
            controller.pause()
            controller.clearMediaItems()
            controller.stop()
        }
        _state.value = PlayerState()
    }

    fun consumeError() {
        _state.value = _state.value.copy(errorMessageRes = null)
    }

    /** Flushes the current position to Room; called when the player screen closes. */
    fun persistPosition() {
        val current = _state.value
        val track = current.currentTrack ?: return
        if (current.durationMs <= 0L) return
        scope.launch {
            userDataRepository.saveProgress(
                trackId = track.id,
                discourseId = track.discourseId,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
            )
            settings.setLastPlayed(track.id, current.positionMs)
        }
    }

    fun release() {
        mainScope.launch {
            controller?.removeListener(listener)
            controller?.release()
            controller = null
        }
    }

    // ---- State publishing -------------------------------------------------

    private fun publish(active: MediaController? = controller) {
        val controller = active ?: return

        val queue = buildList {
            for (i in 0 until controller.mediaItemCount) {
                MediaItems.toTrack(controller.getMediaItemAt(i))?.let(::add)
            }
        }

        val duration = controller.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: MediaItems.toTrack(controller.currentMediaItem)?.durationMs
            ?: 0L

        _state.value = _state.value.copy(
            currentTrack = MediaItems.toTrack(controller.currentMediaItem),
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
            queue = queue,
            queueIndex = controller.currentMediaItemIndex,
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode.toRepeatMode(),
            speed = controller.playbackParameters.speed,
            hasNext = controller.hasNextMediaItem(),
            hasPrevious = controller.hasPreviousMediaItem(),
            stationName = stationDisplayName,
            stationId = station?.id,
        )

        topUpStation(controller)
    }

    private fun MediaController.sendCustomCommandSafely(action: String) {
        val command = SessionCommand(action, android.os.Bundle.EMPTY)
        if (isSessionCommandAvailable(command)) {
            sendCustomCommand(command, android.os.Bundle.EMPTY)
        }
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
        const val RESTART_THRESHOLD_MS = 5_000L
        const val TOP_UP_THRESHOLD = 5
    }
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    else -> RepeatMode.OFF
}
