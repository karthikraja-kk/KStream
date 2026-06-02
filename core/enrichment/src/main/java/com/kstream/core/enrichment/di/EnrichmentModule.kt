package com.kstream.core.enrichment.di

import android.content.Context
import androidx.room.Room
import com.kstream.core.enrichment.db.EnrichmentDatabase
import com.kstream.core.enrichment.db.MovieEnrichmentDao
import com.kstream.core.enrichment.tmdb.TmdbClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object EnrichmentModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): EnrichmentDatabase =
        Room.databaseBuilder(ctx, EnrichmentDatabase::class.java, EnrichmentDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(db: EnrichmentDatabase): MovieEnrichmentDao = db.enrichmentDao()

    @Provides
    @Singleton
    fun provideTmdbClient(): TmdbClient = TmdbClient()
}
