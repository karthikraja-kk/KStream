package com.kstream.core.domain

import com.kstream.core.domain.repository.WatchProgressRepository
import com.kstream.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SaveWatchProgressUseCase @Inject constructor(
    private val repository: WatchProgressRepository
) {
    suspend operator fun invoke(movieId: String, position: Long, duration: Long, quality: String? = null) {
        repository.saveProgress(movieId, position, duration, quality)
    }
}

class GetWatchProgressUseCase @Inject constructor(
    private val repository: WatchProgressRepository
) {
    suspend operator fun invoke(movieId: String): Long {
        return repository.getProgress(movieId)?.lastPosition ?: 0L
    }
}

class GetAllWatchProgressUseCase @Inject constructor(
    private val repository: WatchProgressRepository
) {
    operator fun invoke(): Flow<List<WatchProgress>> {
        return repository.getAllProgress()
    }
}
