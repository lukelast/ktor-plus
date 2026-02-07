package net.ghue.ktp.gcp.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.SettableApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldPath
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.QuerySnapshot
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.WriteBatch
import com.google.cloud.firestore.WriteResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.ghue.ktp.ktor.error.KtpRspEx
import net.ghue.ktp.ktor.error.KtpRspExNotFound

class FirestoreExtTest :
    StringSpec({
        "idFieldValue returns id for string and value class ids" {
            data class User(val id: String, val name: String)

            idFieldValue(User(id = "user-1", name = "Ada")) shouldBe "user-1"
            idFieldValue(ValueUser(id = UserId("value-1"), name = "Ada")) shouldBe "value-1"
        }

        "idFieldValue throws when id missing or null" {
            data class MissingId(val name: String)
            data class NullId(val id: String?, val name: String)

            val missing = shouldThrow<KtpRspEx> { idFieldValue(MissingId(name = "Ada")) }
            missing.detail shouldBe "Property 'id' not found on MissingId"

            val nullId = shouldThrow<KtpRspEx> { idFieldValue(NullId(id = null, name = "Ada")) }
            nullId.detail shouldBe "Property 'id' is null on NullId"
        }

        "idFieldValue throws when id is empty string" {
            data class EmptyId(val id: String, val name: String)

            val emptyId = shouldThrow<KtpRspEx> { idFieldValue(EmptyId(id = "", name = "Ada")) }
            emptyId.detail shouldBe "Property 'id' is empty on EmptyId"
        }

        "idFieldValue throws when value class id is null" {
            val error =
                shouldThrow<KtpRspEx> {
                    idFieldValue(NullableValueUser(id = NullableUserId(null), name = "Ada"))
                }
            error.detail shouldBe "Property 'id' is null on NullableValueUser"
        }

        "upsert stores serialized data without id and passes options" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()
            val future = completedFuture(mockk<WriteResult>())
            val options = SetOptions.mergeFields("name")

            every { collection.document("user-1") } returns docRef
            every { docRef.set(any<Map<String, Any>>(), options) } returns future

            collection.upsert(User(id = "user-1", name = "Ada"), options)

            verify(exactly = 1) { docRef.set(mapOf("name" to "Ada"), options) }
        }

        "newDoc creates a document with generated id and stores without id" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()
            val future = completedFuture(mockk<WriteResult>())

            every { collection.document() } returns docRef
            every { docRef.id } returns "new-id"
            every { docRef.set(any<Map<String, Any>>()) } returns future

            val created = collection.newDoc { id -> User(id = id, name = "Ada") }

            created shouldBe User(id = "new-id", name = "Ada")
            verify(exactly = 1) { docRef.set(mapOf("name" to "Ada")) }
        }

        "getList deserializes documents" {
            data class User(val id: String, val name: String)

            val query = mockk<Query>()
            val snapshot = mockk<QuerySnapshot>()
            val doc1 = mockk<QueryDocumentSnapshot>()
            val doc2 = mockk<QueryDocumentSnapshot>()

            every { query.get() } returns completedFuture(snapshot)
            every { snapshot.documents } returns mutableListOf(doc1, doc2)
            every { doc1.data } returns mapOf("name" to "Ada")
            every { doc1.id } returns "user-1"
            every { doc2.data } returns mapOf("name" to "Bob")
            every { doc2.id } returns "user-2"

            val result = query.getList<User>()

            result shouldContainExactly
                listOf(User(id = "user-1", name = "Ada"), User(id = "user-2", name = "Bob"))
        }

        "firstOrNull returns null when no documents" {
            data class User(val id: String, val name: String)

            val query = mockk<Query>()
            val snapshot = mockk<QuerySnapshot>()

            every { query.get() } returns completedFuture(snapshot)
            every { snapshot.documents } returns mutableListOf()

            query.firstOrNull<User>() shouldBe null
        }

        "getOrNull returns deserialized object with id and getOrThrow throws when missing" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()
            val doc = mockk<DocumentSnapshot>()
            val emptyDoc = mockk<DocumentSnapshot>()

            every { collection.document("user-1") } returns docRef
            every { docRef.get() } returnsMany
                listOf(completedFuture(doc), completedFuture(emptyDoc))

            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"
            every { emptyDoc.data } returns null

            collection.getOrNull<User>("user-1") shouldBe User(id = "user-1", name = "Ada")

            val error = shouldThrow<KtpRspExNotFound> { collection.getOrThrow<User>("user-1") }
            error.id shouldBe "user-1"
        }

        "batchWrite chunks items and commits each batch" {
            data class Item(val id: String, val name: String)

            val firestore = mockk<Firestore>()
            val collection = mockk<CollectionReference>()
            val batch = mockk<WriteBatch>()

            val items = (1..(FIRESTORE_BATCH_SIZE + 1)).map { Item(id = "id-$it", name = "n-$it") }

            every { firestore.batch() } returns batch
            every { batch.set(any(), any<Map<String, Any>>(), any()) } returns batch
            every { batch.commit() } returns completedFuture(listOf(mockk<WriteResult>()))
            every { collection.document(any<String>()) } returns mockk<DocumentReference>()

            firestore.batchWrite(collection, items)

            verify(exactly = 2) { firestore.batch() }
            verify(exactly = items.size) {
                batch.set(any(), match { !it.containsKey("id") }, any())
            }
            verify(exactly = 2) { batch.commit() }
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
    })

@JvmInline value class UserId(val value: String)

data class ValueUser(val id: UserId, val name: String)

@JvmInline value class NullableUserId(val value: String?)

data class NullableValueUser(val id: NullableUserId, val name: String)

private fun <T> completedFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }
