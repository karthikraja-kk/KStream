package com.kstream.core.data.repositories

import com.kstream.core.data.db.daos.WatchDao
import com.kstream.core.data.db.entities.ContinueWatchingEntity
import com.kstream.core.data.db.entities.RecentlyVisitedEntity
import com.kstream.core.domain.models.ContinueWatchingItem
import com.kstream.core.domain.models.RecentlyVisitedItem
import com.kstream.core.domain.models.WatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchRepositoryImpl @Inject constructor(
    private val watchDao: WatchDao
) : WatchRepository {

    override fun getContinueWatching(): Flow<List<ContinueWatchingItem>> {
        return watchDao.getContinueWatching().map { list ->
            list.map { 
                ContinueWatchingItem(
                    it.moviePath, it.title, it.posterUrl, 
                    it.progressMs, it.durationMs, it.updatedAt
                )
            }
        }
    }

    override suspend fun updateWatchProgress(
        moviePath: String, title: String, posterUrl: String?, 
        progressMs: Long, durationMs: Long
    ) {
        watchDao.insertContinueWatchingWithEviction(
            ContinueWatchingEntity(moviePath, title, posterUrl, progressMs, durationMs)
        )
    }

    override suspend fun removeFromContinueWatching(moviePath: String) {
        watchDao.deleteContinueWatching(moviePath)
    }

    override fun getRecentlyVisited(): Flow<List<RecentlyVisitedItem>> {
        return watchDao.getRecentlyVisited().map { list ->
            list.map { 
                RecentlyVisitedItem(
                    it.moviePath, it.title, it.posterUrl, 
                    it.year, it.visitedAt
                )
            }
        }
    }

    override suspend fun addRecentlyVisited(
        moviePath: String, title: String, posterUrl: String?, year: String
    ) {
        watchDao.insertRecentlyVisitedWithEviction(
            RecentlyVisitedEntity(moviePath, title, posterUrl, year)
        )
    }
}
