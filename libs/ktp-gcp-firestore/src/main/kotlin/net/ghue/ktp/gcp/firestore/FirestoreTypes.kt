package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Firestore [Timestamp] truncated to microseconds (Firestore's precision) so in-memory round-trips
 * match real ones; also for raw writes like [setMerge] that bypass the serializer, e.g. TTL fields,
 * which Firestore only honors as native timestamps.
 */
fun Instant.toTimestamp(): Timestamp {
    val truncated = truncatedTo(ChronoUnit.MICROS)
    return Timestamp.ofTimeSecondsAndNanos(truncated.epochSecond, truncated.nano)
}

object FirestoreTypes {
    fun registerDefaults() {
        registerInstant()
        registerDate()
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
