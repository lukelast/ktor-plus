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
    const val GC_LOG = "/gclog"
    const val THREADS = "/threads"
    const val VERSION = "/version"
}

/** Configuration for [DebugEndpointsPlugin]. */
class DebugEndpointsConfig {
    /** Prefix under which all debug routes are mounted. */
    var routePrefix: String = DebugEndpoints.BASE

    /** Serves the config page at [DebugEndpoints.CONFIG]. */
    var enableConfigEndpoint: Boolean = true

    /** Serves the GC log at [DebugEndpoints.GC_LOG]. */
    var enableGcLogEndpoint: Boolean = true

    /** Serves the version at [DebugEndpoints.VERSION]. */
    var enableVersionEndpoint: Boolean = true

    /** Serves the thread dump at [DebugEndpoints.THREADS]. */
    var enableThreadDumpEndpoint: Boolean = true

    /** Guards all debug endpoints; false yields 403 Forbidden, null (default) allows everyone. */
    var accessControl: (suspend ApplicationCall.() -> Boolean)? = null
}

/**
 * Serves debug index, config, GC log, thread dump, and version pages; they expose sensitive data,
 * so set [DebugEndpointsConfig.accessControl] in production.
 */
val DebugEndpointsPlugin =
    createApplicationPlugin(name = "DebugEndpoints", createConfiguration = ::DebugEndpointsConfig) {
        if (pluginConfig.accessControl == null) {
            log {}
                .warn {
                    "DebugEndpointsPlugin installed without access control! " +
                        "This may expose sensitive information. " +
                        "It is strongly recommended to set accessControl to restrict access."
                }
        }
        application.routing {
            route(pluginConfig.routePrefix) { installDebugEndpoints(pluginConfig) }
        }
    }

private fun Route.installDebugEndpoints(pluginConfig: DebugEndpointsConfig) {
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
        get(DebugEndpoints.GC_LOG) {
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

    // Registered last so child routes are matched first.
    get("") {
        if (pluginConfig.accessControl?.invoke(call) == false) {
            call.respond(HttpStatusCode.Forbidden)
            return@get
        }
        call.respondDebugIndex(pluginConfig)
    }
}
