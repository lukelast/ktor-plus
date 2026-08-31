package net.ghue.ktp.gcp.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.application.pluginOrNull
import io.ktor.server.auth.authentication
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.maxAge
import io.ktor.server.sessions.sameSite
import io.ktor.server.sessions.sessions
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
                    MDC.put("email", session.email)
                    // TODO Refresh the cookie if it's close to expiring?
                    session
                }
                challenge {
                    call.sessions.clear<UserSession>()
                    // APIs need an error; a browser would prefer a redirect (not detected yet).
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }

        application.routing {
            get(AuthUrls.CLIENT_CONFIG) {
                val service =
                    try {
                        authClientConfigService
                    } catch (ex: Exception) {
                        call.application.log.error(
                            "Unable to initialize Firebase client configuration",
                            ex,
                        )
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.respond(HttpStatusCode.ServiceUnavailable)
                        return@get
                    }
                with(service) { handleClientConfig() }
            }
            post(AuthUrls.LOGIN) { with(authService) { handleLogin() } }
            post(AuthUrls.LOGOUT) { with(authService) { handleLogout() } }
        }
    }
