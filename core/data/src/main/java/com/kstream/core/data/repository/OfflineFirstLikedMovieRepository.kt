package com.kstream.core.data.repository

import com.kstream.core.data.local.dao.LikedMovieDao
import com.kstream.core.data.local.entities.LikedMovieEntity
import com.kstream.core.domain.repository.LikedMovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class OfflineFirstLikedMovieRepository @Inject constructor(
    private val likedMovieDao: LikedMovieDao
) : LikedMovieRepository {

    private val toggleMutex = Mutex()

    override fun isLiked(movieId: String): Flow<Boolean> {
        return likedMovieDao.isLiked(movieId)
    }

    override fun getAllLikedMovieIds(): Flow<List<String>> {
        return likedMovieDao.getAllLiked().map { entities ->
            entities.map { it.movieId }
        }
    }

    override suspend fun toggleLike(movieId: String) {
        toggleMutex.withLock {
            val currentlyLiked = likedMovieDao.isLiked(movieId).first()
            if (currentlyLiked) {
                likedMovieDao.unlike(movieId)
            } else {
                likedMovieDao.like(LikedMovieEntity(movieId = movieId, likedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun clearAll() {
        likedMovieDao.clearAll()
    }
}
