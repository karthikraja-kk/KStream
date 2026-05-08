package com.kstream.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import com.kstream.core.model.DownloadMetadata
import kotlinx.serialization.json.Json

@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadRoute(
    onBackClick: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val downloadDir = viewModel.getDownloadDir()
        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = "No downloads yet.",
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(downloads, key = { it.request.id }) { download ->
                    DownloadItem(
                        download = download,
                        downloadDir = downloadDir,
                        onPause = { viewModel.pauseDownload(download.request.id) },
                        onResume = { viewModel.resumeDownload(download.request.id) },
                        onRemove = { viewModel.removeDownload(download.request.id) }
                    )
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun DownloadItem(
    download: Download,
    downloadDir: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    val metadata = try {
        Json.decodeFromString<DownloadMetadata>(download.request.data.decodeToString())
    } catch (e: Exception) {
        null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
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
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
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
                
                val statusText = when (download.state) {
                    Download.STATE_DOWNLOADING -> "Downloading... ${download.percentDownloaded.toInt()}%"
                    Download.STATE_COMPLETED -> "Completed"
                    Download.STATE_RESTARTING -> "Restarting..."
                    Download.STATE_FAILED -> "Failed"
                    Download.STATE_STOPPED -> "Paused"
                    Download.STATE_QUEUED -> "Queued"
                    else -> "Unknown"
                }
                
                Text(text = statusText, style = MaterialTheme.typography.labelSmall)
                
                if (download.state != Download.STATE_COMPLETED) {
                    LinearProgressIndicator(
                        progress = download.percentDownloaded / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = "Stored in: $downloadDir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                if (download.state == Download.STATE_DOWNLOADING) {
                    IconButton(onClick = onPause) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Pause")
                    }
                } else if (download.state == Download.STATE_STOPPED) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                }
                
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = if (download.state == Download.STATE_COMPLETED) Icons.Default.Delete else Icons.Default.Clear,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
