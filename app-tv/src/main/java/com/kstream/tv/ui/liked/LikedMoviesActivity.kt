package com.kstream.tv.ui.liked

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LikedMoviesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liked_movies)
        AppState.currentRoute = ROUTE

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.liked_movies_container, LikedMoviesFragment(), TAG)
                .commitNow()
        }
    }

    companion object {
        const val ROUTE = "tv/liked-movies"
        private const val TAG = "liked_movies_frag"

        fun newIntent(context: Context): Intent = Intent(context, LikedMoviesActivity::class.java)
    }
}
