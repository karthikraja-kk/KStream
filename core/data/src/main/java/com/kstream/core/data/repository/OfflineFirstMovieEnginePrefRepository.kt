package com.kstream.core.data.repository

import com.kstream.core.data.local.dao.MovieEnginePrefDao
import com.kstream.core.data.local.entities.MovieEnginePrefEntity
import com.kstream.core.domain.repository.MovieEnginePref
import com.kstream.core.domain.repository.MovieEnginePrefRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstMovieEnginePrefRepository @Inject constructor(
    private val dao: MovieEnginePrefDao
) : MovieEnginePrefRepository {

    override suspend fun get(movieId: String): MovieEnginePref? =
        dao.get(movieId)?.let { MovieEnginePref(it.engine, it.lastFailMs) }

    override suspend fun setEngine(movieId: String, engine: String?) {
        val existing = dao.get(movieId)
        if (existing == null) {
            dao.upsert(MovieEnginePrefEntity(movieId = movieId, engine = engine, lastFailMs = null))
        } else {
            dao.updateEngine(movieId, engine)
        }
    }

    override suspend fun recordFailure(movieId: String, ts: Long) {
        val existing = dao.get(movieId)
        if (existing == null) {
            dao.upsert(MovieEnginePrefEntity(movieId = movieId, engine = null, lastFailMs = ts))
        } else {
            dao.updateLastFail(movieId, ts)
        }
    }

    override suspend fun clearFailure(movieId: String) {
        if (dao.get(movieId) != null) dao.updateLastFail(movieId, null)
    }

    override suspend fun clearAll() = dao.clearAll()
}
