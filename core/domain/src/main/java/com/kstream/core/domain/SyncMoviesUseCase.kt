package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import javax.inject.Inject

class SyncMoviesUseCase @Inject constructor(
    private val movieRepository: MovieRepository
) {
    suspend operator fun invoke() {
        movieRepository.syncMovies()
    }
}
