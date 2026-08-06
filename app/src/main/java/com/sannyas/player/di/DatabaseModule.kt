package com.sannyas.player.di

import android.content.Context
import androidx.room.Room
import com.sannyas.player.data.local.database.OshoDatabase
import com.sannyas.player.data.local.database.dao.BookmarkDao
import com.sannyas.player.data.local.database.dao.DownloadMetadataDao
import com.sannyas.player.data.local.database.dao.FavouriteDao
import com.sannyas.player.data.local.database.dao.FavouriteTrackDao
import com.sannyas.player.data.local.database.dao.PlaylistDao
import com.sannyas.player.data.local.database.dao.HistoryDao
import com.sannyas.player.data.local.database.dao.PlaybackProgressDao
import com.sannyas.player.data.local.database.dao.RecentSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): OshoDatabase =
        Room.databaseBuilder(context, OshoDatabase::class.java, OshoDatabase.NAME)
            .addMigrations(OshoDatabase.MIGRATION_1_2)
            // Only reached if an install somehow skips a migration path. Every
            // table here can be rebuilt by listening again, so losing it beats
            // refusing to open the database.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFavouriteDao(db: OshoDatabase): FavouriteDao = db.favouriteDao()

    @Provides
    fun provideFavouriteTrackDao(db: OshoDatabase): FavouriteTrackDao = db.favouriteTrackDao()

    @Provides fun providePlaylistDao(db: OshoDatabase): PlaylistDao = db.playlistDao()

    @Provides fun provideHistoryDao(db: OshoDatabase): HistoryDao = db.historyDao()

    @Provides
    fun providePlaybackProgressDao(db: OshoDatabase): PlaybackProgressDao =
        db.playbackProgressDao()

    @Provides fun provideBookmarkDao(db: OshoDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideRecentSearchDao(db: OshoDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides
    fun provideDownloadMetadataDao(db: OshoDatabase): DownloadMetadataDao =
        db.downloadMetadataDao()
}
