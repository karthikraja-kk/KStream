package com.kstream.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    var sidebarExpanded by remember { mutableStateOf(false) }
    val navFocusRequesters = remember {
        mapOf(
            "home" to FocusRequester(),
            "search" to FocusRequester(),
            "downloads" to FocusRequester(),
            "settings" to FocusRequester()
        )
    }

    LaunchedEffect(sidebarExpanded) {
        if (sidebarExpanded) {
            val target = navFocusRequesters[currentRoute] ?: navFocusRequesters["home"]
            try { target?.requestFocus() } catch (_: Exception) {}
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar — always visible on top-level routes
        if (currentRoute in topLevelRoutes) {
            val sidebarWidth = if (sidebarExpanded) 200.dp else 56.dp

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sidebarWidth)
                    .animateContentSize(animationSpec = tween(250))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A1A),
                                Color(0xFF111111)
                            )
                        )
                    )
                    .padding(vertical = 20.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                            sidebarExpanded = false
                            true
                        } else false
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .then(if (sidebarExpanded) Modifier.fillMaxWidth().padding(horizontal = 24.dp) else Modifier),
                    contentAlignment = if (sidebarExpanded) Alignment.CenterStart else Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.kstream_logo),
                        contentDescription = "KStream",
                        modifier = Modifier.size(if (sidebarExpanded) 36.dp else 28.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.height(24.dp))

                SideNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = currentRoute == "home",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("home") },
                    focusRequester = navFocusRequesters["home"]!!
                )
                SideNavItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    selected = currentRoute == "search",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("search") },
                    focusRequester = navFocusRequesters["search"]!!
                )
                SideNavItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    selected = currentRoute == "downloads",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("downloads") },
                    focusRequester = navFocusRequesters["downloads"]!!
                )

                Spacer(Modifier.weight(1f))

                SideNavItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = currentRoute == "settings",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("settings") },
                    focusRequester = navFocusRequesters["settings"]!!
                )
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onPreviewKeyEvent { keyEvent ->
                    // Only open sidebar when D-pad Left is pressed and sidebar is collapsed
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                        if (!sidebarExpanded && currentRoute in topLevelRoutes) {
                            // Let the event propagate first — if nothing consumes it,
                            // the focus system will fail and we open sidebar
                            false
                        } else false
                    } else false
                }
                .onFocusChanged { focusState ->
                    // When content gains focus, collapse sidebar
                    if (focusState.hasFocus && sidebarExpanded) {
                        sidebarExpanded = false
                    }
                }
        ) {
            content()

            // Invisible left-edge focus catcher — opens sidebar when focus reaches left edge
            if (!sidebarExpanded && currentRoute in topLevelRoutes) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .fillMaxHeight()
                        .focusable()
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                sidebarExpanded = true
                            }
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SideNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isFocused -> Color(0xFFE50914)
        selected -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val iconColor = when {
        isFocused -> Color.White
        selected -> Color(0xFFE50914)
        else -> Color(0xFF808080)
    }
    val textColor = when {
        isFocused -> Color.White
        selected -> Color.White
        else -> Color(0xFF808080)
    }

    val itemModifier = Modifier
        .then(if (expanded) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier.width(56.dp))
        .height(48.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(bgColor)
        .focusRequester(focusRequester)
        .focusable()
        .onFocusChanged { isFocused = it.isFocused }
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown &&
                (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
            ) {
                onClick()
                true
            } else false
        }

    if (expanded) {
        Row(
            modifier = itemModifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = iconColor
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
        }
    } else {
        Box(
            modifier = itemModifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = iconColor
            )
        }
    }
}
