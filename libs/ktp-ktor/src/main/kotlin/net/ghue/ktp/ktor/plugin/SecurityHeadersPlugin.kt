package net.ghue.ktp.ktor.plugin

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import java.util.concurrent.ConcurrentHashMap
import net.ghue.ktp.config.Env
import net.ghue.ktp.config.KtpConfig
import net.ghue.ktp.log.log
import org.koin.ktor.ext.get

/** Response header names [SecurityHeadersPlugin] sets; Ktor's `HttpHeaders` has none of them. */
object SecurityHeaderNames {
    const val CONTENT_SECURITY_POLICY = "Content-Security-Policy"
    const val CONTENT_SECURITY_POLICY_REPORT_ONLY = "Content-Security-Policy-Report-Only"
    const val CROSS_ORIGIN_OPENER_POLICY = "Cross-Origin-Opener-Policy"
    const val PERMISSIONS_POLICY = "Permissions-Policy"
    const val REFERRER_POLICY = "Referrer-Policy"
    const val X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
    const val X_FRAME_OPTIONS = "X-Frame-Options"
}

/** Same-origin POST endpoint browsers send CSP violation reports to. */
const val CSP_REPORT_PATH = "/csp-report"

/** The only value `X-Content-Type-Options` takes. */
private const val NOSNIFF = "nosniff"

/** `X-Frame-Options` value matching `frame-ancestors 'none'`. */
private const val FRAME_OPTIONS_DENY = "DENY"

/** The browser default, stated so a proxy or CDN cannot loosen it. */
private const val REFERRER_POLICY_VALUE = "strict-origin-when-cross-origin"

/** `same-origin` would break Firebase's sign-in popup, which the opener polls for closure. */
private const val CROSS_ORIGIN_OPENER_POLICY_VALUE = "same-origin-allow-popups"

/** Browser features no app uses; each is denied for the page and every frame in it. */
private const val PERMISSIONS_POLICY_VALUE =
    "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), " +
        "microphone=(), payment=(), usb=()"

// CSP source keywords.
private const val SELF = "'self'"
private const val NONE = "'none'"
private const val UNSAFE_INLINE = "'unsafe-inline'"

// CSP directives that take no configured sources, so they are not in [CspDirective].
private const val DEFAULT_SRC = "default-src"
private const val BASE_URI = "base-uri"
private const val OBJECT_SRC = "object-src"
private const val FRAME_ANCESTORS = "frame-ancestors"
private const val FORM_ACTION = "form-action"
private const val REPORT_URI = "report-uri"

// Vite's dev server, reached from the page origin: the HMR WebSocket and the restart ping.
private const val LOCAL_DEV_WS_SOURCE = "ws://localhost:*"
private const val LOCAL_DEV_HTTP_SOURCE = "http://localhost:*"

/** CSP fetch directives an app or a KTP lib can add sources to; each already allows `'self'`. */
enum class CspDirective(val directive: String) {
    SCRIPT_SRC("script-src"),
    STYLE_SRC("style-src"),
    FONT_SRC("font-src"),
    IMG_SRC("img-src"),
    CONNECT_SRC("connect-src"),
    FRAME_SRC("frame-src"),
}

/**
 * Sources a KTP plugin adds to the policy in code, so a version bump carries them to every app and
 * an app's config list never has to repeat them. Read when the first HTML response is sent, so a
 * plugin installed after [SecurityHeadersPlugin] still counts.
 */
class CspSources {
    private val sources = ConcurrentHashMap<CspDirective, MutableSet<String>>()

    fun add(directive: CspDirective, vararg origins: String) {
        sources.getOrPut(directive) { ConcurrentHashMap.newKeySet() }.addAll(origins)
    }

    operator fun get(directive: CspDirective): Set<String> = sources[directive].orEmpty()
}

private val cspSourcesKey = AttributeKey<CspSources>("KtpCspSources")

val Application.cspSources: CspSources
    get() = attributes.computeIfAbsent(cspSourcesKey) { CspSources() }

class SecurityHeadersPluginConfig {
    /** Defaults to the Koin-provided [KtpConfig]; set when the app has no Koin. */
    var ktpConfig: KtpConfig? = null
}

private const val MAX_REPORT_BYTES = 8 * 1024L
private val whitespaceRun = Regex("\\s+")

