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
    val isLoading: Boolean = true,
    val availableQualities: List<String> = emptyList(),
    val currentQuality: String = "",
    val isPlayingLocal: Boolean = false,
    val isOffline: Boolean = false,
    val isBuffering: Boolean = false,
    val isRefreshingLinks: Boolean = false,
    val showRefreshOverlay: Boolean = false,
    val funnyMessage: String? = null,
    val refreshError: String? = null,
    val loadError: String? = null,
    val localFileMissing: Boolean = false
) {
    companion object {
        val FUNNY_MESSAGES = listOf(
            "Link expired! Fetching fresh URLs... 🔗",
            "Grab some popcorn! This'll take a moment 🍿",
            "Our movie elves are finding the best link... 🧝",
            "Almost there! Warming up the stream... 🎬",
            "Good things come to those who wait... ⏳",
            "Mixing the perfect streaming cocktail... 🍹",
            "Polishing the pixels for you... ✨",
            "Just a moment, negotiating with the servers... 🤝",
        )
    }
}

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

    private var movieSlug: String? = null
    private var refreshPollingJob: Job? = null
    private var funnyMessageJob: Job? = null

    fun retryRefresh() {
        _uiState.update { it.copy(refreshError = null) }
        refreshAndRetry()
    }

    private fun refreshAndRetry() {
        if (_uiState.value.isRefreshingLinks) return

        val slug = movieSlug ?: movieWithMedia?.movie?.slug
        if (slug == null) {
            _uiState.update { it.copy(refreshError = "Could not refresh links — movie info missing.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingLinks = true, refreshError = null, showRefreshOverlay = false, funnyMessage = null) }

            // Start the delayed funny message overlay (after 2 seconds)
            startFunnyMessages()

            try {
                val result = refreshMovieMediaUseCase(slug)
                handleRefreshResult(result, slug)
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Media refresh failed", e)
                stopFunnyMessages()
                _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = e.toUserMessage()) }
            }
        }
    }

    private fun handleRefreshResult(result: com.kstream.core.model.RefreshMediaResult, slug: String) {
        when (result) {
            is com.kstream.core.model.RefreshMediaResult.Queued,
            is com.kstream.core.model.RefreshMediaResult.Processing -> {
                android.util.Log.d("PlayerViewModel", "Refresh ${if (result is com.kstream.core.model.RefreshMediaResult.Queued) "queued" else "processing"}, starting poll...")
                startPolling(slug)
            }
            is com.kstream.core.model.RefreshMediaResult.Done -> {
                android.util.Log.d("PlayerViewModel", "Refresh done, fetching fresh URLs...")
                stopFunnyMessages()
                fetchFreshUrlsAndResume()
            }
            is com.kstream.core.model.RefreshMediaResult.Failed -> {
                android.util.Log.e("PlayerViewModel", "Refresh failed: ${result.error}")
                stopFunnyMessages()
                _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = result.error) }
            }
        }
    }

    private fun startPolling(slug: String) {
        refreshPollingJob?.cancel()
        refreshPollingJob = viewModelScope.launch {
            val maxAttempts = 60 // 60 × 5s = 5 minutes max (GHA cold-start needs time)
            for (attempt in 1..maxAttempts) {
                delay(5000)
                try {
                    val result = refreshMovieMediaUseCase(slug)
                    when (result) {
                        is com.kstream.core.model.RefreshMediaResult.Done -> {
                            stopFunnyMessages()
                            fetchFreshUrlsAndResume()
                            return@launch
                        }
                        is com.kstream.core.model.RefreshMediaResult.Failed -> {
                            stopFunnyMessages()
                            _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = result.error) }
                            return@launch
                        }
                        else -> {
                            // Still queued/processing, continue polling
                            android.util.Log.d("PlayerViewModel", "Poll attempt $attempt: still processing...")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Poll error: ${e.message}")
                }
            }
            // Timeout after 2 minutes
            stopFunnyMessages()
            _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = "Refresh timed out. Please try again later.") }
        }
    }

    private fun fetchFreshUrlsAndResume() {
        viewModelScope.launch {
            try {
                // Wait briefly for the backend DB to propagate refreshed media URLs
                delay(2000)
                val refreshed = getMovieDetailsUseCase(movieId)
                if (refreshed != null) {
                    val oldUrls = movieWithMedia?.media
                        ?.find { it.quality == _uiState.value.currentQuality }
                        ?.let { listOfNotNull(it.watchUrl1, it.watchUrl2) }
                        ?: emptyList()

                    movieWithMedia = refreshed
                    val media = refreshed.media.find { it.quality == _uiState.value.currentQuality }
                    val freshUrls = listOfNotNull(media?.watchUrl1, media?.watchUrl2)
                        .filter { it.isNotBlank() }

                    // Only use URLs that are actually new
                    val newUrls = freshUrls.filter { it !in oldUrls }
                    val urlsToPlay = newUrls.ifEmpty { freshUrls }

                    if (urlsToPlay.isNotEmpty()) {
                        val startPosition = playerManager.getPlayer().currentPosition.coerceAtLeast(0)
                        playerManager.play(urlsToPlay, startPosition)
                        _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null) }
                        android.util.Log.d("PlayerViewModel", "Playback resumed with refreshed URLs")
                    } else {
                        _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = "No working links found. Please try again later.") }
                    }
                } else {
                    _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = "Could not load refreshed links.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = e.toUserMessage()) }
            }
        }
    }

    private fun startFunnyMessages() {
        funnyMessageJob?.cancel()
        funnyMessageJob = viewModelScope.launch {
            // Wait before showing overlay
            delay(4000)
            if (!_uiState.value.isRefreshingLinks) return@launch

            var messageIndex = 0
            _uiState.update { it.copy(showRefreshOverlay = true, funnyMessage = PlayerUiState.FUNNY_MESSAGES[0]) }

            // Cycle through messages every 5 seconds
            while (true) {
                delay(5000)
                if (!_uiState.value.isRefreshingLinks) return@launch
                messageIndex = (messageIndex + 1) % PlayerUiState.FUNNY_MESSAGES.size
                _uiState.update { it.copy(funnyMessage = PlayerUiState.FUNNY_MESSAGES[messageIndex]) }
            }
        }
    }

    private fun stopFunnyMessages() {
        funnyMessageJob?.cancel()
        funnyMessageJob = null
        refreshPollingJob?.cancel()
        refreshPollingJob = null
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
                    _uiState.update { it.copy(isLoading = false, isPlayingLocal = true, isOffline = false) }
                    startProgressSync()
                    return@launch
                }

                // From downloads page: don't fall back to streaming
                if (source == "download") {
                    _uiState.update { it.copy(localFileMissing = true, isLoading = false) }
                    return@launch
                }

                movieWithMedia = getMovieDetailsUseCase(movieId)
                movieSlug = movieWithMedia?.movie?.slug
                val mediaList = movieWithMedia?.media ?: emptyList()
                _uiState.update { it.copy(
                    availableQualities = mediaList.map { m -> m.quality },
                    currentQuality = initialQuality
                ) }
                
                val media = mediaList.find { it.quality == initialQuality }
                val fallbackUrls = listOfNotNull(
                    media?.watchUrl1,
                    media?.watchUrl2
                )

                if (fallbackUrls.isNotEmpty()) {
                    val startPosition = getWatchProgressUseCase(movieId)
                    playerManager.play(fallbackUrls, startPosition)
                    _uiState.update { it.copy(isLoading = false, isOffline = !isOnline) }
                    startProgressSync()
                } else {
                    // URLs are null/empty — trigger refresh to fetch fresh links
                    android.util.Log.w("PlayerViewModel", "No watch URLs available, triggering media refresh...")
                    _uiState.update { it.copy(isLoading = false) }
                    refreshAndRetry()
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error loading media", e)
                _uiState.update { it.copy(isLoading = false, loadError = e.toUserMessage()) }
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
                    media?.watchUrl1, media?.watchUrl2
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
                        media.watchUrl2
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