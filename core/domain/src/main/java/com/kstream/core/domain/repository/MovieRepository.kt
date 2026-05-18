package com.kstream.core.domain.repository

import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import com.kstream.core.model.ScanStatusInfo
import com.kstream.core.model.ScanTriggerResult
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    fun getMovies(): Flow<List<Movie>>
    suspend fun getMovieWithMedia(movieId: String): MovieWithMedia?
    suspend fun syncMovies()
    suspend fun searchMovies(query: String): List<Movie>
    suspend fun refreshMedia(movieId: String): MovieWithMedia?
    suspend fun triggerScan(): ScanTriggerResult
    suspend fun getScanStatus(): ScanStatusInfo
    suspend fun clearCache()
}
