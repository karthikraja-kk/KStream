package com.kstream.core.data.db.daos

import androidx.room.*
import com.kstream.core.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE path = :path")
    suspend fun getMovieByPath(path: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: MovieEntity)

    @Query("UPDATE movies SET lastAccessedAt = :time WHERE path = :path")
    suspend fun updateLastAccessed(path: String, time: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getCount(): Int

    @Query("DELETE FROM movies WHERE path IN (SELECT path FROM movies ORDER BY lastAccessedAt ASC LIMIT 1)")
    suspend fun deleteOldest()

    @Transaction
    suspend fun insertWithEviction(movie: MovieEntity) {
        insertMovie(movie)
        if (getCount() > 100) {
            deleteOldest()
        }
    }
}

@Dao
interface YearDao {
    @Query("SELECT * FROM years ORDER BY year DESC")
    fun getAllYears(): Flow<List<YearEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertYears(years: List<YearEntity>)
}

@Dao
interface PosterDao {
    @Query("SELECT * FROM posters WHERE moviePath = :path")
    suspend fun getPoster(path: String): PosterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoster(poster: PosterEntity)

    @Query("SELECT COUNT(*) FROM posters")
    suspend fun getCount(): Int

    @Query("DELETE FROM posters WHERE moviePath IN (SELECT moviePath FROM posters ORDER BY cachedAt ASC LIMIT 1)")
    suspend fun deleteOldest()

    @Transaction
    suspend fun insertWithEviction(poster: PosterEntity) {
        insertPoster(poster)
        if (getCount() > 200) {
            deleteOldest()
        }
    }
}

@Dao
interface WatchDao {
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC")
    fun getContinueWatching(): Flow<List<ContinueWatchingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContinueWatching(item: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE moviePath = :path")
    suspend fun deleteContinueWatching(path: String)

    @Query("SELECT COUNT(*) FROM continue_watching")
    suspend fun getContinueWatchingCount(): Int

    @Query("DELETE FROM continue_watching WHERE moviePath IN (SELECT moviePath FROM continue_watching ORDER BY updatedAt ASC LIMIT 1)")
    suspend fun deleteOldestContinueWatching()

    @Transaction
    suspend fun insertContinueWatchingWithEviction(item: ContinueWatchingEntity) {
        insertContinueWatching(item)
        if (getContinueWatchingCount() > 20) {
            deleteOldestContinueWatching()
        }
    }

    @Query("SELECT * FROM recently_visited ORDER BY visitedAt DESC")
    fun getRecentlyVisited(): Flow<List<RecentlyVisitedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyVisited(item: RecentlyVisitedEntity)

    @Query("SELECT COUNT(*) FROM recently_visited")
    suspend fun getRecentlyVisitedCount(): Int

    @Query("DELETE FROM recently_visited WHERE moviePath IN (SELECT moviePath FROM recently_visited ORDER BY visitedAt ASC LIMIT 1)")
    suspend fun deleteOldestRecentlyVisited()

    @Transaction
    suspend fun insertRecentlyVisitedWithEviction(item: RecentlyVisitedEntity) {
        insertRecentlyVisited(item)
        if (getRecentlyVisitedCount() > 10) {
            deleteOldestRecentlyVisited()
        }
    }
}
