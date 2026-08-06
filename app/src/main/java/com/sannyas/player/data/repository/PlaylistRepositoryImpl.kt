package com.sannyas.player.data.repository

import com.sannyas.player.data.local.database.dao.PlaylistDao
import com.sannyas.player.data.local.database.entity.PlaylistEntity
import com.sannyas.player.domain.model.Playlist
import com.sannyas.player.domain.model.Track
import com.sannyas.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observeSummaries().map { rows ->
            rows.map { Playlist(it.id, it.name, it.trackCount, it.updatedAt) }
        }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observeSummaries().map { rows ->
            rows.firstOrNull { it.id == playlistId }
                ?.let { Playlist(it.id, it.name, it.trackCount, it.updatedAt) }
        }

    override fun observeTrackIds(playlistId: Long): Flow<List<String>> =
        playlistDao.observeTracks(playlistId).map { rows -> rows.map { it.trackId } }

    override fun observePlaylistIdsContaining(trackId: String): Flow<Set<Long>> =
        playlistDao.observePlaylistIdsContaining(trackId).map { it.toSet() }

    override suspend fun create(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now),
        )
    }

    override suspend fun rename(playlistId: Long, name: String) =
        playlistDao.rename(playlistId, name.trim(), System.currentTimeMillis())

    override suspend fun delete(playlistId: Long) = playlistDao.deletePlaylist(playlistId)

    override suspend fun add(playlistId: Long, track: Track) =
        playlistDao.append(playlistId, track.id, track.discourseId, System.currentTimeMillis())

    override suspend fun remove(playlistId: Long, trackId: String) =
        playlistDao.remove(playlistId, trackId, System.currentTimeMillis())

    override suspend fun reorder(playlistId: Long, orderedTrackIds: List<String>) =
        playlistDao.reorder(playlistId, orderedTrackIds, System.currentTimeMillis())
}
