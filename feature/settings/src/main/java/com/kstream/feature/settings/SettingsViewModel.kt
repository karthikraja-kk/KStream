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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import javax.inject.Inject

enum class ScanState {
    IDLE, TRIGGERING, RUNNING, COOLDOWN, COMPLETED, FAILED
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
    val scanState: ScanState = ScanState.IDLE,
    val isScanButtonEnabled: Boolean = false,
    val scanStatusText: String = "Status: Checking",
    val lastRefreshText: String = "Last refresh: --",
    val scanDetailText: String = "Checking scan status...",
    val cacheCleared: Boolean = false,
    val watchHistory: List<WatchHistoryItem> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val successMessage: String? = null
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
    private var cooldownTickerJob: Job? = null
    private var lastObservedScanStatus: ScanStatus? = null

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
        private const val COOLDOWN_MS = 15 * 60 * 1000L
    }

    init {
        userDataRepository.username
            .catch { emit("") }
            .onEach { username -> _uiState.update { it.copy(username = username) } }
            .launchIn(viewModelScope)

        startLiveScanMonitor()
        startWatchHistoryMonitor()
    }

    fun onUsernameChange(newName: String) {
        _uiState.update { it.copy(username = newName) }
    }

    fun saveUsername() {
        viewModelScope.launch {
            try {
                userDataRepository.setUsername(_uiState.value.username)
                showSuccess("Username saved")
            } catch (_: Exception) { }
        }
    }

    fun clearLikedMovies() {
        viewModelScope.launch {
            try {
                likedMovieRepository.clearAll()
                showSuccess("Liked movies cleared")
            } catch (_: Exception) { }
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
                showSuccess("Cache cleared successfully")
                delay(2000L)
                _uiState.update { it.copy(cacheCleared = false) }
            } catch (_: Exception) { }
        }
    }

    fun deleteWatchHistory(movieIds: Set<String>) {
        viewModelScope.launch {
            try {
                movieIds.forEach { id -> watchProgressRepository.deleteProgress(id) }
                val count = movieIds.size
                showSuccess(if (count == 1) "Removed 1 item from history" else "Removed $count items from history")
            } catch (_: Exception) { }
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    private fun showSuccess(message: String) {
        _uiState.update { it.copy(successMessage = message) }
    }

    private fun startWatchHistoryMonitor() {
        _uiState.update { it.copy(isLoadingHistory = true) }
        combine(
            movieRepository.getMovies(),
            watchProgressRepository.getAllProgress()
        ) { movies, progressList ->
            val movieMap = movies.associateBy { it.id }
            progressList
                .sortedByDescending { it.lastUpdated }
                .map { p ->
                    val movie = movieMap[p.movieId]
                    WatchHistoryItem(
                        movieId = p.movieId,
                        movieName = movie?.movieName ?: "Unknown",
                        posterUrl = movie?.posterUrl ?: "",
                        completionPercent = p.completionPercent,
                        lastWatched = p.lastUpdated
                    )
                }
        }
            .catch { emit(emptyList()) }
            .onEach { items -> _uiState.update { it.copy(watchHistory = items, isLoadingHistory = false) } }
            .launchIn(viewModelScope)
    }

    fun triggerScan() {
        if (_uiState.value.scanState == ScanState.TRIGGERING || _uiState.value.scanState == ScanState.RUNNING) return
        if (!_uiState.value.isScanButtonEnabled) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scanState = ScanState.TRIGGERING,
                    isScanButtonEnabled = false,
                    scanDetailText = "Requesting scan trigger..."
                )
            }

            try {
                val result = triggerScanUseCase()

                when (result.status) {
                    "started" -> {
                        _uiState.update {
                            it.copy(scanDetailText = "Scan request accepted. Waiting for status update...")
                        }
                    }
                    "already_running" -> {
                        _uiState.update {
                            it.copy(scanDetailText = "A scan is currently running. Please wait.")
                        }
                    }
                    "too_recent" -> {
                        _uiState.update {
                            it.copy(scanDetailText = result.message ?: "Scanned recently. Cooldown active.")
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                scanState = ScanState.FAILED,
                                isScanButtonEnabled = true,
                                scanDetailText = result.message ?: "Failed to trigger scan."
                            )
                        }
                    }
                }

                refreshScanState()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        scanState = ScanState.FAILED,
                        isScanButtonEnabled = true,
                        scanDetailText = e.toUserMessage()
                    )
                }
            }
        }
    }

    private fun startLiveScanMonitor() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshScanState()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshScanState() {
        try {
            val info = getScanStatusUseCase()
            val now = System.currentTimeMillis()
            val latestCompletedMillis = parseIsoMillis(info.latestCompletedTime)
            val cooldownEndMillis = latestCompletedMillis?.plus(COOLDOWN_MS)
            val cooldownRemainingMillis = (cooldownEndMillis ?: 0L) - now
            val isCooldown = cooldownRemainingMillis > 0
            val isRunning = info.latestStatus == ScanStatus.RUNNING

            val nextState = when {
                isCooldown -> ScanState.COOLDOWN
                isRunning -> ScanState.RUNNING
                info.latestStatus == ScanStatus.COMPLETED -> ScanState.COMPLETED
                info.latestStatus == ScanStatus.FAILED -> ScanState.FAILED
                else -> ScanState.IDLE
            }

            // If we just triggered a scan but the server hasn't transitioned to RUNNING yet,
            // keep the TRIGGERING state so the button stays disabled until server catches up.
            val currentState = _uiState.value.scanState
            if (currentState == ScanState.TRIGGERING && nextState != ScanState.RUNNING && nextState != ScanState.COOLDOWN) {
                return
            }

            val statusLabel = when (nextState) {
                ScanState.RUNNING -> "Status: Running"
                ScanState.COOLDOWN -> "Status: Cooldown"
                ScanState.COMPLETED -> "Status: Completed"
                ScanState.FAILED -> "Status: Failed"
                ScanState.TRIGGERING -> "Status: Triggering"
                ScanState.IDLE -> "Status: Idle"
            }

            val detailText = when (nextState) {
                ScanState.RUNNING -> "A scan is currently running. Please wait."
                ScanState.COOLDOWN -> "Available in ${formatRemaining(cooldownRemainingMillis)}"
                ScanState.COMPLETED -> "Last scan completed successfully."
                ScanState.FAILED -> "Last scan failed. You can retry now."
                ScanState.TRIGGERING -> "Requesting scan trigger..."
                ScanState.IDLE -> "Ready to scan for latest movies."
            }

            _uiState.update {
                it.copy(
                    scanState = nextState,
                    isScanButtonEnabled = !isCooldown && !isRunning,
                    scanStatusText = statusLabel,
                    lastRefreshText = "Last refresh: ${formatIsoTimestamp(info.latestRefreshTime)}",
                    scanDetailText = detailText
                )
            }

            if (isCooldown && cooldownEndMillis != null) {
                startCooldownTicker(cooldownEndMillis)
            } else {
                cooldownTickerJob?.cancel()
            }

            if (lastObservedScanStatus == ScanStatus.RUNNING && info.latestStatus == ScanStatus.COMPLETED) {
                try {
                    syncMoviesUseCase()
                } catch (_: Exception) {}
            }
            lastObservedScanStatus = info.latestStatus
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    scanState = ScanState.FAILED,
                    isScanButtonEnabled = true,
                    scanStatusText = "Status: Unknown",
                    scanDetailText = "Unable to fetch latest scan status."
                )
            }
        }
    }

    private fun startCooldownTicker(cooldownEndMillis: Long) {
        if (cooldownTickerJob?.isActive == true) return

        cooldownTickerJob = viewModelScope.launch {
            while (isActive) {
                val remaining = cooldownEndMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    refreshScanState()
                    break
                }

                _uiState.update { state ->
                    if (state.scanState == ScanState.COOLDOWN) {
                        state.copy(scanDetailText = "Available in ${formatRemaining(remaining)}")
                    } else {
                        state
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun parseIsoMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(value)?.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun formatIsoTimestamp(value: String?): String {
        val millis = parseIsoMillis(value) ?: return "--"
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
        cooldownTickerJob?.cancel()
    }
}
