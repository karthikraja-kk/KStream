package com.kstream.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kstream.core.ui.components.KStreamTvSideNav
import com.kstream.core.domain.repository.UserDataRepository
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.PlatformProvider
import com.kstream.core.ui.theme.KStreamTheme
import com.kstream.core.ui.theme.KStreamTvTheme
import com.kstream.feature.details.DetailsRoute
import com.kstream.feature.home.HomeRoute
import com.kstream.feature.player.PlayerRoute
import com.kstream.feature.welcome.PermissionRoute
import com.kstream.feature.welcome.WelcomeRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            PlatformProvider {
                val platform = LocalPlatform.current
                if (platform == Platform.TV) {
                    KStreamTvTheme {
                        KStreamTheme {
                            KStreamAppContent()
                        }
                    }
                } else {
                    KStreamTheme {
                        KStreamAppContent()
                    }
                }
            }
        }
    }
}

@Composable
fun KStreamAppContent() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val isFirstLaunchCompleted by viewModel.isFirstLaunchCompleted.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    val startDestination = remember { "splash" }

    // Extract deep link movie ID if present (kstream://movie/{movieId})
    val deepLinkMovieId = remember {
        val activity = context as? ComponentActivity
        val uri = activity?.intent?.data
        if (uri?.scheme == "kstream" && uri.host == "movie") {
            uri.pathSegments.firstOrNull()
        } else null
    }

    val platform = LocalPlatform.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
        ?.substringBefore("/")
        ?.substringBefore("?")

    val onTvNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (platform == Platform.TV) {
        KStreamTvSideNav(currentRoute = currentRoute, onNavigate = onTvNavigate) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable("splash") {
                    SplashScreenWithNav(
                        isFirstLaunchCompleted = isFirstLaunchCompleted,
                        onNavigateToWelcome = {
                            navController.navigate("welcome") {
                                popUpTo("splash") { inclusive = true }
                            }
                        },
                        onNavigateToHome = {
                            navController.navigate("home") {
                                popUpTo("splash") { inclusive = true }
                            }
                            if (!deepLinkMovieId.isNullOrBlank()) {
                                navController.navigate("details/${Uri.encode(deepLinkMovieId)}")
                            }
                        }
                    )
                }
                composable("welcome") {
                    WelcomeRoute(
                        onNavigateToHome = {
                            navController.navigate("home") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        },
                        onTermsClick = { navController.navigate("terms") }
                    )
                }
                composable("home") {
                    HomeRoute(
                        onMovieClick = { movieId ->
                            navController.navigate("details/${Uri.encode(movieId)}")
                        },
                        onWatchClick = { movieId, quality ->
                            navController.navigate("player/${Uri.encode(movieId)}/${Uri.encode(quality)}/stream?startOver=false")
                        },
                        onSeeMoreClick = { railTitle ->
                            navController.navigate("search/${Uri.encode(railTitle)}")
                        },
                        onSearchClick = { onTvNavigate("search") },
                        onDownloadsClick = { onTvNavigate("downloads") },
                        onSettingsClick = { onTvNavigate("settings") }
                    )
                }
                composable(route = "downloads") {
                    com.kstream.feature.downloads.DownloadRoute(
                        onBackClick = { navController.popBackStack() },
                        onMovieClick = { movieId ->
                            navController.navigate("details/${Uri.encode(movieId)}")
                        },
                        onWatchClick = { movieId, quality ->
                            navController.navigate("player/${Uri.encode(movieId)}/${Uri.encode(quality)}/download?startOver=false")
                        }
                    )
                }
                composable(
                    route = "details/{movieId}",
                    arguments = listOf(navArgument("movieId") { type = NavType.StringType })
                ) {
                    DetailsRoute(
                        onBackClick = { navController.popBackStack() },
                        onWatchClick = { movieId, quality, startOver ->
                            navController.navigate("player/${Uri.encode(movieId)}/${Uri.encode(quality)}/stream?startOver=$startOver")
                        },
                        onGoToDownloads = { _, _ -> navController.navigate("downloads") }
                    )
                }
                composable(
                    route = "player/{movieId}/{quality}/{source}?startOver={startOver}",
                    arguments = listOf(
                        navArgument("movieId") { type = NavType.StringType },
                        navArgument("quality") { type = NavType.StringType },
                        navArgument("source") {
                            type = NavType.StringType
                            defaultValue = "stream"
                        },
                        navArgument("startOver") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val startOver = backStackEntry.arguments?.getString("startOver")?.toBoolean() ?: false
                    PlayerRoute(
                        onBackClick = { navController.popBackStack() },
                        onGoToDownloads = { navController.navigate("downloads") },
                        startOver = startOver
                    )
                }
                composable("search") {
                    com.kstream.feature.search.SearchRoute(
                        onMovieClick = { movieId ->
                            navController.navigate("details/${Uri.encode(movieId)}")
                        },
                        onDownloadsClick = { onTvNavigate("downloads") }
                    )
                }
                composable(
                    route = "search/{seed}",
                    arguments = listOf(navArgument("seed") { type = NavType.StringType })
                ) { backStackEntry ->
                    com.kstream.feature.search.SearchRoute(
                        onMovieClick = { movieId ->
                            navController.navigate("details/${Uri.encode(movieId)}")
                        },
                        onDownloadsClick = { onTvNavigate("downloads") },
                        initialQuery = backStackEntry.arguments?.getString("seed")?.let(Uri::decode)
                    )
                }
                composable("settings") {
                    com.kstream.feature.settings.SettingsRoute(
                        onBackClick = { navController.popBackStack() },
                        onMovieClick = { movieId ->
                            navController.navigate("details/${android.net.Uri.encode(movieId)}")
                        },
                        onTermsClick = { navController.navigate("terms") }
                    )
                }
                composable("terms") {
                    com.kstream.feature.settings.TermsAndConditionsScreen(
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    } else {
        NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("splash") {
            SplashScreenWithNav(
                isFirstLaunchCompleted = isFirstLaunchCompleted,
                onNavigateToWelcome = {
                    navController.navigate("welcome") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                    if (!deepLinkMovieId.isNullOrBlank()) {
                        navController.navigate("details/${Uri.encode(deepLinkMovieId)}")
                    }
                }
            )
        }
        composable("welcome") {
            WelcomeRoute(
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onTermsClick = { navController.navigate("terms") }
            )
        }
        composable("home") {
            HomeRoute(
                onMovieClick = { movieId ->
                    navController.navigate("details/${Uri.encode(movieId)}")
                },
                onWatchClick = { movieId, quality ->
                    navController.navigate("player/${Uri.encode(movieId)}/${Uri.encode(quality)}/stream?startOver=false")
                },
                onSeeMoreClick = { railTitle ->
                    navController.navigate("search/${Uri.encode(railTitle)}")
                },
                onSearchClick = {
                    navController.navigate("search")
                },
                onDownloadsClick = {
                    navController.navigate("downloads")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable(
            route = "downloads",
        ) {
            com.kstream.feature.downloads.DownloadRoute(
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId ->
                    navController.navigate("details/${Uri.encode(movieId)}")
                },
                onWatchClick = { movieId, quality ->
                    navController.navigate("player/${Uri.encode(movieId)}/${Uri.encode(quality)}/download?startOver=false")
                }
            )
        }
        composable(
            route = "details/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) {
            DetailsRoute(
                onBackClick = { navController.popBackStack() },
                onWatchClick = { movieId, quality, startOver ->
                    navController.navigate("player/${Uri.encode(movieId)}/${Uri.encode(quality)}/stream?startOver=$startOver")
                },
                onGoToDownloads = { movieId, quality ->
                    navController.navigate("downloads")
                }
            )
        }
        composable(
            route = "player/{movieId}/{quality}/{source}?startOver={startOver}",
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                navArgument("quality") { type = NavType.StringType },
                navArgument("source") { 
                    type = NavType.StringType 
                    defaultValue = "stream"
                },
                navArgument("startOver") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val startOver = backStackEntry.arguments?.getString("startOver")?.toBoolean() ?: false
            PlayerRoute(
                onBackClick = { navController.popBackStack() },
                onGoToDownloads = { 
                    navController.navigate("downloads")
                },
                startOver = startOver
            )
        }
        composable("search") {
            com.kstream.feature.search.SearchRoute(
                onMovieClick = { movieId ->
                    navController.navigate("details/${Uri.encode(movieId)}")
                },
                onDownloadsClick = {
                    navController.navigate("downloads")
                }
            )
        }
        composable(
            route = "search/{seed}",
            arguments = listOf(navArgument("seed") { type = NavType.StringType })
        ) { backStackEntry ->
            com.kstream.feature.search.SearchRoute(
                onMovieClick = { movieId ->
                    navController.navigate("details/${Uri.encode(movieId)}")
                },
                onDownloadsClick = {
                    navController.navigate("downloads")
                },
                initialQuery = backStackEntry.arguments?.getString("seed")?.let(Uri::decode)
            )
        }
        composable("settings") {
            com.kstream.feature.settings.SettingsRoute(
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId ->
                    navController.navigate("details/${android.net.Uri.encode(movieId)}")
                },
                onTermsClick = { navController.navigate("terms") }
            )
        }
        composable("terms") {
            com.kstream.feature.settings.TermsAndConditionsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
    } // end else (mobile)
}

@Composable
fun SplashScreenWithNav(
    isFirstLaunchCompleted: Boolean?,
    onNavigateToWelcome: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    var animationStarted by remember { mutableStateOf(false) }
    var animationDone by remember { mutableStateOf(false) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.9f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    LaunchedEffect(Unit) {
        animationStarted = true
        delay(1500)
        animationDone = true
    }

    // Navigate only after animation finishes AND DataStore has loaded
    LaunchedEffect(animationDone, isFirstLaunchCompleted) {
        if (animationDone && isFirstLaunchCompleted != null) {
            if (isFirstLaunchCompleted) onNavigateToHome() else onNavigateToWelcome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.kstream_logo_with_name),
            contentDescription = "KStream",
            modifier = Modifier
                .size(200.dp)
                .alpha(animatedAlpha)
                .scale(animatedScale)
        )
    }
}