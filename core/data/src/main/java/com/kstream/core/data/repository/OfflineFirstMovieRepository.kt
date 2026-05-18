package com.kstream.core.data.repository

import android.util.Log
import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.data.local.KStreamDataStore
import com.kstream.core.data.local.KStreamDatabase
import com.kstream.core.network.KStreamNetworkDataSource
import com.kstream.core.network.model.asExternalModel
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import com.kstream.core.model.ScanStatus
import com.kstream.core.model.ScanTriggerResult
import com.kstream.core.domain.repository.MovieRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstMovieRepository @Inject constructor(
    private val movieDao: MovieDao,
    private val network: KStreamNetworkDataSource,
    private val dataStore: KStreamDataStore,
    private val database: KStreamDatabase
) : MovieRepository {

    override fun getMovies(): Flow<List<Movie>> {
        return movieDao.getMovies().map { entities ->
            Log.d("MovieRepository", "Local DB has ${entities.size} movies")
            val baseUrl = dataStore.getPosterBaseUrlSync()
            entities.map { it.asExternalModel(baseUrl) }
        }
    }

    override suspend fun getMovieWithMedia(movieId: String): MovieWithMedia? {
        Log.d("MovieRepository", "Fetching movie details from network for $movieId")
        val baseUrl = dataStore.getPosterBaseUrlSync()
        return network.getMovieWithMedia(movieId)?.asExternalModel(baseUrl)
    }

    override suspend fun syncMovies() {
        try {
            Log.d("MovieRepository", "Starting movie sync...")

            // Fetch and cache the base URL from source table
            val baseUrl = network.getBaseUrl()
            if (baseUrl.isNotEmpty()) {
                dataStore.setPosterBaseUrl(baseUrl)
                Log.d("MovieRepository", "Base URL updated: $baseUrl")
            }

            val networkMovies = network.getMovies()
            Log.d("MovieRepository", "Network returned ${networkMovies.size} movies. Inserting into local DB...")
            database.withTransaction {
                movieDao.insertMovies(networkMovies.map { it.asLocalEntity() })
            }
            Log.d("MovieRepository", "Sync completed successfully")
        } catch (e: Exception) {
            Log.e("MovieRepository", "Sync failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun searchMovies(query: String): List<Movie> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        Log.d("MovieRepository", "Searching movies for: $query")
        
        val baseUrl = dataStore.getPosterBaseUrlSync()

        // Handle prefixed search queries — only exact prefix match, not partial
        if (normalizedQuery.startsWith("genre:", ignoreCase = true)) {
            val genre = normalizedQuery.substringAfter(":").trim()
            if (genre.isNotEmpty()) {
                return try {
                    val localResults = movieDao.searchByGenre("%$genre%")
                    localResults.map { it.asExternalModel(baseUrl) }
                } catch (e: Exception) {
                    Log.e("MovieRepository", "Local genre search failed: ${e.message}", e)
                    emptyList()
                }
            }
        }

        if (normalizedQuery.startsWith("year:", ignoreCase = true)) {
            val yearStr = normalizedQuery.substringAfter(":").trim()
            val year = yearStr.toIntOrNull()
            if (year != null) {
                return try {
                    val localResults = movieDao.searchByYear(year)
                    localResults.map { it.asExternalModel(baseUrl) }
                } catch (e: Exception) {
                    Log.e("MovieRepository", "Local year search failed: ${e.message}", e)
                    emptyList()
                }
            }
        }

        // Wildcard — return all movies from local cache (used by history:* flow)
        if (normalizedQuery == "*") {
            return try {
                val localMovies = movieDao.searchMovies("%%")
                localMovies.map { it.asExternalModel(baseUrl) }
            } catch (e: Exception) {
                network.getMovies().map { it.asExternalModel(baseUrl) }
            }
        }

        // Special rail queries — use local cache
        when (normalizedQuery) {
            "New Releases" -> {
                return try {
                    val localMovies = movieDao.searchMovies("%%")
                    localMovies.map { it.asExternalModel(baseUrl) }.sortedByDescending { it.year }.take(100)
                } catch (e: Exception) {
                    network.getMovies().map { it.asExternalModel(baseUrl) }.sortedByDescending { it.year }.take(100)
                }
            }
            "You Might Like" -> {
                return try {
                    val localMovies = movieDao.searchMovies("%%")
                    localMovies.map { it.asExternalModel(baseUrl) }.take(100)
                } catch (e: Exception) {
                    network.getMovies().map { it.asExternalModel(baseUrl) }.take(100)
                }
            }
        }

        // Normal text search — try server-side first, fall back to local
        return try {
            val serverResults = network.searchMovies(normalizedQuery)
            Log.d("MovieRepository", "Server search returned ${serverResults.size} results")
            serverResults.map { it.asExternalModel(baseUrl) }
        } catch (e: Exception) {
            Log.w("MovieRepository", "Server search failed, falling back to local: ${e.message}")
            try {
                val likeQuery = "%${normalizedQuery}%"
                val localResults = movieDao.searchMovies(likeQuery)
                Log.d("MovieRepository", "Local search returned ${localResults.size} results")
                localResults.map { it.asExternalModel(baseUrl) }
            } catch (localEx: Exception) {
                Log.e("MovieRepository", "Local search also failed: ${localEx.message}", localEx)
                emptyList()
            }
        }
    }

    override suspend fun refreshMedia(movieId: String): MovieWithMedia? {
        return try {
            Log.d("MovieRepository", "Refreshing media for movie: $movieId")
            val refreshedMedia = network.refreshMovieMedia(movieId)
            if (refreshedMedia.isEmpty()) {
                Log.w("MovieRepository", "No refreshed media returned")
                return null
            }
            // Re-fetch the full movie with updated media
            val baseUrl = dataStore.getPosterBaseUrlSync()
            network.getMovieWithMedia(movieId)?.asExternalModel(baseUrl)
        } catch (e: Exception) {
            Log.e("MovieRepository", "Error refreshing media: ${e.message}", e)
            null
        }
    }

    override suspend fun triggerScan(): ScanTriggerResult {
        return try {
            val response = network.triggerMovieScan()
            ScanTriggerResult(
                status = response.status,
                triggeredAt = response.triggeredAt,
                message = response.message
            )
        } catch (e: Exception) {
            Log.e("MovieRepository", "Error triggering scan: ${e.message}", e)
            ScanTriggerResult(status = "error", message = e.message)
        }
    }

    override suspend fun getScanStatus(): ScanStatus {
        return try {
            val entry = network.getScanStatus()
            when (entry?.status) {
                "inprogress" -> ScanStatus.RUNNING
                "completed" -> ScanStatus.COMPLETED
                "failed" -> ScanStatus.FAILED
                else -> ScanStatus.IDLE
            }
        } catch (e: Exception) {
            Log.e("MovieRepository", "Error getting scan status: ${e.message}", e)
            ScanStatus.IDLE
        }
    }

    override suspend fun clearCache() {
        movieDao.clearMovies()
    }
}

@Suppress("USELESS_ELVIS_CHECK")
private fun MovieEntity.asExternalModel(baseUrl: String = "") = Movie(
    id = id,
    movieName = movieName,
    year = year,
    posterUrl = resolvePosterUrl(posterUrl, baseUrl),
    duration = duration,
    synopsis = synopsis,
    director = director.split(",").filter { it.isNotBlank() },
    castMembers = castMembers.split(",").filter { it.isNotBlank() },
    genres = genres.split(",").filter { it.isNotBlank() },
    rating = rating,
    language = language,
    type = type,
    slug = slug,
    lastUpdated = lastUpdated
)

/**
 * Constructs a full poster URL from a relative path and base URL.
 * If posterUrl is already a full URL (starts with http), returns as-is.
 * If posterUrl is a relative path (starts with /), prepends the base URL.
 */
private fun resolvePosterUrl(posterUrl: String, baseUrl: String): String {
    if (posterUrl.isBlank()) return posterUrl
    if (posterUrl.startsWith("http")) return posterUrl
    if (baseUrl.isBlank()) return posterUrl
    return "${baseUrl.trimEnd('/')}$posterUrl"
}

@Suppress("USELESS_ELVIS_CHECK")
private fun com.kstream.core.network.model.NetworkMovie.asLocalEntity() = MovieEntity(
    id = id,
    movieName = movieName,
    year = year ?: 0,
    posterUrl = posterUrl ?: "",
    duration = duration ?: "",
    synopsis = synopsis ?: "",
    director = director?.joinToString(",") ?: "",
    castMembers = castMembers?.joinToString(",") ?: "",
    genres = genres?.joinToString(",") ?: "",
    rating = rating ?: "",
    language = language ?: "",
    type = type ?: "",
    slug = slug,
    lastUpdated = lastUpdated ?: ""
)