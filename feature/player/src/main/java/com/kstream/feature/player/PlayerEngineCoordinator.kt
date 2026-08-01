package com.kstream.feature.player

import com.kstream.core.domain.repository.MovieEnginePrefRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level coordinator that owns [PlayerManager] (ExoPlayer) and drives a
 * playback session.
 *
 * ExoPlayer is the only engine. The previous libVLC fallback was removed once
 * the real root cause of the "downloads whole file before playing" symptom was
 * found and fixed in [PlayerManager]: some CDNs hand out single-use signed
 * links, so ExoPlayer's seek to read the MP4 `moov` atom re-opened a dead URL
 * and fell back to a byte-0 full read. [RedirectResolver] now re-resolves the
 * link on every open, so ordinary Range-based seeking works.
 *
 * Resolution rules (per call to [play]):
 *   1. User setting [VideoEngine.EXO] is honored verbatim.
 *   2. In [VideoEngine.AUTO] mode, if a per-movie [MovieEnginePrefRepository]
 *      entry exists and its `lastFailMs` is within [LOCKOUT_MS],
 *      [Event.SourceUnavailable] is emitted immediately without starting.
 *   3. A [HARD_CAP_MS] timer wraps the whole play attempt. If no first paint by
 *      then, playback is released and [Event.SourceUnavailable] is emitted.
 *   4. In AUTO mode, the first failure on possibly-stale URLs emits
 *      [Event.LinkProbablyStale] so the VM can refresh and retry.
 */
