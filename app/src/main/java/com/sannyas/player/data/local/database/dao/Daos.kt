package com.sannyas.player.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.sannyas.player.data.local.database.entity.BookmarkEntity
import com.sannyas.player.data.local.database.entity.DownloadMetadataEntity
import androidx.room.Transaction
import com.sannyas.player.data.local.database.entity.FavouriteEntity
import com.sannyas.player.data.local.database.entity.FavouriteTrackEntity
import com.sannyas.player.data.local.database.entity.PlaylistEntity
import com.sannyas.player.data.local.database.entity.PlaylistTrackEntity
import com.sannyas.player.data.local.database.entity.HistoryEntity
import com.sannyas.player.data.local.database.entity.PlaybackProgressEntity
import com.sannyas.player.data.local.database.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {

    @Query("SELECT discourseId FROM favourites ORDER BY addedAt DESC")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE discourseId = :discourseId)")
    fun observeIsFavourite(discourseId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE discourseId = :discourseId)")
    suspend fun isFavourite(discourseId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE discourseId = :discourseId")
    suspend fun delete(discourseId: String)
}

@Dao
interface HistoryDao {

    /**
     * Most recent play per track. Grouping keeps the list free of the duplicates
     * that would otherwise appear when a track is replayed.
     */
    @Query(
        """
        SELECT * FROM history
        WHERE id IN (SELECT MAX(id) FROM history GROUP BY trackId)
        ORDER BY playedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity)

    /** Caps the table so history never grows without bound. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface PlaybackProgressDao {

    @Query("SELECT * FROM playback_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE trackId = :trackId")
    fun observeFor(trackId: String): Flow<PlaybackProgressEntity?>

    @Query("SELECT * FROM playback_progress WHERE trackId = :trackId")
    suspend fun get(trackId: String): PlaybackProgressEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM playback_progress")
    suspend fun clear()
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE trackId = :trackId ORDER BY positionMs ASC")
    fun observeFor(trackId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(entity: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecentSearchDao {

    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()

    @Query("DELETE FROM recent_searches WHERE `query` NOT IN (SELECT `query` FROM recent_searches ORDER BY searchedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface DownloadMetadataDao {

    @Query("SELECT * FROM download_metadata ORDER BY requestedAt DESC")
    fun observeAll(): Flow<List<DownloadMetadataEntity>>

    @Query("SELECT * FROM download_metadata WHERE discourseId = :discourseId")
    suspend fun forDiscourse(discourseId: String): List<DownloadMetadataEntity>

    @Upsert
    suspend fun upsert(entity: DownloadMetadataEntity)

    @Query("DELETE FROM download_metadata WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM download_metadata")
    suspend fun clear()
}

@Dao
interface FavouriteTrackDao {

    @Query("SELECT * FROM favourite_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteTrackEntity>>

    @Query("SELECT trackId FROM favourite_tracks ORDER BY addedAt DESC")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_tracks WHERE trackId = :trackId)")
    fun observeIsFavourite(trackId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_tracks WHERE trackId = :trackId)")
    suspend fun isFavourite(trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavouriteTrackEntity)

    @Query("DELETE FROM favourite_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: String)
}

/** One row per playlist, with the count the list screen needs. */
data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount
        FROM playlists p
        ORDER BY p.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    /** Which playlists already hold this talk, for the add-to-playlist sheet. */
    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    fun observePlaylistIdsContaining(trackId: String): Flow<List<Long>>

    @Insert
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, updatedAt: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(entity: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrack(playlistId: Long, trackId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: String, position: Int)

    /**
     * Appends in one go so the caller cannot interleave two adds and hand out
     * the same position twice.
     */
    @Transaction
    suspend fun append(playlistId: Long, trackId: String, discourseId: String, now: Long) {
        insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                discourseId = discourseId,
                position = nextPosition(playlistId),
                addedAt = now,
            ),
        )
        touch(playlistId, now)
    }

    /** Rewrites every position from an ordered list of track ids. */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<String>, now: Long) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            setPosition(playlistId, trackId, index)
        }
        touch(playlistId, now)
    }

    @Transaction
    suspend fun remove(playlistId: Long, trackId: String, now: Long) {
        deleteTrack(playlistId, trackId)
        touch(playlistId, now)
    }
}
