package com.kstream.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import android.widget.Toast

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerRoute(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showQualityMenu by remember { mutableStateOf(false) }
    
    // Controls visibility tracking
    var controlsVisible by remember { mutableStateOf(true) }
    
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            onBackClick()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.playerManager.getPlayer().pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { controlsVisible = !controlsVisible }
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = viewModel.playerManager.getPlayer()
                    useController = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == android.view.View.VISIBLE
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Quality Selection Button
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 48.dp)
        ) {
            Box {
                IconButton(
                    onClick = { showQualityMenu = true },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Quality", tint = Color.White)
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
        
        // Show current quality briefly when switched
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
    }
}