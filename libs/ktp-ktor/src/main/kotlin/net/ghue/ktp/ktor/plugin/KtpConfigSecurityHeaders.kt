package net.ghue.ktp.ktor.plugin

import net.ghue.ktp.config.KtpConfig

val KtpConfig.securityHeaders: SecurityHeaders
    get() = this.extractChild()

/** The `securityHeaders` config block; see `9.securityHeaders.conf` for what each value does. */
data class SecurityHeaders(val enabled: Boolean, val csp: Csp) {
    /** Only what differs per app or per env; everything else in the policy is fixed in code. */
    data class Csp(
        val reportOnly: Boolean,
        val scriptSrc: List<String>,
        val styleSrc: List<String>,
        val fontSrc: List<String>,
        val imgSrc: List<String>,
        val connectSrc: List<String>,
        val frameSrc: List<String>,
    ) {
        fun configured(directive: CspDirective): List<String> =
            when (directive) {
                CspDirective.SCRIPT_SRC -> scriptSrc
                CspDirective.STYLE_SRC -> styleSrc
                CspDirective.FONT_SRC -> fontSrc
                CspDirective.IMG_SRC -> imgSrc
                CspDirective.CONNECT_SRC -> connectSrc
                CspDirective.FRAME_SRC -> frameSrc
            }
    }
}
