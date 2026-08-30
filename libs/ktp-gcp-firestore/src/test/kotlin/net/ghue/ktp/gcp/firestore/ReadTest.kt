package net.ghue.ktp.gcp.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.SettableApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.QueryDocumentSnapshot
import com.google.cloud.firestore.QuerySnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.ghue.ktp.ktor.error.KtpRspExNotFound

class ReadTest :
    StringSpec({
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

        "firstOrNull deserializes the first document" {
            data class User(val id: String, val name: String)

            val query = mockk<Query>()
            val snapshot = mockk<QuerySnapshot>()
            val doc = mockk<QueryDocumentSnapshot>()

            every { query.get() } returns completedFuture(snapshot)
            every { snapshot.documents } returns mutableListOf(doc)
            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"

            query.firstOrNull<User>() shouldBe User(id = "user-1", name = "Ada")
        }

        "getOrNull returns deserialized object with id and getOrThrow throws when missing" {
            data class User(val id: String, val name: String)

            val collection = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()
            val doc = mockk<DocumentSnapshot>()
            val emptyDoc = mockk<DocumentSnapshot>()

            every { collection.document("user-1") } returns docRef
            every { docRef.get() } returnsMany
                listOf(completedFuture(doc), completedFuture(doc), completedFuture(emptyDoc))

            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"
            every { emptyDoc.data } returns null

            collection.getOrNull<User>("user-1") shouldBe User(id = "user-1", name = "Ada")
            collection.getOrThrow<User>("user-1") shouldBe User(id = "user-1", name = "Ada")

            val error = shouldThrow<KtpRspExNotFound> { collection.getOrThrow<User>("user-1") }
            error.id shouldBe "user-1"
        }

        "DocumentReference getOrNull and getOrThrow handle present and missing documents" {
            data class User(val id: String, val name: String)

            val docRef = mockk<DocumentReference>()
            val doc = mockk<DocumentSnapshot>()
            val emptyDoc = mockk<DocumentSnapshot>()

            every { docRef.id } returns "user-1"
            every { docRef.get() } returnsMany
                listOf(completedFuture(doc), completedFuture(doc), completedFuture(emptyDoc))
            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"
            every { emptyDoc.data } returns null

            docRef.getOrNull<User>() shouldBe User(id = "user-1", name = "Ada")
            docRef.getOrThrow<User>() shouldBe User(id = "user-1", name = "Ada")

            val error = shouldThrow<KtpRspExNotFound> { docRef.getOrThrow<User>() }
            error.id shouldBe "user-1"
        }

        "listIds returns the ids of all documents" {
            val collection = mockk<CollectionReference>()
            val ref1 = mockk<DocumentReference> { every { id } returns "a" }
            val ref2 = mockk<DocumentReference> { every { id } returns "b" }

            every { collection.listDocuments() } returns listOf(ref1, ref2)

            collection.listIds() shouldContainExactly listOf("a", "b")
        }
    })

private fun <T> completedFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }
