package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Query
import net.ghue.ktp.gcp.join
import net.ghue.ktp.ktor.error.KtpRspExNotFound

/** Executes the query and returns all matching documents as a list. */
inline fun <reified T : Any> Query.getList(): List<T> =
    get().join().documents.mapNotNull { it.deserialize<T>() }

/** Executes the query and returns the first matching document, or null if none found. */
inline fun <reified T : Any> Query.firstOrNull(): T? =
    get().join().documents.firstOrNull()?.deserialize()

/** Gets a document by ID, throwing [KtpRspExNotFound] if not found. */
inline fun <reified T : Any> CollectionReference.getOrThrow(documentId: String): T {
    return getOrNull(documentId)
        ?: throw KtpRspExNotFound(T::class.simpleName ?: "Document", documentId)
}

/** Gets a document by ID, returning null if not found. */
inline fun <reified T : Any> CollectionReference.getOrNull(documentId: String): T? {
    val doc = this.document(documentId).get().join()
    return doc.deserialize<T>()
}

/** Gets a document from this reference, throwing [KtpRspExNotFound] if not found. */
inline fun <reified T : Any> DocumentReference.getOrThrow(): T {
    return getOrNull<T>() ?: throw KtpRspExNotFound(T::class.simpleName ?: "Document", id)
}

/** Gets a document from this reference, returning null if not found. */
inline fun <reified T : Any> DocumentReference.getOrNull(): T? {
    val doc = get().join()
    return doc.deserialize<T>()
}

/** All document IDs, including "virtual" parents existing only as subcollection path segments. */
fun CollectionReference.listIds(): List<String> = listDocuments().map { it.id }
