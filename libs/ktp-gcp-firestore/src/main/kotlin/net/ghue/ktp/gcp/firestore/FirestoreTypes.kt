package net.ghue.ktp.gcp.firestore

import com.google.cloud.Timestamp
import java.time.Instant
import java.util.*

object FirestoreTypes {
    fun registerDefaults() {
        registerInstant()
        registerDate()
    }

    private fun registerInstant() {
        FirestoreSerializer.registerSerializer<Instant> {
            Timestamp.ofTimeSecondsAndNanos(it.epochSecond, it.nano)
        }
        FirestoreDeserializer.registerDeserializer<Instant> {
            (it as Timestamp).let { ts -> Instant.ofEpochSecond(ts.seconds, ts.nanos.toLong()) }
        }
    }

    private fun registerDate() {
        FirestoreSerializer.registerSerializer<Date> { Timestamp.of(it) }
        FirestoreDeserializer.registerDeserializer<Date> { (it as Timestamp).toDate() }
    }
}
