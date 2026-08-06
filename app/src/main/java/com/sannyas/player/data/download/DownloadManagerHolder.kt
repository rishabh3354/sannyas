package com.sannyas.player.data.download

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import com.sannyas.player.di.IoDispatcher
import com.sannyas.player.di.UpstreamDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the singleton [DownloadManager].
 *
 * [AudioDownloadService] is created by the framework and cannot take
 * constructor arguments, so it reaches the manager through this injected
 * holder. Keeping the manager here — rather than in the service — also means
 * the repository can observe downloads while no service is running.
 */
@Singleton
class DownloadManagerHolder @Inject constructor(
    private val context: Context,
    databaseProvider: DatabaseProvider,
    downloadCache: Cache,
    @UpstreamDataSource upstreamDataSourceFactory: DataSource.Factory,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        upstreamDataSourceFactory,
        ioDispatcher.asExecutor(),
    ).apply {
        // Three at a time keeps throughput high without starving playback of
        // bandwidth when the user starts listening mid-download.
        maxParallelDownloads = 3
        requirements = Requirements(Requirements.NETWORK)
    }

    /**
     * Restarts pending downloads after reboot. [PlatformScheduler] needs
     * API 21+ and the JobScheduler permission, both of which we have.
     */
    val scheduler: Scheduler = PlatformScheduler(context, JOB_ID)

    fun setMaxParallelDownloads(count: Int) {
        downloadManager.maxParallelDownloads = count.coerceAtLeast(1)
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        downloadManager.requirements =
            if (wifiOnly) AudioDownloadService.REQUIREMENTS_WIFI else AudioDownloadService.REQUIREMENTS_ANY
    }

    private companion object {
        const val JOB_ID = 4021
    }
}
