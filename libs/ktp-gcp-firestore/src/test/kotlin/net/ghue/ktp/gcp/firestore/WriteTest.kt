package net.ghue.ktp.gcp.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.SettableApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FirestoreException
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.WriteResult
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.ghue.ktp.ktor.error.KtpRspEx

class WriteTest :
    StringSpec({
        "idFieldValue returns id for string and value class ids" {
            data class User(val id: String, val name: String)
            data class NumericUser(val id: Int)

            idFieldValue(User(id = "user-1", name = "Ada")) shouldBe "user-1"
            idFieldValue(ValueUser(id = UserId("value-1"), name = "Ada")) shouldBe "value-1"
            idFieldValue(NumericUser(id = 42)) shouldBe "42"
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

        "upsert uses merge options by default" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { docRef.set(any<Map<String, Any>>(), SetOptions.merge()) } returns
                completedFuture(mockk<WriteResult>())

            collection.upsert(User(id = "user-1", name = "Ada"))

            verify(exactly = 1) { docRef.set(mapOf("name" to "Ada"), SetOptions.merge()) }
        }

        "replace overwrites serialized data without merge options" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { docRef.set(any<Map<String, Any>>()) } returns
                completedFuture(mockk<WriteResult>())

            collection.replace(User(id = "user-1", name = "Ada"))

            verify(exactly = 1) { docRef.set(mapOf("name" to "Ada")) }
        }

        "setMerge forwards raw fields with merge options" {
            val docRef = mockk<DocumentReference>()
            val fields = mapOf<String, Any>("id" to "kept", "count" to 2)

            every { docRef.set(fields, SetOptions.merge()) } returns
                completedFuture(mockk<WriteResult>())

            docRef.setMerge(fields)

            verify(exactly = 1) { docRef.set(fields, SetOptions.merge()) }
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

        "createOrNull stores serialized data without id and returns the document" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { docRef.create(any<Map<String, Any>>()) } returns
                completedFuture(mockk<WriteResult>())

            val user = User(id = "user-1", name = "Ada")
            collection.createOrNull(user) shouldBe user

            verify(exactly = 1) { docRef.create(mapOf("name" to "Ada")) }
        }

        "createOrNull returns null when the document already exists" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { docRef.create(any<Map<String, Any>>()) } returns
                failedFuture(FirestoreException.forServerRejection(Status.ALREADY_EXISTS, "exists"))

            collection.createOrNull(User(id = "user-1", name = "Ada")) shouldBe null
        }

        "createOrNull rethrows other Firestore errors" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { docRef.create(any<Map<String, Any>>()) } returns
                failedFuture(FirestoreException.forServerRejection(Status.PERMISSION_DENIED, "no"))

            shouldThrow<FirestoreException> {
                collection.createOrNull(User(id = "user-1", name = "Ada"))
            }
        }

        "newId mints an id without writing" {
            val collection = mockk<CollectionReference>()

            every { collection.document() } returns mockk { every { id } returns "new-id" }

            collection.newId() shouldBe "new-id"
        }

        "deleteById and delete remove the document by id" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { docRef.delete() } returns completedFuture(mockk<WriteResult>())

            collection.deleteById("user-1")
            collection.delete(User(id = "user-1", name = "Ada"))

            verify(exactly = 2) { docRef.delete() }
        }
    })

@JvmInline value class UserId(val value: String)

data class ValueUser(val id: UserId, val name: String)

@JvmInline value class NullableUserId(val value: String?)

data class NullableValueUser(val id: NullableUserId, val name: String)

private fun <T> completedFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }

private fun <T> failedFuture(error: Throwable): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { setException(error) }
