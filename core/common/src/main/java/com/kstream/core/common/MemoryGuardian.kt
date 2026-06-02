package com.kstream.core.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MemoryLevel {
    NORMAL,   // < 70% heap used
    WARNING,  // 70-85% — clear image cache
    CRITICAL, // 85-90% — clear all caches, GC
    EMERGENCY // > 90% — force lite rendering
}

class MemoryGuardian private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MemoryGuardian"
        private const val CHECK_INTERVAL_MS = 3000L
        private const val WARNING_THRESHOLD = 0.70
        private const val CRITICAL_THRESHOLD = 0.85
        private const val EMERGENCY_THRESHOLD = 0.90

        @Volatile
        private var instance: MemoryGuardian? = null

        fun getInstance(context: Context): MemoryGuardian {
            return instance ?: synchronized(this) {
                instance ?: MemoryGuardian(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _memoryLevel = MutableStateFlow(MemoryLevel.NORMAL)
    val memoryLevel: StateFlow<MemoryLevel> = _memoryLevel.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Callback to clear app caches (set by Application class) */
    var onClearCaches: (() -> Unit)? = null

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                checkMemory()
                delay(CHECK_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Memory monitoring started")
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun checkMemory() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usageRatio = usedMemory.toDouble() / maxMemory.toDouble()

        val newLevel = when {
            usageRatio > EMERGENCY_THRESHOLD -> MemoryLevel.EMERGENCY
            usageRatio > CRITICAL_THRESHOLD -> MemoryLevel.CRITICAL
            usageRatio > WARNING_THRESHOLD -> MemoryLevel.WARNING
            else -> MemoryLevel.NORMAL
        }

        val previousLevel = _memoryLevel.value
        if (newLevel != previousLevel) {
            _memoryLevel.value = newLevel
            Log.w(TAG, "Memory level: $previousLevel → $newLevel (${(usageRatio * 100).toInt()}% used, ${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
            onLevelChanged(newLevel)
        }
    }

    private fun onLevelChanged(level: MemoryLevel) {
        when (level) {
            MemoryLevel.WARNING -> {
                onClearCaches?.invoke()
            }
            MemoryLevel.CRITICAL -> {
                onClearCaches?.invoke()
                System.gc()
                Log.w(TAG, "CRITICAL: Cleared caches and triggered GC")
            }
            MemoryLevel.EMERGENCY -> {
                onClearCaches?.invoke()
                System.gc()
                Log.e(TAG, "EMERGENCY: Forcing lite rendering mode")
            }
            MemoryLevel.NORMAL -> {
                Log.d(TAG, "Memory returned to normal")
            }
        }
    }

    fun forceCleanup() {
        onClearCaches?.invoke()
        System.gc()
        Log.d(TAG, "Forced cleanup completed")
    }
}
