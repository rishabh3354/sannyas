package com.osho.player.data.repository

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.osho.player.data.download.AudioDownloadService
import com.osho.player.data.download.DownloadManagerHolder
import com.osho.player.data.local.database.dao.DownloadMetadataDao
import com.osho.player.di.IoDispatcher
import com.osho.player.data.local.database.entity.DownloadMetadataEntity
import com.osho.player.domain.model.Discourse
import com.osho.player.domain.model.DownloadState
import com.osho.player.domain.model.DownloadStatus
import com.osho.player.domain.model.Track
import com.osho.player.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.exoplayer.offline.DownloadManager as Media3DownloadManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Offline downloads on top of Media3's [Media3DownloadManager].
 *
 * Media3 is the source of truth for state and byte counts. A small Room table
 * adds the series/title metadata the download index does not carry, so the
 * Downloads screen can group and label entries without the catalog being loaded.
 */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val context: Context,
    private val holder: DownloadManagerHolder,
    private val metadataDao: DownloadMetadataDao,
    private val downloadCache: Cache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DownloadRepository {

    private val manager get() = holder.downloadManager

    /**
     * Emits on every download event, plus a slow tick while work is in flight.
     * Media3 only fires events on state changes, so the tick is what makes the
     * progress bars move.
     */
    private val downloads: Flow<List<Download>> = callbackFlow {
        fun snapshot(): List<Download> = buildList {
            manager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) add(cursor.download)
            }
        }

        val listener = object : Media3DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: Media3DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                trySend(snapshot())
            }

            override fun onDownloadRemoved(
                downloadManager: Media3DownloadManager,
                download: Download,
            ) {
                trySend(snapshot())
            }
        }

        manager.addListener(listener)
        trySend(snapshot())

        // The ticker runs as a child job so that `awaitClose` is actually
        // reached; polling inline here would keep the builder suspended forever
        // and the listener would never be removed on cancellation.
        val ticker = launch {
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                if (manager.currentDownloads.isNotEmpty()) trySend(snapshot())
            }
        }

        awaitClose {
            ticker.cancel()
            manager.removeListener(listener)
        }
    }.flowOn(ioDispatcher).conflate()

    override fun observeAll(): Flow<Map<String, DownloadStatus>> =
        downloads.map { list -> list.associate { it.request.id to it.toStatus() } }
            .distinctUntilChanged()

    override fun observeStatus(trackId: String): Flow<DownloadStatus> =
        observeAll().map { it[trackId] ?: DownloadStatus(trackId, DownloadState.NOT_DOWNLOADED) }
            .distinctUntilChanged()

    override fun observeDownloadedTrackIds(): Flow<Set<String>> =
        downloads.map { list ->
            list.filter { it.state == Download.STATE_COMPLETED }
                .map { it.request.id }
                .toSet()
        }.distinctUntilChanged()

    /**
     * Reports what the download cache actually occupies on disk rather than the
     * sum of per-download counters, so partially written and orphaned spans are
     * included and the figure matches the system storage settings.
     */
    override fun observeStorageUsage(): Flow<Long> =
        downloads.map { downloadCache.cacheSpace }.distinctUntilChanged()

    override fun observePaused(): Flow<Boolean> =
        downloads.map { manager.downloadsPaused }.distinctUntilChanged()

    override suspend fun download(track: Track) = withContext(ioDispatcher) {
        metadataDao.upsert(
            DownloadMetadataEntity(
                trackId = track.id,
                discourseId = track.discourseId,
                title = track.title,
                discourseTitle = track.discourseTitle,
                artworkUrl = track.artworkUrl,
                durationSeconds = track.durationSeconds,
                requestedAt = System.currentTimeMillis(),
            ),
        )

        val request = DownloadRequest.Builder(track.id, android.net.Uri.parse(track.url))
            .setMimeType(MediaItem.fromUri(track.url).localConfiguration?.mimeType)
            .build()

        DownloadService.sendAddDownload(
            context,
            AudioDownloadService::class.java,
            request,
            /* foreground = */ false,
        )
    }

    override suspend fun downloadAll(discourse: Discourse) {
        discourse.tracks.forEach { download(it) }
    }

    override suspend fun remove(trackId: String) = withContext(ioDispatcher) {
        metadataDao.delete(trackId)
        DownloadService.sendRemoveDownload(
            context,
            AudioDownloadService::class.java,
            trackId,
            /* foreground = */ false,
        )
    }

    override suspend fun removeAll(discourseId: String) = withContext(ioDispatcher) {
        metadataDao.forDiscourse(discourseId).forEach { remove(it.trackId) }
    }

    override suspend fun removeEverything() = withContext(ioDispatcher) {
        metadataDao.clear()
        DownloadService.sendRemoveAllDownloads(
            context,
            AudioDownloadService::class.java,
            /* foreground = */ false,
        )
    }

    override suspend fun pauseAll() = withContext(ioDispatcher) {
        DownloadService.sendPauseDownloads(
            context,
            AudioDownloadService::class.java,
            /* foreground = */ false,
        )
    }

    override suspend fun resumeAll() = withContext(ioDispatcher) {
        DownloadService.sendResumeDownloads(
            context,
            AudioDownloadService::class.java,
            /* foreground = */ false,
        )
    }

    private fun Download.toStatus(): DownloadStatus {
        val total = if (contentLength > 0) contentLength else 0L
        return DownloadStatus(
            trackId = request.id,
            state = when (state) {
                Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadState.QUEUED
                Download.STATE_DOWNLOADING -> DownloadState.DOWNLOADING
                Download.STATE_COMPLETED -> DownloadState.DOWNLOADED
                Download.STATE_FAILED -> DownloadState.FAILED
                Download.STATE_REMOVING -> DownloadState.REMOVING
                else -> DownloadState.NOT_DOWNLOADED
            },
            // percentDownloaded is -1 until the content length is known.
            progress = (percentDownloaded.takeIf { it >= 0f } ?: 0f) / 100f,
            bytesDownloaded = bytesDownloaded,
            totalBytes = total,
        )
    }

    private companion object {
        const val PROGRESS_TICK_MS = 700L
    }
}
