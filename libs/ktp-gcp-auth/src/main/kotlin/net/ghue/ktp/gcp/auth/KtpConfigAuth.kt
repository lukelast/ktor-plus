package net.ghue.ktp.gcp.auth

import kotlin.time.Duration
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.config.parseDuration

val KtpConfig.auth: Auth
    get() = this.extractChild()

data class Auth(
    val loginUrl: String,
    val logoutUrl: String,
    val redirectAfterLogout: String,
    val sessionTimeout: String,
    val secureCookies: Boolean,
) {
    val sessionTimeoutDuration: Duration
        get() = parseDuration(sessionTimeout)
}
