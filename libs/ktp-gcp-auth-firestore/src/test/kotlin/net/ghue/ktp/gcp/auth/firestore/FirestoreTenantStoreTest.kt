package net.ghue.ktp.gcp.auth.firestore

import com.google.api.core.ApiFutures
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.ghue.ktp.gcp.auth.TenantId

class FirestoreTenantStoreTest :
    FunSpec({
        test("get returns the typed tenant or null when missing") {
            TenantDb(mapOf("setting" to "saved")).store.get(TENANT) shouldBe
                TestTenant(TENANT, "saved")
            TenantDb(null).store.get(TENANT) shouldBe null
        }

        test("initialization preserves an existing tenant without evaluating defaults") {
            val db = TenantDb(mapOf("setting" to "saved"))

            db.store.getOrCreate(TENANT) { error("Must not reset settings") } shouldBe
                TestTenant(TENANT, "saved")

            verify(exactly = 0) { db.transaction.create(any(), any<Any>()) }
        }

        test("initialization creates the typed application record at the tenant id") {
            val db = TenantDb(null)

            db.store.getOrCreate(TENANT) { TestTenant(TENANT, "default") } shouldBe
                TestTenant(TENANT, "default")

            verify { db.transaction.create(db.reference, mapOf("setting" to "default")) }
        }

        test("initialization rejects a record for a different tenant") {
            val db = TenantDb(null)

            shouldThrow<IllegalArgumentException> {
                db.store.getOrCreate(TENANT) { TestTenant(TenantId("other")) }
            }

            verify(exactly = 0) { db.transaction.create(any(), any<Any>()) }
        }
    })

private val TENANT = TenantId("tenant-1")

data class TestTenant(override val id: TenantId, val setting: String = "") : TenantRecord

private class TenantDb(data: Map<String, Any>?) {
    val reference = mockk<DocumentReference>()
    val transaction = mockk<Transaction>(relaxed = true)
    private val db = mockk<Firestore>()
    val store: FirestoreTenantStore<TestTenant>

    init {
        val tenants = mockk<CollectionReference>()
        every { db.collection(TENANT_COLLECTION) } returns tenants
        every { tenants.document(TENANT.value) } returns reference
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.data } returns data
        every { snapshot.id } returns TENANT.value
        every { reference.get() } returns ApiFutures.immediateFuture(snapshot)
        every { transaction.get(reference) } returns ApiFutures.immediateFuture(snapshot)
        every { transaction.create(reference, any<Map<String, Any>>()) } returns transaction
        every { db.runTransaction(any<Transaction.Function<TestTenant>>()) } answers
            {
                ApiFutures.immediateFuture(
                    firstArg<Transaction.Function<TestTenant>>().updateCallback(transaction)
                )
            }
        store = db.tenantStore()
    }
}
