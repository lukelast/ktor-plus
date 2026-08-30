package net.ghue.ktp.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Parses `7d`, `24h`, `60m`, or `30s` into a [Duration]; throws on malformed input. */
fun parseDuration(durationString: String): Duration {
    val trimmed = durationString.trim()
    if (trimmed.isEmpty()) {
        throw IllegalArgumentException("Duration string cannot be empty")
    }

    val unit = trimmed.last()
    val valueStr = trimmed.dropLast(1)
    val value =
        valueStr.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid duration format: $durationString")

    if (value < 0) {
        throw IllegalArgumentException("Duration value cannot be negative: $durationString")
    }

    return when (unit) {
        'd' -> value.days
        'h' -> value.hours
        'm' -> value.minutes
        's' -> value.seconds
        else ->
            throw IllegalArgumentException(
                "Invalid duration unit: $unit. Supported units: d (days), h (hours), m (minutes), s (seconds)"
            )
    }
}
