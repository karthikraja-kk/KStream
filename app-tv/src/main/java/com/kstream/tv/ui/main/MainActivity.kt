package com.kstream.tv.ui.main

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.kstream.core.common.AppState
import com.kstream.core.model.Movie
import com.kstream.tv.R
import com.kstream.tv.ui.home.HeroFragment
import com.kstream.tv.ui.home.HomeRowsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Post-welcome host activity. Mounts two fragments side-by-side vertically:
 *  - [HeroFragment] (top 60%) — auto-rotating premium hero carousel.
 *  - [HomeRowsFragment] (bottom 40%) — Leanback RowsSupportFragment with rails.
 *
 * The activity owns cross-fragment focus handoff (D-pad UP from row 0 → hero
 * Play button; D-pad DOWN from any hero button → first card of first rail).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var heroFragment: HeroFragment? = null
    private var rowsFragment: HomeRowsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppState.currentRoute = ROUTE

        if (savedInstanceState == null) {
            val hero = HeroFragment()
            val rows = HomeRowsFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.hero_container, hero, TAG_HERO)
                .replace(R.id.rows_container, rows, TAG_ROWS)
                .commitNow()
            heroFragment = hero
            rowsFragment = rows
        } else {
            heroFragment = supportFragmentManager.findFragmentByTag(TAG_HERO) as? HeroFragment
            rowsFragment = supportFragmentManager.findFragmentByTag(TAG_ROWS) as? HomeRowsFragment
        }

        heroFragment?.setOnPlayClicked { movie: Movie ->
            val mediaQuality = "1080p"
            startActivity(
                com.kstream.tv.ui.player.PlayerActivity.newIntent(
                    this,
                    movieId = movie.id,
                    title = movie.movieName,
                    streamUrl = "",
                    quality = mediaQuality
                )
            )
        }
        heroFragment?.setOnMoreInfoClicked { movie: Movie ->
            startActivity(com.kstream.tv.ui.details.DetailsActivity.newIntent(this, movie))
        }
        rowsFragment?.setOnMovieClicked { movie: Movie ->
            startActivity(com.kstream.tv.ui.details.DetailsActivity.newIntent(this, movie))
        }
    }

    /**
     * Cross-fragment focus handoff.
     *  - UP key inside the rows container while row index is 0 → focus hero Play.
     *
     * Note: Hero → rows DOWN is handled by the standard `nextFocusDown` chain
     * on the hero buttons (which look up the rows container by ID via
     * descendantFocusability=afterDescendants).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP
        ) {
            val rows = rowsFragment
            val rowsContainer = findViewById<android.view.View>(R.id.rows_container)
            val focusInRows = rowsContainer?.findFocus() != null
            if (focusInRows && rows != null && rows.currentSelectedRow == 0) {
                if (heroFragment?.focusPlayButton() == true) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val ROUTE = "tv/home"
        private const val TAG_HERO = "hero"
        private const val TAG_ROWS = "home_rows"
    }
}