/**
 * Browser hardening headers, on unless `securityHeaders.enabled` is false. Every response gets
 * `X-Content-Type-Options` and `Referrer-Policy`; HTML responses also get the CSP,
 * `Cross-Origin-Opener-Policy`, `Permissions-Policy` and `X-Frame-Options`. A header the app
 * already set wins. Serves [CSP_REPORT_PATH], where browsers POST violation reports.
 *
 * The policy is one static string, so HTML stays cacheable: scripts must be same-origin files or
 * come from a listed origin (no inline scripts), while inline styles are allowed because React and
 * UI libraries inject them. Only the per-directive origins and report-only mode come from config
 * (`securityHeaders.csp`); the rest is fixed here.
 */
val SecurityHeadersPlugin =
    createApplicationPlugin(
        name = "SecurityHeadersPlugin",
        createConfiguration = ::SecurityHeadersPluginConfig,
    ) {
        val ktpConfig = pluginConfig.ktpConfig ?: application.get<KtpConfig>()
        val settings = ktpConfig.securityHeaders
        if (!settings.enabled) return@createApplicationPlugin
        val registry = application.cspSources
        // Deferred so plugins installed after this one can still register sources.
        val csp by lazy { buildCsp(settings.csp, registry, ktpConfig.env) }
        val cspHeader =
            if (settings.csp.reportOnly) SecurityHeaderNames.CONTENT_SECURITY_POLICY_REPORT_ONLY
            else SecurityHeaderNames.CONTENT_SECURITY_POLICY

        on(ResponseBodyReadyForSend) { call, content ->
            val headers = call.response.headers
            fun setIfAbsent(name: String, value: String) {
                if (headers[name] == null) headers.append(name, value)
            }
            setIfAbsent(SecurityHeaderNames.X_CONTENT_TYPE_OPTIONS, NOSNIFF)
            setIfAbsent(SecurityHeaderNames.REFERRER_POLICY, REFERRER_POLICY_VALUE)
            if (content.contentType?.match(ContentType.Text.Html) == true) {
                setIfAbsent(cspHeader, csp)
                setIfAbsent(
                    SecurityHeaderNames.CROSS_ORIGIN_OPENER_POLICY,
                    CROSS_ORIGIN_OPENER_POLICY_VALUE,
                )
                setIfAbsent(SecurityHeaderNames.PERMISSIONS_POLICY, PERMISSIONS_POLICY_VALUE)
                // Legacy twin of frame-ancestors for browsers that predate CSP level 2.
                setIfAbsent(SecurityHeaderNames.X_FRAME_OPTIONS, FRAME_OPTIONS_DENY)
            }
        }

        application.routing {
            post(CSP_REPORT_PATH) {
                val report = call.receiveChannel().readRemaining(MAX_REPORT_BYTES).readText()
                // One line: the body is attacker-controlled, so no forging extra log entries.
                log {}.warn { "CSP violation report: ${report.replace(whitespaceRun, " ").trim()}" }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

internal fun buildCsp(csp: SecurityHeaders.Csp, registered: CspSources, env: Env): String {
    fun sources(directive: CspDirective): List<String> =
        (csp.configured(directive) + registered[directive]).distinct().sorted()

    fun fetch(directive: CspDirective, vararg first: String): String {
        val all = first.toList() + sources(directive)
        return "${directive.directive} ${all.ifEmpty { listOf(NONE) }.joinToString(" ")}"
    }

    // Vite's dev server injects an inline script (the React Refresh preamble), opens its HMR
    // socket against the page origin and then the dev server, and pings the dev server over HTTP
    // to detect a restart. Production builds do none of this.
    val devScript = if (env.isLocalDev) arrayOf(UNSAFE_INLINE) else emptyArray()
    val devConnect =
        if (env.isLocalDev) arrayOf(LOCAL_DEV_WS_SOURCE, LOCAL_DEV_HTTP_SOURCE) else emptyArray()
    val directives =
        listOf(
            "$DEFAULT_SRC $SELF",
            "$BASE_URI $SELF",
            "$OBJECT_SRC $NONE",
            "$FRAME_ANCESTORS $NONE",
            "$FORM_ACTION $SELF",
            fetch(CspDirective.SCRIPT_SRC, SELF, *devScript),
            fetch(CspDirective.STYLE_SRC, SELF, UNSAFE_INLINE),
            fetch(CspDirective.FONT_SRC, SELF),
            fetch(CspDirective.IMG_SRC, SELF),
            fetch(CspDirective.CONNECT_SRC, SELF, *devConnect),
            fetch(CspDirective.FRAME_SRC),
            "$REPORT_URI $CSP_REPORT_PATH",
        )
    return directives.joinToString("; ")
}