@Singleton
class PlayerEngineCoordinator @Inject constructor(
    val exoManager: PlayerManager,
    private val enginePrefRepo: MovieEnginePrefRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _activeEngine = MutableStateFlow(VideoEngine.EXO)
    val activeEngine: StateFlow<VideoEngine> = _activeEngine.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var currentMovieId: String = ""
    private var currentUrls: List<String> = emptyList()
    private var hardCapJob: Job? = null
    private var hasReachedReady = false

    // True once the VM has confirmed (via [markUrlsFresh]) that the URLs handed
    // to [play] came straight from a successful refresh. When false, any Exo
    // failure in Auto mode is treated as "probably-stale URL" and we ask the VM
    // to refresh before giving up.
    private var urlsKnownFresh: Boolean = false

    // Cached so we can read it without going back to the repo.
    private var userSetting: VideoEngine = VideoEngine.DEFAULT

    private val exoListener = object : ControllerListener {
        override fun onFirstFrameRendered() {
            android.util.Log.i(TAG, "Exo: first frame rendered")
            onEngineReady(VideoEngine.EXO)
        }
        override fun onAllUrlsFailed() {
            onEngineFailed(VideoEngine.EXO)
        }
    }

    init {
        exoManager.controller().addListener(exoListener)

        // Forward ExoPlayer's allUrlsFailed SharedFlow into the listener path.
        scope.launch {
            exoManager.allUrlsFailed.collect { exoListener.onAllUrlsFailed() }
        }
    }

    /**
     * VM signals that the URLs it just handed [play] came from a successful
     * refresh and should be considered fresh. After this the next Exo failure
     * in Auto mode surfaces as [Event.SourceUnavailable] rather than another
     * refresh request.
     */
    fun markUrlsFresh() {
        android.util.Log.i(TAG, "URLs marked fresh — next Exo failure will surface as unavailable")
        urlsKnownFresh = true
    }

    /**
     * Cancel the running hard-cap timer without releasing the engine. Used by
     * the VM when it suspends playback to perform a link refresh — the refresh
     * has its own polling timeout and we don't want the hard-cap to fire while
     * we're waiting for new URLs.
     */
    fun pauseTimers() {
        android.util.Log.i(TAG, "pauseTimers() — refresh in progress")
        cancelTimers()
    }

    /** Returns the controller for the active engine (always ExoPlayer). */
    fun controller(): PlaybackController = exoManager.controller()

    /** Play a streaming URL chain. */
    fun play(
        movieId: String,
        urls: List<String>,
        startPosition: Long,
        forceStartOver: Boolean,
        userSetting: VideoEngine
    ) {
        // If movie changed since the last play, reset per-session freshness flag.
        if (movieId != currentMovieId) urlsKnownFresh = false
        currentMovieId = movieId
        currentUrls = urls
        this.userSetting = userSetting
        hasReachedReady = false
        cancelTimers()

        scope.launch {
            val chosen = pickEngine(movieId, userSetting)
            android.util.Log.i(TAG, "play(movie=$movieId, urls=${urls.size}, userSetting=$userSetting) → chosen=$chosen")
            if (chosen == null) {
                // 5-min lockout from previous failure — surface error now.
                _events.tryEmit(Event.SourceUnavailable(reason = LockoutReason.RECENT_FAILURE))
                return@launch
            }
            startOn(chosen, urls, startPosition, forceStartOver)
            armHardCap()
        }
    }

    /** Play a local file. Always uses ExoPlayer. */
    fun playLocal(filePath: String, startPosition: Long, forceStartOver: Boolean) {
        _activeEngine.value = VideoEngine.EXO
        cancelTimers()
        exoManager.playLocal(filePath, startPosition, forceStartOver)
    }

    /** Swap to the next quality URL, preserving the supplied position. */
    fun switchQuality(url: String, position: Long = 0L) {
        exoManager.switchQuality(url, position)
    }

    fun pauseIfExists() {
        exoManager.pauseIfExists()
    }

    fun release() {
        // Soft release: stop playback but keep this singleton alive (scope,
        // listeners, engine instance) so the next player session can reuse it.
        // Per-session state is reset so the next play() begins fresh.
        cancelTimers()
        try { exoManager.pauseIfExists() } catch (_: Exception) {}
        hasReachedReady = false
        urlsKnownFresh = false
    }

    /**
     * Called externally (e.g. from "Try Again" button) to clear the 5-min
     * lockout for the current movie and re-attempt playback with the same URLs.
     */
    fun retryAfterLockout(startPosition: Long, userSetting: VideoEngine) {
        scope.launch {
            try { enginePrefRepo.clearFailure(currentMovieId) } catch (_: Exception) {}
            if (currentUrls.isNotEmpty()) {
                play(currentMovieId, currentUrls, startPosition, forceStartOver = false, userSetting = userSetting)
            }
        }
    }

    // ------------------------------------------------------------------

    private suspend fun pickEngine(movieId: String, userSetting: VideoEngine): VideoEngine? {
        if (userSetting == VideoEngine.EXO) return VideoEngine.EXO

        // AUTO
        val pref = try { enginePrefRepo.get(movieId) } catch (_: Exception) { null }
        val failMs = pref?.lastFailMs
        if (failMs != null && (System.currentTimeMillis() - failMs) < LOCKOUT_MS) {
            return null
        }
        return VideoEngine.EXO
    }

    private fun startOn(engine: VideoEngine, urls: List<String>, startPos: Long, forceStartOver: Boolean) {
        _activeEngine.value = engine
        exoManager.play(urls, startPos, forceStartOver)
    }

    private fun armHardCap() {
        hardCapJob?.cancel()
        hardCapJob = scope.launch {
            delay(HARD_CAP_MS)
            if (!hasReachedReady) {
                android.util.Log.w(TAG, "Hard cap (${HARD_CAP_MS}ms): engine never reached READY")
                onEngineFailed(_activeEngine.value, hardCap = true)
            }
        }
    }

    private fun cancelTimers() {
        hardCapJob?.cancel(); hardCapJob = null
    }

    private fun onEngineReady(engine: VideoEngine) {
        if (hasReachedReady) return
        hasReachedReady = true
        cancelTimers()
        if (userSetting == VideoEngine.AUTO && currentMovieId.isNotEmpty()) {
            val movieAtReady = currentMovieId
            scope.launch {
                delay(ENGINE_PERSIST_DELAY_MS)
                if (currentMovieId != movieAtReady || _activeEngine.value != engine) return@launch
                try {
                    enginePrefRepo.setEngine(currentMovieId, engine.key)
                    enginePrefRepo.clearFailure(currentMovieId)
                    android.util.Log.i(TAG, "Engine pref persisted: $engine for movie=$currentMovieId")
                } catch (_: Exception) {}
            }
        }
    }

    private fun onEngineFailed(engine: VideoEngine, hardCap: Boolean = false) {
        // In Auto mode, the first failure on possibly-stale URLs should trigger
        // a link refresh and a retry on the same engine, NOT an immediate
        // lockout. The single-use-link fix means a refreshed URL usually plays.
        if (userSetting == VideoEngine.AUTO && !hardCap && !urlsKnownFresh) {
            android.util.Log.i(TAG, "$engine failed with possibly-stale URLs → asking VM to refresh first")
            cancelTimers()
            val pos = try { controller().currentPositionMs } catch (_: Exception) { 0L }
            _events.tryEmit(Event.LinkProbablyStale(currentEngine = engine, position = pos))
            return
        }
        // Engine failed on fresh URLs (or user pinned Exo and it failed). Clear
        // any remembered engine pref and record the failure so the next attempt
        // respects the lockout, then surface the error.
        if (currentMovieId.isNotEmpty()) {
            scope.launch {
                try {
                    enginePrefRepo.recordFailure(currentMovieId, System.currentTimeMillis())
                    enginePrefRepo.setEngine(currentMovieId, "")
                } catch (_: Exception) {}
            }
        }
        cancelTimers()
        _events.tryEmit(
            Event.SourceUnavailable(
                reason = if (hardCap) LockoutReason.HARD_CAP else LockoutReason.BOTH_FAILED
            )
        )
    }

    sealed interface Event {
        data class SourceUnavailable(val reason: LockoutReason) : Event
        /**
         * The active engine failed and the coordinator believes the URLs may be
         * stale (no fresh refresh has been confirmed for this session). The VM
         * should refresh URLs and call [play] again. After a successful refresh
         * the VM must call [markUrlsFresh] so a subsequent failure surfaces as
         * unavailable rather than refreshing forever.
         */
        data class LinkProbablyStale(val currentEngine: VideoEngine, val position: Long) : Event
    }

    enum class LockoutReason { HARD_CAP, BOTH_FAILED, RECENT_FAILURE }

    companion object {
        private const val TAG = "EngineCoordinator"

        /** Total wall-clock budget for first paint before "Source unavailable". */
        private const val HARD_CAP_MS = 30_000L

        /** How long after a failure to short-circuit subsequent attempts. */
        private const val LOCKOUT_MS = 5 * 60 * 1000L

        /**
         * Time the engine must remain "ready" (no failure) before we persist it
         * as the remembered Auto-mode engine for this movie.
         */
        private const val ENGINE_PERSIST_DELAY_MS = 10_000L
    }
}
