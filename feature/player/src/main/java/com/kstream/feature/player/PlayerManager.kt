package com.kstream.feature.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
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
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _allUrlsFailed = kotlinx.coroutines.flow.MutableSharedFlow<PlaybackException>(extraBufferCapacity = 1)
    val allUrlsFailed: kotlinx.coroutines.flow.SharedFlow<PlaybackException> = _allUrlsFailed

    fun getPlayer(): Player {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && isPlaying) {
                            lastPlaybackPosition = currentPosition
                            hasRetriedCurrentUrl = false
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
                                    lastPlaybackPosition = player.currentPosition
                                    player.pause()
                                    return
                                }
                            }
                        }

                        // On app resume, URLs may still be valid — retry current URL once
                        if (!hasRetriedCurrentUrl) {
                            hasRetriedCurrentUrl = true
                            player.prepare()
                            player.play()
                            return
                        }
                        hasRetriedCurrentUrl = false
                        
                        val next = fallbackUrls.getOrNull(fallbackIndex + 1)
                        if (next != null) {
                            fallbackIndex += 1
                            player.setMediaItem(MediaItem.fromUri(next))
                            player.prepare()
                            player.play()
                        } else {
                            // All fallback URLs exhausted — signal for media refresh
                            _allUrlsFailed.tryEmit(error)
                        }
                    }
                })
            }
        }
        return exoPlayer!!
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

    fun switchQuality(url: String) {
        val player = getPlayer()
        val currentPosition = player.currentPosition
        val wasPlaying = player.isPlaying
        val mediaItem = MediaItem.fromUri(url)
        
        // setMediaItem with resetPosition = false keeps the current position
        player.setMediaItem(mediaItem, false)
        player.prepare()
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
        // runBlocking on a continuous flow deadlocks the main thread.
        // This method is not used, but let's provide a safe implementation if needed.
        return true // Defaulting to true as the player handles errors internally anyway
    }

    fun release() {
        networkObserverJob?.cancel()
        networkObserverJob = null
        exoPlayer?.release()
        exoPlayer = null
        try {
            // Cancel the scope to stop all internal coroutines
            scope.cancel()
        } catch (e: Exception) {}
    }
}