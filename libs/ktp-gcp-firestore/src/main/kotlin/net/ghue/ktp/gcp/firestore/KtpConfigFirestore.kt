package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.FirestoreOptions
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module

val KtpConfig.firestore: Google
    get() = extractChild()

// `Google` must match the `google` config block; [KtpConfig.extractChild] keys on the type name.
data class Google(val firestore: Firestore) {
    data class Firestore(val dbId: String) {
        fun firestore(): com.google.cloud.firestore.Firestore =
            FirestoreOptions.newBuilder().setDatabaseId(dbId).build().service
                ?: error("error creating firestore")
    }
}

fun firestoreModule() = module { single { get<KtpConfig>().firestore.firestore.firestore() } }
