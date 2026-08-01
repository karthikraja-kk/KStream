package com.kstream.core.domain

import com.kstream.core.domain.repository.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Single source of truth for the "single refresh = ~48h alive" behaviour.
 *
 * The CDN's `download.php` gateway tokens die in ~3 minutes, but the Cloudflare
 * R2 presigned URL they 302 to is valid for ~48h. We resolve once and cache that
 * long-lived URL (per movie+quality) so seeks/pause/resume and the details-page
 * refresh button reuse it instead of re-hitting the short-lived gateway.
 */
class ResolveAndCachePlayableUrlUseCase @Inject constructor(
    private val userDataRepository: UserDataRepository
) {

    /** The cached direct R2 URL for [movieId]+[quality], or null if missing/expired. */
    suspend fun cachedPlayableUrl(movieId: String, quality: String): String? {
        return try {
            val cached = userDataRepository.getResolvedMediaUrl(movieId, quality) ?: return null
            if (MediaLinkUtils.isR2UrlValid(cached.url, cached.expiresAt)) cached.url else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolve a short-lived `download.php` URL to its direct ~48h R2 URL once and
     * persist it. Falls back to the original candidates if resolution fails so the
     * existing error/refresh path still applies.
     */
    suspend fun resolveAndCacheUrl(
        movieId: String,
        quality: String,
        candidates: List<String>
    ): List<String> {
        val first = candidates.firstOrNull { it.isNotBlank() } ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val resolved = RedirectUrlResolver.resolveOnce(first)
                if (resolved != first && resolved.isNotBlank()) {
                    val expiresAt = MediaLinkUtils.r2ExpiryMs(resolved)
                        ?: (System.currentTimeMillis() + R2_FALLBACK_TTL_MS)
                    userDataRepository.setResolvedMediaUrl(movieId, quality, resolved, expiresAt)
                    listOf(resolved)
                } else {
                    candidates
                }
            } catch (_: Exception) {
                candidates
            }
        }
    }

    private companion object {
        /** Fallback lifetime for a resolved URL when the CDN omits X-Amz-Expires. */
        const val R2_FALLBACK_TTL_MS = 48 * 60 * 60 * 1000L
    }
}
