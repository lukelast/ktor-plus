package net.ghue.ktp.gcp.firestore

import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FirestoreException
import com.google.cloud.firestore.SetOptions
import io.grpc.Status
import net.ghue.ktp.gcp.join

/** Inserts or updates a document using its 'id' property as the document ID. */
fun <T : Any> CollectionReference.upsert(document: T, setOptions: SetOptions = SetOptions.merge()) {
    document(idFieldValue(document)).set(document.serialize(), setOptions).join()
}

/** Full overwrite: fields absent from [document] are deleted, unlike [upsert]'s merge. */
fun <T : Any> CollectionReference.replace(document: T) {
    document(idFieldValue(document)).set(document.serialize()).join()
}

/** Creates the document keyed by its 'id'; returns it, or null if that ID already exists. */
fun <T : Any> CollectionReference.createOrNull(document: T): T? =
    try {
        document(idFieldValue(document)).create(document.serialize()).join()
        document
    } catch (e: FirestoreException) {
        if (e.status?.code == Status.Code.ALREADY_EXISTS) null else throw e
    }

/** Merges unserialized fields so `FieldValue` sentinels like increment work; creates if missing. */
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

/** Mints a new random document ID without writing anything. */
fun CollectionReference.newId(): String = document().id

/** Deletes the document with the given ID. Deleting a missing document is a no-op. */
fun CollectionReference.deleteById(documentId: String) {
    document(documentId).delete().join()
}

/** Deletes the document whose ID comes from [document]'s 'id' property. */
fun CollectionReference.delete(document: Any) {
    deleteById(idFieldValue(document))
}
