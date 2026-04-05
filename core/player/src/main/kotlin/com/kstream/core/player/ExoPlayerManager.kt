package com.kstream.core.player

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

class ExoPlayerManager(private val context: Context) {

    fun buildPlayer(): ExoPlayer {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRam = activityManager.isLowRamDevice

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (isLowRam) 15000 else 30000, // min
                if (isLowRam) 30000 else 60000, // max
                if (isLowRam) 1500 else 2500,   // buffer for playback
                if (isLowRam) 3000 else 5000    // buffer for rebuffering
            )
            .build()

        val trackSelector = DefaultTrackSelector(context)
        if (isLowRam) {
            trackSelector.parameters = DefaultTrackSelector.Parameters.Builder(context)
                .setMaxVideoSizeSd()
                .build()
        }

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
    }

    fun buildMediaItem(url: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
    }
}
