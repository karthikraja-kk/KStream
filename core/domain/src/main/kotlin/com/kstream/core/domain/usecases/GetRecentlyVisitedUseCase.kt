package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.RecentlyVisitedItem
import kotlinx.coroutines.flow.Flow

import com.kstream.core.domain.models.WatchRepository

class GetRecentlyVisitedUseCase(private val repository: WatchRepository) {
    operator fun invoke(): Flow<List<RecentlyVisitedItem>> = repository.getRecentlyVisited()
}
