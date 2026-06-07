package com.kstream.tv.ui.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.kstream.core.common.AppState
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.tv.R
import com.kstream.tv.ui.main.MainActivity
import com.kstream.tv.ui.welcome.WelcomeActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Cold-start entry point (LEANBACK_LAUNCHER).
 *
 *  1. Plays the brand Lottie animation (assets/kstream_splash.json) ONCE.
 *  2. In parallel, reads [UserDataRepository.isFirstLaunchCompleted].
 *  3. When BOTH the animation has ended AND the prefs read is done, routes
 *     to [WelcomeActivity] (first launch) or [MainActivity] (returning user)
 *     and finishes itself.
 *
 *  If the animation cannot load for any reason, [MAX_SPLASH_MS] acts as a
 *  safety timeout so we never block the user on the splash.
 */
@AndroidEntryPoint
class SplashActivity : FragmentActivity() {

    @Inject
    lateinit var userDataRepository: UserDataRepository

    // Injected purely to trigger the Hilt singleton's eager init() — that
    // kicks off the TMDb prewarm so MainActivity arrives with the cache hot.
    @Suppress("unused")
    @Inject
    lateinit var homePrewarmTask: HomePrewarmTask

    private var firstLaunchDone: Boolean? = null
    private var animationEnded: Boolean = false
    private var navigated: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        AppState.currentRoute = ROUTE

        val lottie = findViewById<LottieAnimationView>(R.id.splash_lottie)
        lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animationEnded = true
                maybeNavigate()
            }

            override fun onAnimationCancel(animation: Animator) {
                animationEnded = true
                maybeNavigate()
            }
        })

        // Read the user-data prefs in parallel with the animation.
        lifecycleScope.launch {
            firstLaunchDone = runCatching {
                userDataRepository.isFirstLaunchCompleted.first()
            }.getOrDefault(false)
            maybeNavigate()
        }

        // Safety timeout in case the animation fails to load (e.g. asset
        // missing, OOM on LOW tier). Without this the splash would hang.
        lottie.postDelayed({
            if (!animationEnded) {
                animationEnded = true
                maybeNavigate()
            }
        }, MAX_SPLASH_MS)
    }

    private fun maybeNavigate() {
        if (navigated) return
        val firstLaunch = firstLaunchDone ?: return
        if (!animationEnded) return
        if (isFinishing || isDestroyed) return
        navigated = true

        val next = if (firstLaunch) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, WelcomeActivity::class.java)
        }
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(next)
        finish()
        // Suppress the default activity-switch animation. The previous
        // overridePendingTransition(fade_in, fade_out) cross-faded the
        // splash window OUT while the home window faded IN, leaving a
        // visible black gap between the two while both were
        // semi-transparent. An instant cut is jarring-free here because
        // the user just watched the splash hold for ~5s — no extra polish
        // animation needed.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ROUTE = "tv/splash"
        // Animation is ~5.5s (132 frames @ 24fps). Give it a generous ceiling
        // so slow devices still get to play it fully if possible.
        private const val MAX_SPLASH_MS = 8_000L
    }
}
