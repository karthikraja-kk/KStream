package com.kstream.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.MovieTileMobile
import com.kstream.core.ui.components.MovieTileTv
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items as tvItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun HomeRoute(
    onMovieClick: (String) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val platform = LocalPlatform.current

    if (platform == Platform.TV) {
        HomeScreenTv(
            uiState = uiState,
            onMovieClick = onMovieClick,
            onSeeMoreClick = onSeeMoreClick,
            onSearchClick = onSearchClick,
            onDownloadsClick = onDownloadsClick,
            onSettingsClick = onSettingsClick,
            onRetry = viewModel::refreshContent
        )
    } else {
        HomeScreenMobile(
            uiState = uiState,
            onMovieClick = onMovieClick,
            onSeeMoreClick = onSeeMoreClick,
            onSearchClick = onSearchClick,
            onDownloadsClick = onDownloadsClick,
            onSettingsClick = onSettingsClick,
            onRetry = viewModel::refreshContent
        )
    }
}

@Composable
fun HomeScreenMobile(
    uiState: HomeUiState,
    onMovieClick: (String) -> Unit,
    onSeeMoreClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
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
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(text = "Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
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
                            TextButton(onClick = { onSeeMoreClick(rail.title) }) {
                                Text("See More")
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rail.movies) { movie ->
                                MovieTileMobile(movie = movie, onClick = onMovieClick)
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
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRetry: () -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
        }
    } else if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                TvText(text = "Error: ${uiState.error}", color = TvMaterialTheme.colorScheme.error)
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
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
            )
        }
    } else {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.tv.material3.Button(onClick = onSearchClick) {
                    TvText("Search")
                }
                Spacer(modifier = Modifier.width(16.dp))
                androidx.tv.material3.Button(onClick = onDownloadsClick) {
                    TvText("Downloads")
                }
                Spacer(modifier = Modifier.width(16.dp))
                androidx.tv.material3.Button(onClick = onSettingsClick) {
                    TvText("Settings")
                }
            }
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
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
                                style = TvMaterialTheme.typography.titleLarge
                            )
                            androidx.tv.material3.Button(onClick = { onSeeMoreClick(rail.title) }) {
                                TvText("See More")
                            }
                        }
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            tvItems(rail.movies) { movie ->
                                MovieTileTv(movie = movie, onClick = onMovieClick)
                            }
                        }
                    }
                }
            }
        }
    }
}
