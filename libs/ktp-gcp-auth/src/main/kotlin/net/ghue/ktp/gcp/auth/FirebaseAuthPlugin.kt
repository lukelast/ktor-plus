package net.ghue.ktp.gcp.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import org.koin.ktor.ext.inject

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
                    cookie.extensions["SameSite"] = "lax"
                    cookie.httpOnly = true
                    cookie.secure = useSecureCookies
                    cookie.maxAge = authConfig.sessionTimeoutDuration
                    transform(ktpConfig.createSessionTransportTransformer())
                }
            }
        }

        application.authentication {
            session<UserSession>(AuthProviderName.FIREBASE_SESSION) {
                validate { session -> session }
            }
        }

        application.routing {
            post(authConfig.loginUrl) { with(authService) { handleLogin() } }
            get(authConfig.logoutUrl) { with(authService) { handleLogout() } }
        }
    }
