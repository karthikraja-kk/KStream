package com.kstream.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.kstream.core.ui.components.AppEmptyScreen
import com.kstream.core.ui.components.AppErrorScreen
import com.kstream.core.ui.components.AppLoadingScreen
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import com.kstream.core.ui.components.tvFocusScale
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items as tvItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import java.util.Calendar
import android.app.Activity

private data class Greeting(val title: String, val subtitle: String)

private fun getTimeBasedGreeting(userName: String): Greeting {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val name = userName.trim()
    val hasName = name.isNotBlank()

    val greetings = when (hour) {
        in 0..4 -> listOf(
            Greeting(
                if (hasName) "Still awake, $name?" else "Still awake?",
                "The best movies happen after midnight 🌙"
            ),
            Greeting(
                if (hasName) "Midnight movie mode, $name!" else "Midnight movie mode!",
                "Sleep is overrated anyway 😏"
            ),
            Greeting(
                if (hasName) "Night owl alert, $name!" else "Night owl alert!",
                "Perfect time for a horror flick 👻"
            )
        )
        in 5..8 -> listOf(
            Greeting(
                if (hasName) "Rise and stream, $name!" else "Rise and stream!",
                "Coffee + movie = perfect morning ☕"
            ),
            Greeting(
                if (hasName) "Up early, $name?" else "Up early?",
                "Catch a movie before the world wakes up 🌅"
            ),
            Greeting(
                if (hasName) "Early bird, $name!" else "Early bird!",
                "The remote is all yours 🎬"
            )
        )
        in 9..11 -> listOf(
            Greeting(
                if (hasName) "Good morning, $name!" else "Good morning!",
                "Time to binge something amazing 🍿"
            ),
            Greeting(
                if (hasName) "Morning, $name!" else "Morning!",
                "Productivity can wait... right? 😄"
            ),
            Greeting(
                if (hasName) "Hey $name!" else "Hey!",
                "A movie a day keeps the boredom away 🎥"
            )
        )
        in 12..16 -> listOf(
            Greeting(
                if (hasName) "Good afternoon, $name!" else "Good afternoon!",
                "Perfect time for a movie marathon 🎬"
            ),
            Greeting(
                if (hasName) "Afternoon chill, $name!" else "Afternoon chill!",
                "Grab some snacks and hit play 🍕"
            ),
            Greeting(
                if (hasName) "Hey there, $name!" else "Hey there!",
                "Siesta + cinema = bliss 😎"
            )
        )
        in 17..20 -> listOf(
            Greeting(
                if (hasName) "Good evening, $name!" else "Good evening!",
                "You've earned some screen time 🛋️"
            ),
            Greeting(
                if (hasName) "Evening vibes, $name!" else "Evening vibes!",
                "Dinner and a movie? Don't mind if we do 🍽️"
            ),
            Greeting(
                if (hasName) "Welcome back, $name!" else "Welcome back!",
                "Prime time for prime entertainment 🌟"
            )
        )
        else -> listOf(
            Greeting(
                if (hasName) "Movie night, $name!" else "Movie night!",
                "Lights off, popcorn ready 🍿"
            ),
            Greeting(
                if (hasName) "Settling in, $name?" else "Settling in?",
                "Time for tonight's feature presentation 🎞️"
            ),
            Greeting(
                if (hasName) "Night mode activated, $name!" else "Night mode activated!",
                "Let the binge begin 📺"
            )
        )
    }
    return greetings.random()
}

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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
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
            AppLoadingScreen(
                title = "Loading your home",
                message = "Fetching your rails and recommendations...",
                isTv = false,
                modifier = Modifier.padding(padding)
            )
        } else if (isOffline && uiState.rails.isEmpty()) {
            OfflineScreen(
                onRetry = onRetry,
                onGoToDownloads = onGoToDownloads
            )
        } else if (uiState.error != null && !isOffline) {
            AppErrorScreen(
                title = "Couldn't load home",
                message = uiState.error ?: "Something went wrong.",
                isTv = false,
                primaryActionLabel = "Retry",
                onPrimaryAction = onRetry,
                secondaryActionLabel = if (isOffline) "Downloads" else null,
                onSecondaryAction = if (isOffline) onGoToDownloads else null,
                modifier = Modifier.padding(padding)
            )
        } else if (uiState.rails.isEmpty()) {
            if (isOffline) {
                OfflineScreen(
                    onRetry = onRetry,
                    onGoToDownloads = onGoToDownloads
                )
            } else {
                AppEmptyScreen(
                    title = "Nothing here yet",
                    message = "We couldn't find any movies to show right now. Try refreshing or search for something new.",
                    isTv = false,
                    primaryActionLabel = "Refresh",
                    onPrimaryAction = onRetry,
                    secondaryActionLabel = "Search",
                    onSecondaryAction = onSearchClick,
                    modifier = Modifier.padding(padding)
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
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                item {
                    val greeting = remember { getTimeBasedGreeting(uiState.userName) }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = greeting.title,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = greeting.subtitle,
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
                                    modifier = Modifier.width(120.dp),
                                    watchProgress = uiState.watchProgressMap[movie.id]?.completionPercent?.div(100f)
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
        AppLoadingScreen(
            title = "Loading your home",
            message = "Fetching your rails and recommendations...",
            isTv = true
        )
    } else if (isOffline && uiState.rails.isEmpty()) {
        TvOfflineScreen(
            onRetry = onRetry,
            onGoToDownloads = onGoToDownloads
        )
    } else if (uiState.error != null && !isOffline) {
        AppErrorScreen(
            title = "Couldn't load home",
            message = uiState.error ?: "Something went wrong.",
            isTv = true,
            primaryActionLabel = "Retry",
            onPrimaryAction = onRetry
        )
    } else if (uiState.rails.isEmpty()) {
        if (isOffline) {
            TvOfflineScreen(
                onRetry = onRetry,
                onGoToDownloads = onGoToDownloads
            )
        } else {
            AppEmptyScreen(
                title = "Nothing here yet",
                message = "We couldn't find any movies to show right now. Try refreshing or open downloads.",
                isTv = true,
                primaryActionLabel = "Refresh",
                onPrimaryAction = onRetry,
                secondaryActionLabel = "Downloads",
                onSecondaryAction = onGoToDownloads
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 16.dp),
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
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.tvFocusScale(),
                                    colors = androidx.tv.material3.ButtonDefaults.colors(
                                        containerColor = Color(0xFFE50914),
                                        contentColor = Color.White,
                        focusedContainerColor = Color(0xFFFF1A1A),
                        focusedContentColor = Color.White
                    )
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
            val tvListState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()
            TvLazyColumn(
                state = tvListState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    val greeting = remember { getTimeBasedGreeting(uiState.userName) }
                    Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)) {
                        TvText(
                            text = greeting.title,
                            style = TvMaterialTheme.typography.displaySmall,
                            color = TvMaterialTheme.colorScheme.onBackground
                        )
                        TvText(
                            text = greeting.subtitle,
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
                                androidx.tv.material3.Button(
                                    onClick = { onSeeMoreClick(query) },
                                    modifier = Modifier.tvFocusScale(),
                                    colors = androidx.tv.material3.ButtonDefaults.colors(
                                        containerColor = Color(0xFFE50914),
                                        contentColor = Color.White,
                                        focusedContainerColor = Color(0xFFFF1A1A),
                                        focusedContentColor = Color.White
                                    )
                                ) {
                                    TvText("See More")
                                }
                            }
                        }
                        TvLazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            tvItems(rail.movies, key = { it.id }) { movie ->
                                MovieTileTv(
                                    movie = movie,
                                    onClick = onMovieClick,
                                    watchProgress = uiState.watchProgressMap[movie.id]?.completionPercent?.div(100f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
