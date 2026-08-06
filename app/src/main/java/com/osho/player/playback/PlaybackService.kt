package com.osho.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.osho.player.MainActivity
import com.osho.player.data.local.datastore.SettingsDataStore
import com.osho.player.R
import com.osho.player.core.locale.AppLocale
import com.osho.player.domain.repository.CatalogRepository
import com.osho.player.domain.repository.UserDataRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The playback service.
 *
 * Extends [MediaLibraryService] rather than plain `MediaSessionService` so the
 * catalog is browsable from Android Auto and Assistant. The service owns the
 * only [ExoPlayer] instance in the process; the UI talks to it exclusively
 * through a `MediaController` (see [PlayerConnection]), which means playback
 * survives the Activity being destroyed.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var catalogRepository: CatalogRepository
    @Inject lateinit var userDataRepository: UserDataRepository
    @Inject lateinit var cacheDataSourceFactory: DataSource.Factory
    @Inject lateinit var sleepTimer: SleepTimerController
    @Inject lateinit var recovery: PlaybackRecovery
    @Inject lateinit var voiceSearch: VoiceSearch
    @Inject lateinit var settings: SettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null

    /** Notification and Android Auto copy follows the app language. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            // Handles ducking and pauses on a phone call or another app taking focus.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pauses instead of continuing out loud when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(openAppIntent())
            .setCustomLayout(customLayout())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .build(),
        )

        sleepTimer.attach(player, serviceScope)
        recovery.attach(player, serviceScope)
        player.addListener(recoveryListener)
        observeProgress()
        observeSettings()
    }

    /**
     * Applies preferences that live on the player itself.
     *
     * `MediaController` has no API for skip-silence, so rather than plumb a
     * custom session command through for a single boolean, the service watches
     * the same DataStore the settings screen writes to.
     */
    private fun observeSettings() {
        settings.settings
            .map { it.skipSilence }
            .distinctUntilChanged()
            .onEach { player.skipSilenceEnabled = it }
            .launchIn(serviceScope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    /**
     * Stops the service when the user swipes the app away while paused. If audio
     * is still playing we keep running, which is what listeners expect from a
     * media app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /**
     * Retry lives on the service's own player, not on the controller: the blip
     * that matters happens with the screen off and no Activity alive.
     */
    private val recoveryListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) = recovery.onPlayerError(error)

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) recovery.onPlaybackHealthy()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recovery.onNewItem()
        }
    }

    override fun onDestroy() {
        recovery.detach()
        sleepTimer.detach()
        serviceScope.cancel()
        mediaLibrarySession?.run {
            release()
            player.release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    /**
     * Persists the resume position.
     *
     * Two triggers, because a timer alone is not enough: the slow tick keeps
     * Continue Listening fresh during a long talk, and the listener captures the
     * exact moment playback stops or moves on. Without the latter, pausing and
     * closing the app discarded everything since the last tick — and, worse, a
     * talk played for under one tick never appeared in Continue Listening at all.
     */
    private fun observeProgress() {
        serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                if (player.isPlaying) saveCurrentPosition()
            }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveCurrentPosition()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Records where the outgoing talk was left before the player
                // has moved on to the next one.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    saveCurrentPosition()
                }
            }
        })
    }

    private fun saveCurrentPosition() {
        val item = player.currentMediaItem ?: return
        val track = MediaItems.toTrack(item) ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: track.durationMs.takeIf { it > 0 }
            ?: return
        val position = player.currentPosition

        serviceScope.launch {
            userDataRepository.saveProgress(
                trackId = track.id,
                discourseId = track.discourseId,
                positionMs = position,
                durationMs = duration,
            )
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Extra transport controls surfaced in the notification and on Auto. */
    private fun customLayout(): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder()
            .setDisplayName(getString(R.string.media_rewind_15))
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(COMMAND_SEEK_BACK, android.os.Bundle.EMPTY))
            .build(),
        CommandButton.Builder()
            .setDisplayName(getString(R.string.media_forward_30))
            .setIconResId(android.R.drawable.ic_media_ff)
            .setSessionCommand(SessionCommand(COMMAND_SEEK_FORWARD, android.os.Bundle.EMPTY))
            .build(),
    )

    /**
     * Exposes the catalog as a browsable tree: root → categories → series →
     * tracks. Android Auto and Assistant walk this hierarchy.
     */
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(COMMAND_SEEK_BACK, android.os.Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SEEK_FORWARD, android.os.Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_SEEK_BACK -> player.seekBack()
                COMMAND_SEEK_FORWARD -> player.seekForward()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(MediaTree.rootItem(this@PlaybackService), params),
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            serviceScope.future {
                val children = MediaTree.children(this@PlaybackService, catalogRepository, parentId)
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val item = MediaTree.item(this@PlaybackService, catalogRepository, mediaId)
            if (item == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItem(item, null)
            }
        }

        /**
         * Android Auto's own search box, and any `MediaBrowser` asking on a
         * voice command's behalf. The contract is two-step: acknowledge the
         * query, publish how many results there are, then hand them over when
         * asked.
         */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val hits = voiceSearch.resolve(query)
            session.notifySearchResultChanged(browser, query, hits.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val hits = voiceSearch.resolve(query)
                .drop(page * pageSize)
                .take(pageSize)
                .map(MediaItems::toMediaItem)
            LibraryResult.ofItemList(ImmutableList.copyOf(hits), params)
        }

        /**
         * Browsable ids carry no playable URI, so when Auto asks to play one we
         * resolve it back to real tracks here.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            mediaItems.flatMap { item ->
                when {
                    item.localConfiguration != null -> listOf(item)
                    else -> MediaTree.resolvePlayable(catalogRepository, item.mediaId)
                }
            }.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            // Restores the last listened track so a headset button press after a
            // cold start resumes where the user left off.
            val latest = userDataRepository.observeProgress().first()
                .maxByOrNull { it.updatedAt }
            val track = latest?.let { catalogRepository.getTrack(it.trackId) }
            if (track == null) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
            } else {
                val discourse = catalogRepository.getDiscourse(track.discourseId)
                val queue = discourse?.tracks ?: listOf(track)
                MediaSession.MediaItemsWithStartPosition(
                    queue.map(MediaItems::toMediaItem),
                    queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                    latest.positionMs,
                )
            }
        }
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "osho_playback"
        const val PLAYBACK_NOTIFICATION_ID = 1001

        const val COMMAND_SEEK_BACK = "com.osho.player.SEEK_BACK"
        const val COMMAND_SEEK_FORWARD = "com.osho.player.SEEK_FORWARD"

        const val SEEK_BACK_MS = 15_000L
        const val SEEK_FORWARD_MS = 30_000L

        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}
