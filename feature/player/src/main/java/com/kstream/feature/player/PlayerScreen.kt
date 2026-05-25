package com.kstream.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import android.widget.Toast
import android.app.Activity
import android.content.pm.ActivityInfo

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.tvFocusBorder
import kotlinx.coroutines.delay

private val BrandRed = Color(0xFFE50914)

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerRoute(
    onBackClick: () -> Unit,
    onGoToDownloads: () -> Unit,
    startOver: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(startOver) {
        if (startOver) {
            viewModel.clearWatchProgress()
        }
    }
    var lastBackPressTime by rememberSaveable { mutableLongStateOf(0L) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val isTV = LocalPlatform.current == Platform.TV
    var controlsVisible by remember { mutableStateOf(true) }
    var hideTimerKey by remember { mutableLongStateOf(0L) }

    // Playback state tracking
    val player = viewModel.playerManager.getPlayer()
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekAccumulated by remember { mutableLongStateOf(0L) }
    var seekIndicatorVisible by remember { mutableStateOf(false) }
    var seekIndicatorKey by remember { mutableLongStateOf(0L) }

    // Poll player state
    LaunchedEffect(Unit) {
        while (true) {
            if (!isSeeking) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
            }
            duration = player.duration.coerceAtLeast(0L)
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(controlsVisible, hideTimerKey) {
        if (controlsVisible && !isSeeking) {
            delay(4000)
            controlsVisible = false
        }
    }

    // Auto-hide seek indicator after 1 second of no input
    LaunchedEffect(seekIndicatorKey) {
        if (seekIndicatorVisible) {
            delay(1000)
            seekIndicatorVisible = false
            seekAccumulated = 0L
        }
    }

    // Focus requester for TV
    val playPauseFocusRequester = remember { FocusRequester() }
    val playerBoxFocusRequester = remember { FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (isTV) {
            delay(150)
            if (controlsVisible) {
                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
            } else {
                try { playerBoxFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    fun resetHideTimer() { hideTimerKey = System.currentTimeMillis() }

    fun enterFullscreen() {
        isFullscreen = true
        val activity = context as? Activity ?: return
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun exitFullscreen() {
        isFullscreen = false
        val activity = context as? Activity ?: return
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    LaunchedEffect(isFullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window
        if (!isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    BackHandler {
        if (isFullscreen) {
            exitFullscreen()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                onBackClick()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.playerManager.getPlayer().pause()
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.playerManager.getPlayer().pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.onBufferingStateChanged(playbackState == androidx.media3.common.Player.STATE_BUFFERING)
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // ─── Main player area ─────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerBoxFocusRequester)
            .pointerInput(Unit) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                    if (controlsVisible) resetHideTimer()
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                resetHideTimer()
                                true
                            } else false
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                resetHideTimer()
                                true
                            } else {
                                resetHideTimer()
                                false
                            }
                        }
                        Key.DirectionLeft -> {
                            if (!controlsVisible) {
                                val newPos = (currentPosition - 10_000L).coerceAtLeast(0L)
                                currentPosition = newPos
                                player.seekTo(newPos)
                                seekAccumulated -= 10
                                seekIndicatorVisible = true
                                seekIndicatorKey = System.currentTimeMillis()
                                true
                            } else {
                                resetHideTimer()
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!controlsVisible) {
                                val newPos = (currentPosition + 10_000L).coerceAtMost(duration)
                                currentPosition = newPos
                                player.seekTo(newPos)
                                seekAccumulated += 10
                                seekIndicatorVisible = true
                                seekIndicatorKey = System.currentTimeMillis()
                                true
                            } else {
                                resetHideTimer()
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        // Video surface
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    setPlayer(viewModel.playerManager.getPlayer())
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Seek indicator overlay
        AnimatedVisibility(
            visible = seekIndicatorVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (seekAccumulated >= 0) "⏩ +${seekAccumulated}s" else "⏪ ${seekAccumulated}s",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ─── Custom controls overlay ──────────────────────────────────────────
        AnimatedVisibility(
            visible = controlsVisible && !uiState.isLoading && uiState.loadError == null &&
                    uiState.refreshError == null && !uiState.localFileMissing &&
                    !(uiState.isOffline && !uiState.isPlayingLocal),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient + controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .then(
                                    if (isTV) Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                                    else Modifier
                                )
                                .clickable { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        // Movie title
                        Text(
                            text = uiState.movieTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // Quality badge
                        if (uiState.currentQuality.isNotBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = uiState.currentQuality,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Center controls (skip back, play/pause, skip forward)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip back 10s
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .then(
                                if (isTV) Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                                else Modifier
                            )
                            .clickable {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                resetHideTimer()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Skip back 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play/Pause (large)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .then(
                                if (isTV) Modifier
                                    .focusRequester(playPauseFocusRequester)
                                    .tvFocusBorder(shape = RoundedCornerShape(50))
                                else Modifier
                            )
                            .clickable {
                                if (player.isPlaying) player.pause() else player.play()
                                resetHideTimer()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Skip forward 10s
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .then(
                                if (isTV) Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                                else Modifier
                            )
                            .clickable {
                                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(player.duration))
                                resetHideTimer()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "Skip forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom gradient + seek bar + buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Seek bar
                    if (duration > 0) {
                        val progress = currentPosition.toFloat() / duration.toFloat()
                        val buffered = bufferedPosition.toFloat() / duration.toFloat()

                        var seekBarFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .then(
                                    if (isTV) Modifier
                                        .onFocusChanged { seekBarFocused = it.isFocused }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionLeft -> {
                                                        val newPos = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                                        player.seekTo(newPos)
                                                        currentPosition = newPos
                                                        resetHideTimer()
                                                        true
                                                    }
                                                    Key.DirectionRight -> {
                                                        val newPos = (player.currentPosition + 10_000L).coerceAtMost(player.duration)
                                                        player.seekTo(newPos)
                                                        currentPosition = newPos
                                                        resetHideTimer()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .focusable()
                                    else Modifier
                                )
                        ) {
                            // Buffered track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(buffered.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.3f))
                            )
                            // Slider
                            Slider(
                                value = progress.coerceIn(0f, 1f),
                                onValueChange = { value ->
                                    isSeeking = true
                                    currentPosition = (value * duration).toLong()
                                    resetHideTimer()
                                },
                                onValueChangeFinished = {
                                    player.seekTo(currentPosition)
                                    isSeeking = false
                                },
                                enabled = !isTV,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = if (seekBarFocused) Color.White else BrandRed,
                                    activeTrackColor = if (seekBarFocused) BrandRed.copy(alpha = 1f) else BrandRed,
                                    inactiveTrackColor = if (seekBarFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
                                    disabledThumbColor = if (seekBarFocused) Color.White else BrandRed,
                                    disabledActiveTrackColor = if (seekBarFocused) BrandRed.copy(alpha = 1f) else BrandRed,
                                    disabledInactiveTrackColor = if (seekBarFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }

                    // Time + controls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Elapsed / Duration
                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Quality selector
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .then(
                                        if (isTV) Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                                        else Modifier
                                    )
                                    .clickable { showQualityMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Quality",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = { showQualityMenu = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                uiState.availableQualities.forEach { quality ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = quality,
                                                fontWeight = if (quality == uiState.currentQuality) FontWeight.Bold else FontWeight.Normal,
                                                color = if (quality == uiState.currentQuality) BrandRed else Color.White
                                            )
                                        },
                                        onClick = {
                                            viewModel.switchQuality(quality)
                                            showQualityMenu = false
                                        },
                                        modifier = Modifier.background(Color.Transparent)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Fullscreen toggle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .then(
                                    if (isTV) Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                                    else Modifier
                                )
                                .clickable { if (isFullscreen) exitFullscreen() else enterFullscreen() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // ─── Mid-playback buffering spinner ───────────────────────────────────
        if (uiState.isBuffering && !uiState.isLoading && !uiState.isOffline) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = BrandRed,
                    strokeWidth = 3.dp
                )
            }
        }

        // ─── Initial loading overlay ──────────────────────────────────────────
        if (uiState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = BrandRed
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = uiState.movieTitle.ifBlank { "Loading…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Preparing your stream…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // ─── Offline overlay ──────────────────────────────────────────────────
        if (uiState.isOffline && !uiState.isPlayingLocal) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Connection Lost",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Will resume when connection is back",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    if (uiState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { viewModel.retryConnection() },
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = onGoToDownloads,
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                        ) {
                            Text("Go to Downloads")
                        }
                    }
                }
            }
        }

        // ─── Refreshing links overlay ─────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.showRefreshOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    AnimatedContent(
                        targetState = uiState.funnyMessage ?: "",
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) +
                                    slideInVertically(
                                        animationSpec = tween(500),
                                        initialOffsetY = { it / 2 }
                                    )).togetherWith(
                                fadeOut(animationSpec = tween(300)) +
                                        slideOutVertically(
                                            animationSpec = tween(300),
                                            targetOffsetY = { -it / 2 }
                                        )
                            )
                        },
                        label = "funnyMessage"
                    ) { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // ─── Refresh error overlay ────────────────────────────────────────────
        if (uiState.refreshError != null && !uiState.isRefreshingLinks) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Couldn't get a working link",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The stream link couldn't be refreshed right now. You can try again or go back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onBackClick,
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Text("Go Back")
                        }
                        Button(
                            onClick = { viewModel.retryRefresh() },
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50)),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Try Again", color = Color.White)
                        }
                    }
                }
            }
        }

        // ─── Load error overlay ───────────────────────────────────────────────
        if (uiState.loadError != null && uiState.refreshError == null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.loadError ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Text("Go Back", color = Color.White)
                    }
                }
            }
        }

        // ─── Downloaded file missing overlay ──────────────────────────────────
        if (uiState.localFileMissing) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Downloaded file not found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The downloaded file may have been moved or deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onGoToDownloads,
                        modifier = Modifier.width(220.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Text("Re-download", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.watchOnline() },
                        modifier = Modifier.width(220.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Text("Watch Online")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.width(220.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}