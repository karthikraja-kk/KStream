package com.kstream.tv.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import com.kstream.tv.ui.common.SideNavController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : FragmentActivity() {

    private val sideNav = SideNavController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        AppState.currentRoute = ROUTE
        if (savedInstanceState == null) {
            val frag = SearchTvFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_QUERY, intent.getStringExtra(EXTRA_INITIAL_QUERY))
                    putString(ARG_INITIAL_SCOPE, intent.getStringExtra(EXTRA_INITIAL_SCOPE))
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_container, frag, TAG)
                .commitNow()
        }
        sideNav.attach(this)
        sideNav.setActive(R.id.nav_item_search)
        // Search tab is the current screen — collapse rather than re-launch.
        sideNav.onSearchClick = { sideNav.collapseAndRestoreFocus() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (sideNav.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val ROUTE = "tv/search"
        const val EXTRA_INITIAL_QUERY = "initialQuery"
        const val EXTRA_INITIAL_SCOPE = "initialScope"
        const val ARG_INITIAL_QUERY = "initialQuery"
        const val ARG_INITIAL_SCOPE = "initialScope"
        private const val TAG = "search_frag"

        fun newIntent(ctx: Context, initialQuery: String? = null, initialScope: String? = null): Intent =
            Intent(ctx, SearchActivity::class.java).apply {
                if (initialQuery != null) putExtra(EXTRA_INITIAL_QUERY, initialQuery)
                if (initialScope != null) putExtra(EXTRA_INITIAL_SCOPE, initialScope)
            }
    }
}
