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
    val movieUrl: String = "",
    val lastUpdated: String = ""
)

@Entity(tableName = "recommendations")
data class RecommendationEntity(
    @PrimaryKey val movieId: String,
    val score: Double,
    val computedAt: Long = System.currentTimeMillis()
)
