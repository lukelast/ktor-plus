package net.ghue.ktp.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parses a duration string into a Duration.
 *
 * Supported formats:
 * - "7d" - days
 * - "168h" - hours
 * - "10080m" - minutes
 * - "604800s" - seconds
 *
 * @param durationString The duration string to parse
 * @return The parsed Duration
 * @throws IllegalArgumentException if the format is invalid
 */
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
