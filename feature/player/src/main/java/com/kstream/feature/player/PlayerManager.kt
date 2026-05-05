package com.kstream.feature.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var fallbackUrls: List<String> = emptyList()
    private var fallbackIndex: Int = 0

    fun getPlayer(): Player {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val player = exoPlayer ?: return
                        val next = fallbackUrls.getOrNull(fallbackIndex + 1) ?: return
                        fallbackIndex += 1
                        player.setMediaItem(MediaItem.fromUri(next))
                        player.prepare()
                        player.play()
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun play(urls: List<String>, startPosition: Long = 0) {
        val validUrls = urls.filter { it.isNotBlank() }.distinct()
        if (validUrls.isEmpty()) return

        fallbackUrls = validUrls
        fallbackIndex = 0

        val player = getPlayer()
        val mediaItem = MediaItem.fromUri(validUrls.first())
        player.setMediaItem(mediaItem)
        if (startPosition > 0) {
            player.seekTo(startPosition)
        }
        player.prepare()
        player.play()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
