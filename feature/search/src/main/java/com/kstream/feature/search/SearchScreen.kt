package com.kstream.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.MovieTileMobile
import com.kstream.core.ui.components.MovieTileTv
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import com.kstream.core.model.Movie
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items as tvItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text as TvText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(
    onMovieClick: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val platform = LocalPlatform.current
    val isOnline by viewModel.isOnline.collectAsState(initial = true)
    val isOffline = !isOnline && uiState.query.isNotBlank()

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.setInitialQuery(initialQuery)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.padding(16.dp)
        )

        if (uiState.recentSearches.isNotEmpty() && uiState.query.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.recentSearches.take(5).forEach { recent ->
                    SuggestionChip(
                        onClick = { viewModel.setInitialQuery(recent) },
                        label = { Text(recent) }
                    )
                }
            }
        }
        
        if (isOffline) {
            OfflineScreen(
                onRetry = { viewModel.onQueryChange(uiState.query) },
                onGoToDownloads = onDownloadsClick
            )
        } else if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "No results found for \"${uiState.query}\"",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            if (platform == Platform.TV) {
                SearchScreenTv(
                    uiState = uiState,
                    onMovieClick = { movie -> viewModel.onMovieClick(movie, onMovieClick) },
                    onRetry = { viewModel.onQueryChange(uiState.query) },
                    isOffline = false,
                    onGoToDownloads = onDownloadsClick
                )
            } else {
                SearchScreenMobile(
                    uiState = uiState,
                    onMovieClick = { movie -> viewModel.onMovieClick(movie, onMovieClick) },
                    onRetry = { viewModel.onQueryChange(uiState.query) },
                    isOffline = false,
                    onGoToDownloads = onDownloadsClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search movies...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true
    )
}

@Composable
fun SearchScreenMobile(
    uiState: SearchUiState,
    onMovieClick: (com.kstream.core.model.Movie) -> Unit,
    onRetry: () -> Unit = {},
    isOffline: Boolean = false,
    onGoToDownloads: () -> Unit = {}
) {
    if (isOffline) {
        OfflineScreen(
            onRetry = onRetry,
            onGoToDownloads = onGoToDownloads
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.results) { movie ->
                MovieTileMobile(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreenTv(
    uiState: SearchUiState,
    onMovieClick: (com.kstream.core.model.Movie) -> Unit,
    onRetry: () -> Unit = {},
    isOffline: Boolean = false,
    onGoToDownloads: () -> Unit = {}
) {
    if (isOffline) {
        TvOfflineScreen(
            onRetry = onRetry,
            onGoToDownloads = onGoToDownloads
        )
    } else {
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(5),
            contentPadding = PaddingValues(48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            tvItems(uiState.results) { movie ->
                MovieTileTv(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}