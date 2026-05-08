package com.kstream.feature.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import com.kstream.core.model.DownloadMetadata
import kotlinx.serialization.json.Json
import java.util.Locale

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadRoute(
    onBackClick: () -> Unit,
    onMovieClick: (String) -> Unit,
    onWatchClick: (String, String) -> Unit,
    scrollMovieId: String? = null,
    scrollQuality: String? = null,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    
    var downloadToRemove by remember { mutableStateOf<Download?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(downloads, scrollMovieId, scrollQuality) {
        if (downloads.isNotEmpty() && scrollMovieId != null) {
            val index = downloads.indexOfFirst { d ->
                try {
                    val metadata = Json.decodeFromString<DownloadMetadata>(d.request.data.decodeToString())
                    d.request.id.startsWith(scrollMovieId) && (scrollQuality == null || metadata.quality == scrollQuality)
                } catch (e: Exception) {
                    false
                }
            }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    BackHandler {
        if (isSearching) {
            isSearching = false
            viewModel.onSearchQueryChange("")
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search downloads...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.textFieldColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearching = false
                            viewModel.onSearchQueryChange("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Downloads") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            }
        }
    ) { padding ->
        val downloadDir = viewModel.getDownloadDir()
        if (downloads.isEmpty() && searchQuery.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = "No downloads yet.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (downloads.isEmpty() && searchQuery.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = "No results found for \"$searchQuery\"",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(downloads, key = { it.request.id }) { download ->
                    val bytesRemaining = download.contentLength - download.bytesDownloaded
                    val remainingTimeMs = if (download.state == Download.STATE_DOWNLOADING) {
                        viewModel.getRemainingTime(download.request.id, bytesRemaining)
                    } else -1L

                    DownloadItem(
                        download = download,
                        downloadDir = downloadDir,
                        remainingTime = formatRemainingTime(remainingTimeMs),
                        onClick = { onMovieClick(download.request.id.substringBefore("_")) },
                        onWatch = { quality -> onWatchClick(download.request.id.substringBefore("_"), quality) },
                        onPause = { viewModel.pauseDownload(download.request.id) },
                        onResume = { viewModel.resumeDownload(download.request.id) },
                        onRemove = { downloadToRemove = download }
                    )
                }
            }
        }
    }

    if (downloadToRemove != null) {
        AlertDialog(
            onDismissRequest = { downloadToRemove = null },
            title = { Text("Delete Download") },
            text = { Text("Are you sure you want to delete this download? This action is not recoverable.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadToRemove?.let { viewModel.removeDownload(it.request.id) }
                        downloadToRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun DownloadItem(
    download: Download,
    downloadDir: String,
    remainingTime: String,
    onClick: () -> Unit,
    onWatch: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    val metadata = try {
        Json.decodeFromString<DownloadMetadata>(download.request.data.decodeToString())
    } catch (e: Exception) {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = metadata?.posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata?.movieName ?: "Unknown Movie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${metadata?.quality} • ${metadata?.fileSize}",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val downloadedSize = formatBytes(download.bytesDownloaded)
                val totalSize = if (download.contentLength > 0) formatBytes(download.contentLength) else "Unknown"
                
                val statusText = when (download.state) {
                    Download.STATE_DOWNLOADING -> {
                        var text = "Downloading... ${if (download.percentDownloaded >= 0) download.percentDownloaded.toInt() else 0}% ($downloadedSize / $totalSize)"
                        if (remainingTime.isNotEmpty()) text += "\n$remainingTime remaining"
                        text
                    }
                    Download.STATE_COMPLETED -> "Completed ($totalSize)"
                    Download.STATE_RESTARTING -> "Restarting..."
                    Download.STATE_FAILED -> "Failed"
                    Download.STATE_STOPPED -> "Paused ($downloadedSize / $totalSize)"
                    Download.STATE_QUEUED -> "Queued"
                    else -> "Unknown"
                }
                
                Text(text = statusText, style = MaterialTheme.typography.labelSmall)
                
                if (download.state != Download.STATE_COMPLETED) {
                    LinearProgressIndicator(
                        progress = if (download.percentDownloaded >= 0) download.percentDownloaded / 100f else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Button(
                        onClick = { onWatch(metadata?.quality ?: "") },
                        modifier = Modifier.padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch Now")
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (download.state == Download.STATE_DOWNLOADING) {
                    IconButton(onClick = onPause) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Pause")
                    }
                } else if (download.state == Download.STATE_STOPPED) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                }
                
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

fun formatRemainingTime(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
