package com.kstream.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface RecommendationRepository {
    fun getRecommendedMovieIds(): Flow<List<String>>
    suspend fun refreshRecommendations()
    suspend fun clearAll()
}
