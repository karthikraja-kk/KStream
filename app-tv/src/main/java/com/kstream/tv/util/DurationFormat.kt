package com.kstream.tv.util

/**
 * Formats a movie duration into a friendly display string:
 *   - "45 mins"
 *   - "2 hrs 28 mins"
 *   - "1 hr 5 mins"
 *   - "2 hrs"
 *   - "1 hr"
 *
 * Accepts every input shape we've seen in source data:
 *   "148"                → 148 → "2 hrs 28 mins"
 *   "148 min" / "148 mins" / "148 minutes"
 *   "2h" / "2hr" / "2 hr" / "2 hours"
 *   "2h 15m" / "2hr 15min" / "2 hours 15 minutes" / "2h15m"
 *   "2:30"               → 150 (H:MM)
 *   "02:13:12"           → 133 (HH:MM:SS, seconds dropped)
 *   "02:58:07 min"       → 178 (HH:MM:SS + redundant unit suffix tolerated)
 *   "1:30:00"            → 90  (H:MM:SS, seconds dropped)
 *   "PT2H15M" / "PT1H" / "PT45M"
 *   "2.5 hours"          → 150 (decimal hours)
 *
 * Whitespace around colons is tolerated ("02 : 13 : 12" works). NBSP and
 * narrow-NBSP get folded to regular space. All matching is case-insensitive.
 *
 * Returns the input verbatim if nothing recognizable is found.
 */
object DurationFormat {

    fun format(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val totalMinutes = parseMinutes(trimmed) ?: return trimmed
        if (totalMinutes <= 0) return trimmed
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val hrLabel = if (hours == 1) "hr" else "hrs"
        return when {
            hours == 0 -> "$minutes mins"
            minutes == 0 -> "$hours $hrLabel"
            else -> "$hours $hrLabel $minutes mins"
        }
    }

    /** Visible for unit tests. Returns total minutes, or null when unrecognized. */
    internal fun parseMinutes(input: String): Int? {
        // Normalise common non-ASCII spaces so the `\s` class catches them.
        val s = input
            .replace('\u00A0', ' ')
            .replace('\u2009', ' ')
            .replace('\u202F', ' ')
            .trim()
        if (s.isEmpty()) return null

        // 1. ISO-8601 duration: PT2H15M / PT1H / PT45M
        ISO.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val mn = m.groupValues[2].toIntOrNull() ?: 0
            val total = h * 60 + mn
            if (total > 0) return total
        }
        // 2. Colon-separated: H:MM or H:MM:SS (seconds discarded).
        //    Tolerates whitespace around colons.
        COLON.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val mn = m.groupValues[2].toIntOrNull() ?: 0
            val total = h * 60 + mn
            if (total > 0) return total
        }
        // 3. Plain integer minutes ("148").
        PURE_INT.matchEntire(s)?.let { return it.groupValues[1].toIntOrNull() }
        // 4. Integer + min suffix ("148 mins" / "148 minute(s)").
        INT_MIN.matchEntire(s)?.let { return it.groupValues[1].toIntOrNull() }
        // 5. Hours and/or minutes with text suffixes. Combined scan handles
        //    "2h", "2hr", "2h 15m", "2 hours 15 min", "2h15m",
        //    "2.5 hours" (decimal hours rounded to minutes), etc.
        var totalMinutesFromHm = 0
        var foundAny = false
        HOUR_TOKEN.findAll(s).forEach { match ->
            val v = match.groupValues[1].toDoubleOrNull() ?: 0.0
            totalMinutesFromHm += (v * 60).toInt()
            foundAny = true
        }
        MIN_TOKEN.findAll(s).forEach { match ->
            val v = match.groupValues[1].toDoubleOrNull() ?: 0.0
            totalMinutesFromHm += v.toInt()
            foundAny = true
        }
        return if (foundAny && totalMinutesFromHm > 0) totalMinutesFromHm else null
    }

    // Decimal-aware digit captures (\d+(?:\.\d+)?) let "2.5 hours" parse.
    // No trailing \b on HOUR_TOKEN/MIN_TOKEN so compact "2h45m" still matches.
    private val ISO = Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?$""", RegexOption.IGNORE_CASE)
    // Trailing unit suffix (e.g. "02:58:07 min", "1:30 hr") is tolerated and
    // ignored — H:MM(:SS) is unambiguous on its own and source data sometimes
    // appends a redundant label.
    private val COLON = Regex(
        """^(\d+)\s*:\s*(\d{1,2})(?:\s*:\s*\d{1,2})?(?:\s*(?:mins?|minutes?|hrs?|hours?|secs?|seconds?|h|m|s))?$""",
        RegexOption.IGNORE_CASE
    )
    private val PURE_INT = Regex("""^\s*(\d+)\s*$""")
    private val INT_MIN = Regex("""^\s*(\d+)\s*(?:mins?|minutes?)\s*$""", RegexOption.IGNORE_CASE)
    private val HOUR_TOKEN = Regex("""(\d+(?:\.\d+)?)\s*(?:hours?|hrs?|h)""", RegexOption.IGNORE_CASE)
    private val MIN_TOKEN = Regex("""(\d+(?:\.\d+)?)\s*(?:minutes?|mins?|m)""", RegexOption.IGNORE_CASE)
}
