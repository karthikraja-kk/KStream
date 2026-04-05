package com.kstream.core.domain.models

import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData

interface MovieRepository {
    fun getMovieDetail(path: String): Flow<Resource<MovieDetail>>
    fun getMoviesByYear(year: String): Flow<PagingData<MovieItem>>
    fun getYears(): Flow<Resource<List<YearItem>>>
    suspend fun pingSource(url: String): Boolean
}

interface WatchRepository {
    fun getContinueWatching(): Flow<List<ContinueWatchingItem>>
    suspend fun updateWatchProgress(moviePath: String, title: String, posterUrl: String?, progressMs: Long, durationMs: Long)
    suspend fun removeFromContinueWatching(moviePath: String)
    fun getRecentlyVisited(): Flow<List<RecentlyVisitedItem>>
    suspend fun addRecentlyVisited(moviePath: String, title: String, posterUrl: String?, year: String)
}

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
}

interface PosterRepository {
    fun getPoster(moviePath: String): Flow<String?>
}
