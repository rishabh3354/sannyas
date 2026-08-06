package com.sannyas.player.data.repository

import com.sannyas.player.data.local.database.dao.BookmarkDao
import com.sannyas.player.data.local.database.dao.FavouriteDao
import com.sannyas.player.data.local.database.dao.FavouriteTrackDao
import com.sannyas.player.data.local.database.dao.HistoryDao
import com.sannyas.player.data.local.database.dao.PlaybackProgressDao
import com.sannyas.player.data.local.database.dao.RecentSearchDao
import com.sannyas.player.data.local.database.entity.BookmarkEntity
import com.sannyas.player.data.local.database.entity.FavouriteEntity
import com.sannyas.player.data.local.database.entity.FavouriteTrackEntity
import com.sannyas.player.data.local.database.entity.HistoryEntity
import com.sannyas.player.data.local.database.entity.PlaybackProgressEntity
import com.sannyas.player.data.local.database.entity.RecentSearchEntity
import com.sannyas.player.domain.model.Bookmark
import com.sannyas.player.domain.model.HistoryEntry
import com.sannyas.player.domain.model.PlaybackProgress
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataRepositoryImpl @Inject constructor(
    private val favouriteDao: FavouriteDao,
    private val favouriteTrackDao: FavouriteTrackDao,
    private val historyDao: HistoryDao,
    private val progressDao: PlaybackProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val recentSearchDao: RecentSearchDao,
) : UserDataRepository {

    // ---- Favourites -------------------------------------------------------

    override fun observeFavouriteDiscourseIds(): Flow<Set<String>> =
        favouriteDao.observeIds().map { it.toSet() }

    override fun observeIsFavourite(discourseId: String): Flow<Boolean> =
        favouriteDao.observeIsFavourite(discourseId)

    override suspend fun toggleFavourite(discourseId: String) {
        if (favouriteDao.isFavourite(discourseId)) {
            favouriteDao.delete(discourseId)
        } else {
            favouriteDao.insert(FavouriteEntity(discourseId, System.currentTimeMillis()))
        }
    }

    override fun observeFavouriteTrackIds(): Flow<List<String>> = favouriteTrackDao.observeIds()

    override fun observeIsTrackFavourite(trackId: String): Flow<Boolean> =
        favouriteTrackDao.observeIsFavourite(trackId)

    override suspend fun toggleTrackFavourite(track: Track) {
        if (favouriteTrackDao.isFavourite(track.id)) {
            favouriteTrackDao.delete(track.id)
        } else {
            favouriteTrackDao.insert(
                FavouriteTrackEntity(
                    trackId = track.id,
                    discourseId = track.discourseId,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // ---- History ----------------------------------------------------------

    override fun observeHistory(limit: Int): Flow<List<HistoryEntry>> =
        historyDao.observeRecent(limit).map { rows ->
            rows.map { HistoryEntry(it.trackId, it.discourseId, it.playedAt) }
        }

    override suspend fun recordPlayed(track: Track) {
        historyDao.insert(
            HistoryEntity(
                trackId = track.id,
                discourseId = track.discourseId,
                playedAt = System.currentTimeMillis(),
            ),
        )
        historyDao.trim(MAX_HISTORY_ROWS)
    }

    override suspend fun clearHistory() = historyDao.clear()

    // ---- Resume positions -------------------------------------------------

    override fun observeProgress(): Flow<List<PlaybackProgress>> =
        progressDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeProgressFor(trackId: String): Flow<PlaybackProgress?> =
        progressDao.observeFor(trackId).map { it?.toDomain() }

    override suspend fun getProgress(trackId: String): PlaybackProgress? =
        progressDao.get(trackId)?.toDomain()

    override suspend fun saveProgress(
        trackId: String,
        discourseId: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        // A finished track should leave Continue Listening rather than sit at 100%.
        val finished = durationMs > 0L &&
            positionMs >= durationMs - PlaybackProgress.COMPLETION_TOLERANCE_MS
        if (finished) {
            progressDao.delete(trackId)
            return
        }
        progressDao.upsert(
            PlaybackProgressEntity(
                trackId = trackId,
                discourseId = discourseId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearProgress(trackId: String) = progressDao.delete(trackId)

    override suspend fun clearAllProgress() = progressDao.clear()

    // ---- Bookmarks --------------------------------------------------------

    override fun observeBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeBookmarksFor(trackId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeFor(trackId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addBookmark(track: Track, positionMs: Long, note: String) {
        bookmarkDao.insert(
            BookmarkEntity(
                trackId = track.id,
                discourseId = track.discourseId,
                positionMs = positionMs,
                note = note,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun removeBookmark(id: Long) = bookmarkDao.delete(id)

    // ---- Recent searches --------------------------------------------------

    override fun observeRecentSearches(limit: Int): Flow<List<String>> =
        recentSearchDao.observeRecent(limit).map { rows -> rows.map { it.query } }

    override suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        recentSearchDao.insert(RecentSearchEntity(trimmed, System.currentTimeMillis()))
        recentSearchDao.trim(MAX_RECENT_SEARCHES)
    }

    override suspend fun removeSearch(query: String) = recentSearchDao.delete(query)

    override suspend fun clearSearches() = recentSearchDao.clear()

    // ---- Mapping ----------------------------------------------------------

    private fun PlaybackProgressEntity.toDomain() = PlaybackProgress(
        trackId = trackId,
        discourseId = discourseId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
    )

    private fun BookmarkEntity.toDomain() = Bookmark(
        id = id,
        trackId = trackId,
        discourseId = discourseId,
        positionMs = positionMs,
        note = note,
        createdAt = createdAt,
    )

    private companion object {
        const val MAX_HISTORY_ROWS = 500
        const val MAX_RECENT_SEARCHES = 12
    }
}
