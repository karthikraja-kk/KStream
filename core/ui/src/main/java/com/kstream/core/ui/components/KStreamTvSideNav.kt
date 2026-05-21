package com.kstream.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import com.kstream.core.ui.R

private val topLevelRoutes = setOf("home", "search", "downloads", "settings")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KStreamTvSideNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (currentRoute in topLevelRoutes) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(72.dp)
                    .background(TvMaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 16.dp, horizontal = 4.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.kstream_logo),
                        contentDescription = "KStream",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.height(8.dp))

                SideNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = currentRoute == "home",
                    onClick = { onNavigate("home") }
                )
                SideNavItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    selected = currentRoute == "search",
                    onClick = { onNavigate("search") }
                )
                SideNavItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    selected = currentRoute == "downloads",
                    onClick = { onNavigate("downloads") }
                )
                SideNavItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = currentRoute == "settings",
                    onClick = { onNavigate("settings") }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SideNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) TvMaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else Color.Transparent
    val contentColor = if (selected) TvMaterialTheme.colorScheme.primary
    else TvMaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
