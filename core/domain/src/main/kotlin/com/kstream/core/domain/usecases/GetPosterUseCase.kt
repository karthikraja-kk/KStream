package com.kstream.core.domain.usecases

import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.PosterRepository

class GetPosterUseCase(private val repository: PosterRepository) {
    operator fun invoke(moviePath: String): Flow<String?> = repository.getPoster(moviePath)
}
