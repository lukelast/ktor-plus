package net.ghue.ktp.gcp.auth

import kotlinx.serialization.Serializable

/** Note that everything here is stored in the session cookie so keep it small. */
@Serializable
data class UserSession(
    override val userId: UserId,
    override val email: String,
    override val name: String,
    override val roles: Set<String>,
    override val tenId: TenantId,
) : UserPrincipal, HasRoles

interface HasRoles {
    val userId: UserId
    val roles: Set<String>

    fun hasRole(role: Role): Boolean = role.name in roles
}

interface UserPrincipal : HasRoles {
    override val userId: UserId
    val tenId: TenantId
    val email: String
    val name: String
    val nameFirst: String
        get() = name.split(" ").first()

    val isAnonymous: Boolean
        get() = email.isEmpty()
}
