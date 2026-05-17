package com.kstream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.os.Environment
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.kstream.core.common.toUserMessage
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.domain.repository.LikedMovieRepository
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.domain.repository.WatchProgressRepository
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.domain.repository.RecommendationRepository
import com.kstream.core.domain.TriggerScanUseCase
import com.kstream.core.domain.GetScanStatusUseCase
import com.kstream.core.domain.SyncMoviesUseCase
import com.kstream.core.model.ScanStatus
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ScanState {
    IDLE, CHECKING, TRIGGERING, RUNNING, COMPLETED, FAILED, TOO_RECENT, ALREADY_RUNNING
}

data class WatchHistoryItem(
    val movieId: String,
    val movieName: String,
    val posterUrl: String,
    val completionPercent: Float,
    val lastWatched: Long
)

data class SettingsUiState(
    val username: String = "",
    val scanState: ScanState = ScanState.CHECKING,
    val scanMessage: String = "",
    val cacheCleared: Boolean = false,
    val watchHistory: List<WatchHistoryItem> = emptyList(),
    val isLoadingHistory: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val likedMovieRepository: LikedMovieRepository,
    private val movieRepository: MovieRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadRepository: DownloadRepository,
    private val recommendationRepository: RecommendationRepository,
    private val triggerScanUseCase: TriggerScanUseCase,
    private val getScanStatusUseCase: GetScanStatusUseCase,
    private val syncMoviesUseCase: SyncMoviesUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var triggeredAt: String? = null

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
        private const val POLL_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
        private const val RESULT_DISPLAY_MS = 3000L
    }

    init {
        userDataRepository.username
            .catch { emit("") }
            .onEach { username -> _uiState.update { it.copy(username = username) } }
            .launchIn(viewModelScope)

        checkScanStatus()
    }

    fun onUsernameChange(newName: String) {
        _uiState.update { it.copy(username = newName) }
    }

    fun saveUsername() {
        viewModelScope.launch {
            try { userDataRepository.setUsername(_uiState.value.username) }
            catch (_: Exception) { }
        }
    }

    fun clearLikedMovies() {
        viewModelScope.launch {
            try { likedMovieRepository.clearAll() }
            catch (_: Exception) { }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            try {
                val imageLoader = Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                movieRepository.clearCache()
                _uiState.update { it.copy(cacheCleared = true) }
                delay(2000L)
                _uiState.update { it.copy(cacheCleared = false) }
            } catch (_: Exception) { }
        }
    }

    private var watchHistoryJob: Job? = null

    fun loadWatchHistory() {
        watchHistoryJob?.cancel()
        watchHistoryJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true) }
            try {
                val progress = watchProgressRepository.getAllProgress()
                val allMovies = movieRepository.searchMovies("*")
                val movieMap = allMovies.associateBy { it.id }

                progress.collect { progressList ->
                    val items = progressList
                        .sortedByDescending { it.lastUpdated }
                        .mapNotNull { p ->
                            val movie = movieMap[p.movieId]
                            if (movie != null) {
                                WatchHistoryItem(
                                    movieId = p.movieId,
                                    movieName = movie.movieName,
                                    posterUrl = movie.posterUrl,
                                    completionPercent = p.completionPercent,
                                    lastWatched = p.lastUpdated
                                )
                            } else null
                        }
                    _uiState.update { it.copy(watchHistory = items, isLoadingHistory = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingHistory = false) }
            }
        }
    }

    fun deleteWatchHistory(movieIds: Set<String>) {
        viewModelScope.launch {
            try {
                movieIds.forEach { id ->
                    watchProgressRepository.deleteProgress(id)
                }
                _uiState.update { state ->
                    state.copy(watchHistory = state.watchHistory.filter { it.movieId !in movieIds })
                }
            } catch (_: Exception) { }
        }
    }

    private fun checkScanStatus() {
        viewModelScope.launch {
            try {
                val status = getScanStatusUseCase()
                when (status) {
                    ScanStatus.RUNNING -> {
                        _uiState.update { it.copy(scanState = ScanState.RUNNING, scanMessage = "Scan in progress") }
                        startPolling()
                    }
                    else -> {
                        _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
            }
        }
    }

    fun triggerScan() {
        if (_uiState.value.scanState != ScanState.IDLE) return

        viewModelScope.launch {
            _uiState.update { it.copy(scanState = ScanState.TRIGGERING, scanMessage = "Initiating scan...") }

            try {
                val result = triggerScanUseCase()

                when (result.status) {
                    "started" -> {
                        triggeredAt = result.triggeredAt
                        _uiState.update { it.copy(scanState = ScanState.RUNNING, scanMessage = "Scanning for new movies...") }
                        startPolling()
                    }
                    "already_running" -> {
                        _uiState.update { it.copy(scanState = ScanState.ALREADY_RUNNING, scanMessage = "Scan already in progress") }
                        startPolling()
                    }
                    "too_recent" -> {
                        _uiState.update { it.copy(scanState = ScanState.TOO_RECENT, scanMessage = result.message ?: "Scanned recently") }
                        // Auto-reset to IDLE after display period
                        delay(RESULT_DISPLAY_MS)
                        _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
                    }
                    else -> {
                        _uiState.update { it.copy(scanState = ScanState.FAILED, scanMessage = result.message ?: "Failed to trigger scan") }
                        delay(RESULT_DISPLAY_MS)
                        _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(scanState = ScanState.FAILED, scanMessage = e.toUserMessage()) }
                delay(RESULT_DISPLAY_MS)
                _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < POLL_TIMEOUT_MS) {
                delay(POLL_INTERVAL_MS)

                try {
                    val status = getScanStatusUseCase()
                    when (status) {
                        ScanStatus.RUNNING -> {
                            _uiState.update { it.copy(scanState = ScanState.RUNNING, scanMessage = "Scanning for new movies...") }
                        }
                        ScanStatus.COMPLETED -> {
                            _uiState.update { it.copy(scanState = ScanState.COMPLETED, scanMessage = "Scan complete ✓") }
                            // Sync local cache with new movies
                            try { syncMoviesUseCase() } catch (_: Exception) {}
                            delay(RESULT_DISPLAY_MS)
                            _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
                            return@launch
                        }
                        ScanStatus.FAILED -> {
                            _uiState.update { it.copy(scanState = ScanState.FAILED, scanMessage = "Scan failed") }
                            delay(RESULT_DISPLAY_MS)
                            _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
                            return@launch
                        }
                        ScanStatus.IDLE -> {
                            // Scraper hasn't started yet (GitHub Actions queuing)
                            _uiState.update { it.copy(scanMessage = "Waiting for scan to start...") }
                        }
                    }
                } catch (_: Exception) {
                    // Continue polling on transient errors
                }
            }

            // Timeout reached
            _uiState.update { it.copy(scanState = ScanState.FAILED, scanMessage = "Scan timed out") }
            delay(RESULT_DISPLAY_MS)
            _uiState.update { it.copy(scanState = ScanState.IDLE, scanMessage = "") }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun resetAllAndRestart() {
        viewModelScope.launch {
            try {
                likedMovieRepository.clearAll()
                watchProgressRepository.deleteAllProgress()
                downloadRepository.deleteAllDownloads()
                movieRepository.clearCache()
                recommendationRepository.clearAll()

                val imageLoader = Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()

                userDataRepository.clearAllData()

                try {
                    val moviesDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "KStream"
                    )
                    if (moviesDir.exists()) moviesDir.deleteRecursively()
                } catch (_: Exception) {}

                try { context.cacheDir.deleteRecursively() } catch (_: Exception) {}

                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                context.startActivity(intent)
                Runtime.getRuntime().exit(0)
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
