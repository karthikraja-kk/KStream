package com.kstream.core.domain.models

data class MovieDetail(
    val path: String,
    val title: String,
    val year: String,
    val synopsis: String,
    val genre: String,
    val cast: List<String>,
    val rating: String,
    val duration: String,
    val qualities: List<QualityOption>,
    val posterUrl: String?
)

data class QualityOption(
    val label: String,
    val server1Url: String?,
    val server2Url: String?,
    val watchServer1Url: String?,
    val watchServer2Url: String?
)

data class MovieItem(
    val path: String,
    val title: String,
    val year: String,
    val posterUrl: String?
)

data class YearItem(
    val year: String,
    val movieCount: Int
)

data class ContinueWatchingItem(
    val moviePath: String,
    val title: String,
    val posterUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

data class RecentlyVisitedItem(
    val moviePath: String,
    val title: String,
    val posterUrl: String?,
    val year: String,
    val visitedAt: Long
)

sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<T>(val data: T, val fromCache: Boolean) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}

data class AppSettings(
    val userName: String,
    val baseUrl: String,
    val workerUrl: String,
    val backupWorkerUrl: String,
    val qualityPreference: String,
    val volume: Int
)
