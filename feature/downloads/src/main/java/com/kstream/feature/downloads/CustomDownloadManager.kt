package com.kstream.feature.downloads

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.model.Download
import com.kstream.core.model.DownloadStatus
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.domain.repository.UserDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val movieRepository: MovieRepository,
    private val userDataRepository: UserDataRepository,
    private val networkMonitor: NetworkMonitor
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val networkPauseJob = AtomicReference<Job?>(null)

    init {
        observeNetwork()
    }

    private fun observeNetwork() {
        networkMonitor.isOnline
            .onEach { isOnline ->
                if (isOnline) {
                    // Cancel any pending pause — network came back in time
                    networkPauseJob.getAndSet(null)?.cancel()
                    resumeAllPausedByNetwork()
                } else {
                    // Debounce: wait 2s before pausing to survive transient handoffs
                    val job = scope.launch {
                        delay(2000)
                        pauseAllActiveDueToNetwork()
                    }
                    networkPauseJob.getAndSet(job)?.cancel()
                }
            }
            .launchIn(scope)
    }

    private fun pauseAllActiveDueToNetwork() {
        activeDownloads.forEach { (id, job) ->
            job.cancel("No internet")
            activeDownloads.remove(id)
            scope.launch {
                downloadRepository.updateDownloadStatusWithMessage(id, DownloadStatus.PAUSED, "Paused: No Internet")
            }
        }
        updateForegroundService()
    }

    private fun updateForegroundService() {
        val count = activeDownloads.size
        if (count > 0) {
            try { DownloadForegroundService.start(context, count) } catch (_: Exception) {}
        } else {
            try { DownloadForegroundService.stop(context) } catch (_: Exception) {}
        }
    }

    private fun resumeAllPausedByNetwork() {
        scope.launch {
            val allDownloads = downloadRepository.getDownloads().first()
            allDownloads.filter { it.status == DownloadStatus.PAUSED && it.statusMessage == "Paused: No Internet" }
                .forEach { download ->
                    resumeDownload(download.id) { }
                }
        }
    }

    suspend fun downloadMovie(
        movieId: String,
        quality: String,
        url: String,
        movieName: String,
        posterUrl: String,
        fileSize: String,
        onProgress: (Float) -> Unit
    ): Result<String> {
        val id = "${movieId}_$quality"
        
        activeDownloads[id]?.cancel()
        
        val job = scope.launch {
            try {
                updateForegroundService()
                performDownload(id, movieId, quality, url, movieName, posterUrl, fileSize, onProgress)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    val isNetworkError = e is java.net.UnknownHostException ||
                            e is java.net.SocketException ||
                            e is java.net.ConnectException ||
                            e.message?.contains("network", ignoreCase = true) == true ||
                            e.message?.contains("internet", ignoreCase = true) == true ||
                            e.message?.contains("Unable to resolve host", ignoreCase = true) == true
                    
                    if (isNetworkError) {
                        downloadRepository.updateDownloadStatusWithMessage(id, DownloadStatus.PAUSED, "Waiting for internet connection...")
                    } else {
                        downloadRepository.updateDownloadStatusWithMessage(id, DownloadStatus.FAILED, e.message)
                    }
                }
            } finally {
                activeDownloads.remove(id)
                updateForegroundService()
            }
        }
        
        activeDownloads[id] = job
        return Result.success("Download started")
    }

    private suspend fun performDownload(
        id: String,
        movieId: String,
        quality: String,
        url: String,
        movieName: String,
        posterUrl: String,
        fileSize: String,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val baseFileName = "${movieName.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${quality}.mp4"
        val pendingFileName = baseFileName
        val contentResolver = context.contentResolver
        
        var download = downloadRepository.getDownload(id)
        var videoUri = download?.localFilePath?.let { Uri.parse(it) }
        var currentBytes = 0L

        if (download == null || videoUri == null || !checkFileExists(videoUri.toString())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, pendingFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/KStream")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                videoUri = contentResolver.insert(collection, contentValues)
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val kStreamDir = File(moviesDir, "KStream")
                if (!kStreamDir.exists() && !kStreamDir.mkdirs()) {
                    throw Exception("Failed to create download directory: ${kStreamDir.absolutePath}")
                }
                val file = File(kStreamDir, pendingFileName)
                if (!file.exists()) file.createNewFile()
                videoUri = Uri.fromFile(file)
            }

            if (videoUri == null) throw Exception("Failed to create file")

            download = Download(
                id = id,
                movieId = movieId,
                quality = quality,
                title = movieName,
                posterUrl = posterUrl,
                fileSize = fileSize,
                downloadUrl = url,
                localFilePath = videoUri.toString(),
                status = DownloadStatus.DOWNLOADING,
                progress = 0f
            )
            downloadRepository.insertDownload(download)
        } else {
            currentBytes = getFileSize(videoUri)
            downloadRepository.updateDownloadStatus(id, DownloadStatus.DOWNLOADING)
        }

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$currentBytes-")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) { 
                    finalizeDownload(id, movieId, quality, videoUri!!, baseFileName)
                    return@use
                }
                throw Exception("Server returned ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = (body.contentLength() + currentBytes).takeIf { it > currentBytes } ?: 0L
            
            val outputStream = if (videoUri!!.scheme == "file") {
                val file = File(videoUri.path!!)
                java.io.FileOutputStream(file, currentBytes > 0)
            } else {
                contentResolver.openOutputStream(videoUri, if (currentBytes > 0) "wa" else "wt")
            } ?: throw Exception("Failed to open output stream")

            java.io.BufferedOutputStream(outputStream, 256 * 1024).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(128 * 1024) // 128KB buffer for faster throughput
                    var bytesRead: Int
                    var downloadedBytes = currentBytes
                    var lastProgressUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val now = System.currentTimeMillis()
                            // Throttle progress updates to every 500ms to reduce DB writes
                            if (now - lastProgressUpdate >= 500) {
                                val progress = downloadedBytes.toFloat() / totalBytes
                                onProgress(progress)
                                downloadRepository.updateDownloadProgress(id, progress, downloadedBytes, totalBytes)
                                lastProgressUpdate = now
                            }
                        }
                    }
                    // Final progress update to ensure 100% is recorded
                    if (totalBytes > 0) {
                        val progress = downloadedBytes.toFloat() / totalBytes
                        onProgress(progress)
                        downloadRepository.updateDownloadProgress(id, progress, downloadedBytes, totalBytes)
                    }
                }
            }
        }

        finalizeDownload(id, movieId, quality, videoUri!!, baseFileName)
    }

    private suspend fun finalizeDownload(id: String, movieId: String, quality: String, videoUri: Uri, finalFileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                put(MediaStore.Video.Media.IS_PENDING, 0)
                put(MediaStore.Video.Media.DESCRIPTION, "$movieId|$quality")
            }
            context.contentResolver.update(videoUri, contentValues, null, null)
        } else {
            val file = File(videoUri.path!!)
            val finalFile = File(file.parentFile, finalFileName)
            file.renameTo(finalFile)
            MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
        }
        downloadRepository.markDownloadComplete(id, videoUri.toString())
    }

    fun pauseDownload(id: String) {
        activeDownloads[id]?.cancel()
        activeDownloads.remove(id)
        scope.launch {
            downloadRepository.updateDownloadStatus(id, DownloadStatus.PAUSED)
        }
        updateForegroundService()
    }

    suspend fun resumeDownload(id: String, onProgress: (Float) -> Unit) {
        val download = downloadRepository.getDownload(id) ?: return
        downloadMovie(
            movieId = download.movieId,
            quality = download.quality,
            url = download.downloadUrl,
            movieName = download.title,
            posterUrl = download.posterUrl,
            fileSize = download.fileSize,
            onProgress = onProgress
        )
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                File(uri.path!!).length()
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { 
                    it.statSize
                } ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getLocalPath(movieId: String, quality: String): String? {
        val id = "${movieId}_$quality"
        return downloadRepository.getDownload(id)?.let { download ->
            if (download.status == DownloadStatus.COMPLETED) {
                if (checkFileExists(download.localFilePath)) {
                    download.localFilePath
                } else null
            } else null
        }
    }

    suspend fun checkFileExists(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(filePath)
            if (uri.scheme == "file") {
                val file = File(uri.path!!)
                file.exists() && file.length() > 0
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteDownload(movieId: String, quality: String): Boolean {
        val id = "${movieId}_$quality"
        activeDownloads[id]?.cancel()
        activeDownloads.remove(id)
        
        val download = downloadRepository.getDownload(id) ?: return false
        try {
            val uri = Uri.parse(download.localFilePath)
            if (uri.scheme == "file") {
                File(uri.path!!).delete()
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
        }
        downloadRepository.deleteDownload(id)
        return true
    }

    suspend fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    suspend fun getFolderUri(): String? = "/Movies/KStream"
    fun getDownloadDirectoryDisplay(): String = "/Movies/KStream"

    private fun getKStreamDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "KStream"
        )
    }

    suspend fun recoverDownloads() = withContext(Dispatchers.IO) {
        try {
            fileScanRecovery()
        } catch (e: Exception) {
            Log.e("CustomDownloadManager", "Download recovery failed", e)
        }
    }

    private suspend fun fileScanRecovery() {
        val videoFiles = listVideoFilesInKStream()
        if (videoFiles.isEmpty()) return

        val existingIds = downloadRepository.getDownloads().first().map { it.id }.toSet()
        val allMovies = movieRepository.getMovies().first()
        if (allMovies.isEmpty()) return

        val sortedMovies = allMovies.sortedByDescending { it.movieName.length }

        for ((uri, fileName, sizeBytes, description) in videoFiles) {
            // Try description-based matching first (embedded during download)
            val descParts = description?.split("|")
            if (descParts != null && descParts.size == 2) {
                val movieId = descParts[0]
                val quality = descParts[1]
                val downloadId = "${movieId}_$quality"
                if (downloadId in existingIds) continue

                val movieWithMedia = movieRepository.getMovieWithMedia(movieId)
                if (movieWithMedia != null) {
                    val media = movieWithMedia.media.firstOrNull { it.quality == quality }
                    val download = Download(
                        id = downloadId,
                        movieId = movieId,
                        title = movieWithMedia.movie.movieName,
                        posterUrl = movieWithMedia.movie.posterUrl,
                        quality = quality,
                        fileSize = formatBytesForDisplay(sizeBytes),
                        downloadUrl = media?.downloadUrl1 ?: "",
                        localFilePath = uri.toString(),
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        downloadedBytes = sizeBytes,
                        totalBytes = sizeBytes
                    )
                    downloadRepository.insertDownload(download)
                    continue
                }
            }

            // Fallback: filename-based matching
            val nameWithoutExt = fileName.removeSuffix(".mp4").removeSuffix(".MP4")
            for (movie in sortedMovies) {
                val sanitizedName = movie.movieName.replace("[^a-zA-Z0-9]".toRegex(), "_")
                if (!nameWithoutExt.startsWith("${sanitizedName}_")) continue

                val quality = nameWithoutExt.removePrefix("${sanitizedName}_")
                if (quality.isBlank()) continue

                val downloadId = "${movie.id}_$quality"
                if (downloadId in existingIds) break

                val movieWithMedia = movieRepository.getMovieWithMedia(movie.id) ?: continue
                val media = movieWithMedia.media.firstOrNull { it.quality == quality }
                if (media == null) continue

                val download = Download(
                    id = downloadId,
                    movieId = movie.id,
                    title = movie.movieName,
                    posterUrl = movie.posterUrl,
                    quality = quality,
                    fileSize = formatBytesForDisplay(sizeBytes),
                    downloadUrl = media.downloadUrl1 ?: "",
                    localFilePath = uri.toString(),
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    downloadedBytes = sizeBytes,
                    totalBytes = sizeBytes
                )
                downloadRepository.insertDownload(download)
                break
            }
        }
    }

    private data class VideoFileInfo(val uri: Uri, val fileName: String, val sizeBytes: Long, val description: String?)

    private fun listVideoFilesInKStream(): List<VideoFileInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return listVideoFilesViaMediaStore()
        }
        val kStreamDir = getKStreamDir()
        if (!kStreamDir.exists()) return emptyList()

        val mp4Files = kStreamDir.listFiles()?.filter {
            it.isFile && it.extension.equals("mp4", ignoreCase = true)
        } ?: return emptyList()

        return mp4Files.map { file ->
            VideoFileInfo(Uri.fromFile(file), file.name, file.length(), null)
        }
    }

    private fun listVideoFilesViaMediaStore(): List<VideoFileInfo> {
        val results = mutableListOf<VideoFileInfo>()
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DESCRIPTION
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_MOVIES}/KStream%")

        try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val descCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DESCRIPTION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val desc = cursor.getString(descCol)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    results.add(VideoFileInfo(uri, name, size, desc))
                }
            }
        } catch (e: Exception) {
            Log.e("CustomDownloadManager", "MediaStore query failed", e)
        }
        return results
    }

    private fun formatBytesForDisplay(bytes: Long): String {
        val sizeMB = bytes / (1024.0 * 1024.0)
        return if (sizeMB >= 1024) String.format("%.1f GB", sizeMB / 1024.0)
        else String.format("%.0f MB", sizeMB)
    }

}
