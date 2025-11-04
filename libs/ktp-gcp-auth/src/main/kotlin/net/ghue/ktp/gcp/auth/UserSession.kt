package net.ghue.ktp.gcp.auth

import kotlinx.serialization.Serializable

/** Note that everything here is stored in the session cookie so keep it small. */
@Serializable
data class UserSession(
    val userId: String,
    val email: String,
    val nameFull: String,
    val nameFirst: String,
)
