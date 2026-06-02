package com.kstream.tv.watchdog

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log

class AnrWatchdog(
    private val intervalMs: Long = 4_000,
    private val thresholdMs: Long = 5_000
) {

    private var handlerThread: HandlerThread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastTick: Long = 0
    @Volatile private var running: Boolean = false

    fun start() {
        if (running) return
        running = true
        lastTick = System.currentTimeMillis()
        val ht = HandlerThread("KStreamAnrWatchdog").apply { start() }
        handlerThread = ht
        val bgHandler = Handler(ht.looper)
        bgHandler.post(object : Runnable {
            override fun run() {
                if (!running) return
                val now = System.currentTimeMillis()
                val sinceLastTick = now - lastTick
                if (sinceLastTick > thresholdMs && lastTick != 0L) {
                    val trace = Looper.getMainLooper().thread.stackTrace
                        .joinToString("\n") { "  at $it" }
                    Log.w(TAG, "ANR suspected (main idle for ${sinceLastTick}ms):\n$trace")
                }
                mainHandler.post { lastTick = System.currentTimeMillis() }
                bgHandler.postDelayed(this, intervalMs)
            }
        })
    }

    fun stop() {
        running = false
        handlerThread?.quitSafely()
        handlerThread = null
    }

    companion object {
        private const val TAG = "KStreamAnrWatchdog"
    }
}
