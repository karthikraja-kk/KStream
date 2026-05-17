package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.domain.repository.RecommendationRepository
import com.kstream.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetRecommendationsUseCase @Inject constructor(
    private val movieRepository: MovieRepository,
    private val recommendationRepository: RecommendationRepository
) {
    /**
     * Returns recommended movies by joining cached recommendation IDs
     * with the full movie list. Recommendations are pre-computed and
     * stored in Room — see [refreshRecommendations].
     */
    operator fun invoke(): Flow<List<Movie>> {
        return combine(
            movieRepository.getMovies(),
            recommendationRepository.getRecommendedMovieIds()
        ) { movies, recommendedIds ->
            val movieMap = movies.associateBy { it.id }
            recommendedIds.mapNotNull { movieMap[it] }
        }
    }

    /**
     * Recomputes recommendations from current user activity
     * and stores the results in Room.
     */
    suspend fun refreshRecommendations() {
        recommendationRepository.refreshRecommendations()
    }
}
