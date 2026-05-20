package com.kstream.core.data.local.dao

import androidx.room.*
import com.kstream.core.data.local.entities.DownloadEntity
import com.kstream.core.data.local.entities.DownloadStatus as EntityDownloadStatus
import com.kstream.core.data.local.entities.LikedMovieEntity
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.data.local.entities.RecommendationEntity
import com.kstream.core.data.local.entities.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies_cache")
    fun getMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies_cache")
    suspend fun getAllMovies(): List<MovieEntity>

    @Query("""
        SELECT * FROM movies_cache 
        WHERE movieName LIKE :query 
        OR director LIKE :query 
        OR castMembers LIKE :query
        OR genres LIKE :query
        OR language LIKE :query
        LIMIT 100
    """)
    suspend fun searchMovies(query: String): List<MovieEntity>

    @Query("SELECT * FROM movies_cache WHERE genres LIKE :genre LIMIT 100")
    suspend fun searchByGenre(genre: String): List<MovieEntity>

    @Query("SELECT * FROM movies_cache WHERE year = :year LIMIT 100")
    suspend fun searchByYear(year: Int): List<MovieEntity>

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

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()
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

    @Query("UPDATE download SET progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, downloadedBytes: Long, totalBytes: Long)

    @Query("UPDATE download SET status = :status, statusMessage = null WHERE id = :id")
    suspend fun updateStatus(id: String, status: EntityDownloadStatus)

    @Query("UPDATE download SET status = :status, statusMessage = :message WHERE id = :id")
    suspend fun updateStatusWithMessage(id: String, status: EntityDownloadStatus, message: String?)

    @Query("UPDATE download SET localFilePath = :localFilePath, status = :status, progress = 1.0, statusMessage = null WHERE id = :id")
    suspend fun markComplete(id: String, localFilePath: String, status: EntityDownloadStatus)

    @Query("DELETE FROM download WHERE movieId = :movieId")
    suspend fun deleteDownload(movieId: String)

    @Query("DELETE FROM download WHERE id = :id")
    suspend fun deleteDownloadById(id: String)

    @Query("DELETE FROM download")
    suspend fun deleteAll()
}

@Dao
interface LikedMovieDao {
    @Query("SELECT * FROM liked_movies ORDER BY likedAt DESC")
    fun getAllLiked(): Flow<List<LikedMovieEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_movies WHERE movieId = :movieId)")
    fun isLiked(movieId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedMovieEntity)

    @Query("DELETE FROM liked_movies WHERE movieId = :movieId")
    suspend fun unlike(movieId: String)

    @Query("DELETE FROM liked_movies")
    suspend fun clearAll()
}

@Dao
interface RecommendationDao {
    @Query("SELECT * FROM recommendations ORDER BY score DESC")
    fun getRecommendations(): Flow<List<RecommendationEntity>>

    @Query("SELECT movieId FROM recommendations ORDER BY score DESC")
    fun getRecommendedMovieIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recommendations: List<RecommendationEntity>)

    @Query("DELETE FROM recommendations")
    suspend fun clearAll()

    @Query("SELECT computedAt FROM recommendations LIMIT 1")
    suspend fun getLastComputedAt(): Long?
}
