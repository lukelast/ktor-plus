package net.ghue.ktp.gcp.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.ktor.plugin.MdcClearPlugin
import org.koin.ktor.ext.inject
import org.slf4j.MDC

object AuthProviderName {
    const val FIREBASE_SESSION: String = "firebase-session"
}

class FirebaseAuthPluginConfig {
    var secureCookies: (ktpConfig: KtpConfig, env: Env) -> Boolean = { ktpConfig, env ->
        if (env.isLocalDev) {
            false
        } else {
            ktpConfig.auth.secureCookies
        }
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
        val authConfig = ktpConfig.auth
        val useSecureCookies = pluginConfig.secureCookies(ktpConfig, ktpConfig.env)

        if (application.pluginOrNull(Sessions) == null) {
            application.install(Sessions) {
                cookie<UserSession>(ktpConfig.data.app.name.replace('.', '_').uppercase()) {
                    cookie.path = "/"
                    cookie.sameSite = "lax"
                    cookie.httpOnly = true
                    cookie.secure = useSecureCookies
                    cookie.maxAge = authConfig.sessionTimeoutDuration
                    transform(ktpConfig.createSessionTransportTransformer())
                }
            }
        }

        if (application.pluginOrNull(MdcClearPlugin) == null) {
            application.install(MdcClearPlugin)
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
                    // API endpoints need an error response.
                    // Browser requests would be better served with a redirect.
                    // Maybe attempt to detect browser requests some day.
                    call.respond(HttpStatusCode.Unauthorized)
                    // call.respondRedirect(authConfig.loginUrl)
                }
            }
        }

        application.routing {
            post(authConfig.loginUrl) { with(authService) { handleLogin() } }
            post(authConfig.logoutUrl) { with(authService) { handleLogout() } }
        }
    }
