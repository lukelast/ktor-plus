package net.ghue.ktp.gcp.auth

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.json.Json

/** Session bodies are per-user and the cookie is the whole session: never let a cache hold one. */
internal const val NO_STORE = "no-store"

/** Runs the app's login hook for [identity] and issues the session it returns as the cookie. */
internal suspend fun ApplicationCall.startSession(
    lifecycle: AuthLifecycleHandler,
    identity: LoginIdentity,
    extraRoles: Set<String> = emptySet(),
): UserSession {
    val login = lifecycle.onLogin(identity)
    val session = login.copy(roles = login.roles + extraRoles)
    sessions.set(session)
    return session
}

internal fun UserSession.toLoginResponse(): LoginResponse =
    LoginResponse(
        LoginResponseUser(
            userId = userId.value,
            email = email,
            nameFull = name,
            nameFirst = nameFirst,
            roles = roles,
        )
    )

internal suspend fun ApplicationCall.respondSessionUser(session: UserSession) {
    response.headers.append(HttpHeaders.CacheControl, NO_STORE)
    respondText(
        Json.encodeToString(session.toLoginResponse()),
        ContentType.Application.Json,
        HttpStatusCode.OK,
    )
}
