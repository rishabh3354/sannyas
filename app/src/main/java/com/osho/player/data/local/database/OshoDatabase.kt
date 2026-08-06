package com.osho.player.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.osho.player.data.local.database.dao.BookmarkDao
import com.osho.player.data.local.database.dao.DownloadMetadataDao
import com.osho.player.data.local.database.dao.FavouriteDao
import com.osho.player.data.local.database.dao.FavouriteTrackDao
import com.osho.player.data.local.database.dao.PlaylistDao
import com.osho.player.data.local.database.dao.HistoryDao
import com.osho.player.data.local.database.dao.PlaybackProgressDao
import com.osho.player.data.local.database.dao.RecentSearchDao
import com.osho.player.data.local.database.entity.BookmarkEntity
import com.osho.player.data.local.database.entity.DownloadMetadataEntity
import com.osho.player.data.local.database.entity.FavouriteEntity
import com.osho.player.data.local.database.entity.FavouriteTrackEntity
import com.osho.player.data.local.database.entity.PlaylistEntity
import com.osho.player.data.local.database.entity.PlaylistTrackEntity
import com.osho.player.data.local.database.entity.HistoryEntity
import com.osho.player.data.local.database.entity.PlaybackProgressEntity
import com.osho.player.data.local.database.entity.RecentSearchEntity

@Database(
    entities = [
        FavouriteEntity::class,
        FavouriteTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HistoryEntity::class,
        PlaybackProgressEntity::class,
        BookmarkEntity::class,
        RecentSearchEntity::class,
        DownloadMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OshoDatabase : RoomDatabase() {
    abstract fun favouriteDao(): FavouriteDao
    abstract fun favouriteTrackDao(): FavouriteTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun downloadMetadataDao(): DownloadMetadataDao

    companion object {
        const val NAME = "osho-database"

        /**
         * Adds per-talk favourites and playlists.
         *
         * Written out properly rather than leaning on the destructive fallback:
         * by the time this ships people have real resume positions, bookmarks
         * and favourites in here, and none of that is worth losing to save a
         * few lines. Purely additive — no existing table is touched.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favourite_tracks` (
                        `trackId` TEXT NOT NULL,
                        `discourseId` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favourite_tracks_discourseId` " +
                        "ON `favourite_tracks` (`discourseId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favourite_tracks_addedAt` " +
                        "ON `favourite_tracks` (`addedAt`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                        `playlistId` INTEGER NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `discourseId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `trackId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` " +
                        "ON `playlist_tracks` (`playlistId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` " +
                        "ON `playlist_tracks` (`trackId`)",
                )
            }
        }
    }
}
