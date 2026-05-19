package com.kstream.core.domain

import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.model.MovieWithMedia
import javax.inject.Inject

class RefreshMovieMediaUseCase @Inject constructor(
    private val movieRepository: MovieRepository
) {
    suspend operator fun invoke(slug: String): com.kstream.core.model.RefreshMediaResult {
        return movieRepository.refreshMedia(slug)
    }
}
