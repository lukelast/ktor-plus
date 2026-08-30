package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.SetOptions
import kotlin.reflect.KClass
import net.ghue.ktp.gcp.join
import net.ghue.ktp.ktor.error.KtpRspExNotFound

/**
 * Collection handle bound to its document type so reads cannot use the wrong one; [ref] is the
 * escape hatch for transactions, batches, and raw queries. Create via [typedCollection].
 */
@Suppress("TooManyFunctions")
class KtpCollection<T : Any>(val ref: CollectionReference, private val kClass: KClass<T>) {

    /** Gets a document by ID, returning null if not found. */
    fun getOrNull(documentId: String): T? =
        ref.document(documentId).get().join().deserialize(kClass)

    /** Gets a document by ID, throwing [KtpRspExNotFound] if not found. */
    fun getOrThrow(documentId: String): T =
        getOrNull(documentId) ?: throw KtpRspExNotFound(kClass.simpleName ?: "Document", documentId)

    /** All documents in the collection. */
    fun getList(): List<T> = query { this }

    /** Runs the query built on this collection and returns all matching documents. */
    fun query(build: Query.() -> Query): List<T> =
        ref.build().get().join().documents.mapNotNull { it.deserialize(kClass) }

    /** See [CollectionReference.upsert]. */
    fun upsert(document: T, setOptions: SetOptions = SetOptions.merge()) =
        ref.upsert(document, setOptions)

    /** See [CollectionReference.replace]. */
    fun replace(document: T) = ref.replace(document)

    /** See [CollectionReference.createOrNull]. */
    fun createOrNull(document: T): T? = ref.createOrNull(document)

    /** See [CollectionReference.newDoc]. */
    fun newDoc(documentBuilder: (String) -> T): T = ref.newDoc(documentBuilder)

    /** See [CollectionReference.newId]. */
    fun newId(): String = ref.newId()

    /** See [CollectionReference.listIds]. */
    fun listIds(): List<String> = ref.listIds()

    /** See [CollectionReference.deleteById]. */
    fun deleteById(documentId: String) = ref.deleteById(documentId)

    /** See [CollectionReference.delete]. */
    fun delete(document: T) = ref.delete(document)

    /** A typed handle for a subcollection under one of this collection's documents. */
    inline fun <reified C : Any> sub(parentId: String, name: String): KtpCollection<C> =
        KtpCollection(ref.document(parentId).collection(name), C::class)
}

/** Creates a [KtpCollection] handle binding the collection [name] to document type [T]. */
inline fun <reified T : Any> Firestore.typedCollection(name: String): KtpCollection<T> =
    KtpCollection(collection(name), T::class)
