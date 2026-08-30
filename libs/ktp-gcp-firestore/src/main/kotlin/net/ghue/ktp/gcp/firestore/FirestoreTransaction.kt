package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.Transaction
import net.ghue.ktp.gcp.join
import net.ghue.ktp.ktor.error.KtpRspExNotFound

/**
 * Runs [block] in a Firestore transaction on an SDK executor thread. Read and write only via the
 * [Transaction] receiver (plain collection extensions run outside it), read before any write, and
 * keep [block] side-effect free: it may rerun on contention. Its exception is rethrown unwrapped.
 */
fun <T> Firestore.transaction(block: Transaction.() -> T): T = runTransaction { it.block() }.join()

/** Reads a document by ID within the transaction, returning null if not found. */
inline fun <reified T : Any> Transaction.getOrNull(
    collection: CollectionReference,
    documentId: String,
): T? = get(collection.document(documentId)).join().deserialize<T>()

/** Reads a document by ID within the transaction, throwing [KtpRspExNotFound] if not found. */
inline fun <reified T : Any> Transaction.getOrThrow(
    collection: CollectionReference,
    documentId: String,
): T =
    getOrNull<T>(collection, documentId)
        ?: throw KtpRspExNotFound(T::class.simpleName ?: "Document", documentId)

/** Runs [query] within the transaction and returns all matching documents. */
inline fun <reified T : Any> Transaction.getList(query: Query): List<T> =
    get(query).join().documents.mapNotNull { it.deserialize<T>() }

/** Transactional [CollectionReference.upsert]: merge-writes [document] keyed by its `id`. */
fun Transaction.upsert(collection: CollectionReference, document: Any) {
    set(collection.document(idFieldValue(document)), document.serialize(), SetOptions.merge())
}

/** Transactional [CollectionReference.replace]: overwrites the document, dropping absent fields. */
fun Transaction.replace(collection: CollectionReference, document: Any) {
    set(collection.document(idFieldValue(document)), document.serialize())
}
