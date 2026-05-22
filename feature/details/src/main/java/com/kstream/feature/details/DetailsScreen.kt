package com.kstream.feature.details

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import com.kstream.core.ui.components.OttConstants
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.kstream.core.ui.components.AppErrorScreen
import com.kstream.core.ui.components.AppLoadingScreen
import com.kstream.core.ui.components.MovieInitialsFallback
import com.kstream.core.ui.components.OfflineScreen
import com.kstream.core.ui.components.TvOfflineScreen
import com.kstream.core.ui.components.tvFocusScale
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

import kotlinx.coroutines.delay

import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

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
            AppLoadingScreen(
                title = "Loading Movie",
                message = "Fetching details and available quality options...",
                isTv = false,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.align(Alignment.TopStart)) { FloatingBackButton() }
        } else if (uiState.error != null) {
            AppErrorScreen(
                title = "Couldn't Load Movie",
                message = uiState.error ?: "Something went wrong.",
                isTv = false,
                primaryActionLabel = "Retry",
                onPrimaryAction = onRetry,
                secondaryActionLabel = "Go Back",
                onSecondaryAction = onBackClick,
                onOpenDownloads = onGoToDownloads,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            )
            Box(modifier = Modifier.align(Alignment.TopStart)) { FloatingBackButton() }
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
                                tint = if (uiState.isLiked) Color(0xFFE50914) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // Metadata pill badges
                    androidx.compose.foundation.layout.Column {
                        // First row: year, duration, language, rating, type
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (movie.year > 0) MetadataBadge(movie.year.toString())
                            if (movie.duration.isNotBlank()) MetadataBadge(movie.duration)
                            if (movie.language.isNotBlank()) MetadataBadge(movie.language)
                            if (movie.rating.isNotBlank()) MetadataBadge("⭐ ${movie.rating}", highlight = true)
                            if (movie.type.isNotBlank()) MetadataBadge(movie.type)
                        }
                        if (movie.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                movie.genres.forEach { MetadataBadge(it) }
                            }
                        }
                    }
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

