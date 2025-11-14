package net.ghue.ktp.gcp.auth

import kotlinx.serialization.Serializable

/** Note that everything here is stored in the session cookie so keep it small. */
@Serializable
data class UserSession(
    override val userId: String,
    override val email: String,
    override val nameFull: String,
    override val roles: Set<String>,
) : UserPrincipal, HasRoles

interface HasRoles {
    val userId: String
    val roles: Set<String>

    fun hasRole(role: Role): Boolean = role.name in roles
}

interface UserPrincipal : HasRoles {
    val email: String
    val nameFull: String
    val nameFirst: String
        get() = nameFull.split(" ").first()
}
