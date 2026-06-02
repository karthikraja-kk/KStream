package com.kstream.tv

import android.app.Application
import android.util.Log
import com.bumptech.glide.Glide
import com.kstream.core.common.MemoryGuardian
import com.kstream.tv.crash.TvCrashHandler
import com.kstream.tv.tier.DeviceTier
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

    override fun onCreate() {
        super.onCreate()

        // 1. Crash handler first — every line below this must be observable on next launch.
        TvCrashHandler.install(this)

        // 2. Resolve and log device tier exactly once (read by Glide's AppGlideModule too).
        val tier = DeviceTier.get(this)
        Log.i(TAG, "KStreamTvApp.onCreate tier=$tier")

        // 3. Memory guardian — clears Glide caches under pressure.
        memoryGuardian = MemoryGuardian.getInstance(this)
        memoryGuardian.onClearCaches = {
            try {
                val glide = Glide.get(this)
                glide.clearMemory()
                Thread {
                    try {
                        glide.clearDiskCache()
                    } catch (t: Throwable) {
                        Log.w(TAG, "Glide disk-cache clear failed: ${t.message}")
                    }
                }.start()
                Log.d(TAG, "Cleared Glide caches under memory pressure")
            } catch (t: Throwable) {
                Log.w(TAG, "Glide cache clear failed: ${t.message}")
            }
        }
        memoryGuardian.start()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            Glide.get(this).clearMemory()
        } catch (_: Throwable) {
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