/** Pill badge used to display metadata items on the details screen. */
@Composable
private fun MetadataBadge(label: String, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (highlight) Color(0xFFE50914).copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) Color(0xFFE50914) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
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
        AppLoadingScreen(
            title = "Loading Movie",
            message = "Fetching details and available quality options...",
            isTv = true,
            modifier = Modifier.fillMaxSize()
        )
    } else if (uiState.error != null) {
        AppErrorScreen(
            title = "Couldn't Load Movie",
            message = uiState.error ?: "Something went wrong.",
            isTv = true,
            primaryActionLabel = "Retry",
            onPrimaryAction = onRetry,
            secondaryActionLabel = "Go Back",
            onSecondaryAction = onBackClick,
            onOpenDownloads = onGoToDownloads,
            modifier = Modifier.fillMaxSize()
        )
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
                    var heartFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onLikeClick,
                        modifier = Modifier
                            .tvFocusScale()
                            .onFocusChanged { heartFocused = it.isFocused }
                            .then(
                                if (heartFocused) Modifier.border(2.dp, Color(0xFFE50914), RoundedCornerShape(50))
                                else Modifier
                            )
                    ) {
                        Icon(
                            imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (uiState.isLiked) "Unlike" else "Like",
                            tint = if (uiState.isLiked) Color(0xFFE50914) else if (heartFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                // TV metadata pill badges
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (movie.year > 0) MetadataBadge(movie.year.toString())
                    if (movie.duration.isNotBlank()) MetadataBadge(movie.duration)
                    if (movie.language.isNotBlank()) MetadataBadge(movie.language)
                    if (movie.rating.isNotBlank()) MetadataBadge("⭐ ${movie.rating}", highlight = true)
                    if (movie.type.isNotBlank()) MetadataBadge(movie.type)
                }
                if (movie.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        movie.genres.forEach { MetadataBadge(it) }
                    }
                }
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
                
                Spacer(modifier = Modifier.height(48.dp))

                TvText(text = "Select Quality", style = TvMaterialTheme.typography.titleLarge, color = TvMaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    mediaList.forEach { media ->
                        val isSelected = uiState.selectedQuality == media.quality
                        androidx.tv.material3.FilterChip(
                            selected = isSelected,
                            onClick = { onQualitySelected(media.quality) },
                            modifier = Modifier.tvFocusScale(),
                            content = {
                                TvText(
                                    media.quality,
                                    color = if (isSelected) Color.White else TvMaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = androidx.tv.material3.FilterChipDefaults.colors(
                                selectedContainerColor = Color(0xFFE50914),
                                selectedContentColor = Color.White,
                                focusedContainerColor = Color(0xFFFF1A1A),
                                focusedContentColor = Color.White,
                                focusedSelectedContainerColor = Color(0xFFFF1A1A),
                                focusedSelectedContentColor = Color.White
                            ),
                            border = androidx.tv.material3.FilterChipDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = BorderStroke(2.dp, Color.White)
                                ),
                                focusedSelectedBorder = androidx.tv.material3.Border(
                                    border = BorderStroke(2.dp, Color.White)
                                )
                            )
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
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = Color(0xFFE50914),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color(0xFFFF1A1A),
                                    focusedContentColor = Color.White
                                ),
                                shape = androidx.tv.material3.ButtonDefaults.shape(
                                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                                ),
                                border = androidx.tv.material3.ButtonDefaults.border(
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = BorderStroke(OttConstants.FocusBorderWidth, Color.White),
                                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                                    )
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TvText(text = "Resume", color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            var dropdownFocused by remember { mutableStateOf(false) }
                            val dropdownScale by animateFloatAsState(
                                targetValue = if (dropdownFocused) OttConstants.FocusScaleFactor else 1f,
                                animationSpec = tween(durationMillis = 200),
                                label = "dropdownScale"
                            )
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .fillMaxHeight()
                                    .focusable()
                                    .onFocusChanged { dropdownFocused = it.isFocused }
                                    .graphicsLayer { scaleX = dropdownScale; scaleY = dropdownScale }
                                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                                    .background(if (dropdownFocused) Color(0xFFFF1A1A) else Color(0xFFE50914))
                                    .then(if (dropdownFocused) Modifier.border(OttConstants.FocusBorderWidth, Color.White, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) else Modifier)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                                        ) {
                                            showMenu = true
                                            true
                                        } else false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "More options", tint = Color.White)
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(Color(0xFF2A2A2A))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Start Over", color = Color.White) },
                                        onClick = {
                                            showMenu = false
                                            onStartOver()
                                            uiState.selectedQuality?.let { onWatchClick(movie.id, it, true) }
                                        },
                                        modifier = Modifier.background(Color.Transparent)
                                    )
                                }
                            }
                        }
                    } else {
                        TvButton(
                            onClick = { uiState.selectedQuality?.let { onWatchClick(movie.id, it, false) } },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = uiState.selectedQuality != null,
                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFFFF1A1A),
                                focusedContentColor = Color.White
                            ),
                            border = androidx.tv.material3.ButtonDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = BorderStroke(OttConstants.FocusBorderWidth, Color.White),
                                    shape = RoundedCornerShape(24.dp)
                                )
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TvText(text = "Watch Now", color = Color.White)
                            }
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = uiState.selectedQuality != null,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFE50914),
                            focusedContainerColor = Color(0xFFE50914),
                            focusedContentColor = Color.White
                        ),
                        border = androidx.tv.material3.ButtonDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = BorderStroke(2.dp, Color(0xFFE50914))
                            ),
                            focusedBorder = androidx.tv.material3.Border(
                                border = BorderStroke(2.dp, Color.White)
                            )
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TvText(text = downloadBtnText)
                        }
                    }
                }
            }
        }
    }
}
