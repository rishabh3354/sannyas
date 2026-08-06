package com.sannyas.player.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.sannyas.player.R
import com.sannyas.player.core.locale.AppLocale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that runs offline downloads.
 *
 * Media3 owns the queue, retries and byte accounting; this class only supplies
 * the notification and the scheduler that restarts unfinished work after a
 * reboot or a network change.
 */
@AndroidEntryPoint
class AudioDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {

    @Inject lateinit var downloadManagerProvider: DownloadManagerHolder

    /** Notification copy follows the app language, not the device locale. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = downloadManagerProvider.downloadManager

    override fun getScheduler(): Scheduler? = downloadManagerProvider.scheduler

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = notificationHelper.buildProgressNotification(
        /* context = */ this,
        /* smallIcon = */ android.R.drawable.stat_sys_download,
        /* contentIntent = */ null,
        /* message = */ getString(R.string.downloading),
        /* downloads = */ downloads,
        /* notMetRequirements = */ notMetRequirements,
    )

    companion object {
        const val CHANNEL_ID = "osho_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2001

        /** Downloads resume automatically once the network requirement is met again. */
        val REQUIREMENTS_WIFI = Requirements(Requirements.NETWORK_UNMETERED)
        val REQUIREMENTS_ANY = Requirements(Requirements.NETWORK)

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
                ?: return
            // Re-registered every launch so the channel name follows the app
            // language; an existing channel keeps whatever importance the user set.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.download_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.download_channel_description)
                    setShowBadge(false)
                },
            )
        }
    }
}
