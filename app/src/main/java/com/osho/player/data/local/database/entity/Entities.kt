package com.osho.player.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Only user-generated state lives in the database. Catalog content stays in the
 * bundled JSON asset, so swapping in a new `discourses.json` never requires a
 * schema migration — rows reference catalog ids as opaque strings.
 */

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val discourseId: String,
    val addedAt: Long,
)

/**
 * A single favourited talk.
 *
 * Separate from [FavouriteEntity] rather than a `type` column on it: liking one
 * talk out of fifty is a different statement from liking the series, and the
 * two lists are browsed separately.
 */
@Entity(
    tableName = "favourite_tracks",
    indices = [Index("discourseId"), Index("addedAt")],
)
data class FavouriteTrackEntity(
    @PrimaryKey val trackId: String,
    val discourseId: String,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    /** Bumped on every edit, so the list can lead with what is being worked on. */
    val updatedAt: Long,
)

/**
 * A talk's place in a playlist.
 *
 * The composite key means a talk cannot appear twice in one playlist — for a
 * library of hour-long discourses that is a guard rather than a limitation, and
 * it lets the "add to playlist" sheet show a simple ticked/unticked state.
 *
 * [position] is dense and rewritten wholesale on a reorder, which is cheaper to
 * reason about than sparse ranks for lists of this size.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId"), Index("trackId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val discourseId: String,
    val position: Int,
    val addedAt: Long,
)

@Entity(
    tableName = "history",
    indices = [Index("playedAt"), Index("discourseId")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val discourseId: String,
    val playedAt: Long,
)

@Entity(
    tableName = "playback_progress",
    indices = [Index("updatedAt"), Index("discourseId")],
)
data class PlaybackProgressEntity(
    @PrimaryKey val trackId: String,
    val discourseId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    indices = [Index("trackId"), Index("createdAt")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val discourseId: String,
    val positionMs: Long,
    val note: String,
    val createdAt: Long,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/**
 * Metadata for a download.
 *
 * Media3's [androidx.media3.exoplayer.offline.DownloadIndex] owns download
 * state and byte counts; this table only stores what the index cannot — which
 * series a downloaded track belongs to, so Downloads can be grouped and a whole
 * series removed at once.
 */
@Entity(
    tableName = "download_metadata",
    indices = [Index("discourseId")],
)
data class DownloadMetadataEntity(
    @PrimaryKey val trackId: String,
    val discourseId: String,
    val title: String,
    val discourseTitle: String,
    val artworkUrl: String,
    val durationSeconds: Long,
    val requestedAt: Long,
)
