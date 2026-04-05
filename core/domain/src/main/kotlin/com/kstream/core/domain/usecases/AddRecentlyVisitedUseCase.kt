package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.WatchRepository

class AddRecentlyVisitedUseCase(private val repository: WatchRepository) {
    suspend operator fun invoke(moviePath: String, title: String, posterUrl: String?, year: String) = 
        repository.addRecentlyVisited(moviePath, title, posterUrl, year)
}
