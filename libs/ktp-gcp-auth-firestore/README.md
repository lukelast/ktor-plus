# Firestore user and tenant stores

## Setup

Depend on `KtpLibs.gcpAuthFirestore`, which includes auth and Firestore. Register in `ktpAppCreate`:

```kotlin
addModule(firebaseAuthModule())
addModule(firestoreModule())
addModule(firestoreUserStoreModule())
```

Install `FirebaseAuthPlugin` as described in [ktp-gcp-auth](../ktp-gcp-auth/README.md).

## Users

`FirestoreUserStore` implements `AuthLifecycleHandler` and stores `KtpUser` at `user/{userId}`.
First login creates the user and a stable tenant ID; later logins refresh only `name`, `email`,
and `lastLogin`. Deleting the record does not ban the account.

Use `get(userId)` to read a user or `login(identity)` in a custom login hook.
`createTime` and `updateTime` come from Firestore metadata. The `user` collection is fixed.

## Tenants

Keep application settings and bounded state in the fixed `tenant` collection at `tenant/{tenantId}`.
Define a `TenantRecord`; `id` is reserved for the document ID and omitted by `serialize()`.
For records implementing `DocTimes`, `createTime` and `updateTime` are also reserved metadata.

```kotlin
data class DbTenant(
    override val id: TenantId,
    val timezone: String = "UTC",
) : TenantRecord

val tenants = firestore.tenantStore<DbTenant>()
val tenant = tenants.getOrCreate(tenantId) { DbTenant(tenantId) }

tenants.transaction(tenantId) { reference, current ->
    requireNotNull(current)
    update(reference, DbTenant::timezone.name, "America/New_York")
}
```

- `get` returns null when missing.
- `getOrCreate` atomically initializes a missing record; login does not initialize tenant data.
- `transaction` supplies the current record (or null), document reference and SDK transaction.
  Read before writing, use that transaction for every operation, and keep callbacks safe to retry.

Update only owned fields. Field updates replace maps; merge-sets retain omitted entries.
Serialize values passed to the SDK, including `record.serialize()` for creates.
The caller must authorize the tenant ID; user deletion does not remove tenant data.

## Testing

Replace `FirebaseAuth` and `Firestore` with mocks and stub the operations exercised.
Use `addOverrideModule` to replace app-owned services.
See [auth testing](../ktp-gcp-auth/README.md#testing-an-app) for session tests.
