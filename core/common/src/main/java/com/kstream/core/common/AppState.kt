package com.kstream.core.common

/**
 * Shared app state accessible from any module.
 * Used for crash recovery (saving player position) and route tracking.
 */
object AppState {
    @Volatile
    var currentRoute: String = ""

    @Volatile
    var currentPlayerPositionMs: Long = 0L
}
