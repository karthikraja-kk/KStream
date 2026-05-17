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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
            WelcomeRoute(onNavigateToHome = {
                navController.navigate("home") {
                    popUpTo("welcome") { inclusive = true }
                }
            })
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
            com.kstream.feature.settings.SettingsRoute(onBackClick = { navController.popBackStack() })
        }
    }
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