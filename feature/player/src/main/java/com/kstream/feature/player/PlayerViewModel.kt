package com.kstream.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.GetMovieDetailsUseCase
import com.kstream.core.domain.GetWatchProgressUseCase
import com.kstream.core.domain.SaveWatchProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val availableQualities: List<String> = emptyList(),
    val currentQuality: String = ""
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase,
    private val getWatchProgressUseCase: GetWatchProgressUseCase,
    private val saveWatchProgressUseCase: SaveWatchProgressUseCase,
    val playerManager: PlayerManager
) : ViewModel() {

    private val movieId: String = checkNotNull(savedStateHandle["movieId"])
    private val initialQuality: String = checkNotNull(savedStateHandle["quality"])

    private val _uiState = MutableStateFlow(PlayerUiState(currentQuality = initialQuality))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var movieWithMedia: com.kstream.core.model.MovieWithMedia? = null
    private var progressSyncJob: Job? = null

    init {
        loadMediaAndPlay()
    }

    private fun loadMediaAndPlay() {
        viewModelScope.launch {
            movieWithMedia = getMovieDetailsUseCase(movieId)
            val mediaList = movieWithMedia?.media ?: emptyList()
            _uiState.update { it.copy(
                availableQualities = mediaList.map { m -> m.quality },
                currentQuality = initialQuality
            ) }
            
            val media = mediaList.find { it.quality == initialQuality }
            val fallbackUrls = listOfNotNull(
                media?.watchUrl1,
                media?.watchUrl2,
                media?.downloadUrl1,
                media?.downloadUrl2
            )

            if (fallbackUrls.isNotEmpty()) {
                val startPosition = getWatchProgressUseCase(movieId)
                playerManager.play(fallbackUrls, startPosition)
                startProgressSync()
            }
        }
    }

    fun switchQuality(newQuality: String) {
        if (newQuality == _uiState.value.currentQuality) return
        
        val media = movieWithMedia?.media?.find { it.quality == newQuality } ?: return
        val fallbackUrls = listOfNotNull(
            media.watchUrl1,
            media.watchUrl2,
            media.downloadUrl1,
            media.downloadUrl2
        )
        
        if (fallbackUrls.isNotEmpty()) {
            val currentPos = playerManager.getPlayer().currentPosition
            playerManager.play(fallbackUrls, currentPos)
            _uiState.update { it.copy(currentQuality = newQuality) }
        }
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Sync every 5 seconds
                val player = playerManager.getPlayer()
                if (player.isPlaying) {
                    saveWatchProgressUseCase(movieId, player.currentPosition, player.duration, _uiState.value.currentQuality)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val player = playerManager.getPlayer()
        viewModelScope.launch {
            saveWatchProgressUseCase(movieId, player.currentPosition, player.duration, _uiState.value.currentQuality)
            playerManager.release()
        }
    }
}
