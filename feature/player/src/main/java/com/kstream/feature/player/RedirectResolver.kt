package com.kstream.feature.player

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves single-use signed download links to their fresh underlying file
 * URL on EVERY open, so no individual byte-range request reuses (and dies on)
 * an already-consumed signed URL.
 *
 * Background: some CDNs (e.g. the `uptomkv` host serving Game Changer) hand out
 * a `download.php?dl=...` link that 302-redirects to a single-use signed
 * `mv1...mp4` URL. ExoPlayer's seek to read an MP4 `moov` atom at end-of-file
 * re-opens the source; with the original single-use link that second open
 * fails, so ExoPlayer falls back to reading the whole file from byte 0 (the
 * "downloads the entire movie before playing" symptom). VLC avoids this by
 * resolving once and seeking against the resolved URL.
 *
 * By re-resolving `download.php` for every open, each range request gets its
 * own fresh signed URL, restoring normal Range-based seeking on ExoPlayer and
 * removing the need for the heavyweight libVLC fallback engine.
 *
 * The requested byte range is preserved automatically: [ResolvingDataSource]
 * only rewrites the URI on the [DataSpec], leaving position/length intact, so
 * the underlying http data source issues the same Range against the resolved
 * file host.
 */
class RedirectResolver(
    private val log: (String) -> Unit = {}
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val original = dataSpec.uri.toString()
        if (!original.contains("download.php", ignoreCase = true)) {
            return dataSpec
        }
        return try {
            val resolved = resolveOnce(original)
            if (resolved != original) {
                log("resolve: pos=${dataSpec.position} len=${dataSpec.length} -> resolved")
                dataSpec.withUri(Uri.parse(resolved))
            } else {
                dataSpec
            }
        } catch (e: Exception) {
            log("resolve ERROR: ${e.message}")
            dataSpec
        }
    }

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

    companion object {
        const val UA = "KStream/1.0 (Android)"
    }
}
