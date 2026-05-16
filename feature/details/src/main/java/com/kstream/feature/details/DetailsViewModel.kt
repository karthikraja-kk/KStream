package com.kstream.feature.details

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.domain.GetMovieDetailsUseCase
import com.kstream.core.domain.repository.WatchProgressRepository
import com.kstream.core.domain.repository.DownloadRepository
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
    val hasWatchProgress: Boolean = false
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase,
    private val watchProgressRepository: WatchProgressRepository,
    private val customDownloadManager: CustomDownloadManager,
    private val downloadRepository: DownloadRepository,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val movieId: String = android.net.Uri.decode(checkNotNull(savedStateHandle["movieId"]))

    private val _uiState = MutableStateFlow(DetailsUiState(isLoading = true))
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        fetchMovieDetails()
        observeDownloads()
        observeNetworkState()
        observeWatchProgress()
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
            val progress = watchProgressRepository.getProgress(movieId)
            _uiState.update { 
                it.copy(hasWatchProgress = progress != null && progress.completionPercent < 95f) 
            }
        }
    }

    private fun observeDownloads() {
        combine(
            downloadRepository.getDownloads(),
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

    private fun fetchMovieDetails() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
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
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
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
            watchProgressRepository.deleteProgress(movieId)
            _uiState.update { it.copy(hasWatchProgress = false) }
        }
    }

    private fun performDownload() {
        val movieWithMedia = _uiState.value.movieWithMedia ?: return
        val quality = _uiState.value.selectedQuality ?: return
        val media = movieWithMedia.media.find { it.quality == quality } ?: return
        val url = media.downloadUrl1 ?: media.downloadUrl2 ?: return

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
                    Toast.makeText(context, "Download failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}