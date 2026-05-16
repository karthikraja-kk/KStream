package com.kstream.core.domain.repository

import com.kstream.core.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    suspend fun saveProgress(movieId: String, position: Long, duration: Long, quality: String? = null)
    suspend fun getProgress(movieId: String): WatchProgress?
    fun getAllProgress(): Flow<List<WatchProgress>>
    suspend fun deleteProgress(movieId: String)
}
