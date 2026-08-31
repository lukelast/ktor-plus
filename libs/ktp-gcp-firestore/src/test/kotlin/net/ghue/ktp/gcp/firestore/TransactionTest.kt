package net.ghue.ktp.gcp.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.SettableApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.QuerySnapshot
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.Transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.ghue.ktp.ktor.error.KtpRspExNotFound

class TransactionTest :
    StringSpec({
        // Runs the callback inline and surfaces its exception via the future, as the SDK does.
        fun mockRunTransaction(firestore: Firestore, txn: Transaction) {
            every { firestore.runTransaction(any<Transaction.Function<Any?>>()) } answers
                {
                    val callback = firstArg<Transaction.Function<Any?>>()
                    try {
                        completedFuture(callback.updateCallback(txn))
                    } catch (e: Exception) {
                        SettableApiFuture.create<Any?>().apply { setException(e) }
                    }
                }
        }

        "transaction runs the block and returns its result" {
            val firestore = mockk<Firestore>()
            val txn = mockk<Transaction>()
            mockRunTransaction(firestore, txn)

            val result = firestore.transaction { "done" }

            result shouldBe "done"
            verify(exactly = 1) { firestore.runTransaction(any<Transaction.Function<Any?>>()) }
        }

        "transaction rethrows the block's exception unwrapped" {
            val firestore = mockk<Firestore>()
            val txn = mockk<Transaction>()
            mockRunTransaction(firestore, txn)

            val error =
                shouldThrow<KtpRspExNotFound> {
                    firestore.transaction { throw KtpRspExNotFound("User", "user-1") }
                }
            error.id shouldBe "user-1"
        }

        "getOrNull reads through the transaction and getOrThrow throws when missing" {
            data class User(val id: String, val name: String)

            val txn = mockk<Transaction>()
            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()
            val doc = mockk<DocumentSnapshot>()
            val emptyDoc = mockk<DocumentSnapshot>()

            every { collection.document("user-1") } returns docRef
            every { txn.get(docRef) } returnsMany
                listOf(completedFuture(doc), completedFuture(doc), completedFuture(emptyDoc))
            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"
            every { emptyDoc.data } returns null

            txn.getOrNull<User>(collection, "user-1") shouldBe User(id = "user-1", name = "Ada")
            txn.getOrThrow<User>(collection, "user-1") shouldBe User(id = "user-1", name = "Ada")

            val error = shouldThrow<KtpRspExNotFound> { txn.getOrThrow<User>(collection, "user-1") }
            error.id shouldBe "user-1"
        }

        "getList deserializes query documents read through the transaction" {
            data class User(val id: String, val name: String)

            val txn = mockk<Transaction>()
            val query = mockk<Query>()
            val snapshot = mockk<QuerySnapshot>()
            val doc1 = mockk<QueryDocumentSnapshot>()
            val doc2 = mockk<QueryDocumentSnapshot>()

            every { txn.get(query) } returns completedFuture(snapshot)
            every { snapshot.documents } returns mutableListOf(doc1, doc2)
            every { doc1.data } returns mapOf("name" to "Ada")
            every { doc1.id } returns "user-1"
            every { doc2.data } returns mapOf("name" to "Bob")
            every { doc2.id } returns "user-2"

            txn.getList<User>(query) shouldContainExactly
                listOf(User(id = "user-1", name = "Ada"), User(id = "user-2", name = "Bob"))
        }

        "upsert merge-writes without id and replace writes exactly" {
            data class User(val id: String, val name: String)

            val txn = mockk<Transaction>()
            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { collection.document("user-1") } returns docRef
            every { txn.set(docRef, any<Map<String, Any>>(), any<SetOptions>()) } returns txn
            every { txn.set(docRef, any<Map<String, Any>>()) } returns txn

            txn.upsert(collection, User(id = "user-1", name = "Ada"))
            txn.replace(collection, User(id = "user-1", name = "Bob"))

            verify(exactly = 1) { txn.set(docRef, mapOf("name" to "Ada"), SetOptions.merge()) }
            verify(exactly = 1) { txn.set(docRef, mapOf("name" to "Bob")) }
        }
    })

private fun <T> completedFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }
