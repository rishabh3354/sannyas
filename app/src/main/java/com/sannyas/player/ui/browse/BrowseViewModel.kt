package com.sannyas.player.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.ui.navigation.BrowseKind
import com.sannyas.player.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One tile on the browse grid.
 *
 * [id] is whatever the corresponding detail route takes — a topic id, or a
 * category name — so the screen can navigate without knowing which kind it is
 * showing beyond picking the route builder.
 */
data class BrowseTile(val id: String, val name: String, val seriesCount: Int)

data class BrowseUiState(
    val kind: BrowseKind = BrowseKind.TOPICS,
    val isLoading: Boolean = true,
    val tiles: List<BrowseTile> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && tiles.isEmpty()
}

/**
 * Backs the "See all" destination behind both browse rails.
 *
 * Themes keep the catalogue's curated order — it runs from where a newcomer
 * actually arrives towards the more particular — while categories are ranked by
 * size, which is the only ordering a list of traditions supports.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val kind: BrowseKind = savedStateHandle.get<String>(Routes.BROWSE_ARG)
        ?.let { raw -> runCatching { BrowseKind.valueOf(raw) }.getOrNull() }
        ?: BrowseKind.TOPICS

    private val loading = MutableStateFlow(true)

    private val tiles: Flow<List<BrowseTile>> = when (kind) {
        BrowseKind.TOPICS -> catalogRepository.observeTopics().map { topics ->
            topics.map { BrowseTile(it.id, it.name, it.seriesCount) }
        }

        BrowseKind.CATEGORIES -> catalogRepository.observeCategories().map { categories ->
            categories
                .sortedByDescending { it.discourseCount }
                .map { BrowseTile(it.name, it.name, it.discourseCount) }
        }
    }

    val uiState: StateFlow<BrowseUiState> = combine(tiles, loading) { items, isLoading ->
        BrowseUiState(
            kind = kind,
            isLoading = isLoading && items.isEmpty(),
            tiles = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState(kind = kind))

    init {
        viewModelScope.launch {
            catalogRepository.getDiscourses()
            loading.value = false
        }
    }
}
