package com.kstream.tv.safemode

import android.content.Context

object SafeMode {

    @Volatile
    private var enabled: Boolean = false

    fun init(context: Context, crashCount: Int) {
        enabled = crashCount >= THRESHOLD
    }

    fun isEnabled(): Boolean = enabled

    fun forceEnabled() { enabled = true }

    private const val THRESHOLD = 2
}
