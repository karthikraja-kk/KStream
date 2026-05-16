package com.kstream.core.data.local.dao

import androidx.room.*
import com.kstream.core.data.local.entities.DownloadEntity
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.data.local.entities.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies_cache")
    fun getMovies(): Flow<List<MovieEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Query("DELETE FROM movies_cache")
    suspend fun clearMovies()
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE movieId = :movieId")
    suspend fun getProgress(movieId: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress")
    fun getAllProgress(): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE movieId = :movieId")
    suspend fun deleteProgress(movieId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download")
    fun getDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE movieId = :movieId")
    suspend fun getDownload(movieId: String): DownloadEntity?

    @Query("SELECT * FROM download WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadEntity)

    @Query("DELETE FROM download WHERE movieId = :movieId")
    suspend fun deleteDownload(movieId: String)

    @Query("DELETE FROM download WHERE id = :id")
    suspend fun deleteDownloadById(id: String)
}
