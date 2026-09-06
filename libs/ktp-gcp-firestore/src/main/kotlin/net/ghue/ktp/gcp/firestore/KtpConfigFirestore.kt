package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.FirestoreOptions
import net.ghue.ktp.config.KtpConfig
import org.koin.dsl.module
import org.koin.dsl.onClose

val KtpConfig.google: Google
    get() = extractChild()

// `Google` must match the `google` config block; [KtpConfig.extractChild] keys on the type name.
data class Google(val firestore: Firestore) {
    data class Firestore(val dbId: String) {
        /** Builds a new [com.google.cloud.firestore.Firestore] client for this database. */
        fun createClient(): com.google.cloud.firestore.Firestore =
            FirestoreOptions.newBuilder().setDatabaseId(dbId).build().service
                ?: error("error creating firestore")
    }
}

fun firestoreModule() = module {
    single { get<KtpConfig>().google.firestore.createClient() } onClose { it?.close() }
}
