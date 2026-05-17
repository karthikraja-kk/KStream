package com.kstream.core.model

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, DELETED
}

data class Download(
    val id: String,
    val movieId: String,
    val title: String,
    val posterUrl: String,
    val quality: String,
    val fileSize: String,
    val downloadUrl: String,
    val localFilePath: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val statusMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)