package net.ghue.ktp.gcp.auth

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserRecord
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.AttributeKey
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.log

/** Maximum age of account and role validation; renewing the cookie does not restart this clock. */
private val SESSION_RECHECK_INTERVAL: Duration = 2.days

private val sessionCheckUnavailable = AttributeKey<Unit>("ktp.sessionCheckUnavailable")

class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth,
    private val lifecycle: AuthLifecycleHandler,
    private val ktpConfig: KtpConfig,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun RoutingContext.handleLogin() {
        call.response.headers.append(HttpHeaders.CacheControl, NO_STORE)
        val loginRequest =
            try {
                json.decodeFromString<LoginRequest>(call.receiveText())
            } catch (_: SerializationException) {
                call.respondText("{}", Json, HttpStatusCode.BadRequest)
                return
            }
        if (loginRequest.idToken.isBlank()) {
            call.respondText("{}", Json, HttpStatusCode.BadRequest)
            return
        }
        try {
            val session =
                withContext(Dispatchers.IO) {
                    val token = verifyToken(loginRequest.idToken)
                    createFirebaseSession(UserId(token.uid), token.isAnonymous)
                }
            call.sessions.set(session)
            call.respondSessionUser(session)
        } catch (_: AuthDeniedException) {
            call.respondSessionFailure()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log {}.warn(ex) { "Unable to establish the session" }
            call.attributes.put(sessionCheckUnavailable, Unit)
            call.respondSessionFailure()
        }
    }

    /** Cookie-only until due; a successful full check refreshes both the principal and cookie. */
    internal suspend fun ApplicationCall.validateSession(session: UserSession?): UserSession? {
        if (session == null) return null
        // Dev users have no Firebase account to check; their cookies only count in local dev.
        if (
            session.userId.value == DEV_USER_ID || session.userId.value.startsWith("$DEV_USER_ID-")
        ) {
            return session.takeIf { ktpConfig.env.isLocalDev }
        }
        // A missing or future timestamp (clock skew) counts as due.
        val age = clock.instant().epochSecond - session.lastValidatedAt
        if (age in 0 until SESSION_RECHECK_INTERVAL.inWholeSeconds) return session
        return try {
            val refreshed =
                withContext(Dispatchers.IO) {
                    createFirebaseSession(session.userId, session.isAnonymous)
                }
            sessions.set(refreshed)
            refreshed
        } catch (_: AuthDeniedException) {
            null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log {}.warn(ex) { "Unable to revalidate the session" }
            attributes.put(sessionCheckUnavailable, Unit)
            null
        }
    }

    /** A denied account clears the cookie; an unavailable dependency leaves it for a retry. */
    internal suspend fun ApplicationCall.respondSessionFailure() {
        val status =
            if (attributes.contains(sessionCheckUnavailable)) {
                HttpStatusCode.ServiceUnavailable
            } else {
                sessions.clear<UserSession>()
                HttpStatusCode.Unauthorized
            }
        response.headers.append(HttpHeaders.CacheControl, NO_STORE)
        respondText("{}", Json, status)
    }

    suspend fun RoutingContext.handleSession() {
        val session = call.validateSession(call.sessions.get<UserSession>())
        if (session == null) {
            call.respondSessionFailure()
            return
        }
        // Sliding cookie expiry is separate from lastValidatedAt.
        call.sessions.set(session)
        call.respondSessionUser(session)
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

    /** The same current-account checks and application rules apply at login and revalidation. */
    private suspend fun createFirebaseSession(
        userId: UserId,
        wasAnonymous: Boolean,
    ): UserSession {
        // The check's age starts when the account is read, not when the login hook finishes.
        val checkedAt = clock.instant().epochSecond
        val user = loadFirebaseUser(userId)
        if (user.isDisabled) {
            throw AuthDeniedException("Firebase account disabled")
        }
        // An anonymous account that has since linked a provider must meet its verification rules.
        val anonymous = wasAnonymous && user.email.isNullOrBlank() && user.providerData.isEmpty()
        if (!anonymous && !user.isEmailVerified) {
            throw AuthDeniedException("Email not verified")
        }
        val identity = LoginIdentity(userId, user.email ?: "", user.displayName ?: "")
        val session = lifecycle.onLogin(identity)
        check(session.userId == userId) { "Login hook returned a different user" }
        return session.copy(lastValidatedAt = checkedAt)
    }

    private fun loadFirebaseUser(userId: UserId): UserRecord =
        try {
            firebaseAuth.getUser(userId.value)
        } catch (ex: FirebaseAuthException) {
            if (ex.authErrorCode == AuthErrorCode.USER_NOT_FOUND) {
                throw AuthDeniedException("Firebase account deleted")
            }
            throw ex
        }

    private fun verifyToken(idToken: String): FirebaseToken =
        try {
            // checkRevoked costs a Firebase round trip; acceptable once per login.
            firebaseAuth.verifyIdToken(idToken, true)
        } catch (ex: FirebaseAuthException) {
            when (ex.authErrorCode) {
                AuthErrorCode.INVALID_ID_TOKEN,
                AuthErrorCode.EXPIRED_ID_TOKEN,
                AuthErrorCode.REVOKED_ID_TOKEN,
                AuthErrorCode.USER_DISABLED,
                AuthErrorCode.USER_NOT_FOUND,
                AuthErrorCode.TENANT_ID_MISMATCH -> throw AuthDeniedException("Invalid credentials")
                else -> throw ex
            }
        }
}
