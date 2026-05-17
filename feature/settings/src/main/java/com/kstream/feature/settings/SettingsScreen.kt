package com.kstream.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditingUsername by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf("") }
    var showClearLikedDialog by remember { mutableStateOf(false) }
    var showWatchHistory by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.username) {
        tempUsername = uiState.username
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = "Profile", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditingUsername) {
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = {
                        viewModel.onUsernameChange(tempUsername)
                        viewModel.saveUsername()
                        isEditingUsername = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                } else {
                    Text(
                        text = uiState.username.ifBlank { "Guest" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isEditingUsername = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Content", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            ScanMoviesButton(
                scanState = uiState.scanState,
                scanMessage = uiState.scanMessage,
                onTriggerScan = { viewModel.triggerScan() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showClearLikedDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Liked Movies")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    viewModel.loadWatchHistory()
                    showWatchHistory = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Watch History")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Downloads", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("Download Location") },
                supportingContent = { 
                    Text("/Internal Storage/Movies/KStream")
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { viewModel.clearCache() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.cacheCleared
            ) {
                if (uiState.cacheCleared) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cache Cleared ✓")
                } else {
                    Text("Clear Cache")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All & Restart")
            }
        }

        if (showClearLikedDialog) {
            AlertDialog(
                onDismissRequest = { showClearLikedDialog = false },
                title = { Text("Clear Liked Movies") },
                text = { Text("This will remove all your liked movies. Are you sure?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearLikedMovies()
                            showClearLikedDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearLikedDialog = false }) {
                        Text("No")
                    }
                }
            )
        }

        if (showWatchHistory) {
            WatchHistoryScreen(
                items = uiState.watchHistory,
                isLoading = uiState.isLoadingHistory,
                onRemove = { selectedIds ->
                    viewModel.deleteWatchHistory(selectedIds)
                },
                onDismiss = { showWatchHistory = false }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        "⚠️ Reset All Data",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        "This will permanently delete ALL app data including:\n\n" +
                        "• All downloaded movies and files\n" +
                        "• Watch history and progress\n" +
                        "• Liked movies list\n" +
                        "• Cached data and preferences\n" +
                        "• Username and settings\n\n" +
                        "The app will restart as if freshly installed. This action cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            viewModel.resetAllAndRestart()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Yes, Reset Everything")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchHistoryScreen(
    items: List<WatchHistoryItem>,
    isLoading: Boolean,
    onRemove: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { androidx.compose.runtime.snapshots.SnapshotStateList<String>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedIds.isNotEmpty()) {
                        Text("${selectedIds.size} selected")
                    } else {
                        Text("Watch History")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedIds.isNotEmpty()) {
                            selectedIds.clear()
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(
                            if (selectedIds.isNotEmpty()) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = if (selectedIds.isNotEmpty()) "Clear selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        // Select All / Deselect All
                        val allSelected = selectedIds.size == items.size
                        TextButton(onClick = {
                            if (allSelected) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(items.map { it.movieId })
                            }
                        }) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                    }
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No watch history", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.movieId }) { item ->
                    val isSelected = item.movieId in selectedIds
                    WatchHistoryListItem(
                        item = item,
                        isSelected = isSelected,
                        onToggleSelect = {
                            if (isSelected) selectedIds.remove(item.movieId)
                            else selectedIds.add(item.movieId)
                        }
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Remove Watch History") },
                text = {
                    Text("Remove ${selectedIds.size} item${if (selectedIds.size > 1) "s" else ""} from watch history?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemove(selectedIds.toSet())
                            selectedIds.clear()
                            showDeleteConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun WatchHistoryListItem(
    item: WatchHistoryItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.movieName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = (item.completionPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.completionPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTimestamp(item.lastWatched),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

@Composable
private fun ScanMoviesButton(
    scanState: ScanState,
    scanMessage: String,
    onTriggerScan: () -> Unit
) {
    val isActive = scanState in listOf(
        ScanState.TRIGGERING, ScanState.RUNNING, ScanState.ALREADY_RUNNING
    )
    val isEnabled = scanState == ScanState.IDLE

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onTriggerScan,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (scanState) {
                ScanState.CHECKING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking status...")
                }
                ScanState.TRIGGERING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Initiating scan...")
                }
                ScanState.RUNNING, ScanState.ALREADY_RUNNING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning...")
                }
                ScanState.COMPLETED -> {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Complete ✓")
                }
                ScanState.FAILED -> {
                    Text("Scan Failed")
                }
                ScanState.TOO_RECENT -> {
                    Text("Scanned recently")
                }
                ScanState.IDLE -> {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Latest Movies")
                }
            }
        }

        // Progress bar below button when active
        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        // Status message
        AnimatedVisibility(
            visible = scanMessage.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = scanMessage,
                style = MaterialTheme.typography.bodySmall,
                color = when (scanState) {
                    ScanState.FAILED -> MaterialTheme.colorScheme.error
                    ScanState.COMPLETED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
