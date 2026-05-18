package com.kstream.core.data.repository

import com.kstream.core.data.local.dao.RecommendationDao
import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.dao.WatchProgressDao
import com.kstream.core.data.local.dao.LikedMovieDao
import com.kstream.core.data.local.dao.DownloadDao
import com.kstream.core.data.local.entities.RecommendationEntity
import com.kstream.core.data.local.KStreamDatabase
import com.kstream.core.domain.RecommendationEngine
import com.kstream.core.domain.repository.RecommendationRepository
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstRecommendationRepository @Inject constructor(
    private val recommendationDao: RecommendationDao,
    private val movieDao: MovieDao,
    private val watchProgressDao: WatchProgressDao,
    private val likedMovieDao: LikedMovieDao,
    private val downloadDao: DownloadDao,
    private val database: KStreamDatabase
) : RecommendationRepository {

    private val engine = RecommendationEngine()

    override fun getRecommendedMovieIds(): Flow<List<String>> {
        return recommendationDao.getRecommendedMovieIds()
    }

    override suspend fun refreshRecommendations() {
        try {
            val movieEntities = movieDao.getMovies().first()
            val progressEntities = watchProgressDao.getAllProgress().first()
            val likedEntities = likedMovieDao.getAllLiked().first()
            val downloadEntities = downloadDao.getDownloads().first()

            val movies = movieEntities.map { it.toMovie() }
            val progress = progressEntities.map { it.toWatchProgress() }
            val likedIds = likedEntities.map { it.movieId }
            val downloadedIds = downloadEntities.map { it.movieId }.distinct()

            val recommended = engine.recommend(movies, progress, likedIds, downloadedIds)

            val now = System.currentTimeMillis()
            val entities = recommended.mapIndexed { index, movie ->
                RecommendationEntity(
                    movieId = movie.id,
                    score = (recommended.size - index).toDouble(),
                    computedAt = now
                )
            }

            database.withTransaction {
                recommendationDao.clearAll()
                if (entities.isNotEmpty()) {
                    recommendationDao.insertAll(entities)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RecommendationRepo", "Failed to refresh recommendations", e)
        }
    }

    override suspend fun clearAll() {
        recommendationDao.clearAll()
    }
}

private fun com.kstream.core.data.local.entities.MovieEntity.toMovie() = Movie(
    id = id,
    movieName = movieName,
    year = year,
    posterUrl = posterUrl,
    duration = duration,
    synopsis = synopsis,
    director = director.split(",").map { it.trim() }.filter { it.isNotBlank() },
    castMembers = castMembers.split(",").map { it.trim() }.filter { it.isNotBlank() },
    genres = genres.split(",").map { it.trim() }.filter { it.isNotBlank() },
    rating = rating,
    language = language,
    type = type,
    slug = slug,
    lastUpdated = lastUpdated
)

private fun com.kstream.core.data.local.entities.WatchProgressEntity.toWatchProgress()= WatchProgress(
    movieId = movieId,
    lastPosition = lastPosition,
    duration = duration,
    completionPercent = completionPercent,
    lastUpdated = lastUpdated,
    quality = quality
)
