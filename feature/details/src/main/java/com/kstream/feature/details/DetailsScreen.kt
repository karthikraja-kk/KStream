package com.kstream.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons

@Composable
fun DetailsRoute(
    onBackClick: () -> Unit,
    onWatchClick: (String, String, Boolean) -> Unit,
    onGoToDownloads: (String, String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val platform = LocalPlatform.current
    val isOffline = !uiState.isOnline

    if (platform == Platform.TV) {
        DetailsScreenTv(
            uiState = uiState,
            onBackClick = onBackClick,
            onWatchClick = onWatchClick,
            onQualitySelected = viewModel::onQualitySelected,
            onDownloadClick = {
                if (uiState.isInDownloads) {
                    onGoToDownloads(uiState.movieWithMedia?.movie?.id ?: "", uiState.selectedQuality ?: "")
                } else {
                    viewModel.downloadMovie()
                }
            },
            isOffline = isOffline,
            onGoToDownloads = { onGoToDownloads(uiState.movieWithMedia?.movie?.id ?: "", uiState.selectedQuality ?: "") },
            onRetry = { viewModel.refreshMovieDetails() },
            onStartOver = { viewModel.onStartOver() }
        )
    } else {
        DetailsScreenMobile(
            uiState = uiState,
            onBackClick = onBackClick,
            onWatchClick = onWatchClick,
            onQualitySelected = viewModel::onQualitySelected,
            onDownloadClick = {
                if (uiState.isInDownloads) {
                    onGoToDownloads(uiState.movieWithMedia?.movie?.id ?: "", uiState.selectedQuality ?: "")
                } else {
                    viewModel.downloadMovie()
                }
            },
            isOffline = isOffline,
            onGoToDownloads = { onGoToDownloads(uiState.movieWithMedia?.movie?.id ?: "", uiState.selectedQuality ?: "") },
            onRetry = { viewModel.refreshMovieDetails() },
            onStartOver = { viewModel.onStartOver() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreenMobile(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onWatchClick: (String, String, Boolean) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDownloadClick: () -> Unit,
    isOffline: Boolean = false,
    onGoToDownloads: () -> Unit = {},
    onRetry: () -> Unit = {},
    onStartOver: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.movieWithMedia?.movie?.movieName ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isOffline && uiState.movieWithMedia == null) {
            OfflineScreen(
                onRetry = onRetry,
                onGoToDownloads = onGoToDownloads,
                modifier = Modifier.padding(padding)
            )
        } else if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Error loading movie", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uiState.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) {
                        Text("Go Back")
                    }
                }
            }
        } else if (uiState.movieWithMedia != null) {
            val movie = uiState.movieWithMedia.movie
            val mediaList = uiState.movieWithMedia.media

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = movie.movieName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${movie.year} • ${movie.duration} • ${movie.language} • ${movie.type}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = movie.synopsis, style = MaterialTheme.typography.bodyLarge)
                    
                    if (movie.director.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Director: ${movie.director.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    if (movie.castMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Cast: ${movie.castMembers.take(5).joinToString(", ")}${if (movie.castMembers.size > 5) "..." else ""}", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    if (movie.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Genres: ${movie.genres.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "Select Quality", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mediaList.forEach { media ->
                            FilterChip(
                                selected = uiState.selectedQuality == media.quality,
                                onClick = { onQualitySelected(media.quality) },
                                label = { Text(media.quality) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val isResume = uiState.hasWatchProgress
                        var showMenu by remember { mutableStateOf(false) }
                        
                        if (isResume) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it, false) } },
                                    modifier = Modifier.weight(1f),
                                    enabled = uiState.selectedQuality != null,
                                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                                ) {
                                    Text(text = "Resume")
                                }
                                
                                Surface(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.height(40.dp).width(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                                    enabled = uiState.selectedQuality != null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "More options", tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Start Over") },
                                            onClick = {
                                                showMenu = false
                                                onStartOver()
                                                uiState.selectedQuality?.let { onWatchClick(movie.id, it, true) }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it, false) } },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.selectedQuality != null
                            ) {
                                Text(text = "Watch Now")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val downloadBtnText = when {
                            uiState.downloadState == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> "Go to downloads"
                            uiState.downloadState == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> "Downloading ${(uiState.downloadProgress * 100).toInt()}%"
                            uiState.isInDownloads -> "In Downloads"
                            else -> {
                                val size = uiState.selectedFileSize?.let { formatSizeString(it) }
                                if (size != null) "Download ($size)" else "Download"
                            }
                        }

                        OutlinedButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.weight(1f),
                            enabled = uiState.selectedQuality != null
                        ) {
                            Text(text = downloadBtnText, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }

                }
            }
        }
    }
}

