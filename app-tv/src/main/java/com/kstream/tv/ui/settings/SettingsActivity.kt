package com.kstream.tv.ui.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.kstream.core.common.AppState
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        AppState.currentRoute = ROUTE
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsTvFragment())
                .commitNow()
        }
    }
    companion object { const val ROUTE = "tv/settings" }
}

