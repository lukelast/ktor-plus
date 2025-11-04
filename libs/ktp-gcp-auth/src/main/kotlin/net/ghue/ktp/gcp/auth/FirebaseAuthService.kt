package net.ghue.ktp.gcp.auth

import com.google.firebase.ErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.ghue.ktp.log.log

class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth,
    private val lifecycle: AuthLifecycleHandler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun RoutingContext.handleLogin() {
        try {
            val loginRequest: LoginRequest = json.decodeFromString(call.receiveText())
            if (loginRequest.idToken.isBlank()) {
                log {}.info { "login request token is empty" }
                call.respondText(
                    json.encodeToString(LoginResponse(error = "Unable to process login request")),
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest,
                )
                return
            }

            // Parse the token and verify it is valid.
            val firebaseToken =
                firebaseAuth.verifyIdToken(loginRequest.idToken, true)
                    ?: error("lib should not return null")

            // Create user session from verified token
            val userSession =
                UserSession(
                    userId = firebaseToken.uid,
                    email = firebaseToken.email,
                    nameFull = firebaseToken.name ?: firebaseToken.email,
                    nameFirst = firebaseToken.name?.split(" ")?.firstOrNull() ?: firebaseToken.email,
                )

            call.sessions.set(userSession)
            lifecycle.onLogin(userSession, firebaseToken)

            val response = LoginResponse(user = userSession)
            call.respondText(
                json.encodeToString(response),
                ContentType.Application.Json,
                HttpStatusCode.OK,
            )
        } catch (ex: IllegalArgumentException) {
            log {}.warn(ex) { "Invalid login request, possibly Firebase app has no project ID" }
            call.respondText(
                json.encodeToString(LoginResponse(error = "Unable to process login request")),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        } catch (ex: FirebaseAuthException) {
            // https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/ErrorCode
            val (statusCode, errorMessage, expectedError) =
                when (ex.errorCode) {
                    ErrorCode.UNAUTHENTICATED,
                    ErrorCode.NOT_FOUND ->
                        Triple(HttpStatusCode.Unauthorized, "Authentication failed", true)

                    ErrorCode.PERMISSION_DENIED ->
                        Triple(HttpStatusCode.Forbidden, "Authentication failed", true)

                    ErrorCode.INVALID_ARGUMENT ->
                        Triple(HttpStatusCode.BadRequest, "Unable to process login request", true)

                    ErrorCode.DEADLINE_EXCEEDED ->
                        Triple(
                            HttpStatusCode.RequestTimeout,
                            "Unable to process login request",
                            false,
                        )

                    else -> {
                        Triple(HttpStatusCode.Unauthorized, "Authentication failed", false)
                    }
                }

            if (expectedError) {
                log {}
                    .info(ex) { "Firebase authentication failure: ${ex.errorCode} - ${ex.message}" }
            } else {
                log {}
                    .error(ex) { "Unexpected Firebase auth error: ${ex.errorCode} - ${ex.message}" }
            }
            call.respondText(
                json.encodeToString(LoginResponse(error = errorMessage)),
                ContentType.Application.Json,
                statusCode,
            )
        } catch (ex: Exception) {
            // Catch any other unexpected errors
            log {}.error(ex) { "Unexpected login error" }
            call.respondText(
                json.encodeToString(LoginResponse(error = "Internal server error")),
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }
}

interface AuthLifecycleHandler {
    suspend fun onLogin(userSession: UserSession, firebaseToken: FirebaseToken) {}

    suspend fun onLogout(userSession: UserSession) {}
}

@Serializable private data class LoginRequest(val idToken: String)

@Serializable
private data class LoginResponse(val user: UserSession? = null, val error: String? = null)
