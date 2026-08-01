package com.kstream.feature.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kstream.core.common.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) {
    private var exoPlayer: ExoPlayer? = null
    private var fallbackUrls: List<String> = emptyList()
    private var fallbackIndex: Int = 0
    private var isStreamingMode = false
    private var lastPlaybackPosition: Long = 0
    private var wasPlayingBeforeNetworkLoss = false
    private var networkObserverJob: Job? = null
    private var wasPlayingBeforePause = false
    private var hasRetriedCurrentUrl = false
    private var isReleased = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _allUrlsFailed = kotlinx.coroutines.flow.MutableSharedFlow<PlaybackException>(extraBufferCapacity = 1)
    val allUrlsFailed: kotlinx.coroutines.flow.SharedFlow<PlaybackException> = _allUrlsFailed

    private var errorRetryCount: Int = 0
    private val maxErrorRetries: Int = 3

    fun getPlayer(): Player {
        if (isReleased) {
            if (exoPlayer != null) return exoPlayer!!
            isReleased = false
        }
        if (exoPlayer == null) {
            val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
            val builder = ExoPlayer.Builder(context)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(buildDataSourceFactory()))
            if (isTv) {
                // Reduce buffer on TV (1GB RAM) to lower memory pressure and GC thrashing
                builder.setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            10_000,  // min buffer: 10s (default 15s)
                            20_000,  // max buffer: 20s (default 50s)
                            2_000,   // playback start: 2s
                            3_000    // rebuffer start: 3s
                        )
                        .build()
                )
            }
            exoPlayer = builder.build().apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC) // fast keyframe seeks for TV
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && isPlaying) {
                            lastPlaybackPosition = currentPosition
                            hasRetriedCurrentUrl = false
                            errorRetryCount = 0
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            lastPlaybackPosition = exoPlayer?.currentPosition ?: 0
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val player = exoPlayer ?: return

                        if (isStreamingMode) {
                            when (error.errorCode) {
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                                    try {
                                        lastPlaybackPosition = player.currentPosition
                                        player.pause()
                                    } catch (_: Exception) {}
                                    return
                                }
                            }
                        }

                        // Limit total retries to prevent infinite retry loop (ANR on low-end TVs)
                        errorRetryCount++
                        if (errorRetryCount > maxErrorRetries) {
                            _allUrlsFailed.tryEmit(error)
                            return
                        }

                        if (!hasRetriedCurrentUrl) {
                            hasRetriedCurrentUrl = true
                            try { player.prepare(); player.play() } catch (_: Exception) {}
                            return
                        }
                        hasRetriedCurrentUrl = false
                       
                        val next = fallbackUrls.getOrNull(fallbackIndex + 1)
                        if (next != null) {
                            fallbackIndex += 1
                            try {
                                player.setMediaItem(MediaItem.fromUri(next))
                                player.prepare()
                                player.play()
                            } catch (_: Exception) {}
                        } else {
                            _allUrlsFailed.tryEmit(error)
                        }
                    }
                })
            }
        }
        return exoPlayer!!
    }

    /**
     * HTTP data source wrapped in a [ResolvingDataSource] that re-resolves
     * single-use signed `download.php` links to a fresh underlying file URL on
     * every open. This restores normal Range-based seeking for CDNs that hand
     * out one-shot redirect tokens (the root cause that previously forced the
     * whole file to download before playback, and the reason libVLC was added).
     */
    private fun buildDataSourceFactory(): ResolvingDataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(RedirectResolver.UA)
            .setAllowCrossProtocolRedirects(true)
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        return ResolvingDataSource.Factory(baseFactory, RedirectResolver())
    }

    fun play(urls: List<String>, startPosition: Long = 0, forceStartOver: Boolean = false) {
        val validUrls = urls.filter { it.isNotBlank() }.distinct()
        if (validUrls.isEmpty()) return

        fallbackUrls = validUrls
        fallbackIndex = 0
        isStreamingMode = true
        lastPlaybackPosition = if (forceStartOver) 0 else startPosition

        val player = getPlayer()
        val mediaItem = MediaItem.fromUri(validUrls.first())
        player.setMediaItem(mediaItem)
        if (!forceStartOver && startPosition > 0) {
            player.seekTo(startPosition)
        }
        player.prepare()
        player.play()
       
        observeNetworkAndResume()
    }

    /**
     * Switch the streaming URL while preserving the playback position. The
     * caller passes `position` because the player's own currentPosition can
     * race with the setMediaItem boundary (ExoPlayer also resets internal
     * position when the URI identity changes even with resetPosition=false,
     * which is why quality switches were restarting from the beginning).
     */
    fun switchQuality(url: String, position: Long = 0L) {
        val player = getPlayer()
        val resumeFrom = if (position > 0) position else player.currentPosition
        val wasPlaying = player.isPlaying
        val mediaItem = MediaItem.fromUri(url)

        player.setMediaItem(mediaItem, /*resetPosition*/ false)
        player.prepare()
        if (resumeFrom > 0) {
            player.seekTo(resumeFrom)
        }
        if (wasPlaying) {
            player.play()
        }
    }

    fun playLocal(filePath: String, startPosition: Long = 0, forceStartOver: Boolean = false) {
        isStreamingMode = false
        networkObserverJob?.cancel()
        networkObserverJob = null
       
        val player = getPlayer()
        val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(java.io.File(filePath))
        }
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        if (!forceStartOver && startPosition > 0) {
            player.seekTo(startPosition)
        }
        player.prepare()
        player.play()
    }

    private fun observeNetworkAndResume() {
        networkObserverJob?.cancel()
        networkObserverJob = scope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isStreamingMode && !isOnline) {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) {
                            lastPlaybackPosition = player.currentPosition
                            wasPlayingBeforeNetworkLoss = true
                        }
                    }
                } else if (isStreamingMode && isOnline && wasPlayingBeforeNetworkLoss) {
                    exoPlayer?.let { player ->
                        if (lastPlaybackPosition > 0) {
                            player.seekTo(lastPlaybackPosition)
                        }
                        player.play()
                    }
                    wasPlayingBeforeNetworkLoss = false
                }
            }
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun pauseIfExists() {
        try {
            exoPlayer?.pause()
        } catch (e: Exception) {
            // Player may be in a bad state during cleanup
        }
    }

    fun playerOrNull(): Player? = exoPlayer

    /**
     * Engine-agnostic facade. Internally backed by the same `ExoPlayer` that
     * `getPlayer()` exposes; safe to use from `PlayerEngineCoordinator`.
     *
     * Note: `PlaybackController.addListener` registers a `Player.Listener` that
     * forwards the 3 events the fragment cares about; we hold a map so that
     * `removeListener` actually removes the underlying listener.
     */
    fun controller(): PlaybackController = exoController

    private val controllerListenerMap = mutableMapOf<ControllerListener, Player.Listener>()

    private val exoController: PlaybackController = object : PlaybackController {
        override val isPlaying: Boolean get() = exoPlayer?.isPlaying == true
        override val currentPositionMs: Long get() = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        override val durationMs: Long get() = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
        override val bufferedPositionMs: Long get() = exoPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
        override val playbackState: Int
            get() = exoPlayer?.playbackState ?: PlaybackController.STATE_IDLE

        override fun play() { exoPlayer?.play() }
        override fun pause() { exoPlayer?.pause() }
        override fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }

        override fun addListener(listener: ControllerListener) {
            val player = getPlayer()
            val pl = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    listener.onIsPlayingChanged(isPlaying)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    listener.onPlaybackStateChanged(playbackState)
                }
                override fun onIsLoadingChanged(isLoading: Boolean) {
                    listener.onIsLoadingChanged(isLoading)
                }
                override fun onRenderedFirstFrame() {
                    listener.onFirstFrameRendered()
                }
            }
            controllerListenerMap[listener] = pl
            player.addListener(pl)
        }

        override fun removeListener(listener: ControllerListener) {
            controllerListenerMap.remove(listener)?.let { exoPlayer?.removeListener(it) }
        }
    }

    fun release() {
        isReleased = true
        errorRetryCount = 0
        networkObserverJob?.cancel()
        networkObserverJob = null
        try { exoPlayer?.release() } catch (_: Exception) {}
        exoPlayer = null
        try { scope.cancel() } catch (_: Exception) {}
    }
}
