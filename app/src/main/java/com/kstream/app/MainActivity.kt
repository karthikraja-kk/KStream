package com.kstream.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kstream.core.domain.repository.UserDataRepository
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
    private var isLottieReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // On process death restoration, the splash composable is no longer in the
        // backstack (it was popped inclusively), so onReady() would never be called.
        // Skip the splash hold to let the restored navigation state render immediately.
        if (savedInstanceState != null) {
            isLottieReady = true
        }

        splashScreen.setKeepOnScreenCondition {
            !isLottieReady
        }
        
        setContent {
            PlatformProvider {
                val platform = LocalPlatform.current
                if (platform == Platform.TV) {
                    KStreamTvTheme {
                        KStreamTheme {
                            KStreamAppContent(onLottieReady = { isLottieReady = true })
                        }
                    }
                } else {
                    KStreamTheme {
                        KStreamAppContent(onLottieReady = { isLottieReady = true })
                    }
                }
            }
        }
    }
}

@Composable
fun KStreamAppContent(onLottieReady: () -> Unit) {
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
                        isFirstLaunch = isFirstLaunchCompleted != true,
                        onReady = onLottieReady,
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
                isFirstLaunch = isFirstLaunchCompleted != true,
                onReady = onLottieReady,
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
    isFirstLaunch: Boolean,
    onReady: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("kstream-lottie-animation.json")
    )
    
    LaunchedEffect(composition) {
        if (composition != null) {
            onReady()
        }
    }
    
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = true,
        restartOnPlay = false
    )
    
    LaunchedEffect(progress) {
        if (progress == 1f) {
            if (isFirstLaunch) {
                onNavigateToWelcome()
            } else {
                onNavigateToHome()
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}