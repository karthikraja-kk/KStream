package com.kstream.tv.ui.player

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.kstream.feature.player.PlayerUiState
import com.kstream.feature.player.PlayerViewModel
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Custom Leanback playback surface — no media3 built-in controller.
 *
 * UI:
 *  - Top: movie title + quality dropdown (only available qualities)
 *  - Center: play/pause circular button
 *  - Bottom: position / SeekBar / duration (gold accent)
 *  - Center spinner when buffering (no "Buffering" text)
 *
 * D-pad state machine (controls hidden):
 *  - LEFT/RIGHT  → accumulating ±10s seek with [« -Ns] / [+Ns »] indicator, debounced 700ms
 *  - CENTER      → toggle play/pause AND show controls immediately
 *  - any other   → show controls
 *
 * D-pad state machine (controls visible):
 *  - all keys behave as normal navigation/focus, auto-hide reset on each press
 *
 * Auto-hide controls after [AUTO_HIDE_MS] of inactivity.
 *
 * Audio leak fix: player is paused in [onStop] (VM survives backgrounding; only
 * [PlayerViewModel.onCleared] fully releases it).
 */
@AndroidEntryPoint
class PlayerTvFragment : Fragment() {

    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var playerView: PlayerView
    private lateinit var controlsOverlay: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var qualityButton: Button
    private lateinit var qualityPanel: LinearLayout
    private lateinit var playPauseButton: ImageButton
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var bufferingSpinner: ProgressBar
    private lateinit var seekIndicatorLeft: TextView
    private lateinit var seekIndicatorRight: TextView

    private lateinit var loadingOverlay: View
    private lateinit var refreshOverlay: View
    private lateinit var refreshTitle: TextView
    private lateinit var refreshSubtitle: TextView
    private lateinit var refreshFunny: TextView
    private lateinit var errorOverlay: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button

    private var controlsVisible = false
    private var pendingSeekMs = 0L
    private var seekCommitJob: Job? = null
    private var autoHideJob: Job? = null
    private var progressJob: Job? = null
    private var availableQualities: List<String> = emptyList()
    private var currentQuality: String = ""
    private var userIsDraggingSeek = false
    private var wasPlayingBeforeStop = false

    private val playerListener = object : Player.Listener {
        override fun onIsLoadingChanged(isLoading: Boolean) {
            viewModel.onBufferingStateChanged(isLoading)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            viewModel.onBufferingStateChanged(playbackState == Player.STATE_BUFFERING)
            updateBufferingSpinner()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player_tv, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playerView = view.findViewById(R.id.player_view)
        controlsOverlay = view.findViewById(R.id.controls_overlay)
        titleText = view.findViewById(R.id.player_title)
        qualityButton = view.findViewById(R.id.btn_quality)
        qualityPanel = view.findViewById(R.id.quality_panel)
        playPauseButton = view.findViewById(R.id.btn_play_pause)
        positionText = view.findViewById(R.id.player_position)
        durationText = view.findViewById(R.id.player_duration)
        seekBar = view.findViewById(R.id.player_seekbar)
        bufferingSpinner = view.findViewById(R.id.buffering_spinner)
        seekIndicatorLeft = view.findViewById(R.id.seek_indicator_left)
        seekIndicatorRight = view.findViewById(R.id.seek_indicator_right)

        loadingOverlay = view.findViewById(R.id.loading_overlay)
        refreshOverlay = view.findViewById(R.id.refresh_overlay)
        refreshTitle = view.findViewById(R.id.refresh_title)
        refreshSubtitle = view.findViewById(R.id.refresh_subtitle)
        refreshFunny = view.findViewById(R.id.refresh_funny)
        errorOverlay = view.findViewById(R.id.error_overlay)
        errorText = view.findViewById(R.id.error_text)
        retryButton = view.findViewById(R.id.btn_retry_player)

        val player = viewModel.playerManager.getPlayer()
        playerView.player = player
        playerView.useController = false
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        player.addListener(playerListener)

        playPauseButton.setOnClickListener { togglePlayPause() }
        qualityButton.setOnClickListener { showQualityMenu() }
        retryButton.setOnClickListener { viewModel.retryRefresh() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player.duration.coerceAtLeast(0L)
                    val target = (duration * progress / 1000L)
                    positionText.text = formatTime(target)
                    resetAutoHide()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userIsDraggingSeek = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                userIsDraggingSeek = false
                val duration = player.duration.coerceAtLeast(0L)
                val target = (duration * (sb?.progress ?: 0) / 1000L)
                player.seekTo(target)
            }
        })

