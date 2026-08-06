package com.sannyas.player.domain.repository

import com.sannyas.player.domain.model.Bookmark
import com.sannyas.player.domain.model.Category
import com.sannyas.player.domain.model.Discourse
import com.sannyas.player.domain.model.DownloadStatus
import com.sannyas.player.domain.model.HistoryEntry
import com.sannyas.player.domain.model.PlaybackProgress
import com.sannyas.player.domain.model.Playlist
import com.sannyas.player.domain.model.Station
import com.sannyas.player.domain.model.Topic
import com.sannyas.player.domain.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the bundled catalog.
 *
 * Implementations parse `assets/discourses.json` once and keep the result in
 * memory; the catalog is small enough that this is far cheaper than a database
 * and lets search run synchronously against pre-lowercased haystacks.
 */
interface CatalogRepository {
    /** Emits the catalog, re-emitting if [refresh] reloads it from disk. */
    fun observeDiscourses(): Flow<List<Discourse>>

    /**
     * The whole catalogue, ignoring the discourse-language filter.
     *
     * "All Series" means all of them. The filter shapes the feed, where the
     * point is a language you follow; a listener who deliberately opens the
     * complete list is asking a different question.
     */
    fun observeAllDiscourses(): Flow<List<Discourse>>

    suspend fun getDiscourses(): List<Discourse>

    suspend fun getDiscourse(id: String): Discourse?

    suspend fun getTrack(trackId: String): Track?

    suspend fun getCategories(): List<Category>

    /**
     * Categories for the selected language(s), re-emitting when that preference
     * changes. A one-shot read leaves stale chips on screen that lead nowhere.
     */
    fun observeCategories(): Flow<List<Category>>

    /** Life themes present in the chosen language(s). */
    fun observeTopics(): Flow<List<Topic>>

    suspend fun getTopic(topicId: String): Topic?

    /** Continuous stations derived from the catalogue, in the chosen language(s). */
    fun observeStations(): Flow<List<Station>>

    /**
     * A shuffled batch of talks for [stationId].
     *
     * Called repeatedly as a station plays, so it must return a fresh shuffle
     * each time rather than a stable slice — that is what keeps the stream from
     * looping back on itself.
     */
    suspend fun stationTracks(stationId: String, limit: Int = 60): List<Track>

    suspend fun search(query: String): SearchResults

    /** Query fragments offered while the user types. */
    suspend fun suggestions(query: String, limit: Int = 8): List<String>

    /** Re-reads the asset. Backs pull-to-refresh and makes catalog swaps testable. */
    suspend fun refresh()
}

data class SearchResults(
    val discourses: List<Discourse> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    val isEmpty: Boolean get() = discourses.isEmpty() && tracks.isEmpty() && categories.isEmpty()
    val total: Int get() = discourses.size + tracks.size + categories.size
}

/** Favourites, history, resume positions and bookmarks. Backed by Room. */
interface UserDataRepository {
    fun observeFavouriteDiscourseIds(): Flow<Set<String>>
    fun observeIsFavourite(discourseId: String): Flow<Boolean>
    suspend fun toggleFavourite(discourseId: String)

    /**
     * Favourited individual talks, newest first.
     *
     * Distinct from favouriting a series: singling out one discourse from a
     * fifty-part set is the more common gesture, and the player's heart means
     * this one.
     */
    fun observeFavouriteTrackIds(): Flow<List<String>>
    fun observeIsTrackFavourite(trackId: String): Flow<Boolean>
    suspend fun toggleTrackFavourite(track: Track)

    fun observeHistory(limit: Int = 100): Flow<List<HistoryEntry>>
    suspend fun recordPlayed(track: Track)
    suspend fun clearHistory()

    fun observeProgress(): Flow<List<PlaybackProgress>>
    fun observeProgressFor(trackId: String): Flow<PlaybackProgress?>
    suspend fun getProgress(trackId: String): PlaybackProgress?
    suspend fun saveProgress(trackId: String, discourseId: String, positionMs: Long, durationMs: Long)
    suspend fun clearProgress(trackId: String)

    /** Forgets every saved position, emptying Continue Listening. */
    suspend fun clearAllProgress()

    fun observeBookmarks(): Flow<List<Bookmark>>
    fun observeBookmarksFor(trackId: String): Flow<List<Bookmark>>
    suspend fun addBookmark(track: Track, positionMs: Long, note: String)
    suspend fun removeBookmark(id: Long)

    fun observeRecentSearches(limit: Int = 10): Flow<List<String>>
    suspend fun recordSearch(query: String)
    suspend fun removeSearch(query: String)
    suspend fun clearSearches()
}

/** Offline downloads, wrapping Media3's DownloadManager. */
interface DownloadRepository {
    fun observeAll(): Flow<Map<String, DownloadStatus>>
    fun observeStatus(trackId: String): Flow<DownloadStatus>
    fun observeDownloadedTrackIds(): Flow<Set<String>>
    /** Total bytes currently occupied by completed and partial downloads. */
    fun observeStorageUsage(): Flow<Long>

    /** True while the queue is paused by the user. */
    fun observePaused(): Flow<Boolean>

    suspend fun download(track: Track)
    suspend fun downloadAll(discourse: Discourse)
    suspend fun remove(trackId: String)
    suspend fun removeAll(discourseId: String)
    suspend fun removeEverything()
    suspend fun pauseAll()
    suspend fun resumeAll()
}

/** Listener-assembled sequences of talks. */
interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    /** Track ids in playlist order; resolved against the catalogue by callers. */
    fun observeTrackIds(playlistId: Long): Flow<List<String>>

    /** Which playlists already contain [trackId], for the add sheet's tick marks. */
    fun observePlaylistIdsContaining(trackId: String): Flow<Set<Long>>

    suspend fun create(name: String): Long

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    /** Appends to the end; a talk already present is left where it is. */
    suspend fun add(playlistId: Long, track: Track)

    suspend fun remove(playlistId: Long, trackId: String)

    suspend fun reorder(playlistId: Long, orderedTrackIds: List<String>)
}
