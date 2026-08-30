package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.Transaction
import net.ghue.ktp.gcp.join
import net.ghue.ktp.ktor.error.KtpRspExNotFound

/**
 * Runs [block] in a Firestore transaction and returns its result. Reads and writes must go through
 * the [Transaction] receiver ([getOrNull], [getList], [upsert], ...); the plain collection
 * extensions would execute outside the transaction. Firestore requires all reads before the first
 * write, and may run [block] more than once on contention, so keep side effects out of it. The
 * block runs on an SDK executor thread, not the calling thread. An exception thrown by the block
 * aborts the transaction and is rethrown unwrapped.
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

/** Executes the query within the transaction and returns all matching documents as a list. */
inline fun <reified T : Any> Transaction.getList(query: Query): List<T> =
    get(query).join().documents.mapNotNull { it.deserialize<T>() }

/**
 * Transactional [CollectionReference.upsert]: merge-writes [document] using its 'id' property as
 * the document ID.
 */
fun Transaction.upsert(collection: CollectionReference, document: Any) {
    set(collection.document(idFieldValue(document)), document.serialize(), SetOptions.merge())
}

/**
 * Transactional [CollectionReference.replace]: sets the document to exactly [document], deleting
 * fields not present in it.
 */
fun Transaction.replace(collection: CollectionReference, document: Any) {
    set(collection.document(idFieldValue(document)), document.serialize())
}