        // Initial state — controls hidden so the user lands on a clean video surface.
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
        updatePlayPauseIcon()
        updateBufferingSpinner()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }

        startProgressLoop()
    }

    private fun render(state: PlayerUiState) {
        loadingOverlay.isVisible = state.isLoading && state.loadError == null && !state.showRefreshOverlay

        refreshOverlay.isVisible = state.showRefreshOverlay
        if (state.showRefreshOverlay) {
            refreshTitle.text = getString(R.string.player_link_refresh_title)
            refreshSubtitle.text = getString(R.string.player_link_refresh_subtitle)
            refreshFunny.text = state.funnyMessage.orEmpty()
        }

        val err = state.loadError ?: state.refreshError
        errorOverlay.isVisible = err != null
        if (err != null) {
            errorText.text = err
            retryButton.requestFocus()
        }

        titleText.text = state.movieTitle
        availableQualities = state.availableQualities
        currentQuality = state.currentQuality
        qualityButton.text = if (currentQuality.isNotEmpty()) "$currentQuality  ▾" else "Quality  ▾"
        qualityButton.isEnabled = availableQualities.size > 1

        updateBufferingSpinner()
    }

    // ------------------------------------------------------------------
    // Key dispatch — called from PlayerActivity.dispatchKeyEvent.
    // ------------------------------------------------------------------
    fun onKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        // Don't intercept while overlays own focus.
        if (errorOverlay.isVisible || refreshOverlay.isVisible || loadingOverlay.isVisible) {
            return false
        }

        return if (controlsVisible) {
            // While controls are showing the seekbar / buttons own focus.
            // SeekBar's built-in D-pad handler only updates the progress
            // value, it never commits a real seek — so we hijack
            // LEFT/RIGHT when the seekbar is focused and route through
            // our debounced addSeek() path (which calls player.seekTo).
            resetAutoHide()
            if (seekBar.hasFocus()) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                        addSeek(-SEEK_STEP_COARSE_MS); return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                        addSeek(SEEK_STEP_COARSE_MS); return true
                    }
                }
            }
            false
        } else {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                    addSeek(-SEEK_STEP_FINE_MS); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                    addSeek(SEEK_STEP_FINE_MS); true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    togglePlayPause(); showControls(); true
                }
                KeyEvent.KEYCODE_BACK -> false // let activity handle BACK
                else -> { showControls(); true }
            }
        }
    }

    /** Called from PlayerActivity when BACK pressed while controls visible. */
    fun hideControlsForBack(): Boolean {
        if (::qualityPanel.isInitialized && qualityPanel.isVisible) {
            hideQualityPanel()
            return true
        }
        if (!controlsVisible) return false
        hideControls()
        return true
    }

    // ------------------------------------------------------------------
    // Seek accumulator with debounced commit.
    // ------------------------------------------------------------------
    private fun addSeek(deltaMs: Long) {
        pendingSeekMs += deltaMs
        showSeekIndicator(pendingSeekMs)
        seekCommitJob?.cancel()
        seekCommitJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SEEK_DEBOUNCE_MS)
            commitPendingSeek()
        }
    }

    private fun commitPendingSeek() {
        val player = viewModel.playerManager.playerOrNull() ?: run {
            pendingSeekMs = 0L; hideSeekIndicators(); return
        }
        val delta = pendingSeekMs
        pendingSeekMs = 0L
        if (delta == 0L) { hideSeekIndicators(); return }
        val duration = player.duration.coerceAtLeast(0L)
        val target = (player.currentPosition + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        // Keep indicator visible briefly after commit, then fade.
        viewLifecycleOwner.lifecycleScope.launch {
            delay(400)
            hideSeekIndicators()
        }
    }

    private fun showSeekIndicator(totalMs: Long) {
        val absMs = kotlin.math.abs(totalMs)
        // Show minutes for coarse scrubs (≥ 60 s), seconds otherwise.
        val (value, useMinutes) = if (absMs >= 60_000L) {
            (totalMs / 60_000L).toInt() to true
        } else {
            (totalMs / 1000L).toInt() to false
        }
        if (value >= 0) {
            val res = if (useMinutes) R.string.player_seek_fwd_min else R.string.player_seek_fwd_sec
            seekIndicatorRight.text = getString(res, value.coerceAtLeast(0))
            seekIndicatorRight.visibility = View.VISIBLE
            seekIndicatorLeft.visibility = View.GONE
        } else {
            val res = if (useMinutes) R.string.player_seek_back_min else R.string.player_seek_back_sec
            seekIndicatorLeft.text = getString(res, (-value))
            seekIndicatorLeft.visibility = View.VISIBLE
            seekIndicatorRight.visibility = View.GONE
        }
    }

    private fun hideSeekIndicators() {
        seekIndicatorLeft.visibility = View.GONE
        seekIndicatorRight.visibility = View.GONE
    }

    // ------------------------------------------------------------------
    // Controls visibility.
    // ------------------------------------------------------------------
    private fun showControls() {
        if (!controlsVisible) {
            controlsOverlay.visibility = View.VISIBLE
            controlsVisible = true
        }
        // Always land focus on play/pause for predictable D-pad UX.
        playPauseButton.requestFocus()
        resetAutoHide()
    }

    private fun hideControls() {
        autoHideJob?.cancel()
        if (::qualityPanel.isInitialized && qualityPanel.isVisible) {
            qualityPanel.visibility = View.GONE
            qualityPanel.removeAllViews()
        }
        controlsOverlay.visibility = View.GONE
        controlsVisible = false
    }

    private fun resetAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(AUTO_HIDE_MS)
            hideControls()
        }
    }

    // ------------------------------------------------------------------
    // Play / pause / quality menu.
    // ------------------------------------------------------------------
    private fun togglePlayPause() {
        val player = viewModel.playerManager.playerOrNull() ?: return
        if (player.isPlaying) player.pause() else player.play()
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        if (!::playPauseButton.isInitialized) return
        val player = viewModel.playerManager.playerOrNull()
        val isPlaying = player?.isPlaying == true
        playPauseButton.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        playPauseButton.contentDescription =
            getString(if (isPlaying) R.string.player_pause else R.string.player_play)
    }

    private fun updateBufferingSpinner() {
        if (!::bufferingSpinner.isInitialized) return
        val player = viewModel.playerManager.playerOrNull()
        val buffering = player?.playbackState == Player.STATE_BUFFERING
        bufferingSpinner.isVisible = buffering && !loadingOverlay.isVisible &&
            !refreshOverlay.isVisible && !errorOverlay.isVisible
    }

    private fun showQualityMenu() {
        if (availableQualities.size <= 1) return
        if (qualityPanel.isVisible) {
            hideQualityPanel()
            return
        }
        qualityPanel.removeAllViews()
        availableQualities.forEachIndexed { idx, q ->
            val isLast = idx == availableQualities.lastIndex
            val row = makeQualityRow(q, q == currentQuality, isLast) {
                if (q != currentQuality) viewModel.switchQuality(q)
                hideQualityPanel()
                resetAutoHide()
            }
            qualityPanel.addView(row)
        }
        qualityPanel.visibility = View.VISIBLE
        qualityPanel.getChildAt(0)?.requestFocus()
        resetAutoHide()
    }

    private fun hideQualityPanel() {
        if (!qualityPanel.isVisible) return
        qualityPanel.visibility = View.GONE
        qualityPanel.removeAllViews()
        qualityButton.requestFocus()
    }

    /** Branded dropdown row matching the search-by / sort-by panels. */
    private fun makeQualityRow(
        label: String,
        selected: Boolean,
        isLast: Boolean,
        onSelect: () -> Unit
    ): View {
        val ctx = requireContext()
        val dm = resources.displayMetrics
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_search_dropdown_row)
            setPadding(
                (12 * dm.density).toInt(),
                (10 * dm.density).toInt(),
                (12 * dm.density).toInt(),
                (10 * dm.density).toInt()
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (!isLast) lp.bottomMargin = (2 * dm.density).toInt()
            layoutParams = lp
        }
        val check = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_check)
            val s = (18 * dm.density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                marginEnd = (10 * dm.density).toInt()
            }
            imageTintList = ContextCompat.getColorStateList(ctx, R.color.accent_primary)
            visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
        val tv = TextView(ctx).apply {
            text = label
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14f
        }
        row.addView(check)
        row.addView(tv)
        row.setOnClickListener { onSelect() }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                hideQualityPanel()
                true
            } else false
        }
        return row
    }

    // ------------------------------------------------------------------
    // Progress / time text loop.
    // ------------------------------------------------------------------
    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val player = viewModel.playerManager.playerOrNull()
                    if (player != null) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        positionText.text = formatTime(pos)
                        durationText.text = formatTime(dur)
                        if (!userIsDraggingSeek && dur > 0) {
                            seekBar.progress = (pos * 1000L / dur).toInt()
                        }
                        val buffered = player.bufferedPosition.coerceAtLeast(0L)
                        if (dur > 0) {
                            seekBar.secondaryProgress = (buffered * 1000L / dur).toInt()
                        }
                    }
                    delay(500)
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    // ------------------------------------------------------------------
    // Lifecycle — pause player when backgrounded to stop audio leak.
    // ------------------------------------------------------------------
    override fun onStop() {
        super.onStop()
        val player = viewModel.playerManager.playerOrNull() ?: return
        wasPlayingBeforeStop = player.isPlaying
        if (player.isPlaying) player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (wasPlayingBeforeStop) {
            viewModel.playerManager.playerOrNull()?.play()
            wasPlayingBeforeStop = false
        }
        updatePlayPauseIcon()
    }

    override fun onDestroyView() {
        autoHideJob?.cancel()
        seekCommitJob?.cancel()
        progressJob?.cancel()
        viewModel.playerManager.playerOrNull()?.removeListener(playerListener)
        playerView.player = null
        super.onDestroyView()
    }

    companion object {
        // Coarse: when the seekbar itself is focused — D-pad LEFT/RIGHT
        // each accumulate 3 min so the user can scrub a long film fast.
        private const val SEEK_STEP_COARSE_MS = 180_000L
        // Fine: when controls are hidden (or focus is on a non-seekbar
        // control) — D-pad LEFT/RIGHT each accumulate 10 s for precise
        // nudges. Both paths share the same debounced commit.
        private const val SEEK_STEP_FINE_MS = 10_000L
        private const val SEEK_DEBOUNCE_MS = 700L
        private const val AUTO_HIDE_MS = 5_000L
    }
}
