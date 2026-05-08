package com.kstream.core.data.repository

import com.kstream.core.data.local.dao.WatchProgressDao
import com.kstream.core.data.local.entities.WatchProgressEntity
import com.kstream.core.domain.repository.WatchProgressRepository
import com.kstream.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstWatchProgressRepository @Inject constructor(
    private val watchProgressDao: WatchProgressDao
) : WatchProgressRepository {

    override suspend fun saveProgress(movieId: String, position: Long, duration: Long, quality: String?) {
        val percent = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100 else 0f
        watchProgressDao.upsertProgress(
            WatchProgressEntity(
                movieId = movieId,
                lastPosition = position,
                duration = duration,
                completionPercent = percent,
                lastUpdated = System.currentTimeMillis(),
                quality = quality
            )
        )
    }

    override suspend fun getProgress(movieId: String): WatchProgress? {
        return watchProgressDao.getProgress(movieId)?.asExternalModel()
    }

    override fun getAllProgress(): Flow<List<WatchProgress>> {
        return watchProgressDao.getAllProgress().map { entities ->
            entities.map { it.asExternalModel() }
        }
    }
}

private fun WatchProgressEntity.asExternalModel() = WatchProgress(
    movieId = movieId,
    lastPosition = lastPosition,
    duration = duration,
    completionPercent = completionPercent,
    lastUpdated = lastUpdated,
    quality = quality
)
