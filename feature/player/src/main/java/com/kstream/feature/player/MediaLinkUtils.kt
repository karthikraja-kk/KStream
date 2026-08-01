package com.kstream.feature.player

import android.util.Base64

/**
 * Helpers for inspecting the streaming URLs the backend hands out. The
 * watch URLs from uptomkv (and similar CDNs) embed a base64 `dl` parameter
 * whose decoded payload contains an `exp=<unix_seconds>` field — once that
 * timestamp passes, the CDN responds with HTML/garbage instead of MP4.
 *
 * We use this to decide whether to refresh links BEFORE attempting playback,
 * avoiding pointless engine swaps when the real problem is a stale URL.
 */
object MediaLinkUtils {

    /** Default safety margin: treat a link as expired if it has less than this many seconds left. */
    private const val DEFAULT_MARGIN_SEC = 30L

    fun isAnyExpired(urls: List<String>, marginSec: Long = DEFAULT_MARGIN_SEC): Boolean =
        urls.any { isExpired(it, marginSec) }

    fun isExpired(url: String, marginSec: Long = DEFAULT_MARGIN_SEC): Boolean {
        return try {
            val dlEncoded = Regex("[?&]dl=([^&]+)").find(url)?.groupValues?.get(1) ?: return false
            // The `dl` value is plain base64 (sometimes URL-safe). Try both.
            val decoded = try {
                String(Base64.decode(dlEncoded, Base64.DEFAULT or Base64.NO_WRAP))
            } catch (_: Exception) {
                String(Base64.decode(dlEncoded, Base64.URL_SAFE or Base64.NO_WRAP))
            }
            val expSec = Regex("(?:^|&)exp=(\\d+)").find(decoded)?.groupValues?.get(1)?.toLongOrNull()
                ?: return false
            val nowSec = System.currentTimeMillis() / 1000L
            (expSec - nowSec) < marginSec
        } catch (_: Exception) {
            // If we can't parse it, don't claim it's expired — let the player try.
            false
        }
    }
}
