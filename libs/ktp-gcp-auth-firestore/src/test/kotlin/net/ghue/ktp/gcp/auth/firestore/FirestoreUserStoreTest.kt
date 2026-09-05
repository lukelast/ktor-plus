package net.ghue.ktp.gcp.auth.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.SettableApiFuture
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreException
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.WriteResult
import io.grpc.Status
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import net.ghue.ktp.gcp.auth.LoginIdentity
import net.ghue.ktp.gcp.auth.TenantId
import net.ghue.ktp.gcp.auth.UserId
import net.ghue.ktp.gcp.auth.UserInfo
import net.ghue.ktp.gcp.firestore.toTimestamp

private val NOW = Instant.parse("2026-09-04T12:00:00Z")
private val EARLIER = Instant.parse("2026-01-01T00:00:00Z")
private val CREATED = Instant.parse("2025-06-01T00:00:00Z")
private val ALICE = LoginIdentity(UserId("dev-alice"), "alice@dev.test", "Alice")

class FirestoreUserStoreTest :
    StringSpec({
        "the login hook returns the stored record as session info" {
            val db = MockDb()
            db.snapshotIsMissing()
            db.acceptCreate()

            val info = db.store().onLogin(ALICE)

            info shouldBe
                UserInfo(
                    userId = UserId("dev-alice"),
                    tenantId = TenantId("tenant-new"),
                    email = "alice@dev.test",
                    name = "Alice",
                    roles = setOf(),
                )
        }

        "first login creates the record with a fresh tenant and stamps the login" {
            val db = MockDb()
            db.snapshotIsMissing()
            val created = db.acceptCreate()

            val user = db.store().login(ALICE)

            user shouldBe
                KtpUser(
                    id = UserId("dev-alice"),
                    tenantId = TenantId("tenant-new"),
                    name = "Alice",
                    email = "alice@dev.test",
                    lastLogin = NOW,
                )
            // Creation time is Firestore metadata, never a stored field.
            created.captured shouldContainExactly
                mapOf(
                    "tenantId" to "tenant-new",
                    "name" to "Alice",
                    "email" to "alice@dev.test",
                    "lastLogin" to NOW.toTimestamp(),
                    "roles" to emptyList<String>(),
                )
            verify(exactly = 0) { db.docRef.set(any<Map<String, Any>>(), any<SetOptions>()) }
        }

        "repeat login merges only the login fields and keeps the stored tenant, roles and times" {
            val db = MockDb()
            db.snapshotHolds(storedAlice(name = "Old Name", roles = listOf("admin")))
            val merged = db.acceptMerge()

            val user = db.store().login(ALICE)

            user shouldBe
                KtpUser(
                    id = UserId("dev-alice"),
                    tenantId = TenantId("tenant-stored"),
                    name = "Alice",
                    email = "alice@dev.test",
                    lastLogin = NOW,
                    roles = setOf("admin"),
                    createTime = CREATED,
                    updateTime = EARLIER,
                )
            merged.captured shouldContainExactly
                mapOf(
                    "name" to "Alice",
                    "email" to "alice@dev.test",
                    "lastLogin" to NOW.toTimestamp(),
                )
            verify(exactly = 0) { db.docRef.create(any<Map<String, Any>>()) }
        }

        "losing the first-login race adopts the winner's tenant instead of minting one" {
            val db = MockDb()
            db.snapshotIsMissingThenHolds(storedAlice(name = "Alice", roles = emptyList()))
            db.rejectCreateAsExisting()
            val merged = db.acceptMerge()

            val user = db.store().login(ALICE)

            user.tenantId shouldBe TenantId("tenant-stored")
            user.createTime shouldBe CREATED
            merged.captured["lastLogin"] shouldBe NOW.toTimestamp()
        }

        "get returns null for an unknown user" {
            val db = MockDb()
            db.snapshotIsMissing()

            db.store().get(UserId("dev-alice")) shouldBe null
        }
    })

private class MockDb {
    val firestore = mockk<Firestore>()
    val collection = mockk<CollectionReference>()
    val docRef = mockk<DocumentReference>()

    init {
        every { firestore.collection("user") } returns collection
        every { collection.document("dev-alice") } returns docRef
        val newDocRef = mockk<DocumentReference>()
        every { newDocRef.id } returns "tenant-new"
        every { collection.document() } returns newDocRef
    }

    fun store() = FirestoreUserStore(firestore, Clock.fixed(NOW, ZoneOffset.UTC))

    fun snapshotIsMissing() {
        every { docRef.get() } returns completedFuture(missingSnapshot())
    }

    fun snapshotHolds(data: Map<String, Any>) {
        every { docRef.get() } returns completedFuture(snapshot(data))
    }

    fun snapshotIsMissingThenHolds(data: Map<String, Any>) {
        every { docRef.get() } returnsMany
            listOf(completedFuture(missingSnapshot()), completedFuture(snapshot(data)))
    }

    fun acceptCreate() =
        slot<Map<String, Any>>().also { captured ->
            every { docRef.create(capture(captured)) } returns completedFuture(mockk<WriteResult>())
        }

    fun rejectCreateAsExisting() {
        every { docRef.create(any<Map<String, Any>>()) } returns
            failedFuture(FirestoreException.forServerRejection(Status.ALREADY_EXISTS, "exists"))
    }

    fun acceptMerge() =
        slot<Map<String, Any>>().also { captured ->
            every { docRef.set(capture(captured), any<SetOptions>()) } returns
                completedFuture(mockk<WriteResult>())
        }
}

private fun storedAlice(name: String, roles: List<String>): Map<String, Any> =
    mapOf(
        "tenantId" to "tenant-stored",
        "name" to name,
        "email" to "alice@dev.test",
        "lastLogin" to EARLIER.toTimestamp(),
        "roles" to roles,
        // Left behind by records written before createTime came from metadata; must be ignored.
        "createdAt" to EARLIER.toTimestamp(),
    )

/** A snapshot created at [CREATED] and last written at [EARLIER]. */
private fun snapshot(data: Map<String, Any>): DocumentSnapshot {
    val doc = mockk<DocumentSnapshot>()
    every { doc.data } returns data
    every { doc.id } returns "dev-alice"
    every { doc.createTime } returns CREATED.toTimestamp()
    every { doc.updateTime } returns EARLIER.toTimestamp()
    return doc
}

private fun missingSnapshot(): DocumentSnapshot {
    val doc = mockk<DocumentSnapshot>()
    every { doc.data } returns null
    return doc
}

private fun <T> completedFuture(value: T): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { set(value) }

private fun <T> failedFuture(error: Throwable): ApiFuture<T> =
    SettableApiFuture.create<T>().apply { setException(error) }
