package com.kstream.tv.crash

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import com.kstream.core.common.AppState
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Installs a global UncaughtExceptionHandler for the TV process.
 *
 * Crash policy:
 *  - Save full stack trace + thread + last route + last player position into SharedPreferences
 *  - Track crash count within a 30s window for loop detection
 *  - Auto-restart the launch intent ONLY when:
 *      * crash is on the main thread (user-visible)
 *      * not OOM / StackOverflow (won't recover with a restart)
 *      * not in a crash loop (>3 crashes in 30s → fall through to default handler so the OS shows the system dialog)
 *  - Otherwise: delegate to the platform default handler
 *
 * SafeMode (P13) reads `crash_count` on next launch and disables Ken Burns,
 * blur, ambient pan, parallax, and Glide heavy decodes when count >= 2.
 */
object TvCrashHandler {

    private const val TAG = "KStreamTvCrash"
    private const val PREFS = "crash_recovery"
    private const val LOOP_WINDOW_MS = 30_000L
    private const val LOOP_THRESHOLD = 3
    private const val STACK_TRACE_LIMIT = 8_000

    fun install(app: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashInfo = formatCrash(thread, throwable)
                Log.e(TAG, crashInfo)

                val now = System.currentTimeMillis()
                val prefs = app.getSharedPreferences(PREFS, Application.MODE_PRIVATE)
                val lastCrashTime = prefs.getLong("crash_time", 0L)
                val crashCount = if (now - lastCrashTime < LOOP_WINDOW_MS) {
                    prefs.getInt("crash_count", 0) + 1
                } else {
                    1
                }

                prefs.edit()
                    .putString("last_crash", crashInfo)
                    .putLong("crash_time", now)
                    .putInt("crash_count", crashCount)
                    .putString("recovery_route", AppState.currentRoute)
                    .putLong("recovery_position", AppState.currentPlayerPositionMs)
                    .commit()

                val isFatal = throwable is OutOfMemoryError || throwable is StackOverflowError
                val isCrashLoop = crashCount > LOOP_THRESHOLD

                if (!isFatal && !isCrashLoop && thread.name == "main") {
                    val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                        app.startActivity(intent)
                        Process.killProcess(Process.myPid())
                        return@setDefaultUncaughtExceptionHandler
                    }
                }
            } catch (_: Throwable) {
                // Crash handler must never throw — fall through to platform handler.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "TvCrashHandler installed")
    }

    private fun formatCrash(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString().take(STACK_TRACE_LIMIT)
        return "Thread: ${thread.name}\n${throwable::class.java.name}: ${throwable.message}\n\n$stack"
    }
}
