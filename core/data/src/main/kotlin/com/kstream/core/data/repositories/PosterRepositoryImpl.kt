package com.kstream.core.data.repositories

import com.kstream.core.data.db.daos.PosterDao
import com.kstream.core.data.db.entities.PosterEntity
import com.kstream.core.domain.models.PosterRepository
import com.kstream.core.network.WorkerApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PosterRepositoryImpl @Inject constructor(
    private val api: WorkerApi,
    private val posterDao: PosterDao
) : PosterRepository {

    override fun getPoster(moviePath: String): Flow<String?> = flow {
        val cached = posterDao.getPoster(moviePath)
        if (cached != null) {
            emit(cached.posterUrl)
            // Revalidate if older than 30 days
            if (System.currentTimeMillis() - cached.cachedAt > 30L * 24 * 60 * 60 * 1000) {
                fetchAndCachePoster(moviePath)?.let { emit(it) }
            }
        } else {
            fetchAndCachePoster(moviePath)?.let { emit(it) } ?: emit(null)
        }
    }

    private suspend fun fetchAndCachePoster(moviePath: String): String? {
        return try {
            val response = api.fetchPoster(moviePath)
            val posterUrl = response.posterUrl
            posterDao.insertWithEviction(PosterEntity(moviePath, posterUrl))
            posterUrl
        } catch (e: Exception) {
            null
        }
    }
}
