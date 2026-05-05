package com.kstream.feature.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null

    fun getPlayer(): Player {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
        return exoPlayer!!
    }

    fun play(url: String, startPosition: Long = 0) {
        val player = getPlayer()
        val mediaItem = MediaItem.fromUri(url)
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
