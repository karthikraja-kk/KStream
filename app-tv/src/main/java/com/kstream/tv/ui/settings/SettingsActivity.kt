package com.kstream.tv.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.core.common.AppState
import com.kstream.feature.settings.SettingsViewModel
import com.kstream.tv.R
import com.kstream.tv.crash.TvCrashHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

@AndroidEntryPoint
class SettingsTvFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var usernameText: TextView
    private lateinit var scanStatusText: TextView
    private lateinit var lastRefreshText: TextView
    private lateinit var scanDetailText: TextView
    private lateinit var triggerScanBtn: Button
    private lateinit var clearCacheBtn: Button
    private lateinit var clearHistoryBtn: Button
    private lateinit var liteModeBtn: Button
    private lateinit var crashStatsText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_tv, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        usernameText = view.findViewById(R.id.settings_username)
        scanStatusText = view.findViewById(R.id.settings_scan_status)
        lastRefreshText = view.findViewById(R.id.settings_last_refresh)
        scanDetailText = view.findViewById(R.id.settings_scan_detail)
        triggerScanBtn = view.findViewById(R.id.btn_trigger_scan)
        clearCacheBtn = view.findViewById(R.id.btn_clear_cache)
        clearHistoryBtn = view.findViewById(R.id.btn_clear_history)
        liteModeBtn = view.findViewById(R.id.btn_toggle_lite)
        crashStatsText = view.findViewById(R.id.settings_crash_stats)

        triggerScanBtn.setOnClickListener { viewModel.triggerScan() }
        clearCacheBtn.setOnClickListener { viewModel.clearCache() }
        clearHistoryBtn.setOnClickListener { viewModel.clearLikedMovies() }
        liteModeBtn.setOnClickListener {
            val newValue = !(viewModel.uiState.value.isLiteMode)
            viewModel.toggleLiteMode(newValue)
        }

        crashStatsText.text = "Recent crashes: ${TvCrashHandler.getCrashCount(requireContext())}"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    usernameText.text = "Hello, ${state.username.ifBlank { "viewer" }}"
                    scanStatusText.text = state.scanStatusText
                    lastRefreshText.text = state.lastRefreshText
                    scanDetailText.text = state.scanDetailText
                    triggerScanBtn.isEnabled = state.isScanButtonEnabled
                    liteModeBtn.text = if (state.isLiteMode) "Lite Mode: ON" else "Lite Mode: OFF"
                }
            }
        }
    }
}
