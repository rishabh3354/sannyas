package com.sannyas.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sannyas.player.data.local.datastore.SettingsDataStore
import com.sannyas.player.data.local.datastore.UserSettings
import com.sannyas.player.domain.model.ContentLanguage
import com.sannyas.player.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the settings the whole app is themed from, and gates the splash screen
 * until both settings and the catalog are ready.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    /** Read by the splash screen's keep-on-screen condition. */
    var isReady: Boolean = false
        private set

    val settingsState: StateFlow<UserSettings> = settings.settings
        .onEach { isReady = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserSettings(),
        )

    init {
        // Warms the catalog so the first frame of Home has data rather than
        // shimmer, and so search is instant on first use.
        viewModelScope.launch { catalogRepository.refresh() }
    }

    fun chooseContentLanguage(language: ContentLanguage) {
        viewModelScope.launch { settings.setContentLanguage(language) }
    }
}
