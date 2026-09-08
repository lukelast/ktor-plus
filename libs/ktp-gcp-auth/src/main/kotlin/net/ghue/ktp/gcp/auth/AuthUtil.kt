package net.ghue.ktp.gcp.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.core.sha256
import net.ghue.ktp.core.sha512
import net.ghue.ktp.ktor.error.KtpRspEx

const val AES128_KEY_BYTES = 128 / 8

fun KtpConfig.createSessionTransportTransformer(): SessionTransportTransformer {
    // Mixing in the env name blocks cookie replay across environments sharing an app secret.
    val secret = this.data.app.secret + this.env.name

    return SessionTransportTransformerEncrypt(
        encryptionKey = secret.sha256().take(AES128_KEY_BYTES).toByteArray(),
        signKey = secret.sha512(),
    )
}

fun ApplicationCall.userOrNull(): UserPrincipal? {
    return principal()
}

suspend fun ApplicationCall.userOrError(): UserPrincipal {
    return userOrNull()
        ?: throw KtpRspEx(
            status = HttpStatusCode.Unauthorized,
            detail = "User session not found",
        )
}

/**
 * Routes for signed-in users. With [optional], no cookie proceeds with a null [userOrNull], but a
 * cookie that is present is still fully checked (periodic recheck included), so a stale cookie for
 * a disabled or deleted account is refused. Never read [UserSession] from the sessions API instead.
 */
fun Route.authenticateFirebase(optional: Boolean = false, build: Route.() -> Unit): Route =
    authenticate(AuthProviderName.FIREBASE_SESSION, optional = optional, build = build)
