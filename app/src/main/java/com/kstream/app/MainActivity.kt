package com.kstream.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kstream.core.ui.LocalPlatform
import com.kstream.core.ui.Platform
import com.kstream.core.ui.PlatformProvider
import com.kstream.core.ui.theme.KStreamTheme
import com.kstream.core.ui.theme.KStreamTvTheme
import com.kstream.feature.details.DetailsRoute
import com.kstream.feature.home.HomeRoute
import com.kstream.feature.player.PlayerRoute
import com.kstream.feature.welcome.WelcomeRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlatformProvider {
                val platform = LocalPlatform.current
                if (platform == Platform.TV) {
                    KStreamTvTheme {
                        KStreamAppContent()
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
    val isFirstLaunchCompleted by viewModel.isFirstLaunchCompleted.collectAsState(initial = null)

    if (isFirstLaunchCompleted == null) return // Wait for DataStore

    NavHost(
        navController = navController,
        startDestination = if (isFirstLaunchCompleted == true) "home" else "welcome"
    ) {
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
                    navController.navigate("details/$movieId")
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
        composable("downloads") {
            com.kstream.feature.downloads.DownloadRoute(onBackClick = { navController.popBackStack() })
        }
        composable(
            route = "details/{movieId}",
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) {
            DetailsRoute(
                onBackClick = { navController.popBackStack() },
                onWatchClick = { movieId, quality ->
                    navController.navigate("player/$movieId/$quality")
                }
            )
        }
        composable(
            route = "player/{movieId}/{quality}",
            arguments = listOf(
                navArgument("movieId") { type = NavType.StringType },
                navArgument("quality") { type = NavType.StringType }
            )
        ) {
            PlayerRoute(onBackClick = { navController.popBackStack() })
        }
        composable("search") {
            com.kstream.feature.search.SearchRoute(onMovieClick = { movieId ->
                navController.navigate("details/$movieId")
            })
        }
        composable(
            route = "search/{seed}",
            arguments = listOf(navArgument("seed") { type = NavType.StringType })
        ) { backStackEntry ->
            com.kstream.feature.search.SearchRoute(
                onMovieClick = { movieId ->
                    navController.navigate("details/$movieId")
                },
                initialQuery = backStackEntry.arguments?.getString("seed")?.let(Uri::decode)
            )
        }
        composable("settings") {
            com.kstream.feature.settings.SettingsRoute(onBackClick = { navController.popBackStack() })
        }
    }
}
