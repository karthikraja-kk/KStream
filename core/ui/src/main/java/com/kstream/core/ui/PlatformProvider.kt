package com.kstream.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class Platform {
    MOBILE, TV
}

val LocalPlatform = staticCompositionLocalOf { Platform.MOBILE }
val LocalLiteMode = compositionLocalOf { false }

@Composable
fun PlatformProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
    val platform = if (isTv) Platform.TV else Platform.MOBILE
    
    CompositionLocalProvider(LocalPlatform provides platform) {
        content()
    }
}
