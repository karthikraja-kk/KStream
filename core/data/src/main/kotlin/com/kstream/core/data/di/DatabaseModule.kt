package com.kstream.core.data.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.kstream.core.data.db.KStreamDatabase
import com.kstream.core.data.db.daos.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KStreamDatabase {
        return Room.databaseBuilder(
            context,
            KStreamDatabase::class.java,
            "kstream_db"
        ).build()
    }

    @Provides
    fun provideMovieDao(db: KStreamDatabase): MovieDao = db.movieDao()

    @Provides
    fun provideYearDao(db: KStreamDatabase): YearDao = db.yearDao()

    @Provides
    fun providePosterDao(db: KStreamDatabase): PosterDao = db.posterDao()

    @Provides
    fun provideWatchDao(db: KStreamDatabase): WatchDao = db.watchDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
