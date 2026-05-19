package com.kstream.feature.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.components.MovieInitialsFallback
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

import kotlinx.coroutines.delay
import android.content.Intent
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DetailsRoute(
    onBackClick: () -> Unit,
    onWatchClick: (String, String, Boolean) -> Unit,
    onGoToDownloads: (String, String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
            onStartOver = { viewModel.onStartOver() },
            onLikeClick = { viewModel.toggleLike() }
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
            onStartOver = { viewModel.onStartOver() },
            onLikeClick = { viewModel.toggleLike() }
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
    onStartOver: () -> Unit = {},
    onLikeClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Floating back button — reused across all states
    @Composable
    fun FloatingBackButton() {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isOffline && uiState.movieWithMedia == null) {
            OfflineScreen(
                onRetry = onRetry,
                onGoToDownloads = onGoToDownloads,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
            Box(modifier = Modifier.align(Alignment.TopStart)) { FloatingBackButton() }
        } else if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            Box(modifier = Modifier.align(Alignment.TopStart)) { FloatingBackButton() }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Unable to load movie", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uiState.error ?: "Something went wrong", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBackClick) {
                            Text("Go Back")
                        }
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        } else if (uiState.movieWithMedia != null) {
            val movie = uiState.movieWithMedia.movie
            val mediaList = uiState.movieWithMedia.media

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Poster — edge to edge, back button floats over it
                Box {
                    var detailsRetryHash by remember { mutableIntStateOf(0) }
                    val detailsContext = LocalContext.current

                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(detailsContext)
                            .data(movie.posterUrl)
                            .setParameter("retry", detailsRetryHash)
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.movieName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentScale = ContentScale.Crop,
                        loading = {
                            KStreamLogoFallback()
                        },
                        error = {
                            if (detailsRetryHash < 3) {
                                LaunchedEffect(detailsRetryHash) {
                                    delay(3000L * (detailsRetryHash + 1))
                                    detailsRetryHash++
                                }
                            }
                            KStreamLogoFallback()
                        },
                        success = {
                            Image(
                                painter = it.painter,
                                contentDescription = movie.movieName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    )

                    // Gradient scrim so back button is always visible
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                        androidx.compose.ui.graphics.Color.Transparent
                                    )
                                )
                            )
                    )

                    // Floating back button pinned to top-start of poster
                    FloatingBackButton()

                    // HD badge pinned to top-end of poster
                    if (movie.type.equals("Original HD", ignoreCase = true)) {
                        com.kstream.core.ui.components.HdBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(8.dp)
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = movie.movieName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onLikeClick) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (uiState.isLiked) "Unlike" else "Like",
                                tint = if (uiState.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            val movieId = movie.id
                            val movieName = movie.movieName
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, movieName)
                                putExtra(Intent.EXTRA_TEXT, "Check out \"$movieName\" on KStream!\nkstream://movie/$movieId")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    Text(
                        text = "${movie.year} • ${movie.duration} • ${movie.language} • ${movie.type}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = movie.synopsis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    
                    if (movie.director.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Director: ${movie.director.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    if (movie.castMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Cast: ${movie.castMembers.take(5).joinToString(", ")}${if (movie.castMembers.size > 5) "..." else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    if (movie.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Genres: ${movie.genres.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (movie.rating.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Rating: ${movie.rating}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (movie.lastUpdated.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Last Updated: ${movie.lastUpdated}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun KStreamLogoFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.kstream.core.ui.R.drawable.kstream_logo),
            contentDescription = "KStream",
            modifier = Modifier.size(100.dp),
            contentScale = ContentScale.Fit,
            alpha = 0.6f
        )
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
    onStartOver: () -> Unit = {},
    onLikeClick: () -> Unit = {}
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
                TvText(text = "Unable to load movie", style = TvMaterialTheme.typography.displayMedium, color = TvMaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                TvText(text = uiState.error ?: "Something went wrong", style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    androidx.tv.material3.OutlinedButton(onClick = onBackClick) {
                        TvText("Go Back")
                    }
                    TvButton(onClick = onRetry) {
                        TvText("Retry")
                    }
                }
            }
        }
    } else if (uiState.movieWithMedia != null) {
        val movie = uiState.movieWithMedia.movie
        val mediaList = uiState.movieWithMedia.media

        Row(modifier = Modifier.fillMaxSize()) {
            var tvDetailsRetryHash by remember { mutableIntStateOf(0) }
            val tvDetailsContext = LocalContext.current

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(tvDetailsContext)
                    .data(movie.posterUrl)
                    .setParameter("retry", tvDetailsRetryHash)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.movieName,
                modifier = Modifier.fillMaxHeight().weight(1f),
                contentScale = ContentScale.Crop,
                loading = {
                    MovieInitialsFallback(title = movie.movieName)
                },
                error = {
                    if (tvDetailsRetryHash < 3) {
                        LaunchedEffect(tvDetailsRetryHash) {
                            delay(3000L * (tvDetailsRetryHash + 1))
                            tvDetailsRetryHash++
                        }
                    }
                    MovieInitialsFallback(title = movie.movieName)
                },
                success = {
                    Image(
                        painter = it.painter,
                        contentDescription = movie.movieName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.5f)
                    .padding(48.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvText(text = movie.movieName, style = TvMaterialTheme.typography.displayMedium, color = TvMaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    IconButton(onClick = onLikeClick) {
                        Icon(
                            imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (uiState.isLiked) "Unlike" else "Like",
                            tint = if (uiState.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                TvText(
                    text = "${movie.year} • ${movie.duration} • ${movie.language} • ${movie.type}",
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = TvMaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                TvText(text = movie.synopsis, style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurface)
                
                if (movie.director.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TvText(text = "Director: ${movie.director.joinToString(", ")}", style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (movie.castMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TvText(text = "Cast: ${movie.castMembers.take(5).joinToString(", ")}${if (movie.castMembers.size > 5) "..." else ""}", style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (movie.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TvText(text = "Genres: ${movie.genres.joinToString(", ")}", style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (movie.rating.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TvText(text = "Rating: ${movie.rating}", style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (movie.lastUpdated.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TvText(text = "Last Updated: ${movie.lastUpdated}", style = TvMaterialTheme.typography.bodyLarge, color = TvMaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(modifier = Modifier.height(48.dp))

                TvText(text = "Select Quality", style = TvMaterialTheme.typography.titleLarge, color = TvMaterialTheme.colorScheme.onSurface)
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