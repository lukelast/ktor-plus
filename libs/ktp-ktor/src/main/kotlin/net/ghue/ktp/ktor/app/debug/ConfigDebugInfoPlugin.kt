package net.ghue.ktp.ktor.app.debug

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
    var routePrefix: String = "/debug"

    /** Whether to enable the /config endpoint. Default: true */
    var enableConfigEndpoint: Boolean = true

    /** Whether to enable the /gclog endpoint. Default: true */
    var enableGcLogEndpoint: Boolean = true

    /** Whether to enable the /version endpoint. Default: true */
    var enableVersionEndpoint: Boolean = true

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
 * - GET /debug/config - HTML page showing all configuration values, GC info, and system info
 * - GET /debug/gclog - Raw GC log file contents (if available at /tmp/gc.log)
 * - GET /debug/version - Plain text application version
 *
 * All endpoints can be individually disabled and protected with custom access control.
 *
 * **Security Warning**: These endpoints expose sensitive application information. Always use
 * `accessControl` to restrict access in production environments.
 */
val ConfigDebugInfoPlugin =
    createApplicationPlugin(
        name = "ConfigDebugInfo",
        createConfiguration = ::ConfigDebugInfoConfig,
    ) {
        application.routing {
            route(pluginConfig.routePrefix) { installDebugEndpoints(pluginConfig) }
        }
    }

private fun Route.installDebugEndpoints(pluginConfig: ConfigDebugInfoConfig) {
    if (pluginConfig.enableConfigEndpoint) {
        get("/config") {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondConfigHtml()
        }
    }

    if (pluginConfig.enableGcLogEndpoint) {
        get("/gclog") {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondGcLog()
        }
    }

    if (pluginConfig.enableVersionEndpoint) {
        get("/version") {
            if (pluginConfig.accessControl?.invoke(call) == false) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }
            call.respondVersion()
        }
    }
}
