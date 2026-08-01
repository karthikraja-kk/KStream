package com.kstream.feature.player

/**
 * Engine-agnostic playback control surface used by `PlayerTvFragment`
 * and `PlayerViewModel`. `PlayerManager` (ExoPlayer) exposes a
 * [PlaybackController].
 *
 * Time units are milliseconds (matching Media3's `Player`).
 *
 * `playbackState` codes match Media3 `Player.STATE_*` so the existing
 * fragment code doesn't have to translate:
 *   - STATE_IDLE       = 1
 *   - STATE_BUFFERING  = 2
 *   - STATE_READY      = 3
 *   - STATE_ENDED      = 4
 */
interface PlaybackController {
    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val bufferedPositionMs: Long
    val playbackState: Int

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    fun addListener(listener: ControllerListener)
    fun removeListener(listener: ControllerListener)

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }
}

interface ControllerListener {
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    fun onPlaybackStateChanged(state: Int) {}
    fun onIsLoadingChanged(isLoading: Boolean) {}
    /** Engine reports its source/URL chain is exhausted. */
    fun onAllUrlsFailed() {}
    /**
     * First decoded video frame has been rendered to the surface. Stronger
     * signal than [onPlaybackStateChanged] STATE_READY because some sources
     * (e.g. MP4s with moov atom at end on no-Range CDNs) cause Exo to report
     * READY without ever producing pixels.
     */
    fun onFirstFrameRendered() {}
}
