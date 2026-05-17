package com.kstream.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.common.toUserMessage
import com.kstream.core.domain.GetMovieDetailsUseCase
import com.kstream.core.domain.GetWatchProgressUseCase
import com.kstream.core.domain.SaveWatchProgressUseCase
import com.kstream.feature.downloads.CustomDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val availableQualities: List<String> = emptyList(),
    val currentQuality: String = "",
    val isPlayingLocal: Boolean = false,
    val isOffline: Boolean = false,
    val isBuffering: Boolean = false,
    val isRefreshingLinks: Boolean = false,
    val refreshError: String? = null,
    val loadError: String? = null,
    val localFileMissing: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetailsUseCase: GetMovieDetailsUseCase,
    private val getWatchProgressUseCase: GetWatchProgressUseCase,
    private val saveWatchProgressUseCase: SaveWatchProgressUseCase,
    private val refreshMovieMediaUseCase: com.kstream.core.domain.RefreshMovieMediaUseCase,
    private val customDownloadManager: CustomDownloadManager,
    private val networkMonitor: NetworkMonitor,
    private val watchProgressRepository: com.kstream.core.domain.repository.WatchProgressRepository,
    val playerManager: PlayerManager
) : ViewModel() {

    private val movieId: String = android.net.Uri.decode(savedStateHandle.get<String>("movieId") ?: "")
    private val initialQuality: String = android.net.Uri.decode(savedStateHandle.get<String>("quality") ?: "")
    private val source: String = savedStateHandle.get<String>("source") ?: "stream"

    private val _uiState = MutableStateFlow(PlayerUiState(currentQuality = initialQuality))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var movieWithMedia: com.kstream.core.model.MovieWithMedia? = null
    private var progressSyncJob: Job? = null

    init {
        if (movieId.isNotEmpty() && initialQuality.isNotEmpty()) {
            loadMediaAndPlay()
            observeNetworkState()
            observeUrlFailures()
        } else {
            _uiState.update { it.copy(loadError = "Unable to play — missing movie information.") }
        }
    }

    private var wasOffline = false

    private fun observeNetworkState() {
        networkMonitor.isOnline
            .onEach { isOnline ->
                if (isOnline && wasOffline && !_uiState.value.isPlayingLocal) {
                    _uiState.update { it.copy(isOffline = false) }
                    wasOffline = false
                    try {
                        val player = playerManager.getPlayer()
                        player.prepare()
                        player.play()
                    } catch (_: Exception) { }
                } else if (isOnline) {
                    _uiState.update { it.copy(isOffline = false) }
                    wasOffline = false
                } else {
                    wasOffline = true
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeUrlFailures() {
        playerManager.allUrlsFailed
            .onEach { error ->
                android.util.Log.w("PlayerViewModel", "All URLs failed (${error.errorCode}), attempting media refresh...")
                refreshAndRetry()
            }
            .launchIn(viewModelScope)
    }

    private fun refreshAndRetry() {
        if (_uiState.value.isRefreshingLinks) return // already refreshing

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingLinks = true, refreshError = null) }
            try {
                val refreshed = refreshMovieMediaUseCase(movieId)
                if (refreshed != null) {
                    movieWithMedia = refreshed
                    val media = refreshed.media.find { it.quality == _uiState.value.currentQuality }
                    val freshUrls = listOfNotNull(
                        media?.watchUrl1,
                        media?.watchUrl2,
                        media?.downloadUrl1,
                        media?.downloadUrl2
                    )
                    if (freshUrls.isNotEmpty()) {
                        val startPosition = playerManager.getPlayer().currentPosition.coerceAtLeast(0)
                        playerManager.play(freshUrls, startPosition)
                        _uiState.update { it.copy(isRefreshingLinks = false) }
                        android.util.Log.d("PlayerViewModel", "Playback resumed with refreshed URLs")
                    } else {
                        _uiState.update { it.copy(isRefreshingLinks = false, refreshError = "No working links found. Please try again later.") }
                    }
                } else {
                    _uiState.update { it.copy(isRefreshingLinks = false, refreshError = "Could not refresh links. Please try again.") }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Media refresh failed", e)
                _uiState.update { it.copy(isRefreshingLinks = false, refreshError = e.toUserMessage()) }
            }
        }
    }

    fun onBufferingStateChanged(isBuffering: Boolean) {
        _uiState.update { 
            it.copy(
                isBuffering = isBuffering,
                isOffline = wasOffline && isBuffering && !it.isPlayingLocal
            )
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            try {
                val isOnline = networkMonitor.isOnline.first()
                if (isOnline) {
                    _uiState.update { it.copy(isOffline = false) }
                    wasOffline = false
                    val player = playerManager.getPlayer()
                    player.prepare()
                    player.play()
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadMediaAndPlay() {
        viewModelScope.launch {
            try {
                val isOnline = networkMonitor.isOnline.first()
                _uiState.update { it.copy(isOffline = !isOnline) }

                val localPath = customDownloadManager.getLocalPath(movieId, initialQuality)
                if (localPath != null && customDownloadManager.checkFileExists(localPath)) {
                    val startPosition = getWatchProgressUseCase(movieId)
                    playerManager.playLocal(localPath, startPosition)
                    _uiState.update { it.copy(isPlayingLocal = true, isOffline = false) }
                    startProgressSync()
                    return@launch
                }

                // From downloads page: don't fall back to streaming
                if (source == "download") {
                    _uiState.update { it.copy(localFileMissing = true) }
                    return@launch
                }

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
                    _uiState.update { it.copy(isOffline = !isOnline) }
                    startProgressSync()
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error loading media", e)
                _uiState.update { it.copy(loadError = e.toUserMessage()) }
            }
        }
    }

    fun watchOnline() {
        _uiState.update { it.copy(localFileMissing = false) }
        viewModelScope.launch {
            try {
                val isOnline = networkMonitor.isOnline.first()
                if (!isOnline) {
                    _uiState.update { it.copy(loadError = "No internet connection. Please connect and try again.") }
                    return@launch
                }
                movieWithMedia = getMovieDetailsUseCase(movieId)
                val mediaList = movieWithMedia?.media ?: emptyList()
                _uiState.update { it.copy(
                    availableQualities = mediaList.map { m -> m.quality },
                    currentQuality = initialQuality
                ) }
                val media = mediaList.find { it.quality == initialQuality }
                val fallbackUrls = listOfNotNull(
                    media?.watchUrl1, media?.watchUrl2,
                    media?.downloadUrl1, media?.downloadUrl2
                )
                if (fallbackUrls.isNotEmpty()) {
                    val startPosition = getWatchProgressUseCase(movieId)
                    playerManager.play(fallbackUrls, startPosition)
                    startProgressSync()
                } else {
                    _uiState.update { it.copy(loadError = "No streaming links available for this movie.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loadError = e.toUserMessage()) }
            }
        }
    }

    private fun setStartOver(startOver: Boolean) {
        if (startOver) {
            viewModelScope.launch {
                try {
                    watchProgressRepository.deleteProgress(movieId)
                    loadMediaAndPlay()
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error starting over", e)
                }
            }
        }
    }

    fun switchQuality(newQuality: String) {
        if (newQuality == _uiState.value.currentQuality) return
        
        viewModelScope.launch {
            try {
                val localPath = customDownloadManager.getLocalPath(movieId, newQuality)
                val currentPos = playerManager.getPlayer().currentPosition
                
                if (localPath != null && customDownloadManager.checkFileExists(localPath)) {
                    playerManager.playLocal(localPath, currentPos)
                    _uiState.update { it.copy(currentQuality = newQuality, isPlayingLocal = true, isOffline = false) }
                } else {
                    val media = movieWithMedia?.media?.find { it.quality == newQuality } ?: return@launch
                    val fallbackUrls = listOfNotNull(
                        media.watchUrl1,
                        media.watchUrl2,
                        media.downloadUrl1,
                        media.downloadUrl2
                    )
                    
                    if (fallbackUrls.isNotEmpty()) {
                        playerManager.switchQuality(fallbackUrls.first())
                        _uiState.update { it.copy(currentQuality = newQuality, isPlayingLocal = false) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error switching quality", e)
            }
        }
    }

    fun clearWatchProgress() {
        setStartOver(true)
    }

    private fun startProgressSync() {
        progressSyncJob?.cancel()
        progressSyncJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                try {
                    val player = playerManager.getPlayer()
                    if (player.isPlaying) {
                        saveWatchProgressUseCase(movieId, player.currentPosition, player.duration, _uiState.value.currentQuality)
                    }
                } catch (e: Exception) {
                    // Ignore transient player errors during sync
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            playerManager.release()
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error in onCleared", e)
        }
    }
}