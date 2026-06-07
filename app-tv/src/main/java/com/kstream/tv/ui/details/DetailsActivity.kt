package com.kstream.tv.ui.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.core.model.Movie
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        AppState.currentRoute = ROUTE

        if (savedInstanceState == null) {
            val movieId = intent.getStringExtra(EXTRA_MOVIE_ID).orEmpty()
            val frag = DetailsTvFragment().apply {
                arguments = Bundle().apply { putString("movieId", Uri.encode(movieId)) }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_container, frag, TAG)
                .commitNow()
        }
    }

    companion object {
        const val ROUTE = "tv/details"
        const val EXTRA_MOVIE_ID = "movieId"
        private const val TAG = "details_frag"

        fun newIntent(ctx: Context, movie: Movie): Intent =
            Intent(ctx, DetailsActivity::class.java).putExtra(EXTRA_MOVIE_ID, movie.id)

        fun newIntent(ctx: Context, movieId: String): Intent =
            Intent(ctx, DetailsActivity::class.java).putExtra(EXTRA_MOVIE_ID, movieId)
    }
}
