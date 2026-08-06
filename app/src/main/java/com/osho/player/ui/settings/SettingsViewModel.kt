package com.osho.player.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.osho.player.R
import com.osho.player.core.designsystem.theme.ThemeMode
import com.osho.player.data.local.datastore.SettingsDataStore
import com.osho.player.data.local.datastore.UserSettings
import com.osho.player.domain.model.ContentLanguage
import com.osho.player.di.IoDispatcher
import com.osho.player.di.StreamCache
import com.osho.player.domain.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.datasource.cache.Cache
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val userDataRepository: UserDataRepository,
    @StreamCache private val streamCache: Cache,
    private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val cacheBytes = MutableStateFlow(0L)

    /** A string resource rather than text, so the toast follows the app language. */
    private val message = MutableStateFlow<Int?>(null)

    val settings: StateFlow<UserSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val cacheSize: StateFlow<Long> = cacheBytes.asStateFlow()
    val transientMessage: StateFlow<Int?> = message.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicColor(enabled) }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch { settingsDataStore.setPlaybackSpeed(speed) }
    }

    fun setDefaultSleepTimer(minutes: Int) {
        viewModelScope.launch { settingsDataStore.setSleepTimerMinutes(minutes) }
    }

    fun setMaxParallelDownloads(count: Int) {
        viewModelScope.launch { settingsDataStore.setMaxParallelDownloads(count) }
    }

    fun setDownloadOverWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDownloadOverWifiOnly(enabled) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSkipSilence(enabled) }
    }

    fun setDimPlayerWhenIdle(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDimPlayerWhenIdle(enabled) }
    }

    fun setContentLanguage(language: ContentLanguage) {
        viewModelScope.launch { settingsDataStore.setContentLanguage(language) }
    }

    /**
     * Clears the streaming and image caches only.
     *
     * Explicit downloads live in a separate cache and are deliberately left
     * alone — the user asked for those, and they are managed from Downloads.
     */
    fun clearCache() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                streamCache.keys.forEach { key -> streamCache.removeResource(key) }
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
            }
            refreshCacheSize()
            message.value = R.string.settings_cache_cleared
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            userDataRepository.clearHistory()
            message.value = R.string.settings_history_cleared
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            cacheBytes.value = withContext(ioDispatcher) {
                streamCache.cacheSpace + (context.imageLoader.diskCache?.size ?: 0L)
            }
        }
    }
}
