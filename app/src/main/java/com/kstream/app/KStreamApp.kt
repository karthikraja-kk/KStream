package com.kstream.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.kstream.core.common.AppState
import com.kstream.core.common.MemoryGuardian
import dagger.hilt.android.HiltAndroidApp
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class KStreamApp : Application(), ImageLoaderFactory {

    lateinit var memoryGuardian: MemoryGuardian
        private set

    override fun onCreate() {
        super.onCreate()
        memoryGuardian = MemoryGuardian.getInstance(this)
        memoryGuardian.onClearCaches = {
            try {
                coil.Coil.imageLoader(this).memoryCache?.clear()
                android.util.Log.d("MemoryGuardian", "Cleared Coil memory cache")
            } catch (_: Exception) {}
        }
        memoryGuardian.start()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val stackTrace = sw.toString().take(8000)
                val crashInfo = "Thread: ${thread.name}\n${throwable::class.java.name}: ${throwable.message}\n\n$stackTrace"
                android.util.Log.e("KStreamCrash", crashInfo)

                val now = System.currentTimeMillis()
                val prefs = getSharedPreferences("crash_recovery", MODE_PRIVATE)
                val lastCrashTime = prefs.getLong("crash_time", 0L)
                val crashCount = if (now - lastCrashTime < 30_000) prefs.getInt("crash_count", 0) + 1 else 1

                prefs.edit()
                    .putString("last_crash", crashInfo)
                    .putLong("crash_time", now)
                    .putInt("crash_count", crashCount)
                    .putString("recovery_route", AppState.currentRoute)
                    .putLong("recovery_position", AppState.currentPlayerPositionMs)
                    .commit()

                // Don't restart for fatal/unrecoverable errors or crash loops
                val isFatal = throwable is OutOfMemoryError || throwable is StackOverflowError
                val isCrashLoop = crashCount > 3

                if (!isFatal && !isCrashLoop && thread.name == "main") {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                        startActivity(intent)
                        android.os.Process.killProcess(android.os.Process.myPid())
                        return@setDefaultUncaughtExceptionHandler
                    }
                }
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        }
    }

    override fun newImageLoader(): ImageLoader {
        val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isTv) 0.10 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isTv) 50L * 1024 * 1024 else 100L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(!isTv)
            .build()
    }
}