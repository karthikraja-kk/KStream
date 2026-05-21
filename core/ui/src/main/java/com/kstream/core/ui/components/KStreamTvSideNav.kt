package com.kstream.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text as TvText
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
                    .width(80.dp)
                    .background(TvMaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 16.dp, horizontal = 8.dp)
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
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.height(8.dp))

                TvNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = currentRoute == "home",
                    onClick = { onNavigate("home") }
                )
                TvNavItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    selected = currentRoute == "search",
                    onClick = { onNavigate("search") }
                )
                TvNavItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    selected = currentRoute == "downloads",
                    onClick = { onNavigate("downloads") }
                )
                TvNavItem(
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
private fun TvNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvMaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else TvMaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        modifier = Modifier.size(56.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (selected) TvMaterialTheme.colorScheme.primary
                else TvMaterialTheme.colorScheme.onSurfaceVariant
            )
            TvText(
                text = label,
                style = TvMaterialTheme.typography.labelSmall,
                color = if (selected) TvMaterialTheme.colorScheme.primary
                else TvMaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
