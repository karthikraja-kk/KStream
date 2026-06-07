package com.kstream.tv.ui.splash

import com.kstream.core.domain.GetMoviesUseCase
import com.kstream.core.enrichment.EnrichmentRepository
import com.kstream.core.model.Movie
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eagerly populates the TMDb enrichment cache during the splash window so
 * MainActivity arrives with TMDb posters/backdrops already in Room (and the
 * bitmaps already on Glide's disk cache from the prior session).
 *
 * Lifecycle:
 *  - Hilt creates this singleton when first injected (in SplashActivity).
 *  - [init] launches a fire-and-forget coroutine on an internal IO scope.
 *  - HomeRowsFragment may poll [isReady] or `await(timeout)` on first emit
 *    to decide whether to skip the shimmer placeholder.
 *
 * Idempotency: relies entirely on [EnrichmentRepository.ensureCached], which
 * is a no-op when a row already exists. Safe to run repeatedly.
 *
 * No back-pressure on cancellation: the work is bounded (max [PREWARM_CAP]
 * movies × ~2 HTTP calls each) and HomePrewarmTask survives for the process
 * lifetime, so we don't try to cancel it when SplashActivity finishes.
 */
@Singleton
class HomePrewarmTask @Inject constructor(
    private val getMoviesUseCase: GetMoviesUseCase,
    private val enrichmentRepository: EnrichmentRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch { run() }
    }

    /** True once the prewarm pass has finished (success OR no-op). */
    val isReady: Boolean get() = ready.isCompleted

    /**
     * Suspends until prewarm completes, or returns false if [timeoutMs]
     * elapses first. Use a short timeout (≤ 1.5s) from HomeRowsFragment so
     * the user is never stuck on an empty home screen.
     */
    suspend fun await(timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { ready.await(); true } ?: false

    private suspend fun run() {
        try {
            // Wait for the FIRST non-empty Movies emission. On reopen this
            // is instant (Room is already populated). On first install we
            // wait for sync to finish, capped by [MOVIES_WAIT_MS] so we
            // never block the user forever if the server is unreachable.
            val movies = withTimeoutOrNull(MOVIES_WAIT_MS) {
                getMoviesUseCase().first { it.isNotEmpty() }
            }.orEmpty().take(PREWARM_CAP)

            if (movies.isEmpty()) return

            coroutineScope {
                val sem = Semaphore(PARALLELISM)
                movies.forEach { movie: Movie ->
                    launch {
                        sem.withPermit {
                            runCatching { enrichmentRepository.ensureCached(movie) }
                        }
                    }
                }
            }
        } finally {
            // Even on failure: unblock HomeRowsFragment so the user is not
            // stuck on shimmer waiting for something that won't complete.
            ready.complete(Unit)
        }
    }

    companion object {
        // Only prewarm the above-the-fold tiles (≈ 2 rails × 6 visible).
        // The remaining cards stream in lazily via HomeRowsFragment's own
        // prefetch + observe pipeline and swap in place once ready.
        private const val PREWARM_CAP = 12
        private const val PARALLELISM = 4
        // First-install ceiling: wait up to 20s for the sync to populate
        // Room. On a slow network this is the cost of avoiding the
        // DB-poster → TMDb-poster flash.
        private const val MOVIES_WAIT_MS = 20_000L
    }
}
