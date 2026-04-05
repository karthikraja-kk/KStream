package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.MovieItem
import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.MovieRepository
import androidx.paging.PagingData

class GetMoviesByYearUseCase(private val repository: MovieRepository) {
    operator fun invoke(year: String): Flow<PagingData<MovieItem>> = repository.getMoviesByYear(year)
}
