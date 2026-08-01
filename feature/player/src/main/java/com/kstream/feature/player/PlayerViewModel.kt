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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val movieTitle: String = "",
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
    val localFileMissing: Boolean = false,
    /** Always "EXO" — which engine is currently driving the surface. */
    val activeEngine: String = "EXO",
    /** Set by the coordinator when both engines failed or hard-cap fired. */
    val sourceUnavailable: Boolean = false
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
    private val userDataRepository: com.kstream.core.domain.repository.UserDataRepository,
    val coordinator: PlayerEngineCoordinator
) : ViewModel() {

    // Exposed for backwards-compatible call sites (e.g. ExoPlayer-only code paths
    // like `getPlayer()` on the PlayerView). New code should use `coordinator`.
    val playerManager: PlayerManager get() = coordinator.exoManager

    private val movieId: String = android.net.Uri.decode(savedStateHandle.get<String>("movieId") ?: "")
    private val initialQuality: String = android.net.Uri.decode(savedStateHandle.get<String>("quality") ?: "")
    private val source: String = savedStateHandle.get<String>("source") ?: "stream"

    private val _uiState = MutableStateFlow(PlayerUiState(currentQuality = initialQuality))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var movieWithMedia: com.kstream.core.model.MovieWithMedia? = null
    private var progressSyncJob: Job? = null

    // Most recent user setting; observed continuously from datastore.
    @Volatile private var userEngine: VideoEngine = VideoEngine.DEFAULT

    init {
        if (movieId.isNotEmpty() && initialQuality.isNotEmpty()) {
            observeVideoEngineSetting()
            observeCoordinatorEvents()
            loadMediaAndPlay()
            observeNetworkState()
        } else {
            _uiState.update { it.copy(isLoading = false, loadError = "Unable to play — missing movie information.") }
        }
    }

    private fun observeVideoEngineSetting() {
        userDataRepository.videoEngine
            .catch { emit("AUTO") }
            .onEach { key -> userEngine = VideoEngine.fromKey(key) }
            .launchIn(viewModelScope)
    }

    private fun observeCoordinatorEvents() {
        coordinator.activeEngine
            .onEach { engine -> _uiState.update { it.copy(activeEngine = engine.key) } }
            .launchIn(viewModelScope)
        coordinator.events
            .onEach { event ->
                when (event) {
                    is PlayerEngineCoordinator.Event.SourceUnavailable -> {
                        android.util.Log.w("PlayerViewModel", "Source unavailable: ${event.reason}")
                        when (event.reason) {
                            // Just-now both-engines-failed OR hard-cap: the URLs we have are
                            // probably stale. Try the refresh chain (will rotate fresh links
                            // and replay). If THAT still ends up here it lands as RECENT_FAILURE.
                            PlayerEngineCoordinator.LockoutReason.BOTH_FAILED,
                            PlayerEngineCoordinator.LockoutReason.HARD_CAP -> {
                                android.util.Log.i("PlayerViewModel",
                                    "Triggering refreshAndRetry (movieSlug=$movieSlug, " +
                                    "movieWithMediaSlug=${movieWithMedia?.movie?.slug}, " +
                                    "isRefreshingLinks=${_uiState.value.isRefreshingLinks})")
                                refreshAndRetry()
                            }
                            // A previous attempt failed within the 5-minute lockout window.
                            // Don't auto-refresh — show "Source unavailable" + Try Again.
                            PlayerEngineCoordinator.LockoutReason.RECENT_FAILURE -> {
                                stopFunnyMessages()
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isRefreshingLinks = false,
                                        showRefreshOverlay = false,
                                        funnyMessage = null,
                                        sourceUnavailable = true,
                                        loadError = "Source unavailable. Try again in a few minutes."
                                    )
                                }
                            }
                        }
                    }
                    is PlayerEngineCoordinator.Event.LinkProbablyStale -> {
                        android.util.Log.i("PlayerViewModel",
                            "LinkProbablyStale (engine=${event.currentEngine}) — refreshing before swap")
                        // Pause coordinator's hard-cap timer so it doesn't fire while the refresh
                        // chain is polling Supabase (which has its own 2-min timeout).
                        coordinator.pauseTimers()
                        refreshAndRetry()
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private var wasOffline = false

    private fun observeNetworkState() {
        networkMonitor.isOnline
            .onEach { isOnline ->
                if (isOnline && wasOffline && !_uiState.value.isPlayingLocal) {
                    _uiState.update { it.copy(isOffline = false) }
                    wasOffline = false
                    try {
                        // Resume on whichever engine is currently active.
                        coordinator.controller().play()
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
        // Legacy: ExoPlayer's allUrlsFailed flow is now routed via PlayerEngineCoordinator
        // which decides whether to swap engines, then emits SourceUnavailable for the VM
        // to refresh. Kept as a no-op stub so external test code that calls it still compiles.
    }

    private var movieSlug: String? = null
    private var refreshPollingJob: Job? = null
    private var funnyMessageJob: Job? = null

    fun retryRefresh() {
        _uiState.update { it.copy(refreshError = null, sourceUnavailable = false, loadError = null) }
        // If the lockout was the reason for sourceUnavailable, ask the coordinator to clear it
        // and re-attempt with the URLs it already has.
        val pos = coordinator.controller().currentPositionMs
        coordinator.retryAfterLockout(pos, userEngine)
        refreshAndRetry()
    }

    private fun refreshAndRetry() {
        if (_uiState.value.isRefreshingLinks) {
            android.util.Log.w("PlayerViewModel", "refreshAndRetry: already refreshing — skip")
            return
        }

        val slug = movieSlug ?: movieWithMedia?.movie?.slug
        if (slug == null) {
            android.util.Log.w("PlayerViewModel", "refreshAndRetry: slug missing — abort")
            _uiState.update { it.copy(refreshError = "Could not refresh links — movie info missing.") }
            return
        }

        android.util.Log.i("PlayerViewModel", "refreshAndRetry: starting refresh for slug=$slug")
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
                        // Fall back to saved watch progress if the coordinator has no
                        // current position yet (e.g. proactive expiry-detect path —
                        // we never even started a play attempt, so currentPositionMs is 0).
                        val livePos = coordinator.controller().currentPositionMs.coerceAtLeast(0)
                        val startPosition = if (livePos > 0L) livePos
                            else getWatchProgressUseCase(movieId).coerceAtLeast(0L)
                        // Resolve the freshly-refreshed download.php URL once into a
                        // ~48h-valid direct R2 URL and cache it, then play that.
                        val urls = resolveAndCacheUrl(movieId, _uiState.value.currentQuality, urlsToPlay)
                        if (urls.isNotEmpty()) {
                            coordinator.markUrlsFresh()
                            coordinator.play(movieId, urls, startPosition, forceStartOver = false, userSetting = userEngine)
                            _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, sourceUnavailable = false, loadError = null) }
                            android.util.Log.d("PlayerViewModel", "Playback resumed with refreshed URLs (pos=$startPosition)")
                        } else {
                            _uiState.update { it.copy(isRefreshingLinks = false, showRefreshOverlay = false, funnyMessage = null, refreshError = "No working links found. Please try again later.") }
                        }
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
                    coordinator.controller().play()
                }
            } catch (_: Exception) { }
        }
    }

    // ------------------------------------------------------------------
    // Direct R2 URL cache ("single refresh = ~48h alive").
    //
    // The CDN's download.php gateway tokens die in ~3 minutes, but the Cloudflare
    // R2 presigned URL they 302 to is valid for ~48h. We resolve once and cache it
    // so seeks/pause/resume reuse the long-lived URL instead of re-hitting the
    // short-lived gateway.
    // ------------------------------------------------------------------

    private suspend fun cachedPlayableUrl(movieId: String, quality: String): String? {
        return try {
            val cached = userDataRepository.getResolvedMediaUrl(movieId, quality)
            if (cached != null && MediaLinkUtils.isR2UrlValid(cached.url, cached.expiresAt)) {
                cached.url
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Turn a short-lived `download.php` URL into a direct, ~48h-valid R2 URL by
     * resolving the redirect once and persisting it. Falls back to the original
     * URL if resolution fails so the existing error/refresh path still applies.
     */
    private suspend fun resolveAndCacheUrl(
        movieId: String,
        quality: String,
        candidates: List<String>
    ): List<String> {
        val first = candidates.firstOrNull { it.isNotBlank() } ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val resolved = RedirectResolver().resolveOnce(first)
                if (resolved != first && resolved.isNotBlank()) {
                    val expiresAt = MediaLinkUtils.r2ExpiryMs(resolved)
                        ?: (System.currentTimeMillis() + R2_FALLBACK_TTL_MS)
                    userDataRepository.setResolvedMediaUrl(movieId, quality, resolved, expiresAt)
                    android.util.Log.i("PlayerViewModel", "Resolved + cached direct URL for $movieId/$quality")
                    listOf(resolved)
                } else {
                    candidates
                }
            } catch (e: Exception) {
                android.util.Log.w("PlayerViewModel", "resolveAndCacheUrl failed: ${e.message}")
                candidates
            }
        }
    }

    private fun loadMediaAndPlay() {        viewModelScope.launch {
            try {
                val isOnline = networkMonitor.isOnline.first()
                _uiState.update { it.copy(isOffline = !isOnline) }

                val localPath = customDownloadManager.getLocalPath(movieId, initialQuality)
                if (localPath != null && customDownloadManager.checkFileExists(localPath)) {
                    val startPosition = getWatchProgressUseCase(movieId)
                    coordinator.playLocal(localPath, startPosition, forceStartOver = false)
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
                    movieTitle = movieWithMedia?.movie?.movieName ?: "",
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
                    // Prefer a still-valid cached direct R2 URL (48h lifetime) —
                    // no refresh, no re-resolve, instant play.
                    val cachedUrl = cachedPlayableUrl(movieId, initialQuality)
                    when {
                        cachedUrl != null -> {
                            coordinator.play(movieId, listOf(cachedUrl), startPosition, forceStartOver = false, userSetting = userEngine)
                            _uiState.update { it.copy(isLoading = false, isOffline = !isOnline) }
                            startProgressSync()
                        }
                        // Proactive expiry check: if the saved URL has an `exp` param that
                        // has already passed, refresh BEFORE trying to play. Saves the user
                        // from watching an inevitable Exo-then-VLC failure on a stale link.
                        MediaLinkUtils.isAnyExpired(fallbackUrls) -> {
                            android.util.Log.i("PlayerViewModel",
                                "Saved URLs are expired — refreshing before play (movie=$movieId)")
                            _uiState.update { it.copy(isLoading = false, isOffline = !isOnline) }
                            refreshAndRetry()
                            return@launch
                        }
                        else -> {
                            val urls = resolveAndCacheUrl(movieId, initialQuality, fallbackUrls)
                            if (urls.isNotEmpty()) {
                                coordinator.play(movieId, urls, startPosition, forceStartOver = false, userSetting = userEngine)
                                _uiState.update { it.copy(isLoading = false, isOffline = !isOnline) }
                                startProgressSync()
                            } else {
                                _uiState.update { it.copy(isLoading = false) }
                                refreshAndRetry()
                            }
                        }
                    }
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
        _uiState.update { it.copy(localFileMissing = false, loadError = null) }
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
                    val cachedUrl = cachedPlayableUrl(movieId, initialQuality)
                    when {
                        cachedUrl != null -> {
                            coordinator.play(movieId, listOf(cachedUrl), startPosition, forceStartOver = false, userSetting = userEngine)
                            startProgressSync()
                        }
                        MediaLinkUtils.isAnyExpired(fallbackUrls) -> {
                            android.util.Log.i("PlayerViewModel",
                                "watchOnline: saved URLs expired — refreshing first")
                            refreshAndRetry()
                            return@launch
                        }
                        else -> {
                            val urls = resolveAndCacheUrl(movieId, initialQuality, fallbackUrls)
                            if (urls.isNotEmpty()) {
                                coordinator.play(movieId, urls, startPosition, forceStartOver = false, userSetting = userEngine)
                                startProgressSync()
                            } else {
                                _uiState.update { it.copy(loadError = "No streaming links available for this movie.") }
                            }
                        }
                    }
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
                    val existing = watchProgressRepository.getProgress(movieId)
                    val duration = existing?.duration ?: 1L
                    val quality = existing?.quality ?: _uiState.value.currentQuality
                    watchProgressRepository.saveProgress(movieId, 0L, duration, quality)
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
                val currentPos = coordinator.controller().currentPositionMs

                if (localPath != null && customDownloadManager.checkFileExists(localPath)) {
                    coordinator.playLocal(localPath, currentPos, forceStartOver = false)
                    _uiState.update { it.copy(currentQuality = newQuality, isPlayingLocal = true, isOffline = false) }
                } else {
                    val media = movieWithMedia?.media?.find { it.quality == newQuality } ?: return@launch
                    val fallbackUrls = listOfNotNull(
                        media.watchUrl1,
                        media.watchUrl2
                    )

                    if (fallbackUrls.isNotEmpty()) {
                        val cachedUrl = cachedPlayableUrl(movieId, newQuality)
                        val url = cachedUrl ?: resolveAndCacheUrl(movieId, newQuality, fallbackUrls).firstOrNull()
                        if (url != null) {
                            coordinator.switchQuality(url, currentPos)
                            _uiState.update { it.copy(currentQuality = newQuality, isPlayingLocal = false) }
                        }
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
                    val c = coordinator.controller()
                    val pos = c.currentPositionMs
                    val dur = c.durationMs
                    if (pos > 0 && dur > 0) {
                        saveWatchProgressUseCase(movieId, pos, dur, _uiState.value.currentQuality)
                    }
                } catch (e: Exception) {
                    // Ignore transient player errors during sync
                }
            }
        }
    }

    /**
     * Capture and persist the current playback position synchronously. Called
     * from `PlayerTvFragment.onStop()` so that the user's progress is written
     * BEFORE the previous destination's `onResume()` re-queries the watch
     * progress table — otherwise the Resume button on the detail page only
     * appears on the second visit because `onCleared()` (which also saves)
     * runs after the destination is already visible.
     */
    fun saveCurrentProgress() {
        if (movieId.isEmpty()) return
        try {
            val c = coordinator.controller()
            val pos = c.currentPositionMs
            val dur = c.durationMs
            if (pos > 0 && dur > 0) {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(1000L) {
                        saveWatchProgressUseCase(movieId, pos, dur, _uiState.value.currentQuality)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "saveCurrentProgress failed", e)
        }
    }

    override fun onCleared() {
        // Persist the final playback position BEFORE releasing the engines so
        // the detail page's "Resume" button is accurate when the user comes
        // back. viewModelScope is already cancelled at this point so we can't
        // launch a normal coroutine; runBlocking with a short timeout keeps
        // the main thread blocked just long enough for the single Room
        // upsert to complete.
        try {
            val c = coordinator.controller()
            val pos = c.currentPositionMs
            val dur = c.durationMs
            if (movieId.isNotEmpty() && pos > 0 && dur > 0) {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(1000L) {
                        saveWatchProgressUseCase(movieId, pos, dur, _uiState.value.currentQuality)
                    }
                }
                android.util.Log.i("PlayerViewModel", "onCleared: saved progress pos=$pos dur=$dur")
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "onCleared: progress save failed", e)
        }
        super.onCleared()
        try {
            coordinator.release()
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error in onCleared", e)
        }
    }

    private companion object {
        /** Fallback lifetime for a resolved URL when the CDN omits X-Amz-Expires. */
        const val R2_FALLBACK_TTL_MS = 48 * 60 * 60 * 1000L
    }
}
