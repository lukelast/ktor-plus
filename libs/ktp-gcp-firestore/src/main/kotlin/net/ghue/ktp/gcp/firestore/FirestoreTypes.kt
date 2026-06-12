package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import java.time.Instant
import java.util.*

/**
 * Converts to the native Firestore [Timestamp] representation. Needed for raw field writes like
 * [setMerge] that bypass the serializer, e.g. TTL fields which Firestore only honors as native
 * Timestamp values.
 */
fun Instant.toTimestamp(): Timestamp = Timestamp.ofTimeSecondsAndNanos(epochSecond, nano)

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
