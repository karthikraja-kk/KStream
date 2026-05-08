package com.kstream.feature.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerRoute(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    BackHandler {
        onBackClick()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.playerManager.getPlayer().pause()
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