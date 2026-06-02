package com.kstream.core.enrichment.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieEnrichmentDao {

    @Query("SELECT * FROM movie_enrichment WHERE movie_key = :key LIMIT 1")
    suspend fun get(key: String): MovieEnrichmentEntity?

    @Query("SELECT * FROM movie_enrichment WHERE movie_key = :key LIMIT 1")
    fun observe(key: String): Flow<MovieEnrichmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MovieEnrichmentEntity)

    @Query("DELETE FROM movie_enrichment WHERE movie_key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM movie_enrichment")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM movie_enrichment")
    suspend fun count(): Int
}
