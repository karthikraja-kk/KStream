package com.kstream.tv.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import com.kstream.tv.ui.home.HomeBrowseFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Post-welcome host activity. Mounts the [HomeBrowseFragment] (Leanback
 * BrowseSupportFragment) which renders the rails sourced from [HomeViewModel].
 *
 * The Activity stays minimal so the rebuilt back stack is always
 * Splash → (Welcome?) → MainActivity, never deeper.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppState.currentRoute = ROUTE

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_container, HomeBrowseFragment(), TAG_HOME)
                .commit()
        }
    }

    companion object {
        const val ROUTE = "tv/home"
        private const val TAG_HOME = "home_browse"
    }
}
