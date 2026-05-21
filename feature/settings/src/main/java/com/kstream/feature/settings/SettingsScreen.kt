package com.kstream.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import coil.compose.AsyncImage
import com.kstream.core.ui.components.AppEmptyScreen
import com.kstream.core.ui.components.AppLoadingScreen
import com.kstream.core.ui.components.tvFocusBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    onMovieClick: (String) -> Unit,
    onTermsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditingUsername by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf("") }
    var showClearLikedDialog by remember { mutableStateOf(false) }
    var showWatchHistory by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.username) {
        tempUsername = uiState.username
    }

    LaunchedEffect(uiState.successMessage) {
        val msg = uiState.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        viewModel.clearSuccessMessage()
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.tvFocusBorder()) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
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
                    }, modifier = Modifier.tvFocusBorder()) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                } else {
                    Text(
                        text = uiState.username.ifBlank { "Guest" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isEditingUsername = true }, modifier = Modifier.tvFocusBorder()) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Content", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            ScanMoviesButton(
                isEnabled = uiState.isScanButtonEnabled,
                scanStatusText = uiState.scanStatusText,
                lastRefreshText = uiState.lastRefreshText,
                scanDetailText = uiState.scanDetailText,
                onTriggerScan = { viewModel.triggerScan() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            var clearLikedFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showClearLikedDialog = true },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { clearLikedFocused = it.isFocused }
                    .tvFocusBorder(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(
                    width = if (clearLikedFocused) 2.dp else 1.dp,
                    color = if (clearLikedFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Liked Movies")
            }

            Spacer(modifier = Modifier.height(8.dp))

            var watchHistoryFocused by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = {
                    showWatchHistory = true
                },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { watchHistoryFocused = it.isFocused }
                    .tvFocusBorder(),
                border = BorderStroke(
                    width = if (watchHistoryFocused) 2.dp else 1.dp,
                    color = if (watchHistoryFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.outline
                )
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
            
            var clearCacheFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { viewModel.clearCache() },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { clearCacheFocused = it.isFocused }
                    .tvFocusBorder(),
                enabled = !uiState.cacheCleared,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (clearCacheFocused) Color(0xFFFF1A1A) else MaterialTheme.colorScheme.primary
                )
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

            var resetFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth()
                    .onFocusChanged { resetFocused = it.isFocused }
                    .tvFocusBorder(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (resetFocused) Color(0xFFFF1A1A) else MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All & Restart")
            }

            Spacer(modifier = Modifier.height(32.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onTermsClick,
                modifier = Modifier.fillMaxWidth().tvFocusBorder()
            ) {
                Text(
                    text = "Terms & Conditions",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) { "1.0.0" }
            }
            Text(
                text = "App Version $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (showClearLikedDialog) {
            AlertDialog(
                onDismissRequest = { showClearLikedDialog = false },
                title = { Text("Clear Liked Movies?") },
                text = {
                    Text(
                        "This will permanently remove all movies from your Liked list.\n\n" +
                        "You can re-like them later, but this cannot be undone."
                    )
                },
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
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearLikedDialog = false }) {
                        Text("Keep")
                    }
                }
            )
        }

        if (showWatchHistory) {
            WatchHistoryScreen(
                items = uiState.watchHistory,
                isLoading = uiState.isLoadingHistory,
                onMovieClick = onMovieClick,
                onRemove = { ids: Set<String> ->
                    viewModel.deleteWatchHistory(ids)
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
    onMovieClick: (String) -> Unit,
    onRemove: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    var isSelecting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Exit selection mode when all items are gone
    LaunchedEffect(items.size) {
        if (items.isEmpty()) {
            isSelecting = false
            selectedIds.clear()
        }
    }

    BackHandler {
        if (isSelecting) {
            isSelecting = false
            selectedIds.clear()
        } else {
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelecting && selectedIds.isNotEmpty()) {
                        Text("${selectedIds.size} selected")
                    } else {
                        Text("Watch History")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelecting) {
                            isSelecting = false
                            selectedIds.clear()
                        } else {
                            onDismiss()
                        }
                    }, modifier = Modifier.tvFocusBorder()) {
                        Icon(
                            if (isSelecting) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = if (isSelecting) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (isSelecting && items.isNotEmpty()) {
                        val allSelected = selectedIds.size == items.size
                        TextButton(onClick = {
                            if (allSelected) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(items.map { it.movieId })
                            }
                        }, modifier = Modifier.tvFocusBorder()) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                    } else if (items.isNotEmpty()) {
                        IconButton(onClick = {
                            isSelecting = true
                        }, modifier = Modifier.tvFocusBorder()) {
                            Icon(Icons.Default.Edit, contentDescription = "Select items")
                        }
                    }
                }
            )
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
        }
    ) { padding ->
        if (isLoading) {
            AppLoadingScreen(
                title = "Loading History",
                message = "Fetching your watch history...",
                isTv = false,
                modifier = Modifier.padding(padding)
            )
        } else if (items.isEmpty()) {
            AppEmptyScreen(
                title = "No Watch History",
                message = "Movies you watch will appear here. Start browsing and enjoy!",
                isTv = false,
                icon = Icons.Default.Info,
                primaryActionLabel = "Browse Content",
                onPrimaryAction = onDismiss,
                modifier = Modifier.padding(padding)
            )
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
                        isSelecting = isSelecting,
                        onTap = {
                            if (isSelecting) {
                                if (isSelected) selectedIds.remove(item.movieId)
                                else selectedIds.add(item.movieId)
                            } else {
                                onMovieClick(item.movieId)
                            }
                        },
                        onLongPress = {
                            if (!isSelecting) {
                                isSelecting = true
                                selectedIds.add(item.movieId)
                            }
                        },
                        onDelete = {
                            pendingDeleteIds = setOf(item.movieId)
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Remove from Watch History?") },
                text = {
                    val count = pendingDeleteIds.size
                    if (count == 1) {
                        Text("This will remove 1 item from your watch history. Your progress for this movie will be lost.")
                    } else {
                        Text("This will remove $count items from your watch history. Watch progress for these movies will be lost.")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemove(pendingDeleteIds)
                            selectedIds.removeAll(pendingDeleteIds)
                            if (selectedIds.isEmpty()) isSelecting = false
                            pendingDeleteIds = emptySet()
                            showDeleteConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.tvFocusBorder()
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }, modifier = Modifier.tvFocusBorder()) {
                        Text("Keep")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchHistoryListItem(
    item: WatchHistoryItem,
    isSelected: Boolean,
    isSelecting: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    var historyCardFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { historyCardFocused = it.hasFocus }
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (historyCardFocused) BorderStroke(2.dp, Color(0xFFE50914)) else null
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox overlay in selection mode
            if (isSelecting) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Poster
            AsyncImage(
                model = item.posterUrl.ifBlank { null },
                contentDescription = item.movieName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(72.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title + last watched
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.movieName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(item.lastWatched),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Per-item delete button (hidden in multi-select mode to reduce clutter)
            if (!isSelecting) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp).tvFocusBorder()
                    ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
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
    isEnabled: Boolean,
    scanStatusText: String,
    lastRefreshText: String,
    scanDetailText: String,
    onTriggerScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        var scanFocused by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = onTriggerScan,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { scanFocused = it.isFocused }
                .tvFocusBorder(),
            border = BorderStroke(
                width = if (scanFocused) 2.dp else 1.dp,
                color = if (scanFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.outline
            )
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Latest Movies")
        }

        Text(
            text = scanStatusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
        Text(
            text = lastRefreshText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
        )
        Text(
            text = scanDetailText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
        )
    }
}
