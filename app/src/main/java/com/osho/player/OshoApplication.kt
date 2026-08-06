package com.osho.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.osho.player.data.download.AudioDownloadService
import com.osho.player.data.local.datastore.SettingsDataStore
import com.osho.player.data.download.DownloadManagerHolder
import com.osho.player.core.locale.AppLocale
import com.osho.player.di.ApplicationScope
import com.osho.player.playback.PlaybackService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject

@HiltAndroidApp
class OshoApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var downloadManagerHolder: DownloadManagerHolder
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    /** Every injected `@ApplicationContext` inherits the chosen app language from here. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        observeDownloadPreferences()
    }

    /**
     * Artwork loader.
     *
     * Series artwork is reused across many screens, so a generous memory cache
     * matters more than a large disk cache; the disk cache mainly serves cold
     * starts and offline browsing.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image-cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .diskCachePolicy(CachePolicy.ENABLED)
        .respectCacheHeaders(false)
        .crossfade(true)
        .crossfade(220)
        .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
        .build()

    private fun createNotificationChannels() {
        AudioDownloadService.ensureChannel(this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return

        // Re-registered on every launch rather than only when missing, so the
        // channel name follows a change of app language. Re-registering an
        // existing channel updates its wording and leaves the importance the
        // user may have set alone.
        manager.createNotificationChannel(
            NotificationChannel(
                PlaybackService.PLAYBACK_CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.playback_channel_description)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /** Keeps the download manager's network requirement in step with settings. */
    private fun observeDownloadPreferences() {
        settings.settings
            .map { it.downloadOverWifiOnly }
            .distinctUntilChanged()
            .onEach { downloadManagerHolder.setWifiOnly(it) }
            .launchIn(appScope)

        settings.settings
            .map { it.maxParallelDownloads }
            .distinctUntilChanged()
            .onEach { downloadManagerHolder.setMaxParallelDownloads(it) }
            .launchIn(appScope)
    }
}
