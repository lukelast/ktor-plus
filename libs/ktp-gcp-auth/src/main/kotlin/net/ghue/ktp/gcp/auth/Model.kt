package net.ghue.ktp.gcp.auth

import com.google.firebase.auth.FirebaseToken
import kotlinx.serialization.Serializable

interface AuthLifecycleHandler {
    suspend fun onLogin(firebaseToken: FirebaseToken): UserInfo

    suspend fun onLogout(userSession: UserSession) {}
}

data class UserInfo(
    val userId: UserId,
    val tenantId: TenantId,
    val email: String,
    val name: String,
    val roles: Set<String>,
    /** Can be any object that can be serialized and sent to the browser. */
    val extra: Any?,
)

@Serializable internal data class LoginRequest(val idToken: String)

internal data class LoginResponse(val user: LoginResponseUser? = null)

internal data class LoginResponseUser(
    val userId: String,
    val email: String,
    val nameFull: String,
    val nameFirst: String,
    val roles: Set<String>,
    val extra: Any?,
)

@JvmInline @Serializable value class UserId(val value: String)

@JvmInline @Serializable value class TenantId(val value: String)

val FirebaseToken.userId: UserId
    get() = UserId(uid)
val FirebaseToken.isAnonymous: Boolean
    get() = claims["provider_id"] == "anonymous"
