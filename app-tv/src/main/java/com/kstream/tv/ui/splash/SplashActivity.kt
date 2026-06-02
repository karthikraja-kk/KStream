package com.kstream.tv.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
 * Responsibilities:
 *  1. Show the cinematic splash artwork for at least [MIN_SPLASH_MS]
 *     (long enough to feel premium, short enough not to annoy returning users).
 *  2. While the splash is on screen, read `isFirstLaunchCompleted` ONCE.
 *  3. Route to [WelcomeActivity] (first launch) or [MainActivity] (returning user)
 *     and finish self so the splash can never be navigated back to.
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var userDataRepository: UserDataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        AppState.currentRoute = ROUTE

        val started = System.currentTimeMillis()
        lifecycleScope.launch {
            val firstLaunchDone = runCatching {
                userDataRepository.isFirstLaunchCompleted.first()
            }.getOrDefault(false)

            val elapsed = System.currentTimeMillis() - started
            val remaining = (MIN_SPLASH_MS - elapsed).coerceAtLeast(0L)

            Handler(Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                val next = if (firstLaunchDone) {
                    Intent(this@SplashActivity, MainActivity::class.java)
                } else {
                    Intent(this@SplashActivity, WelcomeActivity::class.java)
                }
                next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(next)
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }, remaining)
        }
    }

    companion object {
        const val ROUTE = "tv/splash"
        private const val MIN_SPLASH_MS = 1_400L
    }
}
