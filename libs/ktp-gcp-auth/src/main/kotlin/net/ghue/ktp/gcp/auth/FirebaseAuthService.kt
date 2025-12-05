package net.ghue.ktp.gcp.auth

import com.google.firebase.ErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.log

class FirebaseAuthService(
    private val ktpConfig: KtpConfig,
    private val firebaseAuth: FirebaseAuth,
    private val lifecycle: AuthLifecycleHandler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun RoutingContext.handleLogin() {
        try {
            val loginRequest: LoginRequest = json.decodeFromString(call.receiveText())
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
                    message = "Email not verified: $firebaseToken.email",
                    status = HttpStatusCode.Unauthorized,
                    userError = false,
                )
            }
            val userInfo = lifecycle.onLogin(firebaseToken)

            val userSession =
                UserSession(
                    userId = firebaseToken.userId,
                    tenId = userInfo.tenantId,
                    email = userInfo.email,
                    name = userInfo.name,
                    roles = userInfo.roles,
                )
            call.sessions.set(userSession)

            val response =
                LoginResponse(
                    user =
                        LoginResponseUser(
                            userId = firebaseToken.userId.value,
                            email = userSession.email,
                            nameFull = userSession.name,
                            nameFirst = userSession.nameFirst,
                            roles = userInfo.roles,
                            extra = userInfo.extra,
                        )
                )
            val rspText = Gson().toJson(response)
            call.respondText(rspText, Json, HttpStatusCode.OK)
        } catch (ex: AuthEx) {
            if (ex.userError) {
                log {}.info(ex) { ex.message }
            } else {
                log {}.warn(ex) { ex.message }
            }
            call.respondText(
                text = json.encodeToString(LoginResponse()),
                contentType = Json,
                status = ex.status,
            )
        } catch (ex: Exception) {
            log {}.warn(ex) { "Unexpected login error" }
            call.respondText(json.encodeToString(LoginResponse()), Json, InternalServerError)
        }
    }

    suspend fun RoutingContext.handleLogout() {
        val userSession = call.sessions.get<UserSession>()
        call.sessions.clear<UserSession>()
        call.respondRedirect(ktpConfig.auth.redirectAfterLogout)
        if (userSession != null) {
            lifecycle.onLogout(userSession)
        }
    }

    @Throws(AuthEx::class)
    private suspend fun verifyToken(firebaseIdToken: String): FirebaseToken =
        withContext(Dispatchers.IO) {
            try {
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
}

private class AuthEx(
    message: String = "",
    val status: HttpStatusCode = InternalServerError,
    val userError: Boolean = true,
    cause: Throwable? = null,
) : Exception(message.ifBlank { cause?.message }, cause)
