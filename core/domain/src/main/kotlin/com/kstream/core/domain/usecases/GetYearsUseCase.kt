package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.YearItem
import com.kstream.core.domain.models.Resource
import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.MovieRepository

class GetYearsUseCase(private val repository: MovieRepository) {
    operator fun invoke(): Flow<Resource<List<YearItem>>> = repository.getYears()
}
