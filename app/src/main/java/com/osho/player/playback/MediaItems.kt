package com.osho.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.osho.player.domain.model.Track

/**
 * Conversion between the domain [Track] and Media3's [MediaItem].
 *
 * Everything needed to rebuild a [Track] is written into the metadata `extras`
 * so the UI can render the queue straight from the player, with no lookup back
 * into the catalog — which matters when the service is restored by the system
 * before the app process has loaded anything.
 */
object MediaItems {

    private const val EXTRA_DISCOURSE_ID = "osho.discourseId"
    private const val EXTRA_DISCOURSE_TITLE = "osho.discourseTitle"
    private const val EXTRA_URL = "osho.url"
    private const val EXTRA_DURATION = "osho.durationSeconds"
    private const val EXTRA_INDEX = "osho.index"
    private const val EXTRA_NUMBER = "osho.number"
    private const val EXTRA_SPEAKER = "osho.speaker"

    fun toMediaItem(track: Track): MediaItem {
        val artwork = track.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse)

        val extras = Bundle().apply {
            putString(EXTRA_DISCOURSE_ID, track.discourseId)
            putString(EXTRA_DISCOURSE_TITLE, track.discourseTitle)
            putString(EXTRA_URL, track.url)
            putLong(EXTRA_DURATION, track.durationSeconds)
            putInt(EXTRA_INDEX, track.index)
            putInt(EXTRA_NUMBER, track.number)
            putString(EXTRA_SPEAKER, track.speaker)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.speaker)
            .setAlbumTitle(track.discourseTitle)
            .setAlbumArtist(track.speaker)
            .setArtworkUri(artwork)
            .setTrackNumber(track.index)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.url)
            .setMediaMetadata(metadata)
            // Lets the player resolve a cached copy before hitting the network.
            .setCustomCacheKey(track.id)
            .build()
    }

    /** Rebuilds a [Track] from a [MediaItem]. Returns null if the extras are missing. */
    fun toTrack(item: MediaItem?): Track? {
        if (item == null) return null
        val metadata = item.mediaMetadata
        val extras = metadata.extras ?: return null
        val url = extras.getString(EXTRA_URL)
            ?: item.localConfiguration?.uri?.toString()
            ?: return null

        return Track(
            id = item.mediaId,
            discourseId = extras.getString(EXTRA_DISCOURSE_ID).orEmpty(),
            title = metadata.title?.toString().orEmpty(),
            url = url,
            durationSeconds = extras.getLong(EXTRA_DURATION),
            index = extras.getInt(EXTRA_INDEX, 1),
            // Falls back to the queue position for items written by an older build.
            number = extras.getInt(EXTRA_NUMBER, extras.getInt(EXTRA_INDEX, 1)),
            artworkUrl = metadata.artworkUri?.toString().orEmpty(),
            discourseTitle = extras.getString(EXTRA_DISCOURSE_TITLE)
                ?: metadata.albumTitle?.toString().orEmpty(),
            speaker = extras.getString(EXTRA_SPEAKER)
                ?: metadata.artist?.toString().orEmpty(),
        )
    }
}
