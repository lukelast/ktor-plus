package net.ghue.ktp.gcp.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.core.sha256
import net.ghue.ktp.core.sha512

fun KtpConfig.createSessionTransportTransformer(): SessionTransportTransformer {
    val secret = this.data.app.secret + this.env.name
    return SessionTransportTransformerEncrypt(
        secret.sha256().take(16).toByteArray(),
        secret.sha512(),
    )
}

fun ApplicationCall.currentUser(): UserSession? {
    return sessions.get<UserSession>()
}

fun ApplicationCall.userOrNull(): UserPrincipal? {
    return principal()
}

suspend fun ApplicationCall.userOrError(): UserPrincipal {
    return userOrNull()
        ?: run {
            respond(HttpStatusCode.Unauthorized)
            error("User session not found, redirected to login")
        }
}

fun Route.authenticateFirebase(build: Route.() -> Unit): Route =
    authenticate(AuthProviderName.FIREBASE_SESSION, build = build)
