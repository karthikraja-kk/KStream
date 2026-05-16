package com.kstream.core.data.repository

import android.util.Log
import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.network.KStreamNetworkDataSource
import com.kstream.core.network.model.asExternalModel
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import com.kstream.core.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.min
import javax.inject.Inject

class OfflineFirstMovieRepository @Inject constructor(
    private val movieDao: MovieDao,
    private val network: KStreamNetworkDataSource
) : MovieRepository {

    override fun getMovies(): Flow<List<Movie>> {
        return movieDao.getMovies().map { entities ->
            Log.d("MovieRepository", "Local DB has ${entities.size} movies")
            entities.map { it.asExternalModel() }
        }
    }

    override suspend fun getMovieWithMedia(movieId: String): MovieWithMedia? {
        Log.d("MovieRepository", "Fetching movie details from network for $movieId")
        return network.getMovieWithMedia(movieId)?.asExternalModel()
    }

    override suspend fun syncMovies() {
        try {
            Log.d("MovieRepository", "Starting movie sync...")
            val networkMovies = network.getMovies()
            Log.d("MovieRepository", "Network returned ${networkMovies.size} movies. Inserting into local DB...")
            movieDao.insertMovies(networkMovies.map { it.asLocalEntity() })
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
        
        // Handle special rail names
        val allMovies = network.getMovies().map { it.asExternalModel() }
        
        when (normalizedQuery) {
            "New Releases" -> {
                return allMovies.sortedByDescending { it.year }.take(100)
            }
            "Continue Watching" -> {
                // Return all movies - actual filtering should happen in UI based on watch progress
                return allMovies.take(100)
            }
            "You Might Like" -> {
                // Return all movies - actual filtering should happen in UI based on user preferences
                return allMovies.take(100)
            }
            else -> {
                // Handle "Released in {year}" pattern
                if (normalizedQuery.startsWith("Released in ")) {
                    val yearStr = normalizedQuery.removePrefix("Released in ").trim()
                    val year = yearStr.toIntOrNull()
                    if (year != null) {
                        return allMovies.filter { it.year == year }.take(100)
                    }
                }
                
                // Normal text search
                val lowercaseQuery = normalizedQuery.lowercase()
                return allMovies.filter { movie -> movie.matchesQuery(lowercaseQuery) }.take(100)
            }
        }
    }
}

private fun Movie.matchesQuery(query: String): Boolean {
    val title = movieName.lowercase()
    val directors = director.map { it.lowercase() }
    val cast = castMembers.map { it.lowercase() }
    val genreList = genres.map { it.lowercase() }

    if (title.contains(query)) return true
    if (directors.any { it.contains(query) }) return true
    if (cast.any { it.contains(query) }) return true
    if (genreList.any { it.contains(query) }) return true

    // Light typo tolerance: allow 1-character edit distance against title words.
    val tokens = title.split(" ").filter { it.isNotBlank() }
    return tokens.any { token -> levenshteinDistance(token, query) <= 1 }
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)

    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = min(
                min(curr[j - 1] + 1, prev[j] + 1),
                prev[j - 1] + cost
            )
        }
        for (j in prev.indices) prev[j] = curr[j]
    }
    return prev[b.length]
}

@Suppress("USELESS_ELVIS_CHECK")
private fun MovieEntity.asExternalModel() = Movie(
    id = id,
    movieName = movieName,
    year = year,
    posterUrl = posterUrl,
    duration = duration,
    synopsis = synopsis,
    director = director.split(",").filter { it.isNotBlank() },
    castMembers = castMembers.split(",").filter { it.isNotBlank() },
    genres = genres.split(",").filter { it.isNotBlank() },
    rating = rating,
    language = language,
    type = type,
    slug = slug
)

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
    slug = slug
)