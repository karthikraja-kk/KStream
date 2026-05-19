package com.kstream.core.ui.components
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import com.kstream.core.model.Movie
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText

private const val MAX_IMAGE_RETRIES = 3
private val GradientStart = Color(0xFF333333)
private val GradientEnd = Color(0xFF1A1A1A)

@Composable
fun MovieInitialsFallback(
    title: String,
    modifier: Modifier = Modifier
) {
    val initials = getInitials(title)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getInitials(title: String): String {
    val cleanTitle = title.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
    val words = cleanTitle.split("\\s+".toRegex()).filter { it.isNotBlank() }
    
    val initials = StringBuilder()
    for (word in words) {
        if (word.isNotEmpty()) {
            initials.append(word[0].uppercaseChar())
        }
    }
    
    val yearMatch = Regex("\\d{4}").find(title)
    if (yearMatch != null) {
        val year = yearMatch.value
        initials.append(year.take(2))
    }
    
    return if (initials.length > 4) initials.toString().take(4) else initials.toString()
}

@Composable
fun MovieTileMobile(
    movie: Movie,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var retryHash by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(movie.id) }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl)
                    .setParameter("retry", retryHash)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.movieName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    MovieInitialsFallback(title = movie.movieName)
                },
                error = {
                    if (retryHash < MAX_IMAGE_RETRIES) {
                        LaunchedEffect(retryHash) {
                            delay(3000L * (retryHash + 1))
                            retryHash++
                        }
                    }
                    MovieInitialsFallback(title = movie.movieName)
                },
                success = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = it.painter,
                            contentDescription = movie.movieName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            )
            if (movie.type.equals("Original HD", ignoreCase = true)) {
                HdBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = movie.movieName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieTileTv(
    movie: Movie,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var retryHash by remember { mutableIntStateOf(0) }

    TvSurface(
        onClick = { onClick(movie.id) },
        modifier = modifier
            .width(160.dp)
            .padding(8.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(2f / 3f)
                    .fillMaxWidth()
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(movie.posterUrl)
                        .setParameter("retry", retryHash)
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.movieName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        MovieInitialsFallback(title = movie.movieName)
                    },
                    error = {
                        if (retryHash < MAX_IMAGE_RETRIES) {
                            LaunchedEffect(retryHash) {
                                delay(3000L * (retryHash + 1))
                                retryHash++
                            }
                        }
                        MovieInitialsFallback(title = movie.movieName)
                    },
                    success = {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = it.painter,
                                contentDescription = movie.movieName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                )
                if (movie.type.equals("Original HD", ignoreCase = true)) {
                    HdBadge(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TvText(
                text = movie.movieName,
                style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                color = androidx.tv.material3.MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun HdBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1976D2)) // blue
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = "HD",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}