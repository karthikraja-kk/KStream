package com.kstream.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val platform = LocalPlatform.current
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val isOffline = !isOnline && uiState.query.isNotBlank()

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.setInitialQuery(initialQuery)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                isTv = platform == Platform.TV
            )
            SortButton(
                currentSort = uiState.sortOption,
                onSortChange = viewModel::onSortChange
            )
        }

        if (uiState.recentSearches.isNotEmpty() && uiState.query.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.recentSearches.take(5).forEach { recent ->
                    var chipFocused by remember { mutableStateOf(false) }
                    SuggestionChip(
                        onClick = { viewModel.setInitialQuery(recent) },
                        label = { Text(recent, color = if (chipFocused) Color.White else Color.Unspecified) },
                        modifier = Modifier
                            .onFocusChanged { chipFocused = it.isFocused },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (chipFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            borderColor = if (chipFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.outline,
                            borderWidth = if (chipFocused) 2.dp else 1.dp
                        )
                    )
                }
            }
        }

        // Type-ahead suggestions from search history
        if (uiState.recentSearches.isNotEmpty() && uiState.query.isNotBlank()) {
            val matchingSuggestions = uiState.recentSearches.filter {
                it.contains(uiState.query, ignoreCase = true) && !it.equals(uiState.query, ignoreCase = true)
            }
            if (matchingSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matchingSuggestions.take(3).forEach { suggestion ->
                        var chipFocused by remember { mutableStateOf(false) }
                        SuggestionChip(
                            onClick = { viewModel.setInitialQuery(suggestion) },
                            label = { Text(suggestion, color = if (chipFocused) Color.White else Color.Unspecified) },
                            modifier = Modifier
                                .onFocusChanged { chipFocused = it.isFocused },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (chipFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                borderColor = if (chipFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.outline,
                                borderWidth = if (chipFocused) 2.dp else 1.dp
                            )
                        )
                    }
                }
            }
        }
        
        // "Did you mean?" suggestion banner
        if (uiState.suggestedQuery != null && uiState.isFuzzyMatch) {
            var bannerFocused by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusable()
                    .onFocusChanged { bannerFocused = it.isFocused }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                        ) {
                            viewModel.setInitialQuery(uiState.suggestedQuery!!)
                            true
                        } else false
                    },
                color = if (bannerFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Did you mean: ")
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )) {
                            append(uiState.suggestedQuery!!)
                        }
                        append("?")
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (isOffline) {
            OfflineScreen(
                onRetry = { viewModel.onQueryChange(uiState.query) },
                onGoToDownloads = onDownloadsClick
            )
        } else if (uiState.error != null) {
            val errorMessage = uiState.error ?: ""
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.onQueryChange(uiState.query) }) {
                        Text("Retry")
                    }
                }
            }
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
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    var isEditing by remember { mutableStateOf(false) }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isTv) Modifier.onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        !isEditing &&
                        (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                    ) {
                        isEditing = true
                        true
                    } else false
                }.onFocusChanged { if (!it.isFocused) isEditing = false }
                else Modifier
            ),
        readOnly = isTv && !isEditing,
        placeholder = { Text(if (isTv && !isEditing) "Search movies... (Press OK to type)" else "Search movies...") },
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
            items(uiState.results, key = { it.id }) { movie ->
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
            columns = TvGridCells.Fixed(4),
            contentPadding = PaddingValues(48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            tvItems(uiState.results, key = { it.id }) { movie ->
                MovieTileTv(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}

@Composable
private fun SortButton(
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var sortFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (sortFocused) Modifier.background(Color(0xFFE50914))
                else Modifier
            )
            .focusable()
            .onFocusChanged { sortFocused = it.isFocused }
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                contentDescription = "Sort",
                tint = when {
                    sortFocused -> Color.White
                    currentSort != SortOption.NONE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortOption.entries.forEach { option ->
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
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}