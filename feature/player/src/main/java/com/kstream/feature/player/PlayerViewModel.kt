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

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase,
    private val getWatchProgressUseCase: GetWatchProgressUseCase,
    private val saveWatchProgressUseCase: SaveWatchProgressUseCase,
    val playerManager: PlayerManager
) : ViewModel() {

    private val movieId: String = checkNotNull(savedStateHandle["movieId"])
    private val quality: String = checkNotNull(savedStateHandle["quality"])

    private var progressSyncJob: Job? = null

    init {
        loadMediaAndPlay()
    }

    private fun loadMediaAndPlay() {
        viewModelScope.launch {
            val movieWithMedia = getMovieDetailsUseCase(movieId)
            val media = movieWithMedia?.media?.find { it.quality == quality }
            val url = media?.watchUrl1 ?: media?.watchUrl2 ?: media?.downloadUrl1
            
            if (url != null) {
                val startPosition = getWatchProgressUseCase(movieId)
                playerManager.play(url, startPosition)
                startProgressSync()
            }
        }
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Sync every 5 seconds
                val player = playerManager.getPlayer()
                if (player.isPlaying) {
                    saveWatchProgressUseCase(movieId, player.currentPosition, player.duration)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val player = playerManager.getPlayer()
        viewModelScope.launch {
            saveWatchProgressUseCase(movieId, player.currentPosition, player.duration)
            playerManager.release()
        }
    }
}
