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
    val displayName: String = "Friend",
    val avatarInitials: String = "F",
    val totalMovies: Int = 0,
    val totalHours: String = "0",
    val totalDays: Int = 0,
    val hasStats: Boolean = false,
    val isHdOnly: Boolean = false,
    val cacheSizeText: String = "",
    val scanState: ScanState = ScanState.IDLE,
    val isScanButtonEnabled: Boolean = false,
    val scanStatusText: String = "Status: Checking",
    val lastRefreshText: String = "Last refresh: --",
    val lastRefreshDateText: String = "--",
    val relativeRefreshText: String = "",
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
            .onEach { username ->
                val display = if (username.isBlank()) "Friend" else username.trim()
                val initials = display.split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercase() }
                    .ifBlank { "F" }
                _uiState.update {
                    it.copy(
                        username = username,
                        displayName = display,
                        avatarInitials = initials
                    )
                }
            }
            .launchIn(viewModelScope)

        userDataRepository.isCarouselEnabled
            .catch { emit(true) }
            .onEach { enabled -> _uiState.update { it.copy(isCarouselEnabled = enabled) } }
            .launchIn(viewModelScope)

        userDataRepository.isLiteMode
            .catch { emit(false) }
            .onEach { enabled -> _uiState.update { it.copy(isLiteMode = enabled) } }
            .launchIn(viewModelScope)

        userDataRepository.isHdOnlyFilter
            .catch { emit(false) }
            .onEach { enabled -> _uiState.update { it.copy(isHdOnly = enabled) } }
            .launchIn(viewModelScope)

        startLiveScanMonitor()
        startWatchHistoryMonitor()
        refreshCacheSize()
    }

    /** Persist a new username directly. Trims and falls back internally to default. */
    fun setUsername(newName: String) {
        viewModelScope.launch {
            try {
                userDataRepository.setUsername(newName.trim())
            } catch (_: Exception) { }
        }
    }

    fun setHdOnly(enabled: Boolean) {
        viewModelScope.launch {
            try {
                userDataRepository.setHdOnlyFilter(enabled)
            } catch (_: Exception) { }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = runCatching {
                folderSize(context.cacheDir)
            }.getOrDefault(0L)
            val text = formatBytes(bytes)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(cacheSizeText = text) }
            }
        }
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += folderSize(it) }
        return total
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.getDefault(), "%.2f GB", gb)
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
                // Wipe the on-disk cache directory itself. Glide / OkHttp / etc.
                // store their disk caches under context.cacheDir, and the size
                // shown to the user is folderSize(context.cacheDir), so clearing
                // it ensures the displayed value actually drops.
                withContext(Dispatchers.IO) {
                    runCatching { wipeFolder(context.cacheDir) }
                    runCatching { context.externalCacheDir?.let { wipeFolder(it) } }
                }
                _uiState.update { it.copy(cacheCleared = true) }
                showSuccess("Cache cleared successfully")
                // Refresh size AFTER wipe completes so the new number reflects
                // reality (running refreshCacheSize() in parallel raced the
                // delete and reported the stale pre-clear size).
                refreshCacheSizeBlocking()
                delay(2000L)
                _uiState.update { it.copy(cacheCleared = false) }
            } catch (_: Exception) { }
        }
    }

    /** Recursively delete children of [folder] but keep the folder itself. */
    private fun wipeFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) return
        folder.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                wipeFolder(child)
                child.delete()
            } else {
                child.delete()
            }
        }
    }

    private suspend fun refreshCacheSizeBlocking() {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { folderSize(context.cacheDir) }.getOrDefault(0L)
        }
        _uiState.update { it.copy(cacheSizeText = formatBytes(bytes)) }
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
            val items = progressList
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

            val totalMoviesWatched = progressList.count { it.completionPercent >= 50f }
            val totalMs = progressList.sumOf { it.lastPosition.coerceAtLeast(0L) }
            val totalHoursDouble = totalMs / (1000.0 * 60.0 * 60.0)
            val totalHours = if (totalHoursDouble >= 10.0) {
                totalHoursDouble.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", totalHoursDouble)
            }
            val totalDays = (totalHoursDouble / 24.0).toInt()
            val hasStats = progressList.isNotEmpty()

            Triple(items, Triple(totalMoviesWatched, totalHours, totalDays), hasStats)
        }
            .catch { emit(Triple(emptyList(), Triple(0, "0", 0), false)) }
            .onEach { (items, stats, hasStats) ->
                _uiState.update {
                    it.copy(
                        watchHistory = items,
                        isLoadingHistory = false,
                        totalMovies = stats.first,
                        totalHours = stats.second,
                        totalDays = stats.third,
                        hasStats = hasStats
                    )
                }
            }
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
                    lastRefreshDateText = formatRefreshDate(info.latestRefreshTime),
                    relativeRefreshText = relativeFromNow(latestCompletedMillis ?: parseIsoMillis(info.latestRefreshTime)),
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

    /** Premium settings timestamp: dd-MM-yyyy HH:mm in 24-hour, e.g. "08-06-2026 05:18". */
    private fun formatRefreshDate(value: String?): String {
        val millis = parseIsoMillis(value) ?: return "--"
        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun relativeFromNow(millis: Long?): String {
        if (millis == null || millis <= 0L) return ""
        val deltaMs = System.currentTimeMillis() - millis
        if (deltaMs < 0L) return "just now"
        val minutes = deltaMs / 60_000L
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h ago"
        val days = hours / 24
        if (days < 7) return "${days}d ago"
        val weeks = days / 7
        if (weeks < 5) return "${weeks}w ago"
        return "long ago"
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

                    // 4) Clear user prefs (DataStore). Explicitly also write
                    //    isFirstLaunchCompleted = false so the splash screen
                    //    will route to Welcome on restart even if clear() races
                    //    or a default value gets re-seeded by some startup code.
                    try { userDataRepository.clearAllData() } catch (e: Exception) { Log.w(tag, "clear prefs", e) }
                    try { userDataRepository.setFirstLaunchCompleted(false) } catch (e: Exception) { Log.w(tag, "reset first-launch flag", e) }

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

                    // 8) Give the FS / MediaStore / DataStore a moment to flush before exit
                    delay(400)
                } catch (e: Exception) {
                    Log.e(tag, "resetAllAndRestart fatal", e)
                }

                // 9) T2: schedule restart via AlarmManager (reliable across OEMs),
                //    then kill the process. AlarmManager survives our exit and
                //    the system will launch the activity ~100 ms later.
                try {
                    // Build an EXPLICIT intent to SplashActivity. Do NOT use
                    // getLaunchIntentForPackage — on LEANBACK_LAUNCHER-only
                    // (Fire TV) builds it may return null, and even when it
                    // resolves it can point at a non-Splash activity, landing
                    // the user directly on Home and skipping the welcome
                    // routing entirely.
                    //
                    // Also pass EXTRA_FORCE_WELCOME=true so Splash bypasses
                    // the prefs read and routes unconditionally to Welcome —
                    // belt-and-suspenders against any DataStore flush race
                    // across the process kill.
                    val intent = android.content.Intent().apply {
                        component = android.content.ComponentName(
                            context.packageName,
                            "com.kstream.tv.ui.splash.SplashActivity"
                        )
                        putExtra("force_welcome", true)
                        addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }
                    run {
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
