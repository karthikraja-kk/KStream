package com.kstream.tv.ui.browse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kstream.core.common.AppState
import com.kstream.tv.ui.search.SearchActivity

/**
 * Browse All — delegated to [SearchActivity] with a reserved-prefix query.
 * The user filters/sorts the full catalog via the same UI as ordinary search.
 */
class BrowseAllActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.currentRoute = ROUTE
        val query = intent.getStringExtra(EXTRA_QUERY) ?: "all:*"
        startActivity(SearchActivity.newIntent(this, query))
        finish()
    }

    companion object {
        const val ROUTE = "tv/browse"
        const val EXTRA_QUERY = "query"

        fun newIntentAll(ctx: Context): Intent =
            Intent(ctx, BrowseAllActivity::class.java).putExtra(EXTRA_QUERY, "all:*")

        fun newIntentLiked(ctx: Context): Intent =
            Intent(ctx, BrowseAllActivity::class.java).putExtra(EXTRA_QUERY, "liked:*")

        fun newIntentHistory(ctx: Context): Intent =
            Intent(ctx, BrowseAllActivity::class.java).putExtra(EXTRA_QUERY, "history:*")
    }
}
