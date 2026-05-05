package com.kstream.core.common

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagManager @Inject constructor() {
    private val flags = mutableMapOf<String, Boolean>(
        "downloads_enabled" to true,
        "smart_downloads_enabled" to false,
        "new_home_layout" to true
    )

    fun isEnabled(flag: String): Boolean {
        return flags[flag] ?: false
    }
}
