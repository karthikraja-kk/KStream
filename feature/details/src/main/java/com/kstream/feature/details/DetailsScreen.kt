package com.kstream.feature.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import androidx.activity.compose.BackHandler

import androidx.compose.material.icons.filled.ArrowBack

@Composable
fun DetailsRoute(
    onBackClick: () -> Unit,
    onWatchClick: (String, String) -> Unit, // movieId, quality
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val platform = LocalPlatform.current

    if (platform == Platform.TV) {
        DetailsScreenTv(
            uiState = uiState,
            onBackClick = onBackClick,
            onWatchClick = onWatchClick,
            onQualitySelected = viewModel::onQualitySelected,
            onDownloadClick = viewModel::downloadMovie
        )
    } else {
        DetailsScreenMobile(
            uiState = uiState,
            onBackClick = onBackClick,
            onWatchClick = onWatchClick,
            onQualitySelected = viewModel::onQualitySelected,
            onDownloadClick = viewModel::downloadMovie
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreenMobile(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onWatchClick: (String, String) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDownloadClick: () -> Unit
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
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
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
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Button(
                            onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it) } },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.selectedQuality != null
                        ) {
                            Text(text = "Watch Now")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = onDownloadClick,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState.selectedQuality != null
                            ) {
                                Text(text = "Download")
                            }
                            if (uiState.selectedFileSize != null) {
                                Text(
                                    text = uiState.selectedFileSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                                )
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
fun DetailsScreenTv(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onWatchClick: (String, String) -> Unit,
    onQualitySelected: (String) -> Unit,
    onDownloadClick: () -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
        }
    } else if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TvButton(
                        onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it) } },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.selectedQuality != null
                    ) {
                        TvText(text = "Watch Now")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        androidx.tv.material3.OutlinedButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.selectedQuality != null
                        ) {
                            TvText(text = "Download")
                        }
                        if (uiState.selectedFileSize != null) {
                            TvText(
                                text = uiState.selectedFileSize,
                                style = TvMaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}
