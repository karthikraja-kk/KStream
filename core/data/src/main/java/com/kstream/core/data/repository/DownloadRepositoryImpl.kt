package com.kstream.core.data.repository

import com.kstream.core.data.local.dao.DownloadDao
import com.kstream.core.data.local.entities.DownloadEntity
import com.kstream.core.data.local.entities.DownloadStatus as EntityDownloadStatus
import com.kstream.core.model.Download
import com.kstream.core.model.DownloadStatus
import com.kstream.core.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override fun getDownloads(): Flow<List<Download>> {
        return downloadDao.getDownloads().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDownload(id: String): Download? {
        return downloadDao.getDownloadById(id)?.toDomain()
    }

    override suspend fun insertDownload(download: Download) {
        downloadDao.upsertDownload(download.toEntity())
    }

    override suspend fun updateDownloadProgress(id: String, progress: Float, downloadedBytes: Long, totalBytes: Long) {
        downloadDao.updateProgress(id, progress, downloadedBytes, totalBytes)
    }

    override suspend fun updateDownloadStatus(id: String, status: DownloadStatus) {
        downloadDao.updateStatus(id, status.toEntity())
    }

    override suspend fun updateDownloadStatusWithMessage(id: String, status: DownloadStatus, message: String?) {
        downloadDao.updateStatusWithMessage(id, status.toEntity(), message)
    }

    override suspend fun markDownloadComplete(id: String, localFilePath: String) {
        downloadDao.markComplete(id, localFilePath, EntityDownloadStatus.COMPLETED)
    }

    override suspend fun deleteDownload(id: String) {
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun deleteAllDownloads() {
        downloadDao.deleteAll()
    }
}

private fun EntityDownloadStatus.toDomain() = when (this) {
    EntityDownloadStatus.QUEUED -> DownloadStatus.QUEUED
    EntityDownloadStatus.DOWNLOADING -> DownloadStatus.DOWNLOADING
    EntityDownloadStatus.PAUSED -> DownloadStatus.PAUSED
    EntityDownloadStatus.COMPLETED -> DownloadStatus.COMPLETED
    EntityDownloadStatus.FAILED -> DownloadStatus.FAILED
    EntityDownloadStatus.DELETED -> DownloadStatus.DELETED
}

private fun DownloadStatus.toEntity() = when (this) {
    DownloadStatus.QUEUED -> EntityDownloadStatus.QUEUED
    DownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
    DownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
    DownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
    DownloadStatus.FAILED -> EntityDownloadStatus.FAILED
    DownloadStatus.DELETED -> EntityDownloadStatus.DELETED
}

private fun DownloadEntity.toDomain() = Download(
    id = id,
    movieId = movieId,
    title = title,
    posterUrl = posterUrl,
    quality = quality,
    fileSize = fileSize,
    downloadUrl = downloadUrl,
    localFilePath = localFilePath,
    status = status.toDomain(),
    progress = progress,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    statusMessage = statusMessage,
    createdAt = createdAt
)

private fun Download.toEntity() = DownloadEntity(
    id = id,
    movieId = movieId,
    title = title,
    posterUrl = posterUrl,
    quality = quality,
    fileSize = fileSize,
    downloadUrl = downloadUrl,
    localFilePath = localFilePath,
    status = status.toEntity(),
    progress = progress,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    statusMessage = statusMessage,
    createdAt = createdAt
)
