package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.ContinueWatchingItem
import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.WatchRepository

class GetContinueWatchingUseCase(private val repository: WatchRepository) {
    operator fun invoke(): Flow<List<ContinueWatchingItem>> = repository.getContinueWatching()
}
