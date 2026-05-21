package com.kstream.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/** Classified connectivity / error category so callers can offer the right action. */
enum class ConnectivityType { OFFLINE, TIMEOUT, SERVER, FORBIDDEN, NOT_FOUND, GENERIC }

/** Detect the class of error from a user-facing error message string. */
fun String.detectConnectivityType(): ConnectivityType = when {
    contains("No internet", ignoreCase = true) || contains("Network error", ignoreCase = true) -> ConnectivityType.OFFLINE
    contains("timed out", ignoreCase = true) -> ConnectivityType.TIMEOUT
    contains("temporarily unavailable", ignoreCase = true) || contains("Unable to connect", ignoreCase = true) -> ConnectivityType.SERVER
    contains("Access denied", ignoreCase = true) || contains("Secure connection", ignoreCase = true) -> ConnectivityType.FORBIDDEN
    contains("not found", ignoreCase = true) || contains("been removed", ignoreCase = true) -> ConnectivityType.NOT_FOUND
    else -> ConnectivityType.GENERIC
}

@Composable
fun AppLoadingScreen(
    title: String,
    message: String,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    useSkeleton: Boolean = true
) {
    if (useSkeleton) {
        AppSkeletonLoadingScreen(isTv = isTv, title = title, modifier = modifier)
    } else if (isTv) {
        TvLoadingScreen(title = title, message = message, modifier = modifier)
    } else {
        LoadingScreen(title = title, message = message, modifier = modifier)
    }
}

/**
 * Connectivity-aware error screen. Automatically adjusts secondary action:
 * - OFFLINE → offers "Open Downloads"
 * - FORBIDDEN / NOT_FOUND → no retry, only "Go Back"
 * - Others → Retry primary, optional secondary
 */
@Composable
fun AppErrorScreen(
    title: String,
    message: String,
    isTv: Boolean,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.ErrorOutline,
    onOpenDownloads: (() -> Unit)? = null,
    connectivityType: ConnectivityType? = null,
    modifier: Modifier = Modifier
) {
    val effectiveConnectivityType = connectivityType ?: message.detectConnectivityType()

    val (effectiveIcon, effectivePrimary, effectivePrimaryLabel, effectiveSecondary, effectiveSecondaryLabel) = when (effectiveConnectivityType) {
        ConnectivityType.OFFLINE -> ErrorScreenConfig(
            icon = Icons.Default.CloudOff,
            primaryAction = if (onOpenDownloads != null) onOpenDownloads else onPrimaryAction,
            primaryLabel = if (onOpenDownloads != null) "Open Downloads" else primaryActionLabel,
            secondaryAction = if (onOpenDownloads != null) onPrimaryAction else null,
            secondaryLabel = if (onOpenDownloads != null) "Retry" else null
        )
        ConnectivityType.FORBIDDEN -> ErrorScreenConfig(
            icon = Icons.Default.Warning,
            primaryAction = onPrimaryAction,
            primaryLabel = primaryActionLabel,
            secondaryAction = null, secondaryLabel = null
        )
        ConnectivityType.NOT_FOUND -> ErrorScreenConfig(
            icon = Icons.Default.Info,
            primaryAction = onPrimaryAction,
            primaryLabel = primaryActionLabel,
            secondaryAction = null, secondaryLabel = null
        )
        ConnectivityType.TIMEOUT, ConnectivityType.SERVER -> ErrorScreenConfig(
            icon = Icons.Default.ErrorOutline,
            primaryAction = onPrimaryAction,
            primaryLabel = primaryActionLabel,
            secondaryAction = onSecondaryAction,
            secondaryLabel = secondaryActionLabel
        )
        ConnectivityType.GENERIC -> ErrorScreenConfig(
            icon = icon,
            primaryAction = onPrimaryAction,
            primaryLabel = primaryActionLabel,
            secondaryAction = onSecondaryAction,
            secondaryLabel = secondaryActionLabel
        )
    }

    if (isTv) {
        TvStatusScreen(
            icon = effectiveIcon,
            title = title,
            message = message,
            primaryActionLabel = effectivePrimaryLabel,
            onPrimaryAction = effectivePrimary,
            secondaryActionLabel = effectiveSecondaryLabel,
            onSecondaryAction = effectiveSecondary,
            modifier = modifier
        )
    } else {
        StatusScreen(
            icon = effectiveIcon,
            title = title,
            message = message,
            primaryActionLabel = effectivePrimaryLabel,
            onPrimaryAction = effectivePrimary,
            secondaryActionLabel = effectiveSecondaryLabel,
            onSecondaryAction = effectiveSecondary,
            modifier = modifier
        )
    }
}

