package com.kstream.core.data.local.entities

import androidx.room.Entity
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
    @PrimaryKey val movieId: String,
    val title: String,
    val posterUrl: String,
    val quality: String,
    val fileSize: String,
    val downloadUrl: String,
    val status: DownloadStatus,
    val progress: Float,
    val localFilePath: String? = null
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, DELETED
}

@Entity(tableName = "movies_cache")
data class MovieEntity(
    @PrimaryKey val id: String,
    val movieName: String,
    val year: Int,
    val posterUrl: String,
    val duration: String,
    val synopsis: String,
    val director: String, // Stored as comma-separated string or use TypeConverter
    val castMembers: String,
    val genres: String,
    val rating: String,
    val language: String,
    val type: String,
    val slug: String
)
