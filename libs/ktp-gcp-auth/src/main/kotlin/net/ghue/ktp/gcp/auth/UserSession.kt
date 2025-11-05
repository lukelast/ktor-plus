package net.ghue.ktp.gcp.auth

import kotlinx.serialization.Serializable

/** Note that everything here is stored in the session cookie so keep it small. */
@Serializable
data class UserSession(
    val userId: String,
    val email: String,
    val nameFull: String,
    val nameFirst: String,
    val roles: Set<String>,
) {
    fun hasRole(role: String): Boolean = role in roles

    fun requireRole(role: String) {
        if (!hasRole(role)) {
            error("User $userId does not have role $role")
        }
    }
}
