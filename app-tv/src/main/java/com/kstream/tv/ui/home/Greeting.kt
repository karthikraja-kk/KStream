package com.kstream.tv.ui.home

import java.time.LocalTime
import kotlin.random.Random

/**
 * Time-based welcome greetings for the Home screen.
 *
 * Variants are picked at random from the slot pool on every Home open,
 * so the message changes between visits (and within a slot).
 *
 * Username substitution: `{name}` is replaced; falls back to "there" if blank.
 * Names longer than 18 chars are truncated with an ellipsis.
 */
object Greeting {

    private const val NAME_FALLBACK = "there"
    private const val NAME_MAX = 18

    private val MORNING = listOf(
        "Good morning, {name} ☀\uFE0F",
        "Morning, {name} — what's first?",
        "Rise and shine, {name}",
        "Hey {name}, fresh picks for your morning"
    )
    private val AFTERNOON = listOf(
        "Good afternoon, {name}",
        "Afternoon, {name} — take a break?",
        "Hey {name}, ready for a quick watch?",
        "{name}, here's something for the afternoon"
    )
    private val EVENING = listOf(
        "Good evening, {name} \uD83C\uDF06",
        "Evening, {name} — wind down with us",
        "Welcome back, {name}",
        "Hey {name}, evening watchlist's ready"
    )
    private val NIGHT = listOf(
        "Good night, {name} \uD83C\uDF19",
        "Late night, {name}? Let's go",
        "Cozy up, {name}",
        "Hey {name}, what's tonight's pick?"
    )
    private val LATE_NIGHT = listOf(
        "Burning the midnight oil, {name}?",
        "Still up, {name}? We've got you",
        "Late shift, {name} — pick something good",
        "Quiet hours, {name} \uD83C\uDF0C"
    )

    fun forNow(
        username: String?,
        now: LocalTime = LocalTime.now(),
        random: Random = Random.Default
    ): String {
        val name = (username?.trim()?.takeIf { it.isNotBlank() } ?: NAME_FALLBACK)
            .let { if (it.length > NAME_MAX) it.substring(0, NAME_MAX) + "…" else it }
        val pool = poolFor(now)
        val template = pool[random.nextInt(pool.size)]
        return template.replace("{name}", name)
    }

    private fun poolFor(now: LocalTime): List<String> {
        val h = now.hour
        return when {
            h in 5..11 -> MORNING
            h in 12..16 -> AFTERNOON
            h in 17..20 -> EVENING
            h in 21..23 -> NIGHT
            else -> LATE_NIGHT // 0..4
        }
    }
}
