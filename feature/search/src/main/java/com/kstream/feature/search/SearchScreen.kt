package com.kstream.feature.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.kstream.core.ui.components.tvFocusBorder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.MovieTileMobile
import com.kstream.core.ui.components.MovieTileTv
import com.kstream.core.ui.components.AppEmptyScreen
import com.kstream.core.ui.components.AppErrorScreen
import com.kstream.core.ui.components.AppLoadingScreen
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import com.kstream.core.ui.components.tvFocusScale
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

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0A0A0A))
        .then(
            if (platform == Platform.TV) Modifier.onKeyEvent { keyEvent ->
                keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft
            } else Modifier
        )
    ) {
        // "Discover" heading — mobile only (TV relies on sidebar for nav context)
        if (platform != Platform.TV) {
            Text(
                text = "Discover",
                style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
            )
        }

        // Request initial focus on the search bar for TV so D-pad highlights it immediately
        val searchBarFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (platform == Platform.TV) {
                try { searchBarFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }

        // Search bar + sort button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(searchBarFocusRequester),
                isTv = platform == Platform.TV
            )
            SortButton(
                currentSort = uiState.sortOption,
                onSortChange = viewModel::onSortChange
            )
        }

        if (uiState.query.isBlank()) {
            // Idle discovery state — replaces the blank screen
            IdleDiscoveryState(
                recentSearches = uiState.recentSearches,
                onRecentClick = { viewModel.setInitialQuery(it) },
                onClearRecents = { viewModel.clearRecentSearches() },
                isTv = platform == Platform.TV
            )
        } else {
            // Type-ahead suggestions from search history
            if (uiState.recentSearches.isNotEmpty()) {
                val matchingSuggestions = uiState.recentSearches.filter {
                    it.contains(uiState.query, ignoreCase = true) &&
                        !it.equals(uiState.query, ignoreCase = true)
                }
                if (matchingSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        matchingSuggestions.take(3).forEach { suggestion ->
                            key(suggestion) {
                                var chipFocused by remember { mutableStateOf(false) }
                                SuggestionChip(
                                    onClick = { viewModel.setInitialQuery(suggestion) },
                                    label = {
                                        Text(
                                            suggestion,
                                            color = if (chipFocused) Color.White else Color.Unspecified
                                        )
                                    },
                                    modifier = Modifier
                                        .tvFocusScale()
                                        .onFocusChanged { chipFocused = it.isFocused },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (chipFocused) Color(0xFFFF1A1A)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        borderColor = if (chipFocused) Color.White
                                        else MaterialTheme.colorScheme.outline,
                                        borderWidth = if (chipFocused) 2.dp else 1.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // "Did you mean?" fuzzy match banner
            if (uiState.suggestedQuery != null && uiState.isFuzzyMatch) {
                var bannerFocused by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .tvFocusScale()
                        .focusable()
                        .onFocusChanged { bannerFocused = it.isFocused }
                        .then(
                            if (bannerFocused) Modifier.border(2.dp, Color.White, MaterialTheme.shapes.small)
                            else Modifier
                        )
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

            // Results / error / loading / empty states
            if (isOffline) {
                OfflineScreen(
                    onRetry = { viewModel.onQueryChange(uiState.query) },
                    onGoToDownloads = onDownloadsClick
                )
            } else if (uiState.error != null) {
                AppErrorScreen(
                    title = "Search failed",
                    message = uiState.error ?: "Something went wrong.",
                    isTv = platform == Platform.TV,
                    primaryActionLabel = "Retry",
                    onPrimaryAction = { viewModel.onQueryChange(uiState.query) },
                    secondaryActionLabel = "Downloads",
                    onSecondaryAction = onDownloadsClick
                )
            } else if (uiState.isLoading) {
                AppLoadingScreen(
                    title = "Searching",
                    message = "Finding the best matches for you...",
                    isTv = platform == Platform.TV
                )
            } else if (uiState.results.isEmpty()) {
                AppEmptyScreen(
                    title = "No results found",
                    message = "We couldn't find anything for \"${uiState.query}\". Try a different title, or clear the search and browse again.",
                    isTv = platform == Platform.TV,
                    primaryActionLabel = "Clear search",
                    onPrimaryAction = { viewModel.onQueryChange("") },
                    secondaryActionLabel = "Downloads",
                    onSecondaryAction = onDownloadsClick
                )
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
}

@Composable
private fun IdleDiscoveryState(
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onClearRecents: () -> Unit,
    isTv: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = TextStyle(
                        color = Color(0xFFB3B3B3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearRecents) {
                    Text("Clear all", color = Color(0xFF777777), fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentSearches.take(8).forEach { recent ->
                    key(recent) {
                        var chipFocused by remember { mutableStateOf(false) }
                        SuggestionChip(
                            onClick = { onRecentClick(recent) },
                            label = {
                                Text(
                                    recent,
                                    color = if (chipFocused) Color.White else Color(0xFFCCCCCC),
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier
                                .tvFocusScale()
                                .onFocusChanged { chipFocused = it.isFocused }
                                .then(
                                    if (chipFocused && isTv)
                                        Modifier.border(2.dp, Color.White, RoundedCornerShape(50))
                                    else Modifier
                                ),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (chipFocused) Color(0xFFE50914)
                                else Color(0xFF1E1E1E)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                borderColor = if (chipFocused) Color(0xFFE50914)
                                else Color(0xFF333333),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
            }
        } else {
            // Central illustration when no recent searches exist
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1A1A1A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Start discovering",
                    style = TextStyle(
                        color = Color(0xFF888888),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Search for movies, series, and more",
                    style = TextStyle(color = Color(0xFF555555), fontSize = 13.sp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            isTv && isFocused -> Color.White
            !isTv && isFocused -> Color(0xFFE50914)
            else -> Color(0xFF333333)
        },
        animationSpec = tween(200),
        label = "searchBorder"
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (!it.isFocused) isEditing = false
            }
            .then(
                if (isTv) Modifier.onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        !isEditing &&
                        (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                    ) {
                        isEditing = true
                        true
                    } else false
                }
                else Modifier
            ),
        readOnly = isTv && !isEditing,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        ),
        cursorBrush = SolidColor(Color(0xFFE50914)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(28.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(28.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isFocused) Color(0xFFE50914) else Color(0xFF777777),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (isTv && !isEditing) "Press OK to search" else "Search movies...",
                            style = TextStyle(color = Color(0xFF666666), fontSize = 16.sp)
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFF3A3A3A), CircleShape)
                            .tvFocusBorder(shape = CircleShape)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
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
        key(uiState.sortGeneration) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
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
        key(uiState.sortGeneration) {
            val numColumns = 4
            val tileFocusRequesters = remember(uiState.results.size) {
                Array(uiState.results.size) { FocusRequester() }
            }
            var focusedIndex by remember { mutableIntStateOf(-1) }

            TvLazyVerticalGrid(
                columns = TvGridCells.Fixed(numColumns),
                modifier = Modifier.onPreviewKeyEvent { event ->
                    // Mirror the Home screen pattern: consume Left to move to the left tile when
                    // not in the leftmost column; pass through when in column 0 so the sidebar opens.
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionLeft &&
                        focusedIndex > 0 &&
                        focusedIndex % numColumns != 0
                    ) {
                        try { tileFocusRequesters[focusedIndex - 1].requestFocus() } catch (_: Exception) {}
                        true
                    } else false
                },
                contentPadding = PaddingValues(48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                tvItems(
                    items = uiState.results.mapIndexed { i, m -> i to m },
                    key = { (_, m) -> m.id }
                ) { (index, movie) ->
                    MovieTileTv(
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        modifier = Modifier
                            .focusRequester(tileFocusRequesters[index])
                            .onFocusChanged { fs ->
                                if (fs.isFocused || fs.hasFocus) focusedIndex = index
                            }
                    )
                }
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
    val borderColor by animateColorAsState(
        targetValue = when {
            sortFocused -> Color.White
            currentSort != SortOption.NONE -> Color(0xFFE50914)
            else -> Color(0xFF333333)
        },
        animationSpec = tween(200),
        label = "sortBorder"
    )

    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(44.dp)
            .background(Color(0xFF1E1E1E), CircleShape)
            .border(1.5.dp, borderColor, CircleShape)
            .tvFocusScale()
            .onFocusChanged { sortFocused = it.isFocused }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_sort_by_size),
                contentDescription = "Sort",
                tint = when {
                    sortFocused -> Color.White
                    currentSort != SortOption.NONE -> Color(0xFFE50914)
                    else -> Color(0xFF777777)
                },
                modifier = Modifier.size(20.dp)
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
