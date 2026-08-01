package com.kstream.core.domain

import java.net.HttpURLConnection
import java.net.URL

/**
 * Follows HTTP redirects manually to the final URL. Used to turn a short-lived
 * `download.php?dl=...` gateway link into the direct Cloudflare R2 presigned
 * URL it 302s to (which is valid for ~48h).
 */
object RedirectUrlResolver {

    private const val UA = "KStream/1.0 (Android)"

    /** Follow up to [maxHops] redirects manually, returning the final URL. */
    fun resolveOnce(start: String, maxHops: Int = 5): String {
        var current = start
        var hops = 0
        while (hops < maxHops) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", UA)
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return current
                    current = absolutize(current, loc)
                    hops++
                    continue
                }
                return current
            } finally {
                conn.disconnect()
            }
        }
        return current
    }

    private fun absolutize(base: String, loc: String): String =
        try { URL(URL(base), loc).toString() } catch (_: Exception) { loc }
}
