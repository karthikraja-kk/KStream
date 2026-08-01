package com.kstream.tv.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.KeyEvent
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
    private lateinit var enginePill: TextView
    private lateinit var tileVideoEngine: View
    private lateinit var scanPill: LinearLayout
    private lateinit var scanPillDot: View
    private lateinit var scanPillLabel: TextView
    private lateinit var scanRelative: TextView
    private lateinit var scanDate: TextView
    private lateinit var cacheSubtitle: TextView

    @Volatile private var isDispatchingScan = false
    private var currentHdOnly: Boolean = false
    private var currentEngineKey: String = "AUTO"
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
        tileVideoEngine = v.findViewById(R.id.tile_video_engine)
        tileCache = v.findViewById(R.id.tile_cache)
        tileAbout = v.findViewById(R.id.tile_about)
        tileTerms = v.findViewById(R.id.tile_terms)
        tileReset = v.findViewById(R.id.tile_reset)
        hdPill = v.findViewById(R.id.tile_hd_pill)
        enginePill = v.findViewById(R.id.tile_engine_pill)
        scanPill = v.findViewById(R.id.scan_status_pill)
        scanPillDot = v.findViewById(R.id.scan_status_dot)
        scanPillLabel = v.findViewById(R.id.scan_status_label)
        scanRelative = v.findViewById(R.id.scan_relative_text)
        scanDate = v.findViewById(R.id.scan_date_text)
        cacheSubtitle = v.findViewById(R.id.tile_cache_sub)
        // Don't auto-pop the IME on focus. The pencil-button click drives
        // enterNameEdit() which explicitly calls showSoftInput; once the
        // user dismisses the keyboard (tick / back), re-focusing the
        // EditText (e.g. via D-pad) must NOT silently re-open it.
        nameEdit.showSoftInputOnFocus = false
    }

    private fun wireTiles() {
        nameAction.setOnClickListener {
            if (isEditingName) commitNameEdit() else enterNameEdit()
        }
        nameEdit.setOnEditorActionListener { _, actionId, event ->
            val isConfirm = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_SEND ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isConfirm) {
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
        tileVideoEngine.setOnClickListener { showVideoEngineChooser() }
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

        if (currentEngineKey != s.videoEngine) {
            currentEngineKey = s.videoEngine
            renderEnginePill(s.videoEngine)
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

    private fun renderEnginePill(engineKey: String) {
        val labelRes = when (engineKey) {
            "EXO" -> R.string.settings_engine_exo
            else -> R.string.settings_engine_auto
        }
        enginePill.setText(labelRes)
        // The "Auto" pill stays inactive; pinned engines get the active treatment
        // so the user sees at a glance that they're overriding the default.
        enginePill.isActivated = (engineKey == "EXO")
    }

    /**
     * Branded chooser dialog (matches the player's quality dropdown look).
     * Each row shows the engine name + a short helper sentence beneath it
     * so the user understands the trade-off before picking.
     */
    private fun showVideoEngineChooser() {
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val current = currentEngineKey

        val title = TextView(ctx).apply {
            setText(R.string.settings_engine_chooser_title)
            setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (10 * dm.density).toInt())
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(
                ctx, R.drawable.bg_search_dropdown_panel
            )
            setPadding(
                (18 * dm.density).toInt(),
                (16 * dm.density).toInt(),
                (18 * dm.density).toInt(),
                (16 * dm.density).toInt()
            )
            addView(title)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setView(container)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0x00000000))

        data class Choice(val key: String, val labelRes: Int, val descRes: Int)
        val choices = listOf(
            Choice("AUTO", R.string.settings_engine_auto, R.string.settings_engine_auto_desc),
            Choice("EXO", R.string.settings_engine_exo, R.string.settings_engine_exo_desc)
        )

        choices.forEachIndexed { idx, c ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                background = androidx.core.content.ContextCompat.getDrawable(
                    ctx, R.drawable.bg_search_dropdown_row
                )
                setPadding(
                    (14 * dm.density).toInt(),
                    (12 * dm.density).toInt(),
                    (14 * dm.density).toInt(),
                    (12 * dm.density).toInt()
                )
                val lp = LinearLayout.LayoutParams(
                    (360 * dm.density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (idx != choices.lastIndex) lp.bottomMargin = (6 * dm.density).toInt()
                layoutParams = lp
            }
            val titleRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val check = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_check)
                val s = (18 * dm.density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = (10 * dm.density).toInt()
                }
                imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                    ctx, R.color.accent_primary
                )
                visibility = if (c.key == current) View.VISIBLE else View.INVISIBLE
            }
            val name = TextView(ctx).apply {
                setText(c.labelRes)
                setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            titleRow.addView(check)
            titleRow.addView(name)

            val desc = TextView(ctx).apply {
                setText(c.descRes)
                setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_tertiary))
                textSize = 11f
                setPadding((28 * dm.density).toInt(), (4 * dm.density).toInt(), 0, 0)
            }

            row.addView(titleRow)
            row.addView(desc)
            row.setOnClickListener {
                if (c.key != currentEngineKey) {
                    viewModel.setVideoEngine(c.key)
                    Toast.makeText(ctx, getString(c.labelRes), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            container.addView(row)
        }

        dialog.show()
        // Focus the currently-selected row so D-pad confirm just re-confirms.
        container.getChildAt(1 + choices.indexOfFirst { it.key == current }.coerceAtLeast(0))
            ?.requestFocus()
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
