package com.kstream.tv.tier

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Three performance tiers for tier-gated rendering on Android TV.
 *
 * Tier is decided once at app start based on:
 *  1. Total heap class size (ActivityManager.memoryClass)
 *  2. Device model whitelist (Fire TV Lite, Gen1 → LOW)
 *  3. Total physical RAM (MemoryInfo.totalMem)
 *
 * Decision is logged once and cached for the process lifetime so every
 * presenter/screen sees a consistent value.
 *
 * Budgets:
 *  - LOW    (<= ~1.5 GB RAM, heap class <= 96 MB): flat dark, gold focus ring, NO animations, RGB_565
 *  - MID    (1.5 – 3 GB RAM): glow + scale, no blur/parallax, ARGB_8888
 *  - HIGH   (> 3 GB RAM, Shield etc.): Ken Burns, glassmorphism, ambient pan, parallax
 */
enum class DeviceTier {
    LOW, MID, HIGH;

    val isLow: Boolean get() = this == LOW
    val isAtLeastMid: Boolean get() = this != LOW
    val isHigh: Boolean get() = this == HIGH

    companion object {
        private const val TAG = "DeviceTier"

        @Volatile
        private var cached: DeviceTier? = null

        /** Returns the tier for this process, computing once. Safe to call from any thread. */
        fun get(context: Context): DeviceTier {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val tier = detect(context.applicationContext)
                cached = tier
                return tier
            }
        }

        private fun detect(context: Context): DeviceTier {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)

            val totalRamMb = memInfo.totalMem / (1024L * 1024L)
            val heapClassMb = am.memoryClass.toLong()
            val largeHeapMb = am.largeMemoryClass.toLong()
            val model = Build.MODEL ?: ""
            val device = Build.DEVICE ?: ""
            val product = Build.PRODUCT ?: ""

            val isFireTvLite = isFireTvLiteModel(model, device, product)

            val tier = when {
                isFireTvLite -> DeviceTier.LOW
                totalRamMb <= 1_536 || heapClassMb <= 96 -> DeviceTier.LOW
                totalRamMb <= 3_072 -> DeviceTier.MID
                else -> DeviceTier.HIGH
            }

            Log.i(
                TAG,
                "Tier=$tier (model=$model device=$device product=$product " +
                    "ram=${totalRamMb}MB heap=${heapClassMb}MB largeHeap=${largeHeapMb}MB " +
                    "isFireTvLite=$isFireTvLite)"
            )
            return tier
        }

        private fun isFireTvLiteModel(model: String, device: String, product: String): Boolean {
            val haystack = "$model $device $product".lowercase()
            // Fire TV Stick Lite (AFTSS, 2020) and earlier-gen sticks (AFTT, AFTN).
            // 4K Max (AFTKA) and Cube (AFTGAZL) explicitly excluded — they have enough RAM for MID.
            val isOldFireStick = haystack.contains("aftss") ||
                haystack.contains("aftt") ||
                haystack.contains("aftn")
            val excluded = haystack.contains("aftka") || haystack.contains("aftgazl")
            return isOldFireStick && !excluded
        }
    }
}
