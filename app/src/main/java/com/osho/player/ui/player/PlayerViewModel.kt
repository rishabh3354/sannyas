package com.osho.player.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osho.player.R
import com.osho.player.data.local.datastore.SettingsDataStore
import com.osho.player.domain.model.DownloadState
import com.osho.player.domain.model.DownloadStatus
import com.osho.player.domain.model.PlayerState
import com.osho.player.domain.model.SleepTimer
import com.osho.player.domain.model.Track
import com.osho.player.domain.repository.CatalogRepository
import com.osho.player.domain.repository.DownloadRepository
import com.osho.player.domain.model.Playlist
import com.osho.player.domain.repository.PlaylistRepository
import com.osho.player.domain.repository.UserDataRepository
import com.osho.player.playback.PlayerConnection
import com.osho.player.playback.PlaybackRecovery
import com.osho.player.playback.SleepTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared state for the mini player, the full player and the queue sheet.
 *
 * All three are views onto the same playback session, so they are backed by one
 * ViewModel scoped to the activity rather than one each — that is what keeps
 * the mini player's progress bar and the full player's seek bar in lockstep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val userDataRepository: UserDataRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val catalogRepository: CatalogRepository,
    private val sleepTimerController: SleepTimerController,
    playbackRecovery: PlaybackRecovery,
    settings: SettingsDataStore,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerConnection.state

    val sleepTimer: StateFlow<SleepTimer> = sleepTimerController.state

    /** Drives the "Reconnecting…" line and the give-up message. */
    val recovery: StateFlow<PlaybackRecovery.State> = playbackRecovery.state

    /** Drives [com.osho.player.ui.player.NightDim]; see the setting in Playback. */
    val dimPlayerWhenIdle: StateFlow<Boolean> = settings.settings
        .map { it.dimPlayerWhenIdle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Favourite state of the series the current track belongs to. */
    /**
     * The heart on the player means *this talk*, not the series it came from.
     *
     * Singling out one discourse from a fifty-part set is the far more common
     * gesture, and it is what someone pressing a heart while listening expects.
     * Favouriting a whole series still lives on the series screen, where the
     * series is what is in front of you.
     */
    val isCurrentFavourite: StateFlow<Boolean> = playerState
        .map { it.currentTrack?.id }
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(false) else userDataRepository.observeIsTrackFavourite(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Playlists that already hold the current talk, for the add sheet. */
    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlistIdsWithCurrent: StateFlow<Set<Long>> = playerState
        .map { it.currentTrack?.id }
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptySet()) else {
                playlistRepository.observePlaylistIdsContaining(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val currentDownloadStatus: StateFlow<DownloadStatus?> = playerState
        .map { it.currentTrack?.id }
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(null) else downloadRepository.observeStatus(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True while the download queue is paused, so the button can offer Resume. */
    val downloadsPaused: StateFlow<Boolean> = downloadRepository.observePaused()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---- Transport --------------------------------------------------------

    fun togglePlayPause() = playerConnection.togglePlayPause()
    fun next() = playerConnection.next()
    fun previous() = playerConnection.previous()
    fun seekTo(positionMs: Long) = playerConnection.seekTo(positionMs)
    fun seekBack() = playerConnection.seekBack()
    fun seekForward() = playerConnection.seekForward()
    fun toggleShuffle() = playerConnection.toggleShuffle()
    fun cycleRepeat() = playerConnection.cycleRepeatMode()
    fun setSpeed(speed: Float) = playerConnection.setSpeed(speed)
    fun playQueueItem(index: Int) = playerConnection.playAt(index)
    fun removeQueueItem(index: Int) = playerConnection.removeFromQueue(index)
    fun moveQueueItem(from: Int, to: Int) = playerConnection.moveInQueue(from, to)
    fun consumeError() = playerConnection.consumeError()

    /** Dismisses the mini player: stops playback and clears the queue. */
    fun stop() = playerConnection.stop()
    fun persistPosition() = playerConnection.persistPosition()

    // ---- Actions on the current track -------------------------------------

    fun toggleFavourite() {
        val track = playerState.value.currentTrack ?: return
        viewModelScope.launch { userDataRepository.toggleTrackFavourite(track) }
    }

    /** Adds the current talk to [playlistId], or takes it out if already there. */
    fun togglePlaylistMembership(playlistId: Long) {
        val track = playerState.value.currentTrack ?: return
        viewModelScope.launch {
            if (playlistId in playlistIdsWithCurrent.value) {
                playlistRepository.remove(playlistId, track.id)
            } else {
                playlistRepository.add(playlistId, track)
            }
        }
    }

    /** Creates a playlist and drops the current talk straight into it. */
    fun createPlaylistWithCurrent(name: String) {
        val track = playerState.value.currentTrack ?: return
        viewModelScope.launch {
            playlistRepository.add(playlistRepository.create(name), track)
        }
    }

    /** Downloads the current talk, or removes it if it is already saved. */
    fun toggleDownload() {
        val track = playerState.value.currentTrack ?: return
        viewModelScope.launch {
            if (currentDownloadStatus.value?.state == DownloadState.DOWNLOADED) {
                downloadRepository.remove(track.id)
            } else {
                downloadRepository.download(track)
            }
        }
    }

    fun pauseDownloads() {
        viewModelScope.launch { downloadRepository.pauseAll() }
    }

    fun resumeDownloads() {
        viewModelScope.launch { downloadRepository.resumeAll() }
    }

    /** Cancels an in-flight download for the current talk and discards the part file. */
    fun cancelCurrentDownload() {
        val track = playerState.value.currentTrack ?: return
        viewModelScope.launch { downloadRepository.remove(track.id) }
    }

    /** Saves the exact moment the user is listening to. */
    // ---- Sleep timer ------------------------------------------------------

    fun setSleepTimerMinutes(minutes: Int) {
        if (minutes <= 0) sleepTimerController.cancel()
        else sleepTimerController.start(minutes * 60_000L)
    }

    fun setSleepTimerEndOfTrack() = sleepTimerController.startEndOfTrack()

    fun cancelSleepTimer() = sleepTimerController.cancel()

    // ---- Sharing ----------------------------------------------------------

    /**
     * Share payload for the current talk.
     *
     * Names the app rather than pasting the raw media URL: a bare .mp3 link is
     * meaningless to a recipient, and it invites hotlinking the source's audio.
     */
    fun shareText(context: Context): String? {
        val track = playerState.value.currentTrack ?: return null
        val appName = context.getString(R.string.app_name)
        return if (track.discourseTitle.isBlank()) {
            context.getString(
                R.string.player_share_text_no_series,
                track.title,
                track.speaker,
                appName,
            )
        } else {
            context.getString(
                R.string.player_share_text,
                track.title,
                track.discourseTitle,
                track.speaker,
                appName,
            )
        }
    }

    /** Resolves the full series for the current track, for "go to series". */
    suspend fun currentDiscourseId(): String? = playerState.value.currentTrack?.discourseId

    fun playTrackList(tracks: List<Track>, startIndex: Int) =
        playerConnection.play(tracks, startIndex)
}
