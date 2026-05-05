package com.kstream.feature.details

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
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

data class DetailsUiState(
    val isLoading: Boolean = false,
    val movieWithMedia: MovieWithMedia? = null,
    val error: String? = null
)

@OptIn(UnstableApi::class)
@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: com.kstream.core.domain.GetMovieDetailsUseCase,
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
                _uiState.update { it.copy(isLoading = false, movieWithMedia = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun downloadMovie(quality: String) {
        val movieWithMedia = _uiState.value.movieWithMedia ?: return
        val media = movieWithMedia.media.find { it.quality == quality } ?: return
        val url = media.downloadUrl1 ?: media.downloadUrl2 ?: return

        val request = DownloadRequest.Builder(movieId, Uri.parse(url))
            .setData(movieWithMedia.movie.movieName.toByteArray())
            .build()

        DownloadService.sendAddDownload(
            context,
            KStreamDownloadService::class.java,
            request,
            false
        )
    }
}
