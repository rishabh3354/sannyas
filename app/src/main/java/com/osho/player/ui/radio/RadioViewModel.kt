package com.osho.player.ui.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osho.player.domain.model.Station
import com.osho.player.domain.repository.CatalogRepository
import com.osho.player.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadioUiState(
    val isLoading: Boolean = true,
    /** Draws from the whole library; always first, and never absent once loaded. */
    val allLibrary: Station? = null,
    val categoryStations: List<Station> = emptyList(),
    /** The station currently on air, if radio is what is playing. */
    val playingStationId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && allLibrary == null
}

/**
 * Radio.
 *
 * A station is only ever a filter over the bundled catalogue, so there is no
 * fetching to do here beyond what the catalogue already exposes; the work is
 * handing [PlayerConnection] a first batch and letting it top itself up.
 */
@HiltViewModel
class RadioViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    /**
     * The catalogue snapshot starts empty, so "no stations" and "not read yet"
     * look identical for the first frame. This distinguishes them.
     */
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<RadioUiState> = combine(
        catalogRepository.observeStations(),
        playerConnection.state,
        loading,
    ) { stations, player, isLoading ->
        RadioUiState(
            isLoading = isLoading && stations.isEmpty(),
            allLibrary = stations.firstOrNull { it.isWholeLibrary },
            categoryStations = stations.filterNot { it.isWholeLibrary },
            playingStationId = player.stationId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadioUiState())

    init {
        viewModelScope.launch {
            catalogRepository.getDiscourses()
            loading.value = false
        }
    }

    /**
     * Starts a station. The first batch is fetched here; [PlayerConnection]
     * keeps it topped up from then on.
     *
     * [displayName] is resolved by the screen because the whole-library station
     * is named by a string resource rather than by catalogue data.
     */
    fun playStation(station: Station, displayName: String) {
        viewModelScope.launch {
            val tracks = catalogRepository.stationTracks(station.id)
            if (tracks.isEmpty()) return@launch
            playerConnection.playStation(station, displayName, tracks)
        }
    }
}
