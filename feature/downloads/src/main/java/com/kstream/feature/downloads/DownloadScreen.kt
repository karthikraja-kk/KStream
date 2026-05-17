package com.kstream.feature.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kstream.core.model.Download
import com.kstream.core.model.DownloadStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadRoute(
    onBackClick: () -> Unit,
    onMovieClick: (String) -> Unit,
    onWatchClick: (String, String) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    var isSearching by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var downloadToRemove by remember { mutableStateOf<Download?>(null) }

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
                    },
                    actions = {
                        DownloadSortButton(
                            currentSort = sortOption,
                            expanded = showSortMenu,
                            onToggle = { showSortMenu = !showSortMenu },
                            onDismiss = { showSortMenu = false },
                            onSortChange = {
                                viewModel.onSortChange(it)
                                showSortMenu = false
                            }
                        )
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
                        DownloadSortButton(
                            currentSort = sortOption,
                            expanded = showSortMenu,
                            onToggle = { showSortMenu = !showSortMenu },
                            onDismiss = { showSortMenu = false },
                            onSortChange = {
                                viewModel.onSortChange(it)
                                showSortMenu = false
                            }
                        )
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val mainContentModifier = Modifier.fillMaxSize()

            if (downloads.isEmpty() && searchQuery.isEmpty()) {
                Box(
                    modifier = mainContentModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No downloads yet.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (downloads.isEmpty() && searchQuery.isNotEmpty()) {
                Box(
                    modifier = mainContentModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = mainContentModifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(downloads, key = { it.id }) { download ->
                        var fileExists by remember { mutableStateOf(true) }
                        
                        LaunchedEffect(download.localFilePath, download.status) {
                            fileExists = viewModel.checkFileExists(download.localFilePath)
                        }

                        DownloadItem(
                            download = download,
                            fileExists = fileExists,
                            onClick = { onMovieClick(download.movieId) },
                            onWatch = { quality -> onWatchClick(download.movieId, quality) },
                            onRedownload = { viewModel.redownload(download.id) },
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onRemove = { downloadToRemove = download }
                        )
                    }
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
                        downloadToRemove?.let { viewModel.removeDownload(it.id) }
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

@Composable
fun DownloadItem(
    download: Download,
    fileExists: Boolean,
    onClick: () -> Unit,
    onWatch: (String) -> Unit,
    onRedownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
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
                model = download.posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${download.quality} • ${download.fileSize}",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val progressPercent = (download.progress * 100).toInt()
                val statusText = when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        val downloaded = formatBytes(download.downloadedBytes)
                        val total = formatBytes(download.totalBytes)
                        "Downloading... $downloaded / $total ($progressPercent%)"
                    }
                    DownloadStatus.PAUSED -> {
                        val downloaded = formatBytes(download.downloadedBytes)
                        val total = formatBytes(download.totalBytes)
                        "${download.statusMessage ?: "Paused"} $downloaded / $total ($progressPercent%)"
                    }
                    DownloadStatus.COMPLETED -> if (fileExists) "Completed" else "File moved or deleted"
                    DownloadStatus.QUEUED -> "Queued"
                    DownloadStatus.FAILED -> download.statusMessage ?: "Failed"
                    DownloadStatus.DELETED -> "Deleted"
                }
                
                Text(
                    text = statusText, 
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!fileExists && download.status == DownloadStatus.COMPLETED) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (download.status != DownloadStatus.COMPLETED) {
                    LinearProgressIndicator(
                        progress = download.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else if (fileExists) {
                    Button(
                        onClick = { onWatch(download.quality) },
                        modifier = Modifier.padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch Now")
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRedownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Redownload", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (download.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = onPause) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause")
                    }
                } else if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.QUEUED) {
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun DownloadSortButton(
    currentSort: DownloadSortOption,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSortChange: (DownloadSortOption) -> Unit
) {
    Box {
        IconButton(onClick = onToggle) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            DownloadSortOption.entries.forEach { option ->
                val isSelected = option == currentSort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSortChange(option) }
                )
            }
        }
    }
}
