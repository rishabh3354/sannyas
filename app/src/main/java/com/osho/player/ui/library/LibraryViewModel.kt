package com.osho.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osho.player.domain.model.Discourse
import com.osho.player.domain.repository.CatalogRepository
import com.osho.player.domain.repository.DownloadRepository
import com.osho.player.domain.repository.UserDataRepository
import com.osho.player.playback.PlayerConnection
import com.osho.player.ui.home.ContinueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.osho.player.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Counts shown on the Library shelves, so each row states what it holds. */
data class LibraryUiState(
    val continueListening: List<ContinueItem> = emptyList(),
    val favourites: List<Discourse> = emptyList(),
    val historyCount: Int = 0,
    val downloadedCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    userDataRepository: UserDataRepository,
    downloadRepository: DownloadRepository,
    private val playerConnection: PlayerConnection,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        catalogRepository.observeDiscourses(),
        userDataRepository.observeProgress(),
        userDataRepository.observeFavouriteDiscourseIds(),
        userDataRepository.observeHistory(),
        downloadRepository.observeDownloadedTrackIds(),
    ) { discourses, progress, favouriteIds, history, downloaded ->
        val byId = discourses.associateBy { it.id }
        val tracksById = discourses.flatMap { it.tracks }.associateBy { it.id }

        LibraryUiState(
            continueListening = progress
                .filter { it.isMeaningful }
                .sortedByDescending { it.updatedAt }
                .mapNotNull { entry ->
                    val track = tracksById[entry.trackId] ?: return@mapNotNull null
                    val discourse = byId[entry.discourseId] ?: return@mapNotNull null
                    ContinueItem(track, discourse, entry.positionMs, entry.durationMs)
                }
                .take(12),
            favourites = favouriteIds.mapNotNull { byId[it] },
            historyCount = history.size,
            downloadedCount = downloaded.size,
        )
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun resume(item: ContinueItem) {
        val index = item.discourse.tracks.indexOfFirst { it.id == item.track.id }
        playerConnection.play(
            queue = item.discourse.tracks,
            startIndex = index.coerceAtLeast(0),
            startPositionMs = item.positionMs,
        )
    }
}
