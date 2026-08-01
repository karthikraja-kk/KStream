package com.kstream.feature.player

/**
 * Selectable video engine for playback.
 *
 * AUTO: app picks the best engine per movie. On a fresh link it retries once
 *       after re-resolving the source URL when ExoPlayer can't make progress
 *       (e.g. on sources whose signed CDN link is single-use).
 * EXO : always use ExoPlayer.
 *
 * Both values map to ExoPlayer; AUTO additionally enables stale-link retry.
 */
enum class VideoEngine(val key: String) {
    AUTO("AUTO"),
    EXO("EXO");

    companion object {
        val DEFAULT: VideoEngine = AUTO

        fun fromKey(value: String?): VideoEngine =
            values().firstOrNull { it.key.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
