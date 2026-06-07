package com.kstream.tv.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : FragmentActivity() {

    private var lastBackPressMs = 0L
    private var backToast: Toast? = null

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

        // BACK handling: 1) hide controls if visible, 2) toast confirmation, 3) finish on
        // second press within BACK_CONFIRM_MS.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val frag = playerFragment()
                if (frag?.hideControlsForBack() == true) return // consumed by fragment

                val now = System.currentTimeMillis()
                if (now - lastBackPressMs <= BACK_CONFIRM_MS) {
                    backToast?.cancel()
                    finish()
                } else {
                    lastBackPressMs = now
                    backToast?.cancel()
                    backToast = Toast.makeText(
                        this@PlayerActivity,
                        getString(R.string.player_back_to_exit),
                        Toast.LENGTH_SHORT
                    ).also { it.show() }
                }
            }
        })
    }

    /** Forward all D-pad input to the fragment's state machine. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // BACK flows through onBackPressedDispatcher (KeyEvent.KEYCODE_BACK ACTION_UP).
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)

        val frag = playerFragment()
        if (frag != null && frag.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun playerFragment(): PlayerTvFragment? =
        supportFragmentManager.findFragmentByTag(TAG) as? PlayerTvFragment

    companion object {
        const val ROUTE = "tv/player"
        const val EXTRA_MOVIE_ID = "movieId"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STREAM_URL = "streamUrl"
        private const val TAG = "player_frag"
        private const val BACK_CONFIRM_MS = 2_000L

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
