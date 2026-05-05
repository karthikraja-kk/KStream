package com.kstream.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.GetMovieDetailsUseCase
import com.kstream.core.model.MovieWithMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val isLoading: Boolean = false,
    val movieWithMedia: MovieWithMedia? = null,
    val error: String? = null
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: com.kstream.core.domain.GetMovieDetailsUseCase,
    private val downloadMovieUseCase: com.kstream.core.domain.DownloadMovieUseCase
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
        
        downloadMovieUseCase(movieId, url, movieWithMedia.movie.movieName)
    }
}
