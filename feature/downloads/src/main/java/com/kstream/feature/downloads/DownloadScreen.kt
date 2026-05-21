package com.kstream.feature.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kstream.core.model.Download
import com.kstream.core.model.DownloadStatus
import com.kstream.core.ui.components.AppEmptyScreen
import com.kstream.core.ui.components.tvFocusBorder
import com.kstream.core.ui.components.tvFocusScale
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val selectedIds = remember { mutableStateListOf<String>() }
    var isSelecting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(downloads.size) {
        if (downloads.isEmpty()) {
            isSelecting = false
            selectedIds.clear()
        }
    }

    BackHandler {
        if (isSelecting) {
            isSelecting = false
            selectedIds.clear()
        } else if (isSearching) {
            isSearching = false
            viewModel.onSearchQueryChange("")
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            if (isSelecting) {
                TopAppBar(
                    title = {
                        Text("${selectedIds.size} selected")
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelecting = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        if (downloads.isNotEmpty()) {
                            val allSelected = selectedIds.size == downloads.size
                            TextButton(onClick = {
                                if (allSelected) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(downloads.map { it.id })
                                }
                            }) {
                                Text(if (allSelected) "Deselect All" else "Select All")
                            }
                        }
                    }
                )
            } else if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search downloads...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.textFieldColors(containerColor = Color.Transparent)
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
                        if (downloads.isNotEmpty()) {
                            IconButton(onClick = { isSelecting = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Select items")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isSelecting && selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            pendingDeleteIds = selectedIds.toSet()
                            showDeleteConfirm = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete ${selectedIds.size} item${if (selectedIds.size > 1) "s" else ""}")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val mainContentModifier = Modifier.fillMaxSize()

            if (downloads.isEmpty() && searchQuery.isEmpty()) {
                AppEmptyScreen(
                    title = "No Downloads Yet",
                    message = "Movies you download will appear here. Tap Download on any movie to save it for offline viewing.",
                    isTv = false,
                    icon = Icons.Default.Download,
                    primaryActionLabel = "Browse Content",
                    onPrimaryAction = onBackClick,
                    modifier = mainContentModifier
                )
            } else if (downloads.isEmpty() && searchQuery.isNotEmpty()) {
                AppEmptyScreen(
                    title = "No Results",
                    message = "No downloads match \"$searchQuery\". Try a different title.",
                    isTv = false,
                    icon = Icons.Default.Search,
                    primaryActionLabel = "Clear Search",
                    onPrimaryAction = { viewModel.onSearchQueryChange("") },
                    modifier = mainContentModifier
                )
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

                        val isSelected = download.id in selectedIds
                        
                        DownloadItem(
                            download = download,
                            fileExists = fileExists,
                            isSelected = isSelected,
                            isSelecting = isSelecting,
                            onClick = {
                                if (isSelecting) {
                                    if (isSelected) selectedIds.remove(download.id)
                                    else selectedIds.add(download.id)
                                } else {
                                    onMovieClick(download.movieId)
                                }
                            },
                            onLongPress = {
                                if (!isSelecting) {
                                    isSelecting = true
                                    selectedIds.add(download.id)
                                }
                            },
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
        val movieTitle = downloadToRemove?.title ?: "this movie"
        AlertDialog(
            onDismissRequest = { downloadToRemove = null },
            title = { Text("Delete Download?") },
            text = {
                Text(
                    "\"$movieTitle\" will be permanently deleted from your device. " +
                    "You will need to download it again to watch offline."
                )
            },
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
                    Text("Keep")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val count = pendingDeleteIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${if (count == 1) "Download" else "$count Downloads"}?") },
            text = {
                if (count == 1) {
                    Text("This movie will be permanently deleted from your device. You will need to download it again to watch offline.")
                } else {
                    Text("$count movies will be permanently deleted from your device. You will need to re-download them to watch offline.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeDownloads(pendingDeleteIds)
                        selectedIds.removeAll(pendingDeleteIds)
                        if (selectedIds.isEmpty()) isSelecting = false
                        pendingDeleteIds = emptySet()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItem(
    download: Download,
    fileExists: Boolean,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onWatch: (String) -> Unit,
    onRedownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit
) {
    var cardFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { cardFocused = it.hasFocus }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .tvFocusScale(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = if (cardFocused) BorderStroke(2.dp, Color(0xFFE50914)) else null
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(80.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                if (isSelecting) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.align(Alignment.TopStart),
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFE50914),
                            checkmarkColor = Color.White
                        )
                    )
                }
            }

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
                val isRefreshingLinks = download.statusMessage == "Refreshing expired links..."
                val statusText = when {
                    isRefreshingLinks -> "Refreshing expired links..."
                    download.status == DownloadStatus.DOWNLOADING -> {
                        val downloaded = formatBytes(download.downloadedBytes)
                        val total = formatBytes(download.totalBytes)
                        "Downloading... $downloaded / $total ($progressPercent%)"
                    }
                    download.status == DownloadStatus.PAUSED -> {
                        val downloaded = formatBytes(download.downloadedBytes)
                        val total = formatBytes(download.totalBytes)
                        "${download.statusMessage ?: "Paused"} $downloaded / $total ($progressPercent%)"
                    }
                    download.status == DownloadStatus.COMPLETED -> if (fileExists) "Completed" else "File moved or deleted"
                    download.status == DownloadStatus.QUEUED -> "Queued"
                    download.status == DownloadStatus.FAILED -> download.statusMessage ?: "Failed"
                    download.status == DownloadStatus.DELETED -> "Deleted"
                    else -> ""
                }
                
                Text(
                    text = statusText, 
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isRefreshingLinks -> MaterialTheme.colorScheme.primary
                        !fileExists && download.status == DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                if (isRefreshingLinks) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else if (download.status != DownloadStatus.COMPLETED) {
                    LinearProgressIndicator(
                        progress = download.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else if (fileExists) {
                    Button(
                        onClick = { onWatch(download.quality) },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .tvFocusBorder(shape = RoundedCornerShape(20.dp)),
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
                            modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(20.dp)),
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
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(48.dp).tvFocusBorder(),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause")
                    }
                } else if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.QUEUED) {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(48.dp).tvFocusBorder(),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                }
                
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusBorder(shape = RoundedCornerShape(50))
                ) {
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
        IconButton(onClick = onToggle, modifier = Modifier.tvFocusBorder()) {
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
