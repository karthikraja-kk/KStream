package com.kstream.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
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
    NavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerContent = {
            val drawerHasFocus = hasFocus
            if (currentRoute in topLevelRoutes) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(TvMaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (drawerHasFocus) {
                            Image(
                                painter = painterResource(R.drawable.kstream_logo_horizontal),
                                contentDescription = "KStream",
                                modifier = Modifier
                                    .height(32.dp)
                                    .widthIn(max = 160.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.kstream_logo),
                                contentDescription = "KStream",
                                modifier = Modifier.size(32.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        selected = currentRoute == "home",
                        onClick = { onNavigate("home") },
                        leadingContent = {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                    ) { TvText("Home") }

                    NavigationDrawerItem(
                        selected = currentRoute == "search",
                        onClick = { onNavigate("search") },
                        leadingContent = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    ) { TvText("Search") }

                    NavigationDrawerItem(
                        selected = currentRoute == "downloads",
                        onClick = { onNavigate("downloads") },
                        leadingContent = {
                            Icon(Icons.Default.Download, contentDescription = "Downloads")
                        }
                    ) { TvText("Downloads") }

                    NavigationDrawerItem(
                        selected = currentRoute == "settings",
                        onClick = { onNavigate("settings") },
                        leadingContent = {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    ) { TvText("Settings") }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
