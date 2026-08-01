package com.kstream.core.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val movieId: String,
    val lastPosition: Long,
    val duration: Long,
    val completionPercent: Float,
    val lastUpdated: Long,
    val quality: String? = null
)

@Entity(tableName = "download")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val movieId: String,
    val title: String,
    val posterUrl: String,
    val quality: String,
    val fileSize: String,
    val downloadUrl: String,
    val localFilePath: String = "",
    val status: DownloadStatus,
    val progress: Float,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val statusMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, DELETED
}

@Entity(tableName = "liked_movies")
data class LikedMovieEntity(
    @PrimaryKey val movieId: String,
    val likedAt: Long
)

@Entity(
    tableName = "movies_cache",
    indices = [
        Index("movieName"),
        Index("genres")
    ]
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val movieName: String,
    val year: Int,
    val posterUrl: String,
    val duration: String,
    val synopsis: String,
    val director: String,
    val castMembers: String,
    val genres: String,
    val rating: String,
    val language: String,
    val type: String,
    val slug: String,
    val lastUpdated: String = ""
)

@Entity(tableName = "recommendations")
data class RecommendationEntity(
    @PrimaryKey val movieId: String,
    val score: Double,
    val computedAt: Long = System.currentTimeMillis()
)

/**
 * Per-movie video-engine memory.
 *
 *  - [engine]      : "EXO" / "VLC" — the engine that last produced a successful play for this movie.
 *                    `null` means "no winner recorded yet".
 *  - [lastFailMs]  : epoch-ms of the most recent both-engines-failed event. `null` means no failure.
 *                    Used to short-circuit subsequent attempts within a 5-minute lockout window
 *                    and surface "Source unavailable" + Try Again immediately.
 */
@Entity(tableName = "movie_engine_pref")
data class MovieEnginePrefEntity(
    @PrimaryKey val movieId: String,
    val engine: String? = null,
    val lastFailMs: Long? = null
)
