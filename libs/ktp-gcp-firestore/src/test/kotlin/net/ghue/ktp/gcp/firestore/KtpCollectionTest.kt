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
import com.google.cloud.firestore.WriteResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.ghue.ktp.ktor.error.KtpRspExNotFound

class KtpCollectionTest :
    StringSpec({
        "typedCollection binds the collection by name" {
            data class User(val id: String, val name: String)

            val db = mockk<Firestore>()
            val ref = mockk<CollectionReference>()
            every { db.collection("user") } returns ref

            db.typedCollection<User>("user").ref shouldBe ref
        }

        "getOrNull returns the typed document and getOrThrow throws when missing" {
            data class User(val id: String, val name: String)

            val ref = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()
            val doc = mockk<DocumentSnapshot>()
            val emptyDoc = mockk<DocumentSnapshot>()

            every { ref.document("user-1") } returns docRef
            every { docRef.get() } returnsMany listOf(colFuture(doc), colFuture(emptyDoc))
            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"
            every { emptyDoc.data } returns null

            val users = KtpCollection(ref, User::class)

            users.getOrNull("user-1") shouldBe User(id = "user-1", name = "Ada")

            val error = shouldThrow<KtpRspExNotFound> { users.getOrThrow("user-1") }
            error.id shouldBe "user-1"
        }

        "query builds against the collection and returns typed matches" {
            data class User(val id: String, val name: String)

            val ref = mockk<CollectionReference>()
            val filtered = mockk<Query>()
            val snapshot = mockk<QuerySnapshot>()
            val doc = mockk<QueryDocumentSnapshot>()

            every { ref.whereEqualTo("name", "Ada") } returns filtered
            every { filtered.get() } returns colFuture(snapshot)
            every { snapshot.documents } returns mutableListOf(doc)
            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"

            val result = KtpCollection(ref, User::class).query { whereEq(User::name, "Ada") }

            result shouldContainExactly listOf(User(id = "user-1", name = "Ada"))
        }

        "getList returns every document in the collection" {
            data class User(val id: String, val name: String)

            val ref = mockk<CollectionReference>()
            val snapshot = mockk<QuerySnapshot>()
            val doc = mockk<QueryDocumentSnapshot>()

            every { ref.get() } returns colFuture(snapshot)
            every { snapshot.documents } returns mutableListOf(doc)
            every { doc.data } returns mapOf("name" to "Ada")
            every { doc.id } returns "user-1"

            KtpCollection(ref, User::class).getList() shouldContainExactly
                listOf(User(id = "user-1", name = "Ada"))
        }

        "upsert delegates with the id stripped" {
            data class User(val id: String, val name: String)

            val ref = mockk<CollectionReference>()
            val docRef = mockk<DocumentReference>()

            every { ref.document("user-1") } returns docRef
            every { docRef.set(any<Map<String, Any>>(), any<SetOptions>()) } returns
                colFuture(mockk<WriteResult>())

            KtpCollection(ref, User::class).upsert(User(id = "user-1", name = "Ada"))

            verify(exactly = 1) { docRef.set(mapOf("name" to "Ada"), any<SetOptions>()) }
        }

        "sub creates a typed handle for a subcollection" {
            data class User(val id: String, val name: String)
            data class Item(val id: String, val label: String)

            val ref = mockk<CollectionReference>()
            val parentDoc = mockk<DocumentReference>()
            val subRef = mockk<CollectionReference>()

            every { ref.document("user-1") } returns parentDoc
            every { parentDoc.collection("item") } returns subRef

            val items = KtpCollection(ref, User::class).sub<Item>("user-1", "item")

            items.ref shouldBe subRef
        }
    })

private fun <T> colFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }
