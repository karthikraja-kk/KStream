package com.kstream.core.model

data class WatchProgress(
    val movieId: String,
    val lastPosition: Long,
    val duration: Long,
    val completionPercent: Float,
    val lastUpdated: Long
)
