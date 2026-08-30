@file:Suppress("TooManyFunctions")

package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.SetOptions
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import net.ghue.ktp.gcp.join
import net.ghue.ktp.ktor.error.KtpRspExNotFound
import net.ghue.ktp.ktor.error.ktpRspError

const val FIRESTORE_BATCH_SIZE = 500

/** Inserts or updates a document using its 'id' property as the document ID. */
inline fun <reified T : Any> CollectionReference.upsert(
    document: T,
    setOptions: SetOptions = SetOptions.merge(),
) {
    document(idFieldValue(document)).set(document.serialize(), setOptions).join()
}

/**
 * Sets the document to exactly [document], deleting fields not present in it. Unlike [upsert],
 * whose merge write never removes existing fields or map keys.
 */
inline fun <reified T : Any> CollectionReference.replace(document: T) {
    document(idFieldValue(document)).set(document.serialize()).join()
}

/**
 * Partial merge write of raw field values, bypassing the reflection serializer. Use for Firestore
 * server-side sentinels like [com.google.cloud.firestore.FieldValue.increment], which [upsert]
 * would mangle. Creates the document when missing.
 */
fun DocumentReference.setMerge(fields: Map<String, Any>) {
    set(fields, SetOptions.merge()).join()
}

/** Creates a new document with an auto-generated ID, passing the ID to the builder function. */
fun <T : Any> CollectionReference.newDoc(documentBuilder: (String) -> T): T {
    val newDocRef = this.document()
    val doc = documentBuilder(newDocRef.id)
    newDocRef.set(doc.serialize()).join()
    return doc
}

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

/** Extracts the value of the 'id' property from the given data object. */
fun idFieldValue(item: Any): String {
    val idProperty =
        item::class.memberProperties.find { it.name == "id" }
            ?: ktpRspError {
                title = "Missing ID Property"
                detail = "Property 'id' not found on ${item::class.simpleName}"
            }

    val idValue =
        idProperty.getter.call(item)
            ?: ktpRspError {
                title = "Null ID"
                detail = "Property 'id' is null on ${item::class.simpleName}"
            }

    val stringValue =
        if (idValue::class.isValue) {
            // Handle value class by getting the single property from the primary constructor
            val property =
                idValue::class.memberProperties.firstOrNull()
                    ?: ktpRspError {
                        title = "Invalid Value Class"
                        detail = "Value class ${idValue::class.simpleName} has no properties"
                    }
            val innerValue =
                property.getter.call(idValue)
                    ?: ktpRspError {
                        title = "Null ID"
                        detail = "Property 'id' is null on ${item::class.simpleName}"
                    }
            innerValue.toString()
        } else {
            idValue.toString()
        }

    if (stringValue.isEmpty()) {
        ktpRspError {
            title = "Empty ID"
            detail = "Property 'id' is empty on ${item::class.simpleName}"
        }
    }
    return stringValue
}

/** Writes multiple documents in batches, respecting Firestore's 500 document limit per batch. */
fun Firestore.batchWrite(collection: CollectionReference, items: List<Any>) {
    items.chunked(FIRESTORE_BATCH_SIZE).forEach { chunk ->
        val batch = batch()
        chunk.forEach { item ->
            batch.set(collection.document(idFieldValue(item)), item.serialize(), SetOptions.merge())
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

/**
 * Deletes documents from the specified collection where the given property matches the provided
 * value.
 */
fun <T> Firestore.deleteByField(
    collection: CollectionReference,
    property: KProperty1<*, T>,
    value: T,
) {
    while (true) {
        val snapshot =
            collection
                .whereEqualTo(property.name, value)
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
