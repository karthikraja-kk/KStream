package com.kstream.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FavoriteBorder
import coil.compose.AsyncImage
import com.kstream.core.ui.components.AppEmptyScreen
import com.kstream.core.ui.components.AppLoadingScreen
import com.kstream.core.ui.components.tvFocusBorder

// ─── Design tokens ────────────────────────────────────────────────────────────
private val BgPage       = Color(0xFF0A0A0A)
private val BgCard       = Color(0xFF141414)
private val BgRow        = Color(0xFF1A1A1A)
private val BorderSubtle = Color(0xFF222222)
private val BorderMid    = Color(0xFF333333)
private val BrandRed     = Color(0xFFE50914)
private val TextPrimary  = Color(0xFFFFFFFF)
private val TextSecond   = Color(0xFFB3B3B3)
private val TextDim      = Color(0xFF666666)

// ─── Reusable building blocks ─────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(BrandRed, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TextSecond, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = TextStyle(color = TextSecond, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp)
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        content = content
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color = TextSecond,
    title: String,
    subtitle: String? = null,
    titleColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(BgRow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = TextStyle(color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = TextStyle(color = TextSecond, fontSize = 12.sp))
            }
        }
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Divider(
        modifier = Modifier.padding(start = 66.dp),
        color = BorderSubtle,
        thickness = 0.5.dp
    )
}

