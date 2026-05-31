package com.kstream.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts movie sync immediately on creation (during splash screen).
 * HomeViewModel calls [awaitOrSkip] to reuse the splash sync result
 * instead of starting a duplicate network call.
 */
@Singleton
class StartupSyncManager @Inject constructor(
    private val syncMoviesUseCase: SyncMoviesUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialSync: Deferred<Boolean> = scope.async {
        try {
            syncMoviesUseCase()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val consumed = AtomicBoolean(false)

    /**
     * First call: awaits the splash sync and returns true if it succeeded.
     * Subsequent calls: returns false so the caller does a fresh sync.
     */
    suspend fun awaitOrSkip(): Boolean {
        if (!consumed.compareAndSet(false, true)) return false
        return try {
            initialSync.await()
        } catch (_: Exception) {
            false
        }
    }
}
