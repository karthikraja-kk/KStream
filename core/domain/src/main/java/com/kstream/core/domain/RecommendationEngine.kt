package com.kstream.core.domain

import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress

/**
 * Local scoring-based recommendation engine.
 * Builds a weighted user preference profile from activity signals,
 * then scores every candidate movie against it.
 */
class RecommendationEngine {

    companion object {
        // Signal weights
        private const val WEIGHT_LIKED = 3.0
        private const val WEIGHT_DOWNLOADED = 2.0
        private const val WEIGHT_WATCHED_HIGH = 1.5   // >=50% completion
        private const val WEIGHT_WATCHED_LOW = 1.0     // <50% completion

        // Recency multipliers
        private const val RECENCY_7D = 1.5
        private const val RECENCY_30D = 1.2
        private const val RECENCY_OLDER = 1.0

        // Scoring dimension weights (must sum to 1.0)
        private const val GENRE_WEIGHT = 0.40
        private const val DIRECTOR_WEIGHT = 0.25
        private const val CAST_WEIGHT = 0.20
        private const val LANGUAGE_WEIGHT = 0.15

        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_RECOMMENDATIONS = 50
    }

    data class UserProfile(
        val genreScores: Map<String, Double>,
        val directorScores: Map<String, Double>,
        val castScores: Map<String, Double>,
        val languageScores: Map<String, Double>
    )

    fun recommend(
        allMovies: List<Movie>,
        watchProgress: List<WatchProgress>,
        likedMovieIds: List<String>,
        downloadedMovieIds: List<String>
    ): List<Movie> {
        val interactedMovieIds = mutableSetOf<String>()
        val movieMap = allMovies.associateBy { it.id }
        val now = System.currentTimeMillis()

        // Build weighted signals per movie as (movieId, weight) pairs
        val signals = mutableListOf<Pair<String, Double>>()

        // Liked movies
        likedMovieIds.forEach { id ->
            interactedMovieIds.add(id)
            signals.add(id to WEIGHT_LIKED)
        }

        // Downloaded movies
        downloadedMovieIds.forEach { id ->
            interactedMovieIds.add(id)
            signals.add(id to WEIGHT_DOWNLOADED)
        }

        // Watched movies
        watchProgress.forEach { wp ->
            interactedMovieIds.add(wp.movieId)
            val baseWeight = if (wp.completionPercent >= 50f) WEIGHT_WATCHED_HIGH else WEIGHT_WATCHED_LOW
            val recency = recencyMultiplier(now, wp.lastUpdated)
            signals.add(wp.movieId to baseWeight * recency)
        }

        if (signals.isEmpty()) return emptyList()

        // Build user preference profile
        val profile = buildProfile(signals, movieMap)

        // Score candidates (exclude already interacted movies)
        val candidates = allMovies.filter { it.id !in interactedMovieIds }
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .map { movie -> movie to scoreMovie(movie, profile) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(MAX_RECOMMENDATIONS)
            .map { it.first }
    }

    private fun buildProfile(
        signals: List<Pair<String, Double>>,
        movieMap: Map<String, Movie>
    ): UserProfile {
        val genreScores = mutableMapOf<String, Double>()
        val directorScores = mutableMapOf<String, Double>()
        val castScores = mutableMapOf<String, Double>()
        val languageScores = mutableMapOf<String, Double>()

        for ((movieId, weight) in signals) {
            val movie = movieMap[movieId] ?: continue

            movie.genres.forEach { genre ->
                genreScores[genre] = (genreScores[genre] ?: 0.0) + weight
            }
            movie.director.forEach { dir ->
                directorScores[dir] = (directorScores[dir] ?: 0.0) + weight
            }
            movie.castMembers.forEach { cast ->
                castScores[cast] = (castScores[cast] ?: 0.0) + weight
            }
            languageScores[movie.language] = (languageScores[movie.language] ?: 0.0) + weight
        }

        return UserProfile(genreScores, directorScores, castScores, languageScores)
    }

    private fun scoreMovie(movie: Movie, profile: UserProfile): Double {
        val genreScore = movie.genres.sumOf { profile.genreScores[it] ?: 0.0 }
        val directorScore = movie.director.sumOf { profile.directorScores[it] ?: 0.0 }
        val castScore = movie.castMembers.sumOf { profile.castScores[it] ?: 0.0 }
        val languageScore = profile.languageScores[movie.language] ?: 0.0

        return (genreScore * GENRE_WEIGHT) +
               (directorScore * DIRECTOR_WEIGHT) +
               (castScore * CAST_WEIGHT) +
               (languageScore * LANGUAGE_WEIGHT)
    }

    private fun recencyMultiplier(now: Long, timestamp: Long): Double {
        if (timestamp <= 0L) return RECENCY_OLDER
        val age = now - timestamp
        return when {
            age <= SEVEN_DAYS_MS -> RECENCY_7D
            age <= THIRTY_DAYS_MS -> RECENCY_30D
            else -> RECENCY_OLDER
        }
    }
}
