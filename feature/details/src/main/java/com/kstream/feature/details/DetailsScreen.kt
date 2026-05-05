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
            onDownloadClick = viewModel::downloadMovie
        )
    } else {
        DetailsScreenMobile(
            uiState = uiState,
            onBackClick = onBackClick,
            onWatchClick = onWatchClick,
            onDownloadClick = viewModel::downloadMovie
        )
    }
}

@Composable
fun DetailsScreenMobile(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onWatchClick: (String, String) -> Unit,
    onDownloadClick: (String) -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
        }
    } else if (uiState.movieWithMedia != null) {
        val movie = uiState.movieWithMedia.movie
        val mediaList = uiState.movieWithMedia.media

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                Text(text = "${movie.year} • ${movie.duration} • ${movie.language}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = movie.synopsis, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(text = "Select Quality", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                mediaList.forEach { media ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Button(
                            onClick = { onWatchClick(movie.id, media.quality) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Watch ${media.quality}")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onDownloadClick(media.quality) }
                        ) {
                            Text(text = "Download")
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
    onDownloadClick: (String) -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
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
                TvText(text = "${movie.year} • ${movie.duration} • ${movie.language}", style = TvMaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(32.dp))
                TvText(text = movie.synopsis, style = TvMaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(48.dp))

                TvText(text = "Select Quality", style = TvMaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                mediaList.forEach { media ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        TvButton(
                            onClick = { onWatchClick(movie.id, media.quality) },
                            modifier = Modifier.weight(1f)
                        ) {
                            TvText(text = "Watch ${media.quality}")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        androidx.tv.material3.OutlinedButton(
                            onClick = { onDownloadClick(media.quality) }
                        ) {
                            TvText(text = "Download")
                        }
                    }
                }
            }
        }
    }
}
