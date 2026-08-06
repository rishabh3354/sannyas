package com.osho.player.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osho.player.core.designsystem.theme.ThemeMode
import com.osho.player.domain.model.ContentLanguage
import com.osho.player.domain.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "osho_settings")

/** User preferences that survive process death. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val defaultPlaybackSpeed: Float = 1f,
    /** Default sleep-timer length in minutes; 0 means "off". */
    val defaultSleepTimerMinutes: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Restored on cold start so the mini player is populated immediately. */
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    /** Off by default: silently refusing to download on mobile data reads as a bug. */
    val downloadOverWifiOnly: Boolean = false,
    /**
     * How many talks may download at once.
     *
     * Three is the default because the audio comes from one modest third-party
     * host: more streams in parallel mostly means the host serving each of them
     * slower, and it competes with playback for the same connection.
     */
    val maxParallelDownloads: Int = DEFAULT_PARALLEL_DOWNLOADS,
    val skipSilence: Boolean = false,
    /** Fades the player screen down after a spell of not being touched. */
    /** Off by default: a screen dimming on its own surprises anyone not in bed. */
    val dimPlayerWhenIdle: Boolean = false,
    val contentLanguage: ContentLanguage = ContentLanguage.ALL,
    /** False until the first-run language prompt has been answered. */
    val languageChosen: Boolean = false,
)

/**
 * Preference storage.
 *
 * Reads are exposed as a single [settings] flow so callers never have to
 * combine keys themselves, and [IOException]s from a corrupt file degrade to
 * defaults rather than crashing the app.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SLEEP_TIMER_MINUTES = longPreferencesKey("sleep_timer_minutes")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION = longPreferencesKey("last_position")
        val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val MAX_PARALLEL_DOWNLOADS = intPreferencesKey("max_parallel_downloads")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val DIM_PLAYER = booleanPreferencesKey("dim_player_when_idle")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val LANGUAGE_CHOSEN = booleanPreferencesKey("language_chosen")
    }

    val settings: Flow<UserSettings> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            UserSettings(
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.DARK,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                defaultPlaybackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1f,
                defaultSleepTimerMinutes = (prefs[Keys.SLEEP_TIMER_MINUTES] ?: 0L).toInt(),
                shuffleEnabled = prefs[Keys.SHUFFLE] ?: false,
                repeatMode = prefs[Keys.REPEAT_MODE]?.let { runCatching { RepeatMode.valueOf(it) }.getOrNull() }
                    ?: RepeatMode.OFF,
                lastTrackId = prefs[Keys.LAST_TRACK_ID],
                lastPositionMs = prefs[Keys.LAST_POSITION] ?: 0L,
                downloadOverWifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
                maxParallelDownloads = prefs[Keys.MAX_PARALLEL_DOWNLOADS]
                    ?: DEFAULT_PARALLEL_DOWNLOADS,
                skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
                dimPlayerWhenIdle = prefs[Keys.DIM_PLAYER] ?: false,
                contentLanguage = prefs[Keys.CONTENT_LANGUAGE]
                    ?.let { runCatching { ContentLanguage.valueOf(it) }.getOrNull() }
                    ?: ContentLanguage.ALL,
                languageChosen = prefs[Keys.LANGUAGE_CHOSEN] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setPlaybackSpeed(speed: Float) = edit { it[Keys.PLAYBACK_SPEED] = speed }

    suspend fun setSleepTimerMinutes(minutes: Int) =
        edit { it[Keys.SLEEP_TIMER_MINUTES] = minutes.toLong() }

    suspend fun setShuffle(enabled: Boolean) = edit { it[Keys.SHUFFLE] = enabled }

    suspend fun setRepeatMode(mode: RepeatMode) = edit { it[Keys.REPEAT_MODE] = mode.name }

    suspend fun setSkipSilence(enabled: Boolean) = edit { it[Keys.SKIP_SILENCE] = enabled }

    suspend fun setDimPlayerWhenIdle(enabled: Boolean) = edit { it[Keys.DIM_PLAYER] = enabled }

    /** Records the choice and marks the first-run prompt as answered. */
    suspend fun setContentLanguage(language: ContentLanguage) = edit {
        it[Keys.CONTENT_LANGUAGE] = language.name
        it[Keys.LANGUAGE_CHOSEN] = true
    }

    suspend fun setDownloadOverWifiOnly(enabled: Boolean) = edit { it[Keys.WIFI_ONLY] = enabled }

    suspend fun setMaxParallelDownloads(count: Int) =
        edit { it[Keys.MAX_PARALLEL_DOWNLOADS] = count.coerceIn(1, MAX_PARALLEL_DOWNLOADS_LIMIT) }

    suspend fun setLastPlayed(trackId: String?, positionMs: Long) = edit { prefs ->
        if (trackId == null) prefs.remove(Keys.LAST_TRACK_ID) else prefs[Keys.LAST_TRACK_ID] = trackId
        prefs[Keys.LAST_POSITION] = positionMs
    }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}

/** Enough to keep the pipe busy without hammering the host. */
const val DEFAULT_PARALLEL_DOWNLOADS = 3

/** Beyond this the host, not the phone, becomes the bottleneck. */
const val MAX_PARALLEL_DOWNLOADS_LIMIT = 5
