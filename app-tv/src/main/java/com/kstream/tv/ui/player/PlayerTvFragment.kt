package com.kstream.tv.ui.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Premium Leanback-style playback surface.
 *
 *  - Hosts an androidx.media3 [PlayerView] attached to PlayerViewModel's PlayerManager.
 *  - Listens to [PlayerUiState] for buffering, link-refresh overlay (P9), errors.
 *  - D-pad CENTER toggles play/pause; BACK exits.
 */
@AndroidEntryPoint
class PlayerTvFragment : Fragment() {

    private val viewModel: PlayerViewModel by viewModels()

    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: TextView
    private lateinit var refreshOverlay: View
    private lateinit var refreshTitle: TextView
    private lateinit var refreshSubtitle: TextView
    private lateinit var refreshFunny: TextView
    private lateinit var errorOverlay: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button

    private val bufferingListener = object : Player.Listener {
        override fun onIsLoadingChanged(isLoading: Boolean) {
            viewModel.onBufferingStateChanged(isLoading)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            viewModel.onBufferingStateChanged(playbackState == Player.STATE_BUFFERING)
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
        loadingOverlay = view.findViewById(R.id.loading_overlay)
        loadingText = view.findViewById(R.id.loading_text)
        refreshOverlay = view.findViewById(R.id.refresh_overlay)
        refreshTitle = view.findViewById(R.id.refresh_title)
        refreshSubtitle = view.findViewById(R.id.refresh_subtitle)
        refreshFunny = view.findViewById(R.id.refresh_funny)
        errorOverlay = view.findViewById(R.id.error_overlay)
        errorText = view.findViewById(R.id.error_text)
        retryButton = view.findViewById(R.id.btn_retry_player)

        val player = viewModel.playerManager.getPlayer()
        playerView.player = player
        playerView.useController = true
        playerView.controllerShowTimeoutMs = 3000
        player.addListener(bufferingListener)

        retryButton.setOnClickListener {
            viewModel.retryRefresh()
        }

        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    val p = viewModel.playerManager.playerOrNull() ?: return@setOnKeyListener false
                    if (p.isPlaying) p.pause() else p.play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    viewModel.playerManager.playerOrNull()?.play(); true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    viewModel.playerManager.playerOrNull()?.pause(); true
                }
                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }
    }

    private fun render(state: PlayerUiState) {
        val showLoading = state.isLoading || state.isBuffering
        loadingOverlay.isVisible = showLoading && !state.showRefreshOverlay && state.loadError == null
        loadingText.text = if (state.isBuffering) "Buffering…" else getString(R.string.player_loading)

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
    }

    override fun onDestroyView() {
        viewModel.playerManager.playerOrNull()?.removeListener(bufferingListener)
        playerView.player = null
        super.onDestroyView()
    }
}
