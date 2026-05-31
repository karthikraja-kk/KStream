package com.kstream.feature.details

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.common.toUserMessage
import com.kstream.core.domain.GetMovieDetailsUseCase
import com.kstream.core.domain.repository.WatchProgressRepository
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.domain.repository.LikedMovieRepository
import com.kstream.core.model.DownloadStatus
import com.kstream.core.model.MovieWithMedia
import com.kstream.feature.downloads.CustomDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val isLoading: Boolean = false,
    val movieWithMedia: MovieWithMedia? = null,
    val selectedQuality: String? = null,
    val selectedFileSize: String? = null,
    val downloadState: Int = -1,
    val downloadProgress: Float = -1f,
    val isInDownloads: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = true,
    val hasWatchProgress: Boolean = false,
    val isRefreshingLinks: Boolean = false,
    val refreshError: String? = null,
    val isLiked: Boolean = false
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase,
    private val refreshMovieMediaUseCase: com.kstream.core.domain.RefreshMovieMediaUseCase,
    private val watchProgressRepository: WatchProgressRepository,
    private val likedMovieRepository: LikedMovieRepository,
    private val customDownloadManager: CustomDownloadManager,
    private val downloadRepository: DownloadRepository,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val movieId: String = android.net.Uri.decode(savedStateHandle.get<String>("movieId") ?: "")

    private val _uiState = MutableStateFlow(DetailsUiState(isLoading = true))
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        if (movieId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Movie not found") }
        } else {
            fetchMovieDetails()
            observeDownloads()
            observeNetworkState()
            observeWatchProgress()
            observeLikedState()
        }
    }

    private fun observeNetworkState() {
        networkMonitor.isOnline
            .onEach { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeWatchProgress() {
        viewModelScope.launch {
            try {
                val progress = watchProgressRepository.getProgress(movieId)
                _uiState.update { 
                    it.copy(hasWatchProgress = progress != null && progress.lastPosition > 0 && progress.completionPercent < 95f) 
                }
            } catch (_: Exception) { }
        }
    }

    private fun observeLikedState() {
        likedMovieRepository.isLiked(movieId)
            .onEach { liked ->
                _uiState.update { it.copy(isLiked = liked) }
            }
            .launchIn(viewModelScope)
    }

    fun toggleLike() {
        viewModelScope.launch {
            try {
                likedMovieRepository.toggleLike(movieId)
            } catch (_: Exception) { }
        }
    }

    private fun observeDownloads() {
        combine(
            downloadRepository.getDownloads().catch { emit(emptyList()) },
            _uiState.map { it.selectedQuality }.distinctUntilChanged()
        ) { downloads, selectedQuality ->
            val movieDownloads = downloads.filter { it.movieId == movieId }
            val download = if (selectedQuality != null) {
                val downloadId = "${movieId}_$selectedQuality"
                movieDownloads.find { it.id == downloadId }
            } else {
                movieDownloads.firstOrNull()
            }
            download
        }.onEach { download ->
            _uiState.update { 
                it.copy(
                    downloadState = when (download?.status) {
                        DownloadStatus.DOWNLOADING -> 2
                        DownloadStatus.PAUSED -> 3
                        DownloadStatus.COMPLETED -> 4
                        DownloadStatus.FAILED -> 16
                        DownloadStatus.QUEUED -> 1
                        else -> -1
                    },
                    downloadProgress = download?.progress ?: -1f,
                    isInDownloads = download != null && download.status != DownloadStatus.DELETED
                )
            }
        }.launchIn(viewModelScope)
    }

    fun fetchMovieDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    val result = getMovieDetailsUseCase(movieId)
                    val highestQualityMedia = result?.media?.maxByOrNull { 
                        it.quality.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 
                    }
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            movieWithMedia = result,
                            selectedQuality = highestQualityMedia?.quality,
                            selectedFileSize = highestQualityMedia?.fileSize
                        ) 
                    }
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 3) kotlinx.coroutines.delay(1000L * attempt)
                }
            }
            _uiState.update { it.copy(isLoading = false, error = lastError?.toUserMessage() ?: "Failed to load movie") }
        }
    }

    fun refreshMovieDetails() {
        fetchMovieDetails()
    }

    fun onQualitySelected(quality: String) {
        val fileSize = _uiState.value.movieWithMedia?.media?.find { it.quality == quality }?.fileSize
        _uiState.update { it.copy(selectedQuality = quality, selectedFileSize = fileSize) }
    }

    fun downloadMovie() {
        performDownload()
    }

    fun onStartOver() {
        viewModelScope.launch {
            try {
                val existing = watchProgressRepository.getProgress(movieId)
                if (existing != null) {
                    watchProgressRepository.saveProgress(movieId, 0L, existing.duration, existing.quality)
                }
                _uiState.update { it.copy(hasWatchProgress = false) }
            } catch (_: Exception) { }
        }
    }

    private fun performDownload() {
        val movieWithMedia = _uiState.value.movieWithMedia ?: return
        val quality = _uiState.value.selectedQuality ?: return
        val media = movieWithMedia.media.find { it.quality == quality } ?: return
        val url = media.downloadUrl1 ?: media.downloadUrl2

        if (url == null) {
            // URLs expired/empty — trigger refresh flow
            refreshAndRetryDownload(quality)
            return
        }

        val downloadId = "${movieId}_$quality"
        
        viewModelScope.launch {
            val existingDownload = downloadRepository.getDownload(downloadId)
            if (existingDownload != null && existingDownload.status == DownloadStatus.COMPLETED) {
                if (customDownloadManager.checkFileExists(existingDownload.localFilePath)) {
                    Toast.makeText(context, "Movie already in downloads", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            Toast.makeText(context, "Download started: ${movieWithMedia.movie.movieName}", Toast.LENGTH_SHORT).show()

            viewModelScope.launch {
                customDownloadManager.downloadMovie(
                    movieId = movieId,
                    quality = quality,
                    url = url,
                    movieName = movieWithMedia.movie.movieName,
                    posterUrl = movieWithMedia.movie.posterUrl,
                    fileSize = media.fileSize,
                    onProgress = { /* Observed via flow */ }
                ).onFailure { error ->
                    // Check if failure is due to expired URL (HTTP 403/404)
                    val msg = error.message ?: ""
                    if (msg.contains("403") || msg.contains("404") || msg.contains("expired")) {
                        refreshAndRetryDownload(quality)
                    } else {
                        Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun refreshAndRetryDownload(quality: String) {
        if (_uiState.value.isRefreshingLinks) return

        val slug = _uiState.value.movieWithMedia?.movie?.slug ?: run {
            Toast.makeText(context, "Could not refresh links. Movie info missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadId = "${movieId}_$quality"

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingLinks = true, refreshError = null) }
            downloadRepository.updateDownloadStatusWithMessage(downloadId, DownloadStatus.DOWNLOADING, "Refreshing expired links...")
            Toast.makeText(context, "Link expired. Refreshing...", Toast.LENGTH_SHORT).show()

            try {
                // Trigger GHA refresh and poll until done (max 2 min)
                val maxAttempts = 24
                for (attempt in 0..maxAttempts) {
                    if (attempt > 0) kotlinx.coroutines.delay(5000)
                    when (val result = refreshMovieMediaUseCase(slug)) {
                        is com.kstream.core.model.RefreshMediaResult.Done -> {
                            // GHA finished — re-fetch movie details for fresh URLs
                            val refreshed = getMovieDetailsUseCase(movieId)
                            if (refreshed != null) {
                                _uiState.update { it.copy(movieWithMedia = refreshed, isRefreshingLinks = false) }
                                val freshMedia = refreshed.media.find { it.quality == quality }
                                val freshUrl = freshMedia?.downloadUrl1 ?: freshMedia?.downloadUrl2
                                if (freshUrl != null) {
                                    customDownloadManager.downloadMovie(
                                        movieId = movieId,
                                        quality = quality,
                                        url = freshUrl,
                                        movieName = refreshed.movie.movieName,
                                        posterUrl = refreshed.movie.posterUrl,
                                        fileSize = freshMedia?.fileSize ?: "",
                                        onProgress = { }
                                    )
                                    Toast.makeText(context, "Download restarted with fresh link", Toast.LENGTH_SHORT).show()
                                } else {
                                    _uiState.update { it.copy(isRefreshingLinks = false, refreshError = "No fresh download link available") }
                                    downloadRepository.updateDownloadStatusWithMessage(downloadId, DownloadStatus.FAILED, "No fresh download link available")
                                    Toast.makeText(context, "No fresh download link found", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                _uiState.update { it.copy(isRefreshingLinks = false, refreshError = "Failed to reload movie") }
                                downloadRepository.updateDownloadStatusWithMessage(downloadId, DownloadStatus.FAILED, "Failed to reload movie details")
                                Toast.makeText(context, "Could not refresh links", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                        is com.kstream.core.model.RefreshMediaResult.Failed -> {
                            _uiState.update { it.copy(isRefreshingLinks = false, refreshError = result.error) }
                            downloadRepository.updateDownloadStatusWithMessage(downloadId, DownloadStatus.FAILED, result.error)
                            Toast.makeText(context, "Could not refresh links. Please try again.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        is com.kstream.core.model.RefreshMediaResult.Queued,
                        is com.kstream.core.model.RefreshMediaResult.Processing -> {
                            // still in progress, keep polling
                        }
                    }
                }
                // Timed out
                _uiState.update { it.copy(isRefreshingLinks = false, refreshError = "Refresh timed out") }
                downloadRepository.updateDownloadStatusWithMessage(downloadId, DownloadStatus.FAILED, "Link refresh timed out")
                Toast.makeText(context, "Refresh timed out. Please try again.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshingLinks = false, refreshError = e.toUserMessage()) }
                downloadRepository.updateDownloadStatusWithMessage(downloadId, DownloadStatus.FAILED, e.toUserMessage())
                Toast.makeText(context, "Could not refresh links. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}