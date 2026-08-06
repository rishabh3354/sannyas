package com.sannyas.player.domain.model

import androidx.annotation.StringRes

/** How the queue advances when the current track ends. */
enum class RepeatMode { OFF, ALL, ONE }

/** Snapshot of everything the UI needs to render a player, mini or full. */
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** A string resource, so the snackbar follows the app language. */
    @StringRes val errorMessageRes: Int? = null,
    /** Set while a station is playing; null for an ordinary series queue. */
    val stationName: String? = null,
    /** Lets Radio mark the tile that is currently on air. */
    val stationId: String? = null,
) {
    val isActive: Boolean get() = currentTrack != null

    val isRadio: Boolean get() = stationId != null

    /** 0f..1f, guarded against the unknown duration ExoPlayer reports while buffering. */
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val bufferedProgress: Float
        get() = if (durationMs > 0L) {
            (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/** A resumable listening position. Drives "Continue Listening". */
data class PlaybackProgress(
    val trackId: String,
    val discourseId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Treated as finished once within two minutes of the end. */
    val isCompleted: Boolean
        get() = durationMs > 0L && positionMs >= durationMs - COMPLETION_TOLERANCE_MS

    /** Ignore accidental taps so they do not pollute Continue Listening. */
    val isMeaningful: Boolean
        get() = positionMs > MEANINGFUL_THRESHOLD_MS && !isCompleted

    companion object {
        const val COMPLETION_TOLERANCE_MS = 2 * 60 * 1000L
        const val MEANINGFUL_THRESHOLD_MS = 30 * 1000L
    }
}

/** A user-saved moment inside a track. */
data class Bookmark(
    val id: Long,
    val trackId: String,
    val discourseId: String,
    val positionMs: Long,
    val note: String,
    val createdAt: Long,
)

/** An entry in the listening history. */
data class HistoryEntry(
    val trackId: String,
    val discourseId: String,
    val playedAt: Long,
)

/** Lifecycle of an offline download, mirrored from Media3's DownloadManager. */
enum class DownloadState { NOT_DOWNLOADED, QUEUED, DOWNLOADING, DOWNLOADED, FAILED, REMOVING }

data class DownloadStatus(
    val trackId: String,
    val state: DownloadState,
    /** 0f..1f; only meaningful while [state] is [DownloadState.DOWNLOADING]. */
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
)

/** Sleep-timer configuration. */
sealed interface SleepTimer {
    data object Off : SleepTimer

    /** Stops playback after [totalMs] have elapsed. */
    data class Duration(
        val totalMs: Long,
        val remainingMs: Long,
        /** Length of the closing volume ramp; 0 disables it. */
        val fadeMs: Long = 0L,
    ) : SleepTimer {
        /** True once the volume has started coming down. */
        val isFading: Boolean get() = fadeMs > 0L && remainingMs <= fadeMs
    }

    /** Stops playback when the current track finishes. */
    data object EndOfTrack : SleepTimer
}
