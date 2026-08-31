package net.ghue.ktp.gcp.auth

import kotlinx.serialization.Serializable

/** Public Firebase configuration used to initialize a browser client at runtime. */
@Serializable
data class FirebaseClientConfig(
    val apiKey: String,
    val projectId: String,
    val authDomain: String,
)

@Serializable
data class AuthClientConfig(
    val firebase: FirebaseClientConfig,
    val enabledProviders: List<String>,
)
