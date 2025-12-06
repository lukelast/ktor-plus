package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.FirestoreOptions
import net.ghue.ktp.config.KtpConfig

val KtpConfig.firestore: Google
    get() = extractChild()

data class Google(val firestore: Firestore) {
    data class Firestore(val dbId: String) {
        fun firestore(): com.google.cloud.firestore.Firestore =
            FirestoreOptions.newBuilder().setDatabaseId(dbId).build().service
                ?: error("error creating firestore")
    }
}
