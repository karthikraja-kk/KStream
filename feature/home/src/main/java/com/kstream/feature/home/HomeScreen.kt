package com.kstream.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kstream.core.ui.R
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.MovieTileMobile
import com.kstream.core.ui.components.MovieTileTv
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items as tvItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun HomeRoute(
    onMovieClick: (String) -> Unit,
    onWatchClick: (String, String) -> Unit, // movieId, quality
    onSeeMoreClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val platform = LocalPlatform.current
    val isOffline = !isOnline
    var showExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = { 
                    showExitDialog = false
                    activity?.finishAffinity()
                }) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val movieClickHandler: (String) -> Unit = { movieId ->
        onMovieClick(movieId)
    }

    if (platform == Platform.TV) {
        HomeScreenTv(
            uiState = uiState,
            onMovieClick = movieClickHandler,
            onSeeMoreClick = onSeeMoreClick,
            onRetry = viewModel::refreshContent,
            onRefresh = viewModel::refreshContent,
            isOffline = isOffline,
            onGoToDownloads = onDownloadsClick
        )
    } else {
        HomeScreenMobile(
            uiState = uiState,
            onMovieClick = movieClickHandler,
            onSeeMoreClick = onSeeMoreClick,
            onSearchClick = onSearchClick,
            onDownloadsClick = onDownloadsClick,
            onSettingsClick = onSettingsClick,
            onRetry = viewModel::refreshContent,
            onRefresh = viewModel::refreshContent,
            isOffline = isOffline,
            onGoToDownloads = onDownloadsClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenMobile(
    uiState: HomeUiState,
    onMovieClick: (String) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    isOffline: Boolean = false,
    onGoToDownloads: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(R.drawable.kstream_logo_horizontal),
                        contentDescription = "KStream",
                        modifier = Modifier.height(52.dp).widthIn(max = 240.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSearchClick,
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onDownloadsClick,
                    icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettingsClick,
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        } else if (isOffline && uiState.rails.isEmpty()) {
            OfflineScreen(
                onRetry = onRetry,
                onGoToDownloads = onGoToDownloads
            )
        } else if (uiState.error != null && !isOffline) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(text = uiState.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
} else if (uiState.rails.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = "No movies found. Pull to refresh or check your connection.",
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isOffline) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📡 You're offline — showing cached content",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (uiState.userName.isNotBlank()) "Hello, ${uiState.userName}!" else "Hello!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "What would you like to watch today?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(uiState.rails) { rail ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = rail.title,
                                style = MaterialTheme.typography.titleLarge
                            )
                            val showSeeMore = true
                            if (showSeeMore) {
                                val query = rail.seeMoreQuery ?: "all:*"
                                TextButton(onClick = { onSeeMoreClick(query) }) {
                                    Text("See More")
                                }
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rail.movies, key = { it.id }) { movie ->
                                MovieTileMobile(
                                    movie = movie,
                                    onClick = onMovieClick,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreenTv(
    uiState: HomeUiState,
    onMovieClick: (String) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    isOffline: Boolean = false,
    onGoToDownloads: () -> Unit = {}
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
        }
    } else if (isOffline && uiState.rails.isEmpty()) {
        TvOfflineScreen(
            onRetry = onRetry,
            onGoToDownloads = onGoToDownloads
        )
    } else if (uiState.error != null && !isOffline) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                TvText(text = uiState.error ?: "Something went wrong", color = TvMaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                androidx.tv.material3.Button(onClick = onRetry) {
                    TvText("Retry")
                }
            }
        }
    } else if (uiState.rails.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            TvText(
                text = "No movies found. Check your connection.",
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                color = TvMaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.kstream_logo_horizontal),
                    contentDescription = "KStream",
                    modifier = Modifier.height(56.dp).widthIn(max = 240.dp),
                    contentScale = ContentScale.Fit
                )
                androidx.tv.material3.Button(
                    onClick = onRefresh,
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText("Refresh")
                }
            }
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)) {
                        TvText(
                            text = if (uiState.userName.isNotBlank()) "Hello, ${uiState.userName}!" else "Hello!",
                            style = TvMaterialTheme.typography.displaySmall,
                            color = TvMaterialTheme.colorScheme.onBackground
                        )
                        TvText(
                            text = "What would you like to watch today?",
                            style = TvMaterialTheme.typography.bodyLarge,
                            color = TvMaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                tvItems(uiState.rails) { rail ->
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            TvText(
                                text = rail.title,
                                style = TvMaterialTheme.typography.titleLarge,
                                color = TvMaterialTheme.colorScheme.onSurface
                            )
                            val showSeeMore = true
                            if (showSeeMore) {
                                val query = rail.seeMoreQuery ?: "all:*"
                                androidx.tv.material3.Button(onClick = { onSeeMoreClick(query) }) {
                                    TvText("See More")
                                }
                            }
                        }
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            tvItems(rail.movies, key = { it.id }) { movie ->
                                MovieTileTv(movie = movie, onClick = onMovieClick)
                            }
                        }
                    }
                }
            }
        }
    }
}
