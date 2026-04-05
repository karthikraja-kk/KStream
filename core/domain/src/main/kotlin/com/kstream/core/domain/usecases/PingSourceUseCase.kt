package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.MovieRepository

class PingSourceUseCase(private val repository: MovieRepository) {
    suspend operator fun invoke(url: String): Boolean = repository.pingSource(url)
}
