package com.osho.player.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osho.player.domain.model.DownloadState
import com.osho.player.domain.model.DownloadStatus
import com.osho.player.domain.model.Track
import com.osho.player.domain.repository.CatalogRepository
import com.osho.player.domain.repository.DownloadRepository
import com.osho.player.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Downloads for one series, grouped so the screen reads as a shelf of albums. */
data class DownloadGroup(
    val discourseId: String,
    val discourseTitle: String,
    val artworkUrl: String,
    val tracks: List<DownloadItem>,
) {
    val completedCount: Int get() = tracks.count { it.status.state == DownloadState.DOWNLOADED }
    val inProgressCount: Int
        get() = tracks.count {
            it.status.state == DownloadState.DOWNLOADING || it.status.state == DownloadState.QUEUED
        }
    val totalBytes: Long get() = tracks.sumOf { it.status.bytesDownloaded }
}

data class DownloadItem(val track: Track, val status: DownloadStatus)

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val groups: List<DownloadGroup> = emptyList(),
    val storageUsedBytes: Long = 0L,
) {
    val trackCount: Int get() = groups.sumOf { it.tracks.size }
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<DownloadsUiState> = combine(
        catalogRepository.observeDiscourses(),
        downloadRepository.observeAll(),
        downloadRepository.observeStorageUsage(),
        loading,
    ) { discourses, downloads, storage, isLoading ->
        val tracksById = discourses.flatMap { it.tracks }.associateBy { it.id }
        val byId = discourses.associateBy { it.id }

        val groups = downloads.values
            .mapNotNull { status ->
                val track = tracksById[status.trackId] ?: return@mapNotNull null
                DownloadItem(track, status)
            }
            .groupBy { it.track.discourseId }
            .mapNotNull { (discourseId, items) ->
                val discourse = byId[discourseId] ?: return@mapNotNull null
                DownloadGroup(
                    discourseId = discourseId,
                    discourseTitle = discourse.title,
                    artworkUrl = discourse.image,
                    tracks = items.sortedBy { it.track.index },
                )
            }
            .sortedBy { it.discourseTitle.lowercase() }

        DownloadsUiState(
            isLoading = isLoading && discourses.isEmpty(),
            groups = groups,
            storageUsedBytes = storage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    init {
        viewModelScope.launch { loading.value = false }
    }

    /** Plays a downloaded talk within the downloaded portion of its series. */
    fun play(group: DownloadGroup, item: DownloadItem) {
        val queue = group.tracks.map { it.track }
        playerConnection.play(
            queue = queue,
            startIndex = queue.indexOfFirst { it.id == item.track.id }.coerceAtLeast(0),
        )
    }

    fun removeTrack(item: DownloadItem) {
        viewModelScope.launch { downloadRepository.remove(item.track.id) }
    }

    fun removeGroup(group: DownloadGroup) {
        viewModelScope.launch { downloadRepository.removeAll(group.discourseId) }
    }

    fun removeAll() {
        viewModelScope.launch { downloadRepository.removeEverything() }
    }

    fun pauseAll() {
        viewModelScope.launch { downloadRepository.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { downloadRepository.resumeAll() }
    }
}
