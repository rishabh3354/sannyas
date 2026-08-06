package com.osho.player.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osho.player.domain.model.Discourse
import com.osho.player.domain.model.Playlist
import com.osho.player.domain.model.Track
import com.osho.player.domain.repository.CatalogRepository
import com.osho.player.domain.repository.PlaylistRepository
import com.osho.player.playback.PlayerConnection
import com.osho.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The list of playlists. */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { playlistRepository.create(name) }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch { playlistRepository.delete(playlistId) }
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch { playlistRepository.rename(playlistId, name) }
    }
}

data class PlaylistTrackItem(val track: Track, val discourse: Discourse)

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<PlaylistTrackItem> = emptyList(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && tracks.isEmpty()
}

/**
 * One playlist.
 *
 * Order is the whole point of a playlist, so the stored positions are the
 * ordering — nothing here re-sorts. Talks whose ids are no longer in the
 * catalogue are dropped rather than shown as blanks.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<String>(Routes.PLAYLIST_ARG)
        ?.toLongOrNull()
        ?: -1L

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        catalogRepository.observeDiscourses(),
        playlistRepository.observePlaylist(playlistId),
        playlistRepository.observeTrackIds(playlistId),
        playerConnection.state,
    ) { discourses, playlist, trackIds, player ->
        val byId = discourses.associateBy { it.id }
        val tracksById = discourses.flatMap { it.tracks }.associateBy { it.id }

        PlaylistDetailUiState(
            playlist = playlist,
            tracks = trackIds.mapNotNull { id ->
                val track = tracksById[id] ?: return@mapNotNull null
                val discourse = byId[track.discourseId] ?: return@mapNotNull null
                PlaylistTrackItem(track, discourse)
            },
            currentTrackId = player.currentTrack?.id,
            isPlaying = player.isPlaying,
            isLoading = discourses.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    /** Plays the playlist as its own queue, which is what makes it a sequence. */
    fun play(startIndex: Int = 0) {
        val queue = uiState.value.tracks.map { it.track }
        if (queue.isEmpty()) return
        playerConnection.play(queue, startIndex.coerceIn(0, queue.lastIndex))
    }

    fun shuffle() {
        val queue = uiState.value.tracks.map { it.track }
        if (queue.isEmpty()) return
        playerConnection.play(queue.shuffled(), startIndex = 0)
    }

    fun remove(item: PlaylistTrackItem) {
        viewModelScope.launch { playlistRepository.remove(playlistId, item.track.id) }
    }

    /**
     * Moves one talk by [delta] places.
     *
     * A plain swap rather than drag-and-drop: Compose has no reorderable list
     * out of the box, and for sequences of a dozen hour-long talks a pair of
     * arrows is both easier to hit and reachable with a screen reader.
     */
    fun move(item: PlaylistTrackItem, delta: Int) {
        val ids = uiState.value.tracks.map { it.track.id }.toMutableList()
        val from = ids.indexOf(item.track.id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        viewModelScope.launch { playlistRepository.reorder(playlistId, ids) }
    }

    fun rename(name: String) {
        viewModelScope.launch { playlistRepository.rename(playlistId, name) }
    }

    fun delete() {
        viewModelScope.launch { playlistRepository.delete(playlistId) }
    }
}
