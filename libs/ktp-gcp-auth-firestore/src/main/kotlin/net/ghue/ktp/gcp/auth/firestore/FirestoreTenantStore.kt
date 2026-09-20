package net.ghue.ktp.gcp.auth.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Transaction
import kotlin.reflect.KClass
import net.ghue.ktp.gcp.auth.TenantId
import net.ghue.ktp.gcp.firestore.deserialize
import net.ghue.ktp.gcp.firestore.serialize
import net.ghue.ktp.gcp.firestore.transaction
import net.ghue.ktp.gcp.join

/** App data at `tenant/{tenantId}`; [id] comes from the document ID. */
interface TenantRecord {
    val id: TenantId
}

const val TENANT_COLLECTION = "tenant"

/** Typed storage for app-owned tenant settings and state. */
class FirestoreTenantStore<T : TenantRecord>(
    private val db: Firestore,
    private val recordClass: KClass<T>,
) {
    private val tenants = db.collection(TENANT_COLLECTION)

    fun get(tenantId: TenantId): T? =
        tenants.document(tenantId.value).get().join().deserialize(recordClass)

    /** Creates only missing records. [initialize] may retry; avoid external side effects. */
    fun getOrCreate(tenantId: TenantId, initialize: () -> T): T =
        transaction(tenantId) { reference, current ->
            current
                ?: initialize().also {
                    require(it.id == tenantId) { "Tenant record does not match write scope" }
                    create(reference, it.serialize())
                }
        }

    /**
     * Atomically reads the tenant and runs [block], which may retry; avoid external side effects.
     * Use the supplied transaction: read before writing and update only owned fields. The caller
     * must authorize [tenantId].
     */
    fun <R> transaction(
        tenantId: TenantId,
        block: Transaction.(DocumentReference, T?) -> R,
    ): R = db.transaction {
        val reference = tenants.document(tenantId.value)
        block(reference, get(reference).join().deserialize(recordClass))
    }
}

inline fun <reified T : TenantRecord> Firestore.tenantStore(): FirestoreTenantStore<T> =
    FirestoreTenantStore(this, T::class)
