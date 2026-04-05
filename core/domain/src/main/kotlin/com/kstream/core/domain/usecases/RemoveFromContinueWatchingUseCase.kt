package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.WatchRepository

class RemoveFromContinueWatchingUseCase(private val repository: WatchRepository) {
    suspend operator fun invoke(moviePath: String) = repository.removeFromContinueWatching(moviePath)
}
