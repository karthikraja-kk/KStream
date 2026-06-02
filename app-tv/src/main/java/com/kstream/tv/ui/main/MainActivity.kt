package com.kstream.tv.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Post-welcome host activity. P3 placeholder; P4 will mount the Leanback
 * BrowseFragment for the Home screen into the FrameLayout below.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppState.currentRoute = ROUTE
    }

    companion object {
        const val ROUTE = "tv/home"
    }
}
