package com.kstream.tv

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bumptech.glide.Glide
import com.kstream.core.common.MemoryGuardian
import com.kstream.tv.crash.TvCrashHandler
import com.kstream.tv.safemode.SafeMode
import com.kstream.tv.tier.DeviceTier
import com.kstream.tv.watchdog.AnrWatchdog
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for the Leanback TV build.
 *
 * P0: bare-bones Hilt-enabled Application.
 * P2 (this commit):
 *  - Install global crash handler (auto-restart on recoverable main-thread crashes)
 *  - Resolve DeviceTier once and log it (Glide & presenters read the cached value)
 *  - Start MemoryGuardian and wire its cache-clear callback to Glide
 * P13 will layer on the ANR Watchdog and SafeMode tracking on top of this base.
 */
@HiltAndroidApp
class KStreamTvApp : Application() {

    private lateinit var memoryGuardian: MemoryGuardian
    private val anrWatchdog = AnrWatchdog()

    override fun onCreate() {
        super.onCreate()

        // 1. Crash handler first — every line below this must be observable on next launch.
        TvCrashHandler.install(this)

        // 1b. SafeMode based on crash count, then start ANR watchdog.
        SafeMode.init(this, TvCrashHandler.getCrashCount(this))
        if (SafeMode.isEnabled()) {
            Log.w(TAG, "SafeMode ENABLED (crashCount=${TvCrashHandler.getCrashCount(this)})")
        }
        anrWatchdog.start()

        // 2. Resolve and log device tier exactly once (read by Glide's AppGlideModule too).
        val tier = DeviceTier.get(this)
        Log.i(TAG, "KStreamTvApp.onCreate tier=$tier")

        // 3. Memory guardian — clears Glide caches under pressure.
        memoryGuardian = MemoryGuardian.getInstance(this)
        val mainHandler = Handler(Looper.getMainLooper())
        memoryGuardian.onClearCaches = {
            mainHandler.post {
                try {
                    val glide = Glide.get(this)
                    glide.clearMemory() // must run on main thread
                    Thread {
                        try {
                            glide.clearDiskCache() // must NOT run on main thread
                        } catch (t: Throwable) {
                            Log.w(TAG, "Glide disk-cache clear failed: ${t.message}")
                        }
                    }.start()
                    Log.d(TAG, "Cleared Glide caches under memory pressure")
                } catch (t: Throwable) {
                    Log.w(TAG, "Glide cache clear failed: ${t.message}")
                }
            }
        }
        memoryGuardian.start()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Handler(Looper.getMainLooper()).post {
            try {
                Glide.get(this).clearMemory()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            Glide.get(this).trimMemory(level)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "KStreamTvApp"
    }
}
