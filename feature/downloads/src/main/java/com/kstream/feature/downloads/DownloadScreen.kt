package com.kstream.feature.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kstream.core.model.Download
import com.kstream.core.model.DownloadStatus
import com.kstream.core.ui.components.AppEmptyScreen
import com.kstream.core.ui.components.tvFocusBorder
import com.kstream.core.ui.components.tvFocusScale
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import java.util.Locale

// ─── Design tokens ────────────────────────────────────────────────────────────
private val BgPage       = Color(0xFF0A0A0A)
private val BgCard       = Color(0xFF141414)
private val BgRow        = Color(0xFF1A1A1A)
private val BorderSubtle = Color(0xFF222222)
private val BorderMid    = Color(0xFF333333)
private val BrandRed     = Color(0xFFE50914)
private val TextPrimary  = Color(0xFFFFFFFF)
private val TextSecond   = Color(0xFFAAAAAA)
private val TextDim      = Color(0xFF666666)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadRoute(
    onBackClick: () -> Unit,
    onMovieClick: (String) -> Unit,
    onWatchClick: (String, String) -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle(initialValue = emptyList())
    val allDownloads by viewModel.allDownloadsRaw.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val filterStatus by viewModel.filterStatus.collectAsStateWithLifecycle()
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
        if (downloads.isEmpty()) { isSelecting = false; selectedIds.clear() }
    }

    BackHandler {
        when {
            isSelecting -> { isSelecting = false; selectedIds.clear() }
            isSearching -> { isSearching = false; viewModel.onSearchQueryChange("") }
            else -> onBackClick()
        }
    }

    val platform = LocalPlatform.current

    // Storage summary
    val totalFiles = allDownloads.count { it.status == DownloadStatus.COMPLETED }
    val totalBytes = allDownloads.filter { it.status == DownloadStatus.COMPLETED }
        .sumOf { it.totalBytes }
    val storageSummary = if (totalFiles == 0) "No offline content"
                         else "$totalFiles file${if (totalFiles != 1) "s" else ""} • ${formatBytes(totalBytes)} saved"

    Scaffold(
        containerColor = BgPage,
        topBar = {
            when {
                isSelecting -> SelectionTopBar(
                    count = selectedIds.size,
                    total = downloads.size,
                    onSelectAll = {
                        if (selectedIds.size == downloads.size) selectedIds.clear()
                        else { selectedIds.clear(); selectedIds.addAll(downloads.map { it.id }) }
                    },
                    onCancel = { isSelecting = false; selectedIds.clear() }
                )
                isSearching -> SearchTopBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClose = { isSearching = false; viewModel.onSearchQueryChange("") },
                    sortOption = sortOption,
                    showSortMenu = showSortMenu,
                    onSortToggle = { showSortMenu = !showSortMenu },
                    onSortDismiss = { showSortMenu = false },
                    onSortChange = { viewModel.onSortChange(it); showSortMenu = false }
                )
                else -> DefaultTopBar(
                    title = "Downloads",
                    subtitle = storageSummary,
                    sortOption = sortOption,
                    showSortMenu = showSortMenu,
                    onSortToggle = { showSortMenu = !showSortMenu },
                    onSortDismiss = { showSortMenu = false },
                    onSortChange = { viewModel.onSortChange(it); showSortMenu = false },
                    onSearchClick = { isSearching = true },
                    onSelectClick = { isSelecting = true },
                    hasItems = downloads.isNotEmpty(),
                    onBackClick = onBackClick
                )
            }
        },
        bottomBar = {
            if (isSelecting && selectedIds.isNotEmpty()) {
                Surface(color = BgCard, shadowElevation = 8.dp, tonalElevation = 0.dp) {
                    Button(
                        onClick = { pendingDeleteIds = selectedIds.toSet(); showDeleteConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .tvFocusBorder(shape = RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete ${selectedIds.size} item${if (selectedIds.size > 1) "s" else ""}")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
                .padding(padding)
        ) {
            // Status filter chips
            StatusFilterRow(
                allDownloads = allDownloads,
                selected = filterStatus,
                onSelect = viewModel::onFilterChange
            )

            val isEmpty = downloads.isEmpty()
            if (isEmpty && searchQuery.isEmpty() && filterStatus == null) {
                AppEmptyScreen(
                    title = "No Downloads Yet",
                    message = "Movies you download will appear here. Tap Download on any movie to save it for offline viewing.",
                    isTv = platform == Platform.TV,
                    icon = Icons.Default.Download,
                    primaryActionLabel = "Browse Content",
                    onPrimaryAction = onBackClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isEmpty) {
                AppEmptyScreen(
                    title = if (filterStatus != null) "No ${filterStatus!!.label()} Downloads"
                            else "No Results",
                    message = if (searchQuery.isNotEmpty()) "No downloads match \"$searchQuery\"."
                              else "No downloads in this category.",
                    isTv = platform == Platform.TV,
                    icon = Icons.Default.Search,
                    primaryActionLabel = if (filterStatus != null) "Show All" else "Clear Search",
                    onPrimaryAction = {
                        if (filterStatus != null) viewModel.onFilterChange(null)
                        else viewModel.onSearchQueryChange("")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloads, key = { it.id }) { download ->
                        var fileExists by remember { mutableStateOf(true) }
                        LaunchedEffect(download.localFilePath, download.status) {
                            fileExists = viewModel.checkFileExists(download.localFilePath)
                        }
                        DownloadItem(
                            download = download,
                            fileExists = fileExists,
                            isSelected = download.id in selectedIds,
                            isSelecting = isSelecting,
                            onClick = {
                                if (isSelecting) {
                                    if (download.id in selectedIds) selectedIds.remove(download.id)
                                    else selectedIds.add(download.id)
                                } else onMovieClick(download.movieId)
                            },
                            onLongPress = {
                                if (!isSelecting) { isSelecting = true; selectedIds.add(download.id) }
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

    // ── Single-item delete dialog ──────────────────────────────────────────────
    if (downloadToRemove != null) {
        val title = downloadToRemove?.title ?: "this movie"
        val singleDeleteDismissFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try { singleDeleteDismissFocus.requestFocus() } catch (_: Exception) {}
        }
        AlertDialog(
            onDismissRequest = { downloadToRemove = null },
            containerColor = BgCard,
            title = { Text("Delete Download?", color = TextPrimary) },
            text = {
                Text(
                    "\"$title\" will be permanently deleted from your device. " +
                    "You will need to download it again to watch offline.",
                    color = TextSecond
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { downloadToRemove?.let { viewModel.removeDownload(it.id) }; downloadToRemove = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandRed),
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { downloadToRemove = null },
                    modifier = Modifier.focusRequester(singleDeleteDismissFocus).tvFocusBorder(shape = RoundedCornerShape(50))
                ) { Text("Keep", color = TextSecond) }
            }
        )
    }

    // ── Multi-item delete dialog ───────────────────────────────────────────────
    if (showDeleteConfirm) {
        val count = pendingDeleteIds.size
        val multiDeleteDismissFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try { multiDeleteDismissFocus.requestFocus() } catch (_: Exception) {}
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BgCard,
            title = { Text("Delete ${if (count == 1) "Download" else "$count Downloads"}?", color = BrandRed) },
            text = {
                Text(
                    if (count == 1) "This movie will be permanently deleted from your device."
                    else "$count movies will be permanently deleted from your device.",
                    color = TextSecond
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeDownloads(pendingDeleteIds)
                        selectedIds.removeAll(pendingDeleteIds)
                        if (selectedIds.isEmpty()) isSelecting = false
                        pendingDeleteIds = emptySet()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                ) { Text("Delete All") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.focusRequester(multiDeleteDismissFocus).tvFocusBorder(shape = RoundedCornerShape(50))
                ) { Text("Keep", color = TextSecond) }
            }
        )
    }
}

// ─── Top bars ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    title: String,
    subtitle: String,
    sortOption: DownloadSortOption,
    showSortMenu: Boolean,
    onSortToggle: () -> Unit,
    onSortDismiss: () -> Unit,
    onSortChange: (DownloadSortOption) -> Unit,
    onSearchClick: () -> Unit,
    onSelectClick: () -> Unit,
    hasItems: Boolean,
    onBackClick: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }
    val sortFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    TopAppBar(
        title = {
            Column {
                Text(
                    title,
                    style = TextStyle(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Text(subtitle, style = TextStyle(color = TextDim, fontSize = 11.sp))
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .focusRequester(backFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) true
                        else false
                    }
                    .tvFocusBorder(shape = CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        },
        actions = {
            SortChip(
                currentSort = sortOption,
                expanded = showSortMenu,
                onToggle = onSortToggle,
                onDismiss = onSortDismiss,
                onSortChange = onSortChange,
                modifier = Modifier
                    .focusRequester(sortFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            try { backFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        } else false
                    }
            )
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .focusRequester(searchFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            try { sortFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        } else false
                    }
                    .tvFocusBorder(shape = CircleShape)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
            }
            if (hasItems) {
                IconButton(
                    onClick = onSelectClick,
                    modifier = Modifier
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else false
                        }
                        .tvFocusBorder(shape = CircleShape)
                ) {
                    Icon(Icons.Default.CheckBox, contentDescription = "Select", tint = TextPrimary)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPage)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit
) {
    TopAppBar(
        title = { Text("$count selected", style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) },
        navigationIcon = {
            IconButton(onClick = onCancel, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimary)
            }
        },
        actions = {
            TextButton(onClick = onSelectAll, modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))) {
                Text(if (count == total) "Deselect All" else "Select All",
                    style = TextStyle(color = BrandRed, fontSize = 14.sp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPage)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    sortOption: DownloadSortOption,
    showSortMenu: Boolean,
    onSortToggle: () -> Unit,
    onSortDismiss: () -> Unit,
    onSortChange: (DownloadSortOption) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var barFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (barFocused) BrandRed else BorderMid, tween(200), label = "searchBorder"
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(BgRow, RoundedCornerShape(20.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { barFocused = it.hasFocus },
                    singleLine = true,
                    cursorBrush = SolidColor(BrandRed),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Search downloads…", style = TextStyle(color = TextDim, fontSize = 15.sp))
                        }
                        inner()
                    }
                )
                if (query.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp).tvFocusBorder(shape = CircleShape)) {
                            Icon(Icons.Default.Close, null, tint = TextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close Search", tint = TextPrimary)
            }
        },
        actions = {
            SortChip(
                currentSort = sortOption,
                expanded = showSortMenu,
                onToggle = onSortToggle,
                onDismiss = onSortDismiss,
                onSortChange = onSortChange
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPage)
    )
}

// ─── Sort chip ────────────────────────────────────────────────────────────────

@Composable
private fun SortChip(
    currentSort: DownloadSortOption,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSortChange: (DownloadSortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        var focused by remember { mutableStateOf(false) }
        val borderColor by animateColorAsState(
            if (focused) Color.White else BorderMid, tween(200), label = "sortBorder"
        )
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(BgRow, RoundedCornerShape(16.dp))
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .onFocusChanged { focused = it.hasFocus }
                .clickable { onToggle() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.SwapVert, null, tint = TextSecond, modifier = Modifier.size(16.dp))
                Text(currentSort.label, style = TextStyle(color = TextSecond, fontSize = 12.sp), maxLines = 1)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.background(BgCard)
        ) {
            DownloadSortOption.entries.forEach { option ->
                val isSelected = option == currentSort
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            color = if (isSelected) BrandRed else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = BrandRed, modifier = Modifier.size(16.dp))
                    },
                    onClick = { onSortChange(option) }
                )
            }
        }
    }
}

// ─── Status filter chips ──────────────────────────────────────────────────────

@Composable
private fun StatusFilterRow(
    allDownloads: List<Download>,
    selected: DownloadStatus?,
    onSelect: (DownloadStatus?) -> Unit
) {
    val counts = remember(allDownloads) {
        DownloadStatus.entries.associateWith { s -> allDownloads.count { it.status == s } }
    }
    val chips = listOf<Pair<String, DownloadStatus?>>(
        "All" to null,
        "Downloading" to DownloadStatus.DOWNLOADING,
        "Paused" to DownloadStatus.PAUSED,
        "Completed" to DownloadStatus.COMPLETED,
        "Failed" to DownloadStatus.FAILED,
        "Queued" to DownloadStatus.QUEUED
    )
    val visibleChips = remember(allDownloads) {
        chips.filter { (_, status) -> status == null || (counts[status] ?: 0) > 0 }
    }
    val chipFocusRequesters = remember(visibleChips.size) {
        List(visibleChips.size) { FocusRequester() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleChips.forEachIndexed { index, (label, status) ->
            val count = if (status == null) allDownloads.size else (counts[status] ?: 0)
            val isActive = selected == status
            var focused by remember { mutableStateOf(false) }
            val bg = if (isActive) BrandRed else BgRow
            val border by animateColorAsState(
                when { focused -> Color.White; isActive -> BrandRed; else -> BorderMid },
                tween(150), label = "chip$label"
            )
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .background(bg, RoundedCornerShape(14.dp))
                    .border(3.dp, border, RoundedCornerShape(14.dp))
                    .focusRequester(chipFocusRequesters[index])
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            if (index > 0) {
                                try { chipFocusRequesters[index - 1].requestFocus() } catch (_: Exception) {}
                            }
                            true // always consume Left — leftmost chip stays, others route left
                        } else false
                    }
                    .onFocusChanged { focused = it.hasFocus }
                    .clickable { onSelect(status) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (status == null) label else "$label ($count)",
                    style = TextStyle(
                        color = if (isActive) Color.White else TextSecond,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }
    }
}

// ─── Download item card ───────────────────────────────────────────────────────

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
    val borderColor = when {
        isSelected  -> BrandRed
        cardFocused -> Color.White
        else        -> BorderSubtle
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { cardFocused = it.hasFocus }
            .focusProperties { canFocus = false }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .tvFocusScale(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1A0A0A) else BgCard
        ),
        border = BorderStroke(if (isSelected || cardFocused) 1.5.dp else 0.5.dp, borderColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Poster with optional checkbox overlay ──────────────────────
            Box(
                modifier = Modifier
                    .clickable { onClick() }
                    .tvFocusBorder(shape = RoundedCornerShape(6.dp))
            ) {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(72.dp)
                        .height(108.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                if (isSelecting) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        colors = CheckboxDefaults.colors(
                            checkedColor = BrandRed,
                            checkmarkColor = Color.White
                        )
                    )
                }
            }

            // ── Info column ────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Status badge + quality
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (badgeColor, badgeLabel) = download.status.badgeStyle(fileExists)
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            badgeLabel,
                            style = TextStyle(
                                color = badgeColor, fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        download.quality,
                        style = TextStyle(color = TextDim, fontSize = 11.sp)
                    )
                }
                Spacer(Modifier.height(4.dp))

                // Title
                Text(
                    download.title,
                    style = TextStyle(color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    download.fileSize,
                    style = TextStyle(color = TextDim, fontSize = 11.sp)
                )

                Spacer(Modifier.height(8.dp))

                val isRefreshing = download.statusMessage == "Refreshing expired links..."
                when {
                    // ── In-progress: progress bar + status text ─────────
                    isRefreshing -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFFFC107), trackColor = BorderMid
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Refreshing expired links…", style = TextStyle(color = Color(0xFFFFC107), fontSize = 11.sp))
                    }
                    download.status == DownloadStatus.DOWNLOADING ||
                    download.status == DownloadStatus.PAUSED ||
                    download.status == DownloadStatus.QUEUED -> {
                        val pct = (download.progress * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = download.progress,
                                modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                color = when (download.status) {
                                    DownloadStatus.PAUSED -> Color(0xFF5C9EE8)
                                    else -> BrandRed
                                },
                                trackColor = BorderMid
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$pct%", style = TextStyle(color = TextDim, fontSize = 10.sp))
                        }
                        Spacer(Modifier.height(3.dp))
                        val downloaded = formatBytes(download.downloadedBytes)
                        val total = formatBytes(download.totalBytes)
                        Text(
                            if (download.status == DownloadStatus.QUEUED) "Queued"
                            else "$downloaded / $total",
                            style = TextStyle(color = TextSecond, fontSize = 11.sp)
                        )
                        Spacer(Modifier.height(6.dp))
                        // Pause / Resume chip
                        if (download.status == DownloadStatus.DOWNLOADING) {
                            OutlinedButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .height(28.dp)
                                    .tvFocusBorder(shape = RoundedCornerShape(14.dp)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, BorderMid)
                            ) {
                                Icon(Icons.Default.Pause, null, tint = TextSecond, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pause", style = TextStyle(color = TextSecond, fontSize = 12.sp))
                            }
                        } else if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.QUEUED) {
                            OutlinedButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .height(28.dp)
                                    .tvFocusBorder(shape = RoundedCornerShape(14.dp)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = BrandRed, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume", style = TextStyle(color = BrandRed, fontSize = 12.sp))
                            }
                        }
                    }
                    // ── Completed + file exists: Watch Now ──────────────
                    download.status == DownloadStatus.COMPLETED && fileExists -> {
                        Button(
                            onClick = { onWatch(download.quality) },
                            modifier = Modifier
                                .height(32.dp)
                                .tvFocusBorder(shape = RoundedCornerShape(16.dp)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Watch Now", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    // ── File missing / failed / deleted: Redownload ─────
                    else -> {
                        if (!fileExists || download.status == DownloadStatus.FAILED) {
                            val warningColor = if (download.status == DownloadStatus.FAILED) BrandRed
                                              else Color(0xFFFFC107)
                            Text(
                                if (download.status == DownloadStatus.FAILED) download.statusMessage ?: "Download failed"
                                else "File moved or deleted",
                                style = TextStyle(color = warningColor, fontSize = 11.sp)
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        OutlinedButton(
                            onClick = onRedownload,
                            modifier = Modifier
                                .height(28.dp)
                                .tvFocusBorder(shape = RoundedCornerShape(14.dp)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, BorderMid)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = TextSecond, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Redownload", style = TextStyle(color = TextSecond, fontSize = 12.sp))
                        }
                    }
                }
            }

            // ── Delete button ──────────────────────────────────────────────
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Top)
                    .tvFocusBorder(shape = RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove",
                    tint = Color(0xFF993333), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun DownloadStatus.label() = when (this) {
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED      -> "Paused"
    DownloadStatus.COMPLETED   -> "Completed"
    DownloadStatus.FAILED      -> "Failed"
    DownloadStatus.QUEUED      -> "Queued"
    DownloadStatus.DELETED     -> "Deleted"
}

private fun DownloadStatus.badgeStyle(fileExists: Boolean): Pair<Color, String> = when (this) {
    DownloadStatus.DOWNLOADING -> Color(0xFFFFC107) to "Downloading"
    DownloadStatus.PAUSED      -> Color(0xFF5C9EE8) to "Paused"
    DownloadStatus.COMPLETED   -> if (fileExists) Color(0xFF4CAF50) to "Ready"
                                  else Color(0xFFFFC107) to "Missing"
    DownloadStatus.FAILED      -> Color(0xFFE50914) to "Failed"
    DownloadStatus.QUEUED      -> Color(0xFF888888) to "Queued"
    DownloadStatus.DELETED     -> Color(0xFF555555) to "Deleted"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
