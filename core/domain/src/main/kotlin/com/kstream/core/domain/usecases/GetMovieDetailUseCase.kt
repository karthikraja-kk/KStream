package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.MovieDetail
import com.kstream.core.domain.models.Resource
import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.MovieRepository

class GetMovieDetailUseCase(private val repository: MovieRepository) {
    operator fun invoke(path: String): Flow<Resource<MovieDetail>> = repository.getMovieDetail(path)
}
