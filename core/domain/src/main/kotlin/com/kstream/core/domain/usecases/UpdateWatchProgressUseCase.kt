package com.kstream.core.domain.usecases

import com.kstream.core.domain.models.WatchRepository

class UpdateWatchProgressUseCase(private val repository: WatchRepository) {
    suspend operator fun invoke(moviePath: String, title: String, posterUrl: String?, progressMs: Long, durationMs: Long) = 
        repository.updateWatchProgress(moviePath, title, posterUrl, progressMs, durationMs)
}