// ─── Main screen ──────────────────────────────────────────────────────────────

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

    BackHandler { onBackClick() }

    Scaffold(
        containerColor = BgPage,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = TextStyle(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPage)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Profile ──────────────────────────────────────────────────────
            SettingsSection("PROFILE", Icons.Default.Person)
            SettingsCard {
                ProfileRow(
                    username = uiState.username,
                    isEditing = isEditingUsername,
                    tempUsername = tempUsername,
                    onTempChange = { tempUsername = it },
                    onEditClick = { isEditingUsername = true },
                    onSaveClick = {
                        viewModel.onUsernameChange(tempUsername)
                        viewModel.saveUsername()
                        isEditingUsername = false
                    },
                    onCancelClick = {
                        tempUsername = uiState.username
                        isEditingUsername = false
                    }
                )
            }

            // ── Content ───────────────────────────────────────────────────────
            SettingsSection("CONTENT", Icons.Default.Search)
            SettingsCard {
                // Scan Movies row
                ScanMoviesRow(
                    isEnabled = uiState.isScanButtonEnabled,
                    scanState = uiState.scanState,
                    scanDetailText = uiState.scanDetailText,
                    lastRefreshText = uiState.lastRefreshText,
                    onTriggerScan = { viewModel.triggerScan() }
                )
                RowDivider()
                // Clear Liked Movies
                var clearLikedFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (clearLikedFocused) Color(0xFF1F0000) else Color.Transparent)
                        .onFocusChanged { clearLikedFocused = it.hasFocus }
                        .tvFocusBorder(shape = RoundedCornerShape(0.dp))
                ) {
                    SettingsRow(
                        icon = Icons.Default.FavoriteBorder,
                        iconTint = BrandRed,
                        title = "Clear Liked Movies",
                        subtitle = "Remove all movies from your liked list",
                        modifier = Modifier.clickableRow { showClearLikedDialog = true }
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                    }
                }
                RowDivider()
                // Manage Watch History
                var watchHistFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (watchHistFocused) Color(0xFF1A1A1A) else Color.Transparent)
                        .onFocusChanged { watchHistFocused = it.hasFocus }
                        .tvFocusBorder(shape = RoundedCornerShape(0.dp))
                ) {
                    SettingsRow(
                        icon = Icons.Default.AccessTime,
                        title = "Manage Watch History",
                        subtitle = "${uiState.watchHistory.size} item${if (uiState.watchHistory.size != 1) "s" else ""} in history",
                        modifier = Modifier.clickableRow { showWatchHistory = true }
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Downloads ─────────────────────────────────────────────────────
            SettingsSection("DOWNLOADS", Icons.Default.Download)
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Download,
                    title = "Download Location",
                    subtitle = "/Internal Storage/Movies/KStream"
                ) {
                    Text("Default", style = TextStyle(color = TextDim, fontSize = 12.sp))
                }
                RowDivider()
                var cacheFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (cacheFocused) Color(0xFF1A1A1A) else Color.Transparent)
                        .onFocusChanged { cacheFocused = it.hasFocus }
                        .tvFocusBorder(shape = RoundedCornerShape(0.dp))
                ) {
                    SettingsRow(
                        icon = Icons.Default.Delete,
                        title = if (uiState.cacheCleared) "Cache Cleared ✓" else "Clear Cache",
                        subtitle = "Frees image and data cache",
                        titleColor = if (uiState.cacheCleared) Color(0xFF4CAF50) else TextPrimary,
                        modifier = Modifier.clickableRow(enabled = !uiState.cacheCleared) {
                            viewModel.clearCache()
                        }
                    ) {
                        if (uiState.cacheCleared) {
                            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Danger Zone ───────────────────────────────────────────────────
            SettingsSection("DANGER ZONE", Icons.Default.Warning)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0000), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF5C0000), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = BrandRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Irreversible actions",
                        style = TextStyle(color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp)
                    )
                }
                Divider(color = Color(0xFF3A0000), thickness = 0.5.dp)
                var resetFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (resetFocused) Color(0xFF2A0000) else Color.Transparent)
                        .onFocusChanged { resetFocused = it.hasFocus }
                        .tvFocusBorder(shape = RoundedCornerShape(0.dp))
                ) {
                    SettingsRow(
                        icon = Icons.Default.Delete,
                        iconTint = BrandRed,
                        title = "Reset All & Restart",
                        titleColor = BrandRed,
                        subtitle = "Permanently deletes all data and restarts",
                        modifier = Modifier.clickableRow { showResetDialog = true }
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = Color(0xFF993333), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            SettingsCard {
                var termsFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (termsFocused) BgRow else Color.Transparent)
                        .onFocusChanged { termsFocused = it.hasFocus }
                        .tvFocusBorder(shape = RoundedCornerShape(0.dp))
                ) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "Terms & Conditions",
                        modifier = Modifier.clickableRow { onTermsClick() }
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = TextDim, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val versionName = remember {
                try { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                catch (_: Exception) { "1.0.0" }
            }
            Text(
                text = "KStream v$versionName",
                style = TextStyle(color = TextDim, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
        }

        // ── Dialogs ───────────────────────────────────────────────────────────
        if (showClearLikedDialog) {
            val clearDismissFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try { clearDismissFocus.requestFocus() } catch (_: Exception) {}
            }
            AlertDialog(
                onDismissRequest = { showClearLikedDialog = false },
                containerColor = BgCard,
                title = { Text("Clear Liked Movies?", color = TextPrimary) },
                text = {
                    Text(
                        "This will permanently remove all movies from your Liked list.\n\n" +
                        "You can re-like them later, but this cannot be undone.",
                        color = TextSecond
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearLikedMovies(); showClearLikedDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = BrandRed),
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) { Text("Clear All") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearLikedDialog = false },
                        modifier = Modifier.focusRequester(clearDismissFocus).tvFocusBorder(shape = RoundedCornerShape(50))
                    ) { Text("Keep") }
                }
            )
        }

        if (showWatchHistory) {
            Dialog(
                onDismissRequest = { showWatchHistory = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                WatchHistoryScreen(
                    items = uiState.watchHistory,
                    isLoading = uiState.isLoadingHistory,
                    onMovieClick = onMovieClick,
                    onRemove = { ids -> viewModel.deleteWatchHistory(ids) },
                    onDismiss = { showWatchHistory = false }
                )
            }
        }

        if (showResetDialog) {
            val resetDismissFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try { resetDismissFocus.requestFocus() } catch (_: Exception) {}
            }
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = BgCard,
                icon = {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = BrandRed, modifier = Modifier.size(32.dp))
                },
                title = { Text("Reset All Data?", color = BrandRed) },
                text = {
                    Text(
                        "This will permanently delete ALL app data including:\n\n" +
                        "• All downloaded movies and files\n" +
                        "• Watch history and progress\n" +
                        "• Liked movies list\n" +
                        "• Cached data and preferences\n" +
                        "• Username and settings\n\n" +
                        "The app will restart as if freshly installed. This action cannot be undone.",
                        color = TextSecond
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showResetDialog = false; viewModel.resetAllAndRestart() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) { Text("Yes, Reset Everything") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showResetDialog = false },
                        modifier = Modifier.focusRequester(resetDismissFocus).tvFocusBorder(shape = RoundedCornerShape(50))
                    ) { Text("Cancel") }
                }
            )
        }
    }
}

