package com.kstream.core.domain.repository

/**
 * Per-movie engine memory used by `PlayerEngineCoordinator` in Auto mode.
 *
 * "Engine" values are the string keys produced by `VideoEngine.key` ("EXO" or "VLC").
 * `null` means no preference is recorded.
 */
data class MovieEnginePref(
    val engine: String?,
    val lastFailMs: Long?
)

interface MovieEnginePrefRepository {
    suspend fun get(movieId: String): MovieEnginePref?
    suspend fun setEngine(movieId: String, engine: String?)
    suspend fun recordFailure(movieId: String, ts: Long)
    suspend fun clearFailure(movieId: String)
    suspend fun clearAll()
}
