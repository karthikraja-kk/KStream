package com.kstream.tv.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        AppState.currentRoute = ROUTE

        if (savedInstanceState == null) {
            val frag = PlayerTvFragment().apply {
                arguments = Bundle().apply {
                    putString("movieId", Uri.encode(intent.getStringExtra(EXTRA_MOVIE_ID).orEmpty()))
                    putString("quality", Uri.encode(intent.getStringExtra(EXTRA_QUALITY).orEmpty()))
                    putString("source", intent.getStringExtra(EXTRA_SOURCE) ?: "stream")
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.player_container, frag, TAG)
                .commitNow()
        }
    }

    companion object {
        const val ROUTE = "tv/player"
        const val EXTRA_MOVIE_ID = "movieId"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STREAM_URL = "streamUrl"
        private const val TAG = "player_frag"

        fun newIntent(
            ctx: Context,
            movieId: String,
            title: String,
            streamUrl: String,
            quality: String,
            source: String = "stream"
        ): Intent = Intent(ctx, PlayerActivity::class.java).apply {
            putExtra(EXTRA_MOVIE_ID, movieId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_STREAM_URL, streamUrl)
            putExtra(EXTRA_QUALITY, quality)
            putExtra(EXTRA_SOURCE, source)
        }
    }
}
