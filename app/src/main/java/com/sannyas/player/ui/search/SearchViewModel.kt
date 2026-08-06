package com.sannyas.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sannyas.player.data.local.datastore.SettingsDataStore
import com.sannyas.player.domain.model.ContentLanguage
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.DiscourseLanguage
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.SearchResults
import com.sannyas.player.domain.repository.UserDataRepository
import com.sannyas.player.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: SearchResults = SearchResults(),
    val suggestions: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val browseCategories: List<com.sannyas.player.domain.model.Category> = emptyList(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val showEmptyResults: Boolean get() = hasQuery && !isSearching && results.isEmpty
}

/**
 * Search.
 *
 * The catalog lives in memory, so matching itself is fast; the debounce exists
 * to avoid re-ranking 173 series on every keystroke, not to wait on IO. A short
 * 120 ms window still feels instant while collapsing a burst of typing into one
 * pass.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val userDataRepository: UserDataRepository,
    private val settings: SettingsDataStore,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    private val searching = MutableStateFlow(false)

    private val results: StateFlow<SearchResults> = query
        .debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            flow {
                if (text.isBlank()) {
                    emit(SearchResults())
                } else {
                    searching.value = true
                    emit(catalogRepository.search(text))
                    searching.value = false
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private val suggestions: StateFlow<List<String>> = query
        .debounce { if (it.isBlank()) 0L else SUGGEST_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            flow { emit(if (text.isBlank()) emptyList() else catalogRepository.suggestions(text)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val categories: StateFlow<List<com.sannyas.player.domain.model.Category>> =
        catalogRepository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        searching,
        results,
        suggestions,
        combine(
            userDataRepository.observeRecentSearches(),
            categories,
        ) { recent, cats -> recent to cats },
    ) { text, isSearching, searchResults, suggestionList, (recent, cats) ->
        SearchUiState(
            query = text,
            isSearching = isSearching,
            results = searchResults,
            suggestions = suggestionList,
            recentSearches = recent,
            browseCategories = cats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun clearQuery() {
        query.value = ""
    }

    /** Called when the user commits a query, so only deliberate searches persist. */
    fun onSearchSubmitted() {
        val text = query.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch { userDataRepository.recordSearch(text) }
    }

    fun onSuggestionSelected(suggestion: String) {
        query.value = suggestion
        viewModelScope.launch { userDataRepository.recordSearch(suggestion) }
    }

    fun removeRecentSearch(text: String) {
        viewModelScope.launch { userDataRepository.removeSearch(text) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { userDataRepository.clearSearches() }
    }

    /** Plays a track found by search inside its own series queue. */
    fun playTrack(trackId: String) {
        viewModelScope.launch {
            val track = catalogRepository.getTrack(trackId) ?: return@launch
            val discourse = catalogRepository.getDiscourse(track.discourseId)
            discourse?.let { adoptLanguageOf(it) }
            val queue = discourse?.tracks ?: listOf(track)
            playerConnection.play(queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
        }
    }

    /**
     * Called when a search result is opened.
     *
     * Because search spans both languages, a listener browsing English can land
     * on a Hindi series. Acting on that result is a clear signal of intent, so
     * the preference follows them rather than leaving the app filtered to a
     * language they have just navigated away from — which would show an empty
     * series list on the way back.
     */
    fun onResultOpened(discourseId: String) {
        viewModelScope.launch {
            catalogRepository.getDiscourse(discourseId)?.let { adoptLanguageOf(it) }
        }
    }

    private suspend fun adoptLanguageOf(discourse: Discourse) {
        val current = settings.settings.first().contentLanguage
        if (current == ContentLanguage.ALL || current.accepts(discourse.language)) return
        settings.setContentLanguage(
            when (discourse.language) {
                DiscourseLanguage.HINDI -> ContentLanguage.HINDI
                DiscourseLanguage.ENGLISH -> ContentLanguage.ENGLISH
            },
        )
    }

    fun playDiscourse(discourse: Discourse) = playerConnection.play(discourse.tracks)

    private companion object {
        const val DEBOUNCE_MS = 120L
        const val SUGGEST_DEBOUNCE_MS = 80L
    }
}