// ─── Modifier helper for tappable rows ────────────────────────────────────────
private fun Modifier.clickableRow(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (enabled) this.clickable(onClick = onClick) else this

// ─── Profile row ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileRow(
    username: String,
    isEditing: Boolean,
    tempUsername: String,
    onTempChange: (String) -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val platform = LocalPlatform.current
    val isTv = platform == Platform.TV
    val textFieldFocusRequester = remember { FocusRequester() }
    var textFieldActive by remember { mutableStateOf(false) }

    // When editing starts on TV, focus the text field area
    LaunchedEffect(isEditing) {
        if (isEditing && isTv) {
            textFieldActive = false
            try { textFieldFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initial
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(BrandRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (username.firstOrNull() ?: 'G').uppercaseChar().toString(),
                style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            if (isEditing) {
                if (isTv) {
                    // On TV: show as a focusable box with white border highlight.
                    // Only open keyboard (make editable) when user presses Enter/click.
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = onTempChange,
                        label = { Text("Username", style = TextStyle(color = TextSecond, fontSize = 12.sp)) },
                        singleLine = true,
                        readOnly = !textFieldActive,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = if (textFieldActive) BrandRed else Color.White,
                            unfocusedBorderColor = BorderMid,
                            cursorColor = BrandRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                    when (event.key) {
                                        androidx.compose.ui.input.key.Key.DirectionLeft -> true // consume left
                                        androidx.compose.ui.input.key.Key.Enter,
                                        androidx.compose.ui.input.key.Key.NumPadEnter -> {
                                            if (!textFieldActive) { textFieldActive = true; true }
                                            else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                } else {
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = onTempChange,
                        label = { Text("Username", style = TextStyle(color = TextSecond, fontSize = 12.sp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = BorderMid,
                            cursorColor = BrandRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = username.ifBlank { "Guest" },
                    style = TextStyle(color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(2.dp))
                Text("Tap to edit", style = TextStyle(color = TextDim, fontSize = 12.sp))
            }
        }
        Spacer(Modifier.width(8.dp))
        if (isEditing) {
            IconButton(onClick = onSaveClick, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50))
            }
            IconButton(onClick = onCancelClick, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                Icon(Icons.Default.Close, null, tint = TextDim)
            }
        } else {
            IconButton(onClick = onEditClick, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                Icon(Icons.Default.Edit, null, tint = TextSecond, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Scan Movies row ──────────────────────────────────────────────────────────
@Composable
private fun ScanMoviesRow(
    isEnabled: Boolean,
    scanState: ScanState,
    scanDetailText: String,
    lastRefreshText: String,
    onTriggerScan: () -> Unit
) {
    var scanFocused by remember { mutableStateOf(false) }
    val badgeColor = when (scanState) {
        ScanState.COMPLETED -> Color(0xFF4CAF50)
        ScanState.RUNNING, ScanState.TRIGGERING -> Color(0xFFFFC107)
        ScanState.FAILED -> BrandRed
        ScanState.COOLDOWN -> Color(0xFFFF9800)
        ScanState.IDLE -> TextDim
    }
    val badgeLabel = when (scanState) {
        ScanState.COMPLETED -> "Ready"
        ScanState.RUNNING -> "Running"
        ScanState.TRIGGERING -> "Starting…"
        ScanState.FAILED -> "Failed"
        ScanState.COOLDOWN -> "Cooldown"
        ScanState.IDLE -> "Idle"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (scanFocused) BgRow else Color.Transparent)
            .onFocusChanged { scanFocused = it.hasFocus }
            .tvFocusBorder(shape = RoundedCornerShape(0.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableRow(enabled = isEnabled, onClick = onTriggerScan)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(BgRow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (scanState == ScanState.RUNNING || scanState == ScanState.TRIGGERING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFFFC107),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, null, tint = if (isEnabled) BrandRed else TextDim,
                            modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Scan Latest Movies",
                        style = TextStyle(
                            color = if (isEnabled) TextPrimary else TextDim,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        scanDetailText,
                        style = TextStyle(color = TextSecond, fontSize = 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Status badge
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        badgeLabel,
                        style = TextStyle(color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            // Last refresh line
            Text(
                lastRefreshText,
                style = TextStyle(color = TextDim, fontSize = 11.sp),
                modifier = Modifier.padding(start = 66.dp, bottom = 10.dp)
            )
        }
    }
}

// ─── Watch History screen ─────────────────────────────────────────────────────
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

    LaunchedEffect(items.size) {
        if (items.isEmpty()) { isSelecting = false; selectedIds.clear() }
    }

    BackHandler {
        if (isSelecting) { isSelecting = false; selectedIds.clear() }
        else onDismiss()
    }

    Scaffold(
        containerColor = BgPage,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelecting && selectedIds.isNotEmpty()) "${selectedIds.size} selected" else "Watch History",
                        style = TextStyle(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelecting) { isSelecting = false; selectedIds.clear() }
                        else onDismiss()
                    }, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                        Icon(
                            if (isSelecting) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = null, tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (isSelecting && items.isNotEmpty()) {
                        val allSelected = selectedIds.size == items.size
                        TextButton(onClick = {
                            if (allSelected) selectedIds.clear()
                            else { selectedIds.clear(); selectedIds.addAll(items.map { it.movieId }) }
                        }, modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))) {
                            Text(if (allSelected) "Deselect All" else "Select All")
                        }
                    } else if (items.isNotEmpty()) {
                        IconButton(onClick = { isSelecting = true }, modifier = Modifier.tvFocusBorder(shape = CircleShape)) {
                            Icon(Icons.Default.Edit, null, tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPage)
            )
        },
        bottomBar = {
            if (isSelecting && selectedIds.isNotEmpty()) {
                Surface(color = BgCard, tonalElevation = 0.dp, shadowElevation = 8.dp) {
                    Button(
                        onClick = { pendingDeleteIds = selectedIds.toSet(); showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete ${selectedIds.size} item${if (selectedIds.size > 1) "s" else ""}")
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            AppLoadingScreen(title = "Loading History", message = "Fetching your watch history...",
                isTv = false, modifier = Modifier.padding(padding))
        } else if (items.isEmpty()) {
            AppEmptyScreen(title = "No Watch History",
                message = "Movies you watch will appear here. Start browsing and enjoy!",
                isTv = false, icon = Icons.Default.Info,
                primaryActionLabel = "Browse Content", onPrimaryAction = onDismiss,
                modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).background(BgPage),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.movieId }) { item ->
                    WatchHistoryListItem(
                        item = item,
                        isSelected = item.movieId in selectedIds,
                        isSelecting = isSelecting,
                        onTap = {
                            if (isSelecting) {
                                if (item.movieId in selectedIds) selectedIds.remove(item.movieId)
                                else selectedIds.add(item.movieId)
                            } else onMovieClick(item.movieId)
                        },
                        onLongPress = {
                            if (!isSelecting) { isSelecting = true; selectedIds.add(item.movieId) }
                        },
                        onDelete = { pendingDeleteIds = setOf(item.movieId); showDeleteConfirm = true }
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            val count = pendingDeleteIds.size
            val histDeleteDismissFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                try { histDeleteDismissFocus.requestFocus() } catch (_: Exception) {}
            }
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = BgCard,
                title = { Text("Remove from History?", color = TextPrimary) },
                text = {
                    Text(
                        if (count == 1)
                            "This will remove 1 item from your watch history. Your progress for this movie will be lost."
                        else
                            "This will remove $count items from your watch history. Watch progress for these movies will be lost.",
                        color = TextSecond
                    )
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
                        colors = ButtonDefaults.textButtonColors(contentColor = BrandRed),
                        modifier = Modifier.tvFocusBorder(shape = RoundedCornerShape(50))
                    ) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.focusRequester(histDeleteDismissFocus).tvFocusBorder(shape = RoundedCornerShape(50))
                    ) { Text("Keep") }
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
    var cardFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { cardFocused = it.hasFocus }
            .focusProperties { canFocus = false }
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1A0A0A) else BgCard
        ),
        border = when {
            isSelected -> BorderStroke(1.5.dp, BrandRed)
            cardFocused -> BorderStroke(1.5.dp, Color.White)
            else -> BorderStroke(0.5.dp, BorderSubtle)
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelecting) {
                Checkbox(
                    checked = isSelected, onCheckedChange = { onTap() },
                    modifier = Modifier.size(20.dp),
                    colors = CheckboxDefaults.colors(checkedColor = BrandRed)
                )
                Spacer(Modifier.width(4.dp))
            }

            AsyncImage(
                model = item.posterUrl.ifBlank { null },
                contentDescription = item.movieName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(60.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTap() }
                    .tvFocusBorder(shape = RoundedCornerShape(6.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.movieName,
                    style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatTimestamp(item.lastWatched),
                    style = TextStyle(color = TextSecond, fontSize = 11.sp)
                )
                // Progress bar
                if (item.completionPercent > 0f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (item.completionPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = BrandRed,
                        trackColor = BorderMid
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${item.completionPercent.toInt()}% watched",
                        style = TextStyle(color = TextDim, fontSize = 10.sp)
                    )
                }
            }

            if (!isSelecting) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp).tvFocusBorder(shape = CircleShape)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFF993333), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
