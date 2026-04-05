package com.kstream.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "years")
data class YearEntity(
    @PrimaryKey val year: String,
    val movieCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val path: String,
    val title: String,
    val year: String,
    val synopsis: String,
    val genre: String,
    val castJson: String, // Stored as JSON string
    val rating: String,
    val duration: String,
    val qualityFoldersJson: String,
    val server1Url: String?,
    val server2Url: String?,
    val watchServer1Url: String?,
    val watchServer2Url: String?,
    val cachedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "posters")
data class PosterEntity(
    @PrimaryKey val moviePath: String,
    val posterUrl: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val moviePath: String,
    val title: String,
    val posterUrl: String?,
    val progressMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recently_visited")
data class RecentlyVisitedEntity(
    @PrimaryKey val moviePath: String,
    val title: String,
    val posterUrl: String?,
    val year: String,
    val visitedAt: Long = System.currentTimeMillis()
)
