package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import kotlin.reflect.KProperty1
import net.ghue.ktp.gcp.join

const val FIRESTORE_BATCH_SIZE = 500

/**
 * Upserts [documents] with [upsert]'s merge semantics, in batches of Firestore's 500-write limit.
 */
fun Firestore.batchUpsert(collection: CollectionReference, documents: List<Any>) {
    documents.chunked(FIRESTORE_BATCH_SIZE).forEach { chunk ->
        val batch = batch()
        chunk.forEach { document ->
            batch.set(
                collection.document(idFieldValue(document)),
                document.serialize(),
                SetOptions.merge(),
            )
        }
        batch.commit().join()
    }
}

/** Deletes all documents in a collection using batched operations. */
fun Firestore.deleteCollection(collection: CollectionReference) {
    while (true) {
        val snapshot =
            collection.limit(FIRESTORE_BATCH_SIZE).select(FieldPath.documentId()).get().join()
        if (snapshot.documents.isEmpty()) break

        val batch = batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().join()
    }
}

/** Deletes all documents where [property] equals [value], serialized as in [whereEq]. */
fun <T, V> Firestore.deleteByField(
    collection: CollectionReference,
    property: KProperty1<T, V>,
    value: V,
) {
    while (true) {
        val snapshot =
            collection
                .whereEq(property, value)
                .limit(FIRESTORE_BATCH_SIZE)
                .select(FieldPath.documentId())
                .get()
                .join()

        if (snapshot.documents.isEmpty()) break

        val batch = batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().join()
    }
}
