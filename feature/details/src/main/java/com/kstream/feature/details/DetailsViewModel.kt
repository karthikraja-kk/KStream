package com.kstream.feature.details

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.GetMovieDetailsUseCase
import com.kstream.core.model.MovieWithMedia
import com.kstream.feature.downloads.KStreamDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.widget.Toast
import com.kstream.core.model.DownloadMetadata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DetailsUiState(
    val isLoading: Boolean = false,
    val movieWithMedia: MovieWithMedia? = null,
    val selectedQuality: String? = null,
    val selectedFileSize: String? = null,
    val error: String? = null
)

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: com.kstream.core.domain.GetMovieDetailsUseCase,
    private val downloadManagerWrapper: com.kstream.feature.downloads.KStreamDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val movieId: String = checkNotNull(savedStateHandle["movieId"])

    private val _uiState = MutableStateFlow(DetailsUiState(isLoading = true))
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        fetchMovieDetails()
    }

    private fun fetchMovieDetails() {
        viewModelScope.launch {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun onQualitySelected(quality: String) {
        val fileSize = _uiState.value.movieWithMedia?.media?.find { it.quality == quality }?.fileSize
        _uiState.update { it.copy(selectedQuality = quality, selectedFileSize = fileSize) }
    }

    fun downloadMovie() {
        val movieWithMedia = _uiState.value.movieWithMedia ?: return
        val quality = _uiState.value.selectedQuality ?: return
        val media = movieWithMedia.media.find { it.quality == quality } ?: return
        val url = media.downloadUrl1 ?: media.downloadUrl2 ?: return

        // Check for duplicates (same ID and quality in metadata)
        val downloadManager = downloadManagerWrapper.downloadManager
        val existingDownloads = downloadManager.downloadIndex.getDownloads().use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.download else null }.toList()
        }

        val isAlreadyDownloading = existingDownloads.any { d ->
            try {
                val metadata = Json.decodeFromString<DownloadMetadata>(d.request.data.decodeToString())
                d.request.id == movieId && metadata.quality == quality
            } catch (e: Exception) {
                false
            }
        }

        if (isAlreadyDownloading) {
            Toast.makeText(context, "This movie in $quality is already in downloads", Toast.LENGTH_SHORT).show()
            return
        }

        val metadata = DownloadMetadata(
            movieName = movieWithMedia.movie.movieName,
            posterUrl = movieWithMedia.movie.posterUrl,
            quality = quality,
            fileSize = media.fileSize
        )
        
        val metadataJson = Json.encodeToString(metadata)

        val request = DownloadRequest.Builder(movieId, Uri.parse(url))
            .setData(metadataJson.toByteArray())
            .build()

        DownloadService.sendAddDownload(
            context,
            KStreamDownloadService::class.java,
            request,
            false
        )
        Toast.makeText(context, "Download started: ${movieWithMedia.movie.movieName}", Toast.LENGTH_SHORT).show()
    }
}
