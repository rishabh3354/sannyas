package com.osho.player.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The permanent cache that holds explicit offline downloads. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

/** The bounded cache that holds recently streamed audio. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamCache

/** The raw network source, with no caching layered on top. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpstreamDataSource

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    private const val STREAM_CACHE_BYTES = 512L * 1024 * 1024
    private const val USER_AGENT = "OshoMusicPlayer/1.0 (Android)"

    @Provides
    @Singleton
    fun provideDatabaseProvider(context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    /**
     * Downloads must never be evicted automatically — the user asked for them —
     * so this cache uses a no-op evictor and is only pruned by explicit removal.
     */
    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(
        context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache = SimpleCache(
        File(context.filesDir, "downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    /**
     * Streaming cache. Bounded and LRU-evicted so revisiting a talk within a
     * session is instant without the app growing without limit.
     */
    @Provides
    @Singleton
    @StreamCache
    fun provideStreamCache(
        context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache = SimpleCache(
        File(context.cacheDir, "media-cache"),
        LeastRecentlyUsedCacheEvictor(STREAM_CACHE_BYTES),
        databaseProvider,
    )

    /** The default binding is the download cache, which is what the manager writes into. */
    @Provides
    @Singleton
    fun provideCache(@DownloadCache cache: Cache): Cache = cache

    /**
     * Upstream network source. Cross-protocol redirects are allowed because the
     * source host redirects between http and https for some assets.
     */
    @Provides
    @Singleton
    @UpstreamDataSource
    fun provideHttpDataSourceFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

    /**
     * The factory the player reads through.
     *
     * Chains both caches: completed downloads are served from the download
     * cache, anything else falls through to the streaming cache and only then
     * to the network. Writes go to the streaming cache only, so casual
     * listening never inflates the "Downloads" storage figure.
     */
    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        context: Context,
        @DownloadCache downloadCache: Cache,
        @StreamCache streamCache: Cache,
        @UpstreamDataSource upstream: DataSource.Factory,
    ): DataSource.Factory {
        val networkAndLocal = DefaultDataSource.Factory(context, upstream)

        val withStreamCache = CacheDataSource.Factory()
            .setCache(streamCache)
            .setUpstreamDataSourceFactory(networkAndLocal)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(withStreamCache)
            // Read-only: only DownloadManager may write into the download cache.
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
