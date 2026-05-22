package com.kstream.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import com.kstream.core.ui.components.OttConstants

private val topLevelRoutes = setOf("home", "search", "downloads", "settings")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KStreamTvSideNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    var sidebarExpanded by remember { mutableStateOf(false) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarExpanded) 208.dp else 64.dp,
        animationSpec = tween(250),
        label = "sidebarWidth"
    )
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

    val navOrder = listOf("home", "search", "downloads", "settings")
    var currentFocusedNav by remember { mutableStateOf(currentRoute ?: "home") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar — always visible on top-level routes
        if (currentRoute in topLevelRoutes) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sidebarWidth)
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
                        } else if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown)
                        ) {
                            val currentIndex = navOrder.indexOf(currentFocusedNav).coerceAtLeast(0)
                            val nextIndex = if (keyEvent.key == Key.DirectionDown) {
                                (currentIndex + 1) % navOrder.size
                            } else {
                                (currentIndex - 1 + navOrder.size) % navOrder.size
                            }
                            try { navFocusRequesters[navOrder[nextIndex]]?.requestFocus() } catch (_: Exception) {}
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
                    focusRequester = navFocusRequesters["home"]!!,
                    onFocusedChange = { if (it) currentFocusedNav = "home" }
                )
                SideNavItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    selected = currentRoute == "search",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("search") },
                    focusRequester = navFocusRequesters["search"]!!,
                    onFocusedChange = { if (it) currentFocusedNav = "search" }
                )
                SideNavItem(
                    icon = Icons.Default.Download,
                    label = "Downloads",
                    selected = currentRoute == "downloads",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("downloads") },
                    focusRequester = navFocusRequesters["downloads"]!!,
                    onFocusedChange = { if (it) currentFocusedNav = "downloads" }
                )

                Spacer(Modifier.weight(1f))

                SideNavItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = currentRoute == "settings",
                    expanded = sidebarExpanded,
                    onClick = { onNavigate("settings") },
                    focusRequester = navFocusRequesters["settings"]!!,
                    onFocusedChange = { if (it) currentFocusedNav = "settings" }
                )
            }
        }

        // Content area
        // onKeyEvent fires AFTER children have had a chance to consume the event.
        // If no child consumed D-pad Left (we're at the leftmost focusable), we open the sidebar.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.DirectionLeft &&
                        !sidebarExpanded &&
                        currentRoute in topLevelRoutes
                    ) {
                        sidebarExpanded = true
                        true
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
    focusRequester: FocusRequester,
    onFocusedChange: (Boolean) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected || isFocused) 6.dp else 0.dp,
        animationSpec = tween(180),
        label = "navIndicatorWidth"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selected || isFocused) 1f else 0f,
        animationSpec = tween(180),
        label = "navIndicatorAlpha"
    )

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
        .then(if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
        .focusRequester(focusRequester)
        .focusable()
        .onFocusChanged {
            isFocused = it.isFocused
            onFocusedChange(it.isFocused)
        }
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
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .fillMaxHeight(0.6f)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914).copy(alpha = indicatorAlpha))
                )
            }
            Spacer(Modifier.width(8.dp))
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
            // Red dot indicator at bottom when selected in collapsed state
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE50914))
                )
            }
        }
    }
}
