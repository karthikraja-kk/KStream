package com.kstream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.util.Log
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
import com.kstream.feature.downloads.CustomDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
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
    val successMessage: String? = null,
    val isCarouselEnabled: Boolean = true,
    val isLiteMode: Boolean = false
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
    private val customDownloadManager: CustomDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var cooldownTickerJob: Job? = null
    private var lastObservedScanStatus: ScanStatus? = null
    private val isResetting = AtomicBoolean(false)

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
        private const val COOLDOWN_MS = 15 * 60 * 1000L
    }

    init {
        userDataRepository.username
            .catch { emit("") }
            .onEach { username -> _uiState.update { it.copy(username = username) } }
            .launchIn(viewModelScope)

        userDataRepository.isCarouselEnabled
            .catch { emit(true) }
            .onEach { enabled -> _uiState.update { it.copy(isCarouselEnabled = enabled) } }
            .launchIn(viewModelScope)

        userDataRepository.isLiteMode
            .catch { emit(false) }
            .onEach { enabled -> _uiState.update { it.copy(isLiteMode = enabled) } }
            .launchIn(viewModelScope)

        startLiveScanMonitor()
        startWatchHistoryMonitor()
    }

    fun onUsernameChange(newName: String) {
        _uiState.update { it.copy(username = newName) }
    }

    fun toggleCarousel(enabled: Boolean) {
        viewModelScope.launch {
            userDataRepository.setCarouselEnabled(enabled)
        }
    }

    fun toggleLiteMode(enabled: Boolean) {
        viewModelScope.launch {
            userDataRepository.setLiteMode(enabled)
        }
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
        // Normalize: replace space between date and time with 'T'
        var normalized = value.trim().replace(' ', 'T')
        // Truncate microseconds to milliseconds (keep only 3 fractional digits)
        normalized = normalized.replace(Regex("(\\d{2}:\\d{2}:\\d{2})\\.?(\\d{0,3})\\d*"), "$1.$2")
        // If fractional part is empty after dot, remove the dot
        normalized = normalized.replace(Regex("\\.$"), "")
        // Normalize timezone: +00 → +00:00, handle Z
        normalized = normalized.replace(Regex("([+-])(\\d{2})$"), "$1$2:00")

        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(normalized)?.time
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
        // T3: prevent overlapping resets if user double-taps or the dialog re-fires
        if (!isResetting.compareAndSet(false, true)) {
            Log.d("ResetAndRestart", "reset already in progress, ignoring")
            return
        }
        viewModelScope.launch {
            // T1: NonCancellable — ViewModel teardown (config change / nav away) must
            // NOT abort an in-flight wipe. We always reach the restart step.
            withContext(NonCancellable + Dispatchers.IO) {
                val tag = "ResetAndRestart"
                try {
                    // 1) Stop any in-flight downloads so they don't race the delete
                    try {
                        customDownloadManager.cancelAllDownloads()
                    } catch (e: Exception) {
                        Log.w(tag, "cancelAllDownloads failed", e)
                    }

                    // 2) Clear DB state
                    try { likedMovieRepository.clearAll() } catch (e: Exception) { Log.w(tag, "clear likes", e) }
                    try { watchProgressRepository.deleteAllProgress() } catch (e: Exception) { Log.w(tag, "clear progress", e) }
                    try { downloadRepository.deleteAllDownloads() } catch (e: Exception) { Log.w(tag, "clear downloads db", e) }
                    try { movieRepository.clearCache() } catch (e: Exception) { Log.w(tag, "clear movie cache", e) }
                    try { recommendationRepository.clearAll() } catch (e: Exception) { Log.w(tag, "clear recs", e) }

                    // 3) Clear image caches
                    try {
                        val imageLoader = Coil.imageLoader(context)
                        imageLoader.memoryCache?.clear()
                        imageLoader.diskCache?.clear()
                    } catch (e: Exception) { Log.w(tag, "clear coil", e) }

                    // 4) Clear user prefs
                    try { userDataRepository.clearAllData() } catch (e: Exception) { Log.w(tag, "clear prefs", e) }

                    // 5) ONE bulk MediaStore delete — wipes all entries this install owns
                    //    under Movies/KStream in a single SQL-like operation.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        try {
                            val collection = android.provider.MediaStore.Video.Media.getContentUri(
                                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                            )
                            val deleted = context.contentResolver.delete(
                                collection,
                                "${android.provider.MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
                                arrayOf("${Environment.DIRECTORY_MOVIES}/KStream%")
                            )
                            Log.d(tag, "MediaStore bulk delete removed $deleted entries")
                        } catch (e: Exception) {
                            Log.w(tag, "MediaStore bulk delete failed", e)
                        }
                    }

                    // 6) ONE whole-folder delete — catches any file not tracked by MediaStore
                    //    and handles pre-Q legacy installs.
                    try {
                        val moviesDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                            "KStream"
                        )
                        if (moviesDir.exists()) {
                            val ok = moviesDir.deleteRecursively()
                            Log.d(tag, "Folder deleteRecursively: $ok (still exists=${moviesDir.exists()})")
                        } else {
                            Log.d(tag, "KStream folder did not exist")
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Folder delete failed", e)
                    }

                    // 7) App cache
                    try { context.cacheDir.deleteRecursively() } catch (e: Exception) { Log.w(tag, "cache dir", e) }

                    // 8) Give the FS / MediaStore a moment to flush before exit
                    delay(150)
                } catch (e: Exception) {
                    Log.e(tag, "resetAllAndRestart fatal", e)
                }

                // 9) T2: schedule restart via AlarmManager (reliable across OEMs),
                //    then kill the process. AlarmManager survives our exit and
                //    the system will launch the activity ~100 ms later.
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                        val piFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context, 0xCAFE, intent, piFlags
                        )
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                        alarmManager?.set(
                            AlarmManager.ELAPSED_REALTIME,
                            SystemClock.elapsedRealtime() + 250,
                            pendingIntent
                        )
                        Log.d(tag, "Restart scheduled via AlarmManager")
                    } else {
                        Log.w(tag, "Launch intent is null — cannot schedule restart")
                    }
                } catch (e: Exception) {
                    Log.e("ResetAndRestart", "AlarmManager restart failed", e)
                }
                Runtime.getRuntime().exit(0)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        cooldownTickerJob?.cancel()
    }
}
