package com.kstream.tv.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.feature.settings.ScanState
import com.kstream.feature.settings.SettingsUiState
import com.kstream.feature.settings.SettingsViewModel
import com.kstream.tv.R
import com.kstream.tv.ui.common.AppConfirmDialog
import com.kstream.tv.ui.history.WatchHistoryActivity
import com.kstream.tv.ui.liked.LikedMoviesActivity
import com.kstream.tv.ui.terms.TermsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Sleek brand-themed settings — hero (avatar + name + stats) + grouped tile sections.
 * Name uses an AlertDialog-backed editor (Fire TV remotes don't reliably bring up the IME
 * for in-layout EditTexts); HD uses an ON/OFF pill (replaces crash-prone SwitchCompat).
 */
@AndroidEntryPoint
class SettingsTvFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var avatarInitials: TextView
    private lateinit var nameText: TextView
    private lateinit var nameEdit: EditText
    private lateinit var nameAction: ImageView
    private lateinit var statsRow: LinearLayout
    private lateinit var statMovies: TextView
    private lateinit var statHours: TextView
    private lateinit var statDays: TextView

    private lateinit var tileWatchHistory: View
    private lateinit var tileLiked: View
    private lateinit var tileScan: View
    private lateinit var tileHdOnly: View
    private lateinit var tileCache: View
    private lateinit var tileAbout: View
    private lateinit var tileTerms: View
    private lateinit var tileReset: View

    private lateinit var hdPill: TextView
    private lateinit var scanPill: LinearLayout
    private lateinit var scanPillDot: View
    private lateinit var scanPillLabel: TextView
    private lateinit var scanRelative: TextView
    private lateinit var scanDate: TextView
    private lateinit var cacheSubtitle: TextView

    @Volatile private var isDispatchingScan = false
    private var currentHdOnly: Boolean = false
    private var isEditingName: Boolean = false
    private var lastRenderedName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings_tv, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        wireTiles()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCacheSize()
    }

    private fun bindViews(v: View) {
        avatarInitials = v.findViewById(R.id.settings_avatar_initials)
        nameText = v.findViewById(R.id.settings_name_text)
        nameEdit = v.findViewById(R.id.settings_name_edit)
        nameAction = v.findViewById(R.id.settings_name_action)
        statsRow = v.findViewById(R.id.settings_stats_row)
        statMovies = v.findViewById(R.id.stat_movies_value)
        statHours = v.findViewById(R.id.stat_hours_value)
        statDays = v.findViewById(R.id.stat_days_value)
        tileWatchHistory = v.findViewById(R.id.tile_watch_history)
        tileLiked = v.findViewById(R.id.tile_liked)
        tileScan = v.findViewById(R.id.tile_scan)
        tileHdOnly = v.findViewById(R.id.tile_hd_only)
        tileCache = v.findViewById(R.id.tile_cache)
        tileAbout = v.findViewById(R.id.tile_about)
        tileTerms = v.findViewById(R.id.tile_terms)
        tileReset = v.findViewById(R.id.tile_reset)
        hdPill = v.findViewById(R.id.tile_hd_pill)
        scanPill = v.findViewById(R.id.scan_status_pill)
        scanPillDot = v.findViewById(R.id.scan_status_dot)
        scanPillLabel = v.findViewById(R.id.scan_status_label)
        scanRelative = v.findViewById(R.id.scan_relative_text)
        scanDate = v.findViewById(R.id.scan_date_text)
        cacheSubtitle = v.findViewById(R.id.tile_cache_sub)
    }

    private fun wireTiles() {
        nameAction.setOnClickListener {
            if (isEditingName) commitNameEdit() else enterNameEdit()
        }
        nameEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitNameEdit(); true
            } else false
        }
        tileWatchHistory.setOnClickListener {
            startActivity(Intent(requireContext(), WatchHistoryActivity::class.java))
        }
        tileLiked.setOnClickListener {
            startActivity(Intent(requireContext(), LikedMoviesActivity::class.java))
        }
        tileScan.setOnClickListener { onScanTileClicked() }
        tileHdOnly.setOnClickListener {
            val next = !currentHdOnly
            currentHdOnly = next
            renderHdPill(next)
            viewModel.setHdOnly(next)
        }
        tileCache.setOnClickListener { showClearCacheDialog() }
        tileAbout.setOnClickListener { showAboutDialog() }
        tileTerms.setOnClickListener {
            startActivity(Intent(requireContext(), TermsActivity::class.java))
        }
        tileReset.setOnClickListener { showResetDialog() }
    }

    private fun enterNameEdit() {
        isEditingName = true
        nameText.visibility = View.GONE
        nameEdit.visibility = View.VISIBLE
        nameEdit.setText(nameText.text)
        nameEdit.setSelection(nameEdit.text?.length ?: 0)
        nameAction.setImageResource(R.drawable.ic_settings_check)
        nameAction.contentDescription = getString(android.R.string.ok)
        nameEdit.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(nameEdit, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun commitNameEdit() {
        val newName = nameEdit.text?.toString().orEmpty().trim()
        viewModel.setUsername(newName)
        exitNameEdit()
    }

    private fun exitNameEdit() {
        isEditingName = false
        nameEdit.visibility = View.GONE
        nameText.visibility = View.VISIBLE
        nameAction.setImageResource(R.drawable.ic_settings_pencil)
        nameAction.contentDescription = getString(R.string.settings_name_edit_hint)
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(nameEdit.windowToken, 0)
        nameAction.requestFocus()
    }

    private fun onScanTileClicked() {
        val state = viewModel.uiState.value.scanState
        if (isDispatchingScan) return
        if (state == ScanState.RUNNING || state == ScanState.COOLDOWN || state == ScanState.TRIGGERING) {
            return
        }
        AppConfirmDialog.show(
            context = requireContext(),
            title = getString(R.string.settings_confirm_scan_title),
            message = getString(R.string.settings_confirm_scan_msg),
            positiveLabel = getString(R.string.settings_confirm_scan_yes),
            negativeLabel = getString(R.string.settings_dialog_cancel),
            onConfirm = {
                if (isDispatchingScan) return@show
                val current = viewModel.uiState.value.scanState
                if (current == ScanState.RUNNING || current == ScanState.COOLDOWN || current == ScanState.TRIGGERING) {
                    return@show
                }
                isDispatchingScan = true
                renderScanPill(ScanState.TRIGGERING)
                tileScan.alpha = 0.6f
                viewModel.triggerScan()
                view?.postDelayed({ isDispatchingScan = false }, 1500)
            }
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }
    }

    private fun render(s: SettingsUiState) {
        avatarInitials.text = s.avatarInitials
        val displayed = s.displayName.ifBlank { getString(R.string.settings_hero_greeting) }
        if (!isEditingName && displayed != lastRenderedName) {
            nameText.text = displayed
            lastRenderedName = displayed
        }

        statsRow.isVisible = s.hasStats
        statMovies.text = s.totalMovies.toString()
        statHours.text = s.totalHours.toString()
        statDays.text = s.totalDays.toString()

        if (currentHdOnly != s.isHdOnly) {
            currentHdOnly = s.isHdOnly
            renderHdPill(s.isHdOnly)
        }

        if (s.cacheSizeText.isNotBlank()) {
            cacheSubtitle.text = getString(R.string.settings_cache_size_fmt, s.cacheSizeText)
        }

        renderScanPill(s.scanState)
        scanRelative.text = s.relativeRefreshText
        scanDate.text = s.lastRefreshDateText

        val tileEnabled = s.scanState != ScanState.RUNNING &&
            s.scanState != ScanState.COOLDOWN &&
            s.scanState != ScanState.TRIGGERING
        tileScan.isEnabled = tileEnabled
        tileScan.alpha = if (tileEnabled) 1f else 0.6f

        s.successMessage?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    private fun renderHdPill(on: Boolean) {
        hdPill.isActivated = on
        hdPill.setText(if (on) R.string.settings_on else R.string.settings_off)
    }

    private fun renderScanPill(state: ScanState) {
        val (bg, dot, label) = when (state) {
            ScanState.IDLE, ScanState.COMPLETED -> Triple(
                R.drawable.bg_status_pill_ready,
                R.drawable.dot_ready,
                R.string.settings_scan_status_ready
            )
            ScanState.COOLDOWN -> Triple(
                R.drawable.bg_status_pill_cooldown,
                R.drawable.dot_cooldown,
                R.string.settings_scan_status_cooldown
            )
            ScanState.TRIGGERING, ScanState.RUNNING -> Triple(
                R.drawable.bg_status_pill_running,
                R.drawable.dot_running,
                R.string.settings_scan_status_running
            )
            ScanState.FAILED -> Triple(
                R.drawable.bg_status_pill_unknown,
                R.drawable.dot_unknown,
                R.string.settings_scan_status_unknown
            )
        }
        scanPill.setBackgroundResource(bg)
        scanPillDot.setBackgroundResource(dot)
        scanPillLabel.setText(label)
    }

    private fun showClearCacheDialog() {
        AppConfirmDialog.show(
            context = requireContext(),
            title = getString(R.string.settings_confirm_cache_title),
            message = getString(R.string.settings_confirm_cache_msg),
            positiveLabel = getString(R.string.settings_confirm_cache_yes),
            negativeLabel = getString(R.string.settings_dialog_cancel),
            onConfirm = {
                viewModel.clearCache()
            }
        )
    }

    private fun showResetDialog() {
        AppConfirmDialog.show(
            context = requireContext(),
            title = getString(R.string.settings_confirm_reset_title),
            message = getString(R.string.settings_confirm_reset_msg),
            positiveLabel = getString(R.string.settings_confirm_reset_yes),
            negativeLabel = getString(R.string.settings_dialog_cancel),
            onConfirm = { viewModel.resetAllAndRestart() }
        )
    }

    private fun showAboutDialog() {
        val ctx = requireContext()
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val versionName = pkg.versionName ?: "?"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pkg.longVersionCode
        } else {
            @Suppress("DEPRECATION") pkg.versionCode.toLong()
        }
        val exoVersion = runCatching {
            androidx.media3.common.MediaLibraryInfo.VERSION
        }.getOrDefault("unknown")

        val message = buildString {
            appendLine(getString(R.string.settings_about_version, versionName, versionCode))
            appendLine()
            appendLine(getString(R.string.settings_about_player, exoVersion))
            appendLine(
                getString(
                    R.string.settings_about_device,
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    android.os.Build.VERSION.RELEASE
                )
            )
            appendLine()
            append(getString(R.string.settings_about_credits_tmdb))
        }
        AppConfirmDialog.showInfo(
            context = ctx,
            title = getString(R.string.settings_about_title),
            message = message,
            dismissLabel = getString(R.string.settings_about_dismiss)
        )
    }
}
