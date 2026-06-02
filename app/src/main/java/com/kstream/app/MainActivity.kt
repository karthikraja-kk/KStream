package com.kstream.app

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.kstream.core.common.MemoryGuardian
import com.kstream.core.common.MemoryLevel
import com.kstream.core.ui.components.KStreamTvSideNav
import com.kstream.core.ui.CrashRecoveryLiteModePrompt
import com.kstream.core.ui.CrashRecoveryOptimizingLoader
import com.kstream.core.ui.LocalLiteMode
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
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var lastDpadTime = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Throttle D-pad navigation keys on TV to prevent ANR from focus search overload
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val now = System.currentTimeMillis()
                if (now - lastDpadTime < 200) return true // skip if <200ms since last
                lastDpadTime = now
            }
        }
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            val stack = e.stackTraceToString()
            if (stack.contains("androidx.compose") || stack.contains("androidx.tv") || stack.contains("Focus")) {
                android.util.Log.w("KStream", "Recoverable key dispatch error: ${e.message}")
                true
            } else {
                throw e
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("KStream", "Key dispatch argument error: ${e.message}")
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val recoveryState = remember { checkCrashRecovery() }
            PlatformProvider {
                val platform = LocalPlatform.current
                val viewModel: MainViewModel = hiltViewModel()
                val isTvDevice = platform == Platform.TV
                val isLiteModeUser by viewModel.isLiteMode.collectAsStateWithLifecycle(initialValue = isTvDevice)
                val memoryGuardian = remember { MemoryGuardian.getInstance(this@MainActivity) }
                val memoryLevel by memoryGuardian.memoryLevel.collectAsStateWithLifecycle()
                val effectiveLiteMode = isLiteModeUser || memoryLevel == MemoryLevel.EMERGENCY

                CompositionLocalProvider(LocalLiteMode provides effectiveLiteMode) {
                    if (platform == Platform.TV) {
                        KStreamTvTheme {
                            KStreamTheme {
                                CrashRecoveryWrapper(
                                    recoveryState = recoveryState,
                                    onEnableLiteMode = { viewModel.setLiteMode(true) },
                                    onCleanupComplete = { clearCrashRecovery() },
                                    isAlreadyLiteMode = isLiteModeUser
                                ) {
                                    KStreamAppContent()
                                }
                            }
                        }
                    } else {
                        KStreamTheme {
                            CrashRecoveryWrapper(
                                recoveryState = recoveryState,
                                onEnableLiteMode = { viewModel.setLiteMode(true) },
                                onCleanupComplete = { clearCrashRecovery() },
                                isAlreadyLiteMode = isLiteModeUser
                            ) {
                                KStreamAppContent()
                            }
                        }
                    }
                }
            }
        }
    }

    data class CrashRecoveryState(
        val hasCrash: Boolean = false,
        val isLiteMode: Boolean = false,
        val isCrashLoop: Boolean = false,
        val route: String? = null,
        val playerPosition: Long = 0L
    )

    private fun checkCrashRecovery(): CrashRecoveryState {
        val prefs = getSharedPreferences("crash_recovery", MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null) ?: return CrashRecoveryState()
        val crashCount = prefs.getInt("crash_count", 0)
        val route = prefs.getString("recovery_route", null)
        val position = prefs.getLong("recovery_position", 0L)

        return CrashRecoveryState(
            hasCrash = true,
            isCrashLoop = crashCount > 3,
            route = route,
            playerPosition = position
        )
    }

    private fun clearCrashRecovery() {
        getSharedPreferences("crash_recovery", MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
private fun CrashRecoveryWrapper(
    recoveryState: MainActivity.CrashRecoveryState,
    onEnableLiteMode: () -> Unit,
    onCleanupComplete: () -> Unit,
    isAlreadyLiteMode: Boolean,
    content: @Composable () -> Unit
) {
    var showRecovery by remember { mutableStateOf(recoveryState.hasCrash && !recoveryState.isCrashLoop) }
    var showOptimizing by remember { mutableStateOf(false) }

    if (showRecovery && isAlreadyLiteMode) {
        // Already in lite mode — show optimizing loader
        showRecovery = false
        showOptimizing = true
    }

    when {
        showOptimizing -> {
            CrashRecoveryOptimizingLoader()
            val appContext = LocalContext.current.applicationContext
            LaunchedEffect(Unit) {
                val guardian = MemoryGuardian.getInstance(appContext)
                guardian.forceCleanup()
                delay(2500)
                onCleanupComplete()
                showOptimizing = false
            }
        }
        showRecovery -> {
            CrashRecoveryLiteModePrompt(
                onEnableLiteMode = {
                    onEnableLiteMode()
                    onCleanupComplete()
                    showRecovery = false
                },
                onContinueAnyway = {
                    onCleanupComplete()
                    showRecovery = false
                }
            )
        }
        else -> {
            onCleanupComplete()
            content()
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

    // Track current route for crash recovery
    LaunchedEffect(navBackStackEntry) {
        val fullRoute = navBackStackEntry?.destination?.route
        if (fullRoute != null) {
            com.kstream.core.common.AppState.currentRoute = fullRoute
        }
    }

    val onTvNavigate: (String) -> Unit = { route ->
        if (route == "home") {
            navController.popBackStack("home", inclusive = false)
        } else {
            navController.navigate(route) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (platform == Platform.TV) {
        KStreamTvSideNav(currentRoute = currentRoute, onNavigate = onTvNavigate) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                // Disable nav animations on TV to prevent LookaheadScope "placed already" crash
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None }
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
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("kstream-lottie-animation.json")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        isPlaying = composition != null
    )

    // Navigate when animation finishes AND DataStore has loaded
    LaunchedEffect(progress, isFirstLaunchCompleted) {
        if (progress >= 1f && isFirstLaunchCompleted != null) {
            if (isFirstLaunchCompleted) onNavigateToHome() else onNavigateToWelcome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CrashInfoDialog(crashLog: String, onDismiss: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { dismissed = true; onDismiss() },
        title = { androidx.compose.material3.Text("Crash Report") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    text = crashLog.take(1500),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 30
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { dismissed = true; onDismiss() }) {
                androidx.compose.material3.Text("OK")
            }
        }
    )
}