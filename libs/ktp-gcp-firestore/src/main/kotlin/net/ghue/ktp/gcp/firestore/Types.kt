package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Truncates to Firestore's microsecond precision so in-memory round-trips match real ones; use for
 * raw [setMerge] writes such as TTL fields, which Firestore only honors as native timestamps.
 */
fun Instant.toTimestamp(): Timestamp {
    val truncated = truncatedTo(ChronoUnit.MICROS)
    return Timestamp.ofTimeSecondsAndNanos(truncated.epochSecond, truncated.nano)
}

/**
 * Types every document may use without registering anything: [Instant] and [Date] as native
 * timestamps, [LocalDate] as its ISO-8601 text (`2024-01-31`), which sorts and range-queries as a
 * string and has no time zone to get wrong. An app registration for the same type replaces the
 * default.
 */
object FirestoreTypes {
    fun registerDefaults() {
        registerInstant()
        registerDate()
        registerLocalDate()
    }

    private fun registerLocalDate() {
        FirestoreSerializer.registerSerializer<LocalDate> { it.toString() }
        FirestoreDeserializer.registerDeserializer<LocalDate> { value ->
            require(value is String) {
                "LocalDate fields hold ISO-8601 text, but the stored value is a " +
                    "${value::class.simpleName}: '$value'"
            }
            LocalDate.parse(value)
        }
    }

    private fun registerInstant() {
        FirestoreSerializer.registerSerializer<Instant> { it.toTimestamp() }
        FirestoreDeserializer.registerDeserializer<Instant> {
            (it as Timestamp).let { ts -> Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()) }
        }
    }

    private fun registerDate() {
        FirestoreSerializer.registerSerializer<Date> { Timestamp.of(it) }
        FirestoreDeserializer.registerDeserializer<Date> { (it as Timestamp).toDate() }
    }
}
