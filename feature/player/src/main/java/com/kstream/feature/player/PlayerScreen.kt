package com.kstream.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    
    var controlsVisible by remember { mutableStateOf(true) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

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

    val player = viewModel.playerManager.getPlayer()
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                viewModel.onBufferingStateChanged(playbackState == androidx.media3.common.Player.STATE_BUFFERING)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { controlsVisible = !controlsVisible }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter,
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight -> {
                            controlsVisible = true
                            playerViewRef?.showController()
                            false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    setPlayer(viewModel.playerManager.getPlayer())
                    useController = true
                    keepScreenOn = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == android.view.View.VISIBLE
                    })
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Initial loading overlay
        if (uiState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White
                    )
                }
            }
        }

        if (controlsVisible) {
            val fullscreenFocusRequester = remember { FocusRequester() }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp, end = 16.dp)
            ) {
                var fsBtnFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (fsBtnFocused) Color(0xFFE50914) else Color.Black.copy(alpha = 0.5f))
                        .focusRequester(fullscreenFocusRequester)
                        .focusable()
                        .onFocusChanged { fsBtnFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                            ) {
                                if (isFullscreen) exitFullscreen() else enterFullscreen()
                                true
                            } else false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                var qualBtnFocused by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (qualBtnFocused) Color(0xFFE50914) else Color.Black.copy(alpha = 0.5f))
                            .focusable()
                            .onFocusChanged { qualBtnFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                                ) {
                                    showQualityMenu = true
                                    true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = "Quality", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false }
                    ) {
                        uiState.availableQualities.forEach { quality: String ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = quality,
                                        fontWeight = if (quality == uiState.currentQuality) FontWeight.Bold else FontWeight.Normal,
                                        color = if (quality == uiState.currentQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = {
                                    viewModel.switchQuality(quality)
                                    showQualityMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
        
        var showStatus by remember { mutableStateOf(false) }
        LaunchedEffect(uiState.currentQuality) {
            showStatus = true
            kotlinx.coroutines.delay(2000)
            showStatus = false
        }
        
        if (showStatus) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = CircleShape
            ) {
                Text(
                    text = "Quality: ${uiState.currentQuality}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (uiState.isOffline && !uiState.isPlayingLocal) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
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
                        Button(onClick = { viewModel.retryConnection() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = onGoToDownloads,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                        ) {
                            Text("Go to Downloads")
                        }
                    }
                }
            }
        }

        // Refreshing links overlay — shows funny messages after 2s delay
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

        // Refresh error overlay
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Text("Go Back")
                        }
                        Button(
                            onClick = { viewModel.retryRefresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Try Again", color = Color.White)
                        }
                    }
                }
            }
        }

        // Load error overlay
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Text("Go Back", color = Color.White)
                    }
                }
            }
        }

        // Downloaded file missing overlay
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
                        modifier = Modifier.width(220.dp)
                    ) {
                        Text("Re-download")
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