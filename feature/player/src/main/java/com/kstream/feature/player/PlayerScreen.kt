package com.kstream.feature.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    DisposableEffect(Unit) {
        onDispose {
            // progress is saved in ViewModel.onCleared
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = viewModel.playerManager.getPlayer()
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
