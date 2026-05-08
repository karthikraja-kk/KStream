package com.kstream.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kstream.core.model.Movie
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText

@Composable
fun MovieTileMobile(
    movie: Movie,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(movie.id) }
            .padding(4.dp)
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.movieName,
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = movie.movieName,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
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
    TvSurface(
        onClick = { onClick(movie.id) },
        modifier = modifier
            .width(160.dp)
            .padding(8.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Column {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.movieName,
                modifier = Modifier
                    .aspectRatio(2f / 3f)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
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
