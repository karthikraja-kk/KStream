package com.kstream.app.di

import com.kstream.core.domain.models.*
import com.kstream.core.domain.usecases.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetYearsUseCase(repository: MovieRepository) = GetYearsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetMoviesByYearUseCase(repository: MovieRepository) = GetMoviesByYearUseCase(repository)

    @Provides
    @Singleton
    fun provideGetMovieDetailUseCase(repository: MovieRepository) = GetMovieDetailUseCase(repository)

    @Provides
    @Singleton
    fun provideGetPosterUseCase(repository: PosterRepository) = GetPosterUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateWatchProgressUseCase(repository: WatchRepository) = UpdateWatchProgressUseCase(repository)

    @Provides
    @Singleton
    fun provideGetContinueWatchingUseCase(repository: WatchRepository) = GetContinueWatchingUseCase(repository)

    @Provides
    @Singleton
    fun provideRemoveFromContinueWatchingUseCase(repository: WatchRepository) = RemoveFromContinueWatchingUseCase(repository)

    @Provides
    @Singleton
    fun provideGetRecentlyVisitedUseCase(repository: WatchRepository) = GetRecentlyVisitedUseCase(repository)

    @Provides
    @Singleton
    fun provideAddRecentlyVisitedUseCase(repository: WatchRepository) = AddRecentlyVisitedUseCase(repository)

    @Provides
    @Singleton
    fun providePingSourceUseCase(repository: MovieRepository) = PingSourceUseCase(repository)

    @Provides
    @Singleton
    fun provideGetAppSettingsUseCase(repository: SettingsRepository) = GetAppSettingsUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateAppSettingsUseCase(repository: SettingsRepository) = UpdateAppSettingsUseCase(repository)
}
