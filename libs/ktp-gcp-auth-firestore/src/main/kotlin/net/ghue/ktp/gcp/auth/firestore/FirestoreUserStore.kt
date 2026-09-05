package net.ghue.ktp.gcp.auth.firestore

import com.google.cloud.firestore.Firestore
import java.time.Clock
import java.time.Instant
import net.ghue.ktp.gcp.auth.AuthLifecycleHandler
import net.ghue.ktp.gcp.auth.LoginIdentity
import net.ghue.ktp.gcp.auth.TenantId
import net.ghue.ktp.gcp.auth.UserId
import net.ghue.ktp.gcp.auth.UserInfo
import net.ghue.ktp.gcp.firestore.DocTimes
import net.ghue.ktp.gcp.firestore.setMerge
import net.ghue.ktp.gcp.firestore.toTimestamp
import net.ghue.ktp.gcp.firestore.typedCollection
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Default Firestore collection holding one [KtpUser] document per user id. */
const val USER_COLLECTION = "user"

/**
 * The stored user record; one document per user id, minting a tenant on first login. [createTime]
 * is when the user first logged in and [updateTime] the last write by anyone, both from Firestore's
 * document metadata, so they are only set on records read back. [lastLogin] is a stored field
 * because a write from another tool (say, granting a role) is not a login.
 */
data class KtpUser(
    val id: UserId,
    val tenantId: TenantId,
    val name: String,
    val email: String,
    val lastLogin: Instant,
    val roles: Set<String> = setOf(),
    override val createTime: Instant? = null,
    override val updateTime: Instant? = null,
) : DocTimes {
    fun toUserInfo(): UserInfo =
        UserInfo(userId = id, tenantId = tenantId, email = email, name = name, roles = roles)
}

/**
 * Exactly the fields a login rewrites, and no others, so a login can never stomp fields that other
 * writers own (roles, app-specific additions). These go through `setMerge`, which bypasses the
 * reflective mapper, hence the hand-converted [Instant].
 */
private fun loginFields(identity: LoginIdentity, at: Instant): Map<String, Any> =
    mapOf(
        KtpUser::name.name to identity.name,
        KtpUser::email.name to identity.email,
        KtpUser::lastLogin.name to at.toTimestamp(),
    )

/**
 * User records on Firestore, keyed by user id in [collectionName], and the stock
 * [AuthLifecycleHandler] that every login runs through. An app wanting extra behaviour wraps this
 * in a handler of its own and calls [login] for the stored record.
 */
class FirestoreUserStore(
    db: Firestore,
    private val clock: Clock = Clock.systemUTC(),
    collectionName: String = USER_COLLECTION,
) : AuthLifecycleHandler {
    private val users = db.typedCollection<KtpUser>(collectionName)

    override suspend fun onLogin(identity: LoginIdentity): UserInfo = login(identity).toUserInfo()

    /**
     * Records a login and returns the stored user, creating it and its tenant on first sight. First
     * sight is a create-only write, and losing the create race means adopting the winner's row, so
     * concurrent first logins can never each mint their own tenant: the returned [KtpUser.tenantId]
     * is always the persisted one. The returned [DocTimes] are those of the record as read, so a
     * first login has none; call [get] for fresh ones.
     */
    fun login(identity: LoginIdentity): KtpUser {
        val now = clock.instant()

        var existing = get(identity.userId)
        if (existing == null) {
            create(identity, now)?.let {
                return it
            }
            // A concurrent first login won the create; Firestore reads are strongly consistent,
            // so the winner's row is there now.
            existing =
                get(identity.userId)
                    ?: error("user ${identity.userId.value} missing after create conflict")
        }

        users.ref.document(existing.id.value).setMerge(loginFields(identity, now))
        return existing.copy(name = identity.name, email = identity.email, lastLogin = now)
    }

    fun get(userId: UserId): KtpUser? = users.getOrNull(userId.value)

    /** Create-only first-login write: null when a concurrent login already created the row. */
    private fun create(identity: LoginIdentity, now: Instant): KtpUser? =
        users.createOrNull(
            KtpUser(
                id = identity.userId,
                tenantId = TenantId(users.newId()),
                name = identity.name,
                email = identity.email,
                lastLogin = now,
            )
        )
}

/**
 * Binds one [FirestoreUserStore], also as the [AuthLifecycleHandler]. An app that needs its own
 * handler just defines one: app definitions are applied after library modules, so theirs wins. Uses
 * the app's [Clock] binding when there is one.
 */
fun firestoreUserStoreModule(collectionName: String = USER_COLLECTION): Module = module {
    single {
        FirestoreUserStore(
            db = get(),
            clock = getOrNull() ?: Clock.systemUTC(),
            collectionName = collectionName,
        )
    }
        .bind<AuthLifecycleHandler>()
}
