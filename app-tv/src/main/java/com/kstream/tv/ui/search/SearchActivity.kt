package com.kstream.tv.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        AppState.currentRoute = ROUTE
        if (savedInstanceState == null) {
            val frag = SearchTvFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_QUERY, intent.getStringExtra(EXTRA_INITIAL_QUERY))
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_container, frag, TAG)
                .commitNow()
        }
    }

    companion object {
        const val ROUTE = "tv/search"
        const val EXTRA_INITIAL_QUERY = "initialQuery"
        const val ARG_INITIAL_QUERY = "initialQuery"
        private const val TAG = "search_frag"

        fun newIntent(ctx: Context, initialQuery: String? = null): Intent =
            Intent(ctx, SearchActivity::class.java).apply {
                if (initialQuery != null) putExtra(EXTRA_INITIAL_QUERY, initialQuery)
            }
    }
}