private fun formatSizeString(sizeStr: String): String {
    val numeric = sizeStr.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return sizeStr
    val unit = sizeStr.filter { it.isLetter() }.uppercase()
    
    return if (unit == "MB" && numeric >= 1024) {
        String.format(java.util.Locale.US, "%.2f GB", numeric / 1024.0)
    } else {
        sizeStr
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreenTv(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onWatchClick: (String, String, Boolean) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDownloadClick: () -> Unit,
    isOffline: Boolean = false,
    onGoToDownloads: () -> Unit = {},
    onRetry: () -> Unit = {},
    onStartOver: () -> Unit = {}
) {
    if (isOffline && uiState.movieWithMedia == null) {
        TvOfflineScreen(
            onRetry = onRetry,
            onGoToDownloads = onGoToDownloads
        )
    } else if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    } else if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TvText(text = "Error loading movie", style = TvMaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                TvText(text = uiState.error, style = TvMaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(32.dp))
                TvButton(onClick = onBackClick) {
                    TvText("Go Back")
                }
            }
        }
    } else if (uiState.movieWithMedia != null) {
        val movie = uiState.movieWithMedia.movie
        val mediaList = uiState.movieWithMedia.media

        Row(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxHeight().weight(1f),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.5f)
                    .padding(48.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TvText(text = movie.movieName, style = TvMaterialTheme.typography.displayMedium)
                TvText(
                    text = "${movie.year} • ${movie.duration} • ${movie.language} • ${movie.type}",
                    style = TvMaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(32.dp))
                TvText(text = movie.synopsis, style = TvMaterialTheme.typography.bodyLarge)
                
                if (movie.director.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TvText(text = "Director: ${movie.director.joinToString(", ")}", style = TvMaterialTheme.typography.bodyLarge)
                }
                
                if (movie.castMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TvText(text = "Cast: ${movie.castMembers.take(5).joinToString(", ")}${if (movie.castMembers.size > 5) "..." else ""}", style = TvMaterialTheme.typography.bodyLarge)
                }
                
                if (movie.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TvText(text = "Genres: ${movie.genres.joinToString(", ")}", style = TvMaterialTheme.typography.bodyLarge)
                }
                
                Spacer(modifier = Modifier.height(48.dp))

                TvText(text = "Select Quality", style = TvMaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    mediaList.forEach { media ->
                        androidx.tv.material3.FilterChip(
                            selected = uiState.selectedQuality == media.quality,
                            onClick = { onQualitySelected(media.quality) },
                            content = { TvText(media.quality) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val isResume = uiState.hasWatchProgress
                    var showMenu by remember { mutableStateOf(false) }
                    
                    if (isResume) {
                        Row(
                            modifier = Modifier.weight(1f).height(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvButton(
                                onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it, false) } },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                enabled = uiState.selectedQuality != null,
                                shape = androidx.tv.material3.ButtonDefaults.shape(
                                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                                )
                            ) {
                                TvText(text = "Resume")
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                                    .background(TvMaterialTheme.colorScheme.primary)
                                    .clickable { showMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "More options", tint = TvMaterialTheme.colorScheme.onPrimary)
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { TvText("Start Over") },
                                        onClick = {
                                            showMenu = false
                                            onStartOver()
                                            uiState.selectedQuality?.let { onWatchClick(movie.id, it, true) }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        TvButton(
                            onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it, false) } },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = uiState.selectedQuality != null
                        ) {
                            TvText(text = "Watch Now")
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    val downloadBtnText = when {
                        uiState.downloadState == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> "Go to downloads"
                        uiState.downloadState == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> "Downloading ${(uiState.downloadProgress * 100).toInt()}%"
                        uiState.isInDownloads -> "In Downloads"
                        else -> {
                            val size = uiState.selectedFileSize?.let { formatSizeString(it) }
                            if (size != null) "Download ($size)" else "Download"
                        }
                    }

                    androidx.tv.material3.OutlinedButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.selectedQuality != null
                    ) {
                        TvText(text = downloadBtnText)
                    }
                }
            }
        }
    }
}