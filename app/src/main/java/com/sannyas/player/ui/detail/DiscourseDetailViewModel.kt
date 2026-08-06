package com.sannyas.player.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sannyas.player.R
import com.sannyas.player.core.common.talkCountText
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.DownloadState
import com.sannyas.player.domain.model.DownloadStatus
import com.sannyas.player.domain.model.PlaybackProgress
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.DownloadRepository
import com.sannyas.player.domain.repository.UserDataRepository
import com.sannyas.player.playback.PlayerConnection
import com.sannyas.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscourseDetailUiState(
    val isLoading: Boolean = true,
    val discourse: Discourse? = null,
    val isFavourite: Boolean = false,
    val progressByTrack: Map<String, PlaybackProgress> = emptyMap(),
    val downloadByTrack: Map<String, DownloadStatus> = emptyMap(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val downloadsPaused: Boolean = false,
) {
    /** True once every talk in the series is available offline. */
    val isFullyDownloaded: Boolean
        get() {
            val tracks = discourse?.tracks ?: return false
            return tracks.isNotEmpty() && tracks.all {
                downloadByTrack[it.id]?.state == DownloadState.DOWNLOADED
            }
        }

    val downloadingCount: Int
        get() = discourse?.tracks.orEmpty().count {
            val state = downloadByTrack[it.id]?.state
            state == DownloadState.DOWNLOADING || state == DownloadState.QUEUED
        }
}

@HiltViewModel
class DiscourseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val userDataRepository: UserDataRepository,
    private val downloadRepository: DownloadRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val discourseId: String =
        savedStateHandle.get<String>(Routes.DISCOURSE_ARG).orEmpty()

    private val discourse = MutableStateFlow<Discourse?>(null)
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<DiscourseDetailUiState> = combine(
        discourse,
        loading,
        userDataRepository.observeIsFavourite(discourseId),
        userDataRepository.observeProgress(),
        combine(
            downloadRepository.observeAll(),
            playerConnection.state,
            downloadRepository.observePaused(),
        ) { downloads, player, paused -> Triple(downloads, player, paused) },
    ) { series, isLoading, favourite, progress, (downloads, player, paused) ->
        DiscourseDetailUiState(
            isLoading = isLoading,
            discourse = series,
            isFavourite = favourite,
            progressByTrack = progress.associateBy { it.trackId },
            downloadByTrack = downloads,
            currentTrackId = player.currentTrack?.id,
            isPlaying = player.isPlaying,
            downloadsPaused = paused,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscourseDetailUiState(),
    )

    init {
        viewModelScope.launch {
            discourse.value = catalogRepository.getDiscourse(discourseId)
            loading.value = false
        }
    }

    /** Plays from the first unfinished talk, which is what "Play all" should mean. */
    fun playAll() {
        val series = discourse.value ?: return
        val progress = uiState.value.progressByTrack
        val resumeIndex = series.tracks.indexOfFirst { track ->
            progress[track.id]?.isMeaningful == true
        }
        playerConnection.play(series.tracks, startIndex = resumeIndex.coerceAtLeast(0))
    }

    fun shuffle() {
        val series = discourse.value ?: return
        playerConnection.shuffle(series.tracks)
    }

    fun playTrack(track: Track) {
        val series = discourse.value ?: return
        playerConnection.play(
            queue = series.tracks,
            startIndex = series.tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
        )
    }

    fun toggleFavourite() {
        viewModelScope.launch { userDataRepository.toggleFavourite(discourseId) }
    }

    /** Queues every talk in the series. The screen confirms before calling this. */
    fun downloadSeries() {
        val series = discourse.value ?: return
        viewModelScope.launch { downloadRepository.downloadAll(series) }
    }

    /**
     * Cancels in-flight downloads for this series and deletes what has landed.
     * Also used as "remove downloads" once the series is fully saved.
     */
    fun cancelSeriesDownload() {
        val series = discourse.value ?: return
        viewModelScope.launch { downloadRepository.removeAll(series.id) }
    }

    fun pauseDownloads() {
        viewModelScope.launch { downloadRepository.pauseAll() }
    }

    fun resumeDownloads() {
        viewModelScope.launch { downloadRepository.resumeAll() }
    }

    /** Estimated bytes for the whole series, shown in the confirm dialog. */
    fun estimatedSeriesBytes(): Long {
        val series = discourse.value ?: return 0L
        // Source audio is a constant-bitrate mono MP3 at roughly 32 kbps.
        return series.durationSeconds * ESTIMATED_BYTES_PER_SECOND
    }

    fun toggleTrackDownload(track: Track) {
        viewModelScope.launch {
            if (uiState.value.downloadByTrack[track.id]?.state == DownloadState.DOWNLOADED) {
                downloadRepository.remove(track.id)
            } else {
                downloadRepository.download(track)
            }
        }
    }

    /**
     * Share payload for the series. Names the app rather than the source site,
     * so the message reads as a recommendation rather than a bare link.
     */
    fun shareText(context: Context): String? {
        val series = discourse.value ?: return null
        return context.getString(
            R.string.share_series_text,
            series.title,
            talkCountText(context, series.trackCount),
            series.speaker,
            context.getString(R.string.app_name),
        )
    }

    private companion object {
        const val ESTIMATED_BYTES_PER_SECOND = 4_000L
    }
}