private data class ErrorScreenConfig(
    val icon: ImageVector,
    val primaryAction: () -> Unit,
    val primaryLabel: String,
    val secondaryAction: (() -> Unit)?,
    val secondaryLabel: String?
)

@Composable
fun AppEmptyScreen(
    title: String,
    message: String,
    isTv: Boolean,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.Info,
    modifier: Modifier = Modifier
) {
    if (isTv) {
        TvStatusScreen(
            icon = icon,
            title = title,
            message = message,
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
            modifier = modifier
        )
    } else {
        StatusScreen(
            icon = icon,
            title = title,
            message = message,
            primaryActionLabel = primaryActionLabel,
            onPrimaryAction = onPrimaryAction,
            secondaryActionLabel = secondaryActionLabel,
            onSecondaryAction = onSecondaryAction,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvLoadingScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(32.dp))
        TvText(text = title, style = TvMaterialTheme.typography.titleLarge, color = TvMaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))
        TvText(
            text = message,
            style = TvMaterialTheme.typography.bodyLarge,
            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusScreen(
    icon: ImageVector,
    title: String,
    message: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPrimaryAction) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(primaryActionLabel)
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                OutlinedButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvStatusScreen(
    icon: ImageVector,
    title: String,
    message: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2A2A2A))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        TvText(text = title, style = TvMaterialTheme.typography.displaySmall, color = TvMaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        TvText(
            text = message,
            style = TvMaterialTheme.typography.bodyLarge,
            color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.tv.material3.Button(onClick = onPrimaryAction) {
                TvText(primaryActionLabel)
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                androidx.tv.material3.OutlinedButton(onClick = onSecondaryAction) {
                    TvText(secondaryActionLabel)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Skeleton / shimmer loading screen
// ---------------------------------------------------------------------------

@Composable
fun AppSkeletonLoadingScreen(
    isTv: Boolean,
    title: String = "",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    // Animate the horizontal offset of the shimmer sweep (0 → 1 → 0)
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val tileW: Dp = if (isTv) 240.dp else 120.dp
    val tileH: Dp = if (isTv) 160.dp else 90.dp
    val hPad: Dp = if (isTv) 48.dp else 16.dp
    val vPad: Dp = if (isTv) 48.dp else 16.dp

    /** Build a sweeping gradient brush for a placeholder of the given [width]. */
    @Composable
    fun shimmerBrush(width: Float = 300f): Brush {
        val sweepX = shimmerOffset * width
        return Brush.linearGradient(
            colors = listOf(
                Color(0xFF2A2A2A),
                Color(0xFF3D3D3D),
                Color(0xFF4A4A4A),
                Color(0xFF3D3D3D),
                Color(0xFF2A2A2A)
            ),
            start = Offset(sweepX - width * 0.5f, 0f),
            end = Offset(sweepX + width * 0.5f, 0f)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = hPad, vertical = vPad)
    ) {
        if (title.isNotBlank()) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(if (isTv) 28.dp else 20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush(200f))
            )
            Spacer(modifier = Modifier.height(if (isTv) 24.dp else 16.dp))
        }

        repeat(if (isTv) 2 else 3) {
            // Section title placeholder
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(if (isTv) 22.dp else 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush(160f))
            )
            Spacer(modifier = Modifier.height(if (isTv) 16.dp else 12.dp))

            // Tile row placeholder
            Row(horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 8.dp)) {
                repeat(if (isTv) 4 else 3) {
                    Box(
                        modifier = Modifier
                            .width(tileW)
                            .height(tileH)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerBrush(tileW.value))
                    )
                }
            }
            Spacer(modifier = Modifier.height(if (isTv) 40.dp else 24.dp))
        }
    }
}
