package net.ghue.ktp.gcp.auth

import com.google.firebase.ErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import net.ghue.ktp.log.log

class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth,
    private val lifecycle: AuthLifecycleHandler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun RoutingContext.handleLogin() {
        try {
            val loginRequest: LoginRequest =
                try {
                    json.decodeFromString(call.receiveText())
                } catch (ex: Exception) {
                    throw AuthEx(
                        message = "Malformed login request",
                        status = BadRequest,
                        userError = true,
                        cause = ex,
                    )
                }
            if (loginRequest.idToken.isBlank()) {
                throw AuthEx(
                    message = "Login request token is empty",
                    status = BadRequest,
                    userError = true,
                )
            }
            val firebaseToken = verifyToken(loginRequest.idToken)

            if (!firebaseToken.isAnonymous && !firebaseToken.isEmailVerified) {
                throw AuthEx(
                    message = "Email not verified: ${firebaseToken.email}",
                    status = HttpStatusCode.Unauthorized,
                    userError = false,
                )
            }
            val session = call.startSession(lifecycle, firebaseToken.toLoginIdentity())
            call.respondSessionUser(session)
        } catch (ex: AuthEx) {
            if (ex.userError) {
                log {}.info(ex) { ex.message }
            } else {
                log {}.warn(ex) { ex.message }
            }
            call.respondText(text = "{}", contentType = Json, status = ex.status)
        } catch (ex: Exception) {
            log {}.warn(ex) { "Unexpected login error" }
            call.respondText("{}", Json, InternalServerError)
        }
    }

    /**
     * Restores the signed-in user from the session cookie alone: no Firebase, no storage. This is
     * the page-load hot path, so the cookie is also re-issued to slide its expiry; an active user
     * is never signed out mid-use. 401 (and a cleared cookie) when there is no valid session.
     */
    suspend fun RoutingContext.handleSession() {
        val userSession = call.sessions.get<UserSession>()
        if (userSession == null) {
            call.sessions.clear<UserSession>()
            call.response.headers.append(HttpHeaders.CacheControl, NO_STORE)
            call.respondText(text = "{}", contentType = Json, status = HttpStatusCode.Unauthorized)
            return
        }
        call.sessions.set(userSession)
        call.respondSessionUser(userSession)
    }

    /**
     * Clearing the cookie is the whole logout (stateless session); 204 lets fetch() verify it.
     * Idempotent: no session still gets 204, just without [AuthLifecycleHandler.onLogout].
     */
    suspend fun RoutingContext.handleLogout() {
        val userSession = call.sessions.get<UserSession>()
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.NoContent)
        if (userSession != null) {
            lifecycle.onLogout(userSession)
        }
    }

    @Throws(AuthEx::class)
    private fun verifyToken(firebaseIdToken: String): FirebaseToken =
        try {
            // checkRevoked costs a Firebase round trip; acceptable once per login.
            firebaseAuth.verifyIdToken(firebaseIdToken, true)
                ?: throw AuthEx(message = "Should not return null", userError = false)
        } catch (ex: IllegalArgumentException) {
            throw AuthEx(
                message = "Invalid login request, possibly Firebase app has no project ID",
                userError = false,
                cause = ex,
            )
        } catch (ex: FirebaseAuthException) {
            // https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/ErrorCode
            when (ex.errorCode) {
                ErrorCode.UNAUTHENTICATED,
                ErrorCode.NOT_FOUND ->
                    throw AuthEx(
                        status = HttpStatusCode.Unauthorized,
                        userError = true,
                        cause = ex,
                    )
                ErrorCode.PERMISSION_DENIED ->
                    throw AuthEx(
                        status = HttpStatusCode.Forbidden,
                        userError = true,
                        cause = ex,
                    )
                ErrorCode.INVALID_ARGUMENT ->
                    throw AuthEx(status = BadRequest, userError = true, cause = ex)
                ErrorCode.DEADLINE_EXCEEDED ->
                    throw AuthEx(
                        status = HttpStatusCode.RequestTimeout,
                        userError = false,
                        cause = ex,
                    )
                else -> {
                    throw AuthEx(
                        status = HttpStatusCode.Unauthorized,
                        userError = false,
                        cause = ex,
                    )
                }
            }
        } catch (ex: Exception) {
            throw AuthEx(userError = false, cause = ex)
        }
}

private class AuthEx(
    message: String = "",
    val status: HttpStatusCode = InternalServerError,
    val userError: Boolean = true,
    cause: Throwable? = null,
) : Exception(message.ifBlank { cause?.message }, cause)
