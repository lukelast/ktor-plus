package net.ghue.ktp.ktor.app.debug

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.ghue.ktp.log.log

/** Default path constants for debug endpoints. */
object DebugEndpoints {
    const val BASE = "/debug"
    const val CONFIG = "/config"
    const val GCLOG = "/gclog"
    const val THREADS = "/threads"
    const val VERSION = "/version"
}

/**
 * Configuration for the ConfigDebugInfo plugin.
 *
 * Example usage with access control (localhost only):
 * ```
 * install(ConfigDebugInfoPlugin) {
 *     accessControl = {
 *         request.local.remoteHost == "127.0.0.1"
 *     }
 * }
 * ```
 *
 * Example usage with custom route prefix:
 * ```
 * install(ConfigDebugInfoPlugin) {
 *     routePrefix = "/admin/debug"
 * }
 * ```
 *
 * Example usage with selective endpoints:
 * ```
 * install(ConfigDebugInfoPlugin) {
 *     enableConfigEndpoint = true
 *     enableVersionEndpoint = true
 *     enableGcLogEndpoint = false  // Disable GC log endpoint
 * }
 * ```
 */
class ConfigDebugInfoConfig {
    /** Route prefix for debug endpoints. Default: "/debug" */
    var routePrefix: String = DebugEndpoints.BASE

    /** Whether to enable the configuration info endpoint. Default: true */
    var enableConfigEndpoint: Boolean = true

    /** Whether to enable the garbage collection endpoint. Default: true */
    var enableGcLogEndpoint: Boolean = true

    /** Whether to enable the version endpoint. Default: true */
    var enableVersionEndpoint: Boolean = true

    /** Whether to enable the thread dump endpoint. Default: true */
    var enableThreadDumpEndpoint: Boolean = true

    /**
     * Custom access control logic for all debug endpoints. Return true to allow access, false to
     * deny (returns 403 Forbidden).
     *
     * Examples:
     * ```
     * // Only allow localhost
     * accessControl = { request.local.remoteHost == "127.0.0.1" }
     *
     * // Check for a specific header
     * accessControl = { request.headers["X-Debug-Token"] == "secret-token" }
     *
     * // Integrate with Ktor authentication (requires ktor-server-auth dependency)
     * accessControl = { principal<UserPrincipal>()?.roles?.contains("admin") == true }
     * ```
     */
    var accessControl: (suspend ApplicationCall.() -> Boolean)? = null
}

/**
 * Modern Ktor plugin for exposing debug information endpoints.
 *
 * This plugin provides the following endpoints:
 * - GET /debug - HTML index page listing all debug endpoints with their status
 * - GET /debug/config - HTML page showing all configuration values, GC info, and system info
 * - GET /debug/gclog - Raw GC log file contents (if available at /tmp/gc.log)
 * - GET /debug/threads - Comprehensive thread dump with stack traces, locks, and CPU stats
 * - GET /debug/version - Plain text application version
 *
 * The index page is always enabled when the plugin is installed. Individual endpoints can be
 * individually disabled via configuration flags. All endpoints are protected with custom access
 * control if configured.
 *
 * **Security Warning**: These endpoints expose sensitive application information. Always use
 * `accessControl` to restrict access in production environments.
 */
val ConfigDebugInfoPlugin =
    createApplicationPlugin(
        name = "ConfigDebugInfo",
        createConfiguration = ::ConfigDebugInfoConfig,
    ) {
        if (pluginConfig.accessControl == null) {
            log {}
                .warn {
                    "ConfigDebugInfoPlugin installed without access control! " +
                        "This may expose sensitive information. " +
                        "It is strongly recommended to set accessControl to restrict access."
                }
        }
        application.routing {
            route(pluginConfig.routePrefix) { installDebugEndpoints(pluginConfig) }
        }
    }

private fun Route.installDebugEndpoints(pluginConfig: ConfigDebugInfoConfig) {
    if (pluginConfig.enableConfigEndpoint) {
        get(DebugEndpoints.CONFIG) {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondConfigHtml()
        }
    }

    if (pluginConfig.enableGcLogEndpoint) {
        get(DebugEndpoints.GCLOG) {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondGcLog()
        }
    }

    if (pluginConfig.enableThreadDumpEndpoint) {
        get(DebugEndpoints.THREADS) {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondThreadDump()
        }
    }

    if (pluginConfig.enableVersionEndpoint) {
        get(DebugEndpoints.VERSION) {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondVersion()
        }
    }

    // Register index route LAST - ensures child routes are matched first
    get("") {
        if (pluginConfig.accessControl?.invoke(call) == false) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.respondDebugIndex(pluginConfig)
    }
}
