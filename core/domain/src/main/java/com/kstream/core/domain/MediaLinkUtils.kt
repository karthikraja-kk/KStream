package com.kstream.core.domain

import android.util.Base64

/**
 * Helpers for inspecting the streaming URLs the backend hands out. The
 * watch URLs from fastbytes/uptomkv (and similar CDNs) embed a base64 `dl`
 * parameter whose decoded payload contains an `exp=<unix_seconds>` field —
 * once that timestamp passes, the CDN responds with HTML/garbage instead of
 * MP4. Direct Cloudflare R2 presigned URLs instead carry `X-Amz-Date` +
 * `X-Amz-Expires` (~48h lifetime).
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
            // Direct Cloudflare R2 presigned URL: expiry is X-Amz-Date + X-Amz-Expires.
            if (url.contains("X-Amz-Date=")) {
                val exp = r2ExpiryMs(url) ?: return false
                return (exp - System.currentTimeMillis()) < marginSec * 1000L
            }
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

    /** True when the cached R2 URL is still inside its signed window (with [marginSec] slack). */
    fun isR2UrlValid(url: String, expiresAt: Long, marginSec: Long = DEFAULT_MARGIN_SEC): Boolean {
        if (expiresAt <= 0L) return false
        if (url.isBlank()) return false
        return (expiresAt - System.currentTimeMillis()) > marginSec * 1000L
    }

    /** Epoch-ms at which an R2 presigned URL stops working, parsed from its own query params. */
    fun r2ExpiryMs(url: String): Long? {
        val amzDate = Regex("[?&]X-Amz-Date=([^&]+)").find(url)?.groupValues?.get(1) ?: return null
        val amzExpires = Regex("[?&]X-Amz-Expires=([^&]+)")
            .find(url)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val issuedMs = parseAmzDate(amzDate) ?: return null
        return issuedMs + amzExpires * 1000L
    }

    /** Parse `20260801T032420Z` (AWS date format, always UTC) to epoch-ms. */
    private fun parseAmzDate(value: String): Long? {
        val m = Regex("(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})(\\d{2})Z").find(value) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), s.toInt())
        }
        return cal.timeInMillis
    }
}
