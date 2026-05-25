package com.kstream.feature.downloads

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
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
import org.json.JSONArray
import org.json.JSONObject
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
        .readTimeout(30, TimeUnit.SECONDS)
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

            outputStream.use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = currentBytes

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes
                            onProgress(progress)
                            downloadRepository.updateDownloadProgress(id, progress, downloadedBytes, totalBytes)
                        }
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
            }
            context.contentResolver.update(videoUri, contentValues, null, null)
        } else {
            val file = File(videoUri.path!!)
            val finalFile = File(file.parentFile, finalFileName)
            file.renameTo(finalFile)
            MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
        }
        downloadRepository.markDownloadComplete(id, videoUri.toString())
        addMetadataEntry(movieId, quality)
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
            context.contentResolver.openFileDescriptor(uri, "r")?.use { 
                it.statSize
            } ?: 0L
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
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
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
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
        }
        downloadRepository.deleteDownload(id)
        removeMetadataEntry(movieId, quality)
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

    private companion object {
        const val METADATA_FILE_NAME = ".metadata"
    }

    private fun getKStreamDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "KStream"
        )
    }

    private fun readMetadataEntries(): List<Pair<String, String>> {
        try {
            val encoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(METADATA_FILE_NAME, "${Environment.DIRECTORY_MOVIES}/KStream/")
                val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.query(
                    filesUri, projection, selection, selectionArgs, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        Uri.withAppendedPath(filesUri, id.toString())
                    } else null
                } ?: return emptyList()
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()?.trim()
                    ?: return emptyList()
            } else {
                val metadataFile = File(getKStreamDir(), METADATA_FILE_NAME)
                if (!metadataFile.exists()) return emptyList()
                metadataFile.readText().trim()
            }
            if (encoded.isEmpty()) return emptyList()
            val decoded = String(Base64.decode(encoded, Base64.NO_WRAP))
            val jsonArray = JSONArray(decoded)
            return (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                Pair(obj.getString("movieId"), obj.getString("quality"))
            }
        } catch (e: Exception) {
            Log.e("CustomDownloadManager", "Failed to read .metadata", e)
            return emptyList()
        }
    }

    private fun writeMetadataEntries(entries: List<Pair<String, String>>) {
        try {
            val jsonArray = JSONArray()
            entries.forEach { (movieId, quality) ->
                jsonArray.put(JSONObject().apply {
                    put("movieId", movieId)
                    put("quality", quality)
                })
            }
            val encoded = Base64.encodeToString(jsonArray.toString().toByteArray(), Base64.NO_WRAP)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(METADATA_FILE_NAME, "${Environment.DIRECTORY_MOVIES}/KStream/")
                val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val existingUri = context.contentResolver.query(
                    filesUri, projection, selection, selectionArgs, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        Uri.withAppendedPath(filesUri, id.toString())
                    } else null
                }

                val uri = existingUri ?: run {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, METADATA_FILE_NAME)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/KStream")
                    }
                    context.contentResolver.insert(filesUri, contentValues)
                } ?: throw Exception("Failed to create .metadata via MediaStore")

                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encoded.toByteArray()) }
            } else {
                val kStreamDir = getKStreamDir()
                if (!kStreamDir.exists()) kStreamDir.mkdirs()
                File(kStreamDir, METADATA_FILE_NAME).writeText(encoded)
            }
        } catch (e: Exception) {
            Log.e("CustomDownloadManager", "Failed to write .metadata", e)
        }
    }

    private fun addMetadataEntry(movieId: String, quality: String) {
        val entries = readMetadataEntries().toMutableList()
        if (entries.none { it.first == movieId && it.second == quality }) {
            entries.add(Pair(movieId, quality))
            writeMetadataEntries(entries)
        }
    }

    private fun removeMetadataEntry(movieId: String, quality: String) {
        val entries = readMetadataEntries().toMutableList()
        val removed = entries.removeAll { it.first == movieId && it.second == quality }
        if (removed) writeMetadataEntries(entries)
    }

    suspend fun recoverDownloads() = withContext(Dispatchers.IO) {
        try {
            val recoveryDone = userDataRepository.isDownloadRecoveryDone.first()
            if (recoveryDone) return@withContext

            val entries = readMetadataEntries()
            if (entries.isEmpty()) {
                userDataRepository.setDownloadRecoveryDone(true)
                return@withContext
            }

            for ((movieId, quality) in entries) {
                val downloadId = "${movieId}_${quality}"
                val existing = downloadRepository.getDownload(downloadId)
                if (existing != null) continue

                val movieWithMedia = movieRepository.getMovieWithMedia(movieId) ?: continue
                val movie = movieWithMedia.movie
                val media = movieWithMedia.media.firstOrNull { it.quality == quality }

                val fileName = "${movie.movieName.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${quality}.mp4"
                val fileResult = findDownloadedFile(fileName)
                val fileExists = fileResult != null

                val localFilePath = fileResult?.first?.toString() ?: ""
                val fileSizeBytes = fileResult?.second ?: 0L
                val fileSize = if (fileExists) {
                    val sizeMB = fileSizeBytes / (1024.0 * 1024.0)
                    if (sizeMB >= 1024) String.format("%.1f GB", sizeMB / 1024.0)
                    else String.format("%.0f MB", sizeMB)
                } else media?.fileSize ?: "0"

                val download = Download(
                    id = downloadId,
                    movieId = movieId,
                    title = movie.movieName,
                    posterUrl = movie.posterUrl,
                    quality = quality,
                    fileSize = fileSize,
                    downloadUrl = media?.downloadUrl1 ?: "",
                    localFilePath = localFilePath,
                    status = if (fileExists) DownloadStatus.COMPLETED else DownloadStatus.FAILED,
                    progress = if (fileExists) 1f else 0f,
                    downloadedBytes = fileSizeBytes,
                    totalBytes = fileSizeBytes,
                    statusMessage = if (fileExists) null else "File moved or deleted"
                )
                downloadRepository.insertDownload(download)
            }

            userDataRepository.setDownloadRecoveryDone(true)
        } catch (e: Exception) {
            Log.e("CustomDownloadManager", "Download recovery failed", e)
        }
    }

    /** Find a downloaded MP4 file by name. Returns (Uri, sizeBytes) or null. */
    private fun findDownloadedFile(fileName: String): Pair<Uri, Long>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE)
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND ${MediaStore.Video.Media.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf(fileName, "${Environment.DIRECTORY_MOVIES}/KStream/")
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    Pair(uri, size)
                } else null
            }
        } else {
            val file = File(getKStreamDir(), fileName)
            if (file.exists()) Pair(Uri.fromFile(file), file.length()) else null
        }
    }
}
