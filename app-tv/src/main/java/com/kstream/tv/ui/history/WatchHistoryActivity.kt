package com.kstream.tv.ui.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WatchHistoryActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_history)
        AppState.currentRoute = ROUTE

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.watch_history_container, WatchHistoryFragment(), TAG)
                .commitNow()
        }
    }

    companion object {
        const val ROUTE = "tv/watch-history"
        private const val TAG = "watch_history_frag"

        fun newIntent(context: Context): Intent = Intent(context, WatchHistoryActivity::class.java)
    }
}
