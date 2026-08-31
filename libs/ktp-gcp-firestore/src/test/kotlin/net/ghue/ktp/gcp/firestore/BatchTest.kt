package net.ghue.ktp.gcp.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.SettableApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.QuerySnapshot
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.WriteBatch
import com.google.cloud.firestore.WriteResult
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class BatchTest :
    StringSpec({
        "batchUpsert chunks items and commits each batch" {
            data class Item(val id: String, val name: String)

            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val batch = mockk<WriteBatch>()

            val items = (1..(FIRESTORE_BATCH_SIZE + 1)).map { Item(id = "id-$it", name = "n-$it") }

            every { firestore.batch() } returns batch
            every { batch.set(any(), any<Map<String, Any>>(), SetOptions.merge()) } returns batch
            every { batch.commit() } returns completedFuture(listOf(mockk<WriteResult>()))
            every { collection.document(any<String>()) } returns mockk<DocumentReference>()

            firestore.batchUpsert(collection, items)

            verify(exactly = 2) { firestore.batch() }
            verify(exactly = items.size) {
                batch.set(any(), match { !it.containsKey("id") }, SetOptions.merge())
            }
            verify(exactly = 2) { batch.commit() }
        }

        "batchUpsert does nothing for an empty list" {
            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()

            firestore.batchUpsert(collection, emptyList())

            verify(exactly = 0) { firestore.batch() }
        }

        "deleteCollection deletes in batches until empty" {
            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val query = mockk<Query>()
            val snapshot1 = mockk<QuerySnapshot>()
            val snapshot2 = mockk<QuerySnapshot>()
            val doc1 = mockk<QueryDocumentSnapshot>()
            val doc2 = mockk<QueryDocumentSnapshot>()
            val ref1 = mockk<DocumentReference>()
            val ref2 = mockk<DocumentReference>()
            val batch = mockk<WriteBatch>()

            every { collection.limit(FIRESTORE_BATCH_SIZE) } returns query
            every { query.select(any<FieldPath>()) } returns query
            every { query.get() } returnsMany
                listOf(completedFuture(snapshot1), completedFuture(snapshot2))

            every { snapshot1.documents } returns mutableListOf(doc1, doc2)
            every { snapshot2.documents } returns mutableListOf()
            every { doc1.reference } returns ref1
            every { doc2.reference } returns ref2

            every { firestore.batch() } returns batch
            every { batch.delete(any()) } returns batch
            every { batch.commit() } returns completedFuture(listOf(mockk<WriteResult>()))

            firestore.deleteCollection(collection)

            verify(exactly = 1) { firestore.batch() }
            verify(exactly = 2) { batch.delete(any()) }
            verify(exactly = 1) { batch.commit() }
        }

        "deleteCollection does not create a batch when the collection is empty" {
            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val query = mockk<Query>()
            val snapshot = mockk<QuerySnapshot>()

            every { collection.limit(FIRESTORE_BATCH_SIZE) } returns query
            every { query.select(any<FieldPath>()) } returns query
            every { query.get() } returns completedFuture(snapshot)
            every { snapshot.documents } returns mutableListOf()

            firestore.deleteCollection(collection)

            verify(exactly = 0) { firestore.batch() }
        }

        "deleteByField deletes in batches using property name" {
            data class User(val id: String, val status: String)

            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val query = mockk<Query>()
            val snapshot1 = mockk<QuerySnapshot>()
            val snapshot2 = mockk<QuerySnapshot>()
            val doc = mockk<QueryDocumentSnapshot>()
            val ref = mockk<DocumentReference>()
            val batch = mockk<WriteBatch>()

            every { collection.whereEqualTo("status", "active") } returns query
            every { query.limit(FIRESTORE_BATCH_SIZE) } returns query
            every { query.select(any<FieldPath>()) } returns query
            every { query.get() } returnsMany
                listOf(completedFuture(snapshot1), completedFuture(snapshot2))

            every { snapshot1.documents } returns mutableListOf(doc)
            every { snapshot2.documents } returns mutableListOf()
            every { doc.reference } returns ref

            every { firestore.batch() } returns batch
            every { batch.delete(any()) } returns batch
            every { batch.commit() } returns completedFuture(listOf(mockk<WriteResult>()))

            firestore.deleteByField(collection, User::status, "active")

            verify(exactly = 1) { firestore.batch() }
            verify(exactly = 1) { batch.delete(ref) }
            verify(exactly = 1) { batch.commit() }
            verify(exactly = 2) { collection.whereEqualTo("status", "active") }
        }

        "deleteByField supports non-string values" {
            data class User(val id: String, val age: Int)

            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val query = mockk<Query>()
            val snapshot1 = mockk<QuerySnapshot>()
            val snapshot2 = mockk<QuerySnapshot>()
            val doc = mockk<QueryDocumentSnapshot>()
            val ref = mockk<DocumentReference>()
            val batch = mockk<WriteBatch>()

            every { collection.whereEqualTo("age", 42) } returns query
            every { query.limit(FIRESTORE_BATCH_SIZE) } returns query
            every { query.select(any<FieldPath>()) } returns query
            every { query.get() } returnsMany
                listOf(completedFuture(snapshot1), completedFuture(snapshot2))

            every { snapshot1.documents } returns mutableListOf(doc)
            every { snapshot2.documents } returns mutableListOf()
            every { doc.reference } returns ref

            every { firestore.batch() } returns batch
            every { batch.delete(any()) } returns batch
            every { batch.commit() } returns completedFuture(listOf(mockk<WriteResult>()))

            firestore.deleteByField(collection, User::age, 42)

            verify(exactly = 1) { firestore.batch() }
            verify(exactly = 1) { batch.delete(ref) }
            verify(exactly = 1) { batch.commit() }
            verify(exactly = 2) { collection.whereEqualTo("age", 42) }
        }

        "deleteByField serializes value class operands" {
            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val query = mockk<Query>()
            val emptySnapshot = mockk<QuerySnapshot>()

            every { collection.whereEqualTo("id", "value-1") } returns query
            every { query.limit(FIRESTORE_BATCH_SIZE) } returns query
            every { query.select(any<FieldPath>()) } returns query
            every { query.get() } returns completedFuture(emptySnapshot)
            every { emptySnapshot.documents } returns mutableListOf()

            firestore.deleteByField(collection, BatchValueUser::id, BatchUserId("value-1"))

            verify(exactly = 1) { collection.whereEqualTo("id", "value-1") }
        }
    })

@JvmInline value class BatchUserId(val value: String)

data class BatchValueUser(val id: BatchUserId, val name: String)

private fun <T> completedFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }
