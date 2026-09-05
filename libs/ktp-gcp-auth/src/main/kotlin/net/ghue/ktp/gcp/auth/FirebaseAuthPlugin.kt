package net.ghue.ktp.gcp.auth

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.authentication
import io.ktor.server.auth.session
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.maxAge
import io.ktor.server.sessions.sameSite
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import org.koin.ktor.ext.inject
import org.slf4j.MDC

object AuthProviderName {
    const val FIREBASE_SESSION: String = "firebase-session"
}

/** Fixed same-origin auth routes. Convention, not configuration: clients hardcode these paths. */
object AuthUrls {
    const val CLIENT_CONFIG: String = "/auth/config"
    const val LOGIN: String = "/auth/login"
    const val LOGOUT: String = "/auth/logout"

    /** `GET`: the signed-in user; periodically rechecks account status and roles. */
    const val SESSION: String = "/auth/session"

    /** `GET`, local dev only: mints a session for a named dev user, then redirects. */
    const val DEV_LOGIN: String = "/auth/dev/login"
}

class FirebaseAuthPluginConfig {
    var secureCookiesProvider: (ktpConfig: KtpConfig, env: Env) -> Boolean = { ktpConfig, env ->
        !env.isLocalDev && ktpConfig.auth.secureCookies
    }
}

val FirebaseAuthPlugin =
    createApplicationPlugin(
        name = "FirebaseAuthPlugin",
        createConfiguration = { FirebaseAuthPluginConfig() },
    ) {
        val pluginConfig = this.pluginConfig

        val ktpConfig: KtpConfig by application.inject()
        val authService: FirebaseAuthService by application.inject()
        val authClientConfigService: FirebaseAuthClientConfigService by application.inject()
        val useSecureCookies = pluginConfig.secureCookiesProvider(ktpConfig, ktpConfig.env)

        // An app that installs Sessions itself must register cookie<UserSession>, or login throws.
        if (application.pluginOrNull(Sessions) == null) {
            application.install(Sessions) {
                cookie<UserSession>(ktpConfig.data.app.name.replace('.', '_').uppercase()) {
                    cookie.path = "/"
                    cookie.sameSite = "lax"
                    cookie.httpOnly = true
                    cookie.secure = useSecureCookies
                    cookie.maxAge = ktpConfig.auth.sessionTimeoutDuration
                    transform(ktpConfig.createSessionTransportTransformer())
                }
            }
        }

        application.authentication {
            session<UserSession>(AuthProviderName.FIREBASE_SESSION) {
                validate { session ->
                    val validated = with(authService) { validateSession(session) }
                    validated?.also { MDC.put("email", it.email) }
                }
                challenge { with(authService) { call.respondSessionFailure() } }
            }
        }

        application.routing {
            get(AuthUrls.CLIENT_CONFIG) { with(authClientConfigService) { handleClientConfig() } }
            post(AuthUrls.LOGIN) { with(authService) { handleLogin() } }
            post(AuthUrls.LOGOUT) { with(authService) { handleLogout() } }
            get(AuthUrls.SESSION) { with(authService) { handleSession() } }

            // Not a guarded handler but an absent route: outside local dev it 404s like any
            // other unknown path, so there is nothing to misconfigure in production.
            if (ktpConfig.env.isLocalDev) {
                val devLoginService: DevLoginService by application.inject()
                application.log.warn("Dev login enabled at ${AuthUrls.DEV_LOGIN} (local dev only)")
                get(AuthUrls.DEV_LOGIN) { with(devLoginService) { handleDevLogin() } }
            }
        }
    }
