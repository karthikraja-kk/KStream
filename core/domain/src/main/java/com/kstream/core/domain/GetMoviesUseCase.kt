package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.model.Movie
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMoviesUseCase @Inject constructor(
    private val movieRepository: MovieRepository
) {
    operator fun invoke(): Flow<List<Movie>> {
        return movieRepository.getMovies()
    }
}
