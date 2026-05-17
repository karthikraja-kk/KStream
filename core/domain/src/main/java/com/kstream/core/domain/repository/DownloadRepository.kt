package com.kstream.core.domain.repository

import com.kstream.core.model.Download
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getDownloads(): Flow<List<Download>>
    suspend fun getDownload(id: String): Download?
    suspend fun insertDownload(download: Download)
    suspend fun updateDownloadProgress(id: String, progress: Float, downloadedBytes: Long, totalBytes: Long)
    suspend fun updateDownloadStatus(id: String, status: com.kstream.core.model.DownloadStatus)
    suspend fun updateDownloadStatusWithMessage(id: String, status: com.kstream.core.model.DownloadStatus, message: String?)
    suspend fun markDownloadComplete(id: String, localFilePath: String)
    suspend fun deleteDownload(id: String)
    suspend fun deleteAllDownloads()
}