package net.ghue.ktp.gcp.auth

import com.google.firebase.auth.FirebaseToken
import kotlinx.serialization.Serializable

/**
 * Who is signing in, independent of how they proved it. Firebase logins build one from the verified
 * ID token; the local-dev login builds one from a request parameter. Everything after this point
 * (user records, tenants, the session cookie) only ever sees this type.
 */
data class LoginIdentity(
    val userId: UserId,
    /** Empty when the identity provider supplied none (anonymous users, some phone sign-ins). */
    val email: String,
    val name: String,
)

interface AuthLifecycleHandler {
    /**
     * Called once per session mint (login or dev login), never per page load. Persist the user here
     * and return the session to issue as the cookie; `FirestoreUserStore` in
     * `ktp-gcp-auth-firestore` is the stock implementation.
     */
    suspend fun onLogin(identity: LoginIdentity): UserSession

    suspend fun onLogout(userSession: UserSession) {}
}

@Serializable internal data class LoginRequest(val idToken: String)

/** Body of `/auth/login`, `/auth/session`, and the dev login; the browser's `User` type. */
@Serializable internal data class LoginResponse(val user: LoginResponseUser)

@Serializable
internal data class LoginResponseUser(
    val userId: String,
    val email: String,
    val nameFull: String,
    val nameFirst: String,
    val roles: Set<String>,
)

@JvmInline @Serializable value class UserId(val value: String)

@JvmInline @Serializable value class TenantId(val value: String)

val FirebaseToken.userId: UserId
    get() = UserId(uid)
val FirebaseToken.isAnonymous: Boolean
    get() = claims["provider_id"] == "anonymous"

fun FirebaseToken.toLoginIdentity(): LoginIdentity =
    LoginIdentity(userId = userId, email = email ?: "", name = name ?: "")
