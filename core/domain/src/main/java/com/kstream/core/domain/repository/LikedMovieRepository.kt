package com.kstream.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface LikedMovieRepository {
    fun isLiked(movieId: String): Flow<Boolean>
    fun getAllLikedMovieIds(): Flow<List<String>>
    suspend fun toggleLike(movieId: String)
    suspend fun clearAll()
}
