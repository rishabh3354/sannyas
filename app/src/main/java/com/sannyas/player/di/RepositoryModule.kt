package com.sannyas.player.di

import com.sannyas.player.data.repository.CatalogRepositoryImpl
import com.sannyas.player.data.repository.DownloadRepositoryImpl
import com.sannyas.player.data.repository.PlaylistRepositoryImpl
import com.sannyas.player.data.repository.UserDataRepositoryImpl
import com.sannyas.player.domain.repository.CatalogRepository
import com.sannyas.player.domain.repository.DownloadRepository
import com.sannyas.player.domain.repository.PlaylistRepository
import com.sannyas.player.domain.repository.UserDataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each repository interface to its implementation.
 *
 * Screens depend only on the interfaces in `domain.repository`, which is what
 * keeps the UI layer testable without Room, Media3 or an Android context.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindUserDataRepository(impl: UserDataRepositoryImpl): UserDataRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository
}
