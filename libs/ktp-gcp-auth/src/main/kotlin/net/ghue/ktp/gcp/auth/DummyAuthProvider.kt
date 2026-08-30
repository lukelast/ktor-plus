package net.ghue.ktp.gcp.auth

import io.ktor.server.auth.*

/** Bypasses authentication for tests. */
fun AuthenticationConfig.dummy(
    providerName: String = AuthProviderName.FIREBASE_SESSION,
    principal: UserSession =
        UserSession(
            userId = UserId(""),
            tenId = TenantId(""),
            email = "",
            name = "",
            roles = emptySet(),
        ),
) {
    register(
        object : AuthenticationProvider(object : Config(providerName) {}) {
            override suspend fun onAuthenticate(context: AuthenticationContext) =
                context.principal(principal)
        }
    )
}
