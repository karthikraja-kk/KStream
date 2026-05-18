package com.kstream.core.data.di

import android.content.Context
import androidx.room.Room
import com.kstream.core.data.local.KStreamDatabase
import com.kstream.core.data.local.dao.DownloadDao
import com.kstream.core.data.local.dao.LikedMovieDao
import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.dao.RecommendationDao
import com.kstream.core.data.local.dao.WatchProgressDao
import com.kstream.core.data.repository.*
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.domain.repository.LikedMovieRepository
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.domain.repository.RecommendationRepository
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindMovieRepository(
        movieRepository: OfflineFirstMovieRepository
    ): MovieRepository

    @Binds
    abstract fun bindUserDataRepository(
        userDataRepository: DefaultUserDataRepository
    ): UserDataRepository

    @Binds
    abstract fun bindWatchProgressRepository(
        watchProgressRepository: OfflineFirstWatchProgressRepository
    ): WatchProgressRepository

    @Binds
    abstract fun bindDownloadRepository(
        downloadRepository: DownloadRepositoryImpl
    ): DownloadRepository

    @Binds
    abstract fun bindLikedMovieRepository(
        likedMovieRepository: OfflineFirstLikedMovieRepository
    ): LikedMovieRepository

    @Binds
    abstract fun bindRecommendationRepository(
        recommendationRepository: OfflineFirstRecommendationRepository
    ): RecommendationRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context
        ): KStreamDatabase {
            return Room.databaseBuilder(
                context,
                KStreamDatabase::class.java,
                "kstream.db"
            ).fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                .build()
        }

        @Provides
        fun provideMovieDao(database: KStreamDatabase): MovieDao = database.movieDao()

        @Provides
        fun provideWatchProgressDao(database: KStreamDatabase): WatchProgressDao = database.watchProgressDao()

        @Provides
        fun provideDownloadDao(database: KStreamDatabase): DownloadDao = database.downloadDao()

        @Provides
        fun provideLikedMovieDao(database: KStreamDatabase): LikedMovieDao = database.likedMovieDao()

        @Provides
        fun provideRecommendationDao(database: KStreamDatabase): RecommendationDao = database.recommendationDao()
    }
}
