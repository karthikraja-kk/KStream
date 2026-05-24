package com.kstream.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kstream.core.model.Movie
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_SCROLL_DELAY = 5000L
private val HERO_HEIGHT_TV = 400.dp
private val HERO_HEIGHT_MOBILE = 340.dp
private val POSTER_HEIGHT_TV = 300.dp
private val POSTER_HEIGHT_MOBILE = 250.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun HeroCarousel(
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    onWatchClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) return

    val platform = LocalPlatform.current
    val isTv = platform == Platform.TV
    val pagerState = rememberPagerState(pageCount = { movies.size })
    val coroutineScope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }

    // Auto-scroll
    LaunchedEffect(pagerState, isFocused) {
        while (true) {
            delay(AUTO_SCROLL_DELAY)
            if (!isFocused) {
                val next = (pagerState.currentPage + 1) % movies.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    val heroHeight = if (isTv) HERO_HEIGHT_TV else HERO_HEIGHT_MOBILE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isTv) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                        true
                                    } else false
                                }
                                Key.DirectionRight -> {
                                    if (pagerState.currentPage < movies.size - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
                } else Modifier
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            userScrollEnabled = !isTv
        ) { page ->
            val movie = movies[page]
            HeroSlide(
                movie = movie,
                isTv = isTv,
                onMovieClick = { onMovieClick(movie.id) },
                onWatchClick = onWatchClick?.let { { it(movie.id) } },
                onFocusChanged = { isFocused = it }
            )
        }

        // Dot indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            movies.forEachIndexed { index, _ ->
                val isSelected = pagerState.currentPage == index
                val color by animateColorAsState(
                    targetValue = if (isSelected) OttConstants.BrandRed else Color.White.copy(alpha = 0.4f),
                    label = "dot"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroSlide(
    movie: Movie,
    isTv: Boolean,
    onMovieClick: () -> Unit,
    onWatchClick: (() -> Unit)?,
    onFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val posterHeight = if (isTv) POSTER_HEIGHT_TV else POSTER_HEIGHT_MOBILE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(if (isTv) 16.dp else 12.dp))
            .then(if (isTv) Modifier else Modifier.clickable { onMovieClick() })
    ) {
        // Blurred backdrop
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(movie.posterUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(30.dp),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.3f),
                        0.4f to Color.Black.copy(alpha = 0.5f),
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )

        // Content: poster + info
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTv) 48.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Sharp poster
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.movieName,
                modifier = Modifier
                    .height(posterHeight)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    MovieInitialsFallback(title = movie.movieName)
                },
                error = {
                    MovieInitialsFallback(title = movie.movieName)
                }
            )

            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                text = movie.movieName,
                color = Color.White,
                fontSize = if (isTv) 22.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            // Meta: rating · year · genre
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (movie.rating.isNotBlank()) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = movie.rating,
                        color = Color(0xFFFFD700),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "  ·  ",
                        color = OttConstants.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "${movie.year}",
                    color = OttConstants.TextSecondary,
                    fontSize = 13.sp
                )
                if (movie.genres.isNotEmpty()) {
                    Text(
                        text = "  ·  ${movie.genres.take(2).joinToString(", ")}",
                        color = OttConstants.TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Watch Now button
            if (isTv) {
                val focusRequester = remember { FocusRequester() }
                androidx.tv.material3.Button(
                    onClick = { (onWatchClick ?: onMovieClick).invoke() },
                    modifier = Modifier
                        .onFocusChanged { onFocusChanged(it.isFocused || it.hasFocus) }
                        .focusRequester(focusRequester)
                        .tvFocusScale(),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = OttConstants.BrandRed,
                        contentColor = Color.White,
                        focusedContainerColor = OttConstants.BrandRed,
                        focusedContentColor = Color.White
                    ),
                    border = androidx.tv.material3.ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                OttConstants.FocusBorderWidth, OttConstants.FocusBorderColor
                            )
                        )
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    androidx.tv.material3.Text("Watch Now")
                }
            } else {
                androidx.compose.material3.Button(
                    onClick = { (onWatchClick ?: onMovieClick).invoke() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = OttConstants.BrandRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
